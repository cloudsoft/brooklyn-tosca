package org.apache.brooklyn.tosca.a4c;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import alien4cloud.utils.AlienYamlPropertiesFactoryBeanFactory;

/**
 * Factory to create a {@link AlienBrooklynYamlPropertiesFactoryBeanFactory} singleton.
 */
public class AlienBrooklynYamlPropertiesFactoryBeanFactory {
    
    private static final Logger log = LoggerFactory.getLogger(AlienBrooklynYamlPropertiesFactoryBeanFactory.class);
    
    // NB: this doesn't change where ES loads config from; it uses elasticsearch.yml (and logging.yml) in its conf dir
    // (or classpath?); that can be overridden using `es.config`
    // (however this shouldn't be confused with elasticsearchConfig which *is* the same as a4c-config,
    // referring to an es block therein and defining a handful of properties used by luc's fork)
    private static final ConfigKey<String> ALIEN_CONFIG_FILE = ConfigKeys.newStringConfigKey("alien4cloud-config.file",
        "URL or classpath or file of the alien4cloud-config.yml file containing the A4C configuration, "
        + "including the address of the ES instance to use. "
        + "If not defined, a default in-memory instance will be used.");
    
    private static YamlPropertiesFactoryBean INSTANCE;

    /**
     * Get a singleton instance of {@link YamlPropertiesFactoryBean}.
     * @param mgmt 
     * 
     * @param resourceLoader The loader to use to find the yaml file.
     * @return an instance of the {@link YamlPropertiesFactoryBean}.
     */
    public static YamlPropertiesFactoryBean get(ManagementContext mgmt, ResourceLoader resourceLoader) {
        if (INSTANCE == null) {
            if (mgmt!=null) {
                String configFile = mgmt.getConfig().getConfig(ALIEN_CONFIG_FILE);
                if (Strings.isNonBlank(configFile)) {
                    log.info("Loading A4C config from "+configFile);
                    Resource resource = new InputStreamResource(new ResourceUtils(AlienBrooklynYamlPropertiesFactoryBeanFactory.class).getResourceFromUrl(configFile),
                        configFile);
                    INSTANCE = new YamlPropertiesFactoryBean();
                    INSTANCE.setResources(new Resource[] { resource });
                    return INSTANCE;
                }
            }
            INSTANCE = AlienYamlPropertiesFactoryBeanFactory.get(resourceLoader);
        }
        return INSTANCE;
    }
}
