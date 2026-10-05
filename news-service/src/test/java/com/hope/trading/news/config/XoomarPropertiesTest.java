package com.hope.trading.news.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class XoomarPropertiesTest {
    @Test
    void exposesTheConfiguredDefaults() {
        XoomarProperties properties = new XoomarProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.baseUrl()).hasToString("https://xoomar.com");
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.lookbackDays()).isEqualTo(1);
        assertThat(properties.lookaheadDays()).isEqualTo(7);
    }

    @Test
    void rejectsPollingBelowTheProviderSafeLimit() {
        XoomarProperties properties = new XoomarProperties();
        properties.setPollInterval(Duration.ofSeconds(30));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one minute");
    }

    @Test
    void rejectsWindowsLongerThanTheApplicationLimit() {
        XoomarProperties properties = new XoomarProperties();
        properties.setLookbackDays(20);
        properties.setLookaheadDays(20);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31 days");
    }
}
