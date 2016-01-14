package io.cloudsoft.tosca.metadata;

import java.util.List;

import com.google.common.base.Optional;

public class ToscaMetadataProvider {

    private final List<ToscaTypeProvider> providers;

    public ToscaMetadataProvider(List<ToscaTypeProvider> providers) {
        this.providers = providers;
    }

    /**
     * @see ToscaTypeProvider#getToscaType(String, String)
     */
    public Optional<String> getToscaType(String type, String version) {
        for (ToscaTypeProvider provider : providers) {
            Optional<String> providedType = provider.getToscaType(type, version);
            if (providedType.isPresent()) {
                return providedType;
            }
        }
        return Optional.absent();
    }

}
