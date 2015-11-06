package alien4cloud.brooklyn;


import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.tosca.ArchiveParser;
import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class PluginTest {

    @Test
    public void testPlugin() {
        log.info("start test");
    }
}