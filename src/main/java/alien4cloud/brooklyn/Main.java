package alien4cloud.brooklyn;

import brooklyn.rest.client.BrooklynApi;
import brooklyn.rest.domain.CatalogEntitySummary;

/**
 * Created by lucboutier on 27/05/15.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        BrooklynApi brooklynApi = new BrooklynApi("http://localhost:8888", "brooklyn", "kktkecBomw");
        CatalogEntitySummary tomcatEntity = brooklynApi.getCatalogApi().getEntity("brooklyn.entity.webapp.tomcat.TomcatServer", "0.7.0-SNAPSHOT");
    }
}
