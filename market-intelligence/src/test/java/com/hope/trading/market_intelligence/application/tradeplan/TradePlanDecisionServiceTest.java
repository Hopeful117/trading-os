package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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
                        java.math.BigDecimal.valueOf(100), null, null, "")));
        return created;
    }

    @Test
    void acceptTransitionsProposedPlanToAcceptedOnNewVersion() {
        var proposed = propose();

        var decided = service.decide(proposed.plan().id().value(),
                proposed.plan().version().value(),
                environment.owner(), TradePlanDecisionService.Decision.ACCEPT);

        assertThat(decided.status()).isEqualTo(TradePlanStatus.ACCEPTED);
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
