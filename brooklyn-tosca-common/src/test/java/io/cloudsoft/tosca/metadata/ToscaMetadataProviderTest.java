package io.cloudsoft.tosca.metadata;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Collection;

import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.rest.api.CatalogApi;
import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ToscaMetadataProviderTest {

    private static final Collection<Object> TAGS = ImmutableList.<Object>of(ImmutableMap.of(
            "traits", ImmutableList.of("org.apache.brooklyn.entity.webapp.WebAppService")
    ));
    @Mock
    private BrooklynApi brooklynApi;
    @Mock
    private CatalogApi catalogApi;
    @Mock
    private CatalogEntitySummary catalogEntitySummary;

    @BeforeClass
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOrdering() {
        ToscaMetadataProvider tmp = new ToscaMetadataProvider(ImmutableList.of(
                new AlwaysBobTypeProvider(),
                new BrooklynToscaTypeProvider(),
                new AlwaysAbsentTypeProvider()));
        assertEquals(tmp.getToscaType("bib", "1.0").get(), "bob");
    }

    @Test
    public void testNoGoodType() {
        ToscaMetadataProvider tmp = new ToscaMetadataProvider(ImmutableList.<ToscaTypeProvider>of(
                new AlwaysAbsentTypeProvider()));
        assertFalse(tmp.getToscaType("xyz", "1.0").isPresent());
    }

    @Test
    public void testBrooklynTypeProvider() throws Exception {
        when(brooklynApi.getCatalogApi()).thenReturn(catalogApi);
        when(catalogApi.getEntity(Mockito.anyString(), Mockito.anyString())).thenReturn(catalogEntitySummary);
        when(catalogEntitySummary.getTags()).thenReturn(TAGS);
        BrooklynToscaTypeProvider provider = new BrooklynToscaTypeProvider();
        provider.setBrooklynApi(brooklynApi);
        ToscaMetadataProvider tmp = new ToscaMetadataProvider(ImmutableList.<ToscaTypeProvider>of(provider));
        assertEquals(tmp.getToscaType(TomcatServer.class.getName(), "1.0").get(), "brooklyn.nodes.WebServer");
    }

    private static class AlwaysBobTypeProvider implements ToscaTypeProvider {
        @Override
        public Optional<String> getToscaType(String type, String version) {
            return Optional.of("bob");
        }
    }

    private static class AlwaysAbsentTypeProvider implements ToscaTypeProvider {
        @Override
        public Optional<String> getToscaType(String type, String version) {
            return Optional.absent();
        }
    }
}
