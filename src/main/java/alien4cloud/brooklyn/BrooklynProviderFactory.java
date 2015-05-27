package alien4cloud.brooklyn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;

/**
 * Created by lucboutier on 26/05/15.
 */
public class BrooklynProviderFactory implements IConfigurablePaaSProviderFactory<Void> {

    private static final Logger log = LoggerFactory.getLogger(BrooklynProviderFactory.class);
    
    @Override
    public IConfigurablePaaSProvider<Void> newInstance() {
        BrooklynProvider result = new BrooklynProvider();
        log.info("NEW INSTANCE: "+result);
        return result;
    }

    @Override
    public void destroy(IConfigurablePaaSProvider<Void> instance) {
        log.info("DESTROYING (noop): "+instance);
    }

    @Override
    public Class<Void> getConfigurationType() {
        return Void.class;
    }

    @Override
    public Void getDefaultConfiguration() {
        return null;
    }
}
