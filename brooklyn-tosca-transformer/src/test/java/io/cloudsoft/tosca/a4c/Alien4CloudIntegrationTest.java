package io.cloudsoft.tosca.a4c;

import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaPlanToSpecTransformer;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;

/**
 * Runs tests in a context test context. The Brooklyn managment context is a singleton.
 */
@ContextConfiguration("classpath:test-context.xml")
public class Alien4CloudIntegrationTest extends AbstractTestNGSpringContextTests {

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudIntegrationTest.class);
    protected ManagementContext mgmt;
    protected TestApplication app;
    protected Alien4CloudToscaPlatform platform;
    protected ToscaPlanToSpecTransformer transformer;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        assertNotNull(super.applicationContext, "No application context for test");
        mgmt = super.applicationContext.getBean(ManagementContext.class);
        assertNotNull(mgmt, "No management context found for test");
        this.setUpApp();
        ((ManagementContextInternal) mgmt).getBrooklynProperties().put(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM, platform);
        new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(mgmt)
                .launch()
                .getCampPlatform();
        Alien4CloudToscaPlatform.grantAdminAuth();

        this.platform = super.applicationContext.getBean(Alien4CloudToscaPlatform.class);
        platform.loadNormativeTypes();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.setManagementContext(mgmt);
    }

    @AfterSuite(alwaysRun = true)
    public void tearDown() throws Exception {
        try {
            if (this.mgmt != null) {
                Entities.destroyAll(this.mgmt);
            }
        } catch (Throwable e) {
            LOG.error("Caught exception in tearDown method", e);
        } finally {
            this.mgmt = null;
        }
    }

    protected boolean shouldSkipOnBoxBaseDirResolution() {
        return true;
    }

    protected void setUpApp() {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());

        app = mgmt.getEntityManager().createEntity(appSpec);
    }

}
