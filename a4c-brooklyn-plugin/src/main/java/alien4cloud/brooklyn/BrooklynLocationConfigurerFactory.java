package alien4cloud.brooklyn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;

@Component
public class BrooklynLocationConfigurerFactory {
    @Inject
    private ApplicationContext applicationContext;

    public ILocationConfiguratorPlugin newInstance(String locationType) {
        if (BrooklynOrchestratorFactory.BROOKLYN.equals(locationType)) {
            return applicationContext.getBean(BrooklynLocationConfigurer.class);
        }
        return new ILocationConfiguratorPlugin() {
            @Override
            public List<PluginArchive> pluginArchives() {
                return new ArrayList<>();
            }

            @Override
            public List<String> getResourcesTypes() {
                return new ArrayList<>();
            }

            @Override
            public Map<String, MatchingConfiguration> getMatchingConfigurations() {
                return new HashMap<>();
            }

            @Override
            public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
                return null;
            }
        };
    }
}
