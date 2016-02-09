package io.cloudsoft.tosca.a4c.brooklyn.spec;

import org.apache.brooklyn.api.entity.EntitySpec;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;

/**
 * A SpecModifier applies a set of changes to an entity spec with reference to a node
 * template and a topology.
 */
public interface EntitySpecModifier {

    void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication);

}
