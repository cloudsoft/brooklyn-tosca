package alien4cloud.brooklyn;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.Response;

import lombok.SneakyThrows;

import org.elasticsearch.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.application.ApplicationService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.Application;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
import alien4cloud.model.common.Tag;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import brooklyn.rest.client.BrooklynApi;
import brooklyn.util.text.Strings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 *
 */
@Component
@Scope(value = "prototype")
public class BrooklynProvider implements IConfigurablePaaSProvider<Configuration> {
    private static final Logger log = LoggerFactory.getLogger(BrooklynProvider.class);

    private Configuration configuration;
    private BrooklynApi brooklynApi;
    // TODO mock cache while we flesh out the impl
    protected Map<String,Object> knownDeployments = Maps.newLinkedHashMap();

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private BrooklynCatalogMapper catalogMapper;

    ThreadLocal<ClassLoader> oldContextClassLoader = new ThreadLocal<ClassLoader>();
    private void useLocalContextClassLoader() {
        if (oldContextClassLoader.get()==null) {
            oldContextClassLoader.set( Thread.currentThread().getContextClassLoader() );
        }
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }
    private void revertContextClassLoader() {
        if (oldContextClassLoader.get()==null) {
            log.warn("No local context class loader to revert");
        }
        Thread.currentThread().setContextClassLoader(oldContextClassLoader.get());
        oldContextClassLoader.remove();
    }
    
    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        useLocalContextClassLoader();
        try {
            log.info("INIT: " + activeDeployments);
            brooklynApi = new BrooklynApi(configuration.getUrl(), configuration.getUser(), configuration.getPassword());

            catalogMapper.mapBrooklynEntities(brooklynApi);
            
        } finally { revertContextClassLoader(); }
    }

    @Override
    @SneakyThrows
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("DEPLOY "+deploymentContext+" / "+callback);
        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
        
        // TODO only does node templates
        // and for now it builds up camp yaml
        Map<String,Object> campYaml = Maps.newLinkedHashMap();
        Topology topology = deploymentContext.getTopology();
        
        addRootPropertiesAsCamp(deploymentContext, campYaml);
        campYaml.put("brooklyn.config", ImmutableMap.of("tosca.id", deploymentContext.getDeploymentId()));

        List<Object> svcs = Lists.newArrayList();
        addNodeTemplatesAsCampServicesList(svcs, topology);
        campYaml.put("services", svcs); 
        
        if (Strings.isNonBlank(configuration.getLocation()))
            campYaml.put("location", configuration.getLocation());
        
        try {
            useLocalContextClassLoader();
            String campYamlString = new ObjectMapper().writeValueAsString(campYaml);
            log.info("DEPLOYING: "+campYamlString);
            Response result = brooklynApi.getApplicationApi().createFromYaml( campYamlString );
            log.info("RESULT: "+result.getEntity());
            validate(result);
            // (the result is a 204 creating, whose entity is a TaskSummary 
            // with an entityId of the entity which is created and id of the task)
            // TODO set the brooklyn entityId somewhere that it can be recorded in A4C for easy cross-referencing
        } catch (Throwable e) {
            log.warn("ERROR DEPLOYING", e);
            throw e;
        } finally { revertContextClassLoader(); }
        
        if (callback!=null) callback.onSuccess(null);
    }

    private void validate(Response r) {
        if (r==null) return;
        if ((r.getStatus() / 100)==2) return;
        throw new IllegalStateException("Server returned "+r.getStatus());
    }
    private void addRootPropertiesAsCamp(PaaSTopologyDeploymentContext deploymentContext, Map<String,Object> result) {
        if (applicationService!=null) {
            try {
                Application app = applicationService.getOrFail(deploymentContext.getDeployment().getSourceId());
                if (app!=null) {
                    result.put("name", app.getName());
                    if (app.getDescription()!=null) result.put("description", app.getDescription());
                    
                    List<String> tags = Lists.newArrayList();
                    for (Tag tag: app.getTags()) {
                        tags.add(tag.getName()+": "+tag.getValue());
                    }
                    if (!tags.isEmpty())
                        result.put("tags", tags);
                    
                    // TODO icon, from app.getImageId());
                    return;
                }
                log.warn("Application null when deploying "+deploymentContext+"; using less information");
            } catch (NotFoundException e) {
                // ignore, fall through to below
                log.warn("Application instance not found when deploying "+deploymentContext+"; using less information");
            }
        } else {
            log.warn("Application service not available when deploying "+deploymentContext+"; using less information");
        }
        
        // no app or app service - use what limited information we have
        result.put("name", "A4C: "+deploymentContext.getDeployment().getSourceName());
        result.put("description", "Created by Alien4Cloud from application "+deploymentContext.getDeployment().getSourceId());
    }
    
    private void addNodeTemplatesAsCampServicesList(List<Object> svcs, Topology topology) {
        for (Entry<String, NodeTemplate> nodeEntry : topology.getNodeTemplates().entrySet()) {
            Map<String,Object> svc = Maps.newLinkedHashMap();
            
            NodeTemplate nt = nodeEntry.getValue();
            svc.put("name", nodeEntry.getKey());
            
            // TODO mangle according to the tag brooklyn_blueprint_catalog_id on the type (but how do we get the IndexedNodeType?)
            // this will give us the type, based on TopologyValidationService
//            IndexedNodeType relatedIndexedNodeType = csarRepoSearchService.getRequiredElementInDependencies(IndexedNodeType.class, nodeTemp.getType(),
//                topology.getDependencies());

            svc.put("type", nt.getType());
            
            for (Entry<String, AbstractPropertyValue> prop: nt.getProperties().entrySet()) {
                String propName = prop.getKey();
                // TODO mangle prop name according to the tag  brooklyn_property_map__<propName>
                AbstractPropertyValue v = prop.getValue();
                Object propValue = null;
                if (v instanceof ScalarPropertyValue) {
                    propValue = ((ScalarPropertyValue)v).getValue();
                } else {
                    log.warn("Ignoring non-scalar property value for "+propName+": "+v);
                }
                if (propValue!=null) {
                    svc.put(propName, propValue);
                }
            }
            svcs.add(svc);
        }
    }
    
    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        knownDeployments.remove(deploymentContext.getDeploymentId());
        log.info("UNDEPLOY "+deploymentContext+" / "+callback);

        if (callback!=null) callback.onSuccess(null);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        log.warn("SCALE not supported");
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        Object dep = knownDeployments.get(deploymentContext.getDeploymentId());
        log.info("GET STATUS - "+dep);
        if (callback!=null) callback.onSuccess(dep==null ? DeploymentStatus.UNDEPLOYED : DeploymentStatus.DEPLOYED);
    }

    @Override
    public void getInstancesInformation(PaaSDeploymentContext deploymentContext, Topology topology,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        // TODO
    }

    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
        // TODO
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request,
            IPaaSCallback<Map<String, String>> operationResultCallback) throws OperationExecutionException {
        log.warn("EXEC OP not supported: " + request);
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType) {
        return new String[0];
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType, String imageId) {
        return new String[0];
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig config) {
        log.info("MATCHER CONFIG (ignored): " + config);
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) throws MaintenanceModeException {
        log.info("MAINT MODE (ignored): " + maintenanceModeOn);
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId, String instanceId, boolean maintenanceModeOn)
            throws MaintenanceModeException {
        log.info("MAINT MODE for INSTANCE (ignored): " + maintenanceModeOn);
    }

    @Override
    public void setConfiguration(Configuration configuration) throws PluginConfigurationException {
        log.info("Setting configuration: " + configuration);
        this.configuration = configuration;
    }
}
