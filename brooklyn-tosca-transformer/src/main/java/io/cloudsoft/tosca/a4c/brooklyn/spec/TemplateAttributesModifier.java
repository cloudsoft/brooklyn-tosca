package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;
import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.sensor.StaticSensor;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import alien4cloud.model.components.IValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

@Component
public class TemplateAttributesModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateAttributesModifier.class);

    private final TopologyTreeBuilderService treeBuilder;

    @Inject
    public TemplateAttributesModifier(ManagementContext mgmt, TopologyTreeBuilderService treeBuilder) {
        super(mgmt);
        this.treeBuilder = treeBuilder;
    }

    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        if (!entitySpec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
            LOG.debug("Not applying attributes to {}: only {} is currently supported", entitySpec, VanillaSoftwareProcess.class.getName());
            return;
        }
        LOG.info("Generating EntityInitializers for static attributes on " + entitySpec);
        Optional<PaaSNodeTemplate> optionalPaaSNodeTemplate = getPaasNodeTemplate(nodeTemplate, topology);
        if (optionalPaaSNodeTemplate.isPresent()) {
            Map<String, PaaSNodeTemplate> allNodes = treeBuilder.buildPaaSTopology(topology).getAllNodes();
            final Map<String, IValue> attributes = getIndexedNodeTemplate(nodeTemplate, topology).get().getAttributes();
            for (Map.Entry<String, IValue> attribute : attributes.entrySet()) {
                String value = FunctionEvaluator.parseAttribute(
                        attribute.getKey(),
                        attribute.getValue(),
                        topology,
                        ImmutableMap.<String, Map<String, InstanceInformation>>of(),
                        "",
                        optionalPaaSNodeTemplate.get(),
                        allNodes);
                final String sensorName = attribute.getKey().replaceAll("\\s+", ".");
                entitySpec.addInitializer(new StaticSensor<String>(ConfigBag.newInstance()
                        .configure(StaticSensor.SENSOR_NAME, sensorName)
                        .configure(StaticSensor.STATIC_VALUE, value)));
            }
        }
    }

    private Optional<PaaSNodeTemplate> getPaasNodeTemplate(NodeTemplate nodeTemplate, Topology topology) {
        PaaSTopology paaSTopology = treeBuilder.buildPaaSTopology(topology);
        if (paaSTopology != null) {
            Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = paaSTopology.getAllNodes();
            String computeName = nodeTemplate.getName();
            return Optional.of(builtPaaSNodeTemplates.get(computeName));
        }
        return Optional.absent();
    }

}
