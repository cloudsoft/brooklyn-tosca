package alien4cloud.brooklyn;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import org.apache.brooklyn.rest.domain.LocationSummary;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.orchestrators.plugin.ILocationAutoConfigurer;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class BrooklynOrchestrator extends BrooklynProvider implements IOrchestratorPlugin<Configuration>, ILocationAutoConfigurer {

    @Inject
    private BrooklynLocationConfigurerFactory brooklynLocationConfigurerFactory;

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return brooklynLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        // TODO: Implement if required (will this obviate the need to upload the normative types?)
        return Collections.emptyList();
    }

    @Override
    public List<Location> getLocations() {
        List<LocationSummary> locations = getNewBrooklynApi().getLocationApi().list();
        List<Location> newLocations = Lists.newArrayList();
        for (LocationSummary location : locations) {
            log.info("location={}" + location);
            Location l = new Location();
            l.setName(location.getName());
            l.setInfrastructureType("Brooklyn");
            newLocations.add(l);
        }
        return newLocations;
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext paaSDeploymentContext, String s, Map<String, Object> map, IPaaSCallback<String> iPaaSCallback) {
        // TODO: Determine whether or not this is required, and implement if required, otherwise no-op
        throw new UnsupportedOperationException("launchWorkflow is not currently supported");
    }
}
