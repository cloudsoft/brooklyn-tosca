package alien4cloud.brooklyn;

import alien4cloud.csar.services.CsarService;
import alien4cloud.model.components.*;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.normative.ToscaType;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.*;
import org.apache.brooklyn.util.time.Duration;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by valentin on 18/08/16.
 */
@Component
@Slf4j
public class BrooklynCatalogEntityItemMapper extends BrooklynCatalogMapper<CatalogEntitySummary> {
    @Autowired
    public BrooklynCatalogEntityItemMapper(ArchiveIndexer archiveIndexer, CsarService csarService, ManagedPlugin selfContext,
                                           ArchiveParser archiveParser) {
        super(archiveIndexer, csarService, selfContext, archiveParser);
    }

    @Override
    protected List<CatalogEntitySummary> retrieveCatalogItemSummary(BrooklynApi brooklynApi) {
        return brooklynApi.getCatalogApi().listEntities(null, null, false);
    }

    @Override
    protected void makeAdditionalBrooklynCatalogToToscaMappings(CatalogEntitySummary brooklynEntity, IndexedNodeType toscaType) {
        addPropertyDefinitions(brooklynEntity, toscaType);
        addAttributeDefinitions(brooklynEntity, toscaType);
        addInterfaces(brooklynEntity, toscaType);
    }

    @Override
    public String getToscaArchiveName() {
        return "brooklyn-catalog-entity-types-autoimport";
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
                    if (propertyType.equals(ToscaType.TIME)) {
                        propertyDefinition.setDefault(Duration.of(entityConfigSummary.getDefaultValue()).toSeconds() + " s");
                    } else {
                        propertyDefinition.setDefault(entityConfigSummary.getDefaultValue().toString());
                    }
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
}
