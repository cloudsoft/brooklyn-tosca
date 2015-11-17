package org.apache.brooklyn.tosca.a4c.brooklyn;


import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class DependencyTree{

    private static final Logger LOG = LoggerFactory.getLogger(DependencyTree.class);

    private Map<String, String> roots = MutableMap.of();
    private Map<String, String> parents = MutableMap.of();
    private Multimap<String, String> children = ArrayListMultimap.create();
    private Map<String, EntitySpec<?>> specs = MutableMap.of();
    private Map<String, NodeTemplate> nodeTemplates;
    private ManagementContext mgmt;
    private Topology topo;
    private ICSARRepositorySearchService repositorySearchService;
    private CsarFileRepository csarFileRepository;

    public DependencyTree(Topology topo, ManagementContext mgmt, ICSARRepositorySearchService repositorySearchService, CsarFileRepository csarFileRepository) {
        this.topo = topo;
        this.repositorySearchService = repositorySearchService;
        this.csarFileRepository = csarFileRepository;
        this.nodeTemplates = topo.getNodeTemplates();
        this.mgmt = mgmt;
        for(Map.Entry<String, NodeTemplate> nodeTemplate : nodeTemplates.entrySet()){
            String parentId = getParentId(nodeTemplate.getValue());
            parents.put(nodeTemplate.getKey(), parentId);
            children.put(parentId, nodeTemplate.getKey());
        }

        Set<String> visited = MutableSet.of();
        for(String id : nodeTemplates.keySet()){
            String root = root(id);
            if(!visited.contains(root)) {
                specs.put(root, build(root, visited, nodeTemplates.get(root)));
            }
        }
    }

    private EntitySpec<? extends Entity> build(String root, Set<String> visited, NodeTemplate nodeTemplate) {
        visited.add(root);
        IndexedArtifactToscaElement indexedNodeTemplate =
                repositorySearchService.getRequiredElementInDependencies(IndexedArtifactToscaElement.class,
                        nodeTemplate.getType(), topo.getDependencies());

        EntitySpec<? extends Entity> spec = ToscaNodeToEntityConverter.with(mgmt)
                .setNodeId(root)
                .setNodeTemplate(nodeTemplate)
                .setIndexedNodeTemplate(indexedNodeTemplate)
                .setCsarFileRepository(csarFileRepository)
                .createSpec(hasMultipleChildren(root));

        for(String child : children.get(root)) {
            if(!visited.contains(child)) {
                spec.child(build(child, visited, nodeTemplates.get(child)));
            }
        }

        return spec;
    }

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
        String parentId = (String) ToscaNodeToEntityConverter.resolve(nodeTemplate.getProperties(), "host");
        if (parentId != null) {
            LOG.warn("Using legacy 'host' *property* to resolve host; use *requirement* instead.");
        }
        return parentId;
    }

    public String root(String id) {
        if(roots.containsKey(id)) {
            return roots.get(id);
        }
        String next = id;
        while(parents.get(next) != null) {
            next = parents.get(next);
        }
        roots.put(id, next);
        return next;
    }

    public boolean hasMultipleChildren(String id){
        return children.containsKey(id) && children.get(id).size() > 1;
    }

    public EntitySpec<?> getSpec(String id) {
        return specs.get(id);
    }

    public Map<String, EntitySpec<?>> getSpecs() {
        return ImmutableMap.copyOf(specs);
    }

    public void addSpecsAsChildrenOf(EntitySpec<? extends Application> application) {
        application.children(specs.values());
    }
}
