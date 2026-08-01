package com.hope.trading.trading_core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TimeConfigurationTest {
    @Test
    void backsOffWhenAnApplicationClockAlreadyExists() {
        Clock supplied = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);

        new ApplicationContextRunner().withUserConfiguration(TimeConfiguration.class)
                .withBean(Clock.class, () -> supplied)
                .run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(supplied);
                });
    }

}
