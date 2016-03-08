package io.cloudsoft.tosca.a4c.brooklyn;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.cloudsoft.tosca.a4c.brooklyn.util.EntitySpecs;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.util.core.config.ConfigBag;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrooklynToscaPolicyDecorator extends AbstractToscaPolicyDecorator {

    private EntitySpec<? extends Application> rootSpec;

    BrooklynToscaPolicyDecorator(EntitySpec<? extends Application> rootSpec, ManagementContext mgmt) {
        super(mgmt);
        this.rootSpec = rootSpec;
    }

    public void decorate(Map<String, ?> policyData, String policyName, Optional<String> type, Set<String> groupMembers) {
        if (!type.isPresent()) {
            throw new IllegalStateException("Type was not found for policy " + policyName);
        }
        ConfigBag policyDefinition = getPolicyDefinition(type.get(), policyData);
        decorateEntityBrooklynWithPolicies(rootSpec, groupMembers, policyDefinition, policyName);
    }

    private ConfigBag getPolicyDefinition(String type, Map<String, ?> policyData) {
        List<?> policies = ImmutableList.of(ImmutableMap.of(
                        "policyType", type,
                        BrooklynCampReservedKeys.BROOKLYN_CONFIG, getPolicyProperties(policyData)
                )
        );
        Map<?, ?> policyDefinition = ImmutableMap.of(BrooklynCampReservedKeys.BROOKLYN_POLICIES, policies);
        return ConfigBag.newInstance(policyDefinition);
    }

    private void decorateEntityBrooklynWithPolicies(EntitySpec<? extends Application> appSpec, Set<String> groupMembers, ConfigBag policyDefinition, String policyName) {
        BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
        BrooklynYamlTypeInstantiator.Factory yamlLoader = new BrooklynYamlTypeInstantiator.Factory(loader, this);

        if (groupMembers.isEmpty()) {
            decorateWithPolicy(yamlLoader, appSpec, policyDefinition);
            return;
        }

        for (String specId : groupMembers) {
            EntitySpec<?> spec = EntitySpecs.findChildEntitySpecByPlanId(appSpec, specId);
            if (spec == null) {
                throw new IllegalStateException("Error: NodeTemplate " + specId +
                        " defined by policy " + policyName + " was not found");
            }
            decorateWithPolicy(yamlLoader, spec, policyDefinition);
        }
    }

    private void decorateWithPolicy(BrooklynYamlTypeInstantiator.Factory yamlLoader, EntitySpec<?> spec, ConfigBag policyDefinition) {
        new BrooklynEntityDecorationResolver.PolicySpecResolver(yamlLoader).decorate(spec, policyDefinition);
    }

}
