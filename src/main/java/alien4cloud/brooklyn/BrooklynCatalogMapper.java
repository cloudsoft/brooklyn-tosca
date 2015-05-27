package alien4cloud.brooklyn;

import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.AttributeDefinition;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.model.ArchiveRoot;
import brooklyn.rest.client.BrooklynApi;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.util.time.Duration;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This component is used to map components out of brooklyn to a4c.
 * 
 * TODO - operation in Brooklyn to export types to A4C, then use existing import mechanism in A4C
 */
@Component
@Slf4j
public class BrooklynCatalogMapper {
    private final static Map<String, String> TYPE_MAPPING = Maps.newHashMap();

    @Autowired
    private ArchiveIndexer archiveIndexer;

    public BrooklynCatalogMapper() {
        TYPE_MAPPING.put(Boolean.class.getName(), "boolean");
        TYPE_MAPPING.put(String.class.getName(), "string");
        TYPE_MAPPING.put(Integer.class.getName(), "integer");
        TYPE_MAPPING.put(Long.class.getName(), "integer");
        TYPE_MAPPING.put(Float.class.getName(), "float");
        TYPE_MAPPING.put(Double.class.getName(), "float");
        TYPE_MAPPING.put(Duration.class.getName(), "scalar-unit.time");
    }

    public void mapBrooklynEntity(BrooklynApi brooklynApi, String entityName, String entityVersion) {
        ArchiveRoot archiveRoot = new ArchiveRoot();
        // Brooklyn actually depends on normative types and alien types
        archiveRoot.getArchive().setToscaDefinitionsVersion("tosca_simple_yaml_1_0_0_wd03");
        archiveRoot.getArchive().setName("brooklyn-types");
        archiveRoot.getArchive().setVersion("1.0.0-SNAPSHOT");
        archiveRoot.getArchive().setTemplateAuthor("Alien4cloud Brooklyn");
        archiveRoot.getArchive().setDescription("Mapping types out of brooklyn to Alien 4 Cloud");

        archiveRoot.getArchive().setDependencies(
                Sets.newHashSet(new CSARDependency("tosca-normative-types", "1.0.0.wd03-SNAPSHOT"), new CSARDependency("alien4cloud-tomcat-types",
                        "1.0.0-SNAPSHOT")));

        try {
            IndexedNodeType tomcatType = new IndexedNodeType();
            CatalogEntitySummary tomcatEntity = loadEntity(brooklynApi, entityName);

            // tomcatEntity.getIconUrl()

            tomcatType.setElementId(tomcatEntity.getId());
            tomcatType.setArchiveVersion(archiveRoot.getArchive().getVersion());

            addPropertyDefinitions(tomcatEntity, tomcatType);
            addAttributeDefinitions(tomcatEntity, tomcatType);
            addInterfaces(tomcatEntity, tomcatType);

            // override the host requirement in order to say that none is required.
            //tomcatType.getRequirements().add()

            archiveRoot.getNodeTypes().put(tomcatEntity.getId(), tomcatType);

            archiveIndexer.indexArchive(archiveRoot.getArchive().getName(), archiveRoot.getArchive().getVersion(), archiveRoot, true);
        } catch (Exception e) {
            e.printStackTrace();
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
        for (EntityConfigSummary entityConfigSummary : entityConfigSummaries) {
            String propertyType = TYPE_MAPPING.get(entityConfigSummary.getType());
            if (propertyType == null) {
                log.warn("Skipping entityConfigSummary as type is not recognized", entityConfigSummary, entityConfigSummary.getType());
            } else {
                PropertyDefinition propertyDefinition = new PropertyDefinition();
                propertyDefinition.setDescription(entityConfigSummary.getDescription());
                propertyDefinition.setType(propertyType);
                if (entityConfigSummary.getDefaultValue() != null) {
                    propertyDefinition.setDefault(entityConfigSummary.getDefaultValue().toString());
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
                log.warn("Skipping sensorSummary as type is not recognized", sensorSummary, sensorSummary.getType());
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
}
