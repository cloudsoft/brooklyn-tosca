package io.cloudsoft.tosca.a4c.brooklyn.util;

import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import alien4cloud.model.components.Interface;

public class NodeTemplates {

    /**
     * Finds the first interface to match one of several valid interface names
     *
     * @param nodeTemplateInterfaces The map containing Interfaces
     * @param validInterfaceNames The list of valid interface keys
     * @return
     */
    public static Optional<Interface> findInterfaceOfNodeTemplate(Map<String, Interface> nodeTemplateInterfaces,
            List<String> validInterfaceNames) {
        if (nodeTemplateInterfaces != null) {
            for (String interfaceName : validInterfaceNames) {
                if (nodeTemplateInterfaces.containsKey(interfaceName)) {
                    return Optional.of(nodeTemplateInterfaces.get(interfaceName));
                }
            }
        }
        return Optional.absent();
    }
}
