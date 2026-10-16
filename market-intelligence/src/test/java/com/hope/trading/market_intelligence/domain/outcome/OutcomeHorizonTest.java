package com.hope.trading.market_intelligence.domain.outcome;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class OutcomeHorizonTest {
    @Test
    void rejectsNonPositiveAndUnboundedValues() {
        Duration zero = Duration.ZERO;
        Duration negative = Duration.ofMinutes(-1);
        Duration overThirtyDays = Duration.ofDays(31);
        assertThatThrownBy(() -> invalid(zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invalid(negative))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invalid(overThirtyDays))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new OutcomeHorizon(OutcomeHorizon.MAXIMUM)).doesNotThrowAnyException();
        Duration overMaximum = OutcomeHorizon.MAXIMUM.plusMinutes(15);
        assertThatThrownBy(() -> new OutcomeHorizon(overMaximum))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OutcomeHorizon invalid(Duration duration) {
        return new OutcomeHorizon(duration);
    }
}
