package io.cloudsoft.tosca.metadata;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class ToscaMetadataProviderTest {

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
    public void testBrooklynTypeProvider() {
        ToscaMetadataProvider tmp = new ToscaMetadataProvider(ImmutableList.<ToscaTypeProvider>of(
                new BrooklynToscaTypeProvider()));
        assertEquals(tmp.getToscaType(MySqlNode.class.getName(), "1.0").get(), "brooklyn.nodes.Database");
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
