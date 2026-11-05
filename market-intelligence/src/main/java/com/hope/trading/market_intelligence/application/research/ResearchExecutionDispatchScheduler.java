package com.hope.trading.market_intelligence.application.research;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ResearchExecutionDispatchScheduler {
    private final ResearchApplicationService research;

    public ResearchExecutionDispatchScheduler(ResearchApplicationService research) {
        this.research = research;
    }

    @Scheduled(fixedDelayString = "${research.execution.dispatch-delay:PT5S}")
    public void dispatch() {
        research.dispatchPending();
    }
}
