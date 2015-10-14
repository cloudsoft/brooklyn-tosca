package org.apache.brooklyn.tosca.a4c.brooklyn;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.tosca.a4c.Alien4CloudToscaPlatform;
import org.apache.brooklyn.util.collections.MutableMap;

import alien4cloud.csar.services.CsarService;
import alien4cloud.dao.IGenericIdDAO;
import alien4cloud.dao.MonitorESDAO;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.Capability;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;

import com.google.common.collect.ImmutableMap;


public class AlienApplicationModelUpdater {

    private final ManagementContext mgmt;

    public AlienApplicationModelUpdater(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    public void writeToAlien(Entity entity) {
        Alien4CloudToscaPlatform platform = mgmt.getConfig().getConfig(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM);

        IGenericIdDAO monitor = (IGenericIdDAO)platform.getBean("alien-es-dao");

        String toscaId = entity.config().get(ConfigKeys.newStringConfigKey("tosca.id"));

        DeploymentTopology topology = monitor.findById(DeploymentTopology.class, toscaId);

//        Collection<Entity> entites = entity.getChildren();

        Map<String, NodeTemplate> templates = topology.getNodeTemplates();

        NodeTemplate newTemplate = new NodeTemplate();
        String newTemplateKey = "MySqlNode";

        Requirement requirement = new Requirement();
        requirement.setType("tosca.capabilities.Root");
        newTemplate.setRequirements(MutableMap.of("dependency", requirement));

        Capability capability = new Capability();
        capability.setType("tosca.capabilities.Root");

        newTemplate.setCapabilities(MutableMap.of("root", capability));
        newTemplate.setType("org.apache.brooklyn.entity.database.mysql.MySqlNode");

        templates.put(newTemplateKey, newTemplate);


        monitor.save(topology);

//        templates.clear();
//
//        NodeTemplate compute = new NodeTemplate();
//        NodeTemplate foo = new NodeTemplate();
//        NodeTemplate bar = new NodeTemplate();
//        templates.put("Compute", compute);
//        templates.put("Foo", foo);
//        templates.put("Bar", bar);
//        topology.setNodeTemplates(templates);
//
////        RelationshipTemplate fooOnCompute = new RelationshipTemplate();
////        fooOnCompute.setTarget("Compute");
////        foo.getRelationships().put("hostedOn", new RelationshipTemplate());
////
////        // Or for cluster
////        NodeTemplate cluster = new NodeTemplate();
//

        //platform.getBean(CsarService.class)....
    }


//        // put this into Groovy console to try
//        new org.apache.brooklyn.tosca.a4c.brooklyn.AlienApplicationModelUpdater(mgmt)
//                .writeToAlien(mgmt.lookup("YVeyxnKR"));

}
