package alien4cloud.brooklyn;

import alien4cloud.plugin.model.ManagedPlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Configuration
public class TestConfiguration {

    @Bean
    public ManagedPlugin managedPlugin() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<ManagedPlugin> constructor = ManagedPlugin.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance("");
    }

}
