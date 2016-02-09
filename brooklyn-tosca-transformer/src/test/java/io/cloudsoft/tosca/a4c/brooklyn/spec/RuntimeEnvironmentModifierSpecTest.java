package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

// FIXME: Reenable along with RuntimeEnvironmentModifier
public class RuntimeEnvironmentModifierSpecTest extends Alien4CloudToscaTest {

    @Mock
    private ToscaFacade alien4CloudFacade;
    @Mock
    private NodeTemplate nodeTemplate;
    @Mock
    private ToscaApplication toscaApplication;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @BeforeClass
    public void initMocks(){
        MockitoAnnotations.initMocks(this);
    }

    @Test(enabled = false)
    public void testArtifactLocationsAreConfiguredAsShellVariables() throws CSARVersionNotFoundException {
        final String artifactName = "artifactName";
        final String artifactKey  = "artifactKey";

        DeploymentArtifact artifact = new DeploymentArtifact();
        artifact.setArchiveName("archiveName");
        artifact.setArchiveVersion("archiveVersion");
        artifact.setArtifactName(artifactName);
        artifact.setArtifactType("tosca.artifacts.File");
        Map<String, DeploymentArtifact> artifacts = ImmutableMap.of(artifactKey, artifact);

        when(alien4CloudFacade.getArtifacts(anyString(), any(ToscaApplication.class))).thenReturn(artifacts.keySet());

        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class);
        RuntimeEnvironmentModifier modifier = new RuntimeEnvironmentModifier(mgmt, alien4CloudFacade);
        modifier.apply(spec, "", toscaApplication);

        TestEntity entity = app.createAndManageChild(spec);
        Map<String, Object> shellEnv = entity.config().get(SoftwareProcess.SHELL_ENVIRONMENT);
        assertNotNull(shellEnv);
        assertTrue(shellEnv.containsKey(artifactKey), "expected " + artifactKey + " key in: " + mapToString(shellEnv));
        final Object artifactNameEnvVar = shellEnv.get(artifactKey);
        assertTrue(artifactNameEnvVar.toString().endsWith(artifactName),
                "Expected " + artifactKey + " envVar to end with " + artifactName + ": " + artifactNameEnvVar);
    }

    private String mapToString(Map<?, ?> map) {
        return "{" + Joiner.on(", ").withKeyValueSeparator("=").join(map) + "}";
    }

}
