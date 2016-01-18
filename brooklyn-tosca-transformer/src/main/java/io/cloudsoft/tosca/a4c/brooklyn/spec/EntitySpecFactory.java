package io.cloudsoft.tosca.a4c.brooklyn.spec;

import org.apache.brooklyn.api.entity.EntitySpec;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

/**
 * An EntitySpecFactory chooses the most appropriate {@link EntitySpec} for a given {@link NodeTemplate}
 * in a {@link Topology}.
 */
public interface EntitySpecFactory {
    EntitySpec<?> create(NodeTemplate nodeTemplate, Topology topology, boolean hasMultipleChildren);
}
