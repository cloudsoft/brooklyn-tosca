package io.cloudsoft.tosca.a4c;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.testng.annotations.BeforeMethod;

public abstract class Alien4CloudToscaLiveTest extends BrooklynAppLiveTestSupport {

    protected CampPlatform campPlatform;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        campPlatform = new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(mgmt)
                .launch()
                .getCampPlatform();
    }
}
