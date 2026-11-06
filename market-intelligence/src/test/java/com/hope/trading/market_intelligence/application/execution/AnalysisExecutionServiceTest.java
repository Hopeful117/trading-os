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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void registerPersistsWithoutDispatchAndDispatchClaimSucceedsOnlyOnce() {
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
        UUID analysisId = UUID.randomUUID();

        AnalysisExecution execution = service.register(
                request(analysisId, marketId), new IdempotencyKey("register-key"),
                "request-1", "trace-1"
        );

        assertThat(dispatcher.dispatches).hasValue(0);
        assertThat(service.claimForDispatch(execution.executionId())).isTrue();
        assertThat(service.claimForDispatch(execution.executionId())).isFalse();

        service.dispatchRegistered(execution.executionId());

        assertThat(dispatcher.dispatches).hasValue(1);
    }

    @Test
    void redispatchPreservesStrictCutoffAndEvidencePolicy() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        Instant cutoff = Instant.parse("2026-07-28T10:00:00Z");
        InMemoryAnalysisExecutionRepository repository = new InMemoryAnalysisExecutionRepository();
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        AnalysisExecutionService service = new AnalysisExecutionService(
                repository, dispatcher, new AnalysisStrategyRegistry(List.of(strategy())),
                new AnalysisExecutionPolicyFactory(), Clock.fixed(now, ZoneOffset.UTC));
        AnalysisExecution execution = service.register(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), UUID.randomUUID(),
                        AnalysisExecutionMode.PASSIVE, "replay", null, null, null, cutoff,
                        EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                new IdempotencyKey("replay-key"), "request", "trace");

        assertThat(execution.provenance().evaluationCutoff()).isEqualTo(cutoff);
        assertThat(execution.provenance().evidencePolicy())
                .isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        service.claimForDispatch(execution.executionId());
        service.dispatchRegistered(execution.executionId());

        assertThat(dispatcher.request.evaluationCutoff()).isEqualTo(cutoff);
        assertThat(dispatcher.request.evidencePolicy())
                .isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
    }

    @Test
    void historicalDispatchFailureIsTerminalAndRetryDoesNotDuplicateWork() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        InMemoryAnalysisExecutionRepository repository = new InMemoryAnalysisExecutionRepository();
        AnalysisExecutionDispatcher dispatcher = new AnalysisExecutionDispatcher() {
            @Override public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) {
                throw new IllegalStateException("dispatcher unavailable");
            }
            @Override public void cancel(UUID executionId) { }
        };
        AnalysisExecutionService service = service(repository, dispatcher, Clock.fixed(now, ZoneOffset.UTC));
        IntelligenceAnalysisRequest request = historicalRequest();
        IdempotencyKey key = new IdempotencyKey("historical-dispatch-failure");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createHistorical(
                        request, key, "request", "trace", "selection"))
                .isInstanceOf(IllegalStateException.class);

        AnalysisExecution failed = repository.findByIdempotencyKey(key).orElseThrow();
        assertThat(failed.status()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(failed.failureCode()).contains("DISPATCH_FAILED");
        assertThatThrownBy(() -> service.createHistorical(request, key, "retry", "trace", "selection"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a terminal");
    }

    @Test
    void equivalentHistoricalRequestReusesActiveExecution() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        InMemoryAnalysisExecutionRepository repository = new InMemoryAnalysisExecutionRepository();
        AnalysisExecutionService service = service(repository, new CountingDispatcher(), Clock.fixed(now, ZoneOffset.UTC));
        IntelligenceAnalysisRequest request = historicalRequest();
        IdempotencyKey key = new IdempotencyKey("historical-equivalent");

        AnalysisExecution first = service.registerHistorical(request, key, "request-1", "trace-1", "selection");
        AnalysisExecution second = service.registerHistorical(request, key, "request-2", "trace-2", "selection");

        assertThat(second.executionId()).isEqualTo(first.executionId());
    }

    @Test
    void staleAcceptedHistoricalExecutionCanBeClaimedOnlyOnceForRecovery() {
        Instant initial = Instant.parse("2026-07-29T10:00:00Z");
        InMemoryAnalysisExecutionRepository repository = new InMemoryAnalysisExecutionRepository();
        CountingDispatcher dispatcher = new CountingDispatcher();
        AnalysisExecutionService initialService = service(repository, dispatcher, Clock.fixed(initial, ZoneOffset.UTC));
        IntelligenceAnalysisRequest request = historicalRequest();
        IdempotencyKey key = new IdempotencyKey("historical-recovery");
        AnalysisExecution registered = initialService.createHistorical(
                request, key, "request", "trace", "selection");

        AnalysisExecutionService recovery = service(repository, dispatcher,
                Clock.fixed(initial.plusSeconds(6), ZoneOffset.UTC));
        recovery.createHistorical(request, key, "retry", "trace", "selection");

        assertThat(dispatcher.dispatches).hasValue(2);
        assertThat(repository.findById(registered.executionId()).orElseThrow().updatedAt())
                .isEqualTo(initial.plusSeconds(6));
    }

    private AnalysisExecutionService service(
            InMemoryAnalysisExecutionRepository repository,
            AnalysisExecutionDispatcher dispatcher,
            Clock clock
    ) {
        return new AnalysisExecutionService(repository, dispatcher,
                new AnalysisStrategyRegistry(List.of(strategy())),
                new AnalysisExecutionPolicyFactory(), clock);
    }

    private IntelligenceAnalysisRequest historicalRequest() {
        return new IntelligenceAnalysisRequest(UUID.randomUUID(), UUID.randomUUID(),
                AnalysisExecutionMode.PASSIVE,
                com.hope.trading.market_intelligence.application.research.HistoricalReconstructionPolicy.OBJECTIVE,
                null, null, null,
                Instant.parse("2026-07-28T10:00:00Z"), EvidenceAcquisitionPolicy.STRICT_OBSERVED)
                .withHistoricalEvidenceSelection("selection");
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
            // Cancellation is outside the scope of these service tests.
        }
    }

    private static final class CapturingDispatcher implements AnalysisExecutionDispatcher {
        private IntelligenceAnalysisRequest request;

        @Override
        public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) {
            this.request = request;
        }

        @Override
        public void cancel(UUID executionId) {
            // Cancellation is intentionally a no-op; these tests only verify dispatch semantics.
        }
    }
}
