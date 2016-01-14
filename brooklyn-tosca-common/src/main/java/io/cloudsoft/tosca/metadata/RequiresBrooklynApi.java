package io.cloudsoft.tosca.metadata;

import org.apache.brooklyn.rest.client.BrooklynApi;

public interface RequiresBrooklynApi {

    void setBrooklynApi(BrooklynApi brooklynApi);
}
