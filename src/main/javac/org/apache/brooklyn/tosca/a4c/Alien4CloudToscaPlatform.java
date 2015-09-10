package org.apache.brooklyn.tosca.a4c;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.common.base.Stopwatch;

import alien4cloud.csar.services.CsarService;
import alien4cloud.git.RepositoryManager;
import alien4cloud.model.components.Csar;
import alien4cloud.security.model.Role;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.utils.AlienYamlPropertiesFactoryBeanFactory;
import alien4cloud.utils.FileUtil;

public class Alien4CloudToscaPlatform implements Closeable {
    
    private static final Logger log = LoggerFactory.getLogger(Alien4CloudToscaPlatform.class);
    
    public static Alien4CloudToscaPlatform newInstance(String ...args) throws Exception {
        log.info("Loading Alien4Cloud platform...");
        // TODO if ES cannot find its config file, it will hang waiting for peers; should warn if does not complete in 1m
        try {
            Stopwatch s = Stopwatch.createStarted();
            configure();
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            // TODO messy, manually loading the properties, but it works
            ctx.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("user", 
                A4CSpringConfig.alienConfig(ctx)));
            ctx.register(A4CSpringConfig.class);
            ctx.refresh();

            log.info("Finished loading Alien4Cloud platform ("+Duration.of(s)+")");
            return new Alien4CloudToscaPlatform( ctx );
            
        } catch (Throwable t) {
            log.warn("Errors loading Alien4Cloud platform (rethrowing): "+t, t);
            throw Exceptions.propagate(t);
        }
    }

    public static void grantAdminAuth() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("brooklyn", "java", 
            MutableList.of(new SimpleGrantedAuthority(Role.ADMIN.name())) ));
    }

    public static void loadNormativeTypes(Alien4CloudToscaPlatform platform) throws Exception {
        
        // load normative types
        String localName = "tosca-normative-types";
        RepositoryManager repositoryManager = new RepositoryManager();

        // TODO use zip
        Path artifactsDirectory = Paths.get("../target/it-artifacts");
        Path zpb = artifactsDirectory.resolve(Paths.get("tosca-normative-types."+Identifiers.makeRandomId(6)));
        Path zpo = Paths.get(zpb.toString()+".orig.zip");
        Streams.copy(new ResourceUtils(Alien4CloudToscaPlatform.class).getResourceFromUrl("https://github.com/alien4cloud/tosca-normative-types/archive/master.zip"),
            new FileOutputStream(zpo.toString()) );
        Path zpe = Paths.get(zpb.toString()+"_expanded");
        FileUtil.unzip(zpo, zpe);
        String zipRootDir = Iterables.getOnlyElement(Arrays.asList(zpe.toFile().list()));
        Path zpc = Paths.get(zpb.toString()+".csar.zip");
        FileUtil.zip(zpe.resolve(zipRootDir), zpc);
        
//        repositoryManager.cloneOrCheckout(artifactsDirectory, "https://github.com/alien4cloud/tosca-normative-types.git", "master", localName);
//
//        Path normativeTypesPath = artifactsDirectory.resolve(localName);
//        Path normativeTypesZipPath = artifactsDirectory.resolve(localName + ".zip2");
//        // Update zip
//        FileUtil.zip(normativeTypesPath, normativeTypesZipPath);
        
////
////        // Path normativeTypesZipPath = Paths.get("../target/it-artifacts/zipped/apache-lb-types-0.1.csar");
////        ArchiveParser archiveParser = platform.getBean(ArchiveParser.class);
////        ParsingResult<ArchiveRoot> normative = archiveParser.parse(normativeTypesZipPath);
////        if (!normative.getContext().getParsingErrors().isEmpty()) {
////            System.out.println("ERRORS:\n  "+Strings.join(tp.getContext().getParsingErrors(), "\n  "));
////        }
////        platform.getCsarService().save(normative.getResult().getArchive());
////        // (save other items)
//        
////        String x = "/Users/alex/dev/gits/alien4cloud/alien4cloud/alien4cloud-core/src/test/resources/alien/paas/plan/csars/tosca-base-types-1.0";
        
        ParsingResult<Csar> normative = platform.getBean(ArchiveUploadService.class).upload(zpc);
        
        if (ArchiveUploadService.hasError(normative, ParsingErrorLevel.ERROR))
            throw new IllegalArgumentException("Errors parsing tosca normative types");
    }

    public static void configure() {
        // TODO desired? A4C code has this, but seems redundant.
//      if (Strings.isBlank(System.getProperty("spring.config.name"))) {
//          System.setProperty("security.basic.enabled", "false");
//          System.setProperty("spring.config.name", AlienYamlPropertiesFactoryBeanFactory.ALIEN_CONFIGURATION);
//      }
    }

    private ConfigurableApplicationContext ctx;   

    private Alien4CloudToscaPlatform(ConfigurableApplicationContext ctx) {
        this.ctx = ctx;
    }

    public synchronized void close() {
        if (ctx!=null) {
            log.info("Closing Alien4Cloud platform");
            ctx.close();
            ctx = null;
        }
    }
    
    public <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }

    public ToscaParser getToscaParser() {
        return getBean(ToscaParser.class);
    }
    public CsarService getCsarService() {
        return getBean(CsarService.class);
    }

    
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = { "alien4cloud", "org.elasticsearch.mapping" }, excludeFilters={ 
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.security.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.audit.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.ldap.*") })
    public static class A4CSpringConfig {
        // previously this returned the YamlPropertiesFactoryBean, but that causes warnings at startup
        @Bean(name={"alienconfig", "elasticsearchConfig"}) 
        public static Properties alienConfig(ResourceLoader resourceLoader) throws IOException {
            return AlienYamlPropertiesFactoryBeanFactory.get(resourceLoader).getObject();
        }
    }
    
}
