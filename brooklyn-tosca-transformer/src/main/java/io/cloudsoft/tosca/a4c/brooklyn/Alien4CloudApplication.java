package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import alien4cloud.model.components.Csar;
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
        if (deploymentTopology==null) return null;
        return deploymentTopology.getId();
    }

    @Override
    public String getDelegateId() {
        if (deploymentTopology==null) return null;
        return deploymentTopology.getDelegateId();
    }

    public Topology getTopology() {
        return deploymentTopology;
    }

    public Csar getArchive() {
        return archive;
    }
    
    private Map<String, NodeTemplate> getNodeTemplates(){
        if (getTopology()==null) return ImmutableMap.of();
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
        final Optional<RelationshipTemplate> relationshipTemplateOptional = Iterables.tryFind(nodeTemplate.getRelationships().values(), new Predicate<RelationshipTemplate>() {
            @Override
            public boolean apply(RelationshipTemplate relationshipTemplate) {
                // TODO derives from
                return relationshipTemplate.getType().equals(NormativeRelationshipConstants.HOSTED_ON);
            }
        });
        return relationshipTemplateOptional;
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

}
