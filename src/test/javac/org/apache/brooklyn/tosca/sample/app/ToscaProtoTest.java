package org.apache.brooklyn.tosca.sample.app;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class ToscaProtoTest {

    private static final Logger log = LoggerFactory.getLogger(ToscaProtoTest.class);
    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }
    
}
