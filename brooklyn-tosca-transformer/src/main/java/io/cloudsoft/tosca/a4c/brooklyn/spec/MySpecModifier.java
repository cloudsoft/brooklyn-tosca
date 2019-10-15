package io.cloudsoft.tosca.a4c.brooklyn.spec;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class MySpecModifier  extends AbstractSpecModifier {

    @Inject
    public MySpecModifier (ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {

    }
}
