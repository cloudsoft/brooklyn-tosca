package alien4cloud.brooklyn;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;

import org.elasticsearch.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
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

            // TODO Not great way to go but that's a POC for now ;)
            catalogMapper.mapBrooklynEntity(brooklynApi, "brooklyn.entity.webapp.tomcat.TomcatServer", "0.0.0-SNAPSHOT");
            
        } finally { revertContextClassLoader(); }
    }

    @Override
    @SneakyThrows
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("DEPLOY "+deploymentContext+" / "+callback+" -- but ignoring what you asked for and just deploying a mock for now... :)");
        
        // TODO only does node templates
        // and for now it builds up camp yaml
        Map<String,Object> campYaml = Maps.newLinkedHashMap();
//        for (NodeTemplate nt: deploymentContext.getTopology().getNodeTemplates().values()) {
//            nt.getType()
//        }

        List<Object> svcs = Lists.newArrayList();
        
        Map<String,Object> svc1 = Maps.newLinkedHashMap();
        svc1.put("type", "brooklyn.entity.webapp.tomcat.TomcatServer");
        svc1.put("war", "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0-M2/brooklyn-example-hello-world-sql-webapp-0.6.0-M2.war");
        svc1.put("httpPort", 80);
        svcs.add(svc1);
        
        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
        
        campYaml.put("services", svcs);  
        campYaml.put("location", "localhost");
        campYaml.put("brooklyn.config", ImmutableMap.of("tosca.id", deploymentContext.getDeploymentId()));
        
        useLocalContextClassLoader();
        try {
            useLocalContextClassLoader();
            brooklynApi.getApplicationApi().createFromYaml( new ObjectMapper().writeValueAsString(campYaml) );
        } finally { revertContextClassLoader(); }
        
        if (callback!=null) callback.onSuccess(null);
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
