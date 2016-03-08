package io.cloudsoft.tosca.a4c.brooklyn;

import alien4cloud.tosca.parser.impl.advanced.GroupPolicyParser;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlLocationResolver;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocationToscaPolicyDecorator extends AbstractToscaPolicyDecorator {

    private Map<String, EntitySpec<?>> specs;

    LocationToscaPolicyDecorator(Map<String, EntitySpec<?>> specs, ManagementContext mgmt) {
        super(mgmt);
        this.specs = specs;
    }

    public void decorate(Map<String, ?> policyData, String policyName, Optional<String> type, Set<String> groupMembers) {
        List<Location> locations = getLocations(policyData);
        for (String id : groupMembers) {
            EntitySpec<?> spec = specs.get(id);
            if (spec == null) {
                throw new IllegalStateException("No node " + id + " found, when setting locations");
            }
            spec.locations(locations);
        }
    }

    private List<Location> getLocations(Map<String, ?> policyData) {
        Object data = policyData.containsKey(GroupPolicyParser.VALUE)
                ? policyData.get(GroupPolicyParser.VALUE)
                : getPolicyProperties(policyData);
        return resolveLocations(ImmutableMap.of("location", data));
    }

    private List<Location> resolveLocations(Map<String, ?> locations) {
        return new BrooklynYamlLocationResolver(mgmt).resolveLocations(locations, true);
    }
}
