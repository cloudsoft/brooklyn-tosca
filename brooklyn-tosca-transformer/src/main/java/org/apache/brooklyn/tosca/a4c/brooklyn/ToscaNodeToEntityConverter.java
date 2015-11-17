package org.apache.brooklyn.tosca.a4c.brooklyn;

import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class ToscaNodeToEntityConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaNodeToEntityConverter.class);

    private final ManagementContext mgnt;
    private String nodeId;
    private IndexedArtifactToscaElement indexedNodeTemplate;
    private NodeTemplate nodeTemplate;
    private CsarFileRepository csarFileRepository;

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

    public EntitySpec<? extends Entity> createSpec(boolean hasMultipleChildren) {
        if (this.nodeTemplate == null) {
            throw new IllegalStateException("TOSCA node template is missing. You must specify it by using the method #setNodeTemplate(NodeTemplate nodeTemplate)");
        }
        if (StringUtils.isEmpty(this.nodeId)) {
            throw new IllegalStateException("TOSCA node ID is missing. You must specify it by using the method #setNodeId(String nodeId)");
        }

        EntitySpec<?> spec = null;

        CatalogItem catalogItem = CatalogUtils.getCatalogItemOptionalVersion(this.mgnt, this.nodeTemplate.getType());
        if (catalogItem != null) {
            log.info("Found Brooklyn catalog item that match node type: " + this.nodeTemplate.getType());
            spec = (EntitySpec<?>) this.mgnt.getCatalog().createSpec(catalogItem);
        } else if (indexedNodeTemplate.getDerivedFrom().contains("tosca.nodes.Compute")) {
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
        prov.putIfNotNull(JcloudsLocationConfig.MIN_RAM, resolve(properties, "mem_size"));
        prov.putIfNotNull(JcloudsLocationConfig.MIN_DISK, resolve(properties, "disk_size"));
        prov.putIfNotNull(JcloudsLocationConfig.MIN_CORES, TypeCoercions.coerce(resolve(properties, "num_cpus"), Integer.class));
        prov.putIfNotNull(JcloudsLocationConfig.OS_FAMILY, TypeCoercions.coerce(resolve(properties, "os_distribution"), OsFamily.class));
        prov.putIfNotNull(JcloudsLocationConfig.OS_VERSION_REGEX, (String)resolve(properties, "os_version"));
        // TODO: Mapping for "os_arch" and "os_type" are missing
        spec.configure(SoftwareProcess.PROVISIONING_PROPERTIES, prov.getAllConfig());

        configurePropertiesForSpec(spec, nodeTemplate);

        configureRuntimeEnvironment(spec);

        // If the entity spec is of type VanillaSoftwareProcess, we assume that it's running. The operations should
        // then take care of setting up the correct scripts.
        if (spec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
            spec.configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "true");
            spec.configure(VanillaSoftwareProcess.STOP_COMMAND, "true");
            spec.configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true");
        }

        // Applying operations
        final Map<String, Operation> operations = getInterfaceOperations();
        if (!operations.isEmpty()) {
            if (!spec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
                throw new IllegalStateException("Brooklyn entity: " + spec.getImplementation() +
                        " does not support interface operations defined by node template" + this.nodeTemplate.getType());
            }

            applyLifecycle(operations, "create", spec, VanillaSoftwareProcess.INSTALL_COMMAND);
            applyLifecycle(operations, "configure", spec, VanillaSoftwareProcess.CUSTOMIZE_COMMAND);
            applyLifecycle(operations, "start", spec, VanillaSoftwareProcess.LAUNCH_COMMAND);
            applyLifecycle(operations, "stop", spec, VanillaSoftwareProcess.STOP_COMMAND);

            if (!operations.isEmpty()) {
                log.warn("Could not translate some operations for " + this.nodeId + ": " + operations.keySet());
            }
        }

        //This is only a fist prototype.
        Map<String, Object> propertiesAndTypedValues = MutableMap.of();
        //ProcessConfigurationRequirement
        for(String requirementId: nodeTemplate.getRequirements().keySet()){
            RelationshipTemplate relationshipTemplate =
                    findRelationshipRequirement(nodeTemplate, requirementId);
            if((relationshipTemplate!=null)
                    &&(relationshipTemplate.getType().equals("tosca.relationships.Configure"))){

                Map<String, Object> relationProperties = getTemplatePropertyObjects(relationshipTemplate);

                String target = relationshipTemplate.getTarget();
                String propName= (String)relationProperties.get("prop.name");
                String propCollection= (String)relationProperties.get("prop.collection");
                String propValue= managePropertyTargetNode(target,
                        (String)relationProperties.get("prop.value"));


                if(Strings.isBlank(propCollection)&&(Strings.isBlank(propName))){
                    throw new IllegalStateException("Relationship for Requirement "
                            + relationshipTemplate.getRequirementName() + " on NodeTemplate "
                            + nodeTemplate.getName() + ". Collection Name or Property Name should" +
                            " be defined for RelationsType " + relationshipTemplate.getType());
                }

                Map<String, String> simpleProperty=null;
                if(!Strings.isBlank(propName)){
                    simpleProperty=ImmutableMap.of(propName, propValue);
                }
                if(simpleProperty==null) {
                    propertiesAndTypedValues=
                            ImmutableMap.of(propCollection, ((Object)ImmutableList.of(propValue)));
                } else {
                    propertiesAndTypedValues =
                            ImmutableMap.of(propCollection, (Object)simpleProperty);
                }
            }
        }

        configureConfigKeysSpec(spec, ConfigBag.newInstance(propertiesAndTypedValues));

        return spec;
    }

    private RelationshipTemplate findRelationshipRequirement(NodeTemplate node, String requirementId){
        if(node.getRelationships()!=null){
            for(Map.Entry<String, RelationshipTemplate> entry: node.getRelationships().entrySet()){
                if(entry.getValue().getRequirementName().equals(requirementId)){
                    return entry.getValue();
                }
            }
            log.warn("Requirement {} is not described by any relationship ", requirementId);
        }
        return null;
    }

    private String managePropertyTargetNode(String targetId, String value){
        if(!Strings.containsLiteralIgnoreCase(value, "TARGET")){
            log.warn("TARGET identifier was not found on value {} in value {}", value);
        }
        return value.replaceAll("(?i)TARGET", "\\$brooklyn:component(\""+ targetId+"\")");
    }

    //TODO PROVISION_PROPERTIES should be added to this method.
    private void configurePropertiesForSpec(EntitySpec spec, NodeTemplate nodeTemplate){
        ConfigBag bag = ConfigBag.newInstance(getTemplatePropertyObjects(nodeTemplate));
        // now set configuration for all the items in the bag
        configureConfigKeysSpec(spec, bag);
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
                Optional<Object> resolvedValue = resolveValue(r.getConfigKeyMaybeValue().get(), Optional.<TypeToken>of(r.getConfigKey().getTypeToken()));
                if (resolvedValue.isPresent()) {
                    spec.configure(r.getConfigKey(), resolvedValue.get());
                }
                keyNamesUsed.add(r.getConfigKey().getName());
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
                    resolve(propertyValueMap, propertyKey));
        }
        return propertyMap;
    }


    protected Map<String, Operation> getInterfaceOperations() {
        final Map<String, Operation> operations = MutableMap.of();

        if (indexedNodeTemplate.getInterfaces() != null) {
            final ImmutableList<String> validInterfaceNames = ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");
            final MutableMap<String, Interface> interfaces = MutableMap.copyOf(indexedNodeTemplate.getInterfaces());

            for (String validInterfaceName : validInterfaceNames) {
                Interface validInterface = interfaces.remove(validInterfaceName);
                if (validInterface != null) {
                    operations.putAll(validInterface.getOperations());
                    if (!interfaces.isEmpty()) {
                        log.warn("Could not translate some interfaces for " + this.nodeId + ": " + interfaces.keySet());
                    }
                    break;
                }
            }
        }

        return operations;
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
                String script = null;

                // Trying to get the CSAR file based on the artifact reference. If it fails, then we try to get the
                // content of the script from any resources
                try {
                    Path csarPath = csarFileRepository.getCSAR(artifact.getArchiveName(), artifact.getArchiveVersion());
                    script = new ResourceUtils(this).getResourceAsString(csarPath.getParent().toString() + "/expanded/" + ref);
                } catch (CSARVersionNotFoundException e) {
                    script = new ResourceUtils(this).getResourceAsString(ref);
                }

                spec.configure(cmdKey, script);
                return;
            }
            log.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no ref");
            return;
        }
        log.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no impl");

    }

    protected void configureRuntimeEnvironment(EntitySpec<?> entitySpec) {
        if (indexedNodeTemplate.getArtifacts() == null) {
            return;
        }

        final Map<String, String> filesToCopy = MutableMap.of();
        final List<String> preInstallCommands = MutableList.of();

        for (final Map.Entry<String, DeploymentArtifact> artifactEntry : indexedNodeTemplate.getArtifacts().entrySet()) {
            if (artifactEntry.getValue() == null) {
                continue;
            }
            if (!"tosca.artifacts.File".equals(artifactEntry.getValue().getArtifactType())) {
                continue;
            }

            final String destRoot = Os.mergePaths("~", artifactEntry.getValue().getArtifactName());
            final String tempRoot = Os.mergePaths("/tmp", artifactEntry.getValue().getArtifactName());

            preInstallCommands.add("mkdir -p " + destRoot);
            preInstallCommands.add("mkdir -p " + tempRoot);
            preInstallCommands.add(String.format("export %s=%s", artifactEntry.getValue().getArtifactName(), destRoot));

            try {
                Path csarPath = csarFileRepository.getCSAR(artifactEntry.getValue().getArchiveName(), artifactEntry.getValue().getArchiveVersion());

                Path resourcesRootPath = Paths.get(csarPath.getParent().toAbsolutePath().toString(), "expanded", artifactEntry.getKey());
                Files.find(resourcesRootPath, Integer.MAX_VALUE, new BiPredicate<Path, BasicFileAttributes>() {
                    @Override
                    public boolean test(Path path, BasicFileAttributes basicFileAttributes) {
                        return basicFileAttributes.isRegularFile();
                    }
                }).forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path file) {
                        String tempDest = Os.mergePaths(tempRoot, file.getFileName().toString());
                        String finalDest = Os.mergePaths(destRoot, file.getFileName().toString());
                        filesToCopy.put(file.toAbsolutePath().toString(), tempDest);
                        preInstallCommands.add(String.format("mv %s %s", tempDest, finalDest));
                    }
                });
            } catch (CSARVersionNotFoundException e) {
                log.warn("CSAR " + artifactEntry.getValue().getArtifactName() + ":" + artifactEntry.getValue().getArchiveVersion() + " does not exists", e);
            } catch (IOException e) {
                log.warn("Cannot parse CSAR resources", e);
            }
        }

        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_COMMAND, Joiner.on("\n").join(preInstallCommands));
    }

    public static Object resolve(Map<String, AbstractPropertyValue> props, String... keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.remove(key);
            if (v == null) {
                continue;
            }
            if (v instanceof ScalarPropertyValue) {
                return ((ScalarPropertyValue)v).getValue();
            }
            if (v instanceof ComplexPropertyValue){
                return ((ComplexPropertyValue)v).getValue();
            }
            log.warn("Ignoring unsupported property value " + v);
        }
        return null;
    }
}
