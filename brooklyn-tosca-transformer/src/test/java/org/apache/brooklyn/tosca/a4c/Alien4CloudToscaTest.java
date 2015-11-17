package org.apache.brooklyn.tosca.a4c;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.util.guava.SerializablePredicate;
import org.testng.annotations.BeforeMethod;

import javax.annotation.Nullable;

public class Alien4CloudToscaTest extends BrooklynAppUnitTestSupport {

    protected CampPlatform campPlatform;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        campPlatform = new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(mgmt)
                .launch()
                .getCampPlatform();
    }

    public String getClasspathUrlForResource(String resourceName){
        return "classpath://"+ resourceName;
    }

    public static EntitySpec<?> findChildEntitySpecByPlanId(EntitySpec<? extends Application> app, String planId){

        Optional<EntitySpec<?>> result = Iterables.tryFind(app.getChildren(),
                configSatisfies(BrooklynCampConstants.PLAN_ID, planId));

        if (result.isPresent()) {
            return result.get();
        }
        //TODO: better NoSuchElementException? return null?
        throw new IllegalStateException("Entity with planId  " + planId + " is not contained on" +
                " ApplicationSpec "+ app +" children");
    }

    public static <T> Predicate<EntitySpec> configSatisfies(final ConfigKey<T> configKey, final T val) {
        return new ConfigKeySatisfies<T>(configKey, Predicates.equalTo(val));
    }

    protected static class ConfigKeySatisfies<T> implements SerializablePredicate<EntitySpec> {
        protected final ConfigKey<T> configKey;
        protected final Predicate<T> condition;

        private ConfigKeySatisfies(ConfigKey<T> configKey, Predicate<T> condition) {
            this.configKey = configKey;
            this.condition = condition;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean apply(@Nullable EntitySpec input) {
            return (input != null) && condition.apply((T)input.getConfig().get(configKey));
        }

        @Override
        public String toString() {
            return "configKeySatisfies("+configKey.getName()+","+condition+")";
        }
    }

}
