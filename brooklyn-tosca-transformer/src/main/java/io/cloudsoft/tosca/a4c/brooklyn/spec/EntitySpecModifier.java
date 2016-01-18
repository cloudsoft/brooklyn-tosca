package io.cloudsoft.tosca.a4c.brooklyn.spec;

import org.apache.brooklyn.api.entity.EntitySpec;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

/**
 * A SpecModifier applies a set of changes to an entity spec with reference to a node
 * template and a topology.
 */
public interface EntitySpecModifier {

    void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology);

}
