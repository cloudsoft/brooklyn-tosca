package org.apache.brooklyn.tosca.a4c.platform;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class SimpleTomcatAndComputeLoc extends AbstractAlien4CloudPlatformTest {

    String TEMPLATE = "templates/tomcat-computeloc-template.yaml";
    String TOMCAT_NODE_ID = "tomcat_server";
    String COMPUTELOC_NODE_ID = "compute_loc_aws";

    @Test
    public void testTomcatTopologyParser() {

        Topology topology = getTopolofyFromClassPathTemplate(TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 2);

        NodeTemplate tomcatNode = topology.getNodeTemplates().get(TOMCAT_NODE_ID);
        assertEquals(tomcatNode.getType(),TOMCATSERVER_NODETYPE_ID);
        assertEquals(resolve(tomcatNode.getProperties(), "host"), COMPUTELOC_NODE_ID);

        NodeTemplate computeLocNode = topology.getNodeTemplates().get(COMPUTELOC_NODE_ID);
        assertEquals(computeLocNode.getType(), COMPUTELOC_NODETYPE_ID);
    }


}
