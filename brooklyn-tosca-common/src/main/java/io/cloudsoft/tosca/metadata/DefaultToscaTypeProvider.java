package io.cloudsoft.tosca.metadata;

import com.google.common.base.Optional;

/**
 * A {@link ToscaTypeProvider} that always returns "brooklyn.nodes.SoftwareProcess".
 */
public class DefaultToscaTypeProvider implements ToscaTypeProvider {

    @Override
    public Optional<String> getToscaType(String type, String version) {
        return Optional.of("brooklyn.nodes.SoftwareProcess");
    }

}
