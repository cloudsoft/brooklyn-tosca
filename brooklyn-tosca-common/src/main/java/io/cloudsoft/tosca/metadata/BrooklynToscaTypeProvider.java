package io.cloudsoft.tosca.metadata;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class BrooklynToscaTypeProvider implements ToscaTypeProvider, RequiresBrooklynApi {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynToscaTypeProvider.class);

    private Map<String, String> typeMapping = ImmutableMap.<String, String>builder()
            .put("org.apache.brooklyn.entity.proxy.LoadBalancer", "brooklyn.nodes.LoadBalancer")
            .put("org.apache.brooklyn.entity.database.DatabaseNode", "brooklyn.nodes.Database")
            .put("org.apache.brooklyn.entity.database.mysql.MySqlNode", "brooklyn.nodes.Database")
            .put("org.apache.brooklyn.entity.webapp.WebAppService", "brooklyn.nodes.WebServer")
            .build();

    private BrooklynApi brooklynApi;

    public void setTypeMapping(Map<String, String> typeMapping) {
        this.typeMapping = typeMapping;
    }

    @Override
    public void setBrooklynApi(BrooklynApi brooklynApi) {
        this.brooklynApi = brooklynApi;
    }

    @Override
    public Optional<String> getToscaType(String type, String version) {
        try {
            CatalogEntitySummary catalogEntitySummary = brooklynApi.getCatalogApi().getEntity(type, version);
            Collection<Object> tags = Objects.firstNonNull(catalogEntitySummary.getTags(), ImmutableList.of());
            for (Object o : tags) {
                Map<String, ?> map = (Map<String, ?>) o;
                if (map.containsKey("traits")) {
                    Collection<String> traits = ImmutableSet.copyOf((Collection<String>) map.get("traits"));
                    for (String key : typeMapping.keySet()) {
                        if (traits.contains(key)) {
                            return Optional.of(typeMapping.get(key));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.info("Error getting entity {}: {}", type, e);
        }
        return Optional.fromNullable(typeMapping.get(type));
    }

}
