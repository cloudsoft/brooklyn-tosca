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

    private static final String TEMPLATE_FOLDER ="templates/";
    protected static final String POLICY_TEMPLATE= "script1.tosca.yaml";
    protected static final String COMPUTE_TEMPLATE ="compute-template.yaml";
    protected static final String TOMCAT_TEMPLATE ="tomcat-template.yaml";
    protected static final String COMPUTELOC_TEMPLATE="computeLoc-template.yaml";
    protected static final String HW_COMPUTELOC_TEMPLATE="HelloWorld-App-ComputeLoc-template.yaml";

    protected static final String COMPUTE_NODETYPE = "tosca.nodes.Compute";
    protected static final String TOMCAT_NODETYPE= "org.apache.brooklyn.entity.webapp.tomcat.TomcatServer";
    protected static final String COMPUTELOC_NODETYPE= "tosca.nodes.ComputeLoc";
    protected static final String VANILLA_SP_TYPE = "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess";

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

    public String getClasspathUrlForTemplateResource(String resourceName){
        return getClasspathUrlForResource(TEMPLATE_FOLDER+resourceName);
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
