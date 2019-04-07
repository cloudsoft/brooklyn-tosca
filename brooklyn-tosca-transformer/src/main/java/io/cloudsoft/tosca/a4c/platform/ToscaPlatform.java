package io.cloudsoft.tosca.a4c.platform;

import java.nio.file.Path;

import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;

public interface ToscaPlatform {

    void loadTypesFromUrl(String url) throws Exception;

    // TODO: Uses of this should be turned into proper methods on this class.
    @Deprecated
    <T> T getBean(Class<T> type);

    // TODO methods below shouldn't have same name as they are quite different
    /** parse a plan (service template) as yaml text */
    ToscaApplication parse(String plan, BrooklynClassLoadingContext brooklynClassLoadingContext);

    /** parse a CSAR given path to the archive */
    ToscaApplication parse(Path path);

    ToscaApplication getToscaApplication(String id);
}
