package io.cloudsoft.tosca.a4c.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.stream.Streams;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.utils.FileUtil;
import io.cloudsoft.tosca.a4c.Alien4CloudIntegrationTest;
import io.cloudsoft.tosca.a4c.brooklyn.util.EntitySpecs;

public class ToscaPlanToSpecTransformerIntegrationTest extends Alien4CloudIntegrationTest {

    private String DATABASE_DEPENDENCY_INJECTION = "$brooklyn:formatString(\"jdbc:" +
            "%s%s?user=%s\\\\&password=%s\",$brooklyn:entity(\"mysql_server\")" +
            ".attributeWhenReady(\"datastore.url\")," +
            "visitors," +
            "brooklyn," +
            "br00k11n)";

    @Test
    public void testSimpleHostedTopologyParser() {
        String templateUrl = "classpath://templates/simple-web-server.yaml";

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);

        EntitySpec<?> server = app.getChildren().get(0);
        assertEquals(server.getConfig().get(SoftwareProcess.CHILDREN_STARTABLE_MODE),
                SoftwareProcess.ChildStartableMode.BACKGROUND_LATE);

        assertEquals(server.getChildren().size(), 1);

        EntitySpec<?> hostedSoftwareComponent = server.getChildren().get(0);

        assertEquals(server.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(server.getType(), SameServerEntity.class);
        assertEquals(server.getLocations().size(), 1);
        assertEquals(server.getLocations().get(0).getDisplayName(), "localhost");

        assertEquals(hostedSoftwareComponent.getFlags().get("tosca.node.type"),
                "tosca.nodes.SoftwareComponent");
        assertEquals(hostedSoftwareComponent.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");

        assertFlagValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.INSTALL_COMMAND.getName(),
                "# install python if not present");
        assertFlagValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.CUSTOMIZE_COMMAND.getName(),
                "# create the web page to serve");
        assertFlagValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.LAUNCH_COMMAND.getName(),
                "# launch in background (ensuring no streams open), and record PID to file");
        assertFlagValueContains(hostedSoftwareComponent, VanillaSoftwareProcess.STOP_COMMAND.getName(),
                "kill -9 `cat ${PID_FILE:-pid.txt}`");
    }

    @Test
    public void testDslInChatApplication() {
        String templateUrl = "classpath://templates/helloworld-sql.tosca.yaml";

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<?> tomcatServer = EntitySpecs
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
        String templateUrl = "classpath://templates/full-location.jclouds.tosca.yaml";

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
        String templateUrl = "classpath://templates/full-location.byon.tosca.yaml";

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
    public void testRelation() throws ParsingException, CSARVersionAlreadyExistsException, IOException {

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(makeOutputPath("relationship.yaml", "relation", "test.sh"));

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 2);

        EntitySpec<?> tomcatServer = EntitySpecs
                .findChildEntitySpecByPlanId(app, "tomcat_server");

        assertNotNull(tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS));
        assertEquals(((Map) tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS)).size(), 1);
        assertEquals(((Map)tomcatServer.getConfig().get(TomcatServer.JAVA_SYSPROPS))
                .get("brooklyn.example.db.url").toString(), DATABASE_DEPENDENCY_INJECTION);

        assertFlagValueContains(tomcatServer, VanillaSoftwareProcess.PRE_CUSTOMIZE_COMMAND.getName(),
                "echo It works!");

    }

    private Path makeOutputPath(String yamlFile, String scriptsFolder, String script) throws IOException {
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        File subfolder = new File(tempDir.getAbsolutePath() + "/scripts");
        subfolder.mkdir();

        Streams.copy(new ResourceUtils(this).getResourceFromUrl("classpath://templates/" + yamlFile),
                new FileOutputStream(tempDir.toString() + "/" + yamlFile));
        Streams.copy(new ResourceUtils(this).getResourceFromUrl("classpath://scripts/" + scriptsFolder + "/" + script),
                new FileOutputStream(subfolder.toString() + "/" + script));

        Path outputPath = java.nio.file.Files.createTempFile("temp", ".zip");
        FileUtil.zip(tempDir.toPath(), outputPath);
        return outputPath;
    }

    @Test
    public void testAddingBrooklynPolicyToEntitySpec() {
        String templateUrl = "classpath://templates/autoscaling.policies.tosca.yaml";

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);
        EntitySpec<?> cluster = EntitySpecs
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
        assertEquals(autoScalerPolicyFlags.get("metric"), "$brooklyn:sensor(" +
                "\"org.apache.brooklyn.entity.webapp.DynamicWebAppCluster\"," +
                " \"webapp.reqs.perSec.windowed.perNode\")");
    }

    @Test
    public void testAddingBrooklynPolicyToApplicationSpec(){
        String templateUrl = "classpath://templates/simple.application-policies.tosca.yaml";

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
            String templateUrl = "classpath://templates/mysql-topology.tosca.yaml";

            EntitySpec<?> spec = transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString(templateUrl));

            // Check the basic structure
            assertNotNull(spec, "spec");
            assertEquals(spec.getType(), BasicApplication.class);

            assertEquals(spec.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> compute = Iterators.getOnlyElement(spec.getChildren().iterator());
            assertEquals(compute.getType(), SameServerEntity.class);

            assertEquals(compute.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> mysql = Iterators.getOnlyElement(compute.getChildren().iterator());
            assertEquals(mysql.getType(), VanillaSoftwareProcess.class);

            // Check the config has been set
            assertEquals(mysql.getConfig().get(ConfigKeys.newStringConfigKey("port")), "3306");
            assertEquals(mysql.getConfig().get(ConfigKeys.newStringConfigKey("db_user")), "martin");

            // Check that the inputs have been set as exports on the scripts
            assertFlagValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND.getName(), "export PORT=\"3306\"");
            assertFlagValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND.getName(), "export DB_USER=\"martin\"");
            assertFlagValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND.getName(), "export DB_NAME=\"wordpress\"");

        } finally {
            if (platform!=null) {
                platform.close();
            }
        }
    }

    @Test
    public void testMongoTopology() throws Exception {
        try {
            String templateUrl = "classpath://templates/mongo-topology.tosca.yaml";
            EntitySpec<?> spec = transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString(templateUrl));

            // Check the basic structure
            assertNotNull(spec, "spec");
            assertEquals(spec.getType(), BasicApplication.class);

            assertEquals(spec.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> compute = Iterators.getOnlyElement(spec.getChildren().iterator());
            assertEquals(compute.getType(), SameServerEntity.class);

            assertEquals(compute.getChildren().size(), 1, "Expected exactly one child of compute entity");
            EntitySpec<?> mongo = Iterators.getOnlyElement(compute.getChildren().iterator());
            assertEquals(mongo.getType(), VanillaSoftwareProcess.class);

            // Check that the inputs have been set as exports on the scripts
            String customize = mongo.getFlags().get(VanillaSoftwareProcess.CUSTOMIZE_COMMAND.getName()).toString();
            assertConfigValueContains(customize, "DB_IP");
            assertConfigValueContains(customize, "$brooklyn:entity(\"Compute\").attributeWhenReady(\"ip_address\")");
        } finally {
            if (platform!=null) {
                platform.close();
            }
        }
    }

    // FIXME: Rework along with RuntimeEnvironmentModifier
    @Test(enabled = false)
    public void testDeploymentArtifacts() {
        String templateUrl = "classpath://templates/deployment-artifact.tosca.yaml";
        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        assertEquals(spec.getChildren().size(), 1);

        EntitySpec<?> tomcatServer = EntitySpecs.findChildEntitySpecByPlanId(spec, "tomcat_server");
        assertEquals(tomcatServer.getConfig().get(TomcatServer.ROOT_WAR),
                "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/" +
                        "brooklyn-example-hello-world-sql-webapp/0.6.0/" +
                        "brooklyn-example-hello-world-sql-webapp-0.6.0.war");

        Application app = this.mgmt.getEntityManager().createEntity(spec);
        ((BasicApplication) app).start(Collections.<Location>emptyList());
        EntityAsserts.assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

    // TODO Do not need to use expensive mysql-topology blueprint to test overwriting interfaces.
    @Test
    public void testOverwriteInterfaceOnMysqlTopology() throws Exception {
        try {
            String templateUrl = "classpath://templates/mysql-topology-overwritten-interface.tosca.yaml";

            EntitySpec<?> spec = transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString(templateUrl));

            // Check the basic structure
            assertNotNull(spec, "spec");
            assertEquals(spec.getType(), BasicApplication.class);

            assertEquals(spec.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> compute = Iterators.getOnlyElement(spec.getChildren().iterator());
            assertEquals(compute.getType(), SameServerEntity.class);

            assertEquals(compute.getChildren().size(), 1, "Expected exactly one child of root application");
            EntitySpec<?> mysql = Iterators.getOnlyElement(compute.getChildren().iterator());
            assertEquals(mysql.getType(), VanillaSoftwareProcess.class);

            // Check that the inputs have been set as exports on the scripts
            assertFlagValueContains(mysql, VanillaSoftwareProcess.LAUNCH_COMMAND.getName(), "#OVERWRITTEN VALUE");

        } finally {
            if (platform!=null) {
                platform.close();
            }
        }
    }

    @Test(enabled = false) //failing to parse tosca
    public void testEntitiesOnSameNodeBecomeSameServerEntities() {
        String templateUrl = "classpath://templates/tomcat-mysql-on-one-compute.yaml";

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
        String templateUrl = "classpath://templates/concat-function.yaml";
        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        Application app = this.mgmt.getEntityManager().createEntity(spec);

        assertEquals(app.getChildren().size(), 1);
        Entity entity = Iterators.getOnlyElement(app.getChildren().iterator());
        String value = entity.sensors().get(Sensors.newStringSensor("my_message"));
        assertEquals(value, "Message: It Works!");
    }

    @Test
    public void testGetAttributeFunctionInTopology() {
        String templateUrl = "classpath://templates/get_attribute-function.yaml";
        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        Application app = this.mgmt.getEntityManager().createEntity(spec);
        assertEquals(app.getChildren().size(), 1);
        Entity entity = Iterators.getOnlyElement(app.getChildren().iterator());
        final String expected = "Message: Hello";
        EntityAsserts.assertAttributeEqualsEventually(entity, Sensors.newStringSensor("my_message"), expected);
        EntityAsserts.assertPredicateEventuallyTrue(entity, new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity entity) {
                return entity.config().get(ConfigKeys.newStringConfigKey("another")).equals(expected);
            }
        });
    }

    @Test
    public void testResolvesKeywordInFunction() {
        String templateUrl = "classpath://templates/resolve-keyword-function.yaml";
        EntitySpec<? extends Application> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(spec);
        Application app = this.mgmt.getEntityManager().createEntity(spec);
        assertEquals(app.getChildren().size(), 1);
        Entity entity = Iterators.getOnlyElement(app.getChildren().iterator());
        final String expected = "Message: Hello";
        EntityAsserts.assertAttributeEqualsEventually(entity, Sensors.newStringSensor("my_message"), expected);
        EntityAsserts.assertPredicateEventuallyTrue(entity, new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity entity) {
                return entity.config().get(ConfigKeys.newStringConfigKey("another")).equals(expected);
            }
        });
    }

    private void assertFlagValueContains(EntitySpec<?> entity, String key, String needle) {
        String haystack = String.valueOf(entity.getFlags().get(key));
        assertConfigValueContains(haystack, needle);
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

    @Test
    public void testTransformationFromComputeWithTwoChildrenToSameServer() {
        String templateUrl = "classpath://templates/compute-with-two-hosted-children.yaml";
        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));
        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        EntitySpec<?> compute = Iterators.getOnlyElement(app.getChildren().iterator());
        assertNotNull(compute);
        assertEquals(compute.getType(), SameServerEntity.class);
    }

}
