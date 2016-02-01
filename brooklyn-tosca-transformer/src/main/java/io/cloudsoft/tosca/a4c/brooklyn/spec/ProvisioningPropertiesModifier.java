package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class ProvisioningPropertiesModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningPropertiesModifier.class);

    @Inject
    public ProvisioningPropertiesModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        LOG.info("Applying provisioning properties to " + entitySpec);
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_RAM, resolveProperty(nodeId, toscaApplication, "mem_size"));
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_DISK, resolveProperty(nodeId, toscaApplication, "disk_size"));
        setSubKey(entitySpec, JcloudsLocationConfig.MIN_CORES, resolveProperty(nodeId, toscaApplication, "num_cpus"));
        setSubKey(entitySpec, JcloudsLocationConfig.OS_FAMILY, resolveProperty(nodeId, toscaApplication, "os_distribution"));
        setSubKey(entitySpec, JcloudsLocationConfig.OS_VERSION_REGEX, resolveProperty(nodeId, toscaApplication, "os_version"));
        // TODO: Mapping for "os_arch" and "os_type" are missing
    }

    private Object resolveProperty(String nodeId, ToscaApplication toscaApplication, String key) {
        return getToscaFacade().resolveProperty(nodeId, toscaApplication, key);
    }

    private void setSubKey(EntitySpec<?> spec, ConfigKey<?> configKey, Object value) {
        if (value != null) {
            final ConfigKey<Object> key = SoftwareProcess.PROVISIONING_PROPERTIES.subKey(configKey.getName());
            spec.configure(key, value);
        }
    }
}
