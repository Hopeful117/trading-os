# IMPLEMENTATION-PLAN-ADR-028 — Risk Domain Architecture

**Related ADR:** ADR-028 — Risk Engine Architecture

**Status:** Implemented

**Implementation report:** `docs/implementation/ADR-028-implementation.md`

**Target:** Trading OS V1

---

# Purpose

This document defines the implementation roadmap for the Risk Domain introduced in ADR-028.

Unlike the ADR, which defines the architectural decisions and design principles, this implementation plan specifies **how the Risk Domain should be built**.

The implementation follows an incremental, domain-driven approach where each phase delivers a coherent and testable part of the system.

The primary goals are to:

- preserve deterministic behavior throughout development;
- keep the business domain independent from infrastructure;
- maximize testability;
- avoid premature optimization;
- produce a reusable foundation for future Trading OS components.

---

# Guiding Principles

The implementation must respect the following principles.

## Domain First

Business concepts must be implemented before infrastructure.

The domain model should remain independent of Spring, persistence, messaging, or broker integrations.

---

## Incremental Development

Each implementation phase should leave the project in a working and testable state.

Avoid implementing multiple architectural layers simultaneously.

---

## Single Responsibility

Every component should own one responsibility only.

Avoid mixing calculations, orchestration, persistence, or presentation.

---

## Determinism First

Every phase must preserve deterministic behavior.

Performance optimizations must never compromise reproducibility.

---

## Test Before Optimization

Correctness is more important than performance.

Optimization only begins after the complete business pipeline has been validated.

---

# Phase 1 — Domain Foundation

## Objective

Establish the ubiquitous language of the Risk Domain.

No business logic is implemented during this phase.

---

## Deliverables

```
risk-domain

├── RiskEvaluationRequest
├── RiskEvaluationContext
├── RiskValidationResult
├── RiskRuleResult
├── RiskDecision
├── EvaluationStatus
├── ValidationMode
├── RuleSeverity
├── RuleStatus
├── RuleCategory
├── RiskPolicy
├── EffectiveRiskRuleSet
└── Domain Value Objects
```

---

## Acceptance Criteria

- Domain compiles successfully.
- No infrastructure dependencies.
- No business calculations.
- No Spring annotations.
- Immutable domain objects wherever appropriate.

---

## Dependencies

None.

---

## Codex Should NOT

- Implement business rules.
- Add persistence.
- Create services.
- Introduce framework-specific code.

---

# Phase 2 — Context & Snapshots

## Objective

Introduce immutable business snapshots and the evaluation context.

---

## Deliverables

```
AccountSnapshot

PortfolioSnapshot

PositionSnapshot

MarketSnapshot

TradingContext

RuleSetSnapshot
```

```
RiskEvaluationContextBuilder
```

---

## Acceptance Criteria

- All snapshots are immutable.
- Context is built only through the builder.
- Risk Engine cannot construct contexts directly.
- No repository access from snapshots.

---

## Dependencies

Phase 1

---

## Codex Should NOT

- Compute metrics.
- Evaluate rules.
- Access external services from the Risk Engine.

---

# Phase 3 — Metrics Layer

## Objective

Centralize every financial calculation used by the Risk Domain.

---

## Deliverables

### Observed Metrics

- BalanceMetric
- EquityMetric
- FloatingPnLMetric
- MarginMetric
- FreeMarginMetric

### Projected Metrics

- ProjectedExposureMetric
- ProjectedDrawdownMetric
- ProjectedMarginMetric

### Derived Metrics

- RemainingRiskMetric
- PortfolioHeatMetric
- RiskUtilizationMetric

---

## Acceptance Criteria

- Rules do not perform financial calculations.
- Metrics are reusable.
- Metrics are deterministic.
- Unit tests validate every calculation.

---

## Dependencies

Phase 2

---

## Codex Should NOT

- Introduce business decisions.
- Simulate future trades.
- Implement Risk Rules.

---

# Phase 4 — Projection Engine

## Objective

Implement deterministic simulation of future account state.

---

## Deliverables

```
ProjectionEngine

↓

Projected Metrics
```

---

## Acceptance Criteria

Projection Engine correctly computes:

- projected exposure;
- projected drawdown;
- projected margin usage;
- projected portfolio state.

---

## Tests

- Opening a new position
- Increasing exposure
- Margin impact
- Drawdown impact
- Portfolio heat impact

---

## Dependencies

Phase 3

---

## Codex Should NOT

- Evaluate business rules.
- Produce authorization decisions.
- Predict market behavior.

---

# Phase 5 — Rule Infrastructure

## Objective

Build the reusable framework used by every Risk Rule.

---

## Deliverables

```
RiskRule

RiskRuleRegistry

RuleApplicability

RuleConfiguration

RiskRuleResult
```

---

## Acceptance Criteria

- Rules are independent.
- Rules are individually testable.
- Rules remain deterministic.
- Registry supports extensibility.

---

## Dependencies

Phase 4

---

## Codex Should NOT

- Implement concrete business rules.
- Introduce policy resolution.

---

# Phase 6 — First Business Rules

## Objective

Implement the first production-ready business rules.

---

## Initial Rules

- MaximumPositionRiskRule
- MaximumExposureRule
- DailyDrawdownRule

Additional rules will follow the same implementation pattern.

---

## Acceptance Criteria

- Rules consume metrics.
- Rules never compute financial values.
- Rules remain independent.
- Rule explanations are structured.

---

## Dependencies

Phase 5

---

## Codex Should NOT

- Modify Projection Engine.
- Add special-case logic to the Risk Engine.

---

# Phase 7 — Policy Resolution

## Objective

Implement policy resolution and effective rule set construction.

---

## Deliverables

```
Configured Policies

↓

Hierarchy Resolution

↓

Conflict Resolution

↓

EffectiveRiskRuleSet
```

---

## Acceptance Criteria

- Platform rules always take precedence.
- Rule conflicts are resolved before evaluation.
- EffectiveRuleSet is immutable.

---

## Dependencies

Phase 6

---

## Codex Should NOT

- Resolve policies during rule execution.
- Allow lower authorities to weaken higher-level constraints.

---

# Phase 8 — Risk Engine

## Objective

Assemble the complete deterministic evaluation pipeline.

---

## Pipeline

```
Request

↓

Context

↓

Observed Metrics

↓

Projection Engine

↓

Projected Metrics

↓

Derived Metrics

↓

Rule Evaluation

↓

Aggregation

↓

RiskValidationResult
```

---

## Acceptance Criteria

- Engine orchestrates only.
- No business calculations inside the engine.
- No repository access.
- Complete evaluation performed before aggregation.

---

## Dependencies

Phase 7

---

## Codex Should NOT

- Embed financial logic.
- Modify TradePlans.
- Execute trades.
- Introduce broker-specific behavior.

---

# Phase 9 — Audit & Traceability

## Objective

Persist complete evaluation metadata for replay and analysis.

---

## Deliverables

- EvaluationId
- CorrelationId
- EngineVersion
- RuleVersions
- PolicyVersions
- EvaluationDuration
- ContextMetadata
- TraceMetadata

---

## Acceptance Criteria

Every evaluation can be replayed later using the recorded information.

---

## Dependencies

Phase 8

---

## Codex Should NOT

- Introduce mutable audit records.
- Remove historical information.

---

# Phase 10 — Explainability

## Objective

Generate structured explanations for every business decision.

---

## Deliverables

```
RuleExplanation

DecisionExplanation

Business Metrics

Structured Messages
```

---

## Acceptance Criteria

Every rejected evaluation answers:

> Why was this decision produced?

without requiring source code inspection.

---

## Dependencies

Phase 9

---

## Codex Should NOT

- Generate explanations using AI.
- Produce opaque text.
- Lose structured business information.

---

# Phase 11 — Testing

## Objective

Validate correctness, determinism and long-term maintainability.

---

## Unit Tests

- Every Rule
- Every Metric
- Every Projection
- Every Policy Resolver
- Every Aggregator

---

## Integration Tests

Validate the complete evaluation pipeline.

---

## Replay Tests

Stored evaluations must always produce identical results.

---

## Regression Tests

Every bug must introduce a permanent regression test.

---

## Determinism Tests

Identical inputs must always produce identical outputs.

---

## Acceptance Criteria

- High domain test coverage.
- Stable deterministic behavior.
- Repeatable evaluations.

---

## Dependencies

All previous phases.

---

# Phase 12 — Performance Optimization

## Objective

Optimize the Risk Domain only after functional completion.

---

## Candidate Optimizations

- Internal caching
- Parallel rule execution
- Allocation reduction
- Profiling
- Performance benchmarks

---

## Acceptance Criteria

Optimizations must not modify:

- business decisions;
- evaluation order;
- auditability;
- determinism.

---

## Dependencies

Phase 11

---

# Definition of Done

The Risk Domain implementation is considered complete when:

- New rules can be added without modifying the Risk Engine.
- New brokers or prop firms can be configured through policies.
- Every evaluation is deterministic.
- Every evaluation is replayable.
- Financial calculations are centralized.
- The Risk Engine only orchestrates the evaluation pipeline.
- Business decisions are fully explainable.
- Audit information is complete.
- Test coverage validates every architectural layer.

---

# Future Extensions

The implemented architecture should support future capabilities without architectural changes, including:

- additional prop firms;
- multiple broker integrations;
- advanced portfolio risk models;
- correlation analysis;
- stress testing;
- scenario simulation;
- AI-assisted analytics;
- historical replay;
- risk dashboards;
- portfolio optimization recommendations.

These capabilities should extend the existing architecture rather than modify its core principles.
