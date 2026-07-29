package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadAnalysisCapabilityTest {
    private final SpreadAnalysisCapability capability = new SpreadAnalysisCapability();

    @Test
    void producesReproducibleExplainableMetrics() {
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                AnalysisExecutionMode.PASSIVE, null
        );
        Instant occurredAt = Instant.parse("2026-07-29T10:00:00Z");
        IntelligenceContext context = context(request.marketId(), occurredAt);

        IntelligenceFinding first = capability.analyze(request, context)
                .findings().getFirst();
        IntelligenceFinding second = capability.analyze(request, context)
                .findings().getFirst();

        assertThat(first.findingId()).isEqualTo(second.findingId());
        assertThat(first.metrics()).isEqualTo(second.metrics());
        assertThat(first.metrics().get("spread")).isEqualByComparingTo("2");
        assertThat(first.metrics().get("spreadPercentage")).isEqualByComparingTo("2");
        assertThat(first.origin()).isEqualTo(AnalysisOrigin.DETERMINISTIC);
        assertThat(first.contextSectionsUsed())
                .containsExactly(ContextSectionType.MARKET_SNAPSHOT);
    }

    private IntelligenceContext context(UUID marketId, Instant occurredAt) {
        MarketSnapshotContext snapshot = new MarketSnapshotContext(
                marketId, "BTC/USD", new BigDecimal("100"),
                new BigDecimal("99"), new BigDecimal("101"), true, occurredAt
        );
        return new IntelligenceContext(Map.of(
                ContextSectionType.MARKET_SNAPSHOT,
                new ContextSection(
                        ContextSectionType.MARKET_SNAPSHOT,
                        ContextSectionStatus.AVAILABLE,
                        ContextSensitivity.PUBLIC,
                        snapshot,
                        new ContextProvenance("market-data", occurredAt, occurredAt),
                        null
                )
        ));
    }
}
