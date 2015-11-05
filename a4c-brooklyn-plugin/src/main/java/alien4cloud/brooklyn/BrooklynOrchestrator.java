package alien4cloud.brooklyn;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
import lombok.extern.slf4j.Slf4j;

import org.apache.brooklyn.rest.domain.LocationSummary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationAutoConfigurer;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@Scope(value = "prototype")
@Slf4j
public class BrooklynOrchestrator extends BrooklynProvider implements IOrchestratorPlugin<Configuration>, ILocationAutoConfigurer {

    @Inject
    private BrooklynLocationConfigurerFactory brooklynLocationConfigurerFactory;
    
    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return brooklynLocationConfigurerFactory.newInstance(locationType);
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
    public void launchWorkflow(PaaSDeploymentContext paaSDeploymentContext, String s, Map<String, Object> map, IPaaSCallback<?> iPaaSCallback) {
        // TODO: Determine whether or not this is required, and implement if required, otherwise no-op
        throw new UnsupportedOperationException("launchWorkflow is not currently supported");
    }
}
