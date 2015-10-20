package org.apache.brooklyn.tosca.a4c.brooklyn;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.tosca.a4c.Alien4CloudToscaTest;
import org.apache.brooklyn.util.core.ResourceUtils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ToscaPlanToSpecTransformerTest extends Alien4CloudToscaTest {

    protected ToscaPlanToSpecTransformer transformer;

    @BeforeMethod
    public void setup() throws Exception {
        super.setup();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.injectManagementContext(getMgmt());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleHostedTopologyParser() {
        String templateUrl = getClasspathUrlForResource("templates/script1.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<VanillaSoftwareProcess> hostVanilla =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);
        assertEquals(hostVanilla.getChildren().size(), 1);

        EntitySpec<VanillaSoftwareProcess> hostedSoftwareComponent =
                (EntitySpec<VanillaSoftwareProcess>)hostVanilla.getChildren().get(0);

        assertEquals(hostVanilla.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(hostVanilla.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
        assertEquals(hostVanilla.getLocations().size(), 1);
        assertEquals(hostVanilla.getLocations().get(0).getDisplayName(), "localhost");

        assertEquals(hostedSoftwareComponent.getFlags().get("tosca.node.type"),
                "tosca.nodes.SoftwareComponent");
        assertEquals(hostedSoftwareComponent.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
    }

    //TODO probably this test could be moved to a new class, e.g. customTypeTransformationTests
    @Test
    @SuppressWarnings("unchecked")
    public void testCustomTomcatHostedTransformation() {
        String templateUrl = getClasspathUrlForResource("templates/customTomcat-Hosted.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<VanillaSoftwareProcess> hostVanilla =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);
        assertEquals(hostVanilla.getChildren().size(), 1);

        EntitySpec<TomcatServer> hostedTomcat =
                (EntitySpec<TomcatServer>)hostVanilla.getChildren().get(0);

        assertEquals(hostVanilla.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(hostVanilla.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
        assertEquals(hostVanilla.getLocations().size(), 1);
        assertEquals(hostVanilla.getLocations().get(0).getDisplayName(), "localhost");

        assertEquals(hostedTomcat.getFlags().get("tosca.node.type"),
                "org.apache.brooklyn.entity.webapp.tomcat.TomcatServer");
        assertEquals(
                hostedTomcat.getType().getName(),
                hostedTomcat.getFlags().get("tosca.node.type"));
        assertEquals(hostedTomcat.getConfig().get(TomcatServer.ROOT_WAR),"file://app.war");
    }

    @Test
    public void testCustomMySQLTransformation(){
        String templateUrl = getClasspathUrlForResource("templates/customMySQL.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<MySqlNode> mySqlNodeEntitySpec =
                (EntitySpec<MySqlNode>)app.getChildren().get(0);

        assertEquals(mySqlNodeEntitySpec.getFlags().get("tosca.node.type"),
                "org.apache.brooklyn.entity.database.mysql.MySqlNode");
        assertEquals(mySqlNodeEntitySpec.getType().getName(),
                "org.apache.brooklyn.entity.database.mysql.MySqlNode");
        assertEquals(mySqlNodeEntitySpec.getLocations().size(), 1);
        assertEquals(mySqlNodeEntitySpec.getLocations().get(0).getDisplayName(), "localhost");
        assertEquals(
                mySqlNodeEntitySpec.getConfig().get(MySqlNode.CREATION_SCRIPT_URL),
                "http://script.sql");
    }


}
