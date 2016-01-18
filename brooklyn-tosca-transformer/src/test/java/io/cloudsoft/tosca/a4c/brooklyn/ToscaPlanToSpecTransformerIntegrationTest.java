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
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.Sensors;
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

import io.cloudsoft.tosca.a4c.Alien4CloudIntegrationTest;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

public class ToscaPlanToSpecTransformerIntegrationTest extends Alien4CloudIntegrationTest {

    public static final String TEMPLATES_FOLDER = "templates/";
    private String DATABASE_DEPENDENCY_INJECTION = "$brooklyn:formatString(\"jdbc:" +
            "%s%s?user=%s\\\\&password=%s\",$brooklyn:entity(\"mysql_server\")" +
            ".attributeWhenReady(\"datastore.url\")," +
            "visitors," +
            "brooklyn," +
            "br00k11n)";

    @Test
    public void testSimpleHostedTopologyParser() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "script1.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<?> hostVanilla = app.getChildren().get(0);
        assertEquals(hostVanilla.getConfig().get(SoftwareProcess.CHILDREN_STARTABLE_MODE),
                SoftwareProcess.ChildStartableMode.BACKGROUND_LATE);

        assertEquals(hostVanilla.getChildren().size(), 1);

        EntitySpec<?> hostedSoftwareComponent = hostVanilla.getChildren().get(0);

        assertEquals(hostVanilla.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(hostVanilla.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
        assertEquals(hostVanilla.getLocations().size(), 1);
        assertEquals(hostVanilla.getLocations().get(0).getDisplayName(), "localhost");

        assertEquals(hostedSoftwareComponent.getFlags().get("tosca.node.type"),
                "tosca.nodes.SoftwareComponent");
        assertEquals(hostedSoftwareComponent.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");

        assertConfigValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.INSTALL_COMMAND,
                "# install python if not present");
        assertConfigValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.CUSTOMIZE_COMMAND,
                "# create the web page to serve");
        assertConfigValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.LAUNCH_COMMAND,
                "# launch in background (ensuring no streams open), and record PID to file");
        assertConfigValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.STOP_COMMAND,
                "kill -9 `cat ${PID_FILE:-pid.txt}`");

    }

    @Test
    public void testDslInChatApplication() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "helloworld-sql.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<?> tomcatServer = ToscaPlanToSpecTransformer
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

        assertEquals(tomcatServer.getLocations().size(), 1, "Expected one location");
        assertTrue(tomcatServer.getLocations().get(0) instanceof LocalhostMachineProvisioningLocation);
    }

    @Test
    public void testFullJcloudsLocationDescription() {
        String templateUrl =
                getClasspathUrlForResource(TEMPLATES_FOLDER + "full-location.jclouds.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        EntitySpec<?> vanillaEntity = Iterables.getOnlyElement(app.getChildren());

        assertEquals(vanillaEntity.getLocations().size(), 1);
        Location location = Iterables.getOnlyElement(vanillaEntity.getLocations());
        assertTrue(location instanceof JcloudsLocation);
        assertEquals(((JcloudsLocation) location).getProvider(), "aws-ec2");
        assertEquals(((JcloudsLocation) location).getRegion(), "us-west-2");
        assertEquals(((JcloudsLocation) location).getIdentity(), "user-key-id");
        assertEquals(((JcloudsLocation) location).getCredential(), "user-key");
    }

    @Test
    public void testFullByonLocationDescription() {
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "full-location.byon.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        EntitySpec<?> vanillaEntity = Iterables.getOnlyElement(app.getChildren());

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

        Object machinesObj = configByon.get("machines");
        assertNotNull(machinesObj, "machines");
        List<?> machines = List.class.cast(machinesObj);
        assertFalse(machines.isEmpty(), "expected value for machines key in " + configByon);
        Object obj = machines.get(0);
        assertEquals(obj.getClass(), SshMachineLocation.class);
        SshMachineLocation sml = SshMachineLocation.class.cast(obj);
        assertEquals(sml.getAddress().getHostAddress(), "192.168.0.18");
    }

    @Test
    public void testRelation(){
        String templateUrl = getClasspathUrlForResource(TEMPLATES_FOLDER + "relationship.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<?> tomcatServer = ToscaPlanToSpecTransformer
                .findChildEntitySpecByPlanId(app, "tomcat_server");

        assertNotNull(tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS));
        assertEquals(((Map) tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS)).size(), 1);
        assertEquals(((Map)tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS))
                .get("brooklyn.example.db.url").toString(), DATABASE_DEPENDENCY_INJECTION);
    }

    @Test
    public void testAddingBrooklynPolicyToEntitySpec(){
        String templateUrl =
                getClasspathUrlForResource(TEMPLATES_FOLDER + "autoscaling.policies.tosca.yaml");

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        EntitySpec<?> cluster = ToscaPlanToSpecTransformer
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
            assertConfigValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND, "export PORT=3306");
            assertConfigValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND, "export DB_USER=martin");
            assertConfigValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND, "export DB_NAME=wordpress");

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

        EntitySpec<?> tomcatServer = ToscaPlanToSpecTransformer.findChildEntitySpecByPlanId(app, "tomcat_server");
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
            assertConfigValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND, "#OVERWRITTEN VALUE");

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

    @Test
    public void testConcatFunctionInTopology() {
        String templateUrl = getClasspathUrlForResource("templates/concat-function.yaml");
        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        Application app = this.mgmt.getEntityManager().createEntity(spec);

        assertEquals(app.getChildren().size(), 1);
        Entity entity = Iterators.getOnlyElement(app.getChildren().iterator());
        String value = entity.sensors().get(Sensors.newStringSensor("tosca.attribute.my_message"));
        assertEquals(value, "Message: It Works!");
    }


    private void assertConfigValueContains(EntitySpec<?> entity, ConfigKey<String> key, String needle) {
        String haystack = (String) entity.getConfig().get(key);
        assertConfigValueContains(haystack, needle);
    }

    private void assertConfigValueContains(Entity entity, ConfigKey<String> key, String needle) {
        String haystack = entity.config().get(key);
        assertConfigValueContains(haystack, needle);
    }

    private void assertConfigValueContains(String haystack, String needle) {
        if (needle == null || haystack == null) {
            throw new AssertionError("Expected non-null values: needle=" + needle + ", haystack=" + haystack);
        } else if (!haystack.contains(needle)) {
            throw new AssertionError("Expected to find '" + needle + "' in " + haystack);
        }
    }
}
