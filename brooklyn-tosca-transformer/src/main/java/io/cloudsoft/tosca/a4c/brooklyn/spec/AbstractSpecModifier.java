package io.cloudsoft.tosca.a4c.brooklyn.spec;

import java.util.Map;
import javax.inject.Inject;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.CampCatalogUtils;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

public abstract class AbstractSpecModifier implements EntitySpecModifier {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSpecModifier.class);

    private final ManagementContext mgmt;

    private CSARRepositorySearchService repositorySearchService;

    protected AbstractSpecModifier(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    @Inject
    @Required
    public void setRepositorySearchService(CSARRepositorySearchService repositorySearchService) {
        this.repositorySearchService = repositorySearchService;
    }

    protected Optional<IndexedArtifactToscaElement> getIndexedNodeTemplate(NodeTemplate nodeTemplate, Topology topology) {
        if (repositorySearchService != null) {
            return Optional.of(repositorySearchService.getRequiredElementInDependencies(
                    IndexedArtifactToscaElement.class,
                    nodeTemplate.getType(),
                    topology.getDependencies()));
        } else {
            return Optional.absent();
        }
    }

    protected Optional<Object> resolveValue(Object unresolvedValue, Optional<TypeToken> desiredType) {
        if (unresolvedValue == null) {
            return Optional.absent();
        }
        // The 'dsl' key is arbitrary, but the interpreter requires a map
        Map<String, Object> resolvedConfigMap = CampCatalogUtils.getCampPlatform(mgmt)
                .pdp()
                .applyInterpreters(ImmutableMap.of("dsl", unresolvedValue));
        return Optional.of(desiredType.isPresent()
                           ? TypeCoercions.coerce(resolvedConfigMap.get("dsl"), desiredType.get())
                           : resolvedConfigMap.get("dsl"));
    }

    public static Optional<Object> resolve(Map<String, ? extends IValue> props, String key) {
        IValue v = props.get(key);
        if (v == null) {
            LOG.warn("No value available for {}", key);
            return Optional.absent();
        }

        if (v instanceof ScalarPropertyValue) {
            return Optional.<Object>fromNullable(((ScalarPropertyValue) v).getValue());
        }
        if (v instanceof ComplexPropertyValue) {
            return Optional.<Object>fromNullable(((ComplexPropertyValue) v).getValue());
        }
        if (!(v instanceof FunctionPropertyValue)) {
            LOG.warn("Ignoring unsupported property value " + v);
        }
        return Optional.absent();
    }

    public static Optional<Object> resolve(Map<String, ? extends IValue> props, String key, PaaSNodeTemplate template, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
        Optional<Object> value = resolve(props, key);
        if (!value.isPresent()) {
            IValue v = props.get(key);
            if (v instanceof FunctionPropertyValue) {
                FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) v;
                String node = Optional.fromNullable(keywordMap.get(functionPropertyValue.getTemplateName())).or(functionPropertyValue.getTemplateName());
                switch (functionPropertyValue.getFunction()) {
                    case ToscaFunctionConstants.GET_PROPERTY:
                        value = Optional.<Object>fromNullable(FunctionEvaluator.evaluateGetPropertyFunction(functionPropertyValue, template, builtPaaSNodeTemplates));
                        break;
                    case ToscaFunctionConstants.GET_ATTRIBUTE:
                        value = Optional.<Object>fromNullable(BrooklynDslCommon.entity(node).attributeWhenReady(functionPropertyValue.getElementNameToFetch()));
                        break;
                    case ToscaFunctionConstants.GET_INPUT:
                    case ToscaFunctionConstants.GET_OPERATION_OUTPUT:
                    default:
                        value = Optional.absent();
                }
            }
        }
        return value;
    }

}
