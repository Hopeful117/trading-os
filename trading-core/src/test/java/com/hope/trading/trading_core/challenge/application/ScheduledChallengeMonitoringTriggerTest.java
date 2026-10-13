package com.hope.trading.trading_core.challenge.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduledChallengeMonitoringTriggerTest {
    @Test
    void triggerDelegatesToMonitoringService() {
        ActiveChallengeMonitoringService monitoring = mock(ActiveChallengeMonitoringService.class);

        new ScheduledChallengeMonitoringTrigger(monitoring).trigger();

        verify(monitoring).monitor();
    }
}
