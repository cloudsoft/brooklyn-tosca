package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.nio.file.Path;
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
import com.google.common.collect.ImmutableMap;

import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;
import io.cloudsoft.tosca.a4c.brooklyn.util.NodeTemplates;

// TODO: Handle interfaces differently.
@Component
public class StandardInterfaceLifecycleModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(StandardInterfaceLifecycleModifier.class);

    private static final ImmutableList<String> VALID_INTERFACE_NAMES =
            ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");

    private static final Map<String, ConfigKey<String>> lifeCycleMapping = ImmutableMap.of(
            ToscaNodeLifecycleConstants.CREATE, VanillaSoftwareProcess.INSTALL_COMMAND,
            ToscaNodeLifecycleConstants.CONFIGURE, VanillaSoftwareProcess.CUSTOMIZE_COMMAND,
            ToscaNodeLifecycleConstants.START, VanillaSoftwareProcess.LAUNCH_COMMAND,
            ToscaNodeLifecycleConstants.STOP, VanillaSoftwareProcess.STOP_COMMAND
    );
    private static final String EXPANDED_FOLDER = "/expanded/";

    private final TopologyTreeBuilderService treeBuilder;
    private final CsarFileRepository csarFileRepository;

    @Inject
    public StandardInterfaceLifecycleModifier(ManagementContext mgmt, TopologyTreeBuilderService treeBuilder, CsarFileRepository csarFileRepository) {
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
        for (String opKey : operations.keySet()) {
            if (!lifeCycleMapping.containsKey(opKey)) {
                LOG.warn("Could not translate operation, {}, for node template, {}.", opKey, nodeTemplate.getName());
                continue;
            }

            Operation op = operations.get(opKey);
            applyLifecycle(op, entitySpec, lifeCycleMapping.get(opKey), nodeTemplate, topology);
        }
    }

    protected Map<String, Operation> getStandardInterfaceOperations(NodeTemplate nodeTemplate, Topology topology) {
        Map<String, Operation> operations = MutableMap.of();
        IndexedArtifactToscaElement indexedNodeTemplate = getIndexedNodeTemplate(nodeTemplate, topology).get();

        Optional<Interface> optionalIndexedNodeTemplateInterface = NodeTemplates.findInterfaceOfNodeTemplate(
                indexedNodeTemplate.getInterfaces(), VALID_INTERFACE_NAMES);

        Optional<Interface> optionalNodeTemplateInterface = NodeTemplates.findInterfaceOfNodeTemplate(
                nodeTemplate.getInterfaces(), VALID_INTERFACE_NAMES);

        if (optionalIndexedNodeTemplateInterface.isPresent()) {
            operations = MutableMap.copyOf(optionalIndexedNodeTemplateInterface.get().getOperations());
        }

        if (optionalNodeTemplateInterface.isPresent()) {
            operations.putAll(optionalNodeTemplateInterface.get().getOperations());
        }
        return operations;
    }

    protected void applyLifecycle(Operation op, EntitySpec<? extends Entity> spec,
            ConfigKey<String> cmdKey, NodeTemplate nodeTemplate, Topology topology) {

        ImplementationArtifact artifact = op.getImplementationArtifact();
        if (artifact == null) {
            LOG.warn("Unsupported operation implementation for " + op.getDescription() + ":  artifact has no impl");
            return;
        }

        String ref = artifact.getArtifactRef();
        if (ref == null) {
            LOG.warn("Unsupported operation implementation for " + op.getDescription() + ": " + artifact + " has no ref");
            return;
        }

        String script = getScript(artifact.getArchiveName(), artifact.getArchiveVersion(), ref);

        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        String computeName = (nodeTemplate.getName() != null) ? nodeTemplate.getName() : (String) spec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID);
        PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
        spec.configure(cmdKey, buildExportStatements(nodeTemplate, op, paasNodeTemplate, builtPaaSNodeTemplates) + "\n" + script);
    }

    private String buildExportStatements(NodeTemplate nodeTemplate, Operation op, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates) {
        StringBuilder inputBuilder = new StringBuilder();
        Map<String, IValue> inputParameters = op.getInputParameters();
        if (inputParameters != null) {
            for (Map.Entry<String, IValue> entry : inputParameters.entrySet()) {
                Map<String, String> keywordMap = MutableMap.of(
                        "SELF", nodeTemplate.getName()
                        // TODO: "HOST" ->  root of the “HostedOn” relationship chain
                );
                Optional<Object> value = resolve(inputParameters, entry.getKey(), paasNodeTemplate, builtPaaSNodeTemplates, keywordMap);
                inputBuilder.append(String.format("export %s=\"%s\"\n", entry.getKey(), value.or("")));
            }
        }
        return inputBuilder.toString();
    }

    private String getScript(String archiveName, String archiveVersion, String artifactRef){
        String script;
        // Trying to get the CSAR file based on the artifact reference. If it fails, then we try to get the
        // content of the script from any resources
        try {
            Path csarPath = csarFileRepository.getCSAR(archiveName, archiveVersion);
            script = new ResourceUtils(this).getResourceAsString(csarPath.getParent().toString() + EXPANDED_FOLDER + artifactRef);
        } catch (CSARVersionNotFoundException | NullPointerException e) {
            script = new ResourceUtils(this).getResourceAsString(artifactRef);
        }
        return script;
    }


}
