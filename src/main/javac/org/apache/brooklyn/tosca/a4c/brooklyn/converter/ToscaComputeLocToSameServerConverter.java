package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ToscaComputeLocToSameServerConverter extends AbstractToscaConverter {

    private static final Logger log = LoggerFactory.getLogger(ToscaComputeLocToSameServerConverter.class);

    public ToscaComputeLocToSameServerConverter(ManagementContext mgmt) {
        super(mgmt);
    }

    public EntitySpec<SameServerEntity> toSpec(String id, NodeTemplate t) {
        EntitySpec<SameServerEntity> spec = EntitySpec.create(SameServerEntity.class);

        if (Strings.isNonBlank( t.getName() )) {
            spec.displayName(t.getName());
        } else {
            spec.displayName(id);
        }

        applyProvisioningProperties(t, spec);

        spec.configure("tosca.node.type", t.getType());

        // just assume it's running
        spec.configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "true");
        spec.configure(VanillaSoftwareProcess.STOP_COMMAND, "true");
        spec.configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true");

        String locationString = resolve(t.getProperties(), "location");
        Location specLocation = resolveLocationFromString(locationString);
        spec.location(specLocation);

        return spec;
    }

    //TODO: this method was copied from ToscaComputeToVanillaConverter. Refactoring
    private void applyProvisioningProperties(NodeTemplate t, EntitySpec<SameServerEntity> spec) {
        Map<String, AbstractPropertyValue> props = t.getProperties();
        // e.g.:
//        num_cpus: 1
//        disk_size: 10 GB
//        mem_size: 4 MB
        ConfigBag prov = ConfigBag.newInstance();
        prov.putIfNotNull(JcloudsLocationConfig.MIN_RAM, resolve(props, "mem_size"));
        prov.putIfNotNull(JcloudsLocationConfig.MIN_DISK, resolve(props, "disk_size"));
        prov.putIfNotNull(JcloudsLocationConfig.MIN_CORES, TypeCoercions.coerce(resolve(props, "num_cpus"), Integer.class));
        // TODO support OS selection

        spec.configure(SoftwareProcess.PROVISIONING_PROPERTIES, prov.getAllConfig());
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
