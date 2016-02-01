package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;

import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class RelationshipModifier extends ConfigKeyModifier {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipModifier.class);

    @Inject
    public RelationshipModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        Map<String, Object> propertiesAndTypedValues = Collections.emptyMap();
        for (String requirementId : toscaApplication.getRequirements(nodeId)) {
            String computeName = toscaApplication.getNodeName(nodeId).or(getToscaTemplateId(entitySpec));
            propertiesAndTypedValues = getToscaFacade().getPropertiesAndTypeValues(nodeId, toscaApplication, requirementId, computeName);
        }
        configureConfigKeysSpec(entitySpec, ConfigBag.newInstance(propertiesAndTypedValues));
    }

    private String getToscaTemplateId(EntitySpec<?> entitySpec) {
        return String.valueOf(entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID));
    }
}
