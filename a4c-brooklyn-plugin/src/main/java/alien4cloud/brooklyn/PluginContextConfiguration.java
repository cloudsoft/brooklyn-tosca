package alien4cloud.brooklyn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.cloudsoft.tosca.metadata.BrooklynToscaTypeProvider;
import io.cloudsoft.tosca.metadata.DefaultToscaTypeProvider;

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