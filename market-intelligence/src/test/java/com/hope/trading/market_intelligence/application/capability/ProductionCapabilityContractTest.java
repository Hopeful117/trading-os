package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCapabilityContractTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void spreadConsumesOnlyNormalizedArtifactAndEmitsCompleteTraceableResult() {
        SpreadAnalysisCapability capability = new SpreadAnalysisCapability();
        UUID executionId = UUID.randomUUID();
        StoredArtifact input = input(
                ProductionArtifactTypes.MARKET_SNAPSHOT,
                new MarketSnapshotContext(UUID.randomUUID(), "BTC/USD",
                        new BigDecimal("100"), new BigDecimal("99"),
                        new BigDecimal("101"), true, NOW));

        CapabilityResult result = capability.execute(context(
                capability.metadata().requirements().getFirst(), input, executionId));

        assertThat(result.completeness()).isEqualTo(CapabilityCompleteness.COMPLETE);
        assertThat(result.metrics().get("spread")).isEqualByComparingTo("2");
        assertThat(result.artifacts()).singleElement().satisfies(produced ->
                assertThat(produced.artifact().provenance().producingExecutionId())
                        .isEqualTo(executionId));
    }

    @Test
    void ohlcProducesDirectionMeasurementAndNoResultFromIncompleteInput() {
        OhlcRangeAnalysisCapability capability = new OhlcRangeAnalysisCapability();
        HistoricalOhlcContext history = new HistoricalOhlcContext(
                UUID.randomUUID(), "FIFTEEN_MINUTES", List.of(
                new OhlcPoint(NOW.minusSeconds(900), NOW,
                        new BigDecimal("90"), new BigDecimal("102"),
                        new BigDecimal("89"), new BigDecimal("100"), BigDecimal.ONE)));

        CapabilityResult result = capability.execute(context(
                capability.metadata().requirements().getFirst(),
                input(ProductionArtifactTypes.OHLC_HISTORY, history), UUID.randomUUID()));
        CapabilityResult empty = capability.execute(context(
                capability.metadata().requirements().getFirst(),
                input(ProductionArtifactTypes.OHLC_HISTORY,
                        new HistoricalOhlcContext(UUID.randomUUID(), "FIFTEEN_MINUTES", List.of())),
                UUID.randomUUID()));

        assertThat(result.metrics().get("priceChange")).isEqualByComparingTo("10");
        assertThat(empty.completeness()).isEqualTo(CapabilityCompleteness.COMPLETE);
        assertThat(empty.artifacts()).isEmpty();
    }

    private CapabilityContext context(
            com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement requirement,
            StoredArtifact input, UUID executionId) {
        return new CapabilityContext(
                UUID.randomUUID(), executionId, Map.of(requirement, List.of(input)),
                Set.of(), Map.of(), List.of(input.provenance()), () -> false);
    }

    private StoredArtifact input(ArtifactType type, ArtifactContent content) {
        UUID marketId = UUID.randomUUID();
        return new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(type.value(), "market-data", "v1"),
                        new ArtifactScope(marketId, "BTC/USD", "15m", null, null, null,
                                AnalysisExecutionMode.ACTIVE, ContextClassification.PUBLIC),
                        ArtifactFingerprint.empty(),
                        ArtifactFingerprint.ofInputs(List.of(content.toString()))),
                content, ArtifactFreshness.validUntil(NOW, NOW.plusSeconds(900), "source-v1"),
                new ArtifactProvenance("market-data", "v1", null, NOW, Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
    }
}
