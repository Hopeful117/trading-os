# Story 0196 - Complete Invariant-Scoped Risk Evidence Admission

## Metadata

**ID:** `0196`
**Title:** Complete Invariant-Scoped Risk Evidence Admission
**Status:** Implementation complete - awaiting human review
**Priority:** P0 - Risk Engine
**Related ADRs:** `ADR-084`, `ADR-085`, `ADR-086`
**Related Stories:** `0195`

---

## Goal

Complete ADR-086 so that Trading Core admits evidence per configured risk
invariant, allowing independently provable diagnostics while keeping policy and
execution authorization strictly fail-closed.

## Context

Story 0195 introduced invariant requirement metadata, nullable normalized equity,
per-rule `INCOMPLETE` diagnostics and fail-closed aggregation. The current
admission path still treats the loaded risk snapshot primarily as one globally
complete or incomplete object. `ModeAwareRiskFactsProvider` and
`TradePlanRiskEvaluationService.requireBroker` can therefore reject the whole
evaluation before the Risk Domain evaluates independent rules.

## Scope

- Use `RiskFactRequirements.InvariantRequirements` in the actual Trading Core
  admission path.
- Preserve provider-neutral provenance, quality, freshness, identity and
  currency validation.
- Pass independently admissible evidence and invariant-specific incompleteness
  diagnostics to the deterministic Risk Domain.
- Keep `PASS`, `FAIL`/`FAILURE`, `INCOMPLETE` and `NOT_APPLICABLE` distinct.
- Preserve fail-closed aggregation and identical T0/T1 requirements.
- Add focused Risk Domain, Trading Core and Broker Service regression tests.
- Update Story 0195 status documentation to reflect the merged implementation
  and remaining scope.

## Non-Goals

- No change to FTMO thresholds, policy version or equity admissibility rules.
- No `equity = balance`, zero, stale or arbitrary fallback.
- No cTrader equity derivation or cTrader Algo bridge.
- No broker-specific policy logic in Broker Service or Risk Domain.
- No new service or generic proof framework.
- No frontend feature, LIVE call, order placement or provider mutation.

## Acceptance Criteria

- [ ] A policy with valid balance/positions and unavailable equity evaluates
      independent position-risk evidence and marks equity-dependent invariants
      `INCOMPLETE`.
- [ ] An inadmissible `BROKER_DERIVED` equity does not block independent
      invariants from producing diagnostics.
- [ ] Admissible fresh `BROKER_REPORTED` equity permits complete evaluation when
      all other mandatory facts are valid.
- [ ] Stale, incoherent, identity-mismatched, currency-mismatched or missing
      facts make only dependent invariants incomplete unless the snapshot
      coherence itself is compromised.
- [ ] A mandatory invariant that is not evaluated blocks the aggregate decision.
- [ ] Missing evidence never produces `NOT_APPLICABLE`, zero or `PASS`.
- [ ] T0 and T1 resolve and validate the same invariant requirements from fresh
      observations; changed T1 facts block execution.
- [ ] No incomplete or partial result reaches Broker Service submission.
- [ ] Existing Kraken, cTrader safety, policy, idempotency and reconciliation
      tests remain green.

## Safety Invariants

```text
PARTIAL_RESULT_IS_NOT_EXECUTION_AUTHORIZATION = true
INCOMPLETE_MANDATORY_INVARIANT = EXECUTION_BLOCKED
MISSING_FACT != NOT_APPLICABLE
MISSING_FACT != ZERO
EQUITY_BALANCE_FALLBACK = false
BROKER_DERIVED_FTMO_EQUITY = INADMISSIBLE
T0_REQUIREMENTS == T1_REQUIREMENTS = true
RISK_DOMAIN_PROVIDER_NEUTRAL = true
CTRADER_LIVE_CALLS = false
```

## Validation

- Focused `risk-domain` tests for invariant results and aggregation.
- Focused `trading-core` tests for admission, T0/T1 and no broker submission.
- Affected Broker Service contract and provider safety tests.
- Relevant module suites when contracts change.
- `git diff --check`.
