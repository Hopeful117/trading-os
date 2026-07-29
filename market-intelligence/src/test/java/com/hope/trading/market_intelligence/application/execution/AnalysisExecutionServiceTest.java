package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.strategy.*;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisExecutionServiceTest {
    @Test
    void equivalentRequestReusesExecutionAndDispatchesOnlyOnce() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        InMemoryAnalysisExecutionRepository repository =
                new InMemoryAnalysisExecutionRepository();
        CountingDispatcher dispatcher = new CountingDispatcher();
        AnalysisExecutionService service = new AnalysisExecutionService(
                repository,
                dispatcher,
                new AnalysisStrategyRegistry(List.of(strategy())),
                new AnalysisExecutionPolicyFactory(),
                Clock.fixed(now, ZoneOffset.UTC)
        );
        UUID marketId = UUID.randomUUID();
        IdempotencyKey key = new IdempotencyKey("public:" + marketId + ":passive");

        AnalysisExecution first = service.create(
                request(UUID.randomUUID(), marketId), key, "request-1", "trace-1"
        );
        AnalysisExecution second = service.create(
                request(UUID.randomUUID(), marketId), key, "request-2", "trace-2"
        );

        assertThat(second.executionId()).isEqualTo(first.executionId());
        assertThat(dispatcher.dispatches).hasValue(1);
    }

    private AnalysisExecutionStrategy strategy() {
        return new AnalysisExecutionStrategy() {
            @Override
            public AnalysisExecutionMode mode() {
                return AnalysisExecutionMode.PASSIVE;
            }

            @Override
            public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
                return new AnalysisExecutionPlan(
                        List.of("spread-analysis"),
                        List.of(ContextRequirement.requiredPublic(
                                ContextSectionType.MARKET_SNAPSHOT
                        )),
                        1,
                        Duration.ofSeconds(10)
                );
            }
        };
    }

    private IntelligenceAnalysisRequest request(UUID executionId, UUID marketId) {
        return new IntelligenceAnalysisRequest(
                executionId, marketId, AnalysisExecutionMode.PASSIVE, ""
        );
    }

    private static final class CountingDispatcher
            implements AnalysisExecutionDispatcher {
        private final AtomicInteger dispatches = new AtomicInteger();

        @Override
        public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) {
            dispatches.incrementAndGet();
        }

        @Override
        public void cancel(UUID executionId) {
        }
    }
}
