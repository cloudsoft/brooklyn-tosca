package org.apache.brooklyn.tosca.a4c.transformer;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudToscaTest;
import org.apache.brooklyn.tosca.a4c.brooklyn.ToscaPlanToSpecTransformer;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ToscaPlanToSpecTransformerTest extends AbstractAlien4CloudToscaTest {

    ToscaPlanToSpecTransformer transformer;

    @BeforeMethod
    public void setup() throws Exception {
        super.setup();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.injectManagementContext(mgmt);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testComputeTopologyParser(){
        String templateUrl = getClasspathUrlForTemplateResource(COMPUTE_TEMPLATE);

        EntitySpec<? extends Application> app = transformer.createApplicationSpec(
                new ResourceUtils(mgmt).getResourceAsString(templateUrl));

        assertNotNull(app);

        assertEquals(app.getChildren().size(), 1);
        EntitySpec<VanillaSoftwareProcess> vanillaSpec =
                (EntitySpec<VanillaSoftwareProcess>)app.getChildren().get(0);
        assertEquals(vanillaSpec.getType().getName(),
                "org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess");
    }


}
