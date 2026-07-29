package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;

import java.time.*;
import java.util.*;

public final class ArtifactTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    public static ArtifactCacheKey key(String version, UUID marketId) {
        return new ArtifactCacheKey(
                new ArtifactIdentity("MARKET_SUMMARY", "spread-analysis", version),
                ArtifactScope.publicMarket(
                        marketId, "5m", AnalysisExecutionMode.PASSIVE
                ),
                ArtifactFingerprint.ofParameters(Map.of("depth", 10, "period", 20)),
                ArtifactFingerprint.ofInputs(List.of("snapshot:42"))
        );
    }

    public static ArtifactScope privateScope(UUID marketId, UUID userId, UUID accountId) {
        return new ArtifactScope(
                marketId, "BTC/EUR", "5m", userId, accountId, null,
                AnalysisExecutionMode.PASSIVE,
                ContextClassification.TRADING_SENSITIVE
        );
    }

    public static StoredArtifact artifact(
            ArtifactCacheKey key,
            ArtifactFreshness freshness
    ) {
        return new StoredArtifact(
                key,
                new TestContent("value"),
                freshness,
                new ArtifactProvenance(
                        key.identity().producerId(),
                        key.identity().producerVersion(),
                        UUID.randomUUID(),
                        NOW.minusSeconds(10),
                        Set.of(),
                        Set.of()
                ),
                AnalysisResultQuality.COMPLETE
        );
    }

    public static AnalysisExecution runningExecution(UUID marketId) {
        AnalysisExecutionPolicy policy = new AnalysisExecutionPolicy(
                Duration.ofMinutes(10),
                Duration.ofSeconds(10),
                1,
                2,
                new ContextLimits(
                        10, 1_000, 100, 2, ContextClassification.TRADING_SENSITIVE
                ),
                new RetryPolicy(
                        1, Duration.ZERO, Set.of(RetryClassification.RETRYABLE)
                ),
                Map.of(),
                new DegradationPolicy(true, true, true, true)
        );
        return AnalysisExecution.requested(
                        UUID.randomUUID(),
                        new IdempotencyKey("execution-" + UUID.randomUUID()),
                        policy,
                        NOW.minusSeconds(30),
                        List.of("spread-analysis"),
                        new AnalysisExecutionProvenance(
                                marketId,
                                AnalysisExecutionMode.PASSIVE,
                                "",
                                "v1"
                        ),
                        AnalysisTraceMetadata.empty()
                )
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, NOW.minusSeconds(29))
                .transitionTo(
                        AnalysisExecutionStatus.CONTEXT_BUILDING,
                        NOW.minusSeconds(28)
                )
                .transitionTo(AnalysisExecutionStatus.RUNNING, NOW.minusSeconds(27));
    }

    public record TestContent(String value) implements ArtifactContent {
    }

    private ArtifactTestFixtures() {
    }
}
