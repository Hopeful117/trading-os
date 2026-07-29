package com.hope.trading.market_intelligence.application.artifact;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryArtifactStore;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultArtifactResolverTest {
    private final Clock clock = Clock.fixed(
            ArtifactTestFixtures.NOW, ZoneOffset.UTC
    );
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final DefaultArtifactResolver resolver =
            new DefaultArtifactResolver(store, new FreshnessEvaluator(), clock);
    private final UUID marketId = UUID.randomUUID();

    @Test
    void reusesFreshArtifactAndTracesTheConsumingExecution() {
        ArtifactCacheKey key = ArtifactTestFixtures.key("v1", marketId);
        store.save(ArtifactTestFixtures.artifact(key, freshness(10)));
        var execution = ArtifactTestFixtures.runningExecution(marketId);

        ArtifactResolution resolution = resolver.resolve(
                requirement(key, FreshnessPolicy.strict(Duration.ofSeconds(30))),
                execution
        );

        assertThat(resolution.decision()).isEqualTo(ReuseDecision.REUSE);
        assertThat(resolution.reused()).isTrue();
        assertThat(resolution.resolvedProvenance().orElseThrow().reusedByExecutions())
                .contains(execution.executionId());
    }

    @Test
    void reusesStaleWithWarningOnlyWhenPolicyAllowsIt() {
        ArtifactCacheKey key = ArtifactTestFixtures.key("v1", marketId);
        store.save(ArtifactTestFixtures.artifact(key, freshness(60)));
        FreshnessPolicy tolerant = new FreshnessPolicy(
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Set.of(AnalysisExecutionMode.PASSIVE),
                false,
                false
        );

        ArtifactResolution resolution = resolver.resolve(
                requirement(key, tolerant),
                ArtifactTestFixtures.runningExecution(marketId)
        );

        assertThat(resolution.decision())
                .isEqualTo(ReuseDecision.REUSE_WITH_WARNING);
        assertThat(resolution.degraded()).isTrue();
    }

    @Test
    void recalculatesStaleExpiredInvalidatedAndUnknownArtifacts() {
        assertRecalculated(freshness(60), FreshnessPolicy.strict(Duration.ofSeconds(30)));
        assertRecalculated(
                ArtifactFreshness.validUntil(
                        ArtifactTestFixtures.NOW.minusSeconds(60),
                        ArtifactTestFixtures.NOW.minusSeconds(1),
                        "source-v1"
                ),
                FreshnessPolicy.strict(Duration.ofMinutes(2))
        );
        assertRecalculated(
                freshness(10).invalidate(ArtifactTestFixtures.NOW.minusSeconds(1)),
                FreshnessPolicy.strict(Duration.ofSeconds(30))
        );
        assertRecalculated(
                new ArtifactFreshness(null, null, null, null),
                FreshnessPolicy.strict(Duration.ofSeconds(30))
        );
    }

    @Test
    void missingArtifactIsRejectedWhenRecalculationIsForbidden() {
        ArtifactCacheKey key = ArtifactTestFixtures.key("v1", marketId);
        ArtifactRequirement requirement = new ArtifactRequirement(
                key,
                FreshnessPolicy.strict(Duration.ofSeconds(30)),
                AnalysisResultQuality.COMPLETE,
                RecalculationPolicy.FORBIDDEN,
                true
        );

        ArtifactResolution resolution = resolver.resolve(
                requirement, ArtifactTestFixtures.runningExecution(marketId)
        );

        assertThat(resolution.decision()).isEqualTo(ReuseDecision.REJECT);
    }

    @Test
    void differentPrivateScopeCannotResolveAnotherUsersArtifact() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        ArtifactCacheKey userAKey = privateKey(userA);
        ArtifactCacheKey userBKey = privateKey(userB);
        store.save(ArtifactTestFixtures.artifact(userAKey, freshness(10)));

        ArtifactResolution resolution = resolver.resolve(
                requirement(userBKey, FreshnessPolicy.strict(Duration.ofSeconds(30))),
                ArtifactTestFixtures.runningExecution(marketId)
        );

        assertThat(resolution.decision()).isEqualTo(ReuseDecision.RECALCULATE);
        assertThat(resolution.reused()).isFalse();
    }

    private void assertRecalculated(
            ArtifactFreshness freshness,
            FreshnessPolicy policy
    ) {
        ArtifactCacheKey key = ArtifactTestFixtures.key(
                UUID.randomUUID().toString(), marketId
        );
        store.save(ArtifactTestFixtures.artifact(key, freshness));

        assertThat(resolver.resolve(
                requirement(key, policy),
                ArtifactTestFixtures.runningExecution(marketId)
        ).decision()).isEqualTo(ReuseDecision.RECALCULATE);
    }

    private ArtifactRequirement requirement(
            ArtifactCacheKey key,
            FreshnessPolicy policy
    ) {
        return new ArtifactRequirement(
                key,
                policy,
                AnalysisResultQuality.COMPLETE,
                RecalculationPolicy.ALLOWED,
                true
        );
    }

    private ArtifactFreshness freshness(long ageSeconds) {
        return ArtifactFreshness.validUntil(
                ArtifactTestFixtures.NOW.minusSeconds(ageSeconds),
                ArtifactTestFixtures.NOW.plusSeconds(300),
                "source-v1"
        );
    }

    private ArtifactCacheKey privateKey(UUID userId) {
        ArtifactCacheKey publicKey = ArtifactTestFixtures.key("v1", marketId);
        return new ArtifactCacheKey(
                publicKey.identity(),
                ArtifactTestFixtures.privateScope(
                        marketId, userId, UUID.randomUUID()
                ),
                publicKey.parametersFingerprint(),
                publicKey.inputFingerprint()
        );
    }
}
