package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

@Component
public class RelationshipModifier extends ConfigKeyModifier {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipModifier.class);
    private static final ImmutableSet<String> SOURCE_OPERATIONS = ImmutableSet.of(
            ToscaRelationshipLifecycleConstants.ADD_SOURCE,
            ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE,
            ToscaRelationshipLifecycleConstants.PRE_CONFIGURE_SOURCE,
            ToscaRelationshipLifecycleConstants.REMOVE_SOURCE
    );

    @Inject
    public RelationshipModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        Map<String, Object> propertiesAndTypedValues = Maps.newHashMap();
        Iterable<ToscaApplication.Relationship> allRelationships = toscaApplication.getAllRelationships(nodeId);

        for (ToscaApplication.Relationship relationship : allRelationships) {
            String computeName = toscaApplication.getNodeName(nodeId).or(getToscaTemplateId(entitySpec));
            Iterable<String> operations = getToscaFacade().getInterfaceOperationsByRelationship(toscaApplication, relationship);
            for (String opKey : operations) {
                Optional<Object> script = getToscaFacade().getRelationshipScript(opKey, toscaApplication, relationship, computeName, StandardInterfaceLifecycleModifier.EXPANDED_FOLDER);
                if (script.isPresent()) {
                    String lifecycle = getToscaFacade().getLifeCycle(opKey).getName();
                    Object existingScript = entitySpec.getFlags().get(lifecycle);
                    Object newScript = script.get();
                    if (existingScript != null && !Strings.isBlank(String.valueOf(existingScript))) {
                        newScript = BrooklynDslCommon.formatString("%s\n%s", existingScript, newScript);
                    }
                    if (isSourceOperation(opKey) && nodeId.equals(relationship.getSourceNodeId())) {
                        // configure source
                        entitySpec.configure(lifecycle, newScript);
                    } else if (!isSourceOperation(opKey) && nodeId.equals(relationship.getTargetNodeId())) {
                        // configure target
                        entitySpec.configure(lifecycle, newScript);
                    }
                }
            }
            joinPropertiesAndValueTypes(propertiesAndTypedValues, getToscaFacade().getPropertiesAndTypeValuesByRelationshipId(relationship.getSourceNodeId(), toscaApplication, relationship.getRelationshipId(), computeName));
        }
        configureConfigKeysSpec(entitySpec, ConfigBag.newInstance(propertiesAndTypedValues));
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> joinPropertiesAndValueTypes(Map<String, Object> properties,
                                                            Map<String, Object> newProperties) {
        for (String newPropertyId : newProperties.keySet()) {
            Object newPropertyValue = newProperties.get(newPropertyId);
            if (!properties.containsKey(newPropertyId)) {
                properties.put(newPropertyId, newPropertyValue);
            } else {
                Object oldPropertyValue = properties.get(newPropertyId);
                if ((oldPropertyValue instanceof Map)
                        && (newPropertyValue instanceof Map)) {
                    ((Map) oldPropertyValue).putAll((Map) newPropertyValue);
                } else if ((oldPropertyValue instanceof List)
                        && (newPropertyValue instanceof List)) {
                    ((List) oldPropertyValue).addAll((List) newPropertyValue);
                }
            }
        }
        return properties;
    }

    private boolean isSourceOperation(String opKey) {
        return SOURCE_OPERATIONS.contains(opKey);
    }

    private String getToscaTemplateId(EntitySpec<?> entitySpec) {
        return String.valueOf(entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID));
    }
}
