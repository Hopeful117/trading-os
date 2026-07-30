package com.hope.trading.risk.engine;

import com.hope.trading.risk.audit.*;
import com.hope.trading.risk.context.RiskEvaluationContext;
import com.hope.trading.risk.domain.*;
import com.hope.trading.risk.domain.RiskTypes.*;
import com.hope.trading.risk.metric.*;
import com.hope.trading.risk.rule.*;
import java.time.*;
import java.util.*;

/** Explicit, deterministic orchestration of the ADR-028 evaluation pipeline. */
public final class DeterministicRiskEngine implements RiskEngine {
    private final String engineVersion;
    private final RiskRuleRegistry registry;
    private final Clock clock;
    private final ObservedMetricsCalculator observedCalculator;
    private final ProjectionEngine projectionEngine;
    private final DerivedMetricsCalculator derivedCalculator;
    private final MetricLimitResolver limitResolver;
    private final RiskMetricsAssembler metricsAssembler;
    private final RiskResultAggregator aggregator;

    public DeterministicRiskEngine(String engineVersion, RiskRuleRegistry registry, Clock clock) {
        this(engineVersion, registry, clock, new ObservedMetricsCalculator(),
                new ProjectionEngine(), new DerivedMetricsCalculator(),
                new MetricLimitResolver(), new RiskMetricsAssembler(),
                new RiskResultAggregator());
    }

    public DeterministicRiskEngine(
            String engineVersion, RiskRuleRegistry registry, Clock clock,
            ObservedMetricsCalculator observedCalculator, ProjectionEngine projectionEngine,
            DerivedMetricsCalculator derivedCalculator, MetricLimitResolver limitResolver,
            RiskMetricsAssembler metricsAssembler, RiskResultAggregator aggregator
    ) {
        this.engineVersion = Objects.requireNonNull(engineVersion);
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
        this.observedCalculator = Objects.requireNonNull(observedCalculator);
        this.projectionEngine = Objects.requireNonNull(projectionEngine);
        this.derivedCalculator = Objects.requireNonNull(derivedCalculator);
        this.limitResolver = Objects.requireNonNull(limitResolver);
        this.metricsAssembler = Objects.requireNonNull(metricsAssembler);
        this.aggregator = Objects.requireNonNull(aggregator);
    }

    @Override public RiskValidationResult evaluate(RiskEvaluationContext context) {
        Objects.requireNonNull(context);
        Instant evaluatedAt = clock.instant();

        ObservedMetrics observed = observedCalculator.calculate(context.account());
        ProjectedMetrics projected = projectionEngine.project(
                context.account(), context.portfolio(), context.request().proposedTrade());
        DerivedMetrics derived = derivedCalculator.calculate(
                context.account(), observed, projected, context.request().proposedTrade(),
                limitResolver.maximumPositionRisk(context.ruleSet()));
        RiskMetrics metrics = metricsAssembler.assemble(observed, projected, derived);
        RiskRuleEvaluationContext ruleContext =
                new RiskRuleEvaluationContext(context.request().mode(),
                        context.request().proposedTrade() != null, metrics);

        List<RiskRuleResult> results = new ArrayList<>();
        boolean incomplete = false;
        boolean failed = false;
        for (var configuration : context.ruleSet().rules()) {
            Optional<RiskRule> candidate = registry.find(configuration.ruleId());
            if (candidate.isEmpty()) {
                incomplete = true;
                results.add(new RiskRuleResult(configuration.ruleId(),
                        configuration.ruleVersion(), RuleStatus.FAILURE,
                        configuration.severity(),
                        new RuleExplanation("rule-implementation-unavailable", Map.of()),
                        Map.of(), evaluatedAt, Map.of()));
                continue;
            }
            RiskRule rule = candidate.get();
            try {
                results.add(rule.supports(ruleContext)
                        ? rule.evaluate(ruleContext, configuration, evaluatedAt)
                        : new RiskRuleResult(configuration.ruleId(),
                            configuration.ruleVersion(), RuleStatus.NOT_APPLICABLE,
                            configuration.severity(),
                            new RuleExplanation("not-applicable", Map.of()), Map.of(),
                            evaluatedAt, Map.of()));
            } catch (RuntimeException exception) {
                failed = true;
                results.add(new RiskRuleResult(configuration.ruleId(),
                        configuration.ruleVersion(), RuleStatus.FAILURE,
                        configuration.severity(),
                        new RuleExplanation("evaluation-failed", Map.of()),
                        Map.of(), evaluatedAt, Map.of(
                            "errorType", exception.getClass().getSimpleName())));
            }
        }

        RiskResultAggregator.Aggregation aggregation =
                aggregator.aggregate(results, incomplete, failed);
        return new RiskValidationResult(aggregation.status(), aggregation.decision(),
                results, aggregation.violations(), aggregation.warnings(), metrics,
                context.request().mode(), Duration.ZERO, evaluatedAt, trace(context));
    }

    private TraceMetadata trace(RiskEvaluationContext context) {
        Map<String, String> ruleVersions = new LinkedHashMap<>();
        context.ruleSet().rules().forEach(r ->
                ruleVersions.put(r.ruleId(), r.ruleVersion()));
        return new TraceMetadata(context.request().evaluationId(),
                context.request().correlationId(), engineVersion,
                context.ruleSet().policyVersions(), ruleVersions,
                new ContextMetadata(context.account().accountId(),
                    context.account().version(), context.portfolio().portfolioId(),
                    context.portfolio().version(), context.market().version(),
                    context.ruleSetSnapshot().version(), context.account().capturedAt(),
                    context.portfolio().capturedAt(), context.market().capturedAt(),
                    context.ruleSetSnapshot().capturedAt()));
    }
}
