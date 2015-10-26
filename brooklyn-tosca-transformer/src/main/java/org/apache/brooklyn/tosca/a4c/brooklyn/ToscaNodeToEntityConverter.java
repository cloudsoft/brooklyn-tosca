package org.apache.brooklyn.tosca.a4c.brooklyn;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;

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
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampUtils;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ToscaNodeToEntityConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaNodeToEntityConverter.class);

    private final ManagementContext mgnt;
    private NodeTemplate nodeTemplate;
    private String nodeId;

    private ToscaNodeToEntityConverter(ManagementContext mgmt) {
        this.mgnt = mgmt;
    }

    public static ToscaNodeToEntityConverter with(ManagementContext mgmt) {
        return new ToscaNodeToEntityConverter(mgmt);
    }

    public ToscaNodeToEntityConverter setNodeTemplate(NodeTemplate nodeTemplate) {
        this.nodeTemplate = nodeTemplate;
        return this;
    }

    public ToscaNodeToEntityConverter setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public EntitySpec<? extends Entity> createSpec() {
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

        configureSpec(spec, nodeTemplate);

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

        return spec;
    }

    //TODO: refactor this method in Brooklyn in {@link org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver}
    //TODO PROVISION_PROPERTIES should be added to this method.
    private void configureSpec(EntitySpec spec, NodeTemplate nodeTemplate){
        Set<String> keyNamesUsed = new LinkedHashSet<>();
        ConfigBag bag = ConfigBag.newInstance(getTemplatePropertyObjects(nodeTemplate));

        // now set configuration for all the items in the bag
        Collection<FlagUtils.FlagConfigKeyAndValueRecord> records = findAllFlagsAndConfigKeys(spec, bag);

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
        Map<String, Object> resolvedConfigMap = CampUtils.getCampPlatform(mgnt).pdp().applyInterpreters(ImmutableMap.of("dsl", unresolvedValue));
        return Optional.of(desiredType.isPresent()
                ? TypeCoercions.coerce(resolvedConfigMap.get("dsl"), desiredType.get())
                : resolvedConfigMap.get("dsl"));
    }

    /**
     * Searches for config keys in the type, additional interfaces and the implementation (if specified)
     */
    private Collection<FlagUtils.FlagConfigKeyAndValueRecord> findAllFlagsAndConfigKeys(EntitySpec<?> spec, ConfigBag bagFlags) {
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
        Map<String, Object> propertyMap = MutableMap.of();
        ImmutableSet<String> propertyKeys = ImmutableSet.copyOf(template.getProperties().keySet());

        for (String propertyKey : propertyKeys) {
            propertyMap.put(propertyKey,
                    resolve(template.getProperties(), propertyKey));
        }
        return propertyMap;
    }

    protected Map<String, Operation> getInterfaceOperations() {
        final Map<String, Operation> operations = MutableMap.of();

        if (this.nodeTemplate.getInterfaces() != null) {
            final ImmutableList<String> validInterfaceNames = ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");
            final MutableMap<String, Interface> interfaces = MutableMap.copyOf(this.nodeTemplate.getInterfaces());

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
                // TODO get script/artifact relative to CSAR
                String script = new ResourceUtils(this).getResourceAsString(ref);
                String setScript = (String) spec.getConfig().get(cmdKey);
                if (Strings.isBlank(setScript) || setScript.trim().equals("true")) {
                    setScript = script;
                } else {
                    setScript += "\n"+script;
                }
                spec.configure(cmdKey, setScript);
                return;
            }
            log.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no ref");
            return;
        }
        log.warn("Unsupported operation implementation for " + opKey + ": " + artifact + " has no impl");

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
