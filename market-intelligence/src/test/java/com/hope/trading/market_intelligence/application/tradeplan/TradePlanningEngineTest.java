package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiTradePlanningAdapter;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TradePlanningEngineTest {
    @Test
    void fullDeterministicFlowBuildsCompleteProposedPlan() {
        var environment = TradePlanTestFixtures.environment();

        TradePlanningResult result = environment.service().create(
                TradePlanTestFixtures.request(environment));

        assertThat(result).isInstanceOf(TradePlanningResult.Success.class);
        TradePlan plan = ((TradePlanningResult.Success) result).plan();
        assertThat(plan.status()).isEqualTo(TradePlanStatus.PROPOSED);
        assertThat(plan.version().value()).isEqualTo(1);
        assertThat(plan.planningContext()).isEqualTo(environment.context().reference());
        assertThat(plan.execution().positionSizing().expectedMonetaryRisk())
                .isEqualByComparingTo(environment.context().riskBudget().amount());
        assertThat(plan.execution().riskReward().ratio()).isEqualByComparingTo("2.00");
        assertThat(plan.rationale().opportunities()).hasSize(1);
        assertThat(environment.plans().history(plan.id())).hasSize(1);
        assertThat(environment.events()).singleElement()
                .isInstanceOf(TradePlanEvent.Created.class);
        assertThat(environment.metrics().count("trade_plans_created")).isEqualTo(1);
    }

    @Test
    void unauthorizedContextAndIncompletePoliciesReturnExplicitFailures() {
        var environment = TradePlanTestFixtures.environment();
        TradePlanningRequest valid = TradePlanTestFixtures.request(environment);
        TradePlanningRequest unauthorized = new TradePlanningRequest(
                valid.opportunityIds(), valid.planningContextId(), valid.contextVersion(),
                UUID.randomUUID(), valid.marketPrice(),
                null, null, "");
        assertThat(environment.engine().plan(unauthorized))
                .isEqualTo(new TradePlanningResult.Failure(
                        PlanningFailureReason.INVALID_TRADING_CONTEXT,
                        "Trading Context is missing or unauthorized", List.of()));

        var engine = new TradePlanningEngine(
                environment.opportunities(), environment.contexts(), (a, c) -> true,
                environment.plans(), new PlanningPolicyRegistry(List.of(
                        new DefaultPlanningPolicies.EntrySelection())),
                new DisabledAiTradePlanningAdapter(), new AiContributionValidator(),
                new TradePlanFactory(), () -> new TradePlanId(UUID.randomUUID()),
                Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC));
        assertThat(engine.plan(valid)).isInstanceOfSatisfying(
                TradePlanningResult.Failure.class,
                failure -> assertThat(failure.reason())
                        .isEqualTo(PlanningFailureReason.INSUFFICIENT_DATA));
    }

    @Test
    void materialPolicyConflictIsNeverResolvedSilently() {
        var environment = TradePlanTestFixtures.environment();
        PlanningPolicy conflicting = new PlanningPolicy() {
            @Override public String id() { return "conflicting-entry"; }
            @Override public int order() { return 11; }
            @Override public boolean supports(PlanningInput input) { return true; }
            @Override public PlanningContribution evaluate(PlanningInput input) {
                return PlanningContribution.deterministic(
                        ContributionType.ENTRY,
                        new EntryStrategy(EntryType.LIMIT, BigDecimal.valueOf(99), Set.of()),
                        id());
            }
        };
        List<PlanningPolicy> policies = new ArrayList<>(
                TradePlanTestFixtures.policies().applicable(new PlanningInput(
                        List.of(environment.opportunity()), environment.context(),
                        BigDecimal.valueOf(100), TradePlanTestFixtures.NOW)));
        policies.add(conflicting);
        var engine = new TradePlanningEngine(
                environment.opportunities(), environment.contexts(), (a, c) -> true,
                environment.plans(), new PlanningPolicyRegistry(policies),
                new DisabledAiTradePlanningAdapter(), new AiContributionValidator(),
                new TradePlanFactory(), () -> new TradePlanId(UUID.randomUUID()),
                Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC));

        assertThat(engine.plan(TradePlanTestFixtures.request(environment)))
                .isInstanceOfSatisfying(TradePlanningResult.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(PlanningFailureReason.POLICY_CONFLICT);
                    assertThat(failure.conflicts()).singleElement()
                            .satisfies(conflict -> assertThat(conflict.type())
                                    .isEqualTo(ContributionType.ENTRY));
                });
    }

    @Test
    void malformedAiIsObservableButDoesNotDestabilizeDeterministicPlanning() {
        var environment = TradePlanTestFixtures.environment();
        var engine = new TradePlanningEngine(
                environment.opportunities(), environment.contexts(), (a, c) -> true,
                environment.plans(), TradePlanTestFixtures.policies(),
                (input, contributions) -> new AiPlanningProposal(
                        "OTHER", input.direction(), List.of(), Set.of()),
                new AiContributionValidator(), new TradePlanFactory(),
                () -> new TradePlanId(UUID.randomUUID()),
                Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC));

        assertThat(engine.plan(TradePlanTestFixtures.request(environment)))
                .isInstanceOfSatisfying(TradePlanningResult.Success.class,
                        success -> assertThat(success.warnings()).singleElement()
                                .asString().contains("AI contribution rejected"));
    }

    @Test
    void policyRegistryRejectsDuplicatesAndHasExplicitOrder() {
        PlanningPolicy first = new DefaultPlanningPolicies.EntrySelection();
        assertThatThrownBy(() -> new PlanningPolicyRegistry(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TradePlanTestFixtures.policies().activePolicyIds())
                .startsWith("entry-selection-v1", "stop-selection-v1")
                .endsWith("thesis-v1");
    }
}
