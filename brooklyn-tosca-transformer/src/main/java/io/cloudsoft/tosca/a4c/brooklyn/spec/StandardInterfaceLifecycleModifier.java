package io.cloudsoft.tosca.a4c.brooklyn.spec;

import javax.inject.Inject;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;

import io.cloudsoft.tosca.a4c.brooklyn.ApplicationSpecsBuilder;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaApplication;
import io.cloudsoft.tosca.a4c.brooklyn.ToscaFacade;

// TODO: Handle interfaces differently.
@Component
public class StandardInterfaceLifecycleModifier extends AbstractSpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(StandardInterfaceLifecycleModifier.class);
    public static final String EXPANDED_FOLDER = "/expanded/";

    @Inject
    public StandardInterfaceLifecycleModifier(ManagementContext mgmt, ToscaFacade<? extends ToscaApplication> alien4CloudFacade) {
        super(mgmt, alien4CloudFacade);
    }

    @Override
    public void apply(EntitySpec<?> entitySpec, String nodeId, ToscaApplication toscaApplication) {
        if (!entitySpec.getType().isAssignableFrom(VanillaSoftwareProcess.class)) {
            return;
        }
        // If the entity spec is of type VanillaSoftwareProcess, we assume that it's running. The operations should
        // then take care of setting up the correct scripts.

        // We add .getName to these ConfigKeys to use them as Flags because later we will potentially
        // override the configured value with a BrooklynDslDeferredSupplier object. This overridden
        // value would not have been used since it would be treated as a Flag while the ConfigKey would
        // take precedence.
        entitySpec.configure(VanillaSoftwareProcess.LAUNCH_COMMAND.getName(), "true");
        entitySpec.configure(VanillaSoftwareProcess.STOP_COMMAND.getName(), "true");
        entitySpec.configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND.getName(), "true");

        // Applying operations
        final Iterable<String> operations = getToscaFacade().getInterfaceOperations(nodeId, toscaApplication);
        for (String opKey : operations) {
            String computeName = toscaApplication.getNodeName(nodeId).or(String.valueOf(entitySpec.getFlags().get(ApplicationSpecsBuilder.TOSCA_TEMPLATE_ID)));
            final Optional<Object> script = getToscaFacade().getScript(opKey, nodeId, toscaApplication, computeName, EXPANDED_FOLDER, mgmt);
            if (script.isPresent()) {
                entitySpec.configure(getToscaFacade().getLifeCycle(opKey).getName(), script.get());
            }
        }
    }
}
