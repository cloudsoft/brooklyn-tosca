package io.cloudsoft.tosca.a4c.brooklyn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Topology;

public class Alien4CloudApplicationTest {

    @Mock
    private Topology deploymentTopology;

    private Alien4CloudApplication alien4CloudApplication;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        alien4CloudApplication = new Alien4CloudApplication("TestApplication", deploymentTopology, "testDeployment");
    }

    @Test
    public void testGetKeywordMapReturnsSelf(){
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        when(deploymentTopology.getNodeTemplates()).thenReturn(ImmutableMap.of("testId", nodeTemplate));
        when(nodeTemplate.getName()).thenReturn("TestNode");
        Map<String, String> keywordMap = alien4CloudApplication.getKeywordMap("testId");
        Assert.assertNotNull(keywordMap);
        String actual = keywordMap.get("SELF");
        String expected = "TestNode";
        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testGetKeywordMapReturnsHost(){
        String node = "TestNode";
        String host = "TestHost";
        NodeTemplate nodeTemplate1 = mock(NodeTemplate.class);
        NodeTemplate nodeTemplate2 = mock(NodeTemplate.class);
        when(deploymentTopology.getNodeTemplates()).thenReturn(ImmutableMap.of(node, nodeTemplate1, host, nodeTemplate2));
        when(nodeTemplate1.getName()).thenReturn(node);
        when(nodeTemplate2.getName()).thenReturn(host);
        RelationshipTemplate relationshipTemplate = mock(RelationshipTemplate.class);
        when(nodeTemplate1.getRelationships()).thenReturn(ImmutableMap.of("HostedOn", relationshipTemplate));
        when(relationshipTemplate.getType()).thenReturn("tosca.relationships.HostedOn");
        when(relationshipTemplate.getTarget()).thenReturn(host);
        Map<String, String> keywordMap = alien4CloudApplication.getKeywordMap(node);
        Assert.assertNotNull(keywordMap);
        String actual = keywordMap.get("HOST");
        String expected = host;
        Assert.assertEquals(actual, expected);
    }
}
