package io.cloudsoft.tosca.metadata;

import com.google.common.base.Optional;

public interface ToscaTypeProvider {

    /**
     * @return A TOSCA type to use for the given input, or {@link Optional#absent absent} if
     * no such type can be chosen.
     */
    Optional<String> getToscaType(String type, String version);

}
