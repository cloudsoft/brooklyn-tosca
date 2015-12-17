package io.cloudsoft.tosca.a4c;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.BeforeMethod;

public class Alien4CloudToscaTest extends BrooklynAppUnitTestSupport {

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

    public String getClasspathUrlForResource(String resourceName){
        return "classpath://"+ resourceName;
    }

}
