package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Optional;

public interface ToscaPolicyDecorator {

    String POLICY_FLAG_TYPE = "type";
    String POLICY_FLAG_NAME = "name";

    void decorate(Map<String, ?> policyData, String policyName, Optional<String> type, Set<String> groupMembers);
}
