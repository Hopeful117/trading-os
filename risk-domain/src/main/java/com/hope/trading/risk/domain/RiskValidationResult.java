package com.hope.trading.risk.domain;

import com.hope.trading.risk.audit.TraceMetadata;
import com.hope.trading.risk.domain.RiskTypes.*;
import com.hope.trading.risk.metric.RiskMetrics;
import com.hope.trading.risk.rule.RiskRuleResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RiskValidationResult(
        EvaluationStatus evaluationStatus, Optional<RiskDecision> decision,
        List<RiskRuleResult> ruleResults, List<RiskRuleResult> violations,
        List<RiskRuleResult> warnings, RiskMetrics globalMetrics,
        ValidationMode evaluationMode, Duration evaluationDuration,
        Instant evaluatedAt, TraceMetadata trace
) {
    public RiskValidationResult {
        Objects.requireNonNull(evaluationStatus); decision = Objects.requireNonNull(decision);
        ruleResults = List.copyOf(ruleResults); violations = List.copyOf(violations);
        warnings = List.copyOf(warnings); Objects.requireNonNull(globalMetrics);
        Objects.requireNonNull(evaluationMode); Objects.requireNonNull(evaluationDuration);
        Objects.requireNonNull(evaluatedAt); Objects.requireNonNull(trace);
        if (evaluationStatus == EvaluationStatus.COMPLETED && decision.isEmpty()
                || evaluationStatus != EvaluationStatus.COMPLETED && decision.isPresent()) {
            throw new IllegalArgumentException("Decision only exists for completed evaluation");
        }
    }
}
