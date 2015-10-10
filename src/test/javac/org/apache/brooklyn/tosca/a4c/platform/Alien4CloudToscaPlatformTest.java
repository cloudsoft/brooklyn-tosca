package org.apache.brooklyn.tosca.a4c.platform;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

//TODO:This class is named as sample/Alien4CloudToscaPlatformTest so it should be renamed
public class Alien4CloudToscaPlatformTest extends AbstractAlien4CloudPlatformTest {

    String COMPUTE_NODE_ID = "my_server";
    String TOMCAT_NODE_ID= "tomcat_server";
    String COMPUTE_LOC_NODE= "compute_loc";
    String WAR_ROOT= "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/" +
            "brooklyn-example-hello-world-sql-webapp/0.6.0/brooklyn-example-hello-world-sql-webapp-0.6.0.war";

    @Test
    public void testCanLoadArchiveWithPolicy() throws Exception {
        Topology t = getTopolofyFromTemplateClassPath(POLICY_TEMPLATE);
        NodeGroup g1 = t.getGroups().values().iterator().next();

        Assert.assertNotNull(g1);
        Assert.assertNotNull(g1.getPolicies());
        Assert.assertEquals(g1.getPolicies().size(), 1);
        Assert.assertNotNull(g1.getPolicies().get(0));
    }

    @Test
    public void testComputeTopologyParser() {
        Topology topology = getTopolofyFromTemplateClassPath(COMPUTE_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate computeNode = topology.getNodeTemplates().get(COMPUTE_NODE_ID);
        assertEquals(computeNode.getType(), COMPUTE_NODETYPE);

        Map<String, AbstractPropertyValue> computeProperties = computeNode.getProperties();
        assertEquals(resolve(computeProperties, "num_cpus"), "1");
        assertEquals(resolve(computeProperties, "mem_size"), "4 MB");
        assertEquals(resolve(computeProperties, "disk_size"), "10 GB");
        assertNull(resolve(computeProperties, "os_arch"));
        assertNull(resolve(computeProperties, "os_type"));
        assertNull(resolve(computeProperties, "os_distribution"));
        assertNull(resolve(computeProperties, "os_version"));
    }

    @Test
    public void testTomcatTopologyParser(){
        Topology topology=getTopolofyFromTemplateClassPath(TOMCAT_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate tomcatNode = topology.getNodeTemplates().get(TOMCAT_NODE_ID);
        assertEquals(tomcatNode.getType(),TOMCAT_NODETYPE);

        Map<String, AbstractPropertyValue> tomcatProperties = tomcatNode.getProperties();
        assertEquals(resolve(tomcatProperties, "wars.root"), WAR_ROOT);
        assertEquals(resolve(tomcatProperties, "http.port"), "8080+");
    }

    @Test
    public void testComputeLocTopologyParser(){
        Topology topology=getTopolofyFromTemplateClassPath(COMPUTELOC_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate computeNode = topology.getNodeTemplates().get(COMPUTE_LOC_NODE);
        assertEquals(computeNode.getType(), COMPUTELOC_NODETYPE);

        Map<String, AbstractPropertyValue> computeProperties = computeNode.getProperties();
        assertEquals(resolve(computeProperties, "location"), "aws-ec2:eu-west-1");
        assertEquals(resolve(computeProperties, "num_cpus"), "1");
        assertEquals(resolve(computeProperties, "mem_size"), "4 MB");
        assertEquals(resolve(computeProperties, "disk_size"), "10 GB");
        assertNull(resolve(computeProperties, "os_arch"));
        assertNull(resolve(computeProperties, "os_type"));
        assertNull(resolve(computeProperties, "os_distribution"));
        assertNull(resolve(computeProperties, "os_version"));

    }

}
