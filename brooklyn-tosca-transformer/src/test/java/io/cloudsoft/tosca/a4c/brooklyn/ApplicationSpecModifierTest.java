package io.cloudsoft.tosca.a4c.brooklyn;

import static org.apache.brooklyn.test.Asserts.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import alien4cloud.model.topology.NodeTemplate;
import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecFactory;
import io.cloudsoft.tosca.a4c.brooklyn.spec.EntitySpecModifier;

public class ApplicationSpecModifierTest extends Alien4CloudToscaTest {

    private static final ConfigKey<String> CONFIG_KEY = ConfigKeys.newStringConfigKey("test.myconfigkey");
    private static final String CONFIG_VALUE = "hello, world";

    private static class TestEntitySpecFactory implements EntitySpecFactory {
        @Override
        public EntitySpec<?> create(String nodeId, ToscaApplication toscaApplication, boolean hasMultipleChildren) {
            return EntitySpec.create(TestEntity.class);
        }
    }

    private static class TestSpecModifier implements EntitySpecModifier {
        @Override
        public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
            entitySpec.configure(CONFIG_KEY, CONFIG_VALUE);
        }
    }

    @Mock
    private NodeTemplate nodeTemplate;
    @Mock
    private ToscaFacade<Alien4CloudApplication> alien4CloudFacade;
    @Mock
    private Alien4CloudApplication toscaApplication;


    @BeforeClass
    public void initMocks(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testUsesSpecSelectorAndBuilders() {
        EntitySpecFactory selector = new TestEntitySpecFactory();
        EntitySpecModifier specModifier = new TestSpecModifier();
        ApplicationSpecsBuilder specsBuilder = new Alien4CloudApplicationSpecsBuilder(mgmt, selector, ImmutableList.of(specModifier), alien4CloudFacade);
        when(toscaApplication.getNodeIds()).thenReturn(ImmutableList.of("node1"));
        when(toscaApplication.getNodeName(anyString())).thenReturn(Optional.of("Test"));
        Map<String, EntitySpec<?>> specs = specsBuilder.getSpecs(toscaApplication);
        assertTrue(specs.containsKey("node1"), "expected node1 key in specs: " + Joiner.on(", ").withKeyValueSeparator("=").join(specs));
        EntitySpec<?> spec = specs.get("node1");
        assertTrue(spec.getType().isAssignableFrom(TestEntity.class));
        assertEquals(spec.getConfig().get(CONFIG_KEY), CONFIG_VALUE);
    }

}
