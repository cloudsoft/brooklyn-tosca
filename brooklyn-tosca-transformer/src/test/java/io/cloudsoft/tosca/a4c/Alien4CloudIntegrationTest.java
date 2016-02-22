package io.cloudsoft.tosca.a4c;

import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaTypePlanTransformer;
import io.cloudsoft.tosca.a4c.brooklyn.Uploader;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;

/**
 * Runs tests in a context test context. The Brooklyn managment context is a singleton.
 */
@ContextConfiguration("classpath:test-context.xml")
public class Alien4CloudIntegrationTest extends AbstractTestNGSpringContextTests {

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudIntegrationTest.class);
    protected ManagementContext mgmt;
    protected Alien4CloudToscaPlatform platform;
    protected ToscaTypePlanTransformer transformer;
    protected Uploader uploader;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        assertNotNull(super.applicationContext, "No application context for test");
        Alien4CloudToscaPlatform.grantAdminAuth();
        this.platform = super.applicationContext.getBean(Alien4CloudToscaPlatform.class);
        this.uploader = super.applicationContext.getBean(Uploader.class);
        mgmt = super.applicationContext.getBean(ManagementContext.class);
        assertNotNull(mgmt, "No management context found for test");
        ((ManagementContextInternal) mgmt).getBrooklynProperties().put(ToscaTypePlanTransformer.TOSCA_ALIEN_PLATFORM, platform);
        new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(mgmt)
                .launch()
                .getCampPlatform();

        transformer = new ToscaTypePlanTransformer();
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

    protected EntitySpec<? extends Application> create(String templateUrl) throws Exception {
        ToscaTypePlanTransformer.ToscaTypeImplementationPlan toscaTypeImplementationPlan =
                new ToscaTypePlanTransformer.ToscaTypeImplementationPlan(new ResourceUtils(mgmt).getResourceAsString(templateUrl));
        RegisteredType registeredType = RegisteredTypes.spec("test", "1.0", toscaTypeImplementationPlan, null);
        return (EntitySpec<? extends Application>) transformer.createSpec(registeredType, null);
    }
}
