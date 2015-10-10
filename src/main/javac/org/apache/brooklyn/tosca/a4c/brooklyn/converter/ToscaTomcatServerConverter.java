package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ToscaTomcatServerConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaComputeToVanillaConverter.class);

    @SuppressWarnings("unused")
    private ManagementContext mgmt;

    public ToscaTomcatServerConverter(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    public EntitySpec<TomcatServer> toSpec(String id, NodeTemplate t) {
        EntitySpec<TomcatServer> spec = EntitySpec.create(TomcatServer.class);

        if (Strings.isNonBlank(t.getName())) {
            spec.displayName(t.getName());
        } else {
            spec.displayName(id);
        }

        spec.configure("tosca.node.type", t.getType());
        Map<String, AbstractPropertyValue> properties = t.getProperties();

        spec.configure(TomcatServer.ROOT_WAR, resolveScalarProperty(t.getProperties(), "wars.root"));
        spec.configure(TomcatServer.HTTP_PORT, PortRanges.fromString(resolveScalarProperty(t.getProperties(), "http.port")));

        return spec;
    }

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


}
