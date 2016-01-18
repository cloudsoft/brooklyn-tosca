package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

public class DefaultSpecFactoryTest extends BrooklynAppUnitTestSupport {

    @Mock
    private ICSARRepositorySearchService repositorySearchService;
    @Mock
    private CsarFileRepository csarFileRepository;
    @Mock
    private Topology topology;
    @Mock
    private IndexedArtifactToscaElement indexedToscaElement;
    @Mock
    private IndexedArtifactToscaElement indexedToscaElement2;
    @Mock
    private NodeTemplate nodeTemplate1;
    @Mock
    private NodeTemplate nodeTemplate2;
    @Mock
    private TopologyTreeBuilderService treeBuilder;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMakesVanillaSoftwareProcess() {
        ImmutableSet<CSARDependency> dependencies = ImmutableSet.of();
        when(topology.getNodeTemplates()).thenReturn(ImmutableMap.of("node1", nodeTemplate1));
        when(topology.getDependencies()).thenReturn(dependencies);
        when(nodeTemplate1.getType()).thenReturn("brooklyn.nodes.Test");
        when(repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class, "brooklyn.nodes.Test", dependencies))
                .thenReturn(indexedToscaElement);
        when(indexedToscaElement.getDerivedFrom()).thenReturn(ImmutableList.<String>of());

        EntitySpecFactory factory = new DefaultEntitySpecFactory(mgmt, repositorySearchService);
        assertEquals(factory.create(nodeTemplate1, topology, false).getType(), VanillaSoftwareProcess.class);
    }

    @Test
    public void testMakesBasicApplication() {
        // model compute node with one child
        ImmutableSet<CSARDependency> dependencies = ImmutableSet.of();
        when(topology.getNodeTemplates()).thenReturn(ImmutableMap.of("node1", nodeTemplate1));
        when(topology.getDependencies()).thenReturn(dependencies);
        when(nodeTemplate1.getType()).thenReturn("brooklyn.nodes.Test");
        when(repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class, "brooklyn.nodes.Test", dependencies))
                .thenReturn(indexedToscaElement);
        when(indexedToscaElement.getDerivedFrom()).thenReturn(ImmutableList.of("tosca.nodes.Compute"));

        EntitySpecFactory factory = new DefaultEntitySpecFactory(mgmt, repositorySearchService);
        assertEquals(factory.create(nodeTemplate1, topology, false).getType(), BasicApplication.class);
    }

    @Test
    public void testMakesSameServerEntity() {
        // model compute node with two children
        ImmutableSet<CSARDependency> dependencies = ImmutableSet.of();
        when(topology.getNodeTemplates()).thenReturn(ImmutableMap.of("node1", nodeTemplate1, "node2", nodeTemplate2, "node3", nodeTemplate2));
        when(nodeTemplate2.getRequirements()).thenReturn(ImmutableMap.of("host", new Requirement()));
        RelationshipTemplate relationshipTemplate = new RelationshipTemplate();
        relationshipTemplate.setRequirementName("host");
        relationshipTemplate.setTarget("node1");
        relationshipTemplate.setType("");
        when(nodeTemplate2.getRelationships()).thenReturn(ImmutableMap.of("test", relationshipTemplate));
        when(topology.getDependencies()).thenReturn(dependencies);
        when(nodeTemplate1.getType()).thenReturn("brooklyn.nodes.Test1");
        when(nodeTemplate2.getType()).thenReturn("brooklyn.nodes.Test2");
        when(repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class, "brooklyn.nodes.Test1", dependencies))
                .thenReturn(indexedToscaElement);
        when(repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class, "brooklyn.nodes.Test2", dependencies))
                .thenReturn(indexedToscaElement2);
        when(indexedToscaElement.getDerivedFrom()).thenReturn(ImmutableList.of("tosca.nodes.Compute"));

        EntitySpecFactory factory = new DefaultEntitySpecFactory(mgmt, repositorySearchService);
        assertEquals(factory.create(nodeTemplate1, topology, true).getType(), SameServerEntity.class);
    }

}
