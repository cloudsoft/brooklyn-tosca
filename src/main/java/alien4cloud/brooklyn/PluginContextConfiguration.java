package alien4cloud.brooklyn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Plugin spring configuration entry point.
 */
@Configuration
@ComponentScan(basePackages = { "alien4cloud.brooklyn" })
public class PluginContextConfiguration {
}