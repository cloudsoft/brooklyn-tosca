package org.apache.brooklyn.tosca.a4c.transformer.converters;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudPlatformTest;
import org.apache.brooklyn.tosca.a4c.brooklyn.converter.ToscaNodeTemplateToEntityConverter;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ToscaNodeTemplateToEntityConverterTest extends AbstractAlien4CloudPlatformTest {

    String TOMCAT_NODE_ID = "tomcat_server";
    String SAMESERVER_NODE_ID = "my_server";
    String WAR_ROOT= "http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0/brooklyn-example-hello-world-sql-webapp-0.6.0.war";

    @Test
    @SuppressWarnings("unchecked")
    public void testTomcatTemplateConverter(){
        Topology topology=getTopolofyFromTemplateClassPath(TOMCAT_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate tomcatNode = topology.getNodeTemplates().get(TOMCAT_NODE_ID);
        assertEquals(tomcatNode.getType(), TOMCAT_NODETYPE);

        ToscaNodeTemplateToEntityConverter nodeTemplatetConverter = new ToscaNodeTemplateToEntityConverter(getMgmt());
        assertNotNull(nodeTemplatetConverter);

        EntitySpec<TomcatServer> tomcatEntitySpec = (EntitySpec<TomcatServer>) nodeTemplatetConverter
                .toSpec(TOMCAT_NODE_ID, tomcatNode);

        assertNotNull(tomcatEntitySpec);
        assertEquals(tomcatEntitySpec.getFlags().size(), 1);
        assertEquals(tomcatEntitySpec.getFlags().get("tosca.node.type"), TOMCAT_NODETYPE);

        assertEquals(tomcatEntitySpec.getConfig().size(), 2);
        Map<ConfigKey<?>, Object> tomcatConfig = tomcatEntitySpec.getConfig();
        assertEquals(tomcatConfig.get(TomcatServer.ROOT_WAR), WAR_ROOT);
        assertEquals(
                tomcatConfig.get(Attributes.HTTP_PORT.getConfigKey()).getClass(),
                String.class);

        assertEquals(tomcatEntitySpec.getType().getName(), TOMCAT_NODETYPE);
        assertNull(tomcatEntitySpec.getParent());
        assertNull(tomcatEntitySpec.getImplementation());
        assertTrue(tomcatEntitySpec.getPolicies().isEmpty());
        assertTrue(tomcatEntitySpec.getChildren().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSameServerTemplateConverter(){
        Topology topology=getTopolofyFromTemplateClassPath(SAMESERVER_TEMPLATE);

        assertNotNull(topology);
        assertEquals(topology.getNodeTemplates().size(), 1);

        NodeTemplate sameServerNode = topology.getNodeTemplates().get(SAMESERVER_NODE_ID);
        assertEquals(sameServerNode.getType(), SAMESERVER_TYPE);

        ToscaNodeTemplateToEntityConverter nodeTemplatetConverter = new ToscaNodeTemplateToEntityConverter(getMgmt());
        assertNotNull(nodeTemplatetConverter);

        EntitySpec<SameServerEntity> sameServerEntitySpec = (EntitySpec<SameServerEntity>) nodeTemplatetConverter
                .toSpec(SAMESERVER_NODE_ID, sameServerNode);

        assertNotNull(sameServerEntitySpec);
        assertEquals(sameServerEntitySpec.getFlags().size(), 1);
        assertEquals(sameServerEntitySpec.getFlags().get("tosca.node.type"), SAMESERVER_TYPE);

        assertEquals(sameServerEntitySpec.getConfig().size(), 7);

        assertFalse(sameServerEntitySpec.getLocations().isEmpty());
        assertEquals(sameServerEntitySpec.getType().getName(), SAMESERVER_TYPE);
        assertNull(sameServerEntitySpec.getParent());
        assertNull(sameServerEntitySpec.getImplementation());
        assertTrue(sameServerEntitySpec.getPolicies().isEmpty());
        assertTrue(sameServerEntitySpec.getChildren().isEmpty());

    }

}
