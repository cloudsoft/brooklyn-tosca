package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ToscaNodeTemplateToEntityConverter extends AbstractToscaConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaNodeTemplateToEntityConverter.class);

    @SuppressWarnings("unused")
    private ManagementContext mgmt;

    public ToscaNodeTemplateToEntityConverter(ManagementContext mgmt) {
        super(mgmt);
    }

    public EntitySpec<?> toSpec(String id, NodeTemplate t) {

        Class clazz = null;
        try {
            clazz = Class.forName(t.getType());
        } catch (ClassNotFoundException e) {
            throw new PropagatedRuntimeException(e);
        }

        EntitySpec<? extends EntitySpec> spec = EntitySpec.create(clazz);

        if (Strings.isNonBlank(t.getName())) {
            spec.displayName(t.getName());
        } else {
            spec.displayName(id);
        }

        spec.configure("tosca.node.type", t.getType());
        populateSpec(spec, t);

        return spec;
    }

    //The code of this method was copied from Brooklyn {@link BrooklynComponentTemplateResolver}
    //TODO: this method only can manage String properties. BrooklynComponentTemplateResolver should be used to fix it.
    public void populateSpec(EntitySpec<? extends Entity> spec, NodeTemplate t) {

        Map<String, Object> templateProperties = getTemplateProperties(t);

        //these properties are not managed here for now
        templateProperties.remove("host");
        templateProperties.remove("location");

        ConfigBag bag = ConfigBag.newInstance(templateProperties);

        // now set configuration for all the items in the bag
        Collection<FlagUtils.FlagConfigKeyAndValueRecord> records = findAllFlagsAndConfigKeys(spec, bag);
        Set<String> keyNamesUsed = new LinkedHashSet<String>();
        for (FlagUtils.FlagConfigKeyAndValueRecord r : records) {
            if (r.getFlagMaybeValue().isPresent()) {
                //Object transformed = new BrooklynComponentTemplateResolver.SpecialFlagsTransformer(loader).apply(r.getFlagMaybeValue().get());
                spec.configure(r.getFlagName(), r.getFlagMaybeValue().get());
                keyNamesUsed.add(r.getFlagName());
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                //Object transformed = new BrooklynComponentTemplateResolver.SpecialFlagsTransformer(loader).apply(r.getConfigKeyMaybeValue().get());
                spec.configure((ConfigKey<Object>) r.getConfigKey(), r.getConfigKeyMaybeValue().get());
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

    public Map<String, Object> getTemplateProperties(NodeTemplate template) {
        Map<String, Object> propertyMap = MutableMap.of();
        Map<String, AbstractPropertyValue> templateProperties = template.getProperties();

        for (String key : templateProperties.keySet()) {
            propertyMap.put(key, resolve(templateProperties, key));
        }
        return propertyMap;
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

}
