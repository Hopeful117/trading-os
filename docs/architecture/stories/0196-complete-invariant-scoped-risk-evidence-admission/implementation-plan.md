# Implementation Plan - Story 0196

## Overview

Complete the existing ADR-086 slice by carrying invariant-specific admission
diagnostics from Trading Core into the deterministic Risk Domain. The change
will remove the premature global rejection of a policy snapshot while retaining
strict structural/coherence checks and aggregate fail-closed authorization.

No broker-specific policy logic, risk-policy threshold, provenance rule or
provider execution behavior will change.

## Design

### 1. Represent invariant admission state at the existing Risk Domain boundary

Extend the existing risk evaluation context with a narrow immutable map of
mandatory invariant admission failures, keyed by rule ID. This is not a generic
proof framework: it is only the transport of Trading Core's already-resolved
policy evidence outcome into rule evaluation.

The Risk Domain will:

- emit `INCOMPLETE` for a configured rule with an admission failure;
- continue to use existing metric-based `evidenceAvailable()` checks;
- preserve `NOT_APPLICABLE` only for explicit rule applicability;
- leave normalized values and provider provenance interpretation outside the
  Risk Domain.

The context builder will preserve backward-compatible construction for callers
that have no admission diagnostics, while the T0 and T1 services will provide
the resolved map.

### 2. Resolve evidence by invariant in Trading Core

Add a small resolver close to `RiskFactRequirements` that evaluates each
`InvariantRequirements` entry against the normalized `RiskFactsProvider.Snapshot`.
The resolver will distinguish:

- structural snapshot failures that invalidate all invariants;
- missing or inadmissible facts for one invariant;
- facts complete enough for an independent invariant.

The resolver will reuse existing checks for:

- equity provenance, value, currency, timestamps, freshness, completeness and
  coherence;
- balance and starting-balance presence;
- positions and protective-stop availability;
- explicit historical fact availability where a rule requires it.

The provider will continue to preserve observations and reasons. It will not
decide policy applicability or execute risk rules.

### 3. Separate global structural rejection from invariant incompleteness

Update `ModeAwareRiskFactsProvider`, the T0 service and the T1 service so that:

- account identity mismatch, invalid source identity, invalid currency
  coherence, malformed observation timestamps and globally incoherent snapshots
  still fail closed for the whole evaluation;
- an absent/inadmissible equity does not reject position-risk diagnostics when
  balance, positions and stops are independently admissible;
- unavailable collections are never represented as authoritative empty facts;
- the same resolver and structural checks are used at T0 and T1.

`requireBroker` will be narrowed to structural safety and will no longer treat
every policy-dependent missing fact as a reason to prevent Risk Domain
diagnostics.

### 4. Preserve aggregate execution safety

Keep `RiskResultAggregator` unchanged unless a focused defect is discovered.
The aggregate result must remain without an executable decision when any
mandatory invariant is `FAILURE`, `INCOMPLETE` or unevaluated.

`ExecuteTradeService` and the existing T1 flow remain the final boundary before
`BrokerSubmissionStep`; an incomplete T1 result must return without submission.

### 5. Documentation reconciliation

- Update Story 0195 status/report only to reflect the merged first slice and its
  remaining scope; do not mark unverified criteria complete.
- Complete Story 0196 implementation and validation sections after tests pass.
- Do not modify ADR-086 unless implementation reveals a material contradiction.

The 10 pre-existing cTrader/0194/documentation modifications will not be
staged, rewritten or included.

## Expected Files

Risk Domain:

- `risk-domain/src/main/java/com/hope/trading/risk/context/RiskEvaluationContext.java`
- `risk-domain/src/main/java/com/hope/trading/risk/context/RiskEvaluationContextBuilder.java`
- `risk-domain/src/main/java/com/hope/trading/risk/engine/DeterministicRiskEngine.java`
- focused Risk Domain tests

Trading Core:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactRequirements.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactsProvider.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecutionTimeRiskRevalidationService.java`
- focused admission, T0, T1 and no-submission tests

Broker Service:

- only affected contract tests if a preservation regression is found;
- no provider logic change is planned.

Documentation:

- Story 0195 implementation/status documentation;
- Story 0196 implementation report after implementation;
- no ADR change expected.

## Test Plan

Risk Domain:

- complete equity evaluates all applicable rules;
- missing equity produces position-risk `PASS`/`FAIL` diagnostics and
  equity-dependent `INCOMPLETE` results;
- missing mandatory invariant admission blocks aggregation;
- explicit non-applicability remains distinct from missing facts.

Trading Core:

- `BROKER_REPORTED` fresh coherent equity is admissible;
- `BROKER_DERIVED` remains inadmissible for FTMO requirements;
- stale, incoherent, identity-mismatched and currency-mismatched facts affect
  the dependent invariants and block authorization;
- missing positions, stops or baseline do not become empty/zero facts;
- T0 and T1 use equivalent requirements;
- changed or incomplete T1 facts prevent broker submission.

Regression:

- Kraken risk snapshot and provenance tests;
- cTrader read-only and safety tests;
- existing policy, idempotency and reconciliation tests;
- `git diff --check`.

No credentials, provider LIVE call, order operation or safety flag activation
will be used.

## Stop Conditions

- If the current snapshot contract cannot distinguish unavailable collections
  from authoritative empty collections without a broader migration, stop and
  report the required architectural decision.
- If partial evaluation requires changing FTMO admissibility or policy
  thresholds, stop and report the contradiction.
- If an existing consumer requires a globally complete snapshot for a reason not
  represented in the current contracts, stop before weakening that invariant.
