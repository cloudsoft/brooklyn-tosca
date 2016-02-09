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
import io.cloudsoft.tosca.a4c.brooklyn.Uploader;

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
        Uploader uploader = applicationContext.getBean(Uploader.class);
        String name = "simple-web-server.yaml";
        String url = "classpath://templates/" + name;
        ParsingResult<Csar> tp = uploader.uploadSingleYaml(new ResourceUtils(platform).getResourceFromUrl(url), name);

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
    }

    public ParsingResult<ArchiveRoot> sampleParseTosca(Alien4CloudToscaPlatform platform) throws Exception {
        ToscaParser parser = platform.getBean(ToscaParser.class);
        ParsingResult<ArchiveRoot> tp = parser.parseFile("<classpath>", "pizza.tosca",
            new ResourceUtils(this).getResourceFromUrl("classpath://templates/pizza.tosca.yaml"), null);
        return tp;
    }

    @Test
    public void testCanParseSampleTosca() throws Exception {
        try {
            ParsingResult<ArchiveRoot> tp = sampleParseTosca(platform);
            Assert.assertTrue( tp.getResult().getNodeTypes().containsKey("tosca.nodes.WebApplication.PayPalPizzaStore") );
        } finally {
            if (platform!=null) platform.close();
        }
    }
}
