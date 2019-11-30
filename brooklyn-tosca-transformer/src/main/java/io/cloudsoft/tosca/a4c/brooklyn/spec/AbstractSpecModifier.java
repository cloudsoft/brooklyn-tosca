package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public abstract class AbstractSpecModifier implements EntitySpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSpecModifier.class);

    protected final ManagementContext mgmt;
    private ToscaFacade<? extends ToscaApplication> alien4CloudFacade;

    protected AbstractSpecModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        this.mgmt = mgmt;
        this.alien4CloudFacade = alien4CloudFacade;
    }

    @SuppressWarnings("unchecked")
    protected ToscaFacade<ToscaApplication> getToscaFacade() {
        return (ToscaFacade<ToscaApplication>) alien4CloudFacade;
    }

    protected Optional<Object> resolveBrooklynDslValue(Object unresolvedValue, Optional<TypeToken> desiredType) {
        return resolveBrooklynDslValue(mgmt, unresolvedValue, desiredType);
    }
    
    public static Optional<Object> resolveBrooklynDslValue(ManagementContext mgmt, Object originalValue, Optional<TypeToken> desiredType) {
        if (originalValue == null) {
            return Optional.absent();
        }
        Object value = originalValue;
        if (mgmt!=null) {
            // The 'dsl' key is arbitrary, but the interpreter requires a map
            Map<String, Object> resolvedConfigMap = BrooklynCampPlatform.findPlatform(mgmt)
                    .pdp()
                    .applyInterpreters(ImmutableMap.of("dsl", originalValue));
            value = resolvedConfigMap.get("dsl");
        }
        
        if (value instanceof DeferredSupplier) {
            // Don't cast - let Brooklyn evaluate it later (the value is a resolved DSL expression).
            return Optional.of(value);
        }
        
        if (desiredType.isPresent()) {
            // Don't look at generics when casting.
            // 
            // Let Brooklyn do that later when it uses/evaluates the config. We just need to create 
            // the EntitySpec object.
            //
            // This is important for DSL expressions (e.g. in a Map<String, String> such as 
            // TomcatServer's javaSysProps, a DSL expression could not be coerced to a string.
            //
            // By stripping the generics, it restores the brooklyn behaviour prior to snapshot at 
            // 31st Aug 2018 (commit 3e57b14b220bd7a994a9143d83bc123879086aff) when map/collection 
            // generics were not respected during coercion.
            
            Class<?> desiredRawType = desiredType.get().getRawType();

            return Optional.of(TypeCoercions.coerce(value, desiredRawType));

        } else {
            return Optional.of(value);
        }
    }
}
