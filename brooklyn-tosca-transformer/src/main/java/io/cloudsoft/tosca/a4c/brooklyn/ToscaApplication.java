package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;

import com.google.common.base.Optional;

/**
 * A representation of a Tosca Application used
 */
public interface ToscaApplication {

    /**
     * @return The name of the Application
     */
    String getName();

    /**
     * @return The Deployment ID
     */
    String getDeploymentId();

    /**
     * @return The Application ID
     */
    String getId();

    /**
     * @return The Delegate ID
     */
    String getDelegateId();

    /**
     * @param id the id of the node
     * @return the node name for the given id
     */
    Optional<String> getNodeName(String id);

    /**
     * The keyword map is used to translate function keywords into node names.
     *
     * @param id the id of the node
     * @return the keyword map
     */
    Map<String, String> getKeywordMap(String id);

    /**
     * @param nodeId the id of the node
     * @return the requirement ids for the given node id
     */
    Iterable<String> getRequirements(String nodeId);

    /**
     * @return the node ids
     */
    Iterable<String> getNodeIds();

    /**
     * @param nodeId the id of the node
     * @param newName the new name to give the node
     */
    void setNodeName(String nodeId, String newName);

    /**
     * @param nodeId the id of the node
     * @return the node type for the given id
     */
    String getNodeType(String nodeId);

    /**
     * @return the node group ids
     */
    Iterable<String> getNodeGroups();

    /**
     * Uses the given toscaPolicyDecorator to decorate an EntitySpec with location policies
     * @param groupId the id of the group
     * @param toscaPolicyDecorator the ToscaPolicyDecorator to use
     */
    void addLocationPolicies(String groupId, ToscaPolicyDecorator toscaPolicyDecorator);

    /**
     * Uses the given brooklynPolicyDecorator to decorate an EntitySpec with brooklyn policies
     * @param groupId the id of the group
     * @param brooklynPolicyDecorator the BrooklynToscaPolicyDecorator to use
     * @param mgmt the Brooklyn ManagementContext
     */
    void addBrooklynPolicies(String groupId, BrooklynToscaPolicyDecorator brooklynPolicyDecorator, ManagementContext mgmt);

    Iterable<String> getCapabilityTypes(String nodeId);
}
