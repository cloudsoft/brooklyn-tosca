package io.cloudsoft.tosca.a4c.brooklyn;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampCatalogUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.TypeCoercions;

import java.util.Map;

public abstract class AbstractToscaPolicyDecorator implements ToscaPolicyDecorator {

    protected ManagementContext mgmt;

    public AbstractToscaPolicyDecorator(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    /**
     * Returns policy properties. In this case, type or name are not considered as properties.
     *
     * @param policyData
     */
    public Map<String, ?> getPolicyProperties(Map<String, ?> policyData) {
        Map<String, ?> data = MutableMap.copyOf(policyData);
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        return resolveProperties(data);
    }

    private Map<String, Object> resolveProperties(Map<String, ?> policyProperties) {
        Map<String, Object> resolvedPolicyProperties = MutableMap.of();
        for (Map.Entry<String, ?> entry : policyProperties.entrySet()) {
            Optional<Object> resolvedValue =
                    resolveValue(entry.getValue(), Optional.<TypeToken>absent());
            if (resolvedValue.isPresent()) {
                resolvedPolicyProperties.put(entry.getKey(), resolvedValue.get());
            } else {
                resolvedPolicyProperties.put(entry.getKey(), entry.getValue());
            }
        }
        return resolvedPolicyProperties;
    }

    private Optional<Object> resolveValue(Object unresolvedValue, Optional<TypeToken> desiredType) {
        if (unresolvedValue == null) {
            return Optional.absent();
        }
        // The 'dsl' key is arbitrary, but the interpreter requires a map
        Map<String, Object> resolvedConfigMap = CampCatalogUtils.getCampPlatform(mgmt)
                .pdp()
                .applyInterpreters(ImmutableMap.of("dsl", unresolvedValue));
        return Optional.of(desiredType.isPresent()
                ? TypeCoercions.coerce(resolvedConfigMap.get("dsl"), desiredType.get())
                : resolvedConfigMap.get("dsl"));
    }

}
