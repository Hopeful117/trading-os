package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.MarketSnapshotContext;
import com.hope.trading.market_intelligence.domain.capability.CapabilityContext;
import com.hope.trading.market_intelligence.domain.capability.CancellationToken;
import com.hope.trading.market_intelligence.domain.capability.ArtifactCardinality;
import com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement;
import com.hope.trading.market_intelligence.domain.capability.VersionCompatibilityMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeabilityAnalysisCapabilityTest {
    @Test
    void rejectsLiveCurrentWithoutAnApprovedLiveOrderBookSource() {
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "live");

        var result = new TradeabilityAnalysisCapability().analyze(request, null);

        assertThat(result.findings()).isEmpty();
        assertThat(result.warnings()).singleElement()
                .isEqualTo("Tradeability requires an approved live order-book source; LIVE_CURRENT is unsupported");
    }

    @Test
    void rejectsArtifactExecutionWithoutEvidenceProvenance() {
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(), Set.of(), Map.of(), List.of(),
                (CancellationToken) () -> false, Instant.parse("2026-10-25T00:00:00Z"));

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires approved non-live evidence provenance at the execution cutoff");
    }

    @Test
    void rejectsArtifactExecutionWithAMismatchedProvenanceCutoff() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-25T00:00:00Z");
        StoredArtifact artifact = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.MARKET_SNAPSHOT.value(), "market-data", "v1"),
                        ArtifactScope.publicMarket(marketId, "SNAPSHOT", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.empty()),
                new MarketSnapshotContext(marketId, "BTC/EUR", BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, true, cutoff),
                ArtifactFreshness.validUntil(cutoff, cutoff.plusSeconds(900), "v1"),
                new ArtifactProvenance("market-data", "v1", null, cutoff, Set.of(), Set.of(),
                        null, null, EvidenceAcquisitionPolicy.STRICT_OBSERVED, 1, cutoff.plusSeconds(60)),
                AnalysisResultQuality.COMPLETE);
        ArtifactRequirement requirement = new ArtifactRequirement(
                ProductionArtifactTypes.MARKET_SNAPSHOT, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, false, ArtifactCardinality.ONE, false);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(artifact)), Set.of(), Map.of(),
                List.of(artifact.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires approved non-live evidence provenance at the execution cutoff");
    }
}
