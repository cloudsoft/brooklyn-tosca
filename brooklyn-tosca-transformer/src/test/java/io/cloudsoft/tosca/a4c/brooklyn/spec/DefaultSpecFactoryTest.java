package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import alien4cloud.model.topology.NodeTemplate;
import io.cloudsoft.tosca.a4c.brooklyn.Alien4CloudApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

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
        EntitySpecFactory factory = new Alien4CloudEntitySpecFactory(mgmt, alien4CloudFacade);
        assertEquals(factory.create("Test", toscaApplication, false).getType(), VanillaSoftwareProcess.class);
    }

    @Test
    public void testMakesBasicApplicationForDerivedFromCompute() {
        when(toscaApplication.getNodeTemplate(Mockito.anyString())).thenReturn(nodeTemplate1);
        when(alien4CloudFacade.isDerivedFrom("Test", toscaApplication, "tosca.nodes.Compute")).thenReturn(true);
        EntitySpecFactory factory = new Alien4CloudEntitySpecFactory(mgmt, alien4CloudFacade);
        assertEquals(factory.create("Test", toscaApplication, false).getType(), BasicApplication.class);
    }

    @Test
    public void testMakesSameServerEntityForDerivedFromCompute() {
        when(toscaApplication.getNodeTemplate(Mockito.anyString())).thenReturn(nodeTemplate1);
        when(alien4CloudFacade.isDerivedFrom("Test", toscaApplication, "tosca.nodes.Compute")).thenReturn(true);
        EntitySpecFactory factory = new Alien4CloudEntitySpecFactory(mgmt, alien4CloudFacade);
        boolean hasMultipleChildren = true;
        assertEquals(factory.create("Test", toscaApplication, hasMultipleChildren).getType(), SameServerEntity.class);
    }
}
