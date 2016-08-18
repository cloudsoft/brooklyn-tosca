package alien4cloud.brooklyn;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.CapabilityDefinition;
import alien4cloud.model.components.Csar;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.RequirementDefinition;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import lombok.extern.slf4j.Slf4j;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogItemSummary;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import alien4cloud.csar.services.CsarService;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.model.ArchiveRoot;
import io.cloudsoft.tosca.metadata.ToscaMetadataProvider;

/**
 * This component is used to map components out of brooklyn to a4c.
 * 
 * TODO - operation in Brooklyn to export types to A4C, then use existing import mechanism in A4C
 */
@Component
@Slf4j
public abstract class BrooklynCatalogMapper<T extends CatalogItemSummary> {
    public static final String TOSCA_NORMATIVE_TYPES_VERSION = "1.0.0.wd06-SNAPSHOT";

    final static Map<String, String> TYPE_MAPPING = Maps.newHashMap();

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

    protected ArchiveIndexer archiveIndexer;
    protected CsarService csarService;
    protected ManagedPlugin selfContext;
    protected ArchiveParser archiveParser;


    @Autowired
    public BrooklynCatalogMapper(ArchiveIndexer archiveIndexer, CsarService csarService, ManagedPlugin selfContext,
            ArchiveParser archiveParser) {
        this.archiveIndexer = archiveIndexer;
        this.csarService = csarService;
        this.selfContext = selfContext;
        this.archiveParser = archiveParser;
    }

    // TODO move to a separate brooklyn types provider
    public void addBaseTypes(){
        Path archivePath = selfContext.getPluginPath().resolve("brooklyn/types");
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

    public void mapBrooklynEntities(BrooklynApi brooklynApi, ToscaMetadataProvider metadataProvider) {
        ArchiveRoot archiveRoot = new ArchiveRoot();
        // Brooklyn actually depends on normative types and alien types
        archiveRoot.getArchive().setToscaDefinitionsVersion("tosca_simple_yaml_1_0_0_wd03");
        // TODO need a uid or a brooklyn server identifier
        archiveRoot.getArchive().setName(getToscaArchiveName());
        
        String brooklynVersion = brooklynApi.getServerApi().getVersion().getVersion();
        archiveRoot.getArchive().setVersion(brooklynVersion);
        archiveRoot.getArchive().setTemplateAuthor("A4C-Brooklyn auto-import");
        archiveRoot.getArchive().setDescription("Mapping types out of brooklyn to Alien 4 Cloud");

        archiveRoot.getArchive().setDependencies(
                Sets.newHashSet(
                    new CSARDependency("tosca-normative-types", TOSCA_NORMATIVE_TYPES_VERSION),
                    new CSARDependency("alien4cloud-tomcat-types", "1.0.0-SNAPSHOT"),
                    new CSARDependency("brooklyn-types", "0.1.0-SNAPSHOT")));

        // TODO Not great way to go but that's a POC for now ;)
        List<T> entities = retrieveCatalogItemSummary(brooklynApi);
        for (T catalogItem: entities) {
            mapBrooklynCatalogItem(brooklynApi, archiveRoot, catalogItem.getSymbolicName(), catalogItem.getVersion(), metadataProvider);
        }
        
        // this is what ArchiveUploadService does:
        csarService.save(archiveRoot.getArchive());
        // save the archive in the repository
//        archiveRepository.storeCSAR(archiveRoot.getArchive().getName(), archiveRoot.getArchive().getVersion(), Path);
        // manage images before archive storage in the repository
//        imageLoader.importImages(path, parsingResult);
        
        archiveIndexer.indexArchive(archiveRoot.getArchive().getName(), archiveRoot.getArchive().getVersion(), archiveRoot, true);
    }

    protected abstract List<T> retrieveCatalogItemSummary(BrooklynApi brooklynApi);

    protected abstract String getToscaArchiveName();

    protected void makeAdditionalBrooklynCatalogToToscaMappings(T catalogItem, IndexedNodeType indexedNodeType) {
    }

    public void mapBrooklynCatalogItem(BrooklynApi brooklynApi, ArchiveRoot archiveRoot, String catalogItemName, String entityVersion, ToscaMetadataProvider metadataProvider) {
        try {
            IndexedNodeType toscaType = new IndexedNodeType();
            CatalogItemSummary brooklynEntity = loadEntity(brooklynApi, catalogItemName);

            // TODO use icon
            // tomcatEntity.getIconUrl()

            toscaType.setElementId(brooklynEntity.getSymbolicName());

            toscaType.setArchiveName(archiveRoot.getArchive().getName());
            toscaType.setArchiveVersion(archiveRoot.getArchive().getVersion());
            // TODO types are versioned separately to brooklyn version -
            // archive is set as brooklynVersion, whereas the node type should have some kind of entityVersion
//            toscaType.setNodeTypeVersion(entityVersion); ???

            makeAdditionalBrooklynCatalogToToscaMappings((T)brooklynEntity, toscaType);

            Optional<String> derivedFrom = metadataProvider.getToscaType(brooklynEntity.getType(), brooklynEntity.getVersion());
            if (derivedFrom.isPresent()) {
                toscaType.setDerivedFrom(ImmutableList.of(derivedFrom.get()));
            }
            addRequirements(brooklynEntity, toscaType);
            addCapabilities(brooklynEntity, toscaType);

            archiveRoot.getNodeTypes().put(brooklynEntity.getSymbolicName(), toscaType);

        } catch (Exception e) {
            log.error("Failed auto-import: "+catalogItemName+"; ignoring", e);
        }
    }

    @SuppressWarnings("deprecation")
    private CatalogItemSummary loadEntity(BrooklynApi brooklynApi, String entityName) throws Exception {
        return brooklynApi.getCatalogApi().getEntity(entityName, BrooklynCatalog.DEFAULT_VERSION);
    }

    private void addRequirements(CatalogItemSummary brooklynEntity, IndexedNodeType toscaType) {
        for (Object tag : brooklynEntity.getTags()) {
            Map<String, ?> tagMap = (Map<String, ?>) tag;
            if (!tagMap.containsKey("tosca:requirements")) continue;
            List<RequirementDefinition> requirementDefinitions = MutableList.of();
            List<Map<String, ?>> requirements = (List<Map<String, ?>>) tagMap.get("tosca:requirements");
            for (Map<String, ?> requirement : requirements) {
                RequirementDefinition requirementDefinition = new RequirementDefinition(requirement.get("id").toString(), requirement.get("capabilityType").toString());
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

    private void addCapabilities(CatalogItemSummary brooklynEntity, IndexedNodeType toscaType) {
        for (Object tag : brooklynEntity.getTags()) {
            Map<String, ?> tagMap = (Map<String, ?>) tag;
            if (!tagMap.containsKey("tosca:capabilities")) continue;
            List<CapabilityDefinition> capabilityDefinitions = MutableList.of();
            List<Map<String, ?>> capabilities = (List<Map<String, ?>>) tagMap.get("tosca:capabilities");
            for (Map<String, ?> capability : capabilities) {
                capabilityDefinitions.add(new CapabilityDefinition(capability.get("id").toString(), capability.get("type").toString(), (Integer) capability.get("upperBound")));
            }
            toscaType.setCapabilities(capabilityDefinitions);
        }
    }
}
