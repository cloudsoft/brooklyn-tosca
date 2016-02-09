package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public abstract class ConfigKeyModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigKeyModifier.class);

    public ConfigKeyModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    protected void configureConfigKeysSpec(EntitySpec spec, ConfigBag bag) {
        Set<String> keyNamesUsed = new LinkedHashSet<>();
        configureWithAllRecords(findAllFlagsAndConfigKeys(spec, bag), spec, keyNamesUsed);
        setUnusedKeysAsAnonymousKeys(spec, keyNamesUsed, bag);
    }

    private void configureWithAllRecords(Collection<FlagUtils.FlagConfigKeyAndValueRecord> records, EntitySpec spec, Set<String> keyNamesUsed){
        for (FlagUtils.FlagConfigKeyAndValueRecord r : records) {
            if (r.getFlagMaybeValue().isPresent()) {
                configureWithResolvedFlag(r, spec, keyNamesUsed);
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                configureWithResolvedConfigKey(r, spec, keyNamesUsed);
            }
        }
    }

    private void configureWithResolvedFlag(FlagUtils.FlagConfigKeyAndValueRecord r, EntitySpec spec, Set<String> keyNamesUsed){
        Optional<Object> resolvedValue = resolveValue(r.getFlagMaybeValue().get(), Optional.<TypeToken>absent());
        if (resolvedValue.isPresent()) {
            spec.configure(r.getFlagName(), resolvedValue.get());
        }
        keyNamesUsed.add(r.getFlagName());
    }

    private void configureWithResolvedConfigKey(FlagUtils.FlagConfigKeyAndValueRecord r, EntitySpec spec, Set<String> keyNamesUsed){
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

    private void setUnusedKeysAsAnonymousKeys(EntitySpec spec, Set<String> keyNamesUsed, ConfigBag bag){
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

}
