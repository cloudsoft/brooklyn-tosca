package alien4cloud.brooklyn;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;
import alien4cloud.tosca.ArchiveIndexer;

/**
 * Created by lucboutier on 26/05/15.
 */
@Slf4j
@Component("brooklyn-provider-factory")
public class BrooklynProviderFactory implements IConfigurablePaaSProviderFactory<Configuration> {
    @Autowired
    private BeanFactory beanFactory;
    @Autowired
    private BrooklynCatalogMapper catalogMapper;
    @Resource
    private ArchiveIndexer archiveIndexer;

    @PostConstruct
    public void ready() {
        log.info("Created brooklyn provider and beanFactory is ", beanFactory, catalogMapper, archiveIndexer);
    }

    @Override
    public IConfigurablePaaSProvider<Configuration> newInstance() {
        BrooklynProvider instance = beanFactory.getBean(BrooklynProvider.class);
        log.info("NEW INSTANCE!", instance);
        log.info("Init brooklyn provider and beanFactory is ", beanFactory, catalogMapper, archiveIndexer);
        return instance;
    }

    @Override
    public void destroy(IConfigurablePaaSProvider<Configuration> instance) {
        log.info("DESTROYING (noop): " + instance);
    }

    @Override
    public Class<Configuration> getConfigurationType() {
        return Configuration.class;
    }

    @Override
    public Configuration getDefaultConfiguration() {
        return null;
    }
}
