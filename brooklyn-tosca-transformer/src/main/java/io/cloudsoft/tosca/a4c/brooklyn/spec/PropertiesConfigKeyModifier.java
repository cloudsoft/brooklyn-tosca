package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.springframework.stereotype.Component;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

@Component
public class PropertiesConfigKeyModifier extends ConfigKeyModifier {

    @Inject
    public PropertiesConfigKeyModifier(ManagementContext mgmt) {
        super(mgmt);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        ConfigBag bag = ConfigBag.newInstance(getTemplatePropertyObjects(nodeTemplate));
        // now set configuration for all the items in the bag
        configureConfigKeysSpec(entitySpec, bag);
    }

}
