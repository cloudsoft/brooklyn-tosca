package io.cloudsoft.tosca.metadata;

import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class BrooklynToscaTypeProvider implements ToscaTypeProvider {

    private Map<String, String> typeMapping = ImmutableMap.<String, String>builder()
            .put("org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database")
            .put("org.apache.brooklyn.entity.webapp.tomcat.TomcatServer", "brooklyn.nodes.WebServer")
            .put("org.apache.brooklyn.entity.webapp.tomcat.Tomcat8Server", "brooklyn.nodes.WebServer")
            .build();

    @Override
    public Optional<String> getToscaType(String type) {
        return Optional.fromNullable(typeMapping.get(type));
    }

}
