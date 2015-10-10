package org.apache.brooklyn.tosca.a4c.brooklyn.converter;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;

public class ToscaComputeToVanillaConverter extends AbstractToscaConverter{

    private static final Logger log = LoggerFactory.getLogger(ToscaComputeToVanillaConverter.class);
    
    public ToscaComputeToVanillaConverter(ManagementContext mgmt) {
        super(mgmt);
    }
    
    public EntitySpec<VanillaSoftwareProcess> toSpec(String id, NodeTemplate t) {
        EntitySpec<VanillaSoftwareProcess> spec = EntitySpec.create(VanillaSoftwareProcess.class);
        
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
        
        applyLifecyle(id, t, spec);
        
        return spec;
    }

    private void applyProvisioningProperties(NodeTemplate t, EntitySpec<VanillaSoftwareProcess> spec) {
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

    private void applyLifecyle(String id, NodeTemplate t, EntitySpec<VanillaSoftwareProcess> spec) {
        
//  C.6.3.1 Definition
//
//  tosca.interfaces.node.lifecycle.Standard:
//    create:
//      description: Standard lifecycle create operation.
//    configure:
//      description: Standard lifecycle configure operation.
//    start:
//      description: Standard lifecycle start operation.
//    stop:
//      description: Standard lifecycle stop operation.
//    delete:
//      description: Standard lifecycle delete operation.
        
        Map<String, Operation> ops = MutableMap.of();

      // first get interface operations on type (may not be necessary if A4C is smart about merging them?)
      // will have to look them up on platform however
//      if (root.getNodeTypes()!=null) {
//          IndexedNodeType type = root.getNodeTypes().get(t.getType());
//          if (type!=null && type.getInterfaces()!=null) {
//              MutableMap<String, Interface> ifs = MutableMap.copyOf(type.getInterfaces());
//              Interface ifa = null;
//              if (ifa==null) ifa = ifs.remove("tosca.interfaces.node.lifecycle.Standard");
//              if (ifa==null) ifa = ifs.remove("standard");
//              if (ifa==null) ifs.remove("Standard");
//              
//              if (ifa!=null) {
//                  ops.putAll(ifa.getOperations());
//              }
//              
//              if (!ifs.isEmpty()) {
//                  log.warn("Could not translate some interfaces for "+id+": "+ifs.keySet());
//              }
//          }
//      }

        // then get interface operations from node template
        if (t.getInterfaces()!=null) {
            MutableMap<String, Interface> ifs = MutableMap.copyOf(t.getInterfaces());
            Interface ifa = null;
            if (ifa==null) ifa = ifs.remove("tosca.interfaces.node.lifecycle.Standard");
            if (ifa==null) ifa = ifs.remove("standard");
            if (ifa==null) ifs.remove("Standard");

            if (ifa!=null) {
                ops.putAll(ifa.getOperations());
            }

            if (!ifs.isEmpty()) {
                log.warn("Could not translate some interfaces for "+id+": "+ifs.keySet());
            }          
        }

        applyLifecycle(ops, "create", spec, VanillaSoftwareProcess.INSTALL_COMMAND);
        applyLifecycle(ops, "configure", spec, VanillaSoftwareProcess.CUSTOMIZE_COMMAND);
        applyLifecycle(ops, "start", spec, VanillaSoftwareProcess.LAUNCH_COMMAND);
        applyLifecycle(ops, "stop", spec, VanillaSoftwareProcess.STOP_COMMAND);

        if (!ops.isEmpty()) {
            log.warn("Could not translate some operations for "+id+": "+ops.keySet());
        }

    }

    private void applyLifecycle(Map<String, Operation> ops, String opKey, EntitySpec<VanillaSoftwareProcess> spec, ConfigKey<String> cmdKey) {
        Operation op = ops.remove(opKey);
        if (op==null) return;
        ImplementationArtifact artifact = op.getImplementationArtifact();
        if (artifact!=null) {
            String ref = artifact.getArtifactRef();
            if (ref!=null) {
                // TODO get script/artifact relative to CSAR
                String script = new ResourceUtils(this).getResourceAsString(ref);
                String setScript = (String) spec.getConfig().get(VanillaSoftwareProcess.INSTALL_COMMAND);
                if (Strings.isBlank(setScript) || setScript.trim().equals("true")) {
                    setScript = script;
                } else {
                    setScript += "\n"+script;
                }
                spec.configure(cmdKey, setScript);
                return;
            }
            log.warn("Unsupported operation implementation for "+opKey+": "+artifact+" has no ref");
            return;
        }
        log.warn("Unsupported operation implementation for "+opKey+": "+artifact+" has no impl");
        return;
    }

    public static String resolve(Map<String, AbstractPropertyValue> props, String ...keys) {
        for (String key: keys) {
            AbstractPropertyValue v = props.get(key);
            if (v==null) continue;
            if (v instanceof ScalarPropertyValue) return ((ScalarPropertyValue)v).getValue();
            log.warn("Ignoring unsupported property value "+v);
        }
        return null;
    }
}
