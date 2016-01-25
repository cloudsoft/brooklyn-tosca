package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlLocationResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import alien4cloud.application.ApplicationService;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.model.components.Csar;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.GenericPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.impl.advanced.GroupPolicyParser;
import io.cloudsoft.tosca.a4c.brooklyn.util.EntitySpecs;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudSpringContext;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;

public class ToscaPlanToSpecTransformer implements PlanToSpecTransformer {

    private static final Logger log = LoggerFactory.getLogger(ToscaPlanToSpecTransformer.class);
    
    public static final ConfigKey<Alien4CloudToscaPlatform> TOSCA_ALIEN_PLATFORM = ConfigKeys.builder(Alien4CloudToscaPlatform.class)
        .name("tosca.a4c.platform").build();

    @VisibleForTesting
    static final String FEATURE_TOSCA_ENABLED = BrooklynFeatureEnablement.FEATURE_PROPERTY_PREFIX + ".tosca";

    private static final ConfigKey<String> TOSCA_ID = ConfigKeys.newStringConfigKey("tosca.id");
    private static final ConfigKey<String> TOSCA_DELEGATE_ID = ConfigKeys.newStringConfigKey("tosca.delegate.id");
    private static final ConfigKey<String> TOSCA_DEPLOYMENT_ID = ConfigKeys.newStringConfigKey("tosca.deployment.id");

    private static final String POLICY_FLAG_TYPE = "type";
    private static final String POLICY_FLAG_NAME = "name";

    private ManagementContext mgmt;
    private Alien4CloudToscaPlatform platform;
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

    private void initialiseAlien(){
        try {
            synchronized (ToscaPlanToSpecTransformer.class) {
                platform = mgmt.getConfig().getConfig(TOSCA_ALIEN_PLATFORM);
                if (platform == null) {
                    Alien4CloudToscaPlatform.grantAdminAuth();
                    ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(mgmt);
                    platform = applicationContext.getBean(Alien4CloudToscaPlatform.class);
                    ((LocalManagementContext) mgmt).getBrooklynProperties().put(TOSCA_ALIEN_PLATFORM, platform);
                    platform.loadNormativeTypes();
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
            ParsingResult<Csar> tp = new ToscaParser(platform).parse(plan);
            String name = tp.getResult().getName();
            Topology topo = platform.getTopologyOfCsar(tp.getResult());
            
            return createApplicationSpec(name, topo, "");
            
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
        DeploymentTopology dt = platform.getBean(DeploymentTopologyService.class).getOrFail(id);
        alien4cloud.model.application.Application application = platform.getBean(ApplicationService.class).getOrFail(dt.getDelegateId());
        return createApplicationSpec(application.getName(), dt, id);
    }

    protected EntitySpec<? extends Application> createApplicationSpec(String name, Topology topo, String deploymentId) {
        // TODO we should support Relationships and have an OtherEntityMachineLocation ?
        EntitySpec<BasicApplication> rootSpec = EntitySpec.create(BasicApplication.class).displayName(name);

        rootSpec.configure(TOSCA_ID, topo.getId());
        rootSpec.configure(TOSCA_DELEGATE_ID, topo.getDelegateId());
        rootSpec.configure(TOSCA_DEPLOYMENT_ID, deploymentId);

        ApplicationSpecsBuilder dt = platform.getBean(ApplicationSpecsBuilder.class);
        Map<String, EntitySpec<?>> specs = dt.getSpecs(topo);
        rootSpec.children(specs.values());

        // TODO: Move to dependencytree/specbuilder.
        if (topo.getGroups() != null) {
            for (NodeGroup g : topo.getGroups().values()) {
                if (g.getPolicies() != null) {
                    for (AbstractPolicy p : g.getPolicies()) {
                        if (p == null) {
                            throw new NullPointerException("Null policy found in topology.");
                        }
                        if ("brooklyn.location".equals(p.getName())) {
                            setLocationsOnSpecs(specs, g, (GenericPolicy) p);
                        } else if (isABrooklynPolicy(getPolicyType((GenericPolicy) p))) {
                            decorateEntityBrooklynWithPolicies(rootSpec, g, (GenericPolicy) p);
                        }
                    }
                }
            }
        }

        log.debug("Created entity from TOSCA spec: " + rootSpec);
        return rootSpec;
    }

    public void decorateEntityBrooklynWithPolicies(EntitySpec<? extends Application> appSpec,
            NodeGroup group, GenericPolicy policy){
        String type;
        BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
        BrooklynYamlTypeInstantiator.Factory yamlLoader =
                new BrooklynYamlTypeInstantiator.Factory(loader, this);

        type = getPolicyType(policy);
        if(policy.getType()==null) {
            throw new IllegalStateException("Type was not found for policy " + policy.getName());
        }

        ImmutableMap policyDefinition = ImmutableMap.of(
                BrooklynCampReservedKeys.BROOKLYN_POLICIES, ImmutableList.of(
                        ImmutableMap.of(
                                "policyType", type,
                                BrooklynCampReservedKeys.BROOKLYN_CONFIG, getPolicyProperties(policy))));

        for (String specId: group.getMembers()){
            EntitySpec<?> spec = EntitySpecs.findChildEntitySpecByPlanId(appSpec, specId);
            if(spec==null){
                throw new IllegalStateException("Error: NodeTemplate " + specId +
                        " defined by policy " + policy.getName() + " was not found");
            }
            new BrooklynEntityDecorationResolver.PolicySpecResolver(yamlLoader)
                    .decorate(spec, ConfigBag.newInstance(policyDefinition));
        }

        if (group.getMembers()==null || group.getMembers().isEmpty()){
            new BrooklynEntityDecorationResolver.PolicySpecResolver(yamlLoader)
                    .decorate(appSpec, ConfigBag.newInstance(policyDefinition));
        }
    }

    private void setLocationsOnSpecs(Map<String, EntitySpec<?>> specs,
                                     NodeGroup group,
                                     GenericPolicy policy) {

        List<Location> foundLocations;
        if (policy.getData().containsKey(GroupPolicyParser.VALUE)){
            foundLocations = new BrooklynYamlLocationResolver(mgmt)
                    .resolveLocations(ImmutableMap.of("location",
                                    policy.getData().get(GroupPolicyParser.VALUE)), true);
        } else {
            Map<String, ?> data = getPolicyProperties(policy);
            foundLocations = new BrooklynYamlLocationResolver(mgmt)
                    .resolveLocations(ImmutableMap.of("location", data), true);
        }

        for (String id: group.getMembers()) {
            EntitySpec<?> spec = specs.get(id);
            if (spec==null){
                throw new IllegalStateException("No node "+id+" found, when setting locations");
            }
            spec.locations(foundLocations);
        }
    }

    public boolean isABrooklynPolicy(String policyType){
        Class clazz;
        CatalogItem catalogItem = CatalogUtils.getCatalogItemOptionalVersion(this.mgmt, policyType);
        if (catalogItem != null) {
            clazz = catalogItem.getCatalogItemJavaType();
        } else {
            try {
                clazz = Class.forName(policyType);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return Policy.class.isAssignableFrom(clazz);
    }

    private String getPolicyType(GenericPolicy policy){
        String type = null;
        if (policy.getType() != null) {
            type = policy.getType();
        } else if (policy.getData().containsKey("type")) {
            type = (String) policy.getData().get("type");
        }
        return type;
    }

    /**
     * Returns policy properties. In this case, type or name are not considered as properties.
     * @param policy
     */
    private Map<String, ?> getPolicyProperties(GenericPolicy policy){
        Map<String, ?> data = MutableMap.copyOf(policy.getData());
        data.remove(POLICY_FLAG_NAME);
        data.remove(POLICY_FLAG_TYPE);
        return data;
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
