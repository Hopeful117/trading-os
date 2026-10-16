package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactCacheKey;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFreshness;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactProvenance;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactScope;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement;
import com.hope.trading.market_intelligence.domain.capability.CapabilityContext;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.orderbook.OrderBookLiquidityFeatureSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookLiquidityCapabilityTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEQUENCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CUTOFF = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void producesVersionedArtifactAndAvailableMetrics() {
        OrderBookLiquidityCapability capability = new OrderBookLiquidityCapability();
        ArtifactRequirement requirement = capability.metadata().requirements().getFirst();
        StoredArtifact input = input(context());

        var result = capability.execute(new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(input)),
                Set.of(), Map.of(), List.of(), () -> false));

        assertThat(result.artifacts()).singleElement().satisfies(value -> {
            ProducedArtifact artifact = value;
            assertThat(artifact.artifact().content()).isInstanceOf(OrderBookLiquidityFeatureSet.class);
            assertThat(artifact.version()).isEqualTo(ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY_V1);
            OrderBookLiquidityFeatureSet content = (OrderBookLiquidityFeatureSet) artifact.artifact().content();
            assertThat(content.latestOccurredAt()).isEqualTo(CUTOFF.minusSeconds(1));
            assertThat(content.sourceStartOrdinal()).isZero();
            assertThat(content.sourceEndOrdinal()).isEqualTo(1L);
            assertThat(content.normalizationVersion()).isEqualTo("order-book-normalization-v1");
        });
        assertThat(result.metrics()).containsEntry("liquidity.spread_absolute", new BigDecimal("1"));
        assertThat(result.completeness().name()).isEqualTo("COMPLETE");
    }

    @Test
    void degradesWhenReplayArtifactIsMissing() {
        OrderBookLiquidityCapability capability = new OrderBookLiquidityCapability();

        var result = capability.execute(new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(), Set.of(), Map.of(), List.of(), () -> false));

        assertThat(result.artifacts()).isEmpty();
        assertThat(result.diagnostics()).containsExactly("Order Book replay is unavailable");
    }

    private OrderBookContext context() {
        return context(List.of(new OrderBookContext.Level(new BigDecimal("100"), BigDecimal.ONE)),
                List.of(new OrderBookContext.Level(new BigDecimal("101"), BigDecimal.ONE)));
    }

    private OrderBookContext context(List<OrderBookContext.Level> bids, List<OrderBookContext.Level> asks) {
        return new OrderBookContext(MARKET_ID, 25, OrderBookContext.ReplayStatus.AVAILABLE,
                SEQUENCE_ID, CUTOFF, CUTOFF.minusSeconds(1), CUTOFF,
                0L, 1L, "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1", "evidence",
                bids, asks, null);
    }

    private StoredArtifact input(OrderBookContext content) {
        return new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.ORDER_BOOK_REPLAY.value(),
                                "market-data", ProductionArtifactTypes.V1.value()),
                        ArtifactScope.publicMarket(MARKET_ID, "ORDER_BOOK", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.ofInputs(List.of("input"))),
                content,
                ArtifactFreshness.validUntil(CUTOFF.minusSeconds(1), CUTOFF.plusSeconds(60), "replay"),
                new ArtifactProvenance("market-data", "replay", null, CUTOFF.minusSeconds(1), Set.of(), Set.of())
                        .withContextFingerprint("context")
                        .withEvidenceFingerprint("evidence"),
                AnalysisResultQuality.COMPLETE, ProductionArtifactTypes.V1);
    }
}
