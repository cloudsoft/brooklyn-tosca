package org.apache.brooklyn.tosca.a4c;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.file.ArchiveBuilder;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.commons.io.FileUtils;
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
    
    public static final String TOSCA_NORMATIVE_TYPES_LOCAL_URL = "classpath://org/apache/brooklyn/tosca/a4c/tosca-normative-types.zip";
    public static final String TOSCA_NORMATIVE_TYPES_GITHUB_URL = "https://github.com/alien4cloud/tosca-normative-types/archive/master.zip";

    public static Alien4CloudToscaPlatform newInstance(String ...args) throws Exception {
        log.info("Loading Alien4Cloud platform...");
        // TODO support pre-existing ES instance
        // TODO if ES cannot find its config file, it will hang waiting for peers; should warn if does not complete in 1m
        try {
            Stopwatch s = Stopwatch.createStarted();
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            // messy, manually loading the properties, but it works
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

    public void loadNormativeTypes() throws Exception {
        Path zpc;
        if (new ResourceUtils(this).doesUrlExist(TOSCA_NORMATIVE_TYPES_LOCAL_URL)) {
            zpc = Paths.get(tmpRoot.toString(), "tosca-normative-types_"+Identifiers.makeRandomId(6)+".tgz");
            Streams.copy(new ResourceUtils(this).getResourceFromUrl(TOSCA_NORMATIVE_TYPES_LOCAL_URL),
                new FileOutputStream(zpc.toString()));

        } else {
            log.debug("Loading TOSCA normative types from GitHub");
            // mainly kept for reference, in case we want to load other items from GitHub
            Path artifactsDirectory = Paths.get(tmpRoot.toString(), "tosca-normative-types");
            Path zpb = artifactsDirectory.resolve(Paths.get("tosca-normative-types."+Identifiers.makeRandomId(6)));
            Path zpo = Paths.get(zpb.toString()+".orig.zip");
            Streams.copy(new ResourceUtils(Alien4CloudToscaPlatform.class).getResourceFromUrl(TOSCA_NORMATIVE_TYPES_GITHUB_URL),
                new FileOutputStream(zpo.toString()) );
            Path zpe = Paths.get(zpb.toString()+"_expanded");
            FileUtil.unzip(zpo, zpe);
            String zipRootDir = Iterables.getOnlyElement(Arrays.asList(zpe.toFile().list()));
            zpc = Paths.get(zpb.toString()+".csar.zip");
            FileUtil.zip(zpe.resolve(zipRootDir), zpc);
        }
        
        ParsingResult<Csar> normative = getBean(ArchiveUploadService.class).upload(zpc);

        if (ArchiveUploadService.hasError(normative, ParsingErrorLevel.ERROR))
            throw new IllegalArgumentException("Errors parsing tosca normative types:\n"+Strings.join(normative.getContext().getParsingErrors(), "\n  "));
    }

    private ConfigurableApplicationContext ctx;   
    File tmpRoot;
    
    private Alien4CloudToscaPlatform(ConfigurableApplicationContext ctx) {
        this.ctx = ctx;
        tmpRoot = Os.newTempDir("brooklyn-a4c");
        Os.deleteOnExitRecursively(tmpRoot);
    }

    public synchronized void close() {
        if (ctx!=null) {
            log.info("Closing Alien4Cloud platform");
            ctx.close();
            ctx = null;
        }
        Os.deleteRecursively(tmpRoot);
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
    public ArchiveUploadService getArchiveUploadService() {
        return getBean(ArchiveUploadService.class);
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

    public ParsingResult<Csar> uploadSingleYaml(InputStream resourceFromUrl, String nameO) {
        try {
            String name = Strings.makeValidFilename(nameO);
            File tmpBase = new File(tmpRoot, name+"_"+Identifiers.makeRandomId(6));
            File tmpExpanded = new File(tmpBase, name+"_"+Identifiers.makeRandomId(6));
            File tmpTarget = new File(tmpExpanded.toString()+".csar.zip");
            tmpExpanded.mkdir();

            FileUtils.copyInputStreamToFile(resourceFromUrl, new File(tmpExpanded, name));
            ArchiveBuilder.archive(tmpTarget.toString()).addDirContentsAt(tmpExpanded, "").create();

            ParsingResult<Csar> result = getArchiveUploadService().upload(Paths.get(tmpTarget.toString()));
            
            Os.deleteRecursively(tmpBase);
            
            if (ArchiveUploadService.hasError(result, null)) {
                log.debug("A4C parse notes for "+nameO+":\n  "+Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }
            if (ArchiveUploadService.hasError(result, ParsingErrorLevel.ERROR)) {
                throw new IllegalArgumentException("Errors parsing "+name+"; archive will not be installed:\n  "
                    +Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }
            
            return result;

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
