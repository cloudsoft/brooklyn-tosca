package io.cloudsoft.tosca.a4c;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.BeforeMethod;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

public class Alien4CloudToscaTest extends BrooklynAppUnitTestSupport {

    protected CampPlatform campPlatform;
    protected EntitySpec<TestEntity> testSpec;
    protected NodeTemplate nodeTemplate;
    protected Topology topology;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        campPlatform = new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(mgmt)
                .launch()
                .getCampPlatform();
        testSpec = EntitySpec.create(TestEntity.class);
        nodeTemplate = new NodeTemplate();
        topology = new Topology();
    }

}
