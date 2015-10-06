package org.apache.brooklyn.tosca.a4c.platform;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudToscaTest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TomcatTopologyTest extends AbstractAlien4CloudPlatformTest {

    String TEMPLATE ="templates/tomcat-template.yaml";
    String TOMCAT_NODE= "tomcat_server";

    @Test
    public void testTomcatTopologyParser(){

        Topology topology=getTopolofyFromClassPathTemplate(TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate tomcatNode = topology.getNodeTemplates().get(TOMCAT_NODE);
        assertEquals(tomcatNode.getType(),TOMCATSERVER_NODETYPE_ID);

        Map<String, AbstractPropertyValue> tomcatProperties = tomcatNode.getProperties();
        assertEquals(resolve(tomcatProperties, "wars.root"), "root.war" );
        assertEquals(resolve(tomcatProperties, "http.port"), "8080+");
        assertNull(resolve(tomcatProperties, "wars.named"));
    }



}
