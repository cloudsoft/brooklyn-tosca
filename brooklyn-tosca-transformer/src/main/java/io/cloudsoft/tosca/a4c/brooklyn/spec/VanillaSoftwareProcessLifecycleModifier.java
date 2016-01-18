package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

// TODO: Handle interfaces differently.
@Component
public class VanillaSoftwareProcessLifecycleModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaSoftwareProcessLifecycleModifier.class);

    private static final ImmutableList<String> VALID_INTERFACE_NAMES =
            ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");

    private static final String EXPANDED_FOLDER = "/expanded/";

    private final TopologyTreeBuilderService treeBuilder;
    private final CsarFileRepository csarFileRepository;

    @Inject
    public VanillaSoftwareProcessLifecycleModifier(ManagementContext mgmt, TopologyTreeBuilderService treeBuilder, CsarFileRepository csarFileRepository) {
        super(mgmt);
        this.treeBuilder = treeBuilder;
        this.csarFileRepository = csarFileRepository;
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        if (!entitySpec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
            return;
        }
        // If the entity spec is of type VanillaSoftwareProcess, we assume that it's running. The operations should
        // then take care of setting up the correct scripts.
        entitySpec.configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "true");
        entitySpec.configure(VanillaSoftwareProcess.STOP_COMMAND, "true");
        entitySpec.configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true");

        // Applying operations
        final Map<String, Operation> operations = getStandardInterfaceOperations(nodeTemplate, topology);
        if (!operations.isEmpty()) {
            applyLifecycle(operations, ToscaNodeLifecycleConstants.CREATE, entitySpec, VanillaSoftwareProcess.INSTALL_COMMAND, nodeTemplate, topology);
            applyLifecycle(operations, ToscaNodeLifecycleConstants.CONFIGURE, entitySpec, VanillaSoftwareProcess.CUSTOMIZE_COMMAND, nodeTemplate, topology);
            applyLifecycle(operations, ToscaNodeLifecycleConstants.START, entitySpec, VanillaSoftwareProcess.LAUNCH_COMMAND, nodeTemplate, topology);
            applyLifecycle(operations, ToscaNodeLifecycleConstants.STOP, entitySpec, VanillaSoftwareProcess.STOP_COMMAND, nodeTemplate, topology);
            if (!operations.isEmpty()) {
                LOG.warn("Could not translate some operations for " + nodeTemplate + ": " + operations.keySet());
            }
        }
    }

    protected Map<String, Operation> getStandardInterfaceOperations(NodeTemplate nodeTemplate, Topology topology) {
        Map<String, Operation> operations = MutableMap.of();
        Interface indexedNodeTemplateInterface = findInterfaceOfNodeTemplate(
                getIndexedNodeTemplate(nodeTemplate, topology).get().getInterfaces(), VALID_INTERFACE_NAMES);
        Interface nodeTemplateInterface = findInterfaceOfNodeTemplate(
                nodeTemplate.getInterfaces(), VALID_INTERFACE_NAMES);

        if ((indexedNodeTemplateInterface != null)
                && (indexedNodeTemplateInterface.getOperations() != null)) {
            operations = MutableMap.copyOf(indexedNodeTemplateInterface.getOperations());
        }

        if ((nodeTemplateInterface != null) && (nodeTemplateInterface.getOperations() != null)) {
            for (String operationName : nodeTemplateInterface.getOperations().keySet()) {
                if (operations.containsKey(operationName)) {
                    operations.put(operationName,
                            nodeTemplateInterface.getOperations().get(operationName));
                }
            }
        }
        return operations;
    }

    protected void applyLifecycle(Map<String, Operation> ops, String opKey, EntitySpec<? extends Entity> spec,
            ConfigKey<String> cmdKey, NodeTemplate nodeTemplate, Topology topology) {
        Operation op = ops.remove(opKey);
        if (op == null) {
            return;
        }
        ImplementationArtifact artifact = op.getImplementationArtifact();
        if (artifact != null) {
            String ref = artifact.getArtifactRef();
            if (ref != null) {
                String script;

                // Trying to get the CSAR file based on the artifact reference. If it fails, then we try to get the
                // content of the script from any resources
                try {
                    Path csarPath = csarFileRepository.getCSAR(artifact.getArchiveName(), artifact.getArchiveVersion());
                    script = new ResourceUtils(this)
                            .getResourceAsString(csarPath.getParent().toString() + EXPANDED_FOLDER + ref);
                } catch (CSARVersionNotFoundException | NullPointerException e) {
                    script = new ResourceUtils(this).getResourceAsString(ref);
                }

                Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
                String computeName = (nodeTemplate.getName() != null) ? nodeTemplate.getName()
                                                                      : (String) spec.getFlags().get("tosca.template.id");
                PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);

                StringBuilder inputBuilder = new StringBuilder();
                Map<String, IValue> inputParameters = op.getInputParameters();
                if (inputParameters != null) {
                    for (Map.Entry<String, IValue> entry : inputParameters.entrySet()) {
                        // case keyword SOURCE used on a NodeType
                        Optional<Object> value = resolve(inputParameters, entry.getKey(), paasNodeTemplate, builtPaaSNodeTemplates);
                        inputBuilder.append(String.format("export %s=%s%n", entry.getKey(), value.or("")));
                    }
                }

                spec.configure(cmdKey, inputBuilder.toString() + "\n" + script);
            } else {
                LOG.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no ref");
            }
        } else {
            LOG.warn("Unsupported operation implementation for " + opKey + ":  artifact has no impl");
        }
    }

    private Interface findInterfaceOfNodeTemplate(Map<String, Interface> nodeTemplateInterfaces,
            List<String> validInterfaceNames) {
        if (nodeTemplateInterfaces != null) {
            for (String interfaceName : validInterfaceNames) {
                if (nodeTemplateInterfaces.containsKey(interfaceName)) {
                    return nodeTemplateInterfaces.get(interfaceName);
                }
            }
        }
        return null;
    }

}
