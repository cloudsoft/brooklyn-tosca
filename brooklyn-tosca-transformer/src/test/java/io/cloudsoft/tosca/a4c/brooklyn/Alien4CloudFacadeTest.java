package io.cloudsoft.tosca.a4c.brooklyn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alien4cloud.tosca.catalog.repository.ICsarRepositry;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Requirement;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractInstantiableToscaType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.topology.TopologyServiceCore;

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
    private DeploymentTopologyService deploymentTopologyService;
    @Mock
    private ApplicationService applicationService;

    private ToscaFacade alien4CloudFacade;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        alien4CloudFacade = new Alien4CloudFacade(icsarRepositorySearchService, topologyTreeBuilderService, iCsarRepositry, topologyServiceCore, deploymentTopologyService, applicationService);
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
        when(nodeTemplate.getProperties()).thenReturn(ImmutableMap.of("host", new ScalarPropertyValue("Test")));
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
        AbstractInstantiableToscaType indexedArtifactToscaElement = mock(AbstractInstantiableToscaType.class);
        Topology topology = mock(Topology.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        when(toscaApplication.getTopology()).thenReturn(topology);
        when(nodeTemplate.getType()).thenReturn("TestType");
        when(topology.getDependencies()).thenReturn(ImmutableSet.of());
        when(icsarRepositorySearchService.getRequiredElementInDependencies(any(Class.class), anyString(), (Set<CSARDependency>) ArgumentMatchers.<CSARDependency>anyCollection())).thenReturn(indexedArtifactToscaElement);
        when(indexedArtifactToscaElement.getDerivedFrom()).thenReturn(ImmutableList.of(Alien4CloudFacade.COMPUTE_TYPE));
        boolean actual = alien4CloudFacade.isDerivedFrom("TestId", toscaApplication, Alien4CloudFacade.COMPUTE_TYPE);
        assertTrue(actual);
    }

    @Test
    public void testResolvesProperties() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);
        ImmutableMap<String, Object> complexProperty = ImmutableMap.of("complexKey", "complexValue");
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
        NodeType indexedNodeType = mock(NodeType.class);
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

    @Test
    @SuppressWarnings("unchecked")
    public void testGetMapPropertiesOfRelation() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        PaaSTopology paaSTopology = mock(PaaSTopology.class);

        PaaSNodeTemplate paaSNodeTemplate = mock(PaaSNodeTemplate.class);
        when(topologyTreeBuilderService.buildPaaSTopology(toscaApplication.getTopology())).thenReturn(paaSTopology);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);

        Requirement requirement = mock(Requirement.class);
        when(nodeTemplate.getRequirements()).thenReturn(ImmutableMap.of("requirementTest", requirement));

        RelationshipTemplate relationshipTemplate1 = mock(RelationshipTemplate.class);
        when(relationshipTemplate1.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate1.getType()).thenReturn("brooklyn.relationships.Configure");

        RelationshipTemplate relationshipTemplate2 = mock(RelationshipTemplate.class);
        when(relationshipTemplate2.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate2.getType()).thenReturn("brooklyn.relationships.Configure");

        RelationshipTemplate relationshipTemplate3 = mock(RelationshipTemplate.class);
        when(relationshipTemplate3.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate3.getType()).thenReturn("brooklyn.relationships.Configure");

        when(nodeTemplate.getRelationships()).thenReturn(
                ImmutableMap.of(
                        "relationTest1", relationshipTemplate1,
                        "relationTest2", relationshipTemplate2,
                        "relationTest3", relationshipTemplate3));

        when(paaSTopology.getAllNodes()).thenReturn(ImmutableMap.of("nodeTest", paaSNodeTemplate));

        when(relationshipTemplate1.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.name", new ScalarPropertyValue("prop1"))
                        .put("prop.value", new ScalarPropertyValue("value1"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());
        when(relationshipTemplate1.getType()).thenReturn("brooklyn.relationships.Configure");

        when(relationshipTemplate2.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.name", new ScalarPropertyValue("prop2"))
                        .put("prop.value", new ScalarPropertyValue("value2"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());
        when(relationshipTemplate2.getType()).thenReturn("brooklyn.relationships.Configure");

        when(relationshipTemplate3.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.value", new ScalarPropertyValue("value3"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());
        when(relationshipTemplate3.getType()).thenReturn("brooklyn.relationships.Configure");

        Map<String, Object> propertiesRequirementTest = alien4CloudFacade
                .getPropertiesAndTypeValuesByRelationshipId("nodeTest", toscaApplication, "relationTest1", "nodeTest");
        propertiesRequirementTest.entrySet();
        assertEquals(propertiesRequirementTest.size(), 1);
        assertTrue(propertiesRequirementTest.containsKey("collection1"));
        assertTrue(propertiesRequirementTest.get("collection1") instanceof Map);
        Map<String, Object> collectionPropertiesValues =
                (Map<String, Object>) propertiesRequirementTest.get("collection1");

        assertEquals(collectionPropertiesValues.size(), 1);
        assertTrue(collectionPropertiesValues.containsKey("prop1"));
        assertEquals(collectionPropertiesValues.get("prop1"), "value1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetListPropertiesOfRelation() {
        Alien4CloudApplication toscaApplication = mock(Alien4CloudApplication.class);
        NodeTemplate nodeTemplate = mock(NodeTemplate.class);
        PaaSTopology paaSTopology = mock(PaaSTopology.class);

        PaaSNodeTemplate paaSNodeTemplate = mock(PaaSNodeTemplate.class);
        when(topologyTreeBuilderService.buildPaaSTopology(toscaApplication.getTopology())).thenReturn(paaSTopology);
        when(toscaApplication.getNodeTemplate(anyString())).thenReturn(nodeTemplate);

        Requirement requirement = mock(Requirement.class);
        when(nodeTemplate.getRequirements()).thenReturn(ImmutableMap.of("requirementTest", requirement));

        RelationshipTemplate relationshipTemplate1 = mock(RelationshipTemplate.class);
        when(relationshipTemplate1.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate1.getType()).thenReturn("brooklyn.relationships.Configure");

        RelationshipTemplate relationshipTemplate2 = mock(RelationshipTemplate.class);
        when(relationshipTemplate2.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate2.getType()).thenReturn("brooklyn.relationships.Configure");

        RelationshipTemplate relationshipTemplate3 = mock(RelationshipTemplate.class);
        when(relationshipTemplate3.getRequirementName()).thenReturn("requirementTest");
        when(relationshipTemplate3.getType()).thenReturn("brooklyn.relationships.Configure");

        when(nodeTemplate.getRelationships()).thenReturn(
                ImmutableMap.of(
                        "relationTest1", relationshipTemplate1,
                        "relationTest2", relationshipTemplate2,
                        "relationTest3", relationshipTemplate3));

        when(paaSTopology.getAllNodes()).thenReturn(ImmutableMap.of("nodeTest", paaSNodeTemplate));

        when(relationshipTemplate1.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.value", new ScalarPropertyValue("value1"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());

        when(relationshipTemplate2.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.value", new ScalarPropertyValue("value2"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());

        when(relationshipTemplate3.getProperties()).thenReturn(
                ImmutableMap.<String, AbstractPropertyValue>builder()
                        .put("prop.name", new ScalarPropertyValue("prop3"))
                        .put("prop.value", new ScalarPropertyValue("value3"))
                        .put("prop.collection", new ScalarPropertyValue("collection1"))
                        .build());

        Map<String, Object> propertiesRequirementTest = alien4CloudFacade
                .getPropertiesAndTypeValuesByRelationshipId("nodeTest", toscaApplication, "relationTest1", "nodeTest");

        assertEquals(propertiesRequirementTest.size(), 1);
        assertTrue(propertiesRequirementTest.containsKey("collection1"));
        assertTrue(propertiesRequirementTest.get("collection1") instanceof List);
        List<String> collectionPropertiesValues =
                (List<String>) propertiesRequirementTest.get("collection1");

        assertEquals(collectionPropertiesValues.size(), 1);
        assertTrue(collectionPropertiesValues.contains("value1"));

        try {
            alien4CloudFacade.getPropertiesAndTypeValuesByRelationshipId("nodeTest", toscaApplication, "unrelated", "nodeTest");
            fail("Expected NPE when retrieving invalid relationship");
        } catch (NullPointerException npe) {
            return;
        }
    }

}
