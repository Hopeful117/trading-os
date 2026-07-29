package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.CancellationToken;

import java.time.Duration;

public interface RetryDelay {
    void await(Duration duration, CancellationToken token) throws InterruptedException;
}
