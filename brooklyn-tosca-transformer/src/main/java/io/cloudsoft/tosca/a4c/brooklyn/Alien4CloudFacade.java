package io.cloudsoft.tosca.a4c.brooklyn;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.ICsarRepositry;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.model.application.Application;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.Csar;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedInheritableToscaElement;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.templates.TopologyTemplate;
import alien4cloud.model.templates.TopologyTemplateVersion;
import alien4cloud.model.topology.AbstractTemplate;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Requirement;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.topology.TopologyTemplateVersionService;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.tosca.parser.ParsingResult;
import io.cloudsoft.tosca.a4c.brooklyn.util.NodeTemplates;

@Component
public class Alien4CloudFacade implements ToscaFacade<Alien4CloudApplication>{

    private static final Logger LOG = LoggerFactory.getLogger(Alien4CloudFacade.class);
    public static final String COMPUTE_TYPE = NormativeComputeConstants.COMPUTE_TYPE;

    private static final Map<String, ConfigKey<String>> lifeCycleMapping = ImmutableMap.<String, ConfigKey<String>>builder()
            .put(ToscaRelationshipLifecycleConstants.PRE_CONFIGURE_SOURCE, VanillaSoftwareProcess.PRE_CUSTOMIZE_COMMAND)
            .put(ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE, VanillaSoftwareProcess.POST_CUSTOMIZE_COMMAND)
            .put(ToscaNodeLifecycleConstants.CREATE, VanillaSoftwareProcess.INSTALL_COMMAND)
            .put(ToscaNodeLifecycleConstants.CONFIGURE, VanillaSoftwareProcess.CUSTOMIZE_COMMAND)
            .put(ToscaNodeLifecycleConstants.START, VanillaSoftwareProcess.LAUNCH_COMMAND)
            .put(ToscaNodeLifecycleConstants.STOP, VanillaSoftwareProcess.STOP_COMMAND)
            .build();

    private ICSARRepositorySearchService repositorySearchService;
    private TopologyTreeBuilderService treeBuilder;
    private ICsarRepositry csarFileRepository;
    private TopologyServiceCore topologyService;
    private TopologyTemplateVersionService topologyTemplateVersionService;
    private DeploymentTopologyService deploymentTopologyService;
    private ApplicationService applicationService;

    private final File tmpRoot;

    @Inject
    public Alien4CloudFacade(ICSARRepositorySearchService repositorySearchService, TopologyTreeBuilderService treeBuilder, ICsarRepositry csarFileRepository, TopologyServiceCore topologyService, TopologyTemplateVersionService topologyTemplateVersionService, DeploymentTopologyService deploymentTopologyService, ApplicationService applicationService) {
        this.repositorySearchService = repositorySearchService;
        this.treeBuilder = treeBuilder;
        this.csarFileRepository = csarFileRepository;
        this.topologyService = topologyService;
        this.topologyTemplateVersionService = topologyTemplateVersionService;
        this.deploymentTopologyService = deploymentTopologyService;
        this.applicationService = applicationService;

        tmpRoot = Os.newTempDir("brooklyn-a4c");
        Os.deleteOnExitRecursively(tmpRoot);
    }

    private Topology getTopologyOfCsar(Csar cs) {
        TopologyTemplate tt = topologyService.searchTopologyTemplateByName(cs.getName());
        if (tt == null) return null;
        TopologyTemplateVersion[] ttv = topologyTemplateVersionService.getByDelegateId(tt.getId());
        if (ttv == null || ttv.length == 0) return null;
        return topologyService.getTopology(ttv[0].getTopologyId());
    }

    /**
     * Resolves the parent dependency of the given node template.
     * <p>
     * The parent dependency is:
     * <ul>
     * <li>The target node of a requirement called "host"</li>
     * <li>The resolved value of a property called "host"</li>
     * <li>Null</li>
     * </ul>
     *
     * @param nodeId The node to examine
     * @param toscaApplication The tosca application
     * @return The id of the parent or null.
     */
    @Override
    public String getParentId(String nodeId, Alien4CloudApplication toscaApplication) {
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        Requirement host = nodeTemplate.getRequirements() != null ? nodeTemplate.getRequirements().get("host") : null;
        if (host != null) {
            for (RelationshipTemplate r : nodeTemplate.getRelationships().values()) {
                if (r.getRequirementName().equals("host")) {
                    return r.getTarget();
                }
            }
        }

        // temporarily, fall back to looking for a *property* called 'host'
        // todo: why?
        Optional<Object> parentId = resolve(nodeTemplate.getProperties(), "host");
        if (parentId.isPresent()) {
            LOG.warn("Using legacy 'host' *property* to resolve host; use *requirement* instead.");
        }
        return (String) parentId.orNull();
    }

    private IndexedArtifactToscaElement getIndexedNodeTemplate(String nodeId, Alien4CloudApplication toscaApplication) {
        return repositorySearchService.getRequiredElementInDependencies(
                IndexedArtifactToscaElement.class,
                toscaApplication.getNodeTemplate(nodeId).getType(),
                toscaApplication.getTopology().getDependencies());
    }

    private Optional<IndexedArtifactToscaElement> getIndexedRelationshipTemplate(String nodeId, Alien4CloudApplication toscaApplication, String requirementId) {
        Optional<RelationshipTemplate> optionalRelationshipTemplate = findRelationshipRequirement(nodeId, toscaApplication, requirementId);
        if(optionalRelationshipTemplate.isPresent()) {
            RelationshipTemplate relationshipTemplate = optionalRelationshipTemplate.get();
            return Optional.<IndexedArtifactToscaElement>of(repositorySearchService.getRequiredElementInDependencies(
                    IndexedRelationshipType.class,
                    relationshipTemplate.getType(),
                    toscaApplication.getTopology().getDependencies()
            ));
        }

        return Optional.absent();
    }

    @Override
    public boolean isDerivedFrom(String nodeId, Alien4CloudApplication toscaApplication, String type) {
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        return nodeTemplate.getType().equals(type) ||
                getIndexedNodeTemplate(nodeId, toscaApplication)
                        .getDerivedFrom()
                        .contains(type);
    }

    private Map<String, PaaSNodeTemplate> getAllNodes(Alien4CloudApplication toscaApplication) {
        return treeBuilder.buildPaaSTopology(toscaApplication.getTopology()).getAllNodes();
    }

    @Override
    public Map<String, Object> getTemplatePropertyObjects(String nodeId, Alien4CloudApplication toscaApplication, String computeName) {
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = getAllNodes(toscaApplication);
        PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        return getTemplatePropertyObjects(nodeTemplate, paasNodeTemplate, builtPaaSNodeTemplates, toscaApplication.getKeywordMap(nodeTemplate));
    }

    private Map<String, Object> getTemplatePropertyObjects(AbstractTemplate template, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
        return getPropertyObjects(template.getProperties(), paasNodeTemplate, builtPaaSNodeTemplates, keywordMap);
    }

    private Map<String, Object> getPropertyObjects(Map<String, AbstractPropertyValue> propertyValueMap, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
        Map<String, Object> propertyMap = MutableMap.of();
        for (String propertyKey : ImmutableSet.copyOf(propertyValueMap.keySet())) {
            propertyMap.put(propertyKey, resolve(propertyValueMap, propertyKey, paasNodeTemplate, builtPaaSNodeTemplates, keywordMap).orNull());
        }
        return propertyMap;
    }

    private Optional<Object> resolve(Map<String, ? extends IValue> props, String key) {
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

    private Optional<Object> resolve(Map<String, ? extends IValue> props, String key, IPaaSTemplate<? extends IndexedInheritableToscaElement> template, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates, Map<String, String> keywordMap) {
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

    private Optional<Path> getCsarPath(DeploymentArtifact artifact) {
        return getCsarPath(artifact.getArchiveName(), artifact.getArchiveVersion());
    }

    @Override
    public Optional<Path> getCsarPath(String archiveName, String archiveVersion) {
        try {
            return Optional.of(csarFileRepository.getCSAR(archiveName, archiveVersion));
        } catch (Exception e) {
            return Optional.absent();
        }
    }

    private Optional<Map<String, DeploymentArtifact>> getArtifactsMap(String nodeId, Alien4CloudApplication toscaApplication) {
        Map<String, DeploymentArtifact> artifacts = getIndexedNodeTemplate(nodeId, toscaApplication).getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return Optional.absent();
        }
        return Optional.of(artifacts);
    }

    @Override
    public Iterable<String> getArtifacts(String nodeId, Alien4CloudApplication toscaApplication) {
        Optional<Map<String, DeploymentArtifact>> optionalArtifacts = getArtifactsMap(nodeId, toscaApplication);
        if(!optionalArtifacts.isPresent()) {
            return Collections.emptyList();
        }
        return optionalArtifacts.get().keySet();
    }

    private Optional<DeploymentArtifact> getArtifact(String nodeId, Alien4CloudApplication toscaApplication, String artifactId) {
        return Optional.fromNullable(getArtifactsMap(nodeId, toscaApplication).get().get(artifactId));
    }

    private Map<String, Operation> getStandardInterfaceOperationsMap(String nodeId, Alien4CloudApplication toscaApplication) {
        Map<String, Operation> operations = MutableMap.of();
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        IndexedArtifactToscaElement indexedNodeTemplate = getIndexedNodeTemplate(nodeId, toscaApplication);
        List<String> validInterfaceNames = ImmutableList.of("tosca.interfaces.node.lifecycle.Standard", "Standard", "standard");
        operations.putAll(getInterfaceOperationsMap(indexedNodeTemplate, validInterfaceNames));

        Optional<Interface> optionalNodeTemplateInterface = NodeTemplates.findInterfaceOfNodeTemplate(
                nodeTemplate.getInterfaces(), validInterfaceNames);

        if (optionalNodeTemplateInterface.isPresent()) {
            operations.putAll(optionalNodeTemplateInterface.get().getOperations());
        }
        return operations;
    }

    private Map<String, Operation> getConfigureInterfaceOperationsMap(String nodeId, Alien4CloudApplication toscaApplication, String requirementId) {
        Map<String, Operation> operations = MutableMap.of();
        Optional<IndexedArtifactToscaElement> indexedRelationshipTemplate = getIndexedRelationshipTemplate(nodeId, toscaApplication, requirementId);
        if(!indexedRelationshipTemplate.isPresent()){
            return operations;
        }
        List<String> validInterfaceNames = ImmutableList.of("tosca.interfaces.relationship.Configure", "Configure", "configure");
        return getInterfaceOperationsMap(indexedRelationshipTemplate.get(), validInterfaceNames);
    }

    private Map<String, Operation> getInterfaceOperationsMap(IndexedArtifactToscaElement indexedRelationshipTemplate, List<String> validInterfaceNames) {
        Map<String, Operation> operations = MutableMap.of();
        Optional<Interface> optionalIndexedNodeTemplateInterface = NodeTemplates.findInterfaceOfNodeTemplate(
                indexedRelationshipTemplate.getInterfaces(),
                validInterfaceNames
        );
        if (!optionalIndexedNodeTemplateInterface.isPresent()) {
            return operations;
        }
        return MutableMap.copyOf(optionalIndexedNodeTemplateInterface.get().getOperations());
    }

    @Override
    public Iterable<String> getInterfaceOperations(String nodeId, Alien4CloudApplication toscaApplication) {
        return getStandardInterfaceOperationsMap(nodeId, toscaApplication).keySet();
    }

    @Override
    public Iterable<String> getInterfaceOperations(String nodeId, Alien4CloudApplication toscaApplication, String requirementId) {
        return getConfigureInterfaceOperationsMap(nodeId, toscaApplication, requirementId).keySet();
    }

    private Optional<PaaSNodeTemplate> getPaasNodeTemplate(String nodeId, Alien4CloudApplication toscaApplication) {
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        PaaSTopology paaSTopology = treeBuilder.buildPaaSTopology(toscaApplication.getTopology());
        if (paaSTopology != null) {
            Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = paaSTopology.getAllNodes();
            String computeName = nodeTemplate.getName();
            return Optional.of(builtPaaSNodeTemplates.get(computeName));
        }
        return Optional.absent();
    }

    private Optional<RelationshipTemplate> findRelationshipRequirement(String nodeId, Alien4CloudApplication toscaApplication, String requirementId) {
        NodeTemplate node = toscaApplication.getNodeTemplate(nodeId);
        if (node.getRelationships() != null) {
            for (Map.Entry<String, RelationshipTemplate> entry : node.getRelationships().entrySet()) {
                if (entry.getValue().getRequirementName().equals(requirementId)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        LOG.warn("Requirement {} is not described by any relationship ", requirementId);
        return Optional.absent();
    }

    private Map<String, Object> getRelationProperties(String nodeId, String computeName, Alien4CloudApplication toscaApplication, RelationshipTemplate relationshipTemplate) {
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = getAllNodes(toscaApplication);
        PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
        NodeTemplate nodeTemplate = toscaApplication.getNodeTemplate(nodeId);
        return getTemplatePropertyObjects(relationshipTemplate, paasNodeTemplate, builtPaaSNodeTemplates, toscaApplication.getKeywordMap(nodeTemplate, relationshipTemplate));
    }

    private String resolveAttribute(Map.Entry<String, IValue> attribute, Alien4CloudApplication toscaApplication, PaaSNodeTemplate paaSNodeTemplate, Map<String, PaaSNodeTemplate> allNodes) {
        String returnValue = FunctionEvaluator.parseAttribute(
                attribute.getKey(),
                attribute.getValue(),
                toscaApplication.getTopology(),
                ImmutableMap.<String, Map<String, InstanceInformation>>of(),
                "",
                paaSNodeTemplate,
                allNodes);
        // Alien4Cloud returns the attribute key surrounded by <> if it does not evaluate to anything
        // We turn this to the empty String to avoid populating sensors.
        if (returnValue.equals("<" + attribute.getKey() + ">")) {
            return "";
        }
        return returnValue;
    }

    @Override
    public Object resolveProperty(String nodeId, Alien4CloudApplication toscaApplication, String key) {
        Map<String, AbstractPropertyValue> properties = toscaApplication.getNodeTemplate(nodeId).getProperties();
        return resolve(properties, key).orNull();
    }

    @Override
    public Optional<Object> getScript(String opKey, String nodeId, Alien4CloudApplication toscaApplication, String computeName, String expandedFolder) {
        return new NodeScriptGenerator(opKey, nodeId, toscaApplication, computeName, expandedFolder).makeScript();
    }

    @Override
    public Optional<Object> getRelationshipScript(String opKey, String nodeId, Alien4CloudApplication toscaApplication, String requirementId, String computeName, String expandedFolder) {
        return new RelationshipGenerator(opKey, nodeId, toscaApplication, requirementId, computeName, expandedFolder).makeScript();
    }

    @Override
    public ConfigKey<String> getLifeCycle(String opKey) {
        return lifeCycleMapping.get(opKey);
    }

    @Override
    public Map<String, String> getResolvedAttributes(String nodeId, Alien4CloudApplication toscaApplication) {
        Map<String, String> resolvedAttributes = MutableMap.of();
        Optional<PaaSNodeTemplate> optionalPaaSNodeTemplate = getPaasNodeTemplate(nodeId, toscaApplication);
        if (optionalPaaSNodeTemplate.isPresent()) {
            Map<String, PaaSNodeTemplate> allNodes = getAllNodes(toscaApplication);
            final Map<String, IValue> attributes = getIndexedNodeTemplate(nodeId, toscaApplication).getAttributes();
            for (Map.Entry<String, IValue> attribute : attributes.entrySet()) {
                String key = attribute.getKey().replaceAll("\\s+", ".");
                String value = resolveAttribute(attribute, toscaApplication, optionalPaaSNodeTemplate.get(), allNodes);
                resolvedAttributes.put(key, value);
            }
        }
        return resolvedAttributes;
    }

    @Override
    public Map<String, Object> getPropertiesAndTypeValues(String nodeId, Alien4CloudApplication toscaApplication, String requirementId, String computeName) {
        Optional<RelationshipTemplate> optionalRelationshipTemplate = findRelationshipRequirement(nodeId, toscaApplication, requirementId);
        if (optionalRelationshipTemplate.isPresent()) {
            RelationshipTemplate relationshipTemplate = optionalRelationshipTemplate.get();
            if (relationshipTemplate.getType().equals("brooklyn.relationships.Configure")) {
                Map<String, Object> relationProperties = getRelationProperties(nodeId, computeName, toscaApplication, relationshipTemplate);
                return getPropertiesAndTypedValues(relationshipTemplate, relationProperties, computeName);
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> getPropertiesAndTypedValues(RelationshipTemplate relationshipTemplate, Map<String, Object> relationProperties, String nodeName) {
        // TODO: Use target properly.
        String target = relationshipTemplate.getTarget();
        String propName = relationProperties.get("prop.name").toString();
        String propCollection = relationProperties.get("prop.collection").toString();
        String propValue = relationProperties.get("prop.value").toString();

        if (Strings.isBlank(propCollection) && (Strings.isBlank(propName))) {
            throw new IllegalStateException("Relationship for Requirement "
                    + relationshipTemplate.getRequirementName() + " on NodeTemplate "
                    + nodeName + ". Collection Name or Property Name should" +
                    " be defined for RelationsType " + relationshipTemplate.getType());
        }

        return (Strings.isBlank(propName)) ? ImmutableMap.<String, Object>of(propCollection, ImmutableList.of(propValue))
                : ImmutableMap.<String, Object>of(propCollection, ImmutableMap.of(propName, propValue));
    }

    @Override
    public Optional<Path> getArtifactPath(String nodeId, Alien4CloudApplication toscaApplication, String artifactId) {
        Optional<DeploymentArtifact> optionalArtifact = getArtifact(nodeId, toscaApplication, artifactId);
        if(!optionalArtifact.isPresent()) return Optional.absent();

        DeploymentArtifact artifact = optionalArtifact.get();
        Optional<Path> csarPath = getCsarPath(artifact);
        if(!csarPath.isPresent()) {
            LOG.warn("CSAR " + artifactId + ":" + artifact.getArchiveVersion() + " does not exist");
            return Optional.absent();
        } else {
            return Optional.of(Paths.get(csarPath.get().getParent().toAbsolutePath().toString(), "expanded", artifactId));
        }
    }

    private Alien4CloudApplication newToscaApplication(Csar csar) {
        return new Alien4CloudApplication(csar.getName(), getTopologyOfCsar(csar), "");
    }

    @Override
    public Alien4CloudApplication newToscaApplication(String id) {
        DeploymentTopology deploymentTopology = deploymentTopologyService.getOrFail(id);
        Application application = applicationService.getOrFail(deploymentTopology.getDelegateId());
        return new Alien4CloudApplication(application.getName(), deploymentTopology, id);
    }

    @Override
    public Alien4CloudApplication parsePlan(String plan, Uploader uploader) {
        ParsingResult<Csar> tp = new ToscaParser(uploader).parse(plan);
        Csar csar = tp.getResult();
        return newToscaApplication(csar);
    }

    @Override
    public Alien4CloudApplication parsePlan(Path path, Uploader uploader) {
        ParsingResult<Csar> tp = uploader.uploadArchive(path.toFile(), "submitted-tosca-archive");
        Csar csar = tp.getResult();
        return newToscaApplication(csar);
    }

    abstract class ScriptGenerator{

        protected final String opKey;
        protected final String nodeId;
        protected final Alien4CloudApplication toscaApplication;
        protected final String computeName;
        protected final String expandedFolder;

        public ScriptGenerator(String opKey, String nodeId, Alien4CloudApplication toscaApplication, String computeName, String expandedFolder) {
            this.opKey = opKey;
            this.nodeId = nodeId;
            this.toscaApplication = toscaApplication;
            this.computeName = computeName;
            this.expandedFolder = expandedFolder;
        }

        public Optional<Object> makeScript() {
            if (!lifeCycleMapping.containsKey(opKey)) {
                LOG.warn("Could not translate operation, {}, for node template, {}.", opKey, toscaApplication.getNodeName(nodeId).orNull());
                return Optional.absent();
            }

            Operation op = getOperation();
            ImplementationArtifact artifact = op.getImplementationArtifact();
            if (artifact == null) {
                LOG.warn("Unsupported operation implementation for " + op.getDescription() + ":  artifact has no impl");
                return Optional.absent();
            }

            String ref = artifact.getArtifactRef();
            if (ref == null) {
                LOG.warn("Unsupported operation implementation for " + op.getDescription() + ": " + artifact + " has no ref");
                return Optional.absent();
            }
            return Optional.of(getScript(artifact, op));
        }

        abstract Object getScript(ImplementationArtifact artifact, Operation op);

        abstract Operation getOperation();

        protected String getScript(ImplementationArtifact artifact) {
            // Trying to get the CSAR file based on the artifact reference. If it fails, then we try to get the
            // content of the script from any resources
            String artifactRef = artifact.getArtifactRef();
            Optional<Path> csarPath = getCsarPath(artifact.getArchiveName(), artifact.getArchiveVersion());
            if(!csarPath.isPresent()) {
                return new ResourceUtils(this).getResourceAsString(artifactRef);
            }
            return new ResourceUtils(this).getResourceAsString(csarPath.get().getParent().toString() + expandedFolder + artifactRef);
        }

        protected Optional<Object> buildExportStatements(Operation op, String script) {
            Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = getAllNodes(toscaApplication);
            PaaSNodeTemplate paasNodeTemplate = builtPaaSNodeTemplates.get(computeName);
            Map<String, IValue> inputParameters = op.getInputParameters();
            if (inputParameters == null) {
                return Optional.absent();
            }
            List<Object> dsls = Lists.newArrayList();
            for (Map.Entry<String, IValue> entry : inputParameters.entrySet()) {
                Optional<Object> value = resolve(inputParameters, entry.getKey(), paasNodeTemplate, builtPaaSNodeTemplates);
                if (value.isPresent() && !Strings.isBlank(entry.getKey())) {
                    dsls.add(BrooklynDslCommon.formatString("export %s=\"%s\"", entry.getKey(), value.get()));
                }
            }
            dsls.add(script);
            return Optional.of(BrooklynDslCommon.formatString(Strings.repeat("%s\n", dsls.size()), dsls.toArray()));
        }

        protected abstract Optional<Object> resolve(Map<String, IValue> inputParameters, String key, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates);

    }

    class NodeScriptGenerator extends  ScriptGenerator{

        public NodeScriptGenerator(String opKey, String nodeId, Alien4CloudApplication toscaApplication, String computeName, String expandedFolder) {
            super(opKey, nodeId, toscaApplication, computeName, expandedFolder);
        }

        @Override
        Object getScript(ImplementationArtifact artifact, Operation op) {
            String script = getScript(artifact);
            return buildExportStatements(op, script).or(script);
        }

        @Override
        Operation getOperation() {
            return getStandardInterfaceOperationsMap(nodeId, toscaApplication).get(opKey);
        }

        @Override
        protected Optional<Object> resolve(Map<String, IValue> inputParameters, String key, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates) {
            return Alien4CloudFacade.this.resolve(inputParameters, key, paasNodeTemplate, builtPaaSNodeTemplates, toscaApplication.getKeywordMap(nodeId));
        }
    }

    class RelationshipGenerator extends ScriptGenerator {

        private String requirementId;
        private RelationshipTemplate relationshipTemplate;

        public RelationshipGenerator(String opKey, String nodeId, Alien4CloudApplication toscaApplication, String requirementId, String computeName, String expandedFolder) {
            super(opKey, nodeId, toscaApplication, computeName, expandedFolder);
            this.requirementId = requirementId;
        }

        @Override
        Object getScript(ImplementationArtifact artifact, Operation op) {
            Optional<RelationshipTemplate> optionalRelationshipTemplate = findRelationshipRequirement(nodeId, toscaApplication, requirementId);
            if (!optionalRelationshipTemplate.isPresent()) {
                LOG.warn("Unsupported operation implementation for " + op.getDescription() + ": no relationship template");
                return Optional.absent();
            }
            this.relationshipTemplate = optionalRelationshipTemplate.get();

            String script = getScript(artifact);
            return buildExportStatements(op, script).or(script);
        }

        @Override
        Operation getOperation() {
            return getConfigureInterfaceOperationsMap(nodeId, toscaApplication, requirementId).get(opKey);
        }

        @Override
        protected Optional<Object> resolve(Map<String, IValue> inputParameters, String key, PaaSNodeTemplate paasNodeTemplate, Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates) {
            Optional<Map.Entry<String, RelationshipTemplate>> relationshipEntry = Iterables.tryFind(toscaApplication.getNodeTemplate(nodeId).getRelationships().entrySet(), new Predicate<Map.Entry<String, RelationshipTemplate>>() {
                @Override
                public boolean apply(@Nullable Map.Entry<String, RelationshipTemplate> o) {
                    return requirementId.equals(o.getValue().getRequirementName());
                }
            });
            if (!relationshipEntry.isPresent()) {
                throw new IllegalStateException("Could not find relationship for requirement " + requirementId);
            }
            PaaSRelationshipTemplate paaSRelationshipTemplate =  paasNodeTemplate.getRelationshipTemplate(relationshipEntry.get().getKey(), nodeId);
            Map<String, String> keywordMap = toscaApplication.getKeywordMap(toscaApplication.getNodeTemplate(nodeId), relationshipTemplate);
            return Alien4CloudFacade.this.resolve(inputParameters, key, paaSRelationshipTemplate, builtPaaSNodeTemplates, keywordMap);
        }
    }
}
