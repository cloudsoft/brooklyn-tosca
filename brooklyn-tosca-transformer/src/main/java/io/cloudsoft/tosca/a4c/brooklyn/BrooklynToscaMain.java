package io.cloudsoft.tosca.a4c.brooklyn;

import java.util.Arrays;

import org.apache.brooklyn.cli.Main;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a static main entry point for launching Brooklyn with TOSCA support.
 * <p>
 * It inherits the standard Brooklyn CLI options from {@link Main},
 * with the TOSCA module loaded as a {@link PlanToSpecTransformer}.
 * <p>
 * It is also fine to drop the TOSCA JAR into your brooklyn drop-ins.
 */
public class BrooklynToscaMain extends Main {
    
    private static final Logger log = LoggerFactory.getLogger(BrooklynToscaMain.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static void main(String... args) {
        log.debug("CLI invoked with args "+Arrays.asList(args));
        new BrooklynToscaMain().execCli(args);
    }

    @Override
    protected String cliScriptName() {
        return "start.sh";
    }
    
}
