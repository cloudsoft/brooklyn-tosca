package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public abstract class AbstractToscaConverter {

    private static final Logger log = LoggerFactory.getLogger(AbstractToscaConverter.class);

    @SuppressWarnings("unused")
    protected ManagementContext mgmt;

    public AbstractToscaConverter(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    public abstract EntitySpec<? extends Entity> toSpec(String id, NodeTemplate t);



    public static String resolveScalarProperty(Map<String, AbstractPropertyValue> props, String ...keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.get(key);
            if (v==null) continue;
            if (v instanceof ScalarPropertyValue) return ((ScalarPropertyValue)v).getValue();
            log.warn("Ignoring unsupported property value "+v);
        }
        return null;
    }

    public static List<String> resolveListProperty(Map<String, AbstractPropertyValue> props, String ...keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.get(key);
            if (v==null) continue;
            if (v instanceof ScalarPropertyValue) return ImmutableList.of(((ScalarPropertyValue) v).getValue());
            log.warn("Ignoring unsupported property value "+v);
        }
        return null;
    }

    //TODO this method should be moved to a Property Management Class
    public static String resolve(Map<String, AbstractPropertyValue> props, String ...keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.get(key);
            if (v==null) continue;
            if (v instanceof ScalarPropertyValue) return ((ScalarPropertyValue)v).getValue();
            log.warn("Ignoring unsupported property value "+v);
        }
        return null;
    }
}
