package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.test.Asserts;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public class RuntimeEnvironmentModifierSpecTest extends Alien4CloudToscaTest {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEnvironmentModifierSpecTest.class);
    
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

    private static String escapeLiteralForRegex(String in) {
        return in.replaceAll("[-\\[\\]{}()*+?.,\\\\\\\\^$|#\\s]", "\\\\$0");
    }
    
    @Test
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
        when(alien4CloudFacade.getArtifactRef(anyString(), any(ToscaApplication.class), ArgumentMatchers.eq(artifactKey))).thenReturn("http://foo");
        
        //when(alien4CloudFacade.getArtifactPath(anyString(), any(ToscaApplication.class), anyString())).thenReturn(Optional.of(Paths.get("/tmp")));

        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class);
        RuntimeEnvironmentModifier modifier = new RuntimeEnvironmentModifier(mgmt, alien4CloudFacade);
        modifier.apply(spec, "", toscaApplication);
        
        Asserts.assertStringMatchesRegex(
            spec.getConfig().get(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactKey)).toString(), 
            escapeLiteralForRegex(BrooklynDslCommon.formatString("%s/%s/%s", BrooklynDslCommon.attributeWhenReady("install.dir"), "__ANYTHING__", artifactKey).toString())
            .replace("__ANYTHING__", ".*"));
    }

}
