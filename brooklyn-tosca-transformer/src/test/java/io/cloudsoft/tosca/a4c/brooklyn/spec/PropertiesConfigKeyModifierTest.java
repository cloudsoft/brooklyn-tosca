package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.core.test.entity.TestEntity;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import alien4cloud.model.topology.NodeTemplate;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public class PropertiesConfigKeyModifierTest extends Alien4CloudToscaTest {

    @Mock
    private ToscaFacade alien4CloudFacade;
    @Mock
    private ToscaApplication toscaApplication;

    @BeforeClass
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetsScalarProperties() {
        when(toscaApplication.getNodeName(anyString())).thenReturn(Optional.of("Test"));
        when(alien4CloudFacade.getTemplatePropertyObjects(Mockito.anyString(), Mockito.any(ToscaApplication.class),
                Mockito.anyString()))
                .thenReturn(ImmutableMap.<String, Object>of(TestEntity.CONF_NAME.getName(), "bar"));
        PropertiesConfigKeyModifier builder = new PropertiesConfigKeyModifier(mgmt, alien4CloudFacade);
        builder.apply(testSpec, "test", toscaApplication);
        assertEquals(testSpec.getConfig().get(TestEntity.CONF_NAME), "bar");
    }

    // TODO: test other AbstractPropertyValue types.


}
