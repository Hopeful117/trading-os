package com.hope.trading.risk;

import com.hope.trading.risk.audit.*;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.engine.*;
import com.hope.trading.risk.explain.DecisionExplainer;
import com.hope.trading.risk.rule.*;
import com.hope.trading.risk.snapshot.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.hope.trading.risk.RiskFixture.*;
import static com.hope.trading.risk.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskEngineIntegrationTest {
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RiskRuleRegistry registry = new RiskRuleRegistry(List.of(
            new MaximumPositionRiskRule(), new MaximumExposureRule(),
            new DailyDrawdownRule(), new MaximumTotalDrawdownRule()));

    @Test void evaluatesEveryRuleAndRejectsAnyBlockingFailure() {
        var context = context(rules(
                rule(MaximumPositionRiskRule.ID, RuleCategory.POSITION,
                        RuleSeverity.BLOCKING, "0.005"),
                rule(MaximumExposureRule.ID, RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.50"),
                rule(DailyDrawdownRule.ID, RuleCategory.PROP_FIRM,
                        RuleSeverity.BLOCKING, "0.025")));
        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);
        assertEquals(EvaluationStatus.COMPLETED, result.evaluationStatus());
        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(3, result.ruleResults().size());
        assertEquals(2, result.violations().size());
        assertEquals("28.1", result.trace().engineVersion());
        assertEquals(context.account().version(), result.trace().context().accountVersion());
        assertEquals(2, new DecisionExplainer().explain(result).reasons().size());
    }

    @Test void staticTotalDrawdownUsesStartingBalanceAndBreachIsInclusive() {
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("50000"), usd("48500"),
                usd("0"), java.util.Optional.of(usd("50000")),
                new DailyRiskBaseline(usd("50000"), NOW, "TEST", java.util.Map.of()), usd("0"));
        var context = context(rules(rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT,
                RuleSeverity.BLOCKING, "0.03")), account, null);

        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(RuleStatus.FAILURE, result.ruleResults().getFirst().status());
        assertEquals(new BigDecimal("0.03"),
                result.globalMetrics().totalDrawdownRatio().orElseThrow().value());
    }

    @Test void staticTotalDrawdownDoesNotMoveAfterProfit() {
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("55000"), usd("52000"),
                usd("0"), java.util.Optional.of(usd("50000")),
                new DailyRiskBaseline(usd("55000"), NOW, "TEST", java.util.Map.of()), usd("0"));
        var context = context(rules(rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT,
                RuleSeverity.BLOCKING, "0.06")), account, null);

        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

        assertEquals(RiskDecision.APPROVED, result.decision().orElseThrow());
        assertEquals(new BigDecimal("0"),
                result.globalMetrics().totalDrawdownRatio().orElseThrow().value());
    }

    @Test void accountMonitoringEvaluatesObservedStateWithoutAProposedTrade() {
        var context = context(rules(
                rule(DailyDrawdownRule.ID, RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, "0.03"),
                rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, "0.03")),
                new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("10000"), usd("9700"), usd("0"),
                        java.util.Optional.of(usd("10000")),
                        new DailyRiskBaseline(usd("10000"), NOW, "TEST", Map.of()), usd("0")), null);

        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

        assertEquals(ValidationMode.ACCOUNT_MONITORING, result.evaluationMode());
        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(2, result.violations().size());
        assertEquals(DailyDrawdownRule.ID, result.violations().getFirst().ruleId());
        assertEquals(MaximumTotalDrawdownRule.RULE_ID, result.violations().get(1).ruleId());
    }

    @Test void dailyDrawdownBelowLimitPassesThroughTheFullRiskPipeline() {
        var result = evaluateDailyDrawdown("48500.01");

        assertEquals(RiskDecision.APPROVED, result.decision().orElseThrow());
        assertEquals(RuleStatus.PASS, result.ruleResults().getFirst().status());
        assertEquals(new BigDecimal("0.0299998"),
                result.globalMetrics().dailyDrawdownRatio().value());
    }

    @Test void dailyDrawdownAtLimitIsAnInclusiveBreach() {
        var result = evaluateDailyDrawdown("48500.00");

        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(RuleStatus.FAILURE, result.ruleResults().getFirst().status());
        assertEquals(new BigDecimal("0.03"),
                result.globalMetrics().dailyDrawdownRatio().value());
    }

    @Test void dailyDrawdownAboveLimitIsAnInclusiveBreach() {
        var result = evaluateDailyDrawdown("48499.99");

        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(RuleStatus.FAILURE, result.ruleResults().getFirst().status());
        assertEquals(new BigDecimal("0.0300002"),
                result.globalMetrics().dailyDrawdownRatio().value());
    }

    @Test void strictDailyDrawdownKeepsExactLimitSafeAndBreachesAboveIt() {
        var exact = new DeterministicRiskEngine("28.1", registry, clock).evaluate(
                context(rules(strictDailyRule("0.03")), new AccountSnapshot(ACCOUNT_ID, 3, NOW,
                        usd("48500"), usd("48500"), usd("0"), java.util.Optional.of(usd("50000")),
                        new DailyRiskBaseline(usd("50000"), NOW, "TEST", Map.of()), usd("0")), null));
        var above = new DeterministicRiskEngine("28.1", registry, clock).evaluate(
                context(rules(strictDailyRule("0.03")), new AccountSnapshot(ACCOUNT_ID, 3, NOW,
                        usd("48499.99"), usd("48499.99"), usd("0"), java.util.Optional.of(usd("50000")),
                        new DailyRiskBaseline(usd("50000"), NOW, "TEST", Map.of()), usd("0")), null));

        assertEquals(RiskDecision.APPROVED, exact.decision().orElseThrow());
        assertEquals(RuleStatus.PASS, exact.ruleResults().getFirst().status());
        assertEquals(RiskDecision.REJECTED, above.decision().orElseThrow());
        assertEquals(RuleStatus.FAILURE, above.ruleResults().getFirst().status());
    }

    @Test void totalDrawdownRetainsItsInclusiveBreachBoundary() {
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("50000"), usd("48500.01"),
                usd("0"), java.util.Optional.of(usd("50000")),
                new DailyRiskBaseline(usd("50000"), NOW, "TEST", Map.of()), usd("0"));
        var context = context(rules(rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT,
                RuleSeverity.BLOCKING, "0.03")), account, null);

        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

        assertEquals(RiskDecision.APPROVED, result.decision().orElseThrow());
        assertEquals(RuleStatus.PASS, result.ruleResults().getFirst().status());
    }

    @Test void totalDrawdownAtAndAboveItsBoundaryStillBreaches() {
        for (String equity : List.of("48500.00", "48499.99")) {
            var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("50000"), usd(equity),
                    usd("0"), java.util.Optional.of(usd("50000")),
                    new DailyRiskBaseline(usd("50000"), NOW, "TEST", Map.of()), usd("0"));
            var context = context(rules(rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT,
                    RuleSeverity.BLOCKING, "0.03")), account, null);

            var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

            assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
            assertEquals(RuleStatus.FAILURE, result.ruleResults().getFirst().status());
        }
    }

    @Test void krakenPropAdvanced50kUsesInclusiveDailyAndStaticTotalBoundaries() {
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("50000"), usd("48500"),
                usd("0"), java.util.Optional.of(usd("50000")),
                new DailyRiskBaseline(usd("50000"), NOW, "UTC/00:30", Map.of()), usd("0"));
        var context = context(rules(
                rule(DailyDrawdownRule.ID, RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, "0.03"),
                rule(MaximumTotalDrawdownRule.RULE_ID, RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, "0.03")),
                account, null);

        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);

        assertEquals(RiskDecision.REJECTED, result.decision().orElseThrow());
        assertEquals(List.of(DailyDrawdownRule.ID, MaximumTotalDrawdownRule.RULE_ID),
                result.violations().stream().map(RiskRuleResult::ruleId).toList());
    }

    @Test void approvesWithWarningsWithoutHidingThem() {
        var context = context(rules(rule(MaximumPositionRiskRule.ID,
                RuleCategory.POSITION, RuleSeverity.WARNING, "0.005")));
        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);
        assertEquals(RiskDecision.APPROVED_WITH_WARNINGS, result.decision().orElseThrow());
        assertEquals(1, result.warnings().size());
    }

    @Test void unknownMandatoryRuleFailsClosed() {
        var context = context(rules(rule("UNKNOWN", RuleCategory.COMPLIANCE,
                RuleSeverity.BLOCKING, "1")));
        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);
        assertEquals(EvaluationStatus.INCOMPLETE, result.evaluationStatus());
        assertTrue(result.decision().isEmpty());
    }

    @Test void storedEvaluationReplaysIdentically() {
        var context = context(rules(rule(MaximumExposureRule.ID,
                RuleCategory.PORTFOLIO, RuleSeverity.BLOCKING, "0.50")));
        var result = new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);
        assertTrue(new RiskReplayService("28.1", registry)
                .reproduces(new RiskEvaluationRecord(context, result)));
    }

    @Test void ruleFailureFailsClosedAndDoesNotPreventCompleteRuleTraversal() {
        RiskRule broken = new RiskRule() {
            @Override public String id() { return "BROKEN"; }
            @Override public RiskRuleResult evaluate(
                    com.hope.trading.risk.metric.RiskRuleEvaluationContext context,
                    com.hope.trading.risk.policy.RuleConfiguration configuration,
                    Instant time) {
                throw new ArithmeticException("unsafe input");
            }
        };
        var registryWithFailure = new RiskRuleRegistry(List.of(
                broken, new MaximumExposureRule()));
        var context = context(rules(
                rule("BROKEN", RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, "1"),
                rule(MaximumExposureRule.ID, RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.50")));
        var result = new DeterministicRiskEngine(
                "28.1", registryWithFailure, clock).evaluate(context);
        assertEquals(EvaluationStatus.FAILED, result.evaluationStatus());
        assertTrue(result.decision().isEmpty());
        assertEquals(2, result.ruleResults().size());
        assertEquals("evaluation-failed",
                result.ruleResults().getFirst().explanation().code());
    }

    @Test void informationalFailureNeverRejectsTrade() {
        var context = context(rules(rule(MaximumPositionRiskRule.ID,
                RuleCategory.POSITION, RuleSeverity.INFO, "0.005")));
        var result = new DeterministicRiskEngine("28.1", registry, clock)
                .evaluate(context);
        assertEquals(RiskDecision.APPROVED, result.decision().orElseThrow());
        assertEquals(RuleStatus.FAILURE, result.ruleResults().getFirst().status());
        assertEquals(RuleSeverity.INFO,
                result.ruleResults().getFirst().severity());
    }

    @Test void sameInputsAlwaysProduceSameResultAndOrdering() {
        var context = context(rules(
                rule(MaximumPositionRiskRule.ID, RuleCategory.POSITION,
                        RuleSeverity.BLOCKING, "0.02"),
                rule(MaximumExposureRule.ID, RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.50")));
        var engine = new DeterministicRiskEngine("28.1", registry, clock);
        assertEquals(engine.evaluate(context), engine.evaluate(context));
    }

    private RiskValidationResult evaluateDailyDrawdown(String equity) {
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("50000"), usd(equity),
                usd("0"), java.util.Optional.of(usd("50000")),
                new DailyRiskBaseline(usd("50000"), NOW, "TEST", Map.of()), usd("0"));
        var context = context(rules(rule(DailyDrawdownRule.ID, RuleCategory.ACCOUNT,
                RuleSeverity.BLOCKING, "0.03")), account, null);
        return new DeterministicRiskEngine("28.1", registry, clock).evaluate(context);
    }
}
