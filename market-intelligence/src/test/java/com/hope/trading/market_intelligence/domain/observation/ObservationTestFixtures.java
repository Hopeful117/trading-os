package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ObservationTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    public static CapabilityExecution completed(UUID analysisId, String capabilityId) {
        CapabilityMetadata metadata = new CapabilityMetadata(
                new CapabilityId(capabilityId), new CapabilityVersion("v1"),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.REQUIRED,
                RetryPolicy.disabled(), List.of(), List.of(), Duration.ofSeconds(1), null);
        CapabilityExecution running = CapabilityExecution.created(
                        analysisId, metadata, NOW.minusSeconds(3))
                .transitionTo(CapabilityExecutionState.READY, NOW.minusSeconds(2))
                .transitionTo(CapabilityExecutionState.RUNNING, NOW.minusSeconds(1));
        ArtifactCacheKey key = new ArtifactCacheKey(
                new ArtifactIdentity("SPREAD_ANALYSIS", capabilityId, "v1"),
                new ArtifactScope(
                        UUID.randomUUID(), "BTC/EUR", "5m", null, null, null,
                        AnalysisExecutionMode.PASSIVE,
                        com.hope.trading.market_intelligence.domain.context.ContextClassification.PUBLIC),
                ArtifactFingerprint.ofParameters(Map.of("window", 20)),
                ArtifactFingerprint.ofInputs(List.of("kraken:BTC/EUR:5m:42")));
        StoredArtifact artifact = new StoredArtifact(
                key, new Content(),
                ArtifactFreshness.validUntil(NOW, NOW.plusSeconds(60), "kraken-v1"),
                new ArtifactProvenance(
                        capabilityId, "v1", running.id(), NOW, Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
        CapabilityResult result = new CapabilityResult(
                List.of(), List.of(new ProducedArtifact(
                        new ArtifactType("SPREAD_ANALYSIS"), new ArtifactVersion("v1"), artifact)),
                Map.of("spread", new BigDecimal("0.002")), List.of(),
                CapabilityCompleteness.COMPLETE);
        return running.complete(result, NOW);
    }

    public static ObservationEvidence evidence(BigDecimal contribution) {
        RawMarketDataReference raw = new RawMarketDataReference(
                "kraken", "BTC/EUR", "5m", "abc", NOW);
        ArtifactTrace artifact = new ArtifactTrace(
                new ArtifactIdentity("SPREAD", "spread", "v1"), "p", "i", List.of(raw));
        return new ObservationEvidence(
                UUID.randomUUID(), "spread", "Spread", "Spread below threshold",
                Map.of("spread", new BigDecimal("0.002")),
                Map.of("maximum", new BigDecimal("0.005")),
                NOW, contribution,
                new CapabilityResultTrace(
                        UUID.randomUUID(), "spread", "v1", List.of(artifact)));
    }

    private record Content() implements ArtifactContent {}
    private ObservationTestFixtures() {}
}
