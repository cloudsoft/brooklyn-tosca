package org.apache.brooklyn.tosca.a4c.transformer;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ToscaPlanToSpecTransformerTest extends AbstractPlanToSpecTransformerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testComputeTopologyParser(){
        String templateUrl = getClasspathUrlForTemplateResource(COMPUTE_TEMPLATE);

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaSpec =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);
        assertEquals(vanillaSpec.getType().getName(),
                VANILLA_SP_TYPE);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTomcatTopologyParser() {
        String templateUrl = getClasspathUrlForTemplateResource(TOMCAT_TEMPLATE);

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getChildren().size(), 1);
        EntitySpec<TomcatServer> tomcatSpec =
                (EntitySpec<TomcatServer>)app.getChildren().get(0);
        assertEquals(tomcatSpec.getType().getName(),
                TOMCAT_NODETYPE);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testComputeLocTopologyParser() {
        String templateUrl = getClasspathUrlForTemplateResource(COMPUTELOC_TEMPLATE);

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaSpec =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);
        assertEquals(vanillaSpec.getType().getName(),
                SAMESERVER_TYPE);
    }


}
