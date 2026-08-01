package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryRiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.PositionSizing;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradingContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradePlanRiskHandoffServiceTest {
    private static final UUID ACKNOWLEDGMENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID EVALUATION_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");

    @Test
    void returnsTheExactLatestAcceptedPlanAndItsCompleteReferencedContext() {
        Fixture fixture = fixture();

        TradePlanRiskSnapshot snapshot = fixture.service().loadAcceptedSnapshot(
                fixture.accepted().id(), fixture.accepted().version());

        assertThat(snapshot.tradePlanId()).isEqualTo(fixture.accepted().id().value());
        assertThat(snapshot.tradePlanVersion()).isEqualTo(fixture.accepted().version().value());
        assertThat(snapshot.status()).isEqualTo("ACCEPTED");
        assertThat(snapshot.context().id()).isEqualTo(fixture.environment().context().id());
        assertThat(snapshot.context().version()).isEqualTo(fixture.environment().context().version());
        assertThat(snapshot.context().ownerId()).isEqualTo(fixture.environment().owner());
        assertThat(snapshot.context().tradingAccountId())
                .isEqualTo(fixture.environment().context().tradingAccountId());
        assertThat(snapshot.context().accountCurrency()).isEqualTo("EUR");
        assertThat(snapshot.context().leverage()).isEqualByComparingTo("2");
        assertThat(snapshot.execution().instrument()).isEqualTo(
                fixture.accepted().execution().instrument());
        assertThat(snapshot.execution().entry().price()).isEqualByComparingTo("100");
        assertThat(snapshot.execution().positionSizing().quantity()).isPositive();
        assertThat(snapshot.execution().positionSizing().notional()).isPositive();
        assertThat(snapshot.execution().positionSizing().expectedMonetaryRisk()).isPositive();
        assertThat(snapshot.execution().positionSizing().currency()).isEqualTo("EUR");
        assertThat(snapshot.execution().takeProfits())
                .hasSameSizeAs(fixture.accepted().execution().takeProfits());
        assertThat(snapshot.execution().expiration().policy()).isNotBlank();
        assertThat(snapshot.rationale().opportunities()).isNotEmpty();
        assertThat(snapshot.rationale().observationIds()).isNotEmpty();
        assertThat(snapshot.rationale().confirmationConditions()).isNotEmpty();
        assertThat(snapshot.rationale().invalidationConditions()).isNotEmpty();
    }

    @Test
    void rejectsMissingStaleNonAcceptedAndIncoherentSnapshotsWithControlledErrors() {
        Fixture fixture = fixture();

        assertCode("TRADE_PLAN_RISK_SNAPSHOT_NOT_FOUND", () ->
                fixture.service().loadAcceptedSnapshot(
                        new com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId(
                                UUID.randomUUID()), fixture.accepted().version()));
        assertCode("STALE_TRADE_PLAN_VERSION", () -> fixture.service().loadAcceptedSnapshot(
                fixture.accepted().id(), fixture.accepted().previousVersion().orElseThrow()));

        var proposedEnvironment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) proposedEnvironment.service().create(
                TradePlanTestFixtures.request(proposedEnvironment))).plan();
        TradePlanRiskHandoffService proposedService = service(proposedEnvironment);
        assertCode("TRADE_PLAN_NOT_ACCEPTED", () -> proposedService.loadAcceptedSnapshot(
                proposed.id(), proposed.version()));

        var missingContextEnvironment = TradePlanTestFixtures.environment();
        TradePlan source = ((TradePlanningResult.Success) missingContextEnvironment.service().create(
                TradePlanTestFixtures.request(missingContextEnvironment))).plan();
        var missingReference = new com.hope.trading.market_intelligence.domain.tradeplan
                .TradingContextReference(UUID.randomUUID(), 1, TradePlanTestFixtures.NOW);
        TradePlan acceptedWithMissingContext = new com.hope.trading.market_intelligence.domain
                .tradeplan.TradePlanFactory().create(
                        source.id(), source.version().next(), source.version(),
                        TradePlanStatus.ACCEPTED, missingReference, source.execution(),
                        source.rationale(), TradePlanTestFixtures.NOW);
        missingContextEnvironment.plans().append(acceptedWithMissingContext);
        TradePlanRiskHandoffService missingContextService = service(missingContextEnvironment);
        assertCode("TRADE_PLAN_RISK_SNAPSHOT_NOT_FOUND", () ->
                missingContextService.loadAcceptedSnapshot(
                        acceptedWithMissingContext.id(), acceptedWithMissingContext.version()));

        var currencyEnvironment = TradePlanTestFixtures.environment();
        TradePlan currencySource = ((TradePlanningResult.Success) currencyEnvironment.service().create(
                TradePlanTestFixtures.request(currencyEnvironment))).plan();
        TradingContext eur = currencyEnvironment.context();
        TradingContext usd = new TradingContext(
                UUID.randomUUID(), 1, eur.snapshotAt(), eur.ownerId(), eur.tradingAccountId(),
                "USD", eur.availableCapital(), eur.buyingPower(), eur.leverage(),
                eur.riskProfile(), eur.ruleProfile(), eur.existingExposure(),
                eur.executionPreferences());
        currencyEnvironment.contexts().saveSnapshot(usd);
        PositionSizing sizing = currencySource.execution().positionSizing();
        TradePlan incoherent = new com.hope.trading.market_intelligence.domain.tradeplan
                .TradePlanFactory().create(
                        currencySource.id(), currencySource.version().next(),
                        currencySource.version(), TradePlanStatus.ACCEPTED, usd.reference(),
                        new com.hope.trading.market_intelligence.domain.tradeplan.ExecutionParameters(
                                currencySource.execution().instrument(),
                                currencySource.execution().direction(),
                                currencySource.execution().entry(),
                                currencySource.execution().stopLoss(),
                                currencySource.execution().takeProfits(),
                                new PositionSizing(sizing.quantity(), sizing.notional(),
                                        sizing.expectedMonetaryRisk(), "EUR"),
                                currencySource.execution().riskReward(),
                                currencySource.execution().expiration(),
                                currencySource.execution().managementRules()),
                        currencySource.rationale(), TradePlanTestFixtures.NOW);
        currencyEnvironment.plans().append(incoherent);
        assertCode("POSITION_SIZING_CURRENCY_MISMATCH", () -> service(currencyEnvironment)
                .loadAcceptedSnapshot(incoherent.id(), incoherent.version()));
    }

    @Test
    void approvedAcknowledgmentIsIdempotentAndOnlyAppendsRiskValidated() {
        Fixture fixture = fixture();
        Instant evaluatedAt = TradePlanTestFixtures.NOW.minusSeconds(5);

        RiskValidationAcknowledgment first = fixture.service().acknowledgeApprovedEvaluation(
                fixture.accepted().id(), fixture.accepted().version(), EVALUATION_ID,
                RiskValidationDecision.APPROVED_WITH_WARNINGS, evaluatedAt);
        RiskValidationAcknowledgment retry = fixture.service().acknowledgeApprovedEvaluation(
                fixture.accepted().id(), fixture.accepted().version(), EVALUATION_ID,
                RiskValidationDecision.APPROVED_WITH_WARNINGS, evaluatedAt);

        assertThat(retry).isEqualTo(first);
        assertThat(first.acknowledgmentId()).isEqualTo(ACKNOWLEDGMENT_ID);
        assertThat(first.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(first.acceptedTradePlanVersion()).isEqualTo(fixture.accepted().version().value());
        assertThat(first.riskValidatedTradePlanVersion())
                .isEqualTo(fixture.accepted().version().value() + 1);
        assertThat(first.tradingContextId()).isEqualTo(fixture.accepted().tradingContext().id());
        assertThat(fixture.environment().plans().findLatest(fixture.accepted().id()).orElseThrow()
                .status()).isEqualTo(TradePlanStatus.RISK_VALIDATED);
        assertThat(fixture.environment().plans().history(fixture.accepted().id()))
                .extracting(TradePlan::status)
                .doesNotContain(TradePlanStatus.READY_TO_EXECUTE);
    }

    @Test
    void conflictsDoNotAdvanceLifecycleAndChangedPlanVersionRequiresNewEvaluation() {
        Fixture fixture = fixture();
        Instant evaluatedAt = TradePlanTestFixtures.NOW.minusSeconds(5);

        assertCode("RISK_DECISION_NOT_APPROVED", () ->
                fixture.service().acknowledgeApprovedEvaluation(
                        fixture.accepted().id(), fixture.accepted().version(), EVALUATION_ID,
                        RiskValidationDecision.REJECTED, evaluatedAt));
        assertThat(fixture.environment().plans().findLatest(fixture.accepted().id()).orElseThrow()
                .status()).isEqualTo(TradePlanStatus.ACCEPTED);

        fixture.service().acknowledgeApprovedEvaluation(
                fixture.accepted().id(), fixture.accepted().version(), EVALUATION_ID,
                RiskValidationDecision.APPROVED, evaluatedAt);
        assertCode("RISK_VALIDATION_ACKNOWLEDGMENT_CONFLICT", () ->
                fixture.service().acknowledgeApprovedEvaluation(
                        fixture.accepted().id(), fixture.accepted().version(), UUID.randomUUID(),
                        RiskValidationDecision.APPROVED, evaluatedAt));

        TradePlan changed = ((TradePlanningResult.Success) new TradePlanReplanningService(
                fixture.environment().plans(), fixture.environment().contexts(),
                fixture.environment().service()).replan(
                        fixture.accepted().id(), fixture.environment().owner(),
                        BigDecimal.valueOf(101), PlanningPreferences.conservative(), "changed"))
                .plan();
        TradePlan changedAccepted = fixture.environment().service().transition(
                changed.id(), TradePlanStatus.ACCEPTED);
        assertCode("RISK_EVALUATION_ALREADY_LINKED", () ->
                fixture.service().acknowledgeApprovedEvaluation(
                        changedAccepted.id(), changedAccepted.version(), EVALUATION_ID,
                        RiskValidationDecision.APPROVED, evaluatedAt));

        RiskValidationAcknowledgment changedLink = fixture.service().acknowledgeApprovedEvaluation(
                changedAccepted.id(), changedAccepted.version(), UUID.randomUUID(),
                RiskValidationDecision.APPROVED, evaluatedAt);
        assertThat(changedLink.acceptedTradePlanVersion())
                .isEqualTo(changedAccepted.version().value());
    }

    private static Fixture fixture() {
        TradePlanTestFixtures.Environment environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        TradePlan accepted = environment.service().transition(proposed.id(), TradePlanStatus.ACCEPTED);
        return new Fixture(environment, accepted, service(environment));
    }

    private static TradePlanRiskHandoffService service(
            TradePlanTestFixtures.Environment environment) {
        return new TradePlanRiskHandoffService(
                environment.plans(), environment.contexts(),
                new DefaultTradePlanIntegrationBoundary(
                        environment.plans(), environment.service()),
                new InMemoryRiskValidationAcknowledgmentRepository(),
                Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC), () -> ACKNOWLEDGMENT_ID);
    }

    private static void assertCode(String code, Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(TradePlanRiskHandoffException.class)
                .extracting(exception -> ((TradePlanRiskHandoffException) exception).code())
                .isEqualTo(code);
    }

    private record Fixture(
            TradePlanTestFixtures.Environment environment, TradePlan accepted,
            TradePlanRiskHandoffService service) { }
}
