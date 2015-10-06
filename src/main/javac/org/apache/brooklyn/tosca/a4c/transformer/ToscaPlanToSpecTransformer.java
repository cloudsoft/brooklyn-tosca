package org.apache.brooklyn.tosca.a4c.transformer;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.ChildStartableMode;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.tosca.a4c.platform.Alien4CloudToscaPlatform;
import org.apache.brooklyn.tosca.a4c.transformer.converters.ToscaComputeLocToVanillaConverter;
import org.apache.brooklyn.tosca.a4c.transformer.converters.ToscaComputeToVanillaConverter;
import org.apache.brooklyn.tosca.a4c.transformer.converters.ToscaTomcatServerConverter;
import org.apache.brooklyn.util.collections.MutableList;
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

import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.model.components.Csar;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.GenericPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.impl.advanced.GroupPolicyParser;

public class ToscaPlanToSpecTransformer implements PlanToSpecTransformer {

    private static final Logger log = LoggerFactory.getLogger(ToscaPlanToSpecTransformer.class);
    
    ConfigKey<Alien4CloudToscaPlatform> TOSCA_ALIEN_PLATFORM = ConfigKeys.builder(Alien4CloudToscaPlatform.class)
        .name("tosca.a4c.platform").build();
    
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
                    platform.loadNodeTypes();
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
            
            return createApplicationSpec(name, topo);
            
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


    public EntitySpec<? extends Application> populateApplicationSpecFromDeploymentTopologyId(EntitySpec<BasicApplication> spec, String id) {
        DeploymentTopology dt = platform.getBean(DeploymentTopologyService.class).getOrFail(id);
        alien4cloud.model.application.Application application = platform.getBean(ApplicationService.class).getOrFail(dt.getDelegateId());
        return populateApplicationSpec(spec, application.getName(), dt);
    }

    
    protected EntitySpec<? extends Application> createApplicationSpec(String name, Topology topo) {
        return populateApplicationSpec(EntitySpec.create(BasicApplication.class), name, topo);
    }
    
    protected EntitySpec<? extends Application> populateApplicationSpec(EntitySpec<BasicApplication> rootSpec, String name, Topology topo) {
        
        // TODO we should support Relationships and have an OtherEntityMachineLocation ?
        rootSpec.configure(EntityManagementUtils.WRAPPER_APP_MARKER, Boolean.TRUE);

        
        rootSpec.displayName(name);

        // get COMPUTE nodes
        Map<String,EntitySpec<?>> allNodeSpecs = MutableMap.of();
        Map<String,EntitySpec<?>> topLevelNodeSpecs = MutableMap.of();
        Map<String,NodeTemplate> otherNodes = MutableMap.of();
        Map<String, List<EntitySpec<?>>> childRequests = MutableMap.of();

        for (Entry<String,NodeTemplate> templateE: topo.getNodeTemplates().entrySet()) {
            String templateId = templateE.getKey();
            NodeTemplate template = templateE.getValue();

            if ("tosca.nodes.Compute".equals(template.getType())) {
                EntitySpec<VanillaSoftwareProcess> spec = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template);
                topLevelNodeSpecs.put(templateId, spec);
                allNodeSpecs.put(templateId, spec);
            } else {
                otherNodes.put(templateId, template);
            }
        }

        // get OTHER nodes, putting as children of the relevant compute node for now
        // (only supporting explicit compute, and always requiring it)
        if (!otherNodes.isEmpty()) {
            for (Entry<String,NodeTemplate> templateE: otherNodes.entrySet()) {
                String templateId = templateE.getKey();
                NodeTemplate template = templateE.getValue();

                EntitySpec<? extends Entity> thisNode=null;

                /*

                try {
                    // TODO: Brooklyn entities should be resolved through the catalog instead of looking up for the type.
                    // This works for now as a quick and dirty solution.
                    thisNode = EntitySpec.create((Class<Entity>) Class.forName(template.getType()));
                    topLevelNodeSpecs.put(templateId, thisNode);
                    allNodeSpecs.put(templateId, thisNode);
                    continue;
                } catch (ClassNotFoundException e) {
                    log.info("Node " + template.getType() + " is not supported");
                }

                thisNode = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template);

                */



                if ("tosca.nodes.Compute".equals(template.getType())) {
                    thisNode = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template);
                }
                else if ("tosca.nodes.ComputeLoc".equals(template.getType())) {
                    thisNode = new ToscaComputeLocToVanillaConverter(mgmt).toSpec(templateId, template);
                }
                else if("org.apache.brooklyn.entity.webapp.tomcat.TomcatServer".equals(template.getType())) {
                    thisNode = new ToscaTomcatServerConverter(mgmt).toSpec(templateId, template);
                }
                else {
                    //tosca.nodes.Software...
                    thisNode = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template);
                }

                String hostNodeId = null;
                Requirement hostR = template.getRequirements()==null ? null : template.getRequirements().get("host");
                if ((hostR!=null) && (template.getRelationships()!=null)) {
                    for (RelationshipTemplate r: template.getRelationships().values()) {
                        if (r.getRequirementName().equals("host")) {
                            hostNodeId = r.getTarget();
                            break;
                        }
                    }
                }
                if (hostNodeId==null) {
                    // temporarily, fall back to looking for a *property* called 'host'
                    hostNodeId = ToscaComputeToVanillaConverter.resolve(template.getProperties(), "host");
                    if (hostNodeId!=null) {
                        log.warn("Using legacy 'host' *property* to resolve host; use *requirement* instead.");
                    }
                }

                if (hostNodeId!=null) {
                    //EntitySpec<?> parent = topLevelNodeSpecs.get(hostNodeId);
                    //if (parent!=null) {
                    //    parent.child(thisNode);
                    //    parent.configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
                    //} else {
                    //    throw new IllegalStateException("Can't find parent '"+hostNodeId+"'");
                    //}
                    if(!childRequests.containsKey(hostNodeId)){
                        childRequests.put(hostNodeId, MutableList.<EntitySpec<?>>of());
                    }
                    childRequests.get(hostNodeId).add(thisNode);

                } else {
                    // temporarily, if no host relationship, treat as top-level (assume derived from compute, but note children can't be on it)
                    topLevelNodeSpecs.put(templateId, thisNode);
                }
                allNodeSpecs.put(templateId, thisNode);
            }
        }

        //process child request
        for(Map.Entry<String, List<EntitySpec<?>>> entry : childRequests.entrySet()){
            String parentId= entry.getKey();
            EntitySpec<?> parent = topLevelNodeSpecs.get(parentId);
            for(EntitySpec<?> child: entry.getValue()){
                parent.child(child);
                parent.configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
            }

        }

        rootSpec.children(topLevelNodeSpecs.values());

        if (topo.getGroups()!=null) {
            for (NodeGroup g: topo.getGroups().values()) {
                if (g.getPolicies()!=null) {
                    for (AbstractPolicy p: g.getPolicies()) {
                        if (p==null) {
                            throw new NullPointerException("Null policy found in topology.");
                        }
                        if ("brooklyn.location".equals(p.getName())) {
                            for (String id: g.getMembers()) {
                                EntitySpec<?> spec = allNodeSpecs.get(id);
                                if (spec==null) throw new IllegalStateException("No node '"+id+"' found, when setting locations");
                                spec.location(mgmt.getLocationRegistry().resolve( (String)((GenericPolicy)p).getData().get(GroupPolicyParser.VALUE) ));
                            }
                        }
                        // other policies ignored
                    }
                }
            }
        }

        log.debug("Created entity from TOSCA spec: "+ rootSpec);
        return rootSpec;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item) throws PlanNotRecognizedException {
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
