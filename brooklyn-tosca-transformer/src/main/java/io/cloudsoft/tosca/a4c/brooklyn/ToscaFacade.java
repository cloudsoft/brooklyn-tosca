package io.cloudsoft.tosca.a4c.brooklyn;

import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.config.ConfigKey;

import com.google.common.base.Optional;

/**
 * A facade to the tosca model
 * @param <A> The type of ToscaApplication used
 */
public interface ToscaFacade<A extends ToscaApplication> {

    Map<String, Object> getPropertiesAndTypeValuesByRelationshipId(String nodeId, A toscaApplication, String relationshipId, String computeName);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @param artifactId
     * @return the Path to the artifact with the given id
     */
    Optional<Path> getArtifactPath(String nodeId, A toscaApplication, String artifactId);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @return the artifact ids for the given node
     */
    Iterable<String> getArtifacts(String nodeId, A toscaApplication);

    /**
     * @param archiveName the name of the archive
     * @param archiveVersion the version of the archive
     * @return the Path of the CSAR archive
     */
    Optional<Path> getCsarPath(String archiveName, String archiveVersion);

    /**
     * @param opKey the key of the operation
     * @return The Brooklyn ConfigKey corresponding to the lifecycle operation
     */
    ConfigKey<String> getLifeCycle(String opKey);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @return The id of the parent node
     */
    String getParentId(String nodeId, A toscaApplication);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @return The Map of resolved attributes
     */
    Map<String, Object> getResolvedAttributes(String nodeId, A toscaApplication);

    /**
     * @param opKey the key name of the operation
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @param computeName the name of the compute node
     * @param expandedFolder the name of the expanded folder
     * @return The script associated with the operation.  This optional script may be in the form
     * of a String if all input values have been resolved, otherwise a  {@link
     * BrooklynDslDeferredSupplier}
     */
    Optional<Object> getScript(String opKey, String nodeId, A toscaApplication, String computeName, String expandedFolder, @Nullable ManagementContext mgmt);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @return The interface operations
     */
    Iterable<String> getInterfaceOperations(String nodeId, A toscaApplication);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @param computeName the name of the compute node
     * @return The map of resolved property objects
     */
    Map<String, Object> getTemplatePropertyObjects(String nodeId, A toscaApplication, String computeName);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @param type the type to test
     * @return whether the specified node is derived from the given type
     */
    boolean isDerivedFrom(String nodeId, A toscaApplication, String type);

    /**
     * @param id the id of the application
     * @return a new ToscaApplication
     */
    A newToscaApplication(String id);

    /**
     * @param plan the Tosca plan to parse
     * @param uploader the Uploader object to use
     * @param context 
     * @return a new ToscaApplication
     */
    A parsePlan(String plan, Uploader uploader, BrooklynClassLoadingContext context);

    /**
     *
     * @param path the path to a CSAR archive
     * @param uploader the Uploader object to use
     * @param context 
     * @return a new ToscaApplication
     */
    A parsePlan(Path path, Uploader uploader);

    Iterable<String> getInterfaceOperationsByRelationship(A toscaApplication, ToscaApplication.Relationship relationship);

    /**
     * @param nodeId the node id
     * @param toscaApplication the tosca application
     * @param key the key name of the property to be resolved
     * @return the resolved property specified by key
     */
    Object resolveProperty(String nodeId, A toscaApplication, String key);

    /**
     * @param expandedFolder the name of the expanded CSAR folder
     * @return the script to be run as part of the relationship is present, Optional.absent() otherwise
     */
    Optional<Object> getRelationshipScript(String opKey, A toscaApplication, ToscaApplication.Relationship relationship, String computeName, String expandedFolder, @Nullable ManagementContext mgmt);

}
