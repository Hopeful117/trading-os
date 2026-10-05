package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.observation.ObservationRuleResult;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OhlcTrendObservationRuleTest {
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void createsNeutralObservationWhenPriceChangeIsZero() {
        ObservationRuleResult result = new OhlcTrendObservationRule().evaluate("BTC/EUR",
                List.of(execution(new BigDecimal("0"))));

        assertThat(result.type().value()).isEqualTo("PRICE_TREND_NEUTRAL");
        assertThat(result.evidence()).hasSize(1);
    }

    @Test
    void createsDirectionalObservationForPositiveAndNegativeChanges() {
        ObservationRuleResult rising = new OhlcTrendObservationRule().evaluate("BTC/EUR",
                List.of(execution(new BigDecimal("1.2"))));
        ObservationRuleResult falling = new OhlcTrendObservationRule().evaluate("BTC/EUR",
                List.of(execution(new BigDecimal("-1.2"))));

        assertThat(rising.type().value()).isEqualTo("PRICE_TREND_LONG");
        assertThat(falling.type().value()).isEqualTo("PRICE_TREND_SHORT");
    }

    @Test
    void rejectsIncompleteOrMalformedResults() {
        CapabilityExecution incomplete = execution(new BigDecimal("1"), CapabilityCompleteness.PARTIAL);
        assertThatThrownBy(() -> new OhlcTrendObservationRule().evaluate("BTC/EUR", List.of(incomplete)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        CapabilityExecution withoutChange = execution(null);
        assertThatThrownBy(() -> new OhlcTrendObservationRule().evaluate("BTC/EUR", List.of(withoutChange)))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("price change");
    }

    private CapabilityExecution execution(BigDecimal change) {
        return execution(change, CapabilityCompleteness.COMPLETE);
    }

    private CapabilityExecution execution(BigDecimal change, CapabilityCompleteness completeness) {
        UUID analysisId = UUID.randomUUID();
        CapabilityMetadata metadata = new CapabilityMetadata(
                new CapabilityId(OhlcRangeAnalysisCapability.CAPABILITY_ID),
                new CapabilityVersion("1.0.0"), CapabilityCategory.DETERMINISTIC,
                ExecutionPolicy.ON_DEMAND, RetryPolicy.disabled(), List.of(), List.of(),
                Duration.ofSeconds(1), null);
        CapabilityExecution running = CapabilityExecution.created(analysisId, metadata, NOW)
                .transitionTo(CapabilityExecutionState.READY, NOW)
                .transitionTo(CapabilityExecutionState.RUNNING, NOW);
        Map<String, BigDecimal> metrics = change == null ? Map.of() : Map.of("priceChange", change);
        DeterministicMeasurements measurements = new DeterministicMeasurements(
                "Historical price range", "OHLC explanation", metrics, NOW);
        ArtifactCacheKey key = new ArtifactCacheKey(
                new ArtifactIdentity("OHLC_RANGE_ANALYSIS", OhlcRangeAnalysisCapability.CAPABILITY_ID, "1.0.0"),
                new ArtifactScope(UUID.randomUUID(), "BTC/EUR", "15m", null, null, null,
                        AnalysisExecutionMode.ACTIVE, com.hope.trading.market_intelligence.domain.context.ContextClassification.PUBLIC),
                ArtifactFingerprint.empty(), ArtifactFingerprint.empty());
        StoredArtifact artifact = new StoredArtifact(key, measurements,
                ArtifactFreshness.validUntil(NOW, NOW.plusSeconds(60), "v1"),
                new ArtifactProvenance(OhlcRangeAnalysisCapability.CAPABILITY_ID, "1.0.0",
                        running.id(), NOW, Set.of(), Set.of()), AnalysisResultQuality.COMPLETE);
        CapabilityResult result = new CapabilityResult(List.of(),
                List.of(new ProducedArtifact(new ArtifactType("OHLC_RANGE_ANALYSIS"),
                        new ArtifactVersion("1"), artifact)), metrics, List.of(), completeness);
        return running.complete(result, NOW);
    }
}
