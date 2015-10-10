package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import alien4cloud.model.topology.NodeTemplate;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;

import java.util.Map;

public class ToscaComputeLocToVanillaConverter extends ToscaComputeToVanillaConverter {

    public ToscaComputeLocToVanillaConverter(ManagementContext mgmt) {
        super(mgmt);
    }

    public EntitySpec<VanillaSoftwareProcess> toSpec(String id, NodeTemplate t) {
        EntitySpec<VanillaSoftwareProcess> spec = super.toSpec(id, t);

        if (Strings.isNonBlank(t.getName())) {
            spec.displayName(t.getName());
        } else {
            spec.displayName(id);
        }

        spec.configure("tosca.node.type", t.getType());

        String locationString = resolve(t.getProperties(), "location");
        Location specLocation = resolveLocationFromString(locationString);
        spec.location(specLocation);

        return spec;
    }

    //TODO refactor this method was copied from BrooklynYamlLocationResolver
    public Location resolveLocationFromString(String location) {
        if (Strings.isBlank(location)) return null;
        return resolveLocation(location, MutableMap.of());
    }

    //TODO refactor this method was copied from BrooklynYamlLocationResolver
    protected Location resolveLocation(String spec, Map<?,?> flags) {
        LocationDefinition ldef = mgmt.getLocationRegistry().getDefinedLocationByName((String)spec);
        if (ldef!=null)
            // found it as a named location
            return mgmt.getLocationRegistry().resolve(ldef, null, flags).get();

        Maybe<Location> l = mgmt.getLocationRegistry().resolve(spec, null, flags);
        if (l.isPresent()) return l.get();

        RuntimeException exception = ((Maybe.Absent<?>)l).getException();
        throw new IllegalStateException("Illegal parameter for 'location' ("+spec+"); not resolvable: "+
                Exceptions.collapseText(exception), exception);
    }

}
