package io.cloudsoft.tosca.a4c.platform;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.tosca.repository.LocalRepositoryImpl;
import io.cloudsoft.tosca.a4c.Alien4CloudIntegrationTest;
import io.cloudsoft.tosca.a4c.brooklyn.Uploader;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.types.NodeType;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

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
        NodeType hello = platform.getBean(LocalRepositoryImpl.class).getElementInDependencies(NodeType.class, "my.Hello", deps);
        NodeType dbms = platform.getBean(LocalRepositoryImpl.class).getElementInDependencies(NodeType.class, "tosca.nodes.DBMS", deps);
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
