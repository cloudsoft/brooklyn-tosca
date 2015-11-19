package alien4cloud.brooklyn.metadata;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class DefaultToscaMetadataProvider extends AbstractToscaMetadataProvider {

    private Map<String, String> typeMapping = ImmutableMap.of(
            "org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database"
    );

    @Override
    public String findToscaType(String type) {
        if(!typeMapping.containsKey(type) && !hasNext()) {
            return "brooklyn.nodes.SoftwareProcess";
        }

        if(!typeMapping.containsKey(type)){
            return next().findToscaType(type);
        }

        return typeMapping.get(type);
    }
}
