package org.apache.brooklyn.tosca.a4c.platform;

import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudToscaTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SimplePolicyNodeTest extends AbstractAlien4CloudPlatformTest {

    String POLICY_TEMPLATE = "templates/simple-policy-Node.yaml";

    @Test
    public void testLoadArchiveWithPolicy() throws Exception {
        Alien4CloudToscaPlatform platform = null;
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            platform = Alien4CloudToscaPlatform.newInstance();
            platform.loadNodeTypes();
            Topology t = getTopolofyFromClassPathTemplate(POLICY_TEMPLATE);
            NodeGroup g1 = t.getGroups().values().iterator().next();
            Assert.assertNotNull(g1);
            Assert.assertNotNull(g1.getPolicies());
            Assert.assertEquals(g1.getPolicies().size(), 1);
            Assert.assertNotNull(g1.getPolicies().get(0));

        } finally {
            if (platform!=null) platform.close();
        }
    }


}
