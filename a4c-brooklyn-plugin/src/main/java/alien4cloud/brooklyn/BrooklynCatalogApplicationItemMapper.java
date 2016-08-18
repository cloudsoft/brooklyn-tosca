package alien4cloud.brooklyn;

import alien4cloud.csar.services.CsarService;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.ArchiveParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogItemSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by valentin on 18/08/16.
 */
@Component
@Slf4j
public class BrooklynCatalogApplicationItemMapper extends BrooklynCatalogMapper<CatalogItemSummary> {
    @Autowired
    public BrooklynCatalogApplicationItemMapper(ArchiveIndexer archiveIndexer, CsarService csarService, ManagedPlugin selfContext,
                                                ArchiveParser archiveParser) {
        super(archiveIndexer, csarService, selfContext, archiveParser);
    }

    @Override
    public String getToscaArchiveName() {
        return "brooklyn-catalog-application-types-autoimport";
    }

    @Override
    protected List<CatalogItemSummary> retrieveCatalogItemSummary(BrooklynApi brooklynApi) {
        return brooklynApi.getCatalogApi().listApplications(null, null, false);
    }
}
