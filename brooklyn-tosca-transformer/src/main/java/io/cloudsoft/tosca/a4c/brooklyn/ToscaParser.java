package io.cloudsoft.tosca.a4c.brooklyn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;

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
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
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

        Exception error = null;
        Object obj = null;
        boolean isTosca = false;
        String csarLink;

        // will set either error or obj, isTosca=true, or csarLink!=null (but not both),
        // or both null meaning it's YAML but not TOSCA
        public PlanTypeChecker(String plan, BrooklynClassLoadingContext context) {
            try {
                obj = Yamls.parseAll(plan).iterator().next();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (isToscaScore(plan)>0) {
                    error = new UserFacingException("Plan looks like it's meant to be TOSCA but it is not valid YAML", e);
                    log.debug("Invalid TOSCA YAML: "+error, error);
                } else {
                    error = new UserFacingException("Plan does not look like TOSCA and is not valid YAML");
                    log.trace("Not YAML", e);
                }
                return;
            }
            if (!(obj instanceof Map)) {
                // don't support just a URL pointing to CSAR (we used to) -- it needs to be a map with key csar_link
                error = new UserFacingException("Plan does not look like TOSCA: parses as YAML but not as a map");
//                log.trace("Not a map");
//                // is it a one-line URL?
//                plan = plan.trim();
//                if (!plan.contains("\n") && Urls.isUrlWithProtocol(plan)) {
//                    csarLink = plan;
//                } else {
//                    
//                }
                return;
            }

            if (isToscaScore((Map<?,?>)obj)>0) {
                isTosca = true;
                return;
            }

            if (((Map<?,?>)obj).size()==1) {
                csarLink = (String) ((Map<?,?>)obj).get("csar_link");
                if (csarLink!=null) {
                    return;
                }
                
                String toscaLink = (String) ((Map<?,?>)obj).get("tosca_link");
                if (toscaLink!=null) {
                    ResourceUtils resLoader = context!=null ? new ResourceUtils(context) : new ResourceUtils(this);
                    obj = Yamls.parseAll(resLoader.getResourceAsString(toscaLink)).iterator().next();
                    isTosca = true;
                }
            }
            
            error = new UserFacingException("Plan does not look like TOSCA or csar_link: parses as YAML map but not one this TOSCA engine understands");
        }

    }
    
    public static double isToscaScore(Map<?,?> obj) {
        return isToscaScore(obj, (map,s) -> map.containsKey(s));
    }
    public static double isToscaScore(String obj) {
        return isToscaScore(obj, (plan,s) -> plan.contains(s));
    }
    public static <T> double isToscaScore(T obj, BiFunction<T,String,Boolean> contains) {
        if (contains.apply(obj, "tosca_definitions_version")) return 1;
        if (contains.apply(obj, "topology_template")) return 0.9;
        if (contains.apply(obj, "topology_name")) return 0.5;
        if (contains.apply(obj, "node_types")) return 0.5;
        log.trace("Not TOSCA - no recognized keys");
        return 0;
    }

    public ToscaParser(Uploader uploader) {
        this.uploader = uploader;
    }

    public ParsingResult<Csar> parse(String plan, BrooklynClassLoadingContext context) {
        ParsingResult<Csar> tp;
        PlanTypeChecker type = new PlanTypeChecker(plan, context);
        
        if (type.error!=null) {
            throw Exceptions.propagate(type.error);
            
        } else if (type.isTosca) {
            tp = uploader.uploadSingleYaml(Streams.newInputStreamWithContents(plan), "submitted-tosca-plan");
            
        } else if (type.csarLink != null) {
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
            tp = uploader.uploadArchive(resourceFromUrl, "submitted-tosca-archive");
            
        } else {
            // one of the above cases should be true, shouldn't come here...
            throw new UnsupportedTypePlanException("Does not look like TOSCA");
        }

        if (ArchiveUploadService.hasError(tp, ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Could not parse TOSCA plan: "+"\n  "
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
