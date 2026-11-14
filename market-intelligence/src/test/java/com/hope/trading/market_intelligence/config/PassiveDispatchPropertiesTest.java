package com.hope.trading.market_intelligence.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassiveDispatchPropertiesTest {
    @Test
    void defaultsAreBoundedAndDisabled() {
        PassiveDispatchProperties properties = new PassiveDispatchProperties();

        properties.validate();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getFixedDelay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getMaxMarketsPerRun()).isEqualTo(2);
        assertThat(properties.getMaxConcurrentExecutions()).isEqualTo(1);
    }

    @Test
    void rejectsUnboundedValues() {
        PassiveDispatchProperties properties = new PassiveDispatchProperties();
        properties.setMaxMarketsPerRun(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("market limit");
    }

    @Test
    void rejectsDelayBelowFiveMinutes() {
        PassiveDispatchProperties properties = new PassiveDispatchProperties();
        properties.setFixedDelay(Duration.ofMinutes(4));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("five minutes");
    }
}
