package alien4cloud.brooklyn;

import brooklyn.rest.client.BrooklynApi;

/**
 * Created by lucboutier on 27/05/15.
 */
public class Main {

    // TODO remove?
    
    public static void main(String[] args) throws Exception {
        BrooklynApi brooklynApi = new BrooklynApi("http://localhost:8888", "brooklyn", "kktkecBomw");
        brooklynApi.getCatalogApi().getEntity("brooklyn.entity.webapp.tomcat.TomcatServer", "0.7.0-SNAPSHOT");
    }
}
