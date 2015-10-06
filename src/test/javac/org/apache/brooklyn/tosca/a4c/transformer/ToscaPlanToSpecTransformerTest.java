package org.apache.brooklyn.tosca.a4c.transformer;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudToscaTest;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ToscaPlanToSpecTransformerTest extends AbstractAlien4CloudToscaTest{

    String TEMPLATE = "templates/tomcat-computeloc-template.yaml";

    private ManagementContext mgmt;

    @BeforeMethod
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @SuppressWarnings("unchecked")
    @Test(groups = {"Integration"})
    public void testTomcatTopologyParser() {

        ToscaPlanToSpecTransformer transformer = new ToscaPlanToSpecTransformer();
        transformer.injectManagementContext(mgmt);
        String templateUrl = getClasspathUrlForResource(TEMPLATE);

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaSpec =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);

        assertEquals(vanillaSpec.getChildren().size(), 1);
        EntitySpec<TomcatServer> tomcatSpec = (EntitySpec<TomcatServer>) vanillaSpec.getChildren().get(0);

        assertEquals(tomcatSpec.getFlags().get("tosca.node.type"), TOMCATSERVER_NODETYPE_ID);

    }

    public String getClasspathUrlForResource(String resourceName){
        return "classpath://"+ resourceName;
    }



}
