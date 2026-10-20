# Implementation Report - Story 0133

## Decision

**B - PARTIAL_IMPLEMENTATION**

Implemented only deterministic acquisition freshness, bounded outcome
maturation, and supported timestamp-coherence checks. Observation continuity,
separate replay/fee operational health, and unsupported aggregate heuristics
remain deferred.

## Changes

- Added `ResearchIntegrity` and fail-closed status values in Market Intelligence.
- Added bounded latest-snapshot and outcome cutoff-range repository queries.
- Added cached `ResearchIntegrityService` owned by Market Intelligence.
- Added authenticated and internal integrity endpoints:
  - `/api/v1/intelligence/research/integrity`
  - `/internal/v1/research/integrity`
- Added Gateway route coverage for the public endpoint.
- Added the Research Overview Acquisition Integrity projection.
- Kept Readiness and Results as separate UI sections.
- Added backend, controller, Gateway, and frontend tests.
- Fixed the Market Intelligence JWT filter so downstream application failures
  are not misreported as `401 Unauthorized`.
- Treated empty optional S1 fee-coverage environment values as absent.
- Added fail-closed dependency short-circuiting after the first remote replay or
  fee lookup failure, preventing repeated sequential timeout waits.
- Reduced the default Market Data read timeout for bounded readiness evidence
  from 5 seconds to 1 second; the value remains environment-configurable.

## Semantics

- Freshness derives from configured market scope, observation cadence, fixed
  delay, and latest persisted strict FeatureSnapshot cutoff.
- Maturation is limited to the configured horizons and a bounded recent window.
- Only existence, status, timing, and warning metadata are exposed for outcomes.
- Timestamp checks cover future snapshot cutoffs, outcome maturity mismatch,
  and impossible computed-at chronology supported by the current domain.
- Repository, configuration, or dependency failures produce `UNKNOWN` rather
  than `HEALTHY`.
- Replay and fee readiness semantics remain owned by the existing Readiness
  projection and are not duplicated.

## Scientific-Integrity Protection

- No S1 signal, population, threshold, regime, replay, fee, outcome, or
  FeatureProjection semantics were changed.
- No historical evidence was deleted, rewritten, regenerated, or repaired.
- No strategy-result or performance values were added to the integrity API or
  frontend.
- No performance values or performance evaluation were inspected during
  implementation or validation.

## Documentation Reconciliation

Documentation update: required and completed.

The Story, Repository Analysis, Implementation Plan, and this Implementation
Report document the new read-only API, bounded query strategy, status semantics,
deferred dimensions, validation, and scientific-integrity protections. No ADR
was created because ownership and persistence boundaries remain unchanged.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: current repository contracts and the approved Story provided the
  authoritative context; no curated transverse note was needed or modified.

## Validation

- `mvn -q -Dtest=ResearchIntegrityServiceTest,ResearchIntegrityControllerTest test`: passed.
- `mvn -q -Dtest=GatewayMarketIntelligenceRouteTest test`: passed.
- `mvn -q test` in `market-intelligence`: passed.
- `npm run test:ci -- --include src/app/features/research/research-overview.spec.ts`: passed.
- `npm run test:ci` in `trading-os-web`: passed, 52 files and 472 tests.
- `npm run build` in `trading-os-web`: passed.
- Prettier checks: passed.
- `git diff --check`: passed.
- Focused readiness, lifecycle-configuration, and controller tests after the
  runtime corrections: passed.

The frontend production build reports existing and new style/bundle budget
warnings, including the expanded Research Overview stylesheet; these are
warnings, not build failures.

## Runtime Validation

Authenticated local runtime validation completed after the corrections:

- Research Overview rendered with the existing local evidence.
- Readiness response: approximately 1.5 seconds on the final cold browser load
  and below 0.5 seconds when the dependent service is already resolved.
- Integrity response: approximately 0.1 seconds.
- No performance or strategy-result values were inspected or exposed.

## Repository Protection

- The pre-existing `docs/architecture/reports/v1-functional-audit.md` remains
  untouched and untracked.
- No commit, push, migration, or generated artifact was created.

## Independent Review Corrections

The independent review identified and the implementation now corrects:

- future-dated latest snapshots are excluded by the bounded repository lookup
  and classified as `UNKNOWN` defensively;
- Readiness renders immediately after its own response instead of waiting for
  Integrity completion;
- boundary coverage was added for future and exact degraded freshness;
- unbounded outcome lookup is explicitly rejected by the service test;
- public and internal integrity endpoint security coverage was added;
- Angular degraded integrity warnings are covered while Readiness remains
  independent.
