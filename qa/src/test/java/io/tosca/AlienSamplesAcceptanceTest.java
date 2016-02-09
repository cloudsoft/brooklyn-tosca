package io.tosca;

import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.client.BrooklynApiUtil;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class AlienSamplesAcceptanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(AlienSamplesAcceptanceTest.class);
    private static final Duration TEST_TIMEOUT = Duration.of(15, TimeUnit.MINUTES);

    private BrooklynApi api;
    private String singleTestBlueprint;

    @BeforeClass(alwaysRun = true)
    @Parameters({"server", "username", "password", "testBlueprint"})
    public void setServer(String server, @Optional String username, @Optional String password,
            @Optional String testBlueprint) {
        if (Strings.isBlank(server)) {
            throw new AssertionError("URL required for server");
        }
        if (!Strings.isBlank(username) && !Strings.isBlank(password)) {
            this.api = BrooklynApi.newInstance(server, username, password);
        } else {
            this.api = BrooklynApi.newInstance(server);
        }
        this.singleTestBlueprint = testBlueprint;
    }

    @DataProvider
    public Object[][] testGenerator() {
        if (!Strings.isBlank(singleTestBlueprint)) {
            return new Object[][]{{this.singleTestBlueprint}};
        }
        return new Object[][]{
                new Object[]{"classpath://projects/apache-topology.tosca.yaml"},
                new Object[]{"classpath://projects/mongo-topology.tosca.yaml"},
                // FIXME: Relies on artifact environment variables. Renable when RuntimeEnvironmentModifier is fixed.
                // new Object[]{"classpath://projects/mysql-topology.tosca.yaml"},
                new Object[]{"classpath://projects/php-topology.tosca.yaml"},
        };
    }

    @Test(dataProvider = "testGenerator")
    public void testBlueprint(String resource) throws Exception {
        String testCaseYaml = loadBlueprint(resource);
        String application = null;
        try {
            TaskSummary task = BrooklynApiUtil.deployBlueprint(api, testCaseYaml);
            application = task.getEntityId();
            BrooklynApiUtil.waitForRunningAndThrowOtherwise(api, application, task.getId());
        } catch (Exception e) {
            throw new AssertionError("Error running application", e);
        } finally {
            if (application != null) {
                LOG.info("Stopping application " + application);
                BrooklynApiUtil.attemptStop(api, application, getTimeout());
            }
        }
    }

    private String loadBlueprint(String resource) {
        return new ResourceUtils(null).getResourceAsString(resource);
    }

    private Duration getTimeout() {
        return TEST_TIMEOUT;
    }

}
