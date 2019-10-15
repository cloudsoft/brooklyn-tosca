package io.cloudsoft.tosca.a4c.brooklyn;

import alien4cloud.tosca.parser.ParsingResult;
import org.alien4cloud.tosca.model.Csar;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.typereg.UnsupportedTypePlanException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.yaml.Yamls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

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
            try {
                resourceFromUrl = resLoader.getResourceFromUrl(type.csarLink);
            } catch (Exception e) {
                if (type.csarLink.startsWith("classpath:")) {
                    throw Exceptions.propagateAnnotated("Could not load "+type.csarLink+" relative to context "+context, e);
                } else {
                    throw Exceptions.propagate(e);
                }
            }
            // TODO either we need to be able to look up the CSAR ZIP after upload, or
            // we have to annotate it with context and csarLink of this file
            tp = uploader.uploadArchive(resourceFromUrl, "submitted-tosca-archive");

        } else {
            tp = uploader.uploadSingleYaml(Streams.newInputStreamWithContents(plan), "submitted-tosca-plan");
        }

       /* if (ArchiveUploadService.hasError(tp, ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Could not parse TOSCA plan: "
                    + Strings.join(tp.getContext().getParsingErrors(), "\n  "));
        }*/

        return tp;
    }
}
