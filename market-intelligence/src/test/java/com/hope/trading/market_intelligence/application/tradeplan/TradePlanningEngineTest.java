package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiTradePlanningAdapter;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
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
    void exactOpportunityVersionRemainsUsableWhenLatestVersionIsExpired() {
        var environment = TradePlanTestFixtures.environment();
        environment.opportunities().append(
                com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures.opportunity(
                        environment.opportunity().id(), 2, OpportunityStatus.EXPIRED,
                        environment.opportunity().score(), TradePlanTestFixtures.NOW.plusSeconds(60)));

        TradePlanningRequest exact = new TradePlanningRequest(
                Set.of(environment.opportunity().id()), environment.context().id(),
                environment.context().version(), environment.owner(), BigDecimal.valueOf(100),
                null, null, "", Map.of(environment.opportunity().id(), new OpportunityVersion(1)));

        assertThat(environment.engine().plan(exact))
                .isInstanceOf(TradePlanningResult.Success.class);
    }

    @Test
    void manualFlowBuildsPlanWithoutOpportunityAndPreservesAuthor() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = new ManualTradePlanningRequest(
                environment.context().id(), environment.context().version(), environment.owner(),
                environment.context().tradingAccountId(),
                "BTC/EUR", TradeDirection.LONG,
                new EntryStrategy(EntryType.LIMIT, BigDecimal.valueOf(100), Set.of()),
                new StopLoss(BigDecimal.valueOf(99), "Manual invalidation"),
                List.of(new TakeProfit(BigDecimal.valueOf(102), BigDecimal.valueOf(100))),
                new PositionSizing(BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.ONE, "EUR"),
                BigDecimal.valueOf(100), TradePlanTestFixtures.NOW.plusSeconds(3600), "MANUAL_VALIDITY",
                "Human discretionary setup", Set.of("Price confirms setup"),
                Set.of("Stop is reached"), Set.of());

        TradePlanningResult result = environment.service().createManual(request);

        assertThat(result).isInstanceOfSatisfying(TradePlanningResult.Success.class, success -> {
            TradePlan plan = success.plan();
            assertThat(plan.origin()).isEqualTo(TradePlanOrigin.MANUAL);
            assertThat(plan.authorId()).contains(environment.owner());
            assertThat(plan.rationale().opportunities()).isEmpty();
            assertThat(plan.rationale().observations()).isEmpty();
            assertThat(environment.plans().find(plan.id(), plan.version()))
                    .isPresent()
                    .get()
                    .extracting(TradePlan::origin, TradePlan::authorId)
                    .containsExactly(TradePlanOrigin.MANUAL, plan.authorId());
        });
    }

    @Test
    void manualFlowMayStartWithoutProtection() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = new ManualTradePlanningRequest(
                environment.context().id(), environment.context().version(), environment.owner(),
                environment.context().tradingAccountId(),
                "BTC/EUR", TradeDirection.LONG,
                new EntryStrategy(EntryType.MARKET, null, Set.of()),
                null, List.of(),
                new PositionSizing(BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.ONE, "EUR"),
                BigDecimal.valueOf(100), TradePlanTestFixtures.NOW.plusSeconds(3600), "MANUAL_VALIDITY",
                "Human discretionary entry without initial protection", Set.of("Human confirms entry"),
                Set.of("Trader adds protection later"), Set.of());

        assertThat(environment.service().createManual(request))
                .isInstanceOfSatisfying(TradePlanningResult.Success.class, success -> {
                    assertThat(success.plan().origin()).isEqualTo(TradePlanOrigin.MANUAL);
                    assertThat(success.plan().execution().stopLoss()).isNull();
                    assertThat(success.plan().execution().takeProfits()).isEmpty();
                    assertThat(success.plan().execution().riskReward()).isNull();
                });
    }

    @Test
    void manualFlowReplaysTheSamePlanForTheSameIdempotencyKey() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = manualRequest(environment);

        TradePlanningResult first = environment.service().createManual(request, "manual-key");
        TradePlanningResult replay = environment.service().createManual(request, "manual-key");

        assertThat(first).isInstanceOf(TradePlanningResult.Success.class);
        assertThat(replay).isInstanceOfSatisfying(TradePlanningResult.Success.class, success ->
                assertThat(success.plan().id()).isEqualTo(
                        ((TradePlanningResult.Success) first).plan().id()));
        assertThat(environment.plans().history(
                ((TradePlanningResult.Success) first).plan().id())).hasSize(1);
    }

    @Test
    void manualFlowRejectsDifferentRequestWithTheSameIdempotencyKey() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = manualRequest(environment);
        environment.service().createManual(request, "manual-key");

        ManualTradePlanningRequest changed = new ManualTradePlanningRequest(
                request.planningContextId(), request.contextVersion(), request.actorId(),
                request.tradingAccountId(),
                request.instrument(), request.direction(), request.entry(), request.stopLoss(),
                request.takeProfits(), request.positionSizing(), request.referencePrice(),
                request.expiresAt(), request.expirationPolicy(), "Changed thesis",
                request.confirmationConditions(), request.invalidationConditions(), request.managementRules());

        assertThatThrownBy(() -> environment.service().createManual(changed, "manual-key"))
                .isInstanceOf(TradePlanIdempotencyException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void manualFlowRejectsDifferentExpirationWithTheSameIdempotencyKey() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = manualRequest(environment);
        environment.service().createManual(request, "expiration-key");

        ManualTradePlanningRequest changed = new ManualTradePlanningRequest(
                request.planningContextId(), request.contextVersion(), request.actorId(),
                request.tradingAccountId(), request.instrument(), request.direction(), request.entry(),
                request.stopLoss(), request.takeProfits(), request.positionSizing(), request.referencePrice(),
                request.expiresAt().plusSeconds(60), request.expirationPolicy(), request.thesis(),
                request.confirmationConditions(), request.invalidationConditions(), request.managementRules());

        assertThatThrownBy(() -> environment.service().createManual(changed, "expiration-key"))
                .isInstanceOf(TradePlanIdempotencyException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void missingPlanForIdempotencyRecordProducesControlledStateError() {
        var environment = TradePlanTestFixtures.environment();
        ManualTradePlanningRequest request = manualRequest(environment);
        String key = "orphaned-plan";
        String fingerprint = new ManualTradePlanFingerprintFactory(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())
                .fingerprint(request);
        environment.idempotency().save(new com.hope.trading.market_intelligence.application.port
                .ManualTradePlanIdempotencyRepository.Entry(
                        request.actorId(), key, fingerprint, UUID.randomUUID(), 1));

        assertThatThrownBy(() -> environment.service().createManual(request, key))
                .isInstanceOfSatisfying(TradePlanIdempotencyException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("IDEMPOTENCY_STATE_INVALID");
                    assertThat(exception.status()).isEqualTo(500);
                });
    }

    private ManualTradePlanningRequest manualRequest(TradePlanTestFixtures.Environment environment) {
        return new ManualTradePlanningRequest(
                environment.context().id(), environment.context().version(), environment.owner(),
                environment.context().tradingAccountId(),
                "BTC/EUR", TradeDirection.LONG,
                new EntryStrategy(EntryType.LIMIT, BigDecimal.valueOf(100), Set.of()),
                new StopLoss(BigDecimal.valueOf(99), "Manual invalidation"),
                List.of(new TakeProfit(BigDecimal.valueOf(102), BigDecimal.valueOf(100))),
                new PositionSizing(BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.ONE, "EUR"),
                BigDecimal.valueOf(100), TradePlanTestFixtures.NOW.plusSeconds(3600), "MANUAL_VALIDITY",
                "Human discretionary setup", Set.of("Price confirms setup"),
                Set.of("Stop is reached"), Set.of());
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
