package com.hope.trading.market_intelligence.domain.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class AnalysisExecutionPolicyTest {
    @Test
    void exposesProviderIndependentExecutionRules() {
        AnalysisExecutionPolicy policy = ExecutionTestFixtures.policy();

        assertThat(policy.maximumDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.maximumAiRequests()).isEqualTo(2);
        assertThat(policy.maximumParallelCapabilities()).isEqualTo(3);
        assertThat(policy.capabilityPriorities())
                .containsEntry("spread-analysis", CapabilityPriority.MANDATORY);
        assertThat(policy.degradationPolicy().acceptPartialResult()).isTrue();
    }

    @Test
    void rejectsCapabilityTimeoutAboveTotalDuration() {
        AnalysisExecutionPolicy valid = ExecutionTestFixtures.policy();

        assertThatThrownBy(() -> new AnalysisExecutionPolicy(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                0,
                1,
                valid.contextLimits(),
                valid.retryPolicy(),
                valid.capabilityPriorities(),
                valid.degradationPolicy()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryPolicyOnlyPermitsRetryableFailuresWithinAttemptLimit() {
        RetryPolicy policy = ExecutionTestFixtures.policy().retryPolicy();

        assertThat(policy.permits(RetryClassification.RETRYABLE, 0)).isTrue();
        assertThat(policy.permits(RetryClassification.NON_RETRYABLE, 0)).isFalse();
        assertThat(policy.permits(RetryClassification.RETRYABLE, 2)).isFalse();
    }
}
