package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import org.apache.brooklyn.util.collections.MutableMap;

public abstract class AbstractToscaPolicyDecorator implements ToscaPolicyDecorator {

    /**
     * Returns policy properties. In this case, type or name are not considered as properties.
     * @param mgmt 
     * @param policyData
     */
    public Map<String, ?> getPolicyProperties(ManagementContext mgmt, Map<String, ?> policyData){
        Map<String, Object> data = MutableMap.copyOf(policyData);
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        
        @SuppressWarnings("unchecked")
        Map<String,?> props = (Map<String,?>) data.remove("properties");
        if (props!=null) {
            data.putAll(props);
        }

        // evaluate DSL
        return BrooklynCampPlatform.findPlatform(mgmt).pdp().applyInterpreters(data);
    }
}
