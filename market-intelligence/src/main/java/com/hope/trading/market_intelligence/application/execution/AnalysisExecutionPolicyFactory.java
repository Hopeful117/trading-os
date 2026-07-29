package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.capability.DisabledAiAnalysisCapability;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AnalysisExecutionPolicyFactory {
    public AnalysisExecutionPolicy from(AnalysisExecutionPlan plan) {
        Map<String, CapabilityPriority> priorities = plan.capabilityIds().stream()
                .collect(Collectors.toUnmodifiableMap(
                        capability -> capability,
                        capability -> capability.equals(DisabledAiAnalysisCapability.CAPABILITY_ID)
                                ? CapabilityPriority.OPTIONAL
                                : CapabilityPriority.MANDATORY
                ));
        return new AnalysisExecutionPolicy(
                plan.timeout().multipliedBy(Math.max(1, plan.maximumCapabilities())),
                plan.timeout(),
                1,
                Math.min(plan.maximumCapabilities(), 4),
                new ContextLimits(
                        Math.max(1, plan.baselineContext().size() + 8),
                        10_000,
                        1_000,
                        4,
                        ContextClassification.TRADING_SENSITIVE
                ),
                new RetryPolicy(2, plan.timeout(), Set.of(RetryClassification.RETRYABLE)),
                priorities,
                new DegradationPolicy(true, true, true, true)
        );
    }
}
