package io.cloudsoft.tosca.metadata;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;

import java.util.List;
import java.util.Map;

public class BrooklynToscaTypeProvider implements ToscaTypeProvider, RequiresBrooklynApi {

    private Map<String, String> typeMapping = ImmutableMap.<String, String>builder()
            .put("org.apache.brooklyn.entity.proxy.LoadBalancer", "brooklyn.nodes.LoadBalancer")
            .put("org.apache.brooklyn.entity.database.DatabaseNode", "brooklyn.nodes.Database")
            .put("org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database")
            .put("org.apache.brooklyn.entity.webapp.WebAppService", "brooklyn.nodes.WebServer")
            .build();

    private BrooklynApi brooklynApi;

    @Override
    public void setBrooklynApi(BrooklynApi brooklynApi) {
        this.brooklynApi = brooklynApi;
    }

    @Override
    public Optional<String> getToscaType(String type, String version) {
        try {
            CatalogEntitySummary catalogEntitySummary = brooklynApi.getCatalogApi().getEntity(type, version);
            for (Object o : catalogEntitySummary.getTags()) {
                Map<String, ?> map = (Map<String, ?>) o;
                if (map.containsKey("traits")) {
                    List<String> traits = (List<String>) map.get("traits");
                    for (String key : typeMapping.keySet()) {
                        if (traits.contains(key)) {
                            return Optional.of(typeMapping.get(key));
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
        return Optional.fromNullable(typeMapping.get(type));
    }

}
