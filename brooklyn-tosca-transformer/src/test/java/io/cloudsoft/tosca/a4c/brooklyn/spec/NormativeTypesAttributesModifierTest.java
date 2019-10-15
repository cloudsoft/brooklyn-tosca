package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.annotation.Nullable;

import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.enricher.stock.Transformer;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import io.cloudsoft.tosca.a4c.Alien4CloudToscaTest;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

public class NormativeTypesAttributesModifierTest extends Alien4CloudToscaTest {

    @Mock
    private ToscaFacade<?> alien4CloudFacade;
    @Mock
    private ToscaApplication toscaApplication;
    @Mock
    private NodeTemplate nodeTemplate;

    @BeforeClass
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSensorsAreInitialised() {
        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class);
        NormativeTypesAttributesModifier modifier = new NormativeTypesAttributesModifier(mgmt, alien4CloudFacade);
        modifier.apply(spec, "", toscaApplication);
        assertEnricherCreated(spec, "ip_address", Attributes.ADDRESS);
        assertEnricherCreated(spec, "private_address", Attributes.SUBNET_ADDRESS);
        assertEnricherCreated(spec, "public_address", Attributes.ADDRESS);
        assertEnricherCreated(spec, "public_ip_address", Attributes.ADDRESS);
    }

    private void assertEnricherCreated(EntitySpec<?> spec, final String publishingName, final Sensor<?> sourceSensor) {
        Assert.assertTrue(Iterables.tryFind(spec.getEnricherSpecs(), new Predicate<EnricherSpec<?>>() {
            @Override
            public boolean apply(@Nullable EnricherSpec<?> enricherSpec) {
                return enricherSpec.getType().equals(Transformer.class)
                    && ((Sensor<?>)enricherSpec.getConfig().get(Transformer.TARGET_SENSOR)).getName().equals(publishingName)
                    && enricherSpec.getConfig().get(Transformer.SOURCE_SENSOR).equals(sourceSensor);
            }
        }).isPresent());
    }
}
