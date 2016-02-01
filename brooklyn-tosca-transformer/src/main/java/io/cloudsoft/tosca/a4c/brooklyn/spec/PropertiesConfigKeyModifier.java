package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class PropertiesConfigKeyModifier extends ConfigKeyModifier {

    @Inject
    public PropertiesConfigKeyModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        String computeName = toscaApplication.getNodeName(nodeId).or(String.valueOf(entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID)));
        Map<String, Object> templatePropertyObjects = getToscaFacade().getTemplatePropertyObjects(nodeId, toscaApplication, computeName);
        configureConfigKeysSpec(entitySpec, ConfigBag.newInstance(templatePropertyObjects));
    }
}
