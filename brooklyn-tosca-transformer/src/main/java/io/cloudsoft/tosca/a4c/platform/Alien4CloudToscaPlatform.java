package io.cloudsoft.tosca.a4c.platform;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.inject.Inject;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.file.ArchiveBuilder;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.model.components.Csar;
import alien4cloud.model.templates.TopologyTemplate;
import alien4cloud.model.templates.TopologyTemplateVersion;
import alien4cloud.model.topology.Topology;
import alien4cloud.security.model.Role;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.topology.TopologyTemplateVersionService;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;
import io.cloudsoft.tosca.a4c.brooklyn.ConfigLoader;

@Component
public class Alien4CloudToscaPlatform implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudToscaPlatform.class);

    // Beans
    private final BeanFactory beanFactory;
    private final TopologyServiceCore topologyService;
    private final TopologyTemplateVersionService topologyTemplateVersionService;
    private final ArchiveUploadService archiveUploadService;

    // State
    private final File tmpRoot;

    public static void grantAdminAuth() {
        final AnonymousAuthenticationToken anonToken = new AnonymousAuthenticationToken("brooklyn", "java",
                MutableList.of(new SimpleGrantedAuthority(Role.ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(anonToken);
    }

    @Inject
    public Alien4CloudToscaPlatform(BeanFactory beanFactory, TopologyServiceCore topologyService,
            TopologyTemplateVersionService templateVersionService, ArchiveUploadService archiveService) {
        this.beanFactory = beanFactory;
        this.topologyService = topologyService;
        this.topologyTemplateVersionService = templateVersionService;
        this.archiveUploadService = archiveService;

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
    }

    @Deprecated
    public void loadTypesFromUrl(String url, boolean hasMultiple) throws Exception {
        loadTypesFromUrl(url);
    }

    public void loadTypesFromUrl(String url) throws Exception {
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
                            upload(outputPath);
                        }
                    } catch (Exception e) {
                        LOG.warn("Cannot load {}: {}", p.getFileName(), e);
                    }
                }
            }
        } else {
            Path outputPath = Paths.get(zipName.toString() + ".csar.zip");
            FileUtil.zip(zipExploded.resolve(zipRootDir), outputPath);
            upload(outputPath);
        }

    }

    public void upload(Path zip) throws ParsingException, CSARVersionAlreadyExistsException {
        LOG.debug("Uploading type: " + zip);
        ParsingResult<Csar> types = archiveUploadService.upload(zip);
        if (ArchiveUploadService.hasError(types, ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Errors parsing types:\n" + Strings.join(types.getContext().getParsingErrors(), "\n  "));
        }
    }

    @Override
    public synchronized void close() {
        Os.deleteRecursively(tmpRoot);
    }

    // TODO: Uses of this should be turned into proper methods on this class.
    @Deprecated
    public <T> T getBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    public ParsingResult<Csar> uploadSingleYaml(InputStream resourceFromUrl, String callerReferenceName) {
        try {
            String nameCleaned = Strings.makeValidFilename(callerReferenceName);
            File tmpBase = new File(tmpRoot, nameCleaned + "_" + Identifiers.makeRandomId(6));
            File tmpExpanded = new File(tmpBase, nameCleaned + "_" + Identifiers.makeRandomId(6));
            File tmpTarget = new File(tmpExpanded.toString() + ".csar.zip");
            boolean created = tmpExpanded.mkdirs();
            if (!created) {
                throw new Exception("Failed to create '" + tmpExpanded + "' when uploading yaml from " + nameCleaned);
            }
            FileUtils.copyInputStreamToFile(resourceFromUrl, new File(tmpExpanded, nameCleaned + ".yaml"));
            ArchiveBuilder.archive(tmpTarget.toString()).addDirContentsAt(tmpExpanded, "").create();

            try {
                return uploadArchive(tmpTarget, callerReferenceName);
            } finally {
                Os.deleteRecursively(tmpBase);
            }

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public ParsingResult<Csar> uploadArchive(InputStream resourceFromUrl, String callerReferenceName) {
        try {
            File f = new File(tmpRoot, callerReferenceName + "_" + Identifiers.makeRandomId(8));
            Streams.copy(resourceFromUrl, new FileOutputStream(f));
            return uploadArchive(f, callerReferenceName);

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private ParsingResult<Csar> uploadArchive(File zipFile, String callerReferenceName) {
        try {
            String nameCleaned = Strings.makeValidFilename(callerReferenceName);
            ParsingResult<Csar> result = archiveUploadService.upload(Paths.get(zipFile.toString()));

            if (ArchiveUploadService.hasError(result, null)) {
                LOG.debug("A4C parse notes for " + nameCleaned + ":\n  " + Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }
            if (ArchiveUploadService.hasError(result, ParsingErrorLevel.ERROR)) {
                // archive will not be installed in this case, so we should throw
                throw new UserFacingException("Could not parse " + callerReferenceName + " as TOSCA:\n  "
                        + Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }

            return result;

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public Topology getTopologyOfCsar(Csar cs) {
        TopologyTemplate tt = topologyService.searchTopologyTemplateByName(cs.getName());
        if (tt == null) return null;
        TopologyTemplateVersion[] ttv = topologyTemplateVersionService.getByDelegateId(tt.getId());
        if (ttv == null || ttv.length == 0) return null;
        return topologyService.getTopology(ttv[0].getTopologyId());
    }

}
