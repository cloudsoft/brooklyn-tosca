package io.cloudsoft.tosca.a4c.platform;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.inject.Inject;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

import alien4cloud.security.model.Role;
import alien4cloud.utils.FileUtil;
import io.cloudsoft.tosca.a4c.brooklyn.ConfigLoader;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;
import io.cloudsoft.tosca.a4c.brooklyn.Uploader;

@Component
public class Alien4CloudToscaPlatform implements ToscaPlatform {

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudToscaPlatform.class);

    // Beans
    private final BeanFactory beanFactory;
    private ToscaFacade alien4CloudFacade;
    private Uploader uploader;

    // State
    private final File tmpRoot;

    public static void grantAdminAuth() {
        final AnonymousAuthenticationToken anonToken = new AnonymousAuthenticationToken("brooklyn", "java",
                MutableList.of(new SimpleGrantedAuthority(Role.ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(anonToken);
    }

    @Inject
    public Alien4CloudToscaPlatform(BeanFactory beanFactory, ToscaFacade alien4CloudFacade, Uploader uploader) {
        this.beanFactory = beanFactory;
        this.alien4CloudFacade = alien4CloudFacade;
        this.uploader = uploader;
        tmpRoot = Os.newTempDir("brooklyn-a4c");
        Os.deleteOnExitRecursively(tmpRoot);
        loadDefaultTypes();
    }

    private void loadDefaultTypes() {
        // NullPointerException thrown in alien4cloud.security.AuthorizationUtil if admin auth is not granted.
        grantAdminAuth();
        final Iterable<String> defaultTypes = ConfigLoader.getDefaultTypes();
        try {
            for (String resource : defaultTypes) {
                // TODO: Check for cached location too.
                loadTypesFromUrl(resource);
            }
        } catch (Exception e) {
            throw Exceptions.propagate("Error loading default types " + Iterables.toString(defaultTypes), e);
        }
        final String brooklynTypes = "classpath://brooklyn/types/brooklyn-types.yaml";
        LOG.info("Loading types from " + brooklynTypes);
        uploader.uploadSingleYaml(new ResourceUtils(this).getResourceFromUrl(brooklynTypes), "brooklyn-types");
    }

    @Override
    public void loadTypesFromUrl(String url) throws Exception {
        LOG.info("Loading types from " + url);
        Path artifactsDirectory = Paths.get(tmpRoot.toString(), "url-types");
        Path zipName = artifactsDirectory.resolve(Paths.get("url-types." + Identifiers.makeRandomId(6)));
        Path zipNameAndExtension = Paths.get(zipName.toString() + ".orig.zip");
        Files.createDirectories(zipNameAndExtension.getParent());

        // Retrieve resource and copy to zipNameAndExtension.
        Streams.copy(new ResourceUtils(Alien4CloudToscaPlatform.class).getResourceFromUrl(url),
                new FileOutputStream(zipNameAndExtension.toString()));
        Path zipExploded = Paths.get(zipName.toString() + "_expanded");
        FileUtil.unzip(zipNameAndExtension, zipExploded);

        // Root of zip should contain exactly one directory.
        final String zipRootDir = Iterables.getOnlyElement(Arrays.asList(zipExploded.toFile().list()));
        final Path zipRootDirPath = zipExploded.resolve(zipRootDir);

        // If zipRootDir has a file whose name ends ".yml" or ".yaml" then attempt to load it as a single item,
        // otherwise check all child directories.
        boolean hasMultiple;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(zipRootDirPath, "*.{yaml,yml}")) {
            hasMultiple = Iterables.isEmpty(s);
        }

        if (hasMultiple) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(zipExploded.resolve(zipRootDir))) {
                for (Path p : directoryStream) {
                    try {
                        if (Files.isDirectory(p)) {
                            Path outputPath = Paths.get(zipName.toString(), p.getFileName() + ".csar.zip");
                            FileUtil.zip(p, outputPath);
                            uploader.upload(outputPath);
                        }
                    } catch (Exception e) {
                        LOG.warn("Cannot load {}: {}", p.getFileName(), e);
                    }
                }
            }
        } else {
            Path outputPath = Paths.get(zipName.toString() + ".csar.zip");
            FileUtil.zip(zipExploded.resolve(zipRootDir), outputPath);
            uploader.upload(outputPath);
        }
    }

    public synchronized void close() {
        Os.deleteRecursively(tmpRoot);
    }

    // TODO: Uses of this should be turned into proper methods on this class.
    @Override
    @Deprecated
    public <T> T getBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    @Override
    public ToscaApplication parse(String plan) {
        return alien4CloudFacade.parsePlan(plan, uploader);
    }

    @Override
    public ToscaApplication getToscaApplication(String id) {
        return alien4CloudFacade.newToscaApplication(id);
    }
}
