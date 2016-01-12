package io.cloudsoft.tosca.a4c.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import io.cloudsoft.tosca.a4c.Alien4CloudToscaPlatform;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

public class ToscaPlanToSpecTransformerTest extends Alien4CloudToscaTest {

    protected ToscaPlanToSpecTransformer transformer;
    private Alien4CloudToscaPlatform platform;

    public static final String TEMPLATES_FOLDER = "templates/";
    private String DATABASE_DEPENDENCY_INJECTION= "$brooklyn:formatString(\"jdbc:" +
            "%s%s?user=%s\\\\&password=%s\",$brooklyn:entity(\"mysql_server\")" +
            ".attributeWhenReady(\"datastore.url\")," +
            "visitors," +
            "brooklyn," +
            "br00k11n)";

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        Alien4CloudToscaPlatform.grantAdminAuth();
        platform = Alien4CloudToscaPlatform.newInstance();
        mgmt.getBrooklynProperties().put(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM, platform);
        platform.loadNormativeTypes();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.setManagementContext(mgmt);
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (platform != null) {
            platform.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleHostedTopologyParser() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "script1.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<VanillaSoftwareProcess> hostVanilla =
                (EntitySpec<VanillaSoftwareProcess>) app.getChildren().get(0);
        assertEquals(hostVanilla.getConfig().get(SoftwareProcess.CHILDREN_STARTABLE_MODE),
                SoftwareProcess.ChildStartableMode.BACKGROUND_LATE);

        assertEquals(hostVanilla.getChildren().size(), 1);

        EntitySpec<VanillaSoftwareProcess> hostedSoftwareComponent =
                (EntitySpec<VanillaSoftwareProcess>) hostVanilla.getChildren().get(0);

        assertEquals(hostVanilla.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(hostVanilla.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
        assertEquals(hostVanilla.getLocations().size(), 1);
        assertEquals(hostVanilla.getLocations().get(0).getDisplayName(), "localhost");

        assertEquals(hostedSoftwareComponent.getFlags().get("tosca.node.type"),
                "tosca.nodes.SoftwareComponent");
        assertEquals(hostedSoftwareComponent.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");

        assertTrue(hostedSoftwareComponent.getConfig().get(VanillaSoftwareProcess.INSTALL_COMMAND)
                .toString().contains("# install python if not present"));
        assertTrue(hostedSoftwareComponent.getConfig().get(VanillaSoftwareProcess.CUSTOMIZE_COMMAND)
                .toString().contains("# create the web page to serve"));
        assertTrue(hostedSoftwareComponent.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND)
                .toString().contains("# launch in background (ensuring no streams open), and record PID to file"));
        assertTrue(hostedSoftwareComponent.getConfig().get(VanillaSoftwareProcess.STOP_COMMAND)
                .toString().contains("kill -9 `cat ${PID_FILE:-pid.txt}`"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDslInChatApplication() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "helloworld-sql.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<TomcatServer> tomcatServer =
                (EntitySpec<TomcatServer>) ToscaPlanToSpecTransformer
                        .findChildEntitySpecByPlanId(app, "tomcat_server");
        assertEquals(tomcatServer.getConfig().get(TomcatServer.ROOT_WAR),
                "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/" +
                "brooklyn-example-hello-world-sql-webapp/0.6.0/" +
                "brooklyn-example-hello-world-sql-webapp-0.6.0.war");
        assertNotNull(tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS));

        Map javaSysProps = (Map) tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS);
        assertEquals(javaSysProps.size(), 1);
        assertTrue(javaSysProps.get("brooklyn.example.db.url") instanceof BrooklynDslDeferredSupplier);
        assertEquals(javaSysProps.get("brooklyn.example.db.url").toString(), DATABASE_DEPENDENCY_INJECTION);

        assertTrue(tomcatServer.getLocations().get(0) instanceof LocalhostMachineProvisioningLocation);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFullJcloudsLocationDescription() {
        String templateUrl =
                getClasspathUrlForResource(TEMPLATES_FOLDER + "full-location.jclouds.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaEntity =
                (EntitySpec<VanillaSoftwareProcess>) Iterables.getOnlyElement(app.getChildren());

        assertEquals(vanillaEntity.getLocations().size(), 1);
        Location location = Iterables.getOnlyElement(vanillaEntity.getLocations());
        assertTrue(location instanceof JcloudsLocation);
        assertEquals(((JcloudsLocation) location).getProvider(), "aws-ec2");
        assertEquals(((JcloudsLocation) location).getRegion(), "us-west-2");
        assertEquals(((JcloudsLocation) location).getIdentity(), "user-key-id");
        assertEquals(((JcloudsLocation) location).getCredential(), "user-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFullByonLocationDescription() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "full-location.byon.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaEntity =
                (EntitySpec<VanillaSoftwareProcess>) Iterables.getOnlyElement(app.getChildren());

        assertEquals(vanillaEntity.getLocations().size(), 1);
        assertTrue(Iterables.getOnlyElement(vanillaEntity.getLocations())
                instanceof FixedListMachineProvisioningLocation);

        FixedListMachineProvisioningLocation location =
                (FixedListMachineProvisioningLocation) Iterables
                        .getOnlyElement(vanillaEntity.getLocations());
        Map<String, Object> configByon = location.getLocalConfigBag().getAllConfig();
        assertEquals(configByon.get("user"), "brooklyn");
        assertEquals(configByon.get("provider"), "byon");
        assertTrue(configByon.get("machines") instanceof Collection);
        assertEquals(((Collection)configByon.get("machines")).size(), 1);

        List<SshMachineLocation> machines = (List<SshMachineLocation>) configByon.get("machines");
        assertEquals(machines.get(0).getAddress().getHostAddress(), "192.168.0.18");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRelation(){
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "relationship.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<TomcatServer> tomcatServer =
                (EntitySpec<TomcatServer>) ToscaPlanToSpecTransformer
                        .findChildEntitySpecByPlanId(app, "tomcat_server");

        assertNotNull(tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS));
        assertEquals(((Map) tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS)).size(), 1);
        assertEquals(((Map)tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS))
                        .get("brooklyn.example.db.url").toString(), DATABASE_DEPENDENCY_INJECTION);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddingBrooklynPolicyToEntitySpec(){
        String templateUrl =
                getClasspathUrlForResource(TEMPLATES_FOLDER + "autoscaling.policies.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        EntitySpec<DynamicWebAppCluster> cluster =
                (EntitySpec<DynamicWebAppCluster>) ToscaPlanToSpecTransformer
                .findChildEntitySpecByPlanId(app, "cluster");

        assertEquals(cluster.getPolicySpecs().size(), 1);
        assertTrue(cluster.getPolicySpecs().get(0).getType().equals(AutoScalerPolicy.class));

        PolicySpec<?> autoScalerPolicy = cluster.getPolicySpecs().get(0);
        assertNotNull(autoScalerPolicy.getFlags());

        Map<String, ?> autoScalerPolicyFlags = autoScalerPolicy.getFlags();
        assertEquals(autoScalerPolicyFlags.size(), 5);
        assertEquals(autoScalerPolicyFlags.get("metricLowerBound"), "10");
        assertEquals(autoScalerPolicyFlags.get("metricUpperBound"), "100");
        assertEquals(autoScalerPolicyFlags.get("minPoolSize"), "1");
        assertEquals(autoScalerPolicyFlags.get("maxPoolSize"), "5");
        assertEquals(autoScalerPolicyFlags.get("metric"),"$brooklyn:sensor(" +
                "\"org.apache.brooklyn.entity.webapp.DynamicWebAppCluster\"," +
                " \"webapp.reqs.perSec.windowed.perNode\")" );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddingBrooklynPolicyToApplicationSpec(){
        String templateUrl =
                getClasspathUrlForResource(TEMPLATES_FOLDER + "simple.application-policies.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getPolicySpecs().size(), 1);
        assertTrue(app.getPolicySpecs().get(0).getType().equals(TestPolicy.class));

        PolicySpec<?> testPolicy = app.getPolicySpecs().get(0);
        assertNotNull(testPolicy.getFlags());

        Map<String, ?> testPolicyFlags = testPolicy.getFlags();
        assertEquals(testPolicyFlags.size(), 4);
        assertEquals(testPolicyFlags.get("policyLiteralValue1"), "Hello");
        assertEquals(testPolicyFlags.get("policyLiteralValue2"), "World");
        assertEquals(testPolicyFlags.get("test.confName"), "Name from YAML");
        assertEquals(testPolicyFlags.get("test.confFromFunction"),
                "$brooklyn:formatString(\"%s: is a fun place\", \"$brooklyn\")");
    }

    @Test
    public void testMysqlTopology() throws Exception {
        try {
            platform.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl("brooklyn-resources.yaml"), "brooklyn-resources");
            platform.loadTypesFromUrl(AlienSamplesLiveTest.ALIEN_SAMPLE_TYPES_GITHUB_URL, true);

            String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "mysql-topology.tosca");

            EntitySpec<?> spec = transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString(templateUrl));

            // Check the basic structure
            assertNotNull(spec, "spec");
            assertEquals(spec.getType(), BasicApplication.class);

            assertEquals(spec.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> compute = Iterators.getOnlyElement(spec.getChildren().iterator());
            assertEquals(compute.getType(), BasicApplication.class);

            assertEquals(compute.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> mysql = Iterators.getOnlyElement(compute.getChildren().iterator());
            assertEquals(mysql.getType(), VanillaSoftwareProcess.class);

            // Check the config has been set
            assertEquals(mysql.getConfig().get(ConfigKeys.newStringConfigKey("port")), "3306");
            assertEquals(mysql.getConfig().get(ConfigKeys.newStringConfigKey("db_user")), "martin");

            // Check that the inputs have been set as exports on the scripts
            assertTrue(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export PORT=3306"));
            assertTrue(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export DB_USER=martin"));
            assertTrue(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export DB_NAME=wordpress"));

        } finally {
            if (platform!=null) {
                platform.close();
            }
        }
    }

    @Test
    public void testDeploymentArtifacts() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "deployment-artifact.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<?> tomcatServer = ToscaPlanToSpecTransformer
                        .findChildEntitySpecByPlanId(app, "tomcat_server");
        assertEquals(tomcatServer.getConfig().get(TomcatServer.ROOT_WAR),
                "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/" +
                        "brooklyn-example-hello-world-sql-webapp/0.6.0/" +
                        "brooklyn-example-hello-world-sql-webapp-0.6.0.war");
    }

    @Test
    public void testOverwriteInterfaceOnMysqlTopology() throws Exception {
        try {

            platform.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl("brooklyn-resources.yaml"), "brooklyn-resources");
            platform.loadTypesFromUrl(AlienSamplesLiveTest.ALIEN_SAMPLE_TYPES_GITHUB_URL, true);

            String templateUrl =
                    getClasspathUrlForResource(TEMPLATES_FOLDER + "mysql-topology-overwritten-interface.tosca.yaml");

            EntitySpec<?> spec = transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString(templateUrl));

            // Check the basic structure
            assertNotNull(spec, "spec");
            assertEquals(spec.getType(), BasicApplication.class);

            assertEquals(spec.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> compute = Iterators.getOnlyElement(spec.getChildren().iterator());
            assertEquals(compute.getType(), BasicApplication.class);

            assertEquals(compute.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> mysql = Iterators.getOnlyElement(compute.getChildren().iterator());
            assertEquals(mysql.getType(), VanillaSoftwareProcess.class);


            // Check that the inputs have been set as exports on the scripts
            assertFalse(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export PORT=3361"));
            assertFalse(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export DB_USER=martin"));
            assertFalse(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("export DB_NAME=wordpress"));
            assertTrue(mysql.getConfig().get(VanillaSoftwareProcess.LAUNCH_COMMAND).toString().contains("#OVERWRITTEN VALUE"));

        } finally {
            if (platform!=null) {
                platform.close();
            }
        }
    }

    @Test(enabled = false) //failing to parse tosca
    public void testEntitiesOnSameNodeBecomeSameServerEntities() {
        String templateUrl = getClasspathUrlForResource("templates/tomcat-mysql-on-one-compute.yaml");

        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        Application app = this.mgmt.getEntityManager().createEntity(spec);

        assertEquals(app.getChildren().size(), 1);
        Entity appChild = Iterables.getOnlyElement(app.getChildren());
        assertTrue(appChild instanceof SameServerEntity, "Expected " + SameServerEntity.class.getName() + ", got " + appChild);

        assertEquals(appChild.getChildren().size(), 2);
        assertEquals(Iterables.size(Entities.descendants(appChild, MySqlNode.class)), 1,
                "expected " + MySqlNode.class.getName() + " in " + appChild.getChildren());
        assertEquals(Iterables.size(Entities.descendants(appChild, TomcatServer.class)), 1,
                "expected " + TomcatServer.class.getName() + " in " + appChild.getChildren());
    }

}
