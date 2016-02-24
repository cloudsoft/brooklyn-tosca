package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.sensor.StaticSensor;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class TemplateAttributesModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateAttributesModifier.class);

    @Inject
    public TemplateAttributesModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        LOG.info("Generating EntityInitializers for static attributes on " + entitySpec);
        Map<String, String> resolvedAttributes = getToscaFacade().getResolvedAttributes(nodeId, toscaApplication);
        for (Map.Entry<String, String> attribute : resolvedAttributes.entrySet()) {
            if(!Strings.isBlank(attribute.getValue())) {
                entitySpec.addInitializer(new StaticSensor<String>(ConfigBag.newInstance()
                        .configure(StaticSensor.SENSOR_NAME, attribute.getKey())
                        .configure(StaticSensor.STATIC_VALUE, attribute.getValue())));
            }
        }
    }
}
