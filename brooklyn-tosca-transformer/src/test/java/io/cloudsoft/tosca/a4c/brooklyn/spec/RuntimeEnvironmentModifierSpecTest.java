package io.cloudsoft.tosca.a4c.brooklyn.spec;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

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

    @Test
    public void testArtifactLocationsAreConfiguredAsShellVariables() {
        final String artifactName = "artifactName";
        final String artifactKey  = "artifactKey";

        DeploymentArtifact artifact = new DeploymentArtifact();
        artifact.setArchiveName("archiveName");
        artifact.setArchiveVersion("archiveVersion");
        artifact.setArtifactName(artifactName);
        artifact.setArtifactType("tosca.artifacts.File");
        Map<String, DeploymentArtifact> artifacts = ImmutableMap.of(artifactKey, artifact);

        when(alien4CloudFacade.getArtifacts(anyString(), any(ToscaApplication.class))).thenReturn(artifacts.keySet());
        when(alien4CloudFacade.getArtifactPath(anyString(), any(ToscaApplication.class), anyString())).thenReturn(Optional.of(Paths.get("/tmp")));

        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class);
        RuntimeEnvironmentModifier modifier = new RuntimeEnvironmentModifier(mgmt, alien4CloudFacade);
        modifier.apply(spec, "", toscaApplication);
        String actual = spec.getConfig().get(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactKey)).toString();
        String expected = BrooklynDslCommon.formatString("%s/%s/%s", BrooklynDslCommon.attributeWhenReady("install.dir"),"RANDOM", artifactKey).toString();
		String[] actualParts = actual.split("install.dir");
        String[] expectedParts = expected.split("install.dir");
        assertEquals(actualParts.length, expectedParts.length);
        assertEquals(actualParts[0], expectedParts[0]);
        // remove the random string for comparison, since we can't seed the Random object
        assertEquals(actualParts[1].substring(11), expectedParts[1].substring(11), "full-actual="+actual+"; full-expected="+expected);
    }

}
