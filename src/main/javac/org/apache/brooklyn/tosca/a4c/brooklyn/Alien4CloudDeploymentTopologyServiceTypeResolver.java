package org.apache.brooklyn.tosca.a4c.brooklyn;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.BrooklynServiceTypeResolver;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Alien4CloudDeploymentTopologyServiceTypeResolver extends BrooklynServiceTypeResolver {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudDeploymentTopologyServiceTypeResolver.class);

    @Override
    public String getTypePrefix() { return "alien4cloud_deployment_topology"; }

    @Override
    public String getBrooklynType(String serviceType) {
        return BasicApplication.class.getName();
    }

    @Override
    public CatalogItem<Entity, EntitySpec<?>> getCatalogItem(BrooklynComponentTemplateResolver resolver, String serviceType) {
        // catalog not used
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T extends Entity> void decorateSpec(BrooklynComponentTemplateResolver resolver, EntitySpec<T> spec) {
        ToscaPlanToSpecTransformer transformer = new ToscaPlanToSpecTransformer();
        transformer.injectManagementContext(resolver.getManagementContext());
        String deploymentId = Strings.removeFromStart(resolver.getDeclaredType(), getTypePrefix()+":");
        
        // TODO discuss with andrew -- this interface should allow us to create the EntitySpec
        // instead all we can do is return the type and populate it
        //EntitySpec<? extends Application> spec = transformer.createApplicationSpecFromDeploymentTopologyId(deploymentId);
        
        transformer.populateApplicationSpecFromDeploymentTopologyId((EntitySpec)spec, deploymentId);
    }

}
