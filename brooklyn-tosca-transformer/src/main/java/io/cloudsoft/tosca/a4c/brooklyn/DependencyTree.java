package io.cloudsoft.tosca.a4c.brooklyn;


import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class DependencyTree {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyTree.class);

    private Map<String, String> parents = MutableMap.of();
    private Multimap<String, String> children = ArrayListMultimap.create();
    private Map<String, EntitySpec<?>> specs = MutableMap.of();
    private Map<String, NodeTemplate> nodeTemplates;
    private ManagementContext mgmt;
    private Topology topo;
    private ICSARRepositorySearchService repositorySearchService;
    private CsarFileRepository csarFileRepository;
    private TopologyTreeBuilderService treeBuilder;

    public DependencyTree(Topology topo, ManagementContext mgmt, ICSARRepositorySearchService repositorySearchService, CsarFileRepository csarFileRepository,
            TopologyTreeBuilderService treeBuilder) {
        this.topo = topo;
        this.repositorySearchService = repositorySearchService;
        this.csarFileRepository = csarFileRepository;
        this.nodeTemplates = topo.getNodeTemplates();
        this.treeBuilder = treeBuilder;
        this.mgmt = mgmt;

        for (Map.Entry<String, NodeTemplate> nodeTemplate : nodeTemplates.entrySet()) {
            String parentId = getParentId(nodeTemplate.getValue());
            parents.put(nodeTemplate.getKey(), parentId);
            children.put(parentId, nodeTemplate.getKey());
        }

        // Build all specs in the tree.
        Set<String> visited = MutableSet.of();
        for (String id : nodeTemplates.keySet()) {
            String root = root(id);
            if (!visited.contains(root)) {
                specs.put(root, build(root, visited, nodeTemplates.get(root)));
            }
        }
    }

    /**
     * Creates an entity spec for the given node then recursively adds its {@link EntitySpec#child children}.
     */
    private EntitySpec<? extends Entity> build(String node, Set<String> visited, NodeTemplate nodeTemplate) {
        visited.add(node);
        IndexedArtifactToscaElement indexedNodeTemplate =
                repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class,
                        nodeTemplate.getType(), topo.getDependencies());

        EntitySpec<? extends Entity> spec = ToscaNodeToEntityConverter.with(mgmt)
                .setNodeId(node)
                .setNodeTemplate(nodeTemplate)
                .setIndexedNodeTemplate(indexedNodeTemplate)
                .setCsarFileRepository(csarFileRepository)
                .setTopology(topo)
                .setTreeBuilder(treeBuilder)
                .createSpec(hasMultipleChildren(node));

        for (String child : children.get(node)) {
            if (!visited.contains(child)) {
                final EntitySpec<? extends Entity> childSpec = build(child, visited, nodeTemplates.get(child));
                spec.child(childSpec)
                        .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, SoftwareProcess.ChildStartableMode.BACKGROUND_LATE);
            }
        }

        return spec;
    }

    /**
     * Resolves the parent dependency of the given node template.
     * <p>
     * The parent dependency is:
     * <ul>
     *     <li>The target node of a requirement called "host"</li>
     *     <li>The resolved value of a property called "host"</li>
     *     <li>Null</li>
     * </ul>
     * @param nodeTemplate The node to examine
     * @return The id of the parent or null.
     */
    private String getParentId(NodeTemplate nodeTemplate) {
        Requirement host = nodeTemplate.getRequirements() != null ? nodeTemplate.getRequirements().get("host") : null;
        if (host != null) {
            for (RelationshipTemplate r : nodeTemplate.getRelationships().values()) {
                if (r.getRequirementName().equals("host")) {
                    return r.getTarget();
                }
            }
        }

        // temporarily, fall back to looking for a *property* called 'host'
        Optional<Object> parentId = ToscaNodeToEntityConverter.resolve(nodeTemplate.getProperties(), "host");
        if (parentId.isPresent()) {
            LOG.warn("Using legacy 'host' *property* to resolve host; use *requirement* instead.");
        }
        return (String) parentId.orNull();
    }

    /**
     * @return The root ancestor of the given id in {@link #parents}.
     */
    private String root(String id) {
        String next = id;
        while (parents.get(next) != null) {
            next = parents.get(next);
        }
        return next;
    }

    private boolean hasMultipleChildren(String id){
        return children.containsKey(id) && children.get(id).size() > 1;
    }

    @VisibleForTesting
    EntitySpec<?> getSpec(String id) {
        return specs.get(id);
    }

    public Map<String, EntitySpec<?>> getSpecs() {
        return ImmutableMap.copyOf(specs);
    }

    public void addSpecsAsChildrenOf(EntitySpec<? extends Application> application) {
        application.children(specs.values());
    }
}
