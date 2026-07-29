package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessEvaluatorTest {
    private final FreshnessEvaluator evaluator = new FreshnessEvaluator();

    @Test
    void evaluatesFreshStaleExpiredInvalidatedAndUnknownSeparately() {
        FreshnessPolicy tolerant = new FreshnessPolicy(
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Set.of(AnalysisExecutionMode.PASSIVE),
                false,
                false
        );

        assertThat(assess(age(10), tolerant).status()).isEqualTo(FreshnessStatus.FRESH);
        FreshnessAssessment stale = assess(age(60), tolerant);
        assertThat(stale.status()).isEqualTo(FreshnessStatus.STALE);
        assertThat(stale.reusable()).isTrue();
        assertThat(stale.warning()).isTrue();
        assertThat(assess(age(200), tolerant).status())
                .isEqualTo(FreshnessStatus.EXPIRED);
        assertThat(assess(age(10).invalidate(ArtifactTestFixtures.NOW), tolerant).status())
                .isEqualTo(FreshnessStatus.INVALIDATED);
        assertThat(evaluator.assess(
                new ArtifactFreshness(null, null, null, null),
                tolerant,
                AnalysisExecutionMode.PASSIVE,
                ArtifactTestFixtures.NOW
        ).status()).isEqualTo(FreshnessStatus.UNKNOWN);
    }

    @Test
    void criticalPolicyRejectsStaleArtifact() {
        FreshnessPolicy critical = new FreshnessPolicy(
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Set.of(AnalysisExecutionMode.PASSIVE),
                false,
                true
        );
        FreshnessAssessment assessment = assess(
                age(60), critical
        );

        assertThat(assessment.status()).isEqualTo(FreshnessStatus.STALE);
        assertThat(assessment.reusable()).isFalse();
    }

    private ArtifactFreshness age(long seconds) {
        return new ArtifactFreshness(
                ArtifactTestFixtures.NOW.minusSeconds(seconds),
                ArtifactTestFixtures.NOW.plusSeconds(300),
                null,
                "source-v1"
        );
    }

    private FreshnessAssessment assess(
            ArtifactFreshness freshness,
            FreshnessPolicy policy
    ) {
        return evaluator.assess(
                freshness, policy, AnalysisExecutionMode.PASSIVE,
                ArtifactTestFixtures.NOW
        );
    }
}
