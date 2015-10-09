package org.apache.brooklyn.tosca.a4c.transformer.converters;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ComputeToVanillaConverterTest extends AbstractAlien4CloudPlatformTest {

    String TEMPLATE ="templates/compute-template.yaml";
    String COMPUTE_NODE_ID= "my_server";

    @Test
    @SuppressWarnings("unchecked")
    public void testComputeTemplateConverter(){
        Topology topology=getTopolofyFromClassPathTemplate(TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate computeNode = topology.getNodeTemplates().get(COMPUTE_NODE_ID);
        assertEquals(computeNode.getType(), COMPUTE_NODETYPE_ID);

        ToscaComputeToVanillaConverter computeConverter = new ToscaComputeToVanillaConverter(getMgmt());
        assertNotNull(computeConverter);

        EntitySpec<VanillaSoftwareProcess> vanillaEntitySpec = computeConverter
                .toSpec(COMPUTE_NODE_ID, computeNode);

        assertNotNull(vanillaEntitySpec);
        assertEquals(vanillaEntitySpec.getFlags().size(), 1);
        assertEquals(vanillaEntitySpec.getFlags().get("tosca.node.type"), "tosca.nodes.Compute");
        assertEquals(vanillaEntitySpec.getType().getName(), "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
        assertNull(vanillaEntitySpec.getParent());
        assertNull(vanillaEntitySpec.getImplementation());
        assertTrue(vanillaEntitySpec.getPolicies().isEmpty());
        assertTrue(vanillaEntitySpec.getChildren().isEmpty());

        Map<ConfigKey<?>, Object> vanillaConfig = vanillaEntitySpec.getConfig();
        assertEquals(vanillaConfig.size(), 4);

        Map<String, String > provisioningProperties = (Map<String, String>) vanillaConfig
                .get(SoftwareProcess.PROVISIONING_PROPERTIES);

        assertEquals(provisioningProperties.size(), 3);
        assertEquals(provisioningProperties.get("minRam"), "4 MB");
        assertEquals(provisioningProperties.get("minDisk"), "10 GB");
        assertEquals(provisioningProperties.get("minCores"), 1);

    }


}
