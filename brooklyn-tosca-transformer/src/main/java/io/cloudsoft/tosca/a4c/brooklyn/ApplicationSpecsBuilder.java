package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;

/**
 * An ApplicationSpecsBuilder is used to create Brooklyn EntitySpecs from A Tosca Application.  Once the specs have been
 * created, it is used to convert Tosca Policies into Brooklyn Policies
 * @param <A> The type of ToscaApplication
 */
public interface ApplicationSpecsBuilder<A extends ToscaApplication> {

    String TOSCA_TEMPLATE_ID = "tosca.template.id";

    Map<String, EntitySpec<?>> getSpecs(A toscaApplication);

    void addPolicies(EntitySpec<? extends Application> rootSpec, A toscaApplication, Map<String, EntitySpec<?>> specs);
}
