package org.apache.brooklyn.tosca.a4c.transformer;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class BrooklynToscaPlanProcessingLiveTest extends AbstractPlanToSpecTransformerTest {

    String LOCATION = "aws-ec2:eu-west-1";

    @Test(groups={"Live"})
    public void testBrooklynToscaProcessing() {
        String plan = new ResourceUtils(this)
                .getResourceAsString(getClasspathUrlForTemplateResource(TOMCAT_TEMPLATE));

        EntitySpec<? extends Application> appSpec = transformer.createApplicationSpec(
                plan);

        appSpec.location(resolveLocationFromString(LOCATION));

        Application application=EntityManagementUtils.createUnstarted(mgmt, appSpec);
        EntityManagementUtils.start(application);
        TomcatServer tomcat = (TomcatServer) application.getChildren().toArray()[0];
        waitForApplicationTasks(mgmt, application);

        assertTrue(tomcat.getAttribute(TomcatServer.SERVICE_PROCESS_IS_RUNNING));
        assertNotNull(tomcat.getAttribute(Attributes.MAIN_URI));
    }

    @Test(groups={"Live"})
    public void testHwAppTopologyBrooklynToscaProcessing() {
        String plan = new ResourceUtils(this)
                .getResourceAsString(getClasspathUrlForTemplateResource(HW_COMPUTELOC_TEMPLATE));

        EntitySpec<? extends Application> appSpec = transformer.createApplicationSpec(plan);
        Application application=EntityManagementUtils.createUnstarted(mgmt, appSpec);
        EntityManagementUtils.start(application);

        final SameServerEntity hostEntity=(SameServerEntity)application.getChildren().toArray()[0];
        waitForApplicationTasks(mgmt, application);

        TomcatServer tomcat = (TomcatServer) hostEntity.getChildren().toArray()[0];

        assertNotNull(tomcat.getAttribute(Attributes.MAIN_URI));
    }

    @Test(groups={"Live"})
    public void testHwAppSameServerTopologyBrooklynProcessing() {
        String plan = new ResourceUtils(this)
                .getResourceAsString(getClasspathUrlForTemplateResource(HW_SS_TEMPLATE));

        EntitySpec<? extends Application> appSpec = transformer.createApplicationSpec(plan);
        Application application=EntityManagementUtils.createUnstarted(mgmt, appSpec);
        EntityManagementUtils.start(application);

        final SameServerEntity vanilla=(SameServerEntity)application.getChildren().toArray()[0];
        waitForApplicationTasks(mgmt, application);

        TomcatServer tomcat = (TomcatServer) vanilla.getChildren().toArray()[0];

        //doent't works
        assertNotNull(tomcat.getAttribute(Attributes.MAIN_URI));
    }

    protected void waitForApplicationTasks(ManagementContext managementContext, Entity app) {
        Set<Task<?>> tasks = BrooklynTaskTags
                .getTasksInEntityContext(managementContext.getExecutionManager(), app);
        for (Task<?> t : tasks) {
            t.blockUntilEnded();
        }
    }

    //TODO refactor this method was copied from BrooklynYamlLocationResolver
    public Location resolveLocationFromString(String location) {
        if (Strings.isBlank(location)) return null;
        return resolveLocation(location, MutableMap.of());
    }

    //TODO refactor this method was copied from BrooklynYamlLocationResolver
    protected Location resolveLocation(String spec, Map<?,?> flags) {
        LocationDefinition ldef = mgmt.getLocationRegistry().getDefinedLocationByName((String)spec);
        if (ldef!=null)
            // found it as a named location
            return mgmt.getLocationRegistry().resolve(ldef, null, flags).get();

        Maybe<Location> l = mgmt.getLocationRegistry().resolve(spec, null, flags);
        if (l.isPresent()) return l.get();

        RuntimeException exception = ((Maybe.Absent<?>)l).getException();
        throw new IllegalStateException("Illegal parameter for 'location' ("+spec+"); not resolvable: "+
                Exceptions.collapseText(exception), exception);
    }


}
