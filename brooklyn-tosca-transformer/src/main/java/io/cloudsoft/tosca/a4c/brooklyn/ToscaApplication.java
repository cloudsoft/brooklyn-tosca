package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import com.google.common.base.Objects;
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

    Iterable<Relationship> getAllRelationships(String nodeId);

    class Relationship {
        private final String sourceNodeId;
        private final String targetNodeId;
        private final String relationshipId;
        private final String relationshipType;

        public Relationship(String sourceNodeId, String targetNodeId, String relationshipId, String relationshipType) {
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
            this.relationshipId = relationshipId;
            this.relationshipType = relationshipType;
        }

        public String getRelationshipId() {
            return relationshipId;
        }

        public String getTargetNodeId() {
            return targetNodeId;
        }

        public String getSourceNodeId() {
            return sourceNodeId;
        }

        public String getRelationshipType() {
            return relationshipType;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(sourceNodeId, targetNodeId, relationshipId, relationshipType);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Relationship that = Relationship.class.cast(obj);
            return Objects.equal(this.sourceNodeId, that.sourceNodeId) &&
                    Objects.equal(this.targetNodeId, that.targetNodeId) &&
                    Objects.equal(this.relationshipId, that.relationshipId) &&
                    Objects.equal(this.relationshipType, that.relationshipType);
        }
    }

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

}
