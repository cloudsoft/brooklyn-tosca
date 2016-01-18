package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.normative.NormativeComputeConstants;

@Component
public class DefaultEntitySpecFactory implements EntitySpecFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEntitySpecFactory.class);

    private final ManagementContext mgmt;
    private final ICSARRepositorySearchService repositorySearchService;

    @Inject
    public DefaultEntitySpecFactory(ManagementContext mgmt, ICSARRepositorySearchService repositorySearchService) {
        this.mgmt = mgmt;
        this.repositorySearchService = repositorySearchService;
    }

    @Override
    public EntitySpec<?> create(NodeTemplate nodeTemplate, Topology topology, boolean hasMultipleChildren) {
        // TODO: Would like to get rid of hasMultipleChildren argument.
        // TODO: decide on how to behave if indexedNodeTemplate.getElementId is abstract.
        // Currently we create a VanillaSoftwareProcess.

        EntitySpec<?> spec;

        CatalogItem catalogItem = CatalogUtils.getCatalogItemOptionalVersion(mgmt, nodeTemplate.getType());
        if (catalogItem != null) {
            LOG.info("Found Brooklyn catalog item that match node type: " + nodeTemplate.getType());
            spec = (EntitySpec<?>) mgmt.getCatalog().createSpec(catalogItem);

        } else if (isComputeType(nodeTemplate, topology)) {
            spec = hasMultipleChildren
                   ? EntitySpec.create(SameServerEntity.class)
                   : EntitySpec.create(BasicApplication.class);

        } else {
            try {
                LOG.info("Found Brooklyn entity that match node type: " + nodeTemplate.getType());
                spec = EntitySpec.create((Class<? extends Entity>) Class.forName(nodeTemplate.getType()));

            } catch (ClassNotFoundException e) {
                LOG.info("Cannot find any Brooklyn catalog item nor Brooklyn entities that match node type: " +
                        nodeTemplate.getType() + ". Defaulting to a VanillaSoftwareProcess");
                spec = EntitySpec.create(VanillaSoftwareProcess.class);
            }
        }

        return spec;
    }

    private boolean isComputeType(NodeTemplate nodeTemplate, Topology topology) {
        return getIndexedNodeTemplate(nodeTemplate, topology)
                .getDerivedFrom()
                .contains(NormativeComputeConstants.COMPUTE_TYPE);
    }

    protected IndexedArtifactToscaElement getIndexedNodeTemplate(NodeTemplate nodeTemplate, Topology topology) {
        return repositorySearchService.getRequiredElementInDependencies(
                IndexedArtifactToscaElement.class,
                nodeTemplate.getType(),
                topology.getDependencies());
    }

}
