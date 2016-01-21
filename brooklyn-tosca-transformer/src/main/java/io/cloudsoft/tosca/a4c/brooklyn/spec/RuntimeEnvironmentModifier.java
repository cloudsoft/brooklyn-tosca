package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;

import alien4cloud.component.repository.ICsarRepositry;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;

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

    private final ICsarRepositry csarFileRepository;

    @Inject
    public RuntimeEnvironmentModifier(ManagementContext mgmt, ICsarRepositry fileRepository) {
        super(mgmt);
        this.csarFileRepository = fileRepository;
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, NodeTemplate nodeTemplate, Topology topology) {
        // TODO: Difference between artifacts on indexed node template and nodeTemplate?
        final Map<String, DeploymentArtifact> artifacts = getIndexedNodeTemplate(nodeTemplate, topology).get().getArtifacts();

        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }

        final Map<String, String> filesToCopy = MutableMap.of();
        final List<String> preInstallCommands = MutableList.of();

        for (final Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
            final DeploymentArtifact artifact = artifactEntry.getValue();
            if (artifact == null) {
                continue;
            } else if (!"tosca.artifacts.File".equals(artifact.getArtifactType())) {
                LOG.info("Skipping unsupported artifact type: " + artifact.getArtifactType());
                continue;
            }

            // In SM8 observe artifactName being the full URL of an artifact.
            final String artifactName = artifact.getArtifactName();
            // Sanity check
            if (Strings.isBlank(artifactName)) {
                LOG.warn("Skipping artifact " + artifact + ": " + " no name known for location on destination");
                continue;
            }

            // TODO: Use standard brooklyn directory rather than homedir.
            final String destRoot = Os.mergePaths("~", "brooklyn-tosca-resources", artifactName);
            final String tempRoot = Os.mergePaths("/tmp", artifactName);

            preInstallCommands.add("mkdir -p " + destRoot);
            preInstallCommands.add("mkdir -p " + tempRoot);

            // Artifact names may be referred to as environment variables in blueprint scripts.
            entitySpec.configure(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactName), destRoot);

            // Copy all files in resource.
            try {
                Path csarPath = csarFileRepository.getCSAR(artifact.getArchiveName(), artifact.getArchiveVersion());

                Path resourcesRootPath = Paths.get(csarPath.getParent().toAbsolutePath().toString(), "expanded", artifactEntry.getKey());
                Files.walkFileTree(resourcesRootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String tempDest = Os.mergePaths(tempRoot, file.getFileName().toString());
                        String finalDest = Os.mergePaths(destRoot, file.getFileName().toString());
                        filesToCopy.put(file.toAbsolutePath().toString(), tempDest);
                        preInstallCommands.add(String.format("mv %s %s", tempDest, finalDest));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (CSARVersionNotFoundException e) {
                LOG.warn("CSAR " + artifactName + ":" + artifact.getArchiveVersion() + " does not exist", e);
            } catch (IOException e) {
                LOG.warn("Cannot parse CSAR resources", e);
            }
        }

        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_COMMAND, Joiner.on("\n").join(preInstallCommands));
    }
}
