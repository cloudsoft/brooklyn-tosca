package org.apache.brooklyn.tosca.a4c.brooklyn;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.model.components.Csar;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.GenericPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.impl.advanced.GroupPolicyParser;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlLocationResolver;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.tosca.a4c.Alien4CloudToscaPlatform;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToscaPlanToSpecTransformer implements PlanToSpecTransformer {

    private static final Logger log = LoggerFactory.getLogger(ToscaPlanToSpecTransformer.class);
    
    public static ConfigKey<Alien4CloudToscaPlatform> TOSCA_ALIEN_PLATFORM = ConfigKeys.builder(Alien4CloudToscaPlatform.class)
        .name("tosca.a4c.platform").build();

   public static ConfigKey<String> TOSCA_ID = ConfigKeys.newStringConfigKey("tosca.id");
   public static ConfigKey<String> TOSCA_DELEGATE_ID = ConfigKeys.newStringConfigKey("tosca.delegate.id");
   public static ConfigKey<String> TOSCA_DEPLOYMENT_ID = ConfigKeys.newStringConfigKey("tosca.deployment.id");
    
    private ManagementContext mgmt;
    private Alien4CloudToscaPlatform platform;

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        if (this.mgmt!=null && this.mgmt!=managementContext) throw new IllegalStateException("Cannot switch mgmt context");
        this.mgmt = managementContext;
        
        try {
            synchronized (ToscaPlanToSpecTransformer.class) {
                platform = (Alien4CloudToscaPlatform) mgmt.getConfig().getConfig(TOSCA_ALIEN_PLATFORM);
                if (platform==null) {
                    Alien4CloudToscaPlatform.grantAdminAuth();
                    platform = Alien4CloudToscaPlatform.newInstance();
                    ((LocalManagementContext)mgmt).getBrooklynProperties().put(TOSCA_ALIEN_PLATFORM, platform);
                    platform.loadNormativeTypes();
                }
            }
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }

    @Override
    public String getShortDescription() {
        return "tosca";
    }

    @Override
    public boolean accepts(String planType) {
        return getShortDescription().equals(planType);
    }

    public static class PlanTypeChecker {

        Object obj;
        boolean isTosca = false;
        String csarLink;
        
        public PlanTypeChecker(String plan) {
            try {
                obj = Yamls.parseAll(plan).iterator().next();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.trace("Not YAML", e);
                return;
            }
            if (!(obj instanceof Map)) {
                log.trace("Not a map");
                // is it a one-line URL?
                plan = plan.trim();
                if (!plan.contains("\n") && Urls.isUrlWithProtocol(plan)) {
                    csarLink = plan;
                }
                return;
            }
            
            if (isTosca((Map<?,?>)obj)) {
                isTosca = true;
                return;
            }
            
            if (((Map<?,?>)obj).size()==1) {
                csarLink = (String) ((Map<?,?>)obj).get("csar_link");
                return;
            }
        }

        private static boolean isTosca(Map<?,?> obj) {
            if (obj.containsKey("topology_template")) return true;
            if (obj.containsKey("topology_name")) return true;
            if (obj.containsKey("node_types")) return true;
            if (obj.containsKey("tosca_definitions_version")) return true;
            log.trace("Not TOSCA - no recognized keys");
            return false;
        }
    }
    
    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) throws PlanNotRecognizedException {
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            ParsingResult<Csar> tp;
            
            PlanTypeChecker type = new PlanTypeChecker(plan);
            if (!type.isTosca) {
                if (type.csarLink==null) {
                    throw new PlanNotRecognizedException("Does not look like TOSCA");
                }
                tp = platform.uploadArchive(new ResourceUtils(this).getResourceFromUrl(type.csarLink), "submitted-tosca-archive");
                
            } else {
                tp = platform.uploadSingleYaml(Streams.newInputStreamWithContents(plan), "submitted-tosca-plan");
            }

            if (ArchiveUploadService.hasError(tp, ParsingErrorLevel.ERROR)) {
                throw new UserFacingException("Could not parse TOSCA plan: "
                    +Strings.join(tp.getContext().getParsingErrors(), "\n  "));
            }
            
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
        CSARRepositorySearchService repositorySearchService = platform.getBean(CSARRepositorySearchService.class);
        EntitySpec<BasicApplication> rootSpec = EntitySpec.create(BasicApplication.class).displayName(name);

        rootSpec.configure(TOSCA_ID, topo.getId());
        rootSpec.configure(TOSCA_DELEGATE_ID, topo.getDelegateId());
        rootSpec.configure(TOSCA_DEPLOYMENT_ID, deploymentId);

        DependencyTree dt = new DependencyTree(topo, mgmt, repositorySearchService);
        dt.addSpecsAsChildrenOf(rootSpec);

        if (topo.getGroups()!=null) {
            for (NodeGroup g: topo.getGroups().values()) {
                if (g.getPolicies()!=null) {
                    for (AbstractPolicy p: g.getPolicies()) {
                        if (p==null) {
                            throw new NullPointerException("Null policy found in topology.");
                        }
                        if ("brooklyn.location".equals(p.getName())) {
                            setLocationsOnSpecs(dt.getSpecs(), g, (GenericPolicy) p);
                        }
                        // TODO: Other policies ignored, should we support them?
                    }
                }
            }
        }

        log.debug("Created entity from TOSCA spec: " + rootSpec);
        return rootSpec;
    }

    private void setLocationsOnSpecs(Map<String, EntitySpec<?>> specs,
                                     NodeGroup group,
                                     GenericPolicy policy) {

        List<Location> foundLocations;
        if(policy.getData().containsKey(GroupPolicyParser.VALUE)){
            foundLocations = new BrooklynYamlLocationResolver(mgmt)
                    .resolveLocations(ImmutableMap.of("location",
                                    policy.getData().get(GroupPolicyParser.VALUE)), true);
        } else {
            Map<String, ?> data = MutableMap.copyOf(policy.getData());
            /* name entry contains the policy name. This value is not necessary for the location
            creating process which is carried out by BrooklynYamlLocationResolver.*/
            data.remove("name");
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


    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) throws PlanNotRecognizedException {
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

}
