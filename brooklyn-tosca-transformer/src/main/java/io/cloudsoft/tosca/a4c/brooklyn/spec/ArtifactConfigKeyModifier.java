package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

@Component
public class ArtifactConfigKeyModifier extends ConfigKeyModifier {

    @Inject
    public ArtifactConfigKeyModifier(ManagementContext mgmt) {
        super(mgmt);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        Map<String, Object> a = MutableMap.of();
        Map<String, DeploymentArtifact> artifacts = nodeTemplate.getArtifacts();
        if (artifacts != null) {
            for (Map.Entry<String, DeploymentArtifact> entry : artifacts.entrySet()) {
                a.put(entry.getKey(), entry.getValue().getArtifactRef());
            }
            ConfigBag bag = ConfigBag.newInstance(a);
            configureConfigKeysSpec(entitySpec, bag);
        }
    }

}
