package io.cloudsoft.tosca.a4c.brooklyn;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.ICsarRepositry;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.AbstractTemplate;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.topology.TopologyTemplateVersionService;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

public class Alien4CloudFacadeTest {

    @Mock
    private ICSARRepositorySearchService icsarRepositorySearchService;
    @Mock
    private TopologyTreeBuilderService topologyTreeBuilderService;
    @Mock
    private ICsarRepositry iCsarRepositry;
    @Mock
    private TopologyServiceCore topologyServiceCore;
    @Mock
    private TopologyTemplateVersionService topologyTemplateVersionService;
    @Mock
    private DeploymentTopologyService deploymentTopologyService;
    @Mock
    private ApplicationService applicationService;

    private ToscaFacade alien4CloudFacade;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        alien4CloudFacade = new Alien4CloudFacade(icsarRepositorySearchService, topologyTreeBuilderService, iCsarRepositry, topologyServiceCore, topologyTemplateVersionService, deploymentTopologyService, applicationService);
    }

    @Test
    public void testGetParentIdReturnsHostFromRelationship() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        Requirement requirement = mock(Requirement.class);
        RelationshipTemplate relationshipTemplate = mock(RelationshipTemplate.class);
        when(nodeTemplate.getRequirements()).thenReturn(ImmutableMap.of("host", requirement));
        when(nodeTemplate.getRelationships()).thenReturn(ImmutableMap.of("host", relationshipTemplate));
        when(relationshipTemplate.getRequirementName()).thenReturn("host");
        when(relationshipTemplate.getTarget()).thenReturn("Test");
        String expected = "Test";
        String actual = alien4CloudFacade.getParentId("TestChild", toscaApplication);
        assertEquals(actual, expected);
    }

    @Test
    public void testGetParentIdReturnsHostFromProperty() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        when(nodeTemplate.getProperties()).thenReturn(ImmutableMap.<String, AbstractPropertyValue>of("host", new ScalarPropertyValue("Test")));
        String expected = "Test";
        String actual = alien4CloudFacade.getParentId("TestChild", toscaApplication);
        assertEquals(actual, expected);
    }

    @Test
    public void testIsDerivedFromWhenTypeIsTheSame() {
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        when(nodeTemplate.getType()).thenReturn(Alien4CloudFacade.COMPUTE_TYPE);
        boolean actual = alien4CloudFacade.isDerivedFrom("TestId", toscaApplication, Alien4CloudFacade.COMPUTE_TYPE);
        assertTrue(actual);
    }

    @Test
    public void testIsDerivedFromWhenTypeIsDerived() {
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        IndexedArtifactToscaElement indexedArtifactToscaElement = mock(IndexedArtifactToscaElement.class);
        Topology topology = mock(Topology.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        when(toscaApplication.getTopology()).thenReturn(topology);
        when(nodeTemplate.getType()).thenReturn("TestType");
        when(topology.getDependencies()).thenReturn(ImmutableSet.<CSARDependency>of());
        when(icsarRepositorySearchService.getRequiredElementInDependencies(any(Class.class), anyString(), anySet())).thenReturn(indexedArtifactToscaElement);
        when(indexedArtifactToscaElement.getDerivedFrom()).thenReturn(ImmutableList.of(Alien4CloudFacade.COMPUTE_TYPE));
        boolean actual = alien4CloudFacade.isDerivedFrom("TestId", toscaApplication, Alien4CloudFacade.COMPUTE_TYPE);
        assertTrue(actual);
    }

    @Test
    public void testResolvesProperties() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        ImmutableMap<String, Object> complexProperty = ImmutableMap.<String, Object>of("complexKey", "complexValue");
        when(nodeTemplate.getProperties()).thenReturn(ImmutableMap.<String, AbstractPropertyValue>of(
                "testKey1", new ScalarPropertyValue("testValue"),
                "testKey2", new ComplexPropertyValue(complexProperty)));
        Object actual = alien4CloudFacade.resolveProperty("testId", toscaApplication, "testKey1");
        assertEquals(actual, "testValue");
        actual = alien4CloudFacade.resolveProperty("testId", toscaApplication, "testKey2");
        assertEquals(actual, complexProperty);
    }

    @Test()
    public void testGetTemplatePropertyObjects() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        PaaSTopology paaSTopology = mock(PaaSTopology.class);
        PaaSNodeTemplate paaSNodeTemplate = mock(PaaSNodeTemplate.class);
        IndexedNodeType indexedNodeType = mock(IndexedNodeType.class);
        when(topologyTreeBuilderService.buildPaaSTopology(toscaApplication.getTopology())).thenReturn(paaSTopology);
        when(paaSTopology.getAllNodes()).thenReturn(ImmutableMap.of(
                "TestNode", paaSNodeTemplate
        ));
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        when(toscaApplication.getKeywordMap(nodeTemplate)).thenReturn(ImmutableMap.of(
                "SELF", "TestNode"
        ));
        when(nodeTemplate.getProperties()).thenReturn(ImmutableMap.of(
                "property1", new ScalarPropertyValue("testValue"),
                "property2", new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY, ImmutableList.of(
                        "SELF", "property1"
                ))
        ));
        when(paaSNodeTemplate.getTemplate()).thenReturn(nodeTemplate);
        when(paaSNodeTemplate.getIndexedToscaElement()).thenReturn(indexedNodeType);
        Map<String, Object> actual = alien4CloudFacade.getTemplatePropertyObjects("TestNode", toscaApplication, "TestNode");
        assertEquals(actual, ImmutableMap.of(
                "property1", "testValue",
                "property2", "testValue"
        ));
    }
}
