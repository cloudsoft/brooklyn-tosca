package io.cloudsoft.tosca.a4c.brooklyn;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampTypePlanTransformer;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

import io.cloudsoft.tosca.a4c.Alien4CloudToscaPlatform;

public class EnablementTest extends BrooklynAppUnitTestSupport {

    @Test(expectedExceptions = {IllegalStateException.class})
    public void testTransformerRejectsBlueprintWhenFeatureDisabled() throws Exception {
        BrooklynFeatureEnablement.disable(ToscaPlanToSpecTransformer.FEATURE_TOSCA_ENABLED);
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            Alien4CloudToscaPlatform platform = Alien4CloudToscaPlatform.newInstance();
            mgmt.getBrooklynProperties().put(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM, platform);
            platform.loadNormativeTypes();
            ToscaPlanToSpecTransformer transformer = new ToscaPlanToSpecTransformer();
            transformer.setManagementContext(mgmt);

            // Should throw.
            transformer.createApplicationSpec(
                    new ResourceUtils(mgmt).getResourceAsString("classpath://templates/helloworld-sql.tosca.yaml"));
        } finally {
            BrooklynFeatureEnablement.enable(ToscaPlanToSpecTransformer.FEATURE_TOSCA_ENABLED);
        }
    }

    @Test
    public void testCreateSpecFromPlanFailsWhenFeatureDisabled() throws Exception {
        BrooklynFeatureEnablement.disable(ToscaPlanToSpecTransformer.FEATURE_TOSCA_ENABLED);
        try {
            Alien4CloudToscaPlatform.grantAdminAuth();
            Alien4CloudToscaPlatform platform = Alien4CloudToscaPlatform.newInstance();
            mgmt.getBrooklynProperties().put(ToscaPlanToSpecTransformer.TOSCA_ALIEN_PLATFORM, platform);
            platform.loadNormativeTypes();
            ToscaPlanToSpecTransformer transformer = new ToscaPlanToSpecTransformer();
            transformer.setManagementContext(mgmt);

            new BrooklynCampPlatformLauncherNoServer()
                    .useManagementContext(mgmt)
                    .launch()
                    .getCampPlatform();

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
            BrooklynFeatureEnablement.enable(ToscaPlanToSpecTransformer.FEATURE_TOSCA_ENABLED);
        }
    }
}
