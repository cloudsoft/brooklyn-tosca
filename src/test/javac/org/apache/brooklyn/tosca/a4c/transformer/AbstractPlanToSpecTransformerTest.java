package org.apache.brooklyn.tosca.a4c.transformer;

import org.apache.brooklyn.tosca.a4c.AbstractAlien4CloudToscaTest;
import org.apache.brooklyn.tosca.a4c.brooklyn.ToscaPlanToSpecTransformer;
import org.testng.annotations.BeforeMethod;

public class AbstractPlanToSpecTransformerTest extends AbstractAlien4CloudToscaTest {

    protected ToscaPlanToSpecTransformer transformer;

    @BeforeMethod
    public void setup() throws Exception {
        super.setup();
        transformer = new ToscaPlanToSpecTransformer();
        transformer.injectManagementContext(mgmt);
    }
}
