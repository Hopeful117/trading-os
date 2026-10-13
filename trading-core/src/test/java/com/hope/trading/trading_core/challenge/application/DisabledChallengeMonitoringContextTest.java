package com.hope.trading.trading_core.challenge.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DisabledChallengeMonitoringContextTest {
    @Autowired(required = false)
    private ScheduledChallengeMonitoringTrigger trigger;

    @Autowired
    private ActiveChallengeMonitoringService monitoring;

    @Test
    void disabledSchedulerDoesNotCreateTimerButKeepsDirectServiceAvailable() {
        assertThat(trigger).isNull();
        assertThat(monitoring).isNotNull();
    }
}
