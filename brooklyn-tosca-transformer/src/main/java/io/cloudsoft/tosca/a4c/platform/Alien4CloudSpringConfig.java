package io.cloudsoft.tosca.a4c.platform;

import java.io.IOException;
import java.util.Properties;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ResourceLoader;

import alien4cloud.security.ResourceRoleService;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
        "alien4cloud",
        "org.elasticsearch.mapping",
        "io.cloudsoft.tosca.a4c.brooklyn"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io.cloudsoft.tosca.a4c.brooklyn.spec.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "alien4cloud.security.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "alien4cloud.audit.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "alien4cloud.ldap.*")})
public class Alien4CloudSpringConfig {

    @Bean
    public ResourceRoleService getDummyRRS() {
        return new ResourceRoleService();
    }

    // A4C code returns the YamlPropertiesFactoryBean, but that causes warnings at startup
    @Bean(name = {"alienconfig", "elasticsearchConfig"})
    public static Properties alienConfig(BeanFactory beans, ResourceLoader resourceLoader) throws IOException {
        ManagementContext mgmt = null;
        if (beans.containsBean("brooklynManagementContext")) {
            mgmt = beans.getBean("brooklynManagementContext", ManagementContext.class);
        }
        return AlienBrooklynYamlPropertiesFactoryBeanFactory.get(mgmt, resourceLoader).getObject();
    }

}
