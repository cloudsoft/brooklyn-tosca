package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import io.cloudsoft.tosca.a4c.brooklyn.spec.AbstractSpecModifier;

public abstract class AbstractToscaPolicyDecorator implements ToscaPolicyDecorator {

    private static final Logger log = LoggerFactory.getLogger(AbstractToscaPolicyDecorator.class);
    
    /**
     * Returns policy properties. In this case, type or name are not considered as properties.
     * @param mgmt 
     * @param policyData
     */
    @SuppressWarnings("unchecked")
    public Map<String, ?> getPolicyProperties(ManagementContext mgmt, Map<String, ?> policyData){
        Map<String, Object> data = MutableMap.copyOf(policyData);
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        
        Map<String,?> props = (Map<String,?>) data.remove("properties");
        if (props!=null) {
            data.putAll(props);
        }

        // evaluate DSL
        Optional<Object> resolved = AbstractSpecModifier.resolveBrooklynDslValue(data, Optional.absent(), mgmt, null);
        if (resolved.isPresent()) {
            return (Map<String, ?>) resolved.get();
        } else {
            log.warn("Unable to resolve "+data+" for TOSCA; ignoring, some DSL/suppliers may not be set");
            return data;
        }
    }
}
