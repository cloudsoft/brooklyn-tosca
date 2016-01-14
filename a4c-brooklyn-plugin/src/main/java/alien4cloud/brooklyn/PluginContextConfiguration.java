package alien4cloud.brooklyn;

import io.cloudsoft.tosca.metadata.BrooklynToscaTypeProvider;
import io.cloudsoft.tosca.metadata.DefaultToscaTypeProvider;
import io.cloudsoft.tosca.metadata.ToscaTypeProvider;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Plugin spring configuration entry point.
 */
@Configuration
@ComponentScan(basePackages = { "alien4cloud.brooklyn" })
public class PluginContextConfiguration {

    @Bean
    public DefaultToscaTypeProvider defaultToscaTypeProvider() {
        return new DefaultToscaTypeProvider();
    }

    @Bean
    public BrooklynToscaTypeProvider brooklynToscaTypeProvider() {
        return new BrooklynToscaTypeProvider();
    }
}