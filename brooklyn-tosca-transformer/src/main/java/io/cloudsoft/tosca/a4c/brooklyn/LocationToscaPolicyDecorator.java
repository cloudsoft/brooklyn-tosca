package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlLocationResolver;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import alien4cloud.tosca.parser.impl.advanced.GroupPolicyParser;

public class LocationToscaPolicyDecorator extends AbstractToscaPolicyDecorator {

    private Map<String, EntitySpec<?>> specs;
    private ManagementContext mgmt;

    LocationToscaPolicyDecorator(Map<String, EntitySpec<?>> specs, ManagementContext mgmt) {
        this.specs = specs;
        this.mgmt = mgmt;
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
