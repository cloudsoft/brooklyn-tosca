package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;

@Component
public class RelationshipModifier extends ConfigKeyModifier {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipModifier.class);
    private final TopologyTreeBuilderService treeBuilder;

    @Inject
    public RelationshipModifier(ManagementContext mgmt, TopologyTreeBuilderService treeBuilder) {
        super(mgmt);
        this.treeBuilder = treeBuilder;
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        Map<String, Object> propertiesAndTypedValues = Collections.emptyMap();
        for (String requirementId : nodeTemplate.getRequirements().keySet()) {
            RelationshipTemplate relationshipTemplate = findRelationshipRequirement(nodeTemplate, requirementId);
            if (relationshipTemplate != null && relationshipTemplate.getType().equals("brooklyn.relationships.Configure")) {

                Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
                String computeName = (nodeTemplate.getName() != null) ? nodeTemplate.getName() : (String) entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID);
                PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
                Map<String, Object> relationProperties = getTemplatePropertyObjects(relationshipTemplate, paasNodeTemplate, builtPaaSNodeTemplates);

                // TODO: Use target properly.
                String target = relationshipTemplate.getTarget();
                String propName = relationProperties.get("prop.name").toString();
                String propCollection = relationProperties.get("prop.collection").toString();
                String propValue = relationProperties.get("prop.value").toString();

                if (Strings.isBlank(propCollection) && (Strings.isBlank(propName))) {
                    throw new IllegalStateException("Relationship for Requirement "
                            + relationshipTemplate.getRequirementName() + " on NodeTemplate "
                            + nodeTemplate.getName() + ". Collection Name or Property Name should" +
                            " be defined for RelationsType " + relationshipTemplate.getType());
                }

                Map<String, String> simpleProperty = null;
                if (!Strings.isBlank(propName)) {
                    simpleProperty = ImmutableMap.of(propName, propValue);
                }
                if (simpleProperty == null) {
                    propertiesAndTypedValues = ImmutableMap.<String, Object>of(
                            propCollection, ImmutableList.of(propValue));
                } else {
                    propertiesAndTypedValues = ImmutableMap.<String, Object>of(
                            propCollection, simpleProperty);
                }
            }
        }

        configureConfigKeysSpec(entitySpec, ConfigBag.newInstance(propertiesAndTypedValues));
    }

    private RelationshipTemplate findRelationshipRequirement(NodeTemplate node, String requirementId) {
        if (node.getRelationships() != null) {
            for (Map.Entry<String, RelationshipTemplate> entry : node.getRelationships().entrySet()) {
                if (entry.getValue().getRequirementName().equals(requirementId)) {
                    return entry.getValue();
                }
            }
        }
        LOG.warn("Requirement {} is not described by any relationship ", requirementId);
        return null;
    }

}
