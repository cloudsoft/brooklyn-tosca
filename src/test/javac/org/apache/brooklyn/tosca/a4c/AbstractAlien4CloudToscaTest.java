package org.apache.brooklyn.tosca.a4c;


import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.Map;

public class AbstractAlien4CloudToscaTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractAlien4CloudToscaTest.class);

    protected final String TOMCATSERVER_NODETYPE_ID="org.apache.brooklyn.entity.webapp.tomcat.TomcatServer";
    protected final String COMPUTELOC_NODETYPE_ID = "tosca.nodes.ComputeLoc";
    protected final String COMPUTE_NODETYPE_ID = "tosca.nodes.Compute";

    protected ManagementContext mgmt;

    @BeforeMethod
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
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

    //TODO this method should be moved to a Property Management Class
    public static String resolve(Map<String, AbstractPropertyValue> props, String ...keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.get(key);
            if (v==null) continue;
            if (v instanceof ScalarPropertyValue) return ((ScalarPropertyValue)v).getValue();
            log.warn("Ignoring unsupported property value "+v);
        }
        return null;
    }




}
