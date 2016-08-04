package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Map;

import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alien4cloud.model.components.Csar;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import io.cloudsoft.tosca.a4c.platform.Alien4CloudToscaPlatform;

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

    public ParsingResult<Csar> parse(String plan) {
        ParsingResult<Csar> tp;
        PlanTypeChecker type = new PlanTypeChecker(plan);
        if (!type.isTosca) {
            if (type.csarLink == null) {
                throw new PlanNotRecognizedException("Does not look like TOSCA");
            }
            tp = uploader.uploadArchive(new ResourceUtils(this).getResourceFromUrl(type.csarLink), "submitted-tosca-archive");

        } else {
            tp = uploader.uploadSingleYaml(Streams.newInputStreamWithContents(plan), "submitted-tosca-plan");
        }

        if (tp.hasError(ParsingErrorLevel.ERROR)) {
            throw new UserFacingException("Could not parse TOSCA plan: "
                    + Strings.join(tp.getContext().getParsingErrors(), "\n  "));
        }

        return tp;
    }
}
