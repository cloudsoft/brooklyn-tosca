package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.AbstractTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

public abstract class ConfigKeyModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigKeyModifier.class);

    public ConfigKeyModifier(ManagementContext mgmt) {
        super(mgmt);
    }

    protected void configureConfigKeysSpec(EntitySpec spec, ConfigBag bag) {
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
                    // todo: Should this be in the if block?
                    keyNamesUsed.add(r.getConfigKey().getName());
                } catch (Exception e) {
                    String message = String.format("Cannot set config key %s, could not coerce %s to %s",
                            r.getConfigKey(), r.getConfigKeyMaybeValue(), r.getConfigKey().getTypeToken());
                    LOG.warn(message, e);
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

    protected static Map<String, Object> getTemplatePropertyObjects(AbstractTemplate template, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
        return getPropertyObjects(template.getProperties(), paasNodeTemplate, builtPaaSNodeTemplates, keywordMap);
    }

    protected static Map<String, Object> getPropertyObjects(Map<String, AbstractPropertyValue> propertyValueMap, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
        Map<String, Object> propertyMap = MutableMap.of();
        ImmutableSet<String> propertyKeys = ImmutableSet.copyOf(propertyValueMap.keySet());

        for (String propertyKey : propertyKeys) {
            propertyMap.put(propertyKey,
                    resolve(propertyValueMap, propertyKey, paasNodeTemplate, builtPaaSNodeTemplates, keywordMap).orNull());
        }
        return propertyMap;
    }

    /**
     * Searches for config keys in the type, additional interfaces and the implementation (if specified)
     */
    private static Collection<FlagUtils.FlagConfigKeyAndValueRecord> findAllFlagsAndConfigKeys(EntitySpec<? extends Entity> spec, ConfigBag bagFlags) {
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

}
