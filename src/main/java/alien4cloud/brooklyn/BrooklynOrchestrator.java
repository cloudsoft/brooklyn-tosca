package alien4cloud.brooklyn;

import java.util.List;
import java.util.Map;

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
    
    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return new ILocationConfiguratorPlugin() {

            @Override
            public List<PluginArchive> pluginArchives() {
                return Lists.newArrayList();
            }

            @Override
            public List<String> getResourcesTypes() {
                return Lists.newArrayList();
            }

            @Override
            public Map<String, MatchingConfiguration> getMatchingConfigurations() {
                return Maps.newHashMap();
            }

            @Override
            public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
                return Lists.newArrayList();
            }
        };
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

}
