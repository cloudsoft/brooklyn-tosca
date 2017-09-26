package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;

import io.cloudsoft.tosca.a4c.brooklyn.Alien4CloudApplication;
import io.cloudsoft.tosca.a4c.brooklyn.Alien4CloudFacade;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

@Component
public class Alien4CloudEntitySpecFactory implements EntitySpecFactory<Alien4CloudApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudEntitySpecFactory.class);

    private final ManagementContext mgmt;
    private ToscaFacade<Alien4CloudApplication> alien4CloudFacade;

    @Inject
    public Alien4CloudEntitySpecFactory(ManagementContext mgmt, ToscaFacade<Alien4CloudApplication> alien4CloudFacade) {
        this.mgmt = mgmt;
        this.alien4CloudFacade = alien4CloudFacade;
    }

    @Override
    public EntitySpec<?> create(String nodeId, Alien4CloudApplication toscaApplication) {
        // TODO: decide on how to behave if indexedNodeTemplate.getElementId is abstract.
        // Currently we create a VanillaSoftwareProcess.

        EntitySpec<?> spec;
        String type = toscaApplication.getNodeTemplate(nodeId).getType();

        RegisteredType registeredType = mgmt.getTypeRegistry().get(type);
        if(registeredType != null) {
            spec = mgmt.getTypeRegistry().create(registeredType, null, null);
        } else if (isComputeType(nodeId, toscaApplication)) {
            spec = EntitySpec.create(SameServerEntity.class);
        } else {
            try {
                LOG.info("Found Brooklyn entity that match node type: " + type);
                spec = EntitySpec.create((Class<? extends Entity>) Class.forName(type));

            } catch (ClassNotFoundException e) {
                LOG.info("Cannot find any Brooklyn catalog item nor Brooklyn entities that match node type: " +
                        type + ". Defaulting to a VanillaSoftwareProcess");
                spec = EntitySpec.create(VanillaSoftwareProcess.class);
            }
        }

        return spec;
    }

    private boolean isComputeType(String nodeId, Alien4CloudApplication toscaApplication) {
        return alien4CloudFacade.isDerivedFrom(nodeId, toscaApplication, Alien4CloudFacade.COMPUTE_TYPE);
    }
}
