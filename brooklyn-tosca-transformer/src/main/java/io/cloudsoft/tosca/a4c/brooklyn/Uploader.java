package io.cloudsoft.tosca.a4c.brooklyn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

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
import org.springframework.stereotype.Component;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.model.components.Csar;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;

@Component
public class Uploader {
    private static final Logger LOG = LoggerFactory.getLogger(Uploader.class);

    private ArchiveUploadService archiveUploadService;
    private final File tmpRoot;

    @Inject
    public Uploader(ArchiveUploadService archiveUploadService){
        this.archiveUploadService = archiveUploadService;
        tmpRoot = Os.newTempDir("brooklyn-a4c");
        Os.deleteOnExitRecursively(tmpRoot);
    }

    public void upload(Path zip) throws ParsingException, CSARVersionAlreadyExistsException {
        LOG.debug("Uploading type: " + zip);
        ParsingResult<Csar> types = archiveUploadService.upload(zip);
        if (ArchiveUploadService.hasError(types, ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Errors parsing types:\n" + Strings.join(types.getContext().getParsingErrors(), "\n  "));
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

    public ParsingResult<Csar> uploadArchive(File zipFile, String callerReferenceName) {
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
            throw Exceptions.propagate("Error uploading archive " + callerReferenceName, e);
        }
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

}
