package com.hope.trading.market_intelligence.domain.outcome;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class OutcomeHorizonTest {
    @Test
    void rejectsNonPositiveAndUnboundedValues() {
        assertThatThrownBy(() -> new OutcomeHorizon(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutcomeHorizon(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutcomeHorizon(Duration.ofDays(31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new OutcomeHorizon(OutcomeHorizon.MAXIMUM)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new OutcomeHorizon(OutcomeHorizon.MAXIMUM.plusMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
