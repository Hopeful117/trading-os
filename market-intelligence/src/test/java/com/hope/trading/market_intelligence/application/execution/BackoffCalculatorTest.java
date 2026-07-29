package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {
    private final BackoffCalculator calculator = new BackoffCalculator();

    @Test
    void calculatesFixedLinearAndExponentialBackoff() {
        assertThat(calculator.delay(policy(BackoffStrategy.FIXED), 3))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(calculator.delay(policy(BackoffStrategy.LINEAR), 3))
                .isEqualTo(Duration.ofSeconds(6));
        assertThat(calculator.delay(policy(BackoffStrategy.EXPONENTIAL), 3))
                .isEqualTo(Duration.ofSeconds(8));
    }

    private RetryPolicy policy(BackoffStrategy strategy) {
        return new RetryPolicy(
                true, 5, strategy, Duration.ofSeconds(2),
                Duration.ofSeconds(20), Set.of("NETWORK"));
    }
}
