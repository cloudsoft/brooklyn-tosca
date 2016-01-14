package alien4cloud.brooklyn;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.Application;
import alien4cloud.model.common.Tag;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.orchestrators.locations.services.LocationService;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSMessageMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.cloudsoft.tosca.metadata.RequiresBrooklynApi;
import io.cloudsoft.tosca.metadata.ToscaMetadataProvider;
import io.cloudsoft.tosca.metadata.ToscaTypeProvider;
import lombok.SneakyThrows;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.util.text.Strings;
import org.elasticsearch.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class BrooklynProvider implements IConfigurablePaaSProvider<Configuration> {
    private static final Logger log = LoggerFactory.getLogger(BrooklynProvider.class);

    private Configuration configuration;

    // TODO mock cache while we flesh out the impl
    protected Map<String,PaaSTopologyDeploymentContext> knownDeployments = Maps.newConcurrentMap();
    protected Map<String, Optional<DeploymentStatus>> deploymentStatuses = Maps.newConcurrentMap();

    // TODO: Sanity check correct InstanceStatuses are being used
    private static final Map<Status, InstanceStatus> SERVICE_STATE_TO_INSTANCE_STATUS = ImmutableMap.<Status, InstanceStatus>builder()
            .put(Status.ACCEPTED, InstanceStatus.PROCESSING)
            .put(Status.STARTING, InstanceStatus.PROCESSING)
            .put(Status.RUNNING, InstanceStatus.SUCCESS)
            .put(Status.STOPPING, InstanceStatus.MAINTENANCE)
            .put(Status.STOPPED, InstanceStatus.MAINTENANCE)
            .put(Status.DESTROYED, InstanceStatus.MAINTENANCE)
            .put(Status.ERROR, InstanceStatus.FAILURE)
            .put(Status.UNKNOWN, InstanceStatus.PROCESSING)
            .build();

    private static final Map<Status, DeploymentStatus> SERVICE_STATE_TO_DEPLOYMENT_STATUS = ImmutableMap.<Status, DeploymentStatus>builder()
            .put(Status.ACCEPTED, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
            .put(Status.STARTING, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
            .put(Status.RUNNING, DeploymentStatus.DEPLOYED)
            .put(Status.STOPPING, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS)
            .put(Status.STOPPED, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS)
            .put(Status.DESTROYED, DeploymentStatus.UNDEPLOYED)
            .put(Status.ERROR, DeploymentStatus.FAILURE)
            .put(Status.UNKNOWN, DeploymentStatus.UNKNOWN)
            .build();


    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private BrooklynCatalogMapper catalogMapper;

    @Autowired
    @Qualifier("alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Autowired
    private BeanFactory beanFactory;

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
            // TODO synchronise locations
            catalogMapper.addBaseTypes();

            List<ToscaTypeProvider> metadataProviders = new LinkedList<>();
            for (String providerClass : configuration.getProviders()) {
                try {
                    Object provider = beanFactory.getBean(Class.forName(providerClass));
                    if(provider instanceof RequiresBrooklynApi) {
                        ((RequiresBrooklynApi) provider).setBrooklynApi(getNewBrooklynApi());
                    }
                    // Alien UI has higher priority items at the end of the list.
                    // Reverse the order here.
                    metadataProviders.add(0, ToscaTypeProvider.class.cast(provider));
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load metadata provider " + providerClass, e);
                }
            }
            catalogMapper.mapBrooklynEntities(getNewBrooklynApi(), new ToscaMetadataProvider(metadataProviders));

        } finally {
            revertContextClassLoader();
        }
    }

    @Override
    @SneakyThrows
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("DEPLOY "+deploymentContext+" / "+callback);
        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
        String topologyId = deploymentContext.getDeploymentTopology().getId();

        Map<String,Object> campYaml = Maps.newLinkedHashMap();
        addRootPropertiesAsCamp(deploymentContext, campYaml);

        List<Object> svcs = Lists.newArrayList();
        Map<String, Object> svc = Maps.newHashMap();
        svc.put("type", "alien4cloud_deployment_topology:" + topologyId);
        svcs.add(svc);
        campYaml.put("services", svcs);
        campYaml.put("brooklyn.config", ImmutableMap.of("tosca.deployment.id", deploymentContext.getDeploymentId()));

        String locationIds[] = deploymentContext.getDeployment().getLocationIds();

        if (locationIds.length > 0) {
            campYaml.put("location", locationService.getOrFail(locationIds[0]).getName());
        }

        try {
            useLocalContextClassLoader();
            String campYamlString = new ObjectMapper().writeValueAsString(campYaml);
            log.info("DEPLOYING: "+campYamlString);
            Response result = getNewBrooklynApi().getApplicationApi().createFromYaml( campYamlString );
            TaskSummary createAppSummary = BrooklynApi.getEntity(result, TaskSummary.class);
            log.info("RESULT: "+result.getEntity());
            validate(result);
            String entityId = createAppSummary.getEntityId();
            deploymentContext.getDeployment().setOrchestratorDeploymentId(entityId);
            alienDAO.save(deploymentContext.getDeployment());
            // (the result is a 204 creating, whose entity is a TaskSummary
            // with an entityId of the entity which is created and id of the task)
            deploymentStatuses.put(entityId, Optional.<DeploymentStatus>absent());
            // inital entry which will immediately trigger an event in getEventsSince()
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
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback callback) {
        log.info("UNDEPLOY " + deploymentContext + " / " + callback);
        Response result = getNewBrooklynApi().getEntityApi().expunge(deploymentContext.getDeployment().getOrchestratorDeploymentId(), deploymentContext.getDeployment().getOrchestratorDeploymentId(), true);
        validate(result);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        log.warn("SCALE not supported");
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        if (callback!=null) {
            String entityId = deploymentContext.getDeployment().getOrchestratorDeploymentId();
            log.info("GET STATUS - " + entityId);
            Optional<DeploymentStatus> deploymentStatus = deploymentStatuses.get(entityId);
            callback.onSuccess(!deploymentStatus.isPresent() ? DeploymentStatus.UNDEPLOYED : deploymentStatus.get());
        }
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {

        BrooklynApi brooklynApi = getNewBrooklynApi();
        // We lookup Entities based on tosca.id (getDeploymentId())
        String appId = deploymentContext.getDeployment().getOrchestratorDeploymentId();

        Map<String, Map<String, InstanceInformation>> topology = Maps.newHashMap();

        List<EntitySummary> descendants = brooklynApi.getEntityApi().getDescendants(appId, appId, ".*");

        // TODO: Either get all sensors for all descendants, or iterate through the descendants,
        // building an InstanceInformation, and populating the topology

        for (EntitySummary descendant : descendants) {
            String entityId = descendant.getId();
            if (entityId.equals(appId)) {
                continue;
            }
            String templateId = String.valueOf(brooklynApi.getEntityConfigApi().get(appId, entityId, "tosca.template.id", false));
            // TODO: Work out what to do with clusters, for now assume it's all flat
            InstanceInformation instanceInformation = new InstanceInformation();
            Map<String, Object> sensorValues = brooklynApi.getSensorApi().batchSensorRead(appId, entityId, null);
            ImmutableMap.Builder<String, String> sensorValuesStringBuilder = ImmutableMap.builder();
            ImmutableMap.Builder<String, String> attributeValuesStringBuilder = ImmutableMap.builder();
            for (Entry<String, Object> entry : sensorValues.entrySet()) {
                if(entry.getKey().startsWith("tosca.attribute")) {
                    attributeValuesStringBuilder.put(entry.getKey(), String.valueOf(entry.getValue()));
                } else {
                    sensorValuesStringBuilder.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            instanceInformation.setRuntimeProperties(sensorValuesStringBuilder.build());
            instanceInformation.setAttributes(attributeValuesStringBuilder.build());
            String serviceState = String.valueOf(sensorValues.get("service.state"));
            instanceInformation.setState(serviceState);
            instanceInformation.setInstanceStatus(SERVICE_STATE_TO_INSTANCE_STATUS.get(Status.valueOf(serviceState)));
            topology.put(templateId, ImmutableMap.of(entityId, instanceInformation));
        }

        callback.onSuccess(topology);
    }

    private void addTasksAndDescendants(Collection<TaskSummary> taskSummaries, Collection<AbstractMonitorEvent> events, String deploymentId,
            Date date, BrooklynApi brooklynApi) {
        for (TaskSummary taskSummary : taskSummaries) {
            Long startTime = taskSummary.getStartTimeUtc();
            if (Strings.isNonBlank(taskSummary.getDisplayName()) && startTime != null) {
                if (startTime > date.getTime()) {
                    // TODO: choose correct event type. See commented-out code below for event types
                    PaaSMessageMonitorEvent messageEvent = new PaaSMessageMonitorEvent();
                    messageEvent.setDate(taskSummary.getStartTimeUtc());
                    messageEvent.setDeploymentId(deploymentId);
                    messageEvent.setMessage(taskSummary.getEntityDisplayName() + " : " +  taskSummary.getDisplayName());
                    events.add(messageEvent);
                }
                Collection<TaskSummary> childSummaries;
                try {
                    childSummaries = brooklynApi.getActivityApi().children(taskSummary.getId());
                    addTasksAndDescendants(childSummaries, events, deploymentId, date, brooklynApi);
                } catch (Exception ignored) {
                    // If we can't get the child tasks (usually because of a 404 if the task is transient), ignore
                    // the error and carry on
                }
            }
        }

//        try {
//            ApplicationSummary summary = getBrooklynApi().getApplicationApi().list("").iterator().next();
//            String applicationId = summary.getId();
//
//            List<EntitySummary> children = getBrooklynApi().getEntityApi().getChildren(applicationId, applicationId);
//
//            EntitySummary child = children.get(0);
//
//            String brooklynType = child.getType();
//            brooklynType = brooklynType.substring(brooklynType.lastIndexOf(".") + 1);
//            String deploymentId = String.valueOf(getBrooklynApi().getEntityConfigApi().get(applicationId, applicationId, "tosca.deployment.id", false));
//
//            // This is asking for events for the entire Brooklyn Management plane
//            // Get a list of tasks since ${date}, limit to maxEvents
//
//            PaaSDeploymentStatusMonitorEvent statusEvent = new PaaSDeploymentStatusMonitorEvent();
//            statusEvent.setDate(System.currentTimeMillis());  // Time of event!
//            statusEvent.setDeploymentId(deploymentId); // Need to look up the tosca.id which relates to this event
//            statusEvent.setDeploymentStatus(DeploymentStatus.DEPLOYED);
//            statusEvent.setDate(System.currentTimeMillis()); // Time of event!
//
//            PaaSInstanceStateMonitorEvent stateMonitorEvent = new PaaSInstanceStateMonitorEvent();
//            stateMonitorEvent.setAttributes(ImmutableMap.of("ip_address", "127.0.0.1")); // Find these in the alien console (tosca nomative types)
//            stateMonitorEvent.setRuntimeProperties(ImmutableMap.of("some.brooklyn.sensor", "some.value"));
//            stateMonitorEvent.setInstanceStatus(InstanceStatus.SUCCESS);
//            stateMonitorEvent.setInstanceId(applicationId); // Broolkyn ID
//            stateMonitorEvent.setNodeTemplateId(brooklynType); // Brooklyn type
////        stateMonitorEvent.setCloudId("tosca.id"); // Internal only - Do not set
//            stateMonitorEvent.setDate(System.currentTimeMillis());  // Time of event!
//            stateMonitorEvent.setDeploymentId(deploymentId); // Need to look up the tosca.id which relates to this event
//
//            PaaSMessageMonitorEvent messageEvent = new PaaSMessageMonitorEvent();
//            messageEvent.setDate(System.currentTimeMillis());  // Time of event!
//            messageEvent.setDeploymentId(deploymentId); // Need to look up the tosca.id which relates to this event
//            messageEvent.setMessage("Hello from Brooklyn!");
//
//
//            // Deferred?
//            // ^^ set date, deploymentid etc
////        PaaSInstanceStorageMonitorEvent event4 = new PaaSInstanceStorageMonitorEvent();
////        event4.setDeletable(false); // These replace to block storage only
//
//            AbstractMonitorEvent[] events = {
//                    statusEvent,
//                    stateMonitorEvent,
//                    messageEvent
//            };
//            eventCallback.onSuccess(events);
//        } catch (Exception e) {
//            eventCallback.onFailure(e);
//        }

    }

    @Override
    public synchronized void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
        BrooklynApi brooklynApi = getNewBrooklynApi();
        try {
            Collection<AbstractMonitorEvent> events = Sets.newHashSet();
            List<ApplicationSummary> appSummaries = brooklynApi.getApplicationApi().list(null);
            for (ApplicationSummary appSummary : appSummaries) {
                String appId = appSummary.getId();
                String deploymentId = String.valueOf(brooklynApi.getEntityConfigApi().get(appId, appId, "tosca.deployment.id", false));

                for (EntitySummary entitySummary : brooklynApi.getEntityApi().getDescendants(appId, appId, null)) {
                    String entityId = entitySummary.getId();
                    List<TaskSummary> taskSummaries = brooklynApi.getEntityApi().listTasks(appId, entityId);
                    addTasksAndDescendants(taskSummaries, events, deploymentId, date, brooklynApi);
                }
            }

            for(Entry<String, Optional<DeploymentStatus>> deploymentStatus : ImmutableSet.copyOf(deploymentStatuses.entrySet())) {
                final String entityId = deploymentStatus.getKey();
                final Optional<PaaSTopologyDeploymentContext> deployment = Iterables.tryFind(knownDeployments.values(), new Predicate<PaaSTopologyDeploymentContext>() {
                    @Override
                    public boolean apply(@Nullable PaaSTopologyDeploymentContext input) {
                        return input.getDeployment().getOrchestratorDeploymentId().equals(entityId);
                    }
                });
                if (!deployment.isPresent()) {
                    continue;
                }
                Optional<ApplicationSummary> applicationSummary = Iterables.tryFind(appSummaries, new Predicate<ApplicationSummary>() {
                    @Override
                    public boolean apply(ApplicationSummary app) {
                        return app.getId().equals(entityId);
                    }
                });
                if (applicationSummary.isPresent()) {
                    DeploymentStatus lastKnownStatus = deploymentStatuses.get(entityId).orNull();
                    DeploymentStatus currentStatus =  SERVICE_STATE_TO_DEPLOYMENT_STATUS.get(applicationSummary.get().getStatus());
                    if (!currentStatus.equals(lastKnownStatus)) {
                        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
                        String deploymentId = deployment.get().getDeploymentId();
                        updatedStatus.setDeploymentId(deploymentId);
                        updatedStatus.setDeploymentStatus(currentStatus);
                        events.add(updatedStatus);
                        deploymentStatuses.put(entityId, Optional.of(currentStatus));
                    }
                } else {
                    PaaSDeploymentStatusMonitorEvent undeployed = new PaaSDeploymentStatusMonitorEvent();
                    String deploymentId = deployment.get().getDeploymentId();
                    undeployed.setDeploymentId(deploymentId);
                    undeployed.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
                    events.add(undeployed);
                    // If it's never been seen before, it's most likely not yet been managed by Brooklyn
                    // If it's previously been seen, and is now absent, it's been deleted (expunged)
                    if (deploymentStatus.getValue().isPresent()) {
                        knownDeployments.remove(deploymentId);
                        deploymentStatuses.remove(entityId);
                    }
                }
            }

            eventCallback.onSuccess(events.toArray(new AbstractMonitorEvent[]{}));
        } catch (Exception e) {
            eventCallback.onFailure(e);
        }
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request,
            IPaaSCallback<Map<String, String>> operationResultCallback) throws OperationExecutionException {
        log.warn("EXEC OP not supported: " + request);
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

    protected BrooklynApi getNewBrooklynApi() {
        return BrooklynApi.newInstance(configuration.getUrl(), configuration.getUser(), configuration.getPassword());
    }
}
