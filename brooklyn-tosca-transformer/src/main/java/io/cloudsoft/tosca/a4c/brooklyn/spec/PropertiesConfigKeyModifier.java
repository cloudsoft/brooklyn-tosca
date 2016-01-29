package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;

@Component
public class PropertiesConfigKeyModifier extends ConfigKeyModifier {

    private final TopologyTreeBuilderService treeBuilder;

    @Inject
    public PropertiesConfigKeyModifier(ManagementContext mgmt, TopologyTreeBuilderService treeBuilder) {
        super(mgmt);
        this.treeBuilder = treeBuilder;
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        String computeName = (nodeTemplate.getName() != null) ? nodeTemplate.getName() : (String) entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID);
        PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
        Map<String, String> keywordMap = MutableMap.of(
                "SELF", nodeTemplate.getName()
                // TODO: "HOST" ->  root of the “HostedOn” relationship chain
        );
        ConfigBag bag = ConfigBag.newInstance(getTemplatePropertyObjects(nodeTemplate, paasNodeTemplate, builtPaaSNodeTemplates, keywordMap));
        // now set configuration for all the items in the bag
        configureConfigKeysSpec(entitySpec, bag);
    }

}
