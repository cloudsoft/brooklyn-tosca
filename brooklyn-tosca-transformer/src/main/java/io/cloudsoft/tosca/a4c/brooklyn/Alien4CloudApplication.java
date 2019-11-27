package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import alien4cloud.model.components.Csar;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.GenericPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

public class Alien4CloudApplication implements ToscaApplication {

    private final String name;
    private final Topology deploymentTopology;
    private final String deploymentId;
    // the class ArchivePostProcessor does not correctly set impl artifact on things in topologies (eg interfaces);
    // so let's _us_ maintain it as well
    private final Csar archive;

    public Alien4CloudApplication(String name, Topology deploymentTopology, String deploymentId, Csar archive) {
        this.name = name;
        this.deploymentTopology = deploymentTopology;
        this.deploymentId = deploymentId;
        this.archive = archive;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDeploymentId() {
        return deploymentId;
    }

    @Override
    public String getId() {
        return deploymentTopology.getId();
    }

    @Override
    public String getDelegateId() {
        return deploymentTopology.getDelegateId();
    }

    public Topology getTopology() {
        return deploymentTopology;
    }

    public Csar getArchive() {
        return archive;
    }
    
    private Map<String, NodeTemplate> getNodeTemplates(){
        return ImmutableMap.copyOf(getTopology().getNodeTemplates());
    }

    public NodeTemplate getNodeTemplate(String id) {
        return getNodeTemplates().get(id);
    }

    @Override
    public Optional<String> getNodeName(String id) {
        String name = getNodeTemplate(id).getName();
        if(Strings.isBlank(name)){
            return Optional.absent();
        }
        return Optional.of(name);
    }

    @Override
    public Map<String, String> getKeywordMap(String id) {
        return getKeywordMap(getNodeTemplate(id));
    }

    public Map<String, String> getKeywordMap(NodeTemplate nodeTemplate) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        String nodeTemplateName = nodeTemplate.getName();
        if (nodeTemplateName != null) {
            builder.put("SELF", nodeTemplateName);
        }
        String hostedOnRoot = hostedOnRoot(nodeTemplate);
        if (hostedOnRoot != null) {
            builder.put("HOST", hostedOnRoot);
        }
        return builder.build();
    }

    private String hostedOnRoot(NodeTemplate nodeTemplate) {
        Optional<RelationshipTemplate> relationship = findHostedOn(nodeTemplate);
        if (!relationship.isPresent()) {
            return nodeTemplate.getName();
        }
        return hostedOnRoot(getNodeTemplate(relationship.get().getTarget()));
    }

    private Optional<RelationshipTemplate> findHostedOn(NodeTemplate nodeTemplate) {
        if (nodeTemplate.getRelationships() == null) return Optional.absent();
        return Iterables.tryFind(nodeTemplate.getRelationships().values(), new Predicate<RelationshipTemplate>() {
            @Override
            public boolean apply(RelationshipTemplate relationshipTemplate) {
                // TODO derives from
                return relationshipTemplate.getType().equals(NormativeRelationshipConstants.HOSTED_ON);
            }
        });
    }

    public Map<String, String> getKeywordMap(NodeTemplate nodeTemplate, RelationshipTemplate relationshipTemplate) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        String nodeTemplateName = nodeTemplate.getName();
        if (nodeTemplateName != null) {
            builder.put("SOURCE", nodeTemplateName);
        }
        String target = relationshipTemplate.getTarget();
        if (target != null) {
            builder.put("TARGET", target);
        }
        return builder.build();
    }

    @Override
    public Iterable<Relationship> getAllRelationships(String nodeId) {
        Set<Relationship> result = Sets.newHashSet();
        for (Map.Entry<String, NodeTemplate> nodeTemplate : getNodeTemplates().entrySet()) {
            if (nodeTemplate.getValue().getRelationships() != null) {
                for (Map.Entry<String, RelationshipTemplate> relationshipTemplate : nodeTemplate.getValue().getRelationships().entrySet()) {
                    if (relationshipTemplate.getValue().getTarget().equals(nodeId)) {
                        result.add(new Relationship(nodeTemplate.getKey(), nodeId, relationshipTemplate.getKey(), relationshipTemplate.getValue().getType()));
                    } else if (nodeTemplate.getKey().equals(nodeId)) {
                        result.add(new Relationship(nodeId, relationshipTemplate.getValue().getTarget(), relationshipTemplate.getKey(), relationshipTemplate.getValue().getType()));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Iterable<String> getNodeIds() {
        return getNodeTemplates().keySet();
    }

    @Override
    public void setNodeName(String nodeId, String newName) {
        getNodeTemplate(nodeId).setName(newName);
    }

    @Override
    public String getNodeType(String nodeId) {
        return getNodeTemplate(nodeId).getType();
    }

    @Override
    public Iterable<String> getNodeGroups() {
        Map<String, NodeGroup> groups = getTopology().getGroups();
        if (groups == null) {
            return Collections.emptyList();
        }
        return groups.keySet();
    }

    private Iterable<AbstractPolicy> getPoliciesWithFilter(String groupId, Predicate<AbstractPolicy> predicate) {
        Map<String, NodeGroup> groups = getTopology().getGroups();
        if (groups == null || groups.get(groupId) == null || groups.get(groupId).getPolicies() == null) {
            return Collections.emptyList();
        }
        return Iterables.filter(groups.get(groupId).getPolicies(), predicate);
    }

    private Iterable<AbstractPolicy> getLocationPolicies(String groupId) {
        return getPoliciesWithFilter(groupId, new Predicate<AbstractPolicy>() {
            @Override
            public boolean apply(@Nullable AbstractPolicy abstractPolicy) {
                return "brooklyn.location".equals(abstractPolicy.getName());
            }
        });
    }

    private Set<String> getGroupMembers(String groupId) {
        NodeGroup g = getTopology().getGroups().get(groupId);
        return (g.getMembers() == null || g.getMembers().isEmpty()) ? Collections.emptySet() : g.getMembers();
    }

    private Iterable<AbstractPolicy> getBrooklynEntities(String groupId, final ManagementContext mgmt, BrooklynObjectType expectedType) {
        return getPoliciesWithFilter(groupId, new Predicate<AbstractPolicy>() {
            @Override
            public boolean apply(@Nullable AbstractPolicy abstractPolicy) {
                GenericPolicy policy = (GenericPolicy) abstractPolicy;
                Optional<String> type = getPolicyType(Optional.fromNullable(policy.getType()), policy.getData());
                return isABrooklynObjectOfType(type, mgmt, expectedType);
            }
        });
    }

    /**
     * Tests if {@code type} is a Policy or Enricher type
     */
    private boolean isABrooklynObjectOfType(Optional<String> type, ManagementContext mgmt, BrooklynObjectType expectedType){
        if(!type.isPresent()) {
            return false;
        }

        RegisteredType match = mgmt.getTypeRegistry().get(type.get(), RegisteredTypeLoadingContexts.withSpecSuperType(null, expectedType.getSpecType()));
        if (match!=null) {
            return true;
        }
        // legacy check, for tests
        try {
            Class<?> clazz = Class.forName(type.get());   // should only be used for testing
            return expectedType.getInterfaceType().isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Optional<String> getPolicyType(Optional<String> policyType, Map<String, ?> policyData){
        String type = null;
        if (policyType.isPresent()) {
            type = policyType.get();
        } else if (policyData.containsKey("type")) {
            type = (String) policyData.get("type");
        }
        return Optional.fromNullable(type);
    }

    private void addPolicies(String groupId, ToscaPolicyDecorator toscaPolicyDecorator, Iterable<AbstractPolicy> policies) {
        Set<String> groupMembers = getGroupMembers(groupId);
        for (AbstractPolicy p : policies) {
            GenericPolicy policy = (GenericPolicy) p;
            Optional<String> type = getPolicyType(Optional.fromNullable(policy.getType()), policy.getData());
            toscaPolicyDecorator.decorate(policy.getData(), policy.getName(), type, groupMembers);
        }
    }

    @Override
    public void addLocationPolicies(String groupId, ToscaPolicyDecorator toscaPolicyDecorator){
        addPolicies(groupId, toscaPolicyDecorator, getLocationPolicies(groupId));
    }

    @Override
    public void addBrooklynPolicies(String groupId, BrooklynToscaPolicyDecorator brooklynPolicyDecorator, ManagementContext mgmt) {
        addPolicies(groupId, brooklynPolicyDecorator, getBrooklynEntities(groupId, mgmt, BrooklynObjectType.POLICY));
    }

    @Override
    public void addBrooklynEnrichers(String groupId, BrooklynToscaEnricherDecorator brooklynEnricherDecorator, ManagementContext mgmt) {
        addPolicies(groupId, brooklynEnricherDecorator, getBrooklynEntities(groupId, mgmt, BrooklynObjectType.ENRICHER));
    }

}
