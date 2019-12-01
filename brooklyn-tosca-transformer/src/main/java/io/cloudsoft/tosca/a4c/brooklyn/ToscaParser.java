package io.cloudsoft.tosca.a4c.brooklyn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.typereg.ManagedBundle;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContextSequential;
import org.apache.brooklyn.core.mgmt.classloading.OsgiBrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.ha.OsgiManager;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.typereg.UnsupportedTypePlanException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alien4cloud.model.components.Csar;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;

public class ToscaParser {

    private static final Logger log = LoggerFactory.getLogger(ToscaParser.class);

    private Uploader uploader;

    private static class PlanTypeChecker {

        Object obj;
        boolean isTosca = false;
        String csarLink;

        public PlanTypeChecker(String plan) {
            try {
                obj = Yamls.parseAll(plan).iterator().next();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.trace("Not YAML", e);
                return;
            }
            if (!(obj instanceof Map)) {
                log.trace("Not a map");
                // is it a one-line URL?
                plan = plan.trim();
                if (!plan.contains("\n") && Urls.isUrlWithProtocol(plan)) {
                    csarLink = plan;
                }
                return;
            }

            if (isTosca((Map<?,?>)obj)) {
                isTosca = true;
                return;
            }

            if (((Map<?,?>)obj).size()==1) {
                csarLink = (String) ((Map<?,?>)obj).get("csar_link");
                return;
            }
        }

        private static boolean isTosca(Map<?,?> obj) {
            if (obj.containsKey("topology_template")) return true;
            if (obj.containsKey("topology_name")) return true;
            if (obj.containsKey("node_types")) return true;
            if (obj.containsKey("tosca_definitions_version")) return true;
            log.trace("Not TOSCA - no recognized keys");
            return false;
        }
    }

    public ToscaParser(Uploader uploader) {
        this.uploader = uploader;
    }

    public ParsingResult<Csar> parse(String plan, BrooklynClassLoadingContext context) {
        ParsingResult<Csar> tp;
        PlanTypeChecker type = new PlanTypeChecker(plan);
        if (!type.isTosca) {
            if (type.csarLink == null) {
                throw new UnsupportedTypePlanException("Does not look like TOSCA");
            }
            ResourceUtils resLoader = context!=null ? new ResourceUtils(context) : new ResourceUtils(this); 
            InputStream resourceFromUrl;
            if (".".equals(type.csarLink)) {
                try {
                    resourceFromUrl = getContainingBundleInputStream(context);
                } catch (Exception e) {
                    throw Exceptions.propagateAnnotated("Could not load same-bundle csar_link relative to context "+context, e);
                }
            } else {
                try {
                    resourceFromUrl = resLoader.getResourceFromUrl(type.csarLink);
                } catch (Exception e) {
                    if (type.csarLink.startsWith("classpath:")) {
                        throw Exceptions.propagateAnnotated("Could not load csar_link "+type.csarLink+" relative to context "+context, e);
                    } else {
                        throw Exceptions.propagate(e);
                    }
                }
            }
            // TODO either we need to be able to look up the CSAR ZIP after upload, or
            // we have to annotate it with context and csarLink of this file
            tp = uploader.uploadArchive(resourceFromUrl, "submitted-tosca-archive");

        } else {
            tp = uploader.uploadSingleYaml(Streams.newInputStreamWithContents(plan), "submitted-tosca-plan");
        }

        if (ArchiveUploadService.hasError(tp, ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Could not parse TOSCA plan: "
                    + Strings.join(tp.getContext().getParsingErrors(), "\n  "));
        }

        return tp;
    }

    protected InputStream getContainingBundleInputStream(BrooklynClassLoadingContext context) {
        // assume the containing bundle is the first item in the context
        if (context==null) {
            throw new IllegalStateException("No class-loading context");
        }
        if (!(context instanceof BrooklynClassLoadingContextSequential)) {
            throw new IllegalStateException("Expected "+BrooklynClassLoadingContextSequential.class+" but had "+context.getClass());
        }
        BrooklynClassLoadingContextSequential seqCtx = (BrooklynClassLoadingContextSequential)context;
        if (seqCtx.getPrimaries().isEmpty()) {
            throw new IllegalStateException("No primaries set in context");
        }
        OsgiBrooklynClassLoadingContext osgiCtx = (OsgiBrooklynClassLoadingContext) seqCtx.getPrimaries().iterator().next();
        Collection<? extends OsgiBundleWithUrl> bundles = osgiCtx.getBundles();
        if (bundles.isEmpty()) {
            throw new IllegalStateException("No bundles in first primary loading context "+osgiCtx);
        }        
        OsgiBundleWithUrl catalogBundle = bundles.iterator().next();
        String url = catalogBundle.getUrl();
        if (url!=null) {
            log.debug("Installing csar_link . from URL "+url);
            return ResourceUtils.create(context, this, "TOSCA csar_link: .").getResourceFromUrl(url);
        }
        
        OsgiManager osgiMgr = ((ManagementContextInternal)osgiCtx.getManagementContext()).getOsgiManager().get();
        Bundle b = osgiMgr.findBundle(catalogBundle).get();
        ManagedBundle mb = osgiMgr.getManagedBundle(catalogBundle.getVersionedName());
        File fn = osgiMgr.getBundleFile(mb);
        if (fn==null) {
            throw new IllegalStateException("No file available for first bundle "+catalogBundle+"/"+mb+" in first primary loading context "+osgiCtx);
        }
        try {
            log.debug("Installing csar_link . from file "+fn);
            return new FileInputStream(fn);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to find file for bundle "+mb+" ("+fn+")", e);
        }
    }
}
