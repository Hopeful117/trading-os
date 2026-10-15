package com.hope.trading.news.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantagePropertiesTest {
    @Test
    void requiresAKeyOnlyWhenTheProviderIsConstructedEnabled() {
        AlphaVantageProperties properties = new AlphaVantageProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void rejectsUnsafePollingIntervals() {
        AlphaVantageProperties properties = new AlphaVantageProperties();
        properties.setApiKey("test-key");
        properties.setPollInterval(java.time.Duration.ofSeconds(30));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poll interval");
    }
}
