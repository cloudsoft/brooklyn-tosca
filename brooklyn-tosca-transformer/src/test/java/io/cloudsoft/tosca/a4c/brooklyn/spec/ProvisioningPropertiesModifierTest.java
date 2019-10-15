package io.cloudsoft.tosca.a4c.brooklyn.spec;

import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.Alien4CloudApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ProvisioningPropertiesModifierTest extends Alien4CloudToscaTest {

    @Mock
    private ToscaFacade alien4CloudFacade;
    @Mock
    private Alien4CloudApplication toscaApplication;
    @Mock
    private NodeTemplate nodeTemplate;

    @BeforeClass
    public void initMocks(){
        MockitoAnnotations.initMocks(this);
    }

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
        Mockito.when(alien4CloudFacade.resolveProperty(
                Mockito.anyString(), Mockito.any(ToscaApplication.class), Mockito.anyString())).thenReturn(value);
        Mockito.when(toscaApplication.getNodeTemplate(Mockito.anyString())).thenReturn(nodeTemplate);
        ProvisioningPropertiesModifier builder = new ProvisioningPropertiesModifier(mgmt, alien4CloudFacade);
        builder.apply(testSpec, "Test", toscaApplication);

        TestEntity entity = app.createAndManageChild(testSpec);
        assertEquals(entity.config().get(SoftwareProcess.PROVISIONING_PROPERTIES)
                .get(configKey.getName()), value);
    }


}
