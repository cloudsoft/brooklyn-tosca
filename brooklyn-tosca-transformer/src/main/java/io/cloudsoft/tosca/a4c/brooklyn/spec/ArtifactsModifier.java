package io.cloudsoft.tosca.a4c.brooklyn.spec;

import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;

@Component
public class ArtifactsModifier extends ConfigKeyModifier {

    @Inject
    public ArtifactsModifier(ManagementContext mgmt) {
        super(mgmt);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        ConfigBag bag = ConfigBag.newInstance(getArtifactRefs(nodeTemplate));
        configureConfigKeysSpec(entitySpec, bag);
    }

    private Map<String, Object> getArtifactRefs(NodeTemplate nodeTemplate) {
        Map<String, Object> artifactRefs = MutableMap.of();

        if (nodeTemplate.getArtifacts() != null) {
            Map<String, DeploymentArtifact> artifacts = nodeTemplate.getArtifacts();
            for (String artifactId : artifacts.keySet()) {
                artifactRefs.put(artifactId, artifacts.get(artifactId).getArtifactRef());
            }
        }
        return artifactRefs;
    }


}
