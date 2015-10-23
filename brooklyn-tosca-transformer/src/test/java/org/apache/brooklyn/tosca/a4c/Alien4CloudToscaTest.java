package org.apache.brooklyn.tosca.a4c;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class Alien4CloudToscaTest {

    private static final Logger log = LoggerFactory.getLogger(Alien4CloudToscaTest.class);

    protected ManagementContext mgmt;
    protected BrooklynLauncher launcher;

    @BeforeMethod
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
        launcher = BrooklynLauncher.newInstance()
                .managementContext(mgmt)
                .start();
    }

    @AfterMethod
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    public ManagementContext getMgmt(){
        return mgmt;
    }

    public String getClasspathUrlForResource(String resourceName){
        return "classpath://"+ resourceName;
    }


}
