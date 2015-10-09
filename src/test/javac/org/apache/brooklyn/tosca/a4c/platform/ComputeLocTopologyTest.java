package org.apache.brooklyn.tosca.a4c.platform;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class ComputeLocTopologyTest extends AbstractAlien4CloudPlatformTest {

    String TEMPLATE ="templates/computeLoc-template.yaml";
    String COMPUTE_LOC_NODE= "compute_loc";

    @Test
    public void testTomcatTopologyParser(){
        Topology topology=getTopolofyFromClassPathTemplate(TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate computeNode = topology.getNodeTemplates().get(COMPUTE_LOC_NODE);
        assertEquals(computeNode.getType(), COMPUTELOC_NODETYPE_ID);

        Map<String, AbstractPropertyValue> computeProperties = computeNode.getProperties();
        assertEquals(resolve(computeProperties, "location"), "aws-ec2:us-west-2");
        assertEquals(resolve(computeProperties, "num_cpus"), "1");
        assertEquals(resolve(computeProperties, "mem_size"), "4 MB");
        assertEquals(resolve(computeProperties, "disk_size"), "10 GB");
        assertNull(resolve(computeProperties, "os_arch"));
        assertNull(resolve(computeProperties, "os_type"));
        assertNull(resolve(computeProperties, "os_distribution"));
        assertNull(resolve(computeProperties, "os_version"));

    }
}
