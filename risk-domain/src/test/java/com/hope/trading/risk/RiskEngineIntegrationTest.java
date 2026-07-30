package com.hope.trading.risk;

import com.hope.trading.risk.audit.*;
import com.hope.trading.risk.engine.*;
import com.hope.trading.risk.explain.DecisionExplainer;
import com.hope.trading.risk.rule.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;

import static com.hope.trading.risk.RiskFixture.*;
import static com.hope.trading.risk.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskEngineIntegrationTest {
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RiskRuleRegistry registry = new RiskRuleRegistry(List.of(
            new MaximumPositionRiskRule(), new MaximumExposureRule(),
            new DailyDrawdownRule()));

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
}
