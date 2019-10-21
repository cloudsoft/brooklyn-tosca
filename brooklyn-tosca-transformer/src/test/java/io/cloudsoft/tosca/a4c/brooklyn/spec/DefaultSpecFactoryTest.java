package io.cloudsoft.tosca.a4c.brooklyn.spec;

import io.cloudsoft.tosca.a4c.brooklyn.Alien4CloudApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DefaultSpecFactoryTest extends BrooklynAppUnitTestSupport {

    @Mock
    private NodeTemplate nodeTemplate1;
    @Mock
    private ToscaFacade alien4CloudFacade;
    @Mock
    private Alien4CloudApplication toscaApplication;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMakesVanillaSoftwareProcessDerived() {
        when(toscaApplication.getNodeTemplate(Mockito.anyString())).thenReturn(nodeTemplate1);
        when(alien4CloudFacade.isDerivedFrom("Test", toscaApplication, "tosca.nodes.Compute")).thenReturn(false);
        when(nodeTemplate1.getType()).thenReturn("test");
        EntitySpecFactory<Alien4CloudApplication> factory = new Alien4CloudEntitySpecFactory(mgmt, alien4CloudFacade);
        assertEquals(factory.create("Test", toscaApplication).getType(), VanillaSoftwareProcess.class);
    }

    @Test
    public void testMakesSameServerEntityForDerivedFromCompute() {
        when(toscaApplication.getNodeTemplate(Mockito.anyString())).thenReturn(nodeTemplate1);
        when(alien4CloudFacade.isDerivedFrom("Test", toscaApplication, "tosca.nodes.Compute")).thenReturn(true);
        EntitySpecFactory<Alien4CloudApplication> factory = new Alien4CloudEntitySpecFactory(mgmt, alien4CloudFacade);
        assertEquals(factory.create("Test", toscaApplication).getType(), SameServerEntity.class);
    }
}
