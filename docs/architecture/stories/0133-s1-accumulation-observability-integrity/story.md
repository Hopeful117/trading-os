# Story 0133 - S1 Accumulation Observability and Integrity

## Status

Implemented locally - pending human review.

## Goal

Expose the smallest trustworthy deterministic observability surface that can
answer whether the ongoing S1 evidence accumulation remains fresh, continuous
enough to interpret, and usable for the future frozen evaluation.

This Story observes the research pipeline. It does not evaluate S1 results,
change frozen experimental semantics, or redefine Research Readiness.

## Scope

- Investigate and expose bounded acquisition-freshness evidence for the
  configured research market universe.
- Investigate and expose bounded outcome-maturation evidence based only on
  outcome existence, status, and timing.
- Add only timestamp-coherence checks supported by existing lifecycle
  semantics.
- Keep Acquisition Integrity separate from Experimental Readiness and Results.
- Keep Market Intelligence as the owner of the deterministic read model.
- Extend the authenticated Research Overview only with a compact projection of
  the backend integrity read model if the backend contract is safe.
- Reuse existing cache and API boundary conventions where appropriate.

## Constraints

- Do not inspect, expose, optimize, or evaluate S1 performance outcomes.
- Do not modify signal definitions, population rules, readiness thresholds,
  regime semantics, replay trust, fee semantics, outcome semantics,
  FeatureProjection semantics, or frozen pre-registration criteria.
- Do not introduce a new database table for derived health state.
- All queries must be bounded, market-scoped, time-scoped, and suitable for
  repeated read-only observability.
- Angular must project authoritative backend states and must not calculate
  integrity semantics.
- Fail closed when freshness, configuration, or dependency state is unknown.
- Preserve unrelated changes and the protected functional audit file.
- Do not commit automatically.

## Explicit Decision

The repository analysis currently recommends **B - PARTIAL_IMPLEMENTATION**:
implement only acquisition freshness, outcome maturation, and rigorously
supported timestamp coherence. Defer continuity, separate replay/fee
operational health, and any aggregate semantics that cannot be derived without
new domain assumptions.

This decision is subject to human approval of the Repository Analysis and a
subsequent Implementation Plan where required.

## Out of Scope

- S1 performance evaluation or result reporting.
- Experiment Detail or Evidence Explorer.
- Backtesting, optimization, strategy comparison, or trading behavior.
- Historical evidence rewriting or repair.
- Generic monitoring or data-quality infrastructure.
- A new persistence model for historical integrity events.
- Actuator health as a substitute for research integrity.
- A new ADR unless implementation reveals a durable architectural decision.

## Validation Expectations

- Focused Market Intelligence tests for deterministic freshness, maturation,
  timestamp, failure, and fail-closed boundaries.
- Focused Research Overview projection tests.
- Affected module build and repository-standard frontend validation.
- Bounded query-shape review and `git diff --check`.
- Read-only runtime validation when the stack and credentials support it.
- No performance metrics or strategy-result inspection.
