package io.cloudsoft.tosca.a4c.brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import io.cloudsoft.tosca.a4c.brooklyn.spec.AbstractSpecModifier;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecFactory;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecModifier;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ApplicationSpecsBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationSpecsBuilder.class);

    // Beans
    private EntitySpecFactory entitySpecFactory;
    private Collection<EntitySpecModifier> specModifiers;

    // State
    private Map<String, String> parents = MutableMap.of();
    private Multimap<String, String> children = ArrayListMultimap.create();
    private Map<String, EntitySpec<?>> cachedSpecs;

    @Inject
    public ApplicationSpecsBuilder(EntitySpecFactory entitySpecFactory, Collection<EntitySpecModifier> specModifiers) {
        this.entitySpecFactory = checkNotNull(entitySpecFactory, "specSelector");
        this.specModifiers = checkNotNull(specModifiers, "specBuilders");
    }

    /**
     * @return A map of node id to of spec for the given topology.
     */
    public Map<String, EntitySpec<?>> getSpecs(Topology topology) {
        if (cachedSpecs != null) {
            return cachedSpecs;
        }
        parents.clear();
        children.clear();

        final Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        for (Map.Entry<String, NodeTemplate> nodeTemplate : nodeTemplates.entrySet()) {
            String parentId = getParentId(nodeTemplate.getValue());
            parents.put(nodeTemplate.getKey(), parentId);
            children.put(parentId, nodeTemplate.getKey());
        }

        // Build all specs in the tree.
        Set<String> visited = MutableSet.of();
        Map<String, EntitySpec<?>> specs = MutableMap.of();
        for (String id : nodeTemplates.keySet()) {
            String root = root(id);
            if (!visited.contains(root)) {
                specs.put(root, build(topology, root, visited));
            }
        }
        cachedSpecs = specs;
        return specs;
    }

    /**
     * Creates an entity spec for the given node then recursively adds its {@link EntitySpec#child children}.
     * Children are configured to stsart {@link SoftwareProcess.ChildStartableMode#BACKGROUND_LATE}.
     */
    private EntitySpec<? extends Entity> build(Topology topology, String node, Set<String> visited) {
        final Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        final NodeTemplate nodeTemplate = nodeTemplates.get(node);
        visited.add(node);

        EntitySpec<?> spec = createSpec(node, nodeTemplate, topology);
        for (EntitySpecModifier builder : specModifiers) {
            builder.apply(spec, nodeTemplate, topology);
        }
        for (String child : children.get(node)) {
            if (!visited.contains(child)) {
                final EntitySpec<? extends Entity> childSpec = build(topology, child, visited);
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
        // todo: why?
        Optional<Object> parentId = AbstractSpecModifier.resolve(nodeTemplate.getProperties(), "host");
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

    private EntitySpec<? extends Entity> createSpec(String nodeId, NodeTemplate nodeTemplate, Topology topology) {
        EntitySpec<?> spec = entitySpecFactory.create(nodeTemplate, topology, hasMultipleChildren(nodeId));

        // Applying name from the node template or its ID
        if (Strings.isNonBlank(nodeTemplate.getName())) {
            spec.displayName(nodeTemplate.getName());
        } else {
            nodeTemplate.setName(nodeId);
            spec.displayName(nodeId);
        }
        // Add TOSCA node type as a property
        spec.configure("tosca.node.type", nodeTemplate.getType());
        spec.configure("tosca.template.id", nodeId);
        // Use the nodeId as the camp.template.id to enable DSL lookup
        spec.configure(BrooklynCampConstants.PLAN_ID, nodeId);

        return spec;
    }

    private boolean hasMultipleChildren(String id){
        return children.containsKey(id) && children.get(id).size() > 1;
    }

}
