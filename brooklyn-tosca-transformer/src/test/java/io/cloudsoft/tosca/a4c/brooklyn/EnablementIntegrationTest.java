package io.cloudsoft.tosca.a4c.brooklyn;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampTypePlanTransformer;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.core.typereg.UnsupportedTypePlanException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

import io.cloudsoft.tosca.a4c.Alien4CloudIntegrationTest;
import io.cloudsoft.tosca.a4c.brooklyn.plan.ToscaTypePlanTransformer;

public class EnablementIntegrationTest extends Alien4CloudIntegrationTest {

    @Test(expectedExceptions = {UnsupportedTypePlanException.class})
    public void testTransformerRejectsBlueprintWhenFeatureDisabled() throws Exception {
        BrooklynFeatureEnablement.disable(ToscaTypePlanTransformer.FEATURE_TOSCA_ENABLED);
        try {
            // Should throw.
            create("classpath://templates/helloworld-sql.tosca.yaml");
        } finally {
            BrooklynFeatureEnablement.enable(ToscaTypePlanTransformer.FEATURE_TOSCA_ENABLED);
        }
    }

    @Test
    public void testCreateSpecFromPlanFailsWhenFeatureDisabled() throws Exception {
        BrooklynFeatureEnablement.disable(ToscaTypePlanTransformer.FEATURE_TOSCA_ENABLED);
        try {
            try {
                mgmt.getTypeRegistry().createSpecFromPlan(CampTypePlanTransformer.FORMAT,
                        new ResourceUtils(mgmt).getResourceAsString("classpath://templates/helloworld-sql.tosca.yaml"),
                        RegisteredTypeLoadingContexts.spec(Application.class),
                        EntitySpec.class);
                fail("Expected spec creation to fail because tosca was disabled");
            } catch (Exception e) {
                final Throwable firstInteresting = Exceptions.getFirstThrowableMatching(e, Predicates.instanceOf(IllegalStateException.class));
                assertNotNull(firstInteresting, "Expected " + IllegalStateException.class.getName());
            }
        } finally {
            BrooklynFeatureEnablement.enable(ToscaTypePlanTransformer.FEATURE_TOSCA_ENABLED);
        }
    }
}
