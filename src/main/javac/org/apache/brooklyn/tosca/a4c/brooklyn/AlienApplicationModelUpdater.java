package org.apache.brooklyn.tosca.a4c.brooklyn;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.tosca.a4c.Alien4CloudToscaPlatform;

import alien4cloud.csar.services.CsarService;
import alien4cloud.dao.MonitorESDAO;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;

/**
 * Created by martin on 06/10/15.
 */
public class AlienApplicationModelUpdater {

    private final ManagementContext mgmt;

    public AlienApplicationModelUpdater(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    public void writeToAlien(Entity entity) {
        Alien4CloudToscaPlatform platform = (Alien4CloudToscaPlatform) mgmt.getConfig().getConfig(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM);

        MonitorESDAO monitor = platform.getBean(MonitorESDAO.class);

        String toscaId = entity.config().get(ConfigKeys.newStringConfigKey("tosca.id"));

        DeploymentTopology topology = monitor.findById(DeploymentTopology.class, toscaId);

        Collection<Entity> entites = entity.getChildren();

        Map<String, NodeTemplate> templates = topology.getNodeTemplates();

        templates.clear();

        NodeTemplate compute = new NodeTemplate();
        NodeTemplate foo = new NodeTemplate();
        NodeTemplate bar = new NodeTemplate();

        templates.put("Compute", compute);
        templates.put("Foo", foo);
        templates.put("Bar", bar);

        RelationshipTemplate fooOnCompute = new RelationshipTemplate();
        fooOnCompute.setTarget("Compute");
        foo.getRelationships().put("hostedOn", new RelationshipTemplate());

        // Or for cluster
        NodeTemplate cluster = new NodeTemplate();

        monitor.save(topology);

        //platform.getBean(CsarService.class)....
    }


//        // put this into Groovy console to try
//        new org.apache.brooklyn.tosca.a4c.brooklyn.AlienApplicationModelUpdater(mgmt)
//                .writeToAlien(mgmt.lookup("YVeyxnKR"));

}
