package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.core.test.entity.TestEntity;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

public class PropertiesConfigKeyModifierTest extends Alien4CloudToscaTest {

    @Mock
    TopologyTreeBuilderService treeBuilder;
    @Mock
    PaaSTopology paaSTopology;

    @BeforeClass
    public void initMocks(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetsScalarProperties() {
        when(treeBuilder.buildPaaSTopology(topology)).thenReturn(paaSTopology);
        when(paaSTopology.getAllNodes()).thenReturn(ImmutableMap.<String, PaaSNodeTemplate>of());

        PropertiesConfigKeyModifier builder = new PropertiesConfigKeyModifier(mgmt, treeBuilder);
        Map<String, AbstractPropertyValue> properties = ImmutableMap.<String, AbstractPropertyValue>of(
                TestEntity.CONF_NAME.getName(), new ScalarPropertyValue("bar"));
        nodeTemplate.setProperties(properties);

        builder.apply(testSpec, nodeTemplate, topology);
        assertEquals(testSpec.getConfig().get(TestEntity.CONF_NAME), "bar");
    }

    // TODO: test other AbstractPropertyValue types.


}
