package alien4cloud.brooklyn.metadata;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class BrooklynToscaTypeProvider implements ToscaTypeProvider {

    private Map<String, String> typeMapping = ImmutableMap.of(
            "org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database"
    );

    @Override
    public Optional<String> getToscaType(String type) {
        return Optional.fromNullable(typeMapping.get(type));
    }

}
