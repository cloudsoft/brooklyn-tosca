package io.cloudsoft.tosca.a4c.brooklyn.spec;

import org.apache.brooklyn.api.entity.EntitySpec;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;

/**
 * An EntitySpecFactory chooses the most appropriate {@link EntitySpec}.
 */
public interface EntitySpecFactory<A extends ToscaApplication> {
    EntitySpec<?> create(String nodeId, A toscaApplication, boolean hasMultipleChildren);
}
