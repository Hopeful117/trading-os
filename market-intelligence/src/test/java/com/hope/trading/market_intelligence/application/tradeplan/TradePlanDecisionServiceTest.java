package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradePlanDecisionServiceTest {
    private TradePlanTestFixtures.Environment environment;
    private TradePlanDecisionService service;

    @BeforeEach
    void setUp() {
        environment = TradePlanTestFixtures.environment();
        service = new TradePlanDecisionService(
                environment.plans(), environment.contexts(), environment.service());
    }

    private TradePlanningContext freshContextForOwner() {
        return TradePlanTestFixtures.context(UUID.randomUUID(), 1, environment.owner());
    }

    private TradePlanningResult.Success propose() {
        var context = freshContextForOwner();
        environment.contexts().saveSnapshot(context);
        var created = ((TradePlanningResult.Success) environment.service().create(
                new TradePlanningRequest(
                        java.util.Set.of(environment.opportunity().id()),
                        context.id(), context.version(), environment.owner(),
                         java.math.BigDecimal.valueOf(100), null, null, "", java.util.Map.of(),
                         "KRAKEN", "SPOT")));
        return created;
    }

    @Test
    void acceptTransitionsProposedPlanToAcceptedOnNewVersion() {
        var proposed = propose();

        var decided = service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT);

        assertThat(decided.status()).isEqualTo(TradePlanStatus.ACCEPTED);
        assertThat(decided.decisionActorId()).contains(environment.owner());
        assertThat(decided.decisionAt()).isPresent();
        assertThat(decided.version().value())
                .isEqualTo(proposed.plan().version().value() + 1);
        assertThat(environment.plans().findLatest(proposed.plan().id()).orElseThrow().status())
                .isEqualTo(TradePlanStatus.ACCEPTED);
    }

    @Test
    void rejectTransitionsProposedPlanToRejected() {
        var proposed = propose();

        var decided = service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                environment.owner(), TradePlanDecisionService.Decision.REJECT);

        assertThat(decided.status()).isEqualTo(TradePlanStatus.REJECTED);
        assertThat(decided.decisionActorId()).contains(environment.owner());
        assertThat(decided.decisionAt()).isPresent();
    }

    @Test
    void repeatedIdenticalDecisionOnLatestVersionIsIdempotentSuccess() {
        var proposed = propose();
        var accepted = service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT);

        var repeated = service.decide(accepted.id().value(), accepted.version().value(),
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT);

        assertThat(repeated.status()).isEqualTo(TradePlanStatus.ACCEPTED);
        assertThat(repeated.version()).isEqualTo(accepted.version());
        assertThat(environment.plans().history(accepted.id())).hasSize(2);
    }

    @Test
    void concurrentIdenticalDecisionsOnProposedVersionReturnTheSameAcceptedVersion()
            throws Exception {
        var proposed = propose();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<TradePlan> request = () -> {
                ready.countDown();
                start.await();
                return service.decide(proposed.plan().id().value(),
                        proposed.plan().version().value(), environment.owner(),
                        TradePlanDecisionService.Decision.ACCEPT);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            ready.await();
            start.countDown();

            List<TradePlan> results = List.of(first.get(), second.get());
            assertThat(results).allSatisfy(result -> {
                assertThat(result.status()).isEqualTo(TradePlanStatus.ACCEPTED);
                assertThat(result.version()).isEqualTo(proposed.plan().version().next());
                assertThat(result.decisionActorId()).contains(environment.owner());
            });
            assertThat(environment.plans().history(proposed.plan().id())).hasSize(2);
        }
    }

    @Test
    void conflictingDecisionAfterAcceptanceFails() {
        var proposed = propose();
        var accepted = service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT);

        assertThatThrownBy(() -> service.decide(accepted.id().value(),
                accepted.version().value(),
                environment.owner(), TradePlanDecisionService.Decision.REJECT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("TRADE_PLAN_NOT_PROPOSED"));
    }

    @Test
    void staleVersionIsRejectedExplicitly() {
        var proposed = propose();

        assertThatThrownBy(() -> service.decide(proposed.plan().id().value(),
                proposed.plan().version().value() + 5,
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("STALE_TRADE_PLAN_VERSION"));
    }

    @Test
    void nonOwnerIsForbidden() {
        var proposed = propose();

        assertThatThrownBy(() -> service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                UUID.randomUUID(), TradePlanDecisionService.Decision.ACCEPT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(
                        ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void ownerCanReloadExactVersion() {
        var proposed = propose();

        var loaded = service.loadForActor(proposed.plan().id().value(),
                proposed.plan().version().value(), environment.owner());

        assertThat(loaded.id()).isEqualTo(proposed.plan().id());
    }
}
