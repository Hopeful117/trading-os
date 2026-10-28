package com.hope.trading.market_data.ctrader;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
final class CTraderEventDispatcher implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 256;

    private final ThreadPoolExecutor executor;
    private final Consumer<JsonNode> events;
    private final Runnable overflow;
    private final AtomicBoolean closed = new AtomicBoolean();

    CTraderEventDispatcher(String threadName, Consumer<JsonNode> events, Runnable overflow) {
        this.events = events;
        this.overflow = overflow;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    void dispatch(JsonNode event) {
        if (closed.get()) return;
        try {
            executor.execute(() -> {
                try {
                    events.accept(event);
                } catch (RuntimeException exception) {
                    log.warn("CTRADER_EVENT_HANDLER_FAILURE errorType={} message={}",
                            exception.getClass().getSimpleName(), exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                log.error("CTRADER_EVENT_DISPATCH_OVERFLOW queueCapacity={}", QUEUE_CAPACITY);
                overflow.run();
            }
        }
    }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow();
    }
}
