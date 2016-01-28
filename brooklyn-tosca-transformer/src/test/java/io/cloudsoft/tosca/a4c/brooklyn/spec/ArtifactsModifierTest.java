package io.cloudsoft.tosca.a4c.brooklyn.spec;

import alien4cloud.model.components.DeploymentArtifact;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;

public class ArtifactsModifierTest extends Alien4CloudToscaTest {

    final private String ARTIFACT_EXAMPLE = "https://brooklyn.apache.org/example-artifact";

    @Test
    public void testSetsScalarProperties() {

        DeploymentArtifact d = new DeploymentArtifact();
        d.setArtifactRef(ARTIFACT_EXAMPLE);
        Map<String, DeploymentArtifact> artifacts = MutableMap.of(TestEntity.CONF_NAME.getName(), d);
        nodeTemplate.setArtifacts(artifacts);

        ArtifactsModifier builder = new ArtifactsModifier(mgmt);

        builder.apply(testSpec, nodeTemplate, topology);
        assertEquals(testSpec.getConfig().get(TestEntity.CONF_NAME), ARTIFACT_EXAMPLE);
    }

    // TODO: test other AbstractPropertyValue types.


}
