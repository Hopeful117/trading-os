package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.CapabilityResult;

import java.time.Duration;
import java.util.concurrent.*;

public interface CapabilityExecutionHandle {
    CapabilityResult await(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException;
    void cancel();
}
