package org.apache.brooklyn.tosca.a4c.brooklyn;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.ChildStartableMode;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.tosca.a4c.Alien4CloudToscaPlatform;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.GenericPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.model.ArchiveRoot;
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

    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) throws PlanNotRecognizedException {
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            
            ParsingResult<ArchiveRoot> tp = platform.getToscaParser().parseFile("<submitted>", "submitted-tosca-plan",
                Streams.newInputStreamWithContents(plan), null);

            if (!tp.getContext().getParsingErrors().isEmpty()) {
                throw new IllegalStateException("Could not parse TOSCA plan: "+Strings.join(tp.getContext().getParsingErrors(), ", ") );
            }
            
            ArchiveRoot root = tp.getResult();
            Topology topo = root.getTopology();
            
            EntitySpec<BasicApplication> result = EntitySpec.create(BasicApplication.class);
            
            String name = null;
            if (tp.getResult().getArchive()!=null && tp.getResult().getArchive().getName()!=null) {
                name = tp.getResult().getArchive().getName();
            } else {
                name = root.getTopologyTemplateDescription();
            }
            result.displayName(name);

            // get COMPUTE nodes
            Map<String,EntitySpec<?>> allNodeSpecs = MutableMap.of();
            Map<String,EntitySpec<?>> computeNodeSpecs = MutableMap.of();
            Map<String,NodeTemplate> otherNodes = MutableMap.of();
            for (Entry<String,NodeTemplate> templateE: topo.getNodeTemplates().entrySet()) {
                String templateId = templateE.getKey();
                NodeTemplate template = templateE.getValue();
                
                if ("tosca.nodes.Compute".equals(template.getType())) {
                    EntitySpec<VanillaSoftwareProcess> spec = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template, root);
                    computeNodeSpecs.put(templateId, spec);
                    allNodeSpecs.put(templateId, spec);
                } else {
                    otherNodes.put(templateId, template);
                }
            }
            
            // get OTHER nodes, putting as children of the relevant compute node for now
            // TODO this is hokey, won't support nested types, etc;
            // we should support Relationships and have an OtherEntityMachineLocation ?
            if (!otherNodes.isEmpty()) {
                for (Entry<String,NodeTemplate> templateE: otherNodes.entrySet()) {
                    String templateId = templateE.getKey();
                    NodeTemplate template = templateE.getValue();
                    
                    EntitySpec<VanillaSoftwareProcess> thisNode = new ToscaComputeToVanillaConverter(mgmt).toSpec(templateId, template, root);
                    
                    String hostNodeId = null;
                    Requirement hostR = template.getRequirements()==null ? null : template.getRequirements().get("host");
                    if (hostR!=null) {
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
                        EntitySpec<?> parent = computeNodeSpecs.get(hostNodeId);
                        if (parent!=null) {
                            parent.child(thisNode);
                            parent.configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
                        } else {
                            throw new IllegalStateException("Can't find parent '"+hostNodeId+"'");
                        }
                    } else {
                        // TODO for now if no host relationship, treat as compute (assume derived from compute, but note children can't be on it)
                        computeNodeSpecs.put(templateId, thisNode);
                    }
                    allNodeSpecs.put(templateId, thisNode);
                }
            }
            
            result.children(computeNodeSpecs.values());
            
            for (NodeGroup g: topo.getGroups().values()) {
                for (AbstractPolicy p: g.getPolicies()) {
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
            
            log.debug("Created entity from TOSCA spec: "+ result);
            return result;
            
        } catch (Exception e) {
            log.debug("Failed to create entity from TOSCA spec:\n" + plan);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<T, SpecT>> AbstractBrooklynObjectSpec<T, SpecT> createCatalogSpec(
            CatalogItem<T, SpecT> item) throws PlanNotRecognizedException {
        // TODO Auto-generated method stub
        return null;
    }

}
