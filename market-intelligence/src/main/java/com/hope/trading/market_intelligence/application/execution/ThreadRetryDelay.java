package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.CancellationToken;

import java.time.Duration;

public class ThreadRetryDelay implements RetryDelay {
    @Override public void await(Duration duration, CancellationToken token)
            throws InterruptedException {
        token.throwIfCancellationRequested();
        Thread.sleep(duration);
        token.throwIfCancellationRequested();
    }
}
