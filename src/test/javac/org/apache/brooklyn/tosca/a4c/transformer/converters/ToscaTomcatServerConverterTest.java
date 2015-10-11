package org.apache.brooklyn.tosca.a4c.transformer.converters;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.apache.brooklyn.tosca.a4c.brooklyn.converter.ToscaTomcatServerConverter;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ToscaTomcatServerConverterTest extends AbstractAlien4CloudPlatformTest {

    String TOMCAT_NODE_ID = "tomcat_server";
    String WAR_ROOT= "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0/brooklyn-example-hello-world-sql-webapp-0.6.0.war";

    @Test
    @SuppressWarnings("unchecked")
    public void testTomcatTemplateConverter(){
        Topology topology=getTopolofyFromTemplateClassPath(TOMCAT_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate computeNode = topology.getNodeTemplates().get(TOMCAT_NODE_ID);
        assertEquals(computeNode.getType(), TOMCAT_NODETYPE);

        ToscaTomcatServerConverter tomcatConverter = new ToscaTomcatServerConverter(getMgmt());
        assertNotNull(tomcatConverter);

        EntitySpec<TomcatServer> tomcatEntitySpec = tomcatConverter
                .toSpec(TOMCAT_NODE_ID, computeNode);

        assertNotNull(tomcatEntitySpec);
        assertEquals(tomcatEntitySpec.getFlags().size(), 1);
        assertEquals(tomcatEntitySpec.getFlags().get("tosca.node.type"), TOMCAT_NODETYPE);

        assertEquals(tomcatEntitySpec.getConfig().size(), 2);
        Map<ConfigKey<?>, Object> tomcatConfig = tomcatEntitySpec.getConfig();
        assertEquals(tomcatConfig.get(TomcatServer.ROOT_WAR), WAR_ROOT);
        assertEquals(
                tomcatConfig.get(Attributes.HTTP_PORT.getConfigKey()).getClass(),
                PortRanges.LinearPortRange.class);

        assertEquals(tomcatEntitySpec.getType().getName(), TOMCAT_NODETYPE);
        assertNull(tomcatEntitySpec.getParent());
        assertNull(tomcatEntitySpec.getImplementation());
        assertTrue(tomcatEntitySpec.getPolicies().isEmpty());
        assertTrue(tomcatEntitySpec.getChildren().isEmpty());

    }



}
