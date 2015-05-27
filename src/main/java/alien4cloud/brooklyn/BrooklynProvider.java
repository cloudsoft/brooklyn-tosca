package alien4cloud.brooklyn;

import java.util.Date;
import java.util.Map;

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
import alien4cloud.paas.model.*;
import brooklyn.rest.client.BrooklynApi;

@Component
@Scope(value = "prototype")
public class BrooklynProvider implements IConfigurablePaaSProvider<Configuration> {
    private static final Logger log = LoggerFactory.getLogger(BrooklynProvider.class);

    private Configuration configuration;
    private BrooklynApi brooklynApi;

    @Autowired
    private BrooklynCatalogMapper catalogMapper;

    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        log.info("INIT: " + activeDeployments);
        brooklynApi = new BrooklynApi(configuration.getUrl(), configuration.getUsername(), configuration.getPassword());
        // TODO Not great way to go but that's a POC for now ;)
        catalogMapper.mapBrooklynEntity(brooklynApi, "brooklyn.entity.webapp.tomcat.TomcatServer", "0.0.0-SNAPSHOT");
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("DEPLOY " + deploymentContext + " / " + callback);
        callback.onSuccess(null);
    }

    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("UNDEPLOY " + deploymentContext + " / " + callback);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        log.warn("SCALE not supported");
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        log.info("GET STATUS");
        callback.onSuccess(DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
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
        log.info("Setting configuration " + configuration);
        this.configuration = configuration;
    }
}