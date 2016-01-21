package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.component.repository.ICsarRepositry;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

// FIXME: Reenable along with RuntimeEnvironmentModifier
public class RuntimeEnvironmentModifierSpecTest extends Alien4CloudToscaTest {

    ICsarRepositry csarRepository;
    CSARRepositorySearchService repositorySearchService;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        csarRepository = Mockito.mock(ICsarRepositry.class);
        when(csarRepository.getCSAR(anyString(), anyString())).thenReturn(Paths.get("/path/to/csar"));
        repositorySearchService = Mockito.mock(CSARRepositorySearchService.class);
    }

    @Test(enabled = false)
    public void testArtifactLocationsAreConfiguredAsShellVariables() {
        final String artifactName = "artifactName";
        final String artifactKey  = "artifactKey";

        DeploymentArtifact artifact = new DeploymentArtifact();
        artifact.setArchiveName("archiveName");
        artifact.setArchiveVersion("archiveVersion");
        artifact.setArtifactName(artifactName);
        artifact.setArtifactType("tosca.artifacts.File");
        Map<String, DeploymentArtifact> artifacts = ImmutableMap.of(artifactKey, artifact);

        IndexedArtifactToscaElement indexedArtifactToscaElement = new IndexedArtifactToscaElement();
        indexedArtifactToscaElement.setArtifacts(artifacts);
        when(repositorySearchService.getRequiredElementInDependencies(
                eq(IndexedArtifactToscaElement.class), eq(nodeTemplate.getType()), eq(topology.getDependencies())))
                .thenReturn(indexedArtifactToscaElement);

        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class);
        RuntimeEnvironmentModifier modifier = new RuntimeEnvironmentModifier(mgmt, csarRepository);
        modifier.setRepositorySearchService(repositorySearchService);
        modifier.apply(spec, nodeTemplate, topology);

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
