package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

// FIXME [2016-01-20 SJC]: Implementation must be revisited.
// * Should use Brooklyn's install directory.
// * Should use artifact key as environment variable, not name (which is often the resource URL).
// * Should clarify with A4C difference between 'implementation' and 'file'. The former is
//   supported but is not in the spec.
// * Should support more than just tosca.artifacts.File.
// * Should clarify the difference between use of nodeTemplate and indexedNodeTemplate.
// * Should not set environment variables like "export = ~/brooklyn-tosca-resources".
// TODO: Rename to highlight that it is handling artifacts.
@Component
public class RuntimeEnvironmentModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeEnvironmentModifier.class);

    @Inject
    public RuntimeEnvironmentModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        // TODO: Difference between artifacts on indexed node template and nodeTemplate?
        final Iterable<String> artifacts = getToscaFacade().getArtifacts(nodeId, toscaApplication);
        if (Iterables.isEmpty(artifacts)) {
            return;
        }

        final Map<String, String> filesToCopy = MutableMap.of();
        final List<String> preInstallCommands = MutableList.of();

        for (String artifactId : artifacts) {

            Optional<Path> optionalResourcesRootPath = getToscaFacade().getArtifactPath(nodeId, toscaApplication, artifactId);
            if(!optionalResourcesRootPath.isPresent()) {
                continue;
            }

            // TODO: Use standard brooklyn directory rather than homedir.
            final String destRoot = Os.mergePaths("~", "brooklyn-tosca-resources", artifactId);
            preInstallCommands.add("mkdir -p " + destRoot);
            // Artifact names may be referred to as environment variables in blueprint scripts.
            entitySpec.configure(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactId), destRoot);

            final String tempRoot = Os.mergePaths("/tmp", artifactId);
            preInstallCommands.add("mkdir -p " + tempRoot);


            // Copy all files in resource.
            try {
                Files.walkFileTree(optionalResourcesRootPath.get(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String tempDest = Os.mergePaths(tempRoot, file.getFileName().toString());
                        String finalDest = Os.mergePaths(destRoot, file.getFileName().toString());
                        filesToCopy.put(file.toAbsolutePath().toString(), tempDest);
                        preInstallCommands.add(String.format("mv %s %s", tempDest, finalDest));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }  catch (IOException e) {
                LOG.warn("Cannot parse CSAR resources", e);
            }
        }

        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_COMMAND, Joiner.on("\n").join(preInstallCommands));
    }
}
