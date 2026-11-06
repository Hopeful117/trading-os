package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureAvailability;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalStructureArtifactValidatorTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-06T15:45:00Z");

    @Test
    void rejectsMissingStructureArtifact() {
        assertThatThrownBy(() -> HistoricalStructureArtifactValidator.requireAvailable(List.of(), CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("market-structure-v2");
    }

    @Test
    void rejectsUnavailableStructureArtifact() {
        MarketStructureResult result = mock(MarketStructureResult.class);
        when(result.availability()).thenReturn(MarketStructureAvailability.UNAVAILABLE);
        when(result.all()).thenReturn(List.of());
        MarketStructureArtifactContent content = new MarketStructureArtifactContent(result, CUTOFF, "input");
        StoredArtifact stored = mock(StoredArtifact.class);
        when(stored.content()).thenReturn(content);
        ProducedArtifact artifact = new ProducedArtifact(
                ProductionArtifactTypes.MARKET_STRUCTURE,
                ProductionArtifactTypes.MARKET_STRUCTURE_V2,
                stored);

        assertThatThrownBy(() -> HistoricalStructureArtifactValidator.requireAvailable(List.of(artifact), CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
    }
}
