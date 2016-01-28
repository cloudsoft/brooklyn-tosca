package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;

import alien4cloud.model.components.ComplexPropertyValue;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.mockito.Mock;
import org.apache.brooklyn.util.collections.MutableMap;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import alien4cloud.model.components.AbstractPropertyValue;
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

    @Test
    public void testCreatingAnEntitySpecChildFromAConfigKey() {
        when(treeBuilder.buildPaaSTopology(topology)).thenReturn(paaSTopology);
        when(paaSTopology.getAllNodes()).thenReturn(ImmutableMap.<String, PaaSNodeTemplate>of());
        PropertiesConfigKeyModifier builder = new PropertiesConfigKeyModifier(mgmt, treeBuilder);


        ComplexPropertyValue entitySpecConfigValue = new ComplexPropertyValue(
                MutableMap.of("$brooklyn:entitySpec",
                        (Object) MutableMap.of("type", "org.apache.brooklyn.entity.webapp.jboss.JBoss7Server")));

        Map<String, AbstractPropertyValue> properties =
                ImmutableMap.<String, AbstractPropertyValue>of(
                        TestEntity.CHILD_SPEC.getName(),
                        entitySpecConfigValue);
        nodeTemplate.setProperties(properties);

        builder.apply(testSpec, nodeTemplate, topology);
        assertNotNull(testSpec.getConfig().get(TestEntity.CHILD_SPEC));
    }

    // TODO: test other AbstractPropertyValue types.


}
