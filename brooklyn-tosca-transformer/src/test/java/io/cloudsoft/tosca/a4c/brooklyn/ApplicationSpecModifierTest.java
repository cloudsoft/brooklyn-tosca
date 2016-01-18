package io.cloudsoft.tosca.a4c.brooklyn;

import static org.apache.brooklyn.test.Asserts.assertTrue;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecFactory;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecModifier;

public class ApplicationSpecModifierTest extends Alien4CloudToscaTest {

    private static final ConfigKey<String> CONFIG_KEY = ConfigKeys.newStringConfigKey("test.myconfigkey");
    private static final String CONFIG_VALUE = "hello, world";

    private static class TestEntitySpecFactory implements EntitySpecFactory {
        @Override
        public EntitySpec<?> create(NodeTemplate nodeTemplate, Topology topology, boolean hasMultipleChildren) {
            return EntitySpec.create(TestEntity.class);
        }
    }

    private static class TestSpecModifier implements EntitySpecModifier {
        @Override
        public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
            entitySpec.configure(CONFIG_KEY, CONFIG_VALUE);
        }
    }

    @Test
    public void testUsesSpecSelectorAndBuilders() {
        NodeTemplate nodeTemplate = Mockito.mock(NodeTemplate.class);
        EntitySpecFactory selector = new TestEntitySpecFactory();
        EntitySpecModifier specModifier = new TestSpecModifier();
        ApplicationSpecsBuilder specsBuilder = new ApplicationSpecsBuilder(selector, ImmutableList.of(specModifier));
        topology.setNodeTemplates(ImmutableMap.of("node1", nodeTemplate));

        Map<String, EntitySpec<?>> specs = specsBuilder.getSpecs(topology);
        assertTrue(specs.containsKey("node1"), "expected node1 key in specs: " + Joiner.on(", ").withKeyValueSeparator("=").join(specs));
        EntitySpec<?> spec = specs.get("node1");
        assertTrue(spec.getType().isAssignableFrom(TestEntity.class));
        assertEquals(spec.getConfig().get(CONFIG_KEY), CONFIG_VALUE);
    }

}
