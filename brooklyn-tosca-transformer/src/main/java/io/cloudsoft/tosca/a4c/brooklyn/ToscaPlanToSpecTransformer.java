package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.google.common.annotations.VisibleForTesting;

import io.cloudsoft.tosca.a4c.platform.Alien4CloudSpringContext;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;
import io.cloudsoft.tosca.a4c.platform.ToscaPlatform;

public class ToscaPlanToSpecTransformer implements PlanToSpecTransformer {

    private static final Logger log = LoggerFactory.getLogger(ToscaPlanToSpecTransformer.class);
    
    public static final ConfigKey<Alien4CloudToscaPlatform> TOSCA_ALIEN_PLATFORM = ConfigKeys.builder(Alien4CloudToscaPlatform.class)
            .name("tosca.a4c.platform")
            .build();

    @VisibleForTesting
    static final String FEATURE_TOSCA_ENABLED = BrooklynFeatureEnablement.FEATURE_PROPERTY_PREFIX + ".tosca";

    private static final ConfigKey<String> TOSCA_ID = ConfigKeys.newStringConfigKey("tosca.id");
    private static final ConfigKey<String> TOSCA_DELEGATE_ID = ConfigKeys.newStringConfigKey("tosca.delegate.id");
    private static final ConfigKey<String> TOSCA_DEPLOYMENT_ID = ConfigKeys.newStringConfigKey("tosca.deployment.id");

    private ManagementContext mgmt;
    private ToscaPlatform platform;
    private final AtomicBoolean alienInitialised = new AtomicBoolean();
    private boolean hasLoggedDisabled = false;

    static {
        BrooklynFeatureEnablement.setDefault(FEATURE_TOSCA_ENABLED, true);
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
        if (!isEnabled()) {
            if (!hasLoggedDisabled) {
                log.info("Not loading brooklyn-tosca platform: feature disabled");
                hasLoggedDisabled = true;
            }
            return;
        }
        if (this.mgmt != null && this.mgmt != managementContext) {
            throw new IllegalStateException("Cannot switch mgmt context");
        } else if (this.mgmt == null) {
            this.mgmt = managementContext;
            initialiseAlien();
        }
    }

    private void initialiseAlien() {
        try {
            synchronized (ToscaPlanToSpecTransformer.class) {
                platform = mgmt.getConfig().getConfig(TOSCA_ALIEN_PLATFORM);
                if (platform == null) {
                    Alien4CloudToscaPlatform.grantAdminAuth();
                    ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(mgmt);
                    platform = applicationContext.getBean(ToscaPlatform.class);
                    ((LocalManagementContext) mgmt).getBrooklynProperties().put(TOSCA_ALIEN_PLATFORM, platform);
                }
            }
            alienInitialised.set(true);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String getShortDescription() {
        return "tosca";
    }

    @Override
    public boolean accepts(String planType) {
        return isEnabled() && alienInitialised.get() && getShortDescription().equals(planType);
    }

    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) throws PlanNotRecognizedException {
        assertAvailable();
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            return createApplicationSpec(platform.parse(plan));
        } catch (Exception e) {
            if (e instanceof PlanNotRecognizedException) {
                if (log.isTraceEnabled())
                    log.debug("Failed to create entity from TOSCA spec:\n" + plan, e);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Failed to create entity from TOSCA spec:\n" + plan, e);
            }
            throw Exceptions.propagate(e);
        }
    }

    public EntitySpec<? extends Application> createApplicationSpecFromTopologyId(String id) {
        return createApplicationSpec(platform.getToscaApplication(id));
    }

    protected EntitySpec<? extends Application> createApplicationSpec(ToscaApplication toscaApplication) {
        // TODO we should support Relationships and have an OtherEntityMachineLocation ?
        EntitySpec<BasicApplication> rootSpec = EntitySpec.create(BasicApplication.class)
                .displayName(toscaApplication.getName())
                .configure(TOSCA_ID, toscaApplication.getId())
                .configure(TOSCA_DELEGATE_ID, toscaApplication.getDelegateId())
                .configure(TOSCA_DEPLOYMENT_ID, toscaApplication.getDeploymentId());

        ApplicationSpecsBuilder specsBuilder = platform.getBean(ApplicationSpecsBuilder.class);
        Map<String, EntitySpec<?>> specs = specsBuilder.getSpecs(toscaApplication);
        rootSpec.children(specs.values());
        specsBuilder.addPolicies(rootSpec, toscaApplication, specs);

        log.debug("Created entity from TOSCA spec: " + rootSpec);
        return rootSpec;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) throws PlanNotRecognizedException {
        assertAvailable();
        switch (item.getCatalogItemType()) {
        case TEMPLATE:
        case ENTITY:
            // unwrap? any other processing?
            return (SpecT) createApplicationSpec(item.getPlanYaml());
        case LOCATION: 
        case POLICY:
            throw new PlanNotRecognizedException("TOSCA does not support: "+item.getCatalogItemType());
        default:
            throw new IllegalStateException("Unknown CI Type "+item.getCatalogItemType());
        }
    }

    private boolean isEnabled() {
        return BrooklynFeatureEnablement.isEnabled(FEATURE_TOSCA_ENABLED);
    }

    /**
     * Throws {@link IllegalStateException} if {@link BrooklynFeatureEnablement#isEnabled(String)}
     * returns false for {@link #FEATURE_TOSCA_ENABLED} or if {@link #alienInitialised} is false.
     */
    private void assertAvailable() {
        if (!BrooklynFeatureEnablement.isEnabled(FEATURE_TOSCA_ENABLED)) {
            throw new PlanNotRecognizedException("Brooklyn TOSCA support is disabled");
        } else if (!alienInitialised.get()) {
            throw new PlanNotRecognizedException("Alien4Cloud platform is uninitialised for " + this);
        }
    }

}
