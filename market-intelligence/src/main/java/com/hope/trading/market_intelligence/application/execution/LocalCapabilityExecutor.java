package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.time.Duration;
import java.util.concurrent.*;

public class LocalCapabilityExecutor implements CapabilityExecutor, AutoCloseable {
    private final ExecutorService executor;
    public LocalCapabilityExecutor(int parallelism) {
        if (parallelism < 1) throw new IllegalArgumentException("Parallelism must be positive");
        executor = Executors.newFixedThreadPool(parallelism);
    }
    @Override public CapabilityExecutionHandle submit(
            Capability capability, CapabilityContext context) {
        Future<CapabilityResult> future = executor.submit(() -> capability.execute(context));
        return new CapabilityExecutionHandle() {
            @Override public CapabilityResult await(Duration timeout)
                    throws InterruptedException, ExecutionException, TimeoutException {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            @Override public void cancel() { future.cancel(true); }
        };
    }
    @Override public void close() { executor.shutdownNow(); }
}
