package io.cloudsoft.tosca.a4c.brooklyn;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;

import java.util.*;

/**
 * This class BrooklynToscaPolicyDecorator to provide a similar way to declare enrichers for entities declared in a TOSCA blueprint
 */
public class BrooklynToscaEnricherDecorator extends  BrooklynToscaPolicyDecorator {

    BrooklynToscaEnricherDecorator(EntitySpec<? extends Application> rootSpec, ManagementContext mgmt) {
       super(rootSpec,mgmt);
    }

    @Override
    public Map<String, ?> getPolicyProperties(Map<String, ?> policyData){
        Map<String, ?> data = MutableMap.copyOf(policyData);
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        // Had to drop the generics, because conversion was a pain
        Map props = (Map)data.remove("properties");
        if (props!=null)  {
            data.putAll(props);
        }
        return data;
    }

    @Override
    protected ConfigBag getDefinition(String type, Map<String, ?> enricherData) {
        List<?> enrichers = ImmutableList.of(ImmutableMap.of(
                "enricherType", type,
                BrooklynCampReservedKeys.BROOKLYN_CONFIG, getPolicyProperties(enricherData)
                )
        );
        Map<?, ?> enricherDefinition = ImmutableMap.of(BrooklynCampReservedKeys.BROOKLYN_ENRICHERS, enrichers);
        return ConfigBag.newInstance(enricherDefinition);
    }

    @Override
    protected void decorateWithSpec(BrooklynYamlTypeInstantiator.Factory yamlLoader, EntitySpec<?> spec, ConfigBag enricherDefinition) {
        new BrooklynEntityDecorationResolver.EnricherSpecResolver(yamlLoader).decorate(spec, enricherDefinition, null);
    }

}
