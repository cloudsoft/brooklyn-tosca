package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.springframework.stereotype.Component;

import com.google.common.base.Functions;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class NormativeTypesAttributesModifier extends AbstractSpecModifier {

    @Inject
    public NormativeTypesAttributesModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {

        // for tosca.capabilities.Endpoint and derivatives (and alien Compute)
        entitySpec.enricher(Enrichers.builder().transforming(Attributes.ADDRESS)
                .computing(Functions.identity())
                .publishing(Sensors.newStringSensor("ip_address")).build());

        entitySpec.enricher(Enrichers.builder().transforming(Attributes.SUBNET_ADDRESS)
                .computing(Functions.identity())
                .publishing(Sensors.newStringSensor("private_address")).build());

        entitySpec.enricher(Enrichers.builder().transforming(Attributes.ADDRESS)
                .computing(Functions.identity())
                .publishing(Sensors.newStringSensor("public_address")).build());

        // alien specific
        entitySpec.enricher(Enrichers.builder().transforming(Attributes.ADDRESS)
                .computing(Functions.identity())
                .publishing(Sensors.newStringSensor("public_ip_address")).build());


        // open up any ports that end with "_port" in addition to the default ".port"
        entitySpec.configure(SoftwareProcess.INBOUND_PORTS_CONFIG_REGEX, ".*[\\._]port$");
    }
}
