package io.cloudsoft.tosca.a4c.brooklyn.osgi;

import io.cloudsoft.tosca.a4c.brooklyn.AlienPlatformFactory;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudSpringContext;
import io.cloudsoft.tosca.a4c.platform.ToscaPlatform;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.elasticsearch.util.AnnotationScanner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ResourceLoader;

import alien4cloud.utils.TypeScanner;

public class AlienPlatformFactoryOsgi implements AlienPlatformFactory {
    
    @Override
    public ToscaPlatform newPlatform(ManagementContext mgmt) throws Exception {
        ResourceLoader rl = new OsgiAwarePathMatchingResourcePatternResolver();
        TypeScanner.setResourceLoader(rl);
        AnnotationScanner.setResourceLoader(rl);
        // TODO only do the above once, cache static
        
        ClassLoader oldCL = null;
        oldCL = Thread.currentThread().getContextClassLoader(); 
        Thread.currentThread().setContextClassLoader(rl.getClass().getClassLoader());
        
        ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(mgmt, rl);
        ToscaPlatform result = applicationContext.getBean(ToscaPlatform.class);
        
        Thread.currentThread().setContextClassLoader(oldCL);

        return result;
    }

}
