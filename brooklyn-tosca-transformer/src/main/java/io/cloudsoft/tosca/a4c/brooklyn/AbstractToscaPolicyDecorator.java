package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;

public abstract class AbstractToscaPolicyDecorator implements ToscaPolicyDecorator {

    /**
     * Returns policy properties. In this case, type or name are not considered as properties.
     * @param policyData
     */
    public Map<String, ?> getPolicyProperties(Map<String, ?> policyData){
        Map<String, ?> data = MutableMap.copyOf(policyData);
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        return data;
    }
}
