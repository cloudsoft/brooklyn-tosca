package io.cloudsoft.tosca.a4c;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import alien4cloud.security.ResourceRoleService;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.file.ArchiveBuilder;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
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
import alien4cloud.model.templates.TopologyTemplate;
import alien4cloud.model.templates.TopologyTemplateVersion;
import alien4cloud.model.topology.Topology;
import alien4cloud.security.model.Role;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.topology.TopologyTemplateVersionService;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.utils.FileUtil;

public class Alien4CloudToscaPlatform implements Closeable {
    
    private static final Logger log = LoggerFactory.getLogger(Alien4CloudToscaPlatform.class);
    
    public static final String TOSCA_NORMATIVE_TYPES_LOCAL_URL = "classpath://org/apache/brooklyn/tosca/a4c/tosca-normative-types.zip";
    public static final String TOSCA_NORMATIVE_TYPES_GITHUB_URL = "https://github.com/alien4cloud/tosca-normative-types/archive/master.zip";

    public static Alien4CloudToscaPlatform newInstance(String ...args) throws Exception {
        return newInstance(new LocalManagementContext(), args);
    }
    
    public static Alien4CloudToscaPlatform newInstance(ManagementContext mgmt, String ...args) throws Exception {
        log.info("Loading Alien4Cloud platform...");
        // TODO if ES cannot find a config file, it will hang waiting for peers; should warn if does not complete in 1m
        try {
            Stopwatch s = Stopwatch.createStarted();

            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            
            // messy, but seems we must manually load the properties before loading the beans; otherwise we get e.g.
            // Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'directories.alien' in string value "${directories.alien}/plugins"
            ctx.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("user", 
                AlienBrooklynYamlPropertiesFactoryBeanFactory.get(mgmt, ctx).getObject()));
            ctx.getBeanFactory().registerSingleton("brooklynManagementContext", mgmt);
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

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = { "alien4cloud", "org.elasticsearch.mapping" }, excludeFilters={
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.security.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.audit.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="alien4cloud.ldap.*") })
    public static class A4CSpringConfig {

        @Bean
        public ResourceRoleService getDummyRRS() {
            return new ResourceRoleService();
        }

        // A4C code returns the YamlPropertiesFactoryBean, but that causes warnings at startup
        @Bean(name={"alienconfig", "elasticsearchConfig"})
        public static Properties alienConfig(BeanFactory beans, ResourceLoader resourceLoader) throws IOException {
            ManagementContext mgmt = null;
            if (beans.containsBean("brooklynManagementContext"))
                mgmt = beans.getBean("brooklynManagementContext", ManagementContext.class);
            return AlienBrooklynYamlPropertiesFactoryBeanFactory.get(mgmt, resourceLoader).getObject();
        }

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
            Files.createDirectories(zpo.getParent());
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
            throw new UserFacingException("Errors parsing tosca normative types:\n"+Strings.join(normative.getContext().getParsingErrors(), "\n  "));
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

    public ParsingResult<Csar> uploadSingleYaml(InputStream resourceFromUrl, String callerReferenceName) {
        try {
            String nameCleaned = Strings.makeValidFilename(callerReferenceName);
            File tmpBase = new File(tmpRoot, nameCleaned+"_"+Identifiers.makeRandomId(6));
            File tmpExpanded = new File(tmpBase, nameCleaned+"_"+Identifiers.makeRandomId(6));
            File tmpTarget = new File(tmpExpanded.toString()+".csar.zip");
            boolean created = tmpExpanded.mkdirs();
            if (!created) {
                throw new Exception("Failed to create '" + tmpExpanded + "' when uploading yaml from " + nameCleaned);
            }
            FileUtils.copyInputStreamToFile(resourceFromUrl, new File(tmpExpanded, nameCleaned+".yaml"));
            ArchiveBuilder.archive(tmpTarget.toString()).addDirContentsAt(tmpExpanded, "").create();

            try {
                return uploadArchive(tmpTarget, callerReferenceName);
            } finally {
                Os.deleteRecursively(tmpBase);
            }

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public ParsingResult<Csar> uploadArchive(InputStream resourceFromUrl, String callerReferenceName) {
        try {
            File f = new File(tmpRoot, callerReferenceName+"_"+Identifiers.makeRandomId(8));
            Streams.copy(resourceFromUrl, new FileOutputStream(f));
            return uploadArchive(f, callerReferenceName);
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public ParsingResult<Csar> uploadArchive(File zipFile, String callerReferenceName) {
        try {
            String nameCleaned = Strings.makeValidFilename(callerReferenceName);
            
            ParsingResult<Csar> result = getArchiveUploadService().upload(Paths.get(zipFile.toString()));

            if (ArchiveUploadService.hasError(result, null)) {
                log.debug("A4C parse notes for "+nameCleaned+":\n  "+Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }
            if (ArchiveUploadService.hasError(result, ParsingErrorLevel.ERROR)) {
                // archive will not be installed in this case, so we should throw
                throw new UserFacingException("Could not parse "+callerReferenceName+" as TOSCA:\n  "
                    +Strings.join(result.getContext().getParsingErrors(), "\n  "));
            }

            return result;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public Topology getTopologyOfCsar(Csar cs) {
        TopologyTemplate tt = getBean(TopologyServiceCore.class).searchTopologyTemplateByName(cs.getName());
        if (tt==null) return null;
        TopologyTemplateVersion[] ttv = getBean(TopologyTemplateVersionService.class).getByDelegateId(tt.getId());
        if (ttv==null || ttv.length==0) return null;
        return getBean(TopologyServiceCore.class).getTopology( ttv[0].getTopologyId() );
    }
    
}
