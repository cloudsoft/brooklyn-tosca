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
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

import alien4cloud.model.components.DeploymentArtifact;

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
        // As PRE_INSTALL_FILES is explicitly Map<String, String>, we can't use a deferred "run.dir" in the
        // destination path, so we must use the install.dir, which is shared by all instances of the same
        // type (with the exception of VanillaSoftwareProcess). In order to prevent collision between
        // e.g. multiple instances of MySql on the same server, we must use an instance-specific prefix
        final String directoryPrefix = Identifiers.makeRandomJavaId(6);

        for (final String artifactId : artifacts) {
            String artifactRef = getToscaFacade().getArtifactRef(nodeId, toscaApplication, artifactId);
            if (artifactRef==null) {
                LOG.warn("No reference/implementation for artifact "+artifactId+" in "+nodeId+" / "+entitySpec+"; skipping");
                continue;
            }
            
            @SuppressWarnings("unchecked")
            BrooklynDslDeferredSupplier<String> deferredInstallDir = (BrooklynDslDeferredSupplier<String>) BrooklynDslCommon.formatString("%s/%s/%s", BrooklynDslCommon.attributeWhenReady("install.dir"), directoryPrefix, artifactId);
            entitySpec.configure(SoftwareProcess.SHELL_ENVIRONMENT.subKey(artifactId), deferredInstallDir);
            
            if (Urls.isUrlWithProtocol(artifactRef)) {
                // brooklyn convention, use classpath:// URLs which resolve against the bundles
                filesToCopy.put(artifactRef, String.format("%s/%s", directoryPrefix, artifactId));
                
            } else {
                // tosca convention, take from the CSAR
                Optional<Path> optionalResourcesRootPath = getToscaFacade().getArtifactPath(nodeId, toscaApplication, artifactId);
                if (!optionalResourcesRootPath.isPresent()) {
                    throw new IllegalStateException("Cannot find artifact "+artifactId+" ("+artifactRef+"); "
                        + "you can use paths (TOSCA) syntax, in which case the artifact must be "
                        + "in the CSAR or one of its explicit imported dependencies; or you can a URL (Brooklyn) including classpath:// but that will look only in Brooklyn bundles that are used by the Brooklyn blueprint (it will not look in TOSCA CSARs unless they are also listed as libraries for a Brooklyn blueprint)");
                }
                filesToCopy.put(optionalResourcesRootPath.get().toFile().getAbsolutePath(), String.format("%s/%s", directoryPrefix, artifactId));
            }
        }

        // It's not possible to infer from the artifact at what point these files will be used, so
        // they need to be copied at the earliest point possible
        entitySpec.configure(SoftwareProcess.PRE_INSTALL_FILES, filesToCopy);
    }
}
