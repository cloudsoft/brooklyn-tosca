package io.cloudsoft.tosca.a4c.platform;

import java.io.Closeable;
import java.nio.file.Path;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;

public interface ToscaPlatform {

    void loadTypesFromUrl(String url) throws Exception;

    // TODO: Uses of this should be turned into proper methods on this class.
    @Deprecated
    <T> T getBean(Class<T> type);

    ToscaApplication parse(String plan);

    ToscaApplication parse(Path path);

    ToscaApplication getToscaApplication(String id);
}
