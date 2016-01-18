package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

@Component
public class ProvisioningPropertiesModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningPropertiesModifier.class);

    @Inject
    public ProvisioningPropertiesModifier(ManagementContext mgmt) {
        super(mgmt);
    }

    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        LOG.info("Applying provisioning properties to " + entitySpec);
        Map<String, AbstractPropertyValue> properties = nodeTemplate.getProperties();
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_RAM, resolveProperty(properties, "mem_size"));
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_DISK, resolveProperty(properties, "disk_size"));
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_CORES, resolveProperty(properties, "num_cpus"));
        setSubKey(entitySpec, JcloudsLocationConfig.OS_FAMILY, resolveProperty(properties, "os_distribution"));
        setSubKey(entitySpec, JcloudsLocationConfig.OS_VERSION_REGEX, resolveProperty(properties, "os_version"));
        // TODO: Mapping for "os_arch" and "os_type" are missing
    }

    private Object resolveProperty(Map<String, AbstractPropertyValue> properties, String key) {
        return resolve(properties, key).orNull();
    }

    private void setSubKey(EntitySpec<?> spec, ConfigKey<?> configKey, Object value) {
        if (value != null) {
            final ConfigKey<Object> key = SoftwareProcess.PROVISIONING_PROPERTIES.subKey(configKey.getName());
            spec.configure(key, value);
        }
    }

}
