package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

public class ProvisioningPropertiesModifierTest extends Alien4CloudToscaTest {

    @DataProvider(name = "mapping")
    public Object[][] mappings() {
        return new Object[][]{
                {"mem_size", JcloudsLocationConfig.MIN_RAM, "minRam"},
                {"disk_size", JcloudsLocationConfig.MIN_DISK, "minDisk"},
                {"num_cpus", JcloudsLocationConfig.MIN_CORES, "minCores"},
                {"os_distribution", JcloudsLocationConfig.OS_FAMILY, "osDistribution"},
                {"os_version", JcloudsLocationConfig.OS_VERSION_REGEX, "osVersion"},
        };
    }

    @Test(dataProvider = "mapping")
    public void testSetsProperty(String templateProperty, ConfigKey<?> configKey, String value) {
        nodeTemplate.setProperties(ImmutableMap.<String, AbstractPropertyValue>of(
                templateProperty, new ScalarPropertyValue(value)));
        ProvisioningPropertiesModifier builder = new ProvisioningPropertiesModifier(mgmt);
        builder.apply(testSpec, nodeTemplate, topology);

        TestEntity entity = app.createAndManageChild(testSpec);
        assertEquals(entity.config().get(SoftwareProcess.PROVISIONING_PROPERTIES)
                .get(configKey.getName()), value);
    }


}
