package io.cloudsoft.tosca.a4c.brooklyn.spec;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;

public class PropertiesConfigKeyModifierTest extends Alien4CloudToscaTest {

    @Test
    public void testSetsScalarProperties() {
        PropertiesConfigKeyModifier builder = new PropertiesConfigKeyModifier(mgmt);
        Map<String, AbstractPropertyValue> properties = ImmutableMap.<String, AbstractPropertyValue>of(
                TestEntity.CONF_NAME.getName(), new ScalarPropertyValue("bar"));
        nodeTemplate.setProperties(properties);

        builder.apply(testSpec, nodeTemplate, topology);
        assertEquals(testSpec.getConfig().get(TestEntity.CONF_NAME), "bar");
    }

    // TODO: test other AbstractPropertyValue types.

}
