package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public abstract class AbstractSpecModifier implements EntitySpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSpecModifier.class);

    private final ManagementContext mgmt;
    private ToscaFacade<? extends ToscaApplication> alien4CloudFacade;

    protected AbstractSpecModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        this.mgmt = mgmt;
        this.alien4CloudFacade = alien4CloudFacade;
    }

    protected ToscaFacade<ToscaApplication> getToscaFacade() {
        return (ToscaFacade<ToscaApplication>) alien4CloudFacade;
    }

    protected Optional<Object> resolveValue(Object unresolvedValue, Optional<TypeToken> desiredType) {
        if (unresolvedValue == null) {
            return Optional.absent();
        }

        CampPlatform platform = BrooklynCampPlatform.findPlatform(mgmt);
        if (platform==null) {
            throw new IllegalStateException("No CAMP Platform is registered with this Brooklyn management context.");
        }

        // The 'dsl' key is arbitrary, but the interpreter requires a map
        Map<String, Object> resolvedConfigMap = platform
                .pdp()
                .applyInterpreters(ImmutableMap.of("dsl", unresolvedValue));
        return Optional.of(desiredType.isPresent()
                           ? TypeCoercions.coerce(resolvedConfigMap.get("dsl"), desiredType.get())
                           : resolvedConfigMap.get("dsl"));
    }

}
