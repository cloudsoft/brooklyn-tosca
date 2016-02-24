package io.cloudsoft.tosca.a4c.brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecFactory;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecModifier;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Alien4CloudApplicationSpecsBuilder implements ApplicationSpecsBuilder<Alien4CloudApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationSpecsBuilder.class);

    // Beans
    private EntitySpecFactory entitySpecFactory;
    private Collection<EntitySpecModifier> specModifiers;

    // State
    private Map<String, String> parents = MutableMap.of();
    private Multimap<String, String> children = ArrayListMultimap.create();
    private Map<String, EntitySpec<?>> cachedSpecs;
    private ManagementContext mgmt;
    private ToscaFacade<Alien4CloudApplication> alien4CloudFacade;

    @Inject
    public Alien4CloudApplicationSpecsBuilder(ManagementContext managementContext, EntitySpecFactory entitySpecFactory, Collection<EntitySpecModifier> specModifiers, ToscaFacade<Alien4CloudApplication> alien4CloudFacade) {
        this.alien4CloudFacade = alien4CloudFacade;
        this.mgmt = checkNotNull(managementContext, "managementContext");
        this.entitySpecFactory = checkNotNull(entitySpecFactory, "entitySpecFactory");
        this.specModifiers = checkNotNull(specModifiers, "specModifiers");
    }

    /**
     * @return A map of node id to of spec for the given topology.
     */
    @Override
    public Map<String, EntitySpec<?>> getSpecs(Alien4CloudApplication toscaApplication) {
        if (cachedSpecs != null) {
            return cachedSpecs;
        }
        parents.clear();
        children.clear();

        final Iterable<String> nodeIds = toscaApplication.getNodeIds();
        for (String nodeId : nodeIds) {
            String parentId = alien4CloudFacade.getParentId(nodeId, toscaApplication);
            parents.put(nodeId, parentId);
            children.put(parentId, nodeId);
        }

        // Build all specs in the tree.
        Set<String> visited = MutableSet.of();
        Map<String, EntitySpec<?>> specs = MutableMap.of();
        for (String id : nodeIds) {
            String root = root(id);
            if (!visited.contains(root)) {
                specs.put(root, build(toscaApplication, root, visited));
            }
        }
        cachedSpecs = specs;
        return specs;
    }

    /**
     * Creates an entity spec for the given node then recursively adds its {@link EntitySpec#child children}.
     * Children are configured to stsart {@link SoftwareProcess.ChildStartableMode#BACKGROUND_LATE}.
     */
    private EntitySpec<? extends Entity> build(Alien4CloudApplication toscaApplication, String node, Set<String> visited) {

        visited.add(node);

        EntitySpec<?> spec = createSpec(node, toscaApplication);
        LOG.trace("applying spec modifiers {} to spec {}", specModifiers, spec);
        for (EntitySpecModifier builder : specModifiers) {
            builder.apply(spec, node, toscaApplication);
        }
        for (String child : children.get(node)) {
            if (!visited.contains(child)) {
                final EntitySpec<? extends Entity> childSpec = build(toscaApplication, child, visited);
                spec.child(childSpec)
                        .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, SoftwareProcess.ChildStartableMode.BACKGROUND_LATE);
            }
        }
        return spec;
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

    private EntitySpec<? extends Entity> createSpec(String nodeId, Alien4CloudApplication toscaApplication) {
        EntitySpec<?> spec = entitySpecFactory.create(nodeId, toscaApplication);

        // Applying name from the node template or its ID
        Optional<String> nodeName = toscaApplication.getNodeName(nodeId);
        if (nodeName.isPresent()) {
            spec.displayName(nodeName.get());
        } else {
            toscaApplication.setNodeName(nodeId, nodeId);
            spec.displayName(nodeId);
        }
        // Add TOSCA node type as a property
        spec.configure("tosca.node.type", toscaApplication.getNodeType(nodeId));
        spec.configure(TOSCA_TEMPLATE_ID, nodeId);
        // Use the nodeId as the camp.template.id to enable DSL lookup
        spec.configure(BrooklynCampConstants.PLAN_ID, nodeId);

        return spec;
    }

    @Override
    public void addPolicies(EntitySpec<? extends Application> rootSpec, Alien4CloudApplication toscaApplication, Map<String, EntitySpec<?>> specs){
        Iterable<String> nodeGroups = toscaApplication.getNodeGroups();
        if(Iterables.isEmpty(nodeGroups)) {
            return;
        }

        LocationToscaPolicyDecorator locationPolicyDecorator = new LocationToscaPolicyDecorator(specs, mgmt);
        BrooklynToscaPolicyDecorator brooklynPolicyDecorator = new BrooklynToscaPolicyDecorator(rootSpec, mgmt);
        for (String groupId : nodeGroups) {
            toscaApplication.addLocationPolicies(groupId, locationPolicyDecorator);
            toscaApplication.addBrooklynPolicies(groupId, brooklynPolicyDecorator, mgmt);
        }
    }
}