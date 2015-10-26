package org.apache.brooklyn.tosca.a4c;

import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;

public class Alien4CloudToscaTest extends BrooklynAppUnitTestSupport {

    public String getClasspathUrlForResource(String resourceName){
        return "classpath://"+ resourceName;
    }

}
