package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.capability.CapabilityContext;
import com.hope.trading.market_intelligence.domain.capability.CancellationToken;
import com.hope.trading.market_intelligence.domain.capability.ArtifactCardinality;
import com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement;
import com.hope.trading.market_intelligence.domain.capability.ArtifactType;
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
    void rejectsStaleStrictObservedSnapshotDuringContextAnalysis() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15Z");
        ContextProvenance provenance = new ContextProvenance(
                "market-data", cutoff.minusSeconds(1), cutoff.minusSeconds(1),
                null, null, null, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                null, 1, cutoff);
        ContextSection snapshot = new ContextSection(
                ContextSectionType.MARKET_SNAPSHOT, ContextSectionStatus.STALE,
                ContextSensitivity.PUBLIC,
                new MarketSnapshotContext(marketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.minusSeconds(1)),
                provenance, "Current market snapshot is stale");
        IntelligenceContext context = new IntelligenceContext(
                marketId, AnalysisExecutionMode.ACTIVE, "context", cutoff, "test",
                ContextQuality.PARTIAL, List.of(), null, Map.of(ContextSectionType.MARKET_SNAPSHOT, snapshot));

        var result = new TradeabilityAnalysisCapability().analyze(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff, EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                context);

        assertThat(result.findings()).isEmpty();
        assertThat(result.warnings()).containsExactly(
                "Tradeability requires a cutoff-compatible market snapshot");
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

    @Test
    void snapshotAbsenceFailsClosedBeforeProducingAnArtifact() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact identity = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.MARKET_IDENTITY.value(), "market-data", "v1"),
                        ArtifactScope.publicMarket(marketId, "IDENTITY", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.empty()),
                new MarketIdentityContext(marketId, "CTRADER", "EUR/USD", "EUR", "USD", true),
                ArtifactFreshness.validUntil(cutoff, cutoff.plusSeconds(900), "v1"),
                new ArtifactProvenance("market-data", "v1", null, cutoff, Set.of(), Set.of(),
                        null, null, EvidenceAcquisitionPolicy.STRICT_OBSERVED, 1, cutoff),
                AnalysisResultQuality.COMPLETE);
        ArtifactRequirement requirement = new ArtifactRequirement(
                ProductionArtifactTypes.MARKET_IDENTITY, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, false, ArtifactCardinality.ONE, false);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(identity)), Set.of(), Map.of(),
                List.of(identity.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires an approved market snapshot");
    }

    @Test
    void validStrictObservedSnapshotReachesCalculatorAndPreservesCanonicalIdentity() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact identity = artifact(
                ProductionArtifactTypes.MARKET_IDENTITY, marketId,
                new MarketIdentityContext(marketId, "CTRADER", "EUR/USD", "EUR", "USD", true), cutoff);
        StoredArtifact snapshot = artifact(
                ProductionArtifactTypes.MARKET_SNAPSHOT, marketId,
                new MarketSnapshotContext(marketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.minusSeconds(1)), cutoff);
        ArtifactRequirement identityRequirement = requirement(ProductionArtifactTypes.MARKET_IDENTITY, false);
        ArtifactRequirement snapshotRequirement = requirement(ProductionArtifactTypes.MARKET_SNAPSHOT, true);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(),
                Map.of(identityRequirement, List.of(identity), snapshotRequirement, List.of(snapshot)), Set.of(),
                Map.of(), List.of(identity.provenance(), snapshot.provenance()),
                (CancellationToken) () -> false, cutoff);

        var result = new TradeabilityAnalysisCapability().execute(context);
        var assessment = ((com.hope.trading.market_intelligence.domain.tradeability.TradeabilityCapabilityContent)
                result.artifacts().getFirst().artifact().content()).assessment();

        assertThat(assessment.instrument()).isEqualTo("EUR/USD");
    }

    @Test
    void rejectsPostCutoffSnapshot() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact snapshot = artifact(ProductionArtifactTypes.MARKET_SNAPSHOT, marketId,
                new MarketSnapshotContext(marketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.plusNanos(1)), cutoff);
        ArtifactRequirement requirement = requirement(ProductionArtifactTypes.MARKET_SNAPSHOT, true);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(snapshot)), Set.of(), Map.of(),
                List.of(snapshot.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires a cutoff-compatible market snapshot");
    }

    @Test
    void rejectsInvalidatedSnapshot() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact snapshot = artifact(ProductionArtifactTypes.MARKET_SNAPSHOT, marketId,
                new MarketSnapshotContext(marketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.minusSeconds(1)), cutoff)
                .invalidate(cutoff);
        ArtifactRequirement requirement = requirement(ProductionArtifactTypes.MARKET_SNAPSHOT, true);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(snapshot)), Set.of(), Map.of(),
                List.of(snapshot.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires a cutoff-compatible market snapshot");
    }

    @Test
    void rejectsSnapshotForAnotherMarket() {
        UUID scopedMarketId = UUID.randomUUID();
        UUID snapshotMarketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact snapshot = artifact(ProductionArtifactTypes.MARKET_SNAPSHOT, scopedMarketId,
                new MarketSnapshotContext(snapshotMarketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.minusSeconds(1)), cutoff);
        ArtifactRequirement requirement = requirement(ProductionArtifactTypes.MARKET_SNAPSHOT, true);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(requirement, List.of(snapshot)), Set.of(), Map.of(),
                List.of(snapshot.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires market-consistent artifacts");
    }

    @Test
    void rejectsIdentityArtifactForAnotherMarket() {
        UUID snapshotMarketId = UUID.randomUUID();
        UUID identityMarketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-29T03:49:15.710432388Z");
        StoredArtifact identity = artifact(
                ProductionArtifactTypes.MARKET_IDENTITY, identityMarketId,
                new MarketIdentityContext(identityMarketId, "CTRADER", "EUR/USD", "EUR", "USD", true), cutoff);
        StoredArtifact snapshot = artifact(ProductionArtifactTypes.MARKET_SNAPSHOT, snapshotMarketId,
                new MarketSnapshotContext(snapshotMarketId, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, true, cutoff.minusSeconds(1)), cutoff);
        ArtifactRequirement identityRequirement = requirement(ProductionArtifactTypes.MARKET_IDENTITY, false);
        ArtifactRequirement snapshotRequirement = requirement(ProductionArtifactTypes.MARKET_SNAPSHOT, true);
        CapabilityContext context = new CapabilityContext(
                UUID.randomUUID(), UUID.randomUUID(),
                Map.of(identityRequirement, List.of(identity), snapshotRequirement, List.of(snapshot)), Set.of(), Map.of(),
                List.of(identity.provenance(), snapshot.provenance()), (CancellationToken) () -> false, cutoff);

        assertThatThrownBy(() -> new TradeabilityAnalysisCapability().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tradeability requires market-consistent artifacts");
    }

    private StoredArtifact artifact(ArtifactType type, UUID marketId, ArtifactContent content, Instant cutoff) {
        return new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(type.value(), "market-data", "v1"),
                        ArtifactScope.publicMarket(marketId, "SNAPSHOT", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.empty()),
                content,
                ArtifactFreshness.validUntil(cutoff.minusSeconds(1), cutoff.plusSeconds(900), "v1"),
                new ArtifactProvenance("market-data", "v1", null, cutoff.minusSeconds(1), Set.of(), Set.of(),
                        null, null, EvidenceAcquisitionPolicy.STRICT_OBSERVED, 1, cutoff),
                AnalysisResultQuality.COMPLETE);
    }

    private ArtifactRequirement requirement(ArtifactType type, boolean required) {
        return new ArtifactRequirement(type, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, required, ArtifactCardinality.ONE, false);
    }
}
