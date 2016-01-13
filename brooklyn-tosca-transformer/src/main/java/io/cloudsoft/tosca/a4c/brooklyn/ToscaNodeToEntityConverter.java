package io.cloudsoft.tosca.a4c.brooklyn;

import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampCatalogUtils;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class ToscaNodeToEntityConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaNodeToEntityConverter.class);

    private static final ImmutableList<String> VALID_INTERFACE_NAMES=
            ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");
    private static final String EXPANDED_FOLDER= "/expanded/";

    private final ManagementContext mgnt;
    private String nodeId;
    private IndexedArtifactToscaElement indexedNodeTemplate;
    private NodeTemplate nodeTemplate;
    private CsarFileRepository csarFileRepository;
    private String env = Strings.EMPTY;
    private Topology topology;
    private TopologyTreeBuilderService treeBuilder;

    private ToscaNodeToEntityConverter(ManagementContext mgmt) {
        this.mgnt = mgmt;
    }

    public static ToscaNodeToEntityConverter with(ManagementContext mgmt) {
        return new ToscaNodeToEntityConverter(mgmt);
    }

    public ToscaNodeToEntityConverter setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public ToscaNodeToEntityConverter setNodeTemplate(NodeTemplate nodeTemplate) {
        this.nodeTemplate = nodeTemplate;
        return this;
    }

    public ToscaNodeToEntityConverter setIndexedNodeTemplate(IndexedArtifactToscaElement indexedNodeTemplate) {
        this.indexedNodeTemplate = indexedNodeTemplate;
        return this;
    }

    public ToscaNodeToEntityConverter setCsarFileRepository(CsarFileRepository csarFileRepository) {
        this.csarFileRepository = csarFileRepository;
        return this;
    }

    public ToscaNodeToEntityConverter setTopology(Topology topology) {
        this.topology = topology;
        return this;
    }

    public ToscaNodeToEntityConverter setTreeBuilder(TopologyTreeBuilderService treeBuilder) {
        this.treeBuilder = treeBuilder;
        return this;
    }

    public EntitySpec<? extends Entity> createSpec(boolean hasMultipleChildren) {
        if (this.nodeTemplate == null) {
            throw new IllegalStateException("TOSCA node template is missing. You must specify it by using the method #setNodeTemplate(NodeTemplate nodeTemplate)");
        } else if (StringUtils.isEmpty(this.nodeId)) {
            throw new IllegalStateException("TOSCA node ID is missing. You must specify it by using the method #setNodeId(String nodeId)");
        }

        // TODO: decide on how to behave if indexedNodeTemplate.getElementId is abstract.
        // Currently we create a VanillaSoftwareProcess.

        EntitySpec<?> spec;
        CatalogItem catalogItem = CatalogUtils.getCatalogItemOptionalVersion(this.mgnt, this.nodeTemplate.getType());
        if (catalogItem != null) {
            log.info("Found Brooklyn catalog item that match node type: " + this.nodeTemplate.getType());
            spec = (EntitySpec<?>) this.mgnt.getCatalog().createSpec(catalogItem);

        } else if (indexedNodeTemplate.getDerivedFrom().contains(NormativeComputeConstants.COMPUTE_TYPE)) {
            spec = hasMultipleChildren ? EntitySpec.create(SameServerEntity.class)
                    : EntitySpec.create(BasicApplication.class);

        } else {
            try {
                log.info("Found Brooklyn entity that match node type: " + this.nodeTemplate.getType());
                spec = EntitySpec.create((Class<? extends Entity>) Class.forName(this.nodeTemplate.getType()));

            } catch (ClassNotFoundException e) {
                log.info("Cannot find any Brooklyn catalog item nor Brooklyn entities that match node type: " +
                        this.nodeTemplate.getType() + ". Defaulting to a VanillaSoftwareProcess");
                spec = EntitySpec.create(VanillaSoftwareProcess.class);
            }
        }

        // Applying name from the node template or its ID
        if (Strings.isNonBlank(this.nodeTemplate.getName())) {
            spec.displayName(this.nodeTemplate.getName());
        } else {
            spec.displayName(this.nodeId);
        }
        // Add TOSCA node type as a property
        spec.configure("tosca.node.type", this.nodeTemplate.getType());
        spec.configure("tosca.template.id", this.nodeId);
        // Use the nodeId as the camp.template.id to enable DSL lookup
        spec.configure(BrooklynCampConstants.PLAN_ID, this.nodeId);

        Map<String, AbstractPropertyValue> properties = this.nodeTemplate.getProperties();
        // Applying provisioning properties
        ConfigBag prov = ConfigBag.newInstance();
        prov.putIfNotNull(JcloudsLocationConfig.MIN_RAM, resolve(properties, "mem_size").orNull());
        prov.putIfNotNull(JcloudsLocationConfig.MIN_DISK, resolve(properties, "disk_size").orNull());
        prov.putIfNotNull(JcloudsLocationConfig.MIN_CORES, TypeCoercions.coerce(resolve(properties, "num_cpus").orNull(), Integer.class));
        prov.putIfNotNull(JcloudsLocationConfig.OS_FAMILY, TypeCoercions.coerce(resolve(properties, "os_distribution").orNull(), OsFamily.class));
        prov.putIfNotNull(JcloudsLocationConfig.OS_VERSION_REGEX, (String)resolve(properties, "os_version").orNull());
        // TODO: Mapping for "os_arch" and "os_type" are missing
        spec.configure(SoftwareProcess.PROVISIONING_PROPERTIES, prov.getAllConfig());

        configurePropertiesForSpec(spec, nodeTemplate);
        configureArtifactsForSpec(spec, nodeTemplate);

        configureRuntimeEnvironment(spec);

        // If the entity spec is of type VanillaSoftwareProcess, we assume that it's running. The operations should
        // then take care of setting up the correct scripts.
        if (spec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
            spec.configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "true");
            spec.configure(VanillaSoftwareProcess.STOP_COMMAND, "true");
            spec.configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true");
        }

        // Applying operations
        final Map<String, Operation> operations = getStandardInterfaceOperations();
        if (!operations.isEmpty()) {
            if (spec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
                applyLifecycle(operations, ToscaNodeLifecycleConstants.CREATE, spec, VanillaSoftwareProcess.INSTALL_COMMAND);
                applyLifecycle(operations, ToscaNodeLifecycleConstants.CONFIGURE, spec, VanillaSoftwareProcess.CUSTOMIZE_COMMAND);
                applyLifecycle(operations, ToscaNodeLifecycleConstants.START, spec, VanillaSoftwareProcess.LAUNCH_COMMAND);
                applyLifecycle(operations, ToscaNodeLifecycleConstants.STOP, spec, VanillaSoftwareProcess.STOP_COMMAND);
                if (!operations.isEmpty()) {
                    log.warn("Could not translate some operations for " + this.nodeId + ": " + operations.keySet());
                }
            }
        }

        Map<String, Object> propertiesAndTypedValues = Collections.emptyMap();
        for (String requirementId: nodeTemplate.getRequirements().keySet()){
            RelationshipTemplate relationshipTemplate = findRelationshipRequirement(nodeTemplate, requirementId);
            if (relationshipTemplate != null && relationshipTemplate.getType().equals("brooklyn.relationships.Configure")) {

                Map<String, Object> relationProperties = getTemplatePropertyObjects(relationshipTemplate);

                // TODO: Use target properly.
                String target = relationshipTemplate.getTarget();
                String propName = relationProperties.get("prop.name").toString();
                String propCollection = relationProperties.get("prop.collection").toString();
                String propValue = relationProperties.get("prop.value").toString();

                if (Strings.isBlank(propCollection) && (Strings.isBlank(propName))) {
                    throw new IllegalStateException("Relationship for Requirement "
                            + relationshipTemplate.getRequirementName() + " on NodeTemplate "
                            + nodeTemplate.getName() + ". Collection Name or Property Name should" +
                            " be defined for RelationsType " + relationshipTemplate.getType());
                }

                Map<String, String> simpleProperty = null;
                if (!Strings.isBlank(propName)) {
                    simpleProperty = ImmutableMap.of(propName, propValue);
                }
                if (simpleProperty == null) {
                    propertiesAndTypedValues = ImmutableMap.<String, Object>of(
                            propCollection, ImmutableList.of(propValue));
                } else {
                    propertiesAndTypedValues = ImmutableMap.<String, Object>of(
                            propCollection, simpleProperty);
                }
            }
        }

        configureConfigKeysSpec(spec, ConfigBag.newInstance(propertiesAndTypedValues));

        return spec;
    }

    private RelationshipTemplate findRelationshipRequirement(NodeTemplate node, String requirementId) {
        if (node.getRelationships() != null) {
            for (Map.Entry<String, RelationshipTemplate> entry : node.getRelationships().entrySet()) {
                if (entry.getValue().getRequirementName().equals(requirementId)) {
                    return entry.getValue();
                }
            }
        }
        log.warn("Requirement {} is not described by any relationship ", requirementId);
        return null;
    }

    //TODO PROVISION_PROPERTIES should be added to this method.
    private void configurePropertiesForSpec(EntitySpec spec, NodeTemplate nodeTemplate){
        ConfigBag bag = ConfigBag.newInstance(getTemplatePropertyObjects(nodeTemplate));
        // now set configuration for all the items in the bag
        configureConfigKeysSpec(spec, bag);
    }

    private void configureArtifactsForSpec(EntitySpec<?> spec, NodeTemplate nodeTemplate) {
        Map<String, Object> a= MutableMap.of();
        Map<String, DeploymentArtifact> artifacts = nodeTemplate.getArtifacts();
        if (artifacts != null) {
            for (Map.Entry<String, DeploymentArtifact> entry : artifacts.entrySet()) {
                a.put(entry.getKey(), entry.getValue().getArtifactRef());
            }
            ConfigBag bag = ConfigBag.newInstance(a);
            configureConfigKeysSpec(spec, bag);
        }
    }

    private void configureConfigKeysSpec(EntitySpec spec, ConfigBag bag){
        Collection<FlagUtils.FlagConfigKeyAndValueRecord> records = findAllFlagsAndConfigKeys(spec, bag);
        Set<String> keyNamesUsed = new LinkedHashSet<>();

        for (FlagUtils.FlagConfigKeyAndValueRecord r : records) {
            if (r.getFlagMaybeValue().isPresent()) {
                Optional<Object> resolvedValue = resolveValue(r.getFlagMaybeValue().get(), Optional.<TypeToken>absent());
                if (resolvedValue.isPresent()) {
                    spec.configure(r.getFlagName(), resolvedValue.get());
                }
                keyNamesUsed.add(r.getFlagName());
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                try {
                    Optional<Object> resolvedValue = resolveValue(r.getConfigKeyMaybeValue().get(), Optional.<TypeToken>of(r.getConfigKey().getTypeToken()));
                    if (resolvedValue.isPresent()) {
                        spec.configure(r.getConfigKey(), resolvedValue.get());
                    }
                    keyNamesUsed.add(r.getConfigKey().getName());
                } catch (Exception e) {
                    log.warn("Cannot set config key {}, could not coerce {} to {}",
                            new Object[]{r.getConfigKey(), r.getConfigKeyMaybeValue(), r.getConfigKey().getTypeToken()});
                }
            }
        }

        // set unused keys as anonymous config keys -
        // they aren't flags or known config keys, so must be passed as config keys in order for
        // EntitySpec to know what to do with them (as they are passed to the spec as flags)
        for (String key : MutableSet.copyOf(bag.getUnusedConfig().keySet())) {
            // we don't let a flag with the same name as a config key override the config key
            // (that's why we check whether it is used)
            if (!keyNamesUsed.contains(key)) {
                //Object transformed = new BrooklynComponentTemplateResolver.SpecialFlagsTransformer(loader).apply(bag.getStringKey(key));
                spec.configure(ConfigKeys.newConfigKey(Object.class, key.toString()), bag.getStringKey(key));
            }
        }
    }

    private Optional<Object> resolveValue(Object unresolvedValue, Optional<TypeToken> desiredType) {
        if (unresolvedValue == null) {
            return Optional.absent();
        }
        // The 'dsl' key is arbitrary, but the interpreter requires a map
        Map<String, Object> resolvedConfigMap = CampCatalogUtils.getCampPlatform(mgnt).pdp().applyInterpreters(ImmutableMap.of("dsl", unresolvedValue));
        return Optional.of(desiredType.isPresent()
                ? TypeCoercions.coerce(resolvedConfigMap.get("dsl"), desiredType.get())
                : resolvedConfigMap.get("dsl"));
    }

    /**
     * Searches for config keys in the type, additional interfaces and the implementation (if specified)
     */
    private Collection<FlagUtils.FlagConfigKeyAndValueRecord> findAllFlagsAndConfigKeys(EntitySpec<? extends Entity> spec, ConfigBag bagFlags) {
        Set<FlagUtils.FlagConfigKeyAndValueRecord> allKeys = MutableSet.of();
        allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, spec.getType(), bagFlags));
        if (spec.getImplementation() != null) {
            allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, spec.getImplementation(), bagFlags));
        }
        for (Class<?> iface : spec.getAdditionalInterfaces()) {
            allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, iface, bagFlags));
        }
        return allKeys;
    }

    private Map<String, Object> getTemplatePropertyObjects(NodeTemplate template) {
        return getPropertyObjects(template.getProperties());
    }

    private Map<String, Object> getTemplatePropertyObjects(RelationshipTemplate template) {
        return getPropertyObjects(template.getProperties());
    }

    private Map<String, Object> getPropertyObjects(Map<String, AbstractPropertyValue> propertyValueMap) {
        Map<String, Object> propertyMap = MutableMap.of();
        ImmutableSet<String> propertyKeys = ImmutableSet.copyOf(propertyValueMap.keySet());

        for (String propertyKey : propertyKeys) {
            propertyMap.put(propertyKey,
                    resolve(propertyValueMap, propertyKey).orNull());
        }
        return propertyMap;
    }
    
    protected Map<String, Operation> getStandardInterfaceOperations() {
        Map<String, Operation> operations = MutableMap.of();

        if (indexedNodeTemplate.getInterfaces() != null) {
            MutableMap<String, Interface> indexedNodeTemplateInterfaces =
                    MutableMap.copyOf(indexedNodeTemplate.getInterfaces());
            Interface indexedNodeTemplateInterface = findInterfaceOfNodeTemplate(
                    indexedNodeTemplateInterfaces, VALID_INTERFACE_NAMES);

            if (indexedNodeTemplateInterface != null) {
                Interface nodeTemplateInterface = findInterfaceOfNodeTemplate(
                        MutableMap.copyOf(nodeTemplate.getInterfaces()), VALID_INTERFACE_NAMES);
                for (Map.Entry<String, Operation> entry :
                        indexedNodeTemplateInterface.getOperations().entrySet()) {
                    String operationName = entry.getKey();
                    Operation operation = entry.getValue();

                    if ((nodeTemplateInterface != null) &&
                            (!Strings.isBlank(
                                    getOpImplArtifactRef(nodeTemplateInterface, operationName)))) {
                        operation = nodeTemplateInterface.getOperations().get(operationName);
                    }
                    operations.put(operationName, operation);
                }
            }
        }
        return operations;
    }

    private Interface findInterfaceOfNodeTemplate(Map<String, Interface> nodeTemplateInterfaces,
                                                              List<String> validInterfaceNames){
        for(String interfaceName: validInterfaceNames){
            if(nodeTemplateInterfaces.containsKey(interfaceName)){
                return nodeTemplateInterfaces.get(interfaceName);
            }
        }
        return null;
    }

    private ImplementationArtifact getOpImplArtifact(Interface interfaze, String operationName){
        ImplementationArtifact result = null;
        if(interfaze.getOperations().containsKey(operationName)){
            result = interfaze.getOperations().get(operationName).getImplementationArtifact();
        }
        return result;
    }

    private String getOpImplArtifactRef(Interface interfaze, String operationName){
        String result = null;
        ImplementationArtifact implArtifact = getOpImplArtifact(interfaze, operationName);
        if (implArtifact != null) {
            result = implArtifact.getArtifactRef();
        }
        return result;
    }

    protected void applyLifecycle(Map<String, Operation> ops, String opKey, EntitySpec<? extends Entity> spec, ConfigKey<String> cmdKey) {
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
                } catch (CSARVersionNotFoundException | NullPointerException  e) {
                    script = new ResourceUtils(this).getResourceAsString(ref);
                }

                Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
                String computeName = (nodeTemplate.getName()!=null) ? nodeTemplate.getName() : (String) spec.getFlags().get("tosca.template.id");
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

                spec.configure(cmdKey, inputBuilder.toString() + env + "\n" + script);
            } else {
                log.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no ref");
            }
        } else {
            log.warn("Unsupported operation implementation for " + opKey + ":  artifact has no impl");
        }
    }

    protected void configureRuntimeEnvironment(EntitySpec<?> entitySpec) {
        if (indexedNodeTemplate.getArtifacts() == null) {
            return;
        }

        final Map<String, String> filesToCopy = MutableMap.of();
        final List<String> preInstallCommands = MutableList.of();
        final List<String> envCommands = MutableList.of();

        for (final Map.Entry<String, DeploymentArtifact> artifactEntry : indexedNodeTemplate.getArtifacts().entrySet()) {
            if (artifactEntry.getValue() == null) {
                continue;
            }
            if (!"tosca.artifacts.File".equals(artifactEntry.getValue().getArtifactType())) {
                continue;
            }

            final String destRoot = Os.mergePaths("~", "brooklyn-tosca-resources", artifactEntry.getValue().getArtifactName());
            final String tempRoot = Os.mergePaths("/tmp", artifactEntry.getValue().getArtifactName());

            preInstallCommands.add("mkdir -p " + destRoot);
            preInstallCommands.add("mkdir -p " + tempRoot);
            envCommands.add(String.format("export %s=%s", artifactEntry.getValue().getArtifactName(), destRoot));

            try {
                Path csarPath = csarFileRepository.getCSAR(artifactEntry.getValue().getArchiveName(), artifactEntry.getValue().getArchiveVersion());

                Path resourcesRootPath = Paths.get(csarPath.getParent().toAbsolutePath().toString(), "expanded", artifactEntry.getKey());
                Files.walkFileTree(resourcesRootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String tempDest = Os.mergePaths(tempRoot, file.getFileName().toString());
                        String finalDest = Os.mergePaths(destRoot, file.getFileName().toString());
                        filesToCopy.put(file.toAbsolutePath().toString(), tempDest);
                        preInstallCommands.add(String.format("mv %s %s", tempDest, finalDest));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (CSARVersionNotFoundException e) {
                log.warn("CSAR " + artifactEntry.getValue().getArtifactName() + ":" + artifactEntry.getValue().getArchiveVersion() + " does not exists", e);
            } catch (IOException e) {
                log.warn("Cannot parse CSAR resources", e);
            }
        }

        env = Joiner.on("\n").join(envCommands) + "\n";
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_COMMAND, Joiner.on("\n").join(preInstallCommands) + "\n" + env);
    }

    public static Optional<Object> resolve(Map<String, ? extends IValue> props, String key) {
        IValue v = props.get(key);
        if (v instanceof ScalarPropertyValue) {
            return Optional.<Object>fromNullable(((ScalarPropertyValue) v).getValue());
        }
        if (v instanceof ComplexPropertyValue) {
            return Optional.<Object>fromNullable(((ComplexPropertyValue) v).getValue());
        }
        if (!(v instanceof FunctionPropertyValue)) {
            log.warn("Ignoring unsupported property value " + v);
        }
        return Optional.absent();
    }

    public Optional<Object> resolve(Map<String, ? extends IValue> props, String key, PaaSNodeTemplate template, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates) {
        Optional<Object> value = resolve(props, key);
        if (!value.isPresent()) {
            value = Optional.<Object>fromNullable(FunctionEvaluator.evaluateGetPropertyFunction((FunctionPropertyValue) props.get(key), template, builtPaaSNodeTemplates));
        }
        return value;
    }

}
