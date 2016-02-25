package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

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
        final Iterable<String> artifacts = getToscaFacade().getArtifacts(nodeId, toscaApplication);
        if (Iterables.isEmpty(artifacts)) {
            return;
        }

        final Map<String, String> filesToCopy = MutableMap.of();

        for (final String artifactId : artifacts) {
            Optional<Path> optionalResourcesRootPath = getToscaFacade().getArtifactPath(nodeId, toscaApplication, artifactId);
            if(!optionalResourcesRootPath.isPresent()) {
                continue;
            }

            // We know that formatString will return a BrooklynDslDeferredSupplier as the 2nd argument is not yet resolved
            BrooklynDslDeferredSupplier deferredRunDir = (BrooklynDslDeferredSupplier) BrooklynDslCommon.formatString("%s/%s", BrooklynDslCommon.attributeWhenReady("install.dir"), artifactId);
            entitySpec.configure(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactId), deferredRunDir);

            // Copy all files in resource.
            try {
                Files.walkFileTree(optionalResourcesRootPath.get(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        filesToCopy.put(file.toAbsolutePath().toString(), artifactId + "/" + file.getFileName().toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }  catch (IOException e) {
                LOG.warn("Cannot parse CSAR resources", e);
            }
        }

        // It's not possible to infer from the artifact at what point these files will be used, so
        // they need to be copied at the earliers point possible
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
    }
}
