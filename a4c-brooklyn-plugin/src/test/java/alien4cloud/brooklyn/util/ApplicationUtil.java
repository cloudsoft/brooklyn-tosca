package alien4cloud.brooklyn.util;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

import javax.annotation.Resource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.utils.YamlParserUtil;

@Component
@Slf4j
public class ApplicationUtil {

    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";

    @Resource
    private ApplicationService applicationService;

    @Resource
    protected ElasticSearchDAO alienDAO;

    @SneakyThrows
    public Topology createAlienApplication(String applicationName, String topologyFileName) {
        Topology topology = parseYamlTopology(topologyFileName);
        applicationService.create("alien", null, applicationName, null, topology);
        alienDAO.save(topology);
        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName) throws IOException {
        Topology topology = YamlParserUtil.parseFromUTF8File(Paths.get(TOPOLOGIES_PATH + topologyFileName + ".yaml"), Topology.class);
        topology.setId(UUID.randomUUID().toString());
        return topology;
    }
}
