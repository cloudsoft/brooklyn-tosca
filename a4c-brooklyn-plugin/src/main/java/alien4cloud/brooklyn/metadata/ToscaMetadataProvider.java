package alien4cloud.brooklyn.metadata;

import java.util.List;

import com.google.common.base.Optional;

public class ToscaMetadataProvider {

    private final List<ToscaTypeProvider> providers;

    public ToscaMetadataProvider(List<ToscaTypeProvider> providers) {
        this.providers = providers;
    }

    /**
     * @see alien4cloud.brooklyn.metadata.ToscaTypeProvider#getToscaType(String)
     */
    public Optional<String> getToscaType(String type) {
        for (ToscaTypeProvider provider : providers) {
            Optional<String> providedType = provider.getToscaType(type);
            if (providedType.isPresent()) {
                return providedType;
            }
        }
        return Optional.absent();
    }

}
