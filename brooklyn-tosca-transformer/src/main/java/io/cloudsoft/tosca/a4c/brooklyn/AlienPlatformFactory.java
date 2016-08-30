package io.cloudsoft.tosca.a4c.brooklyn;

import io.cloudsoft.tosca.a4c.platform.Alien4CloudSpringContext;
import io.cloudsoft.tosca.a4c.platform.ToscaPlatform;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.springframework.context.ApplicationContext;

public interface AlienPlatformFactory {

    ToscaPlatform newPlatform(ManagementContext mgmt) throws Exception;
    
    public static class Default implements AlienPlatformFactory {
        @Override
        public ToscaPlatform newPlatform(ManagementContext mgmt) throws Exception {
            ApplicationContext applicationContext = Alien4CloudSpringContext.newApplicationContext(mgmt);
            return applicationContext.getBean(ToscaPlatform.class);
        }
    }
    
}
