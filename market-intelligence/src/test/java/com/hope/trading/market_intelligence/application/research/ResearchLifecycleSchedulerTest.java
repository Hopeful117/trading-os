package com.hope.trading.market_intelligence.application.research;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResearchLifecycleSchedulerTest {
    @Test
    void delegatesScheduledRunsToTheLifecycleService() {
        ResearchLifecycleService lifecycle = mock(ResearchLifecycleService.class);

        new ResearchLifecycleScheduler(lifecycle).run();

        verify(lifecycle).runOnce();
    }
}
