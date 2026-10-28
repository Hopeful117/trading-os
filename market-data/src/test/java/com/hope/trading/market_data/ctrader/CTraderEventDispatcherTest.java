package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CTraderEventDispatcherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void eventHandoffDoesNotBlockImmediateResponsePath() throws Exception {
        CountDownLatch eventStarted = new CountDownLatch(1);
        CountDownLatch releaseEvent = new CountDownLatch(1);
        try (CTraderEventDispatcher dispatcher = new CTraderEventDispatcher("test-events",
                ignored -> {
                    eventStarted.countDown();
                    await(releaseEvent);
                }, () -> { })) {
            dispatcher.dispatch(objectMapper.createObjectNode().put("payloadType", 2131));
            assertThat(eventStarted.await(2, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            dispatcher.dispatch(objectMapper.createObjectNode().put("payloadType", 2128));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).isLessThan(100L);
            releaseEvent.countDown();
        }
    }

    @Test
    void preservesArrivalOrder() throws Exception {
        List<Integer> processed = new ArrayList<>();
        CountDownLatch processedAll = new CountDownLatch(3);
        try (CTraderEventDispatcher dispatcher = new CTraderEventDispatcher("test-events",
                event -> {
                    synchronized (processed) {
                        processed.add(event.path("sequence").asInt());
                    }
                    processedAll.countDown();
                }, () -> { })) {
            for (int sequence = 1; sequence <= 3; sequence++) {
                dispatcher.dispatch(objectMapper.createObjectNode().put("sequence", sequence));
            }

            assertThat(processedAll.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(processed).containsExactly(1, 2, 3);
        }
    }

    @Test
    void isolatesEventHandlerFailure() throws Exception {
        AtomicInteger processed = new AtomicInteger();
        CountDownLatch secondEvent = new CountDownLatch(1);
        try (CTraderEventDispatcher dispatcher = new CTraderEventDispatcher("test-events",
                event -> {
                    if (event.path("fail").asBoolean()) throw new IllegalStateException("test failure");
                    processed.incrementAndGet();
                    secondEvent.countDown();
                }, () -> { })) {
            dispatcher.dispatch(objectMapper.createObjectNode().put("fail", true));
            dispatcher.dispatch(objectMapper.createObjectNode().put("fail", false));

            assertThat(secondEvent.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(processed).hasValue(1);
        }
    }

    @Test
    void closesTheDispatcherAndRejectsFurtherEvents() throws Exception {
        CountDownLatch processed = new CountDownLatch(1);
        try (CTraderEventDispatcher dispatcher = new CTraderEventDispatcher("test-events",
                ignored -> processed.countDown(), () -> { })) {
            dispatcher.close();
            dispatcher.dispatch(objectMapper.createObjectNode());

            assertThat(processed.await(100, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(dispatcher.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reportsQueueOverflowInsteadOfDroppingEventsSilently() throws Exception {
        CountDownLatch eventStarted = new CountDownLatch(1);
        CountDownLatch releaseEvent = new CountDownLatch(1);
        CountDownLatch overflow = new CountDownLatch(1);
        try (CTraderEventDispatcher dispatcher = new CTraderEventDispatcher("test-events",
                ignored -> {
                    eventStarted.countDown();
                    await(releaseEvent);
                }, overflow::countDown)) {
            dispatcher.dispatch(objectMapper.createObjectNode());
            assertThat(eventStarted.await(2, TimeUnit.SECONDS)).isTrue();

            for (int index = 0; index < 257; index++) {
                dispatcher.dispatch(objectMapper.createObjectNode().put("sequence", index));
            }

            assertThat(overflow.await(2, TimeUnit.SECONDS)).isTrue();
            releaseEvent.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
