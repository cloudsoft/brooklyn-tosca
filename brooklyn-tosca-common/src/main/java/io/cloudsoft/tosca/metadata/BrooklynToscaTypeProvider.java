package io.cloudsoft.tosca.metadata;

import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class BrooklynToscaTypeProvider implements ToscaTypeProvider {

    private Map<String, String> typeMapping = ImmutableMap.of(
            "org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database"
    );

    @Override
    public Optional<String> getToscaType(String type) {
        return Optional.fromNullable(typeMapping.get(type));
    }

}
