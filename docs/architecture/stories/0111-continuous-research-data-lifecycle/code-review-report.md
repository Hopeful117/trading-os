# Code Review Report - Story 0111

Date: 2026-10-16 · Review type: implementation-state review, pending human acceptance

## Verdict

**NEEDS HUMAN REVIEW**

The current implementation preserves the strict research boundary and reuses
existing deterministic services. Human acceptance remains outstanding.

## Findings and Remediation

### Resolved: Readiness Horizon Scope

`ResearchReadinessService` now filters persisted outcomes to the configured
outcome horizons before calculating status counts, eligible outcomes, and the
approximate available population.

### Resolved: Persistence and Concurrency Coverage

Persistence coverage now verifies Flyway startup through V16, strict execution
scope rehydration after persistence, and concurrent duplicate execution
insertion through the durable idempotency key. The JPA adapter returns the
durable existing execution when the database uniqueness boundary wins a race.

### Remaining: Quality Gate Failed

SonarQube project `trading-os:market-intelligence` reported:

- new-code coverage: `77.6%`, required `80%`;
- new violations: `65`, required `0`;
- new duplicated lines density: `0.05085%`, passed.

The violations span broader existing Market Intelligence and research changes,
not only the lifecycle files. They remain outside this focused remediation and
must not be silently expanded into this Story.

### Remaining: Runtime Validation Not Executed

The lifecycle is disabled by default and no live enablement was performed. The
end-to-end sequence from captured evidence through strict execution, snapshot,
and matured outcome therefore remains operationally unverified.

## Positive Review Observations

- Cutoffs are aligned to UTC cadence boundaries.
- New executions explicitly use `STRICT_OBSERVED` and persist the evaluation
  cutoff and evidence policy.
- Existing snapshot and outcome services remain authoritative.
- Outcome evaluation is guarded by `now >= T0 + H`.
- Restart and duplicate behavior use persisted scope and deterministic
  idempotency/fingerprint boundaries.
- The default configuration does not activate acquisition implicitly.

## Validation Evidence

- `mvn -q -Dtest=ResearchLifecycleServiceTest,ResearchLifecyclePropertiesTest test` - passed.
- `mvn -q verify -DskipTests=false` - passed.
- `git diff --check` - passed.
- SonarQube analysis with `-Dsonar.qualitygate.wait=true` - executed, Quality Gate failed as documented above.

## Residual Risks

- No deployed or local enabled lifecycle run has been validated.
- Readiness freshness is derived from the existing Market Data read model.
- Project-level quality debt remains unresolved outside the clearly scoped
  lifecycle implementation.
- Human review, approval, commit, and integration remain outstanding.

## Updated Validation Evidence

- `mvn -q -Dtest=ResearchReadinessServiceTest,ResearchLifecycleServiceTest,ResearchLifecyclePropertiesTest,DurableExecutionPersistenceTest test` - passed.
- `mvn -q verify -DskipTests=false` - passed.
- `git diff --check` - passed.

## Approval Boundary

This report is a review record and recommendation. It does not grant approval
or authorize commit, push, merge, or pull-request creation.
