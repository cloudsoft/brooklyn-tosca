package alien4cloud.brooklyn.metadata;

import com.google.common.base.Optional;

/**
 * A {@link ToscaTypeProvider} that always returns "brooklyn.nodes.SoftwareProcess".
 */
public class DefaultToscaTypeProvider implements ToscaTypeProvider {

    @Override
    public Optional<String> getToscaType(String type) {
        return Optional.of("brooklyn.nodes.SoftwareProcess");
    }

}
