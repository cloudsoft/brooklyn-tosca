package io.cloudsoft.tosca.a4c.platform;

import java.util.Set;

import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.Csar;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import io.cloudsoft.tosca.a4c.Alien4CloudIntegrationTest;

public class Alien4CloudToscaPlatformIntegrationTest extends Alien4CloudIntegrationTest {

    /**
     * Sample instantiation of Alien4Cloud platform.
     *
     * @param args Arguments that will be delegated to spring.
     */
    public static void main(String[] args) throws Exception {
        Alien4CloudToscaPlatform.grantAdminAuth();
        ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(new LocalManagementContext());
        Alien4CloudToscaPlatform platform = applicationContext.getBean(Alien4CloudToscaPlatform.class);
        
        platform.loadNormativeTypes();
        
        String name = "script1.tosca.yaml";
        String url = "classpath://templates/" + name;
        ParsingResult<Csar> tp = platform.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl(url), name);
        
        explore(platform, tp);
        
        platform.close();
    }

    @SuppressWarnings("unused")
    private static void explore(Alien4CloudToscaPlatform platform, ParsingResult<Csar> tp) {
        Csar cs = tp.getResult();
        System.out.println(cs);

        Set<CSARDependency> deps = MutableSet.<CSARDependency>builder().addAll(cs.getDependencies()).add(new CSARDependency(cs.getName(), cs.getVersion())).build();
        IndexedNodeType hello = platform.getBean(CSARRepositorySearchService.class).getElementInDependencies(IndexedNodeType.class, "my.Hello", deps);
        IndexedNodeType dbms = platform.getBean(CSARRepositorySearchService.class).getElementInDependencies(IndexedNodeType.class, "tosca.nodes.DBMS", deps);

        Topology topo = platform.getTopologyOfCsar(cs);
        
        PaaSTopology paasTopo = platform.getBean(TopologyTreeBuilderService.class).buildPaaSTopology(topo);
        NodeTemplate nt = topo.getNodeTemplates().get("script_hello");
        PaaSNodeTemplate pnt = paasTopo.getAllNodes().get("script_hello");
        
        System.out.println(topo);
    }

    public ParsingResult<ArchiveRoot> sampleParseTosca(Alien4CloudToscaPlatform platform) throws Exception {
        ToscaParser parser = platform.getToscaParser();
        ParsingResult<ArchiveRoot> tp = parser.parseFile("<classpath>", "pizza.tosca",
            new ResourceUtils(this).getResourceFromUrl("classpath://io/cloudsoft/tosca/a4c/sample/pizza.tosca"), null);
        return tp;
    }

    @Test
    public void testCanParseSampleTosca() throws Exception {
        try {
            ParsingResult<ArchiveRoot> tp = new Alien4CloudToscaPlatformIntegrationTest().sampleParseTosca(platform);
            Assert.assertTrue( tp.getResult().getNodeTypes().containsKey("tosca.nodes.WebApplication.PayPalPizzaStore") );
        } finally {
            if (platform!=null) platform.close();
        }
    }
    
    @Test
    public void testCanLoadArchiveWithPolicy() throws Exception {
        try {
            String name = "script1.tosca.yaml";
            String url = "classpath://templates/" + name;
            ParsingResult<Csar> tp = platform.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl(url), name);
            Topology t = platform.getTopologyOfCsar(tp.getResult());
            NodeGroup g1 = t.getGroups().values().iterator().next();
            Assert.assertNotNull(g1);
            Assert.assertNotNull(g1.getPolicies());
            Assert.assertEquals(g1.getPolicies().size(), 1);
            Assert.assertNotNull(g1.getPolicies().get(0));
        } finally {
            if (platform!=null) platform.close();
        }
    }
    
}
