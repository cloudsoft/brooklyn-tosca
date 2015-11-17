package alien4cloud.brooklyn;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import alien4cloud.model.components.AttributeDefinition;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.CapabilityDefinition;
import alien4cloud.model.components.Csar;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.RequirementDefinition;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import lombok.extern.slf4j.Slf4j;

import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.apache.brooklyn.rest.domain.EffectorSummary;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import alien4cloud.component.repository.ICsarRepositry;
import alien4cloud.csar.services.CsarService;
import alien4cloud.tosca.ArchiveImageLoader;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.model.ArchiveRoot;

/**
 * This component is used to map components out of brooklyn to a4c.
 * 
 * TODO - operation in Brooklyn to export types to A4C, then use existing import mechanism in A4C
 */
@Component
@Slf4j
public class BrooklynCatalogMapper {
    private final static Map<String, String> TYPE_MAPPING = Maps.newHashMap();
    static {
        TYPE_MAPPING.put(Boolean.class.getName(), ToscaType.BOOLEAN);
        TYPE_MAPPING.put(String.class.getName(), ToscaType.STRING);
        TYPE_MAPPING.put(Integer.class.getName(), ToscaType.INTEGER);
        TYPE_MAPPING.put(Long.class.getName(), ToscaType.INTEGER);
        TYPE_MAPPING.put(Float.class.getName(), ToscaType.FLOAT);
        TYPE_MAPPING.put(Double.class.getName(), ToscaType.FLOAT);
        TYPE_MAPPING.put(Duration.class.getName(), ToscaType.TIME);
        TYPE_MAPPING.put(List.class.getName(), ToscaType.LIST);
        TYPE_MAPPING.put(Map.class.getName(), ToscaType.MAP);
    }

    private ArchiveIndexer archiveIndexer;
    private ArchiveImageLoader imageLoader;
    private ICsarRepositry archiveRepository;
    private CsarService csarService;
    private ManagedPlugin selfContext;
    private ArchiveParser archiveParser;


    @Autowired
    public BrooklynCatalogMapper(ArchiveIndexer archiveIndexer, ArchiveImageLoader imageLoader, ICsarRepositry archiveRepository, CsarService csarService, ManagedPlugin selfContext, ArchiveParser archiveParser) {
        this.archiveIndexer = archiveIndexer;
        this.imageLoader = imageLoader;
        this.archiveRepository = archiveRepository;
        this.csarService = csarService;
        this.selfContext = selfContext;
        this.archiveParser = archiveParser;
    }

    public void addBaseTypes(){
        Path archivePath = selfContext.getPluginPath().resolve("brooklyn/brooklyn-resources");
        // Parse the archives
        try {
            ParsingResult<ArchiveRoot> result = archiveParser.parseDir(archivePath);
            ArchiveRoot root = result.getResult();
            Csar csar = root.getArchive();
            csarService.save(csar);
            archiveIndexer.indexArchive(csar.getName(), csar.getVersion(), root, true);

        } catch(ParsingException e) {
            log.error("Failed to parse archive", e);
        }
    }

    public void mapBrooklynEntities(BrooklynApi brooklynApi) {
        ArchiveRoot archiveRoot = new ArchiveRoot();
        // Brooklyn actually depends on normative types and alien types
        archiveRoot.getArchive().setToscaDefinitionsVersion("tosca_simple_yaml_1_0_0_wd03");
        // TODO need a uid or a brooklyn server identifier
        archiveRoot.getArchive().setName("brooklyn-types-autoimport");
        
        String brooklynVersion = brooklynApi.getServerApi().getVersion().getVersion();
        archiveRoot.getArchive().setVersion(brooklynVersion);
        archiveRoot.getArchive().setTemplateAuthor("A4C-Brooklyn auto-import");
        archiveRoot.getArchive().setDescription("Mapping types out of brooklyn to Alien 4 Cloud");

        archiveRoot.getArchive().setDependencies(
                Sets.newHashSet(
                    new CSARDependency("tosca-normative-types", "1.0.0.wd06-SNAPSHOT"),
                    new CSARDependency("alien4cloud-tomcat-types", "1.0.0-SNAPSHOT"),
                    new CSARDependency("brooklyn-types", "0.1.0-SNAPSHOT")));

        // TODO Not great way to go but that's a POC for now ;)
        List<CatalogEntitySummary> entities = brooklynApi.getCatalogApi().listEntities(null, null, false);
        for (CatalogEntitySummary entity: entities) {
            mapBrooklynEntity(brooklynApi, archiveRoot, entity.getSymbolicName(), entity.getVersion());
        }
        
        // this is what ArchiveUploadService does:
        csarService.save(archiveRoot.getArchive());
        // save the archive in the repository
//        archiveRepository.storeCSAR(archiveRoot.getArchive().getName(), archiveRoot.getArchive().getVersion(), Path);
        // manage images before archive storage in the repository
//        imageLoader.importImages(path, parsingResult);
        
        archiveIndexer.indexArchive(archiveRoot.getArchive().getName(), archiveRoot.getArchive().getVersion(), archiveRoot, true);
    }

    public void mapBrooklynEntity(BrooklynApi brooklynApi, ArchiveRoot archiveRoot, String entityName, String entityVersion) {
        try {
            IndexedNodeType toscaType = new IndexedNodeType();
            CatalogEntitySummary brooklynEntity = loadEntity(brooklynApi, entityName);

            // TODO use icon
            // tomcatEntity.getIconUrl()

            toscaType.setElementId(brooklynEntity.getSymbolicName());

            toscaType.setArchiveName(archiveRoot.getArchive().getName());
            toscaType.setArchiveVersion(archiveRoot.getArchive().getVersion());
            // TODO types are versioned separately to brooklyn version -
            // archive is set as brooklynVersion, whereas the node type should have some kind of entityVersion
//            toscaType.setNodeTypeVersion(entityVersion); ???
            
            addPropertyDefinitions(brooklynEntity, toscaType);
            addAttributeDefinitions(brooklynEntity, toscaType);
            addInterfaces(brooklynEntity, toscaType);
            addRequirements(brooklynEntity, toscaType);
            addCapabilities(brooklynEntity, toscaType);

            String derivedFrom = "brooklyn.nodes.SoftwareProcess";
            for (Object tag : brooklynEntity.getRawTags()) {
                Map<String, ?> tagMap = (Map<String, ?>) tag;
                if (!tagMap.containsKey("derivedFrom")) continue;
                derivedFrom = String.valueOf(tagMap.get("derivedFrom"));
            }
            toscaType.setDerivedFrom(Arrays.asList(derivedFrom));

            archiveRoot.getNodeTypes().put(brooklynEntity.getSymbolicName(), toscaType);

        } catch (Exception e) {
            log.error("Failed auto-import: "+entityName+"; ignoring", e);
        }
    }

    @SuppressWarnings("deprecation")
    private CatalogEntitySummary loadEntity(BrooklynApi brooklynApi, String entityName) throws Exception {
        // deprecated method doesn't require version to be set
        return brooklynApi.getCatalogApi().getEntity(entityName);
    }

    private void addPropertyDefinitions(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        Set<EntityConfigSummary> entityConfigSummaries = brooklynEntity.getConfig(); // properties in TOSCA
        Map<String, PropertyDefinition> properties = Maps.newHashMap();
        toscaType.setProperties(properties);
        if (entityConfigSummaries == null) {
            return;
        }
        for (EntityConfigSummary entityConfigSummary : entityConfigSummaries) {
            String propertyType = TYPE_MAPPING.get(entityConfigSummary.getType());
            if (propertyType == null) {
                log.warn("Skipping entityConfigSummary {} as type {} is not recognized", entityConfigSummary, entityConfigSummary.getType());
            } else {
                PropertyDefinition propertyDefinition = new PropertyDefinition();
                propertyDefinition.setDescription(entityConfigSummary.getDescription());
                propertyDefinition.setType(propertyType);
                if (entityConfigSummary.getDefaultValue() != null) {
                    propertyDefinition.setDefault(entityConfigSummary.getDefaultValue().toString());
                }
                if (ToscaType.MAP.equals(propertyType)) {
                    PropertyDefinition mapDefinition = new PropertyDefinition();
                    // TODO: More complex map types. Unfortunately the type is not available from EntityConfigSummary
                    mapDefinition.setType(ToscaType.STRING);
                    propertyDefinition.setEntrySchema(mapDefinition);
                }
                propertyDefinition.setRequired(false);
                toscaType.getProperties().put(entityConfigSummary.getName(), propertyDefinition);
            }
        }
    }

    private void addAttributeDefinitions(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        Set<SensorSummary> sensorSummaries = brooklynEntity.getSensors();
        Map<String, IValue> attributes = Maps.newHashMap();
        toscaType.setAttributes(attributes);
        for (SensorSummary sensorSummary : sensorSummaries) {
            String attributeType = TYPE_MAPPING.get(sensorSummary.getType());
            if (attributeType == null) {
                log.warn("Skipping sensorSummary {} as type {} is not recognized", sensorSummary, sensorSummary.getType());
            } else {
                AttributeDefinition attributeDefinition = new AttributeDefinition();
                attributeDefinition.setType(attributeType);
                attributeDefinition.setDescription(sensorSummary.getDescription());
                toscaType.getAttributes().put(sensorSummary.getName(), attributeDefinition);
            }
        }
    }

    private void addInterfaces(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        Set<EffectorSummary> effectorSummaries = brooklynEntity.getEffectors();

        Interface interfaz = new Interface();
        interfaz.setDescription("Brooklyn effectors management operations.");
        Map<String, Operation> operationMap = Maps.newHashMap();
        for (EffectorSummary effectorSummary : effectorSummaries) {
            Operation operation = new Operation();
            operation.setDescription(effectorSummary.getDescription());
            Set<EffectorSummary.ParameterSummary<?>> parameterSummaries = effectorSummary.getParameters();

            Map<String, IValue> inputs = Maps.newHashMap();
            operation.setInputParameters(inputs);
            for (EffectorSummary.ParameterSummary<?> parameterSummary : parameterSummaries) {
                String parameterType = TYPE_MAPPING.get(parameterSummary.getType());
                if (parameterType == null) {
                    log.warn("Skipping parameterType as type is not recognized", parameterSummary, parameterSummary.getType());
                } else {
                    PropertyDefinition propertyDefinition = new PropertyDefinition();
                    propertyDefinition.setType(parameterType);
                    propertyDefinition.setDescription(parameterSummary.getDescription());
                    if(parameterSummary.getDefaultValue()!=null) {
                        propertyDefinition.setDefault(parameterSummary.getDefaultValue().toString());
                    }
                    operation.getInputParameters().put(parameterSummary.getName(), propertyDefinition);
                }

            }

            operationMap.put(effectorSummary.getName(), operation);
        }

        Map<String, Interface> interfaces = Maps.newHashMap();
        toscaType.setInterfaces(interfaces);
        interfaces.put("brooklyn_management", interfaz);
    }

    private void addRequirements(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        for (Object tag : brooklynEntity.getRawTags()) {
            Map<String, ?> tagMap = (Map<String, ?>) tag;
            if (!tagMap.containsKey("requirements")) continue;
            List<RequirementDefinition> requirementDefinitions = MutableList.of();
            List<Map<String, ?>> requirements = (List<Map<String, ?>>) tagMap.get("requirements");
            for (Map<String, ?> requirement : requirements) {
                RequirementDefinition requirementDefinition = new RequirementDefinition(requirement.get("id").toString(), requirement.get("targetType").toString());
                requirementDefinition.setRelationshipType(requirement.get("relationshipType").toString());
                if (requirement.containsKey("lowerBound")) {
                    requirementDefinition.setLowerBound((Integer) requirement.get("lowerBound"));
                }
                if (requirement.containsKey("upperBound")) {
                    requirementDefinition.setUpperBound((Integer) requirement.get("upperBound"));
                }
                requirementDefinitions.add(requirementDefinition);
            }
            toscaType.setRequirements(requirementDefinitions);
        }
    }

    private void addCapabilities(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        for (Object tag : brooklynEntity.getRawTags()) {
            Map<String, ?> tagMap = (Map<String, ?>) tag;
            if (!tagMap.containsKey("capabilities")) continue;
            List<CapabilityDefinition> capabilityDefinitions = MutableList.of();
            List<Map<String, ?>> capabilities = (List<Map<String, ?>>) tagMap.get("capabilities");
            for (Map<String, ?> capability : capabilities) {
                capabilityDefinitions.add(new CapabilityDefinition(capability.get("id").toString(), capability.get("type").toString(), (Integer) capability.get("upperBound")));
            }
            toscaType.setCapabilities(capabilityDefinitions);
        }
    }
}
