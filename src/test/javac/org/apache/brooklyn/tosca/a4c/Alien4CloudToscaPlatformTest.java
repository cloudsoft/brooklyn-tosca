package org.apache.brooklyn.tosca.a4c;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.testng.Assert;
import org.testng.annotations.Test;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.topology.TopologyService;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;

public class Alien4CloudToscaPlatformTest {

    /**
     * Sample instantiation of Alien4Cloud platform.
     *
     * @param args Arguments that will be delegated to spring.
     */
    public static void main(String[] args) throws Exception {
        Alien4CloudToscaPlatform.grantAdminAuth();
        Alien4CloudToscaPlatform platform = Alien4CloudToscaPlatform.newInstance(args);
        
        ParsingResult<ArchiveRoot> tp = new Alien4CloudToscaPlatformTest().sampleParseTosca(platform);
        
        System.out.println(tp);

        Alien4CloudToscaPlatform.loadNormativeTypes(platform);
        
        tp = platform.getToscaParser().parseFile("<classpath>", "basic-compute.tosca",
            new ResourceUtils(platform).getResourceFromUrl("classpath:/org/apache/brooklyn/tosca/a4c/"
//                + "sample/basic-compute.tosca"
                + "script1.tosca.yaml"
                ), null);

        if (!tp.getContext().getParsingErrors().isEmpty()) {
            System.out.println("ERRORS:\n  "+Strings.join(tp.getContext().getParsingErrors(), "\n  "));
        }
        ArchiveRoot t = tp.getResult();
        // TODO complains if archive doesn't have name (which it won't with the above parse)
//        platform.getCsarService().save(t.getArchive());
        TopologyService ts = platform.getBean(TopologyService.class);
        
        System.out.println("Topology is:\n"+ts.getYaml(t.getTopology()));
        
        MutableList<NodeTemplate> templates = MutableList.copyOf(t.getTopology().getNodeTemplates().values());
        System.out.println("Node templates "+templates);
        
        System.out.println("Node types "+t.getNodeTypes());
        
        t.getArchive().setName("on-the-go");
        t.getArchive().setVersion("1.0.0");
        platform.getCsarService().save(t.getArchive());

//        t.getNodeTypes().values().iterator().next().setArchiveName(t.getArchive().getName());
//        t.getNodeTypes().values().iterator().next().setArchiveVersion(t.getArchive().getVersion());
//        ts.loadType(t.getTopology(), t.getNodeTypes().values().iterator().next());
//        t.getTopology().getDependencies().add(new CSARDependency(t.getArchive().getName(), t.getArchive().getVersion()));
//        TopologyDTO td = ts.buildTopologyDTO(t.getTopology());
//        System.out.println("DTO toplogy:\n"+td.getYaml());
        
        platform.close();
    }

    public ParsingResult<ArchiveRoot> sampleParseTosca(Alien4CloudToscaPlatform platform) throws Exception {
        ToscaParser parser = platform.getToscaParser();
        ParsingResult<ArchiveRoot> tp = parser.parseFile("<classpath>", "pizza.tosca",
            new ResourceUtils(this).getResourceFromUrl("classpath:/org/apache/brooklyn/tosca/a4c/sample/pizza.tosca"), null);
        return tp;
    }

    // TODO mark integration if we can't make it start fast!
    @Test
    // older eclipse testng plugins will fail because they drag in an older version of snake yaml
    public void testCanParseSampleTosca() throws Exception {
        Alien4CloudToscaPlatform platform = null;
        try {
            platform = Alien4CloudToscaPlatform.newInstance();
            ParsingResult<ArchiveRoot> tp = new Alien4CloudToscaPlatformTest().sampleParseTosca(platform);
            
            Assert.assertTrue( tp.getResult().getNodeTypes().containsKey("tosca.nodes.WebApplication.PayPalPizzaStore") );
            
        } finally {
            if (platform!=null) platform.close();
        }
    }
    
}
