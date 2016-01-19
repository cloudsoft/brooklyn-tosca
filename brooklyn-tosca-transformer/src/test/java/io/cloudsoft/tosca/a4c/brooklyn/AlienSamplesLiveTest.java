package io.cloudsoft.tosca.a4c.brooklyn;

import static org.apache.brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;

import com.google.common.collect.ImmutableList;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaLiveTest;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudSpringContext;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class AlienSamplesLiveTest extends Alien4CloudToscaLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(AlienSamplesLiveTest.class);
    private static final String DEFAULT_LOCATION_SPEC = "aws-ec2:eu-west-1";
    private static String RESOURCE_LOC = "classpath://templates";
    public static final String ALIEN_SAMPLE_TYPES_GITHUB_URL = "https://github.com/alien4cloud/samples/archive/1.1.0-SM8.zip";

    protected ToscaPlanToSpecTransformer transformer;
    private Alien4CloudToscaPlatform platform;
    private String locationSpec;

    @Parameters({"locationSpec"})
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional String locationSpec) throws Exception {
        super.setUp();
        this.locationSpec = !Strings.isBlank(locationSpec) ? locationSpec : DEFAULT_LOCATION_SPEC;
        Alien4CloudToscaPlatform.grantAdminAuth();
        ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(mgmt);
        platform = applicationContext.getBean(Alien4CloudToscaPlatform.class);
        mgmt.getBrooklynProperties().put(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM, platform);
        platform.loadNormativeTypes();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.setManagementContext(mgmt);
        platform.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl("brooklyn-resources.yaml"), "brooklyn-resources");
    }


    @Test(groups = "Live")
    public void testMySql() throws Exception {
        try {
            Location testLocation = mgmt.getLocationRegistry().resolve(this.locationSpec);
            testMySql(app, testLocation);
        } finally {
            if (platform != null) {
                platform.close();
            }
        }
    }

    public void testMySql(TestApplication app, Location testLocation) throws Exception {
        Entity entity = createAndManageFrom("mysql.zip", "mysql-topology.tosca", app);
        app.start(ImmutableList.of(testLocation));
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
    }

    @Test(groups = "Live")
    public void testPhp() throws Exception {
        try {
            Location testLocation = mgmt.getLocationRegistry().resolve(this.locationSpec);
            testPhp(app, testLocation);
        } finally {
            if (platform != null) {
                platform.close();
            }
        }
    }

    public void testPhp(TestApplication app, Location testLocation) throws Exception {
        Entity entity = createAndManageFrom("php.zip", "php-topology.tosca", app);
        app.start(ImmutableList.of(testLocation));
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
    }

    @Test(groups = "Live")
    public void testApache() throws Exception {
        try {
            Location testLocation = mgmt.getLocationRegistry().resolve(this.locationSpec);
            testApache(app, testLocation);
        } finally {
            if (platform != null) {
                platform.close();
            }
        }
    }

    public void testApache(TestApplication app, Location testLocation) throws Exception {
        Entity entity = createAndManageFrom("apache.zip", "apache-topology.tosca", app);
        app.start(ImmutableList.of(testLocation));
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
    }

    public Entity createAndManageFrom(String zipName, String template, TestApplication app) throws Exception {
        platform.loadTypesFromUrl(ALIEN_SAMPLE_TYPES_GITHUB_URL, true);
        String templateUrl = RESOURCE_LOC + "/" + template;
        EntitySpec<?> spec = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));
        return app.createAndManageChild(spec);
    }
}
