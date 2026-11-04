# Story 0197 - Experimental cTrader Derived Equity Reconstruction and Validation

## Metadata

**ID:** `0197`
**Title:** Experimental cTrader Derived Equity Reconstruction and Validation
**Status:** Review findings corrected - awaiting follow-up code review
**Priority:** P0 - FTMO first-trade readiness
**Related ADRs:** `ADR-082`, `ADR-084`, `ADR-085`, `ADR-086`
**Related Stories:** `0193`, `0194`, `0195`, `0196`

---

## Goal

Build an isolated deterministic experimental prototype that reconstructs a
candidate cTrader account equity from officially available observations,
preserves the calculation evidence and uncertainty, and detects incomplete or
temporally incoherent observations.

The prototype is investigative only. It must never become a Risk Engine input,
change FTMO policy admission, authorize T0/T1 execution, or place a broker
order.

## Context

cTrader Open API documents current account balance, open positions and
position-level unrealized PnL in the deposit currency. It does not document a
general current-account equity field in `ProtoOATrader`. Position commission,
swap and mirroring commission fields exist, but their relationship to the
documented net unrealized PnL and FTMO equity semantics is not yet sufficient
for admission.

The current cTrader observation is assembled from multiple sequential provider
responses and is not an atomic financial snapshot. The current normalized
equity therefore remains unavailable for FTMO admission.

## Scope

- Add an isolated experimental deterministic equity reconstruction model.
- Start with the hypothesis `balance + sum(netUnrealizedPnL)`.
- Preserve BigDecimal precision, account identity, currency, position identity,
  source timestamps, collection window, calculation version and diagnostics.
- Compare explicitly documented accounting hypotheses without selecting an
  unvalidated hypothesis as authoritative.
- Detect missing, duplicate, mismatched, stale and temporally incoherent facts.
- Reuse existing cTrader observation types where practical.
- Add deterministic fixtures and tests for accounting, completeness and
  temporal consistency.
- Prepare a read-only independent-validation protocol without connecting to a
  broker during this Story.

## Out of Scope

- Making any derived equity admissible for `FTMO_2STEP_50K_RISK`.
- Changing FTMO thresholds, policy versions or provenance admission rules.
- Feeding the experimental value into `RiskFactsProvider`, T0, T1 or execution.
- Changing `CTRADER_SAFETY_EXECUTION_ENABLED`.
- Any authenticated broker call, provider mutation or order operation.
- Any cTrader Algo bridge, private FTMO endpoint, new microservice or new
  provider-specific logic in the Risk Domain.
- Replacing the existing fail-closed cTrader equity behavior.
- Creating an approved financial contract or amending ADRs.

## Experimental Safety Invariants

```text
EXPERIMENTAL_EQUITY_IS_NOT_RISK_EVIDENCE = true
EXPERIMENTAL_EQUITY_IS_NOT_EXECUTION_AUTHORIZATION = true
BROKER_REPORTED_PROVENANCE_IS_UNCHANGED = true
FTMO_DERIVED_EQUITY_ADMISSION_IS_UNCHANGED = true
EQUITY_BALANCE_FALLBACK_IN_PRODUCTION = false
CTRADER_SAFETY_EXECUTION_ENABLED = false
BROKER_AUTHENTICATED_CALLS = false
T0_T1_BEHAVIOR_IS_UNCHANGED = true
```

## Acceptance Criteria

- [ ] A deterministic prototype computes a candidate value only from complete,
      identity-matched balance and position PnL observations.
- [ ] All monetary calculations use `BigDecimal` and preserve source precision.
- [ ] Missing balance, currency, position identity or PnL produces an explicit
      non-success diagnostic.
- [ ] Missing, duplicate or unmatched position/PnL identifiers are rejected.
- [ ] The prototype preserves source timestamps, observation window and
      calculation version.
- [ ] Balance version changes, position-set changes, stale timestamps and
      partial responses produce explicit consistency diagnostics.
- [ ] Swap, commission and mirroring commission are not silently added or
      discarded; each remains explicitly classified as included, excluded or
      semantically unknown.
- [ ] The prototype result is not represented as `RiskFactsProvider.EquityFact`
      and cannot enter T0, T1 or Broker Service submission.
- [ ] Existing `UNAVAILABLE` cTrader equity and FTMO policy admission remain
      unchanged.
- [ ] Tests cover positive candidate calculation and all required incomplete,
      duplicate, mismatch, stale and incoherent cases.
- [ ] No test performs an authenticated broker call or order operation.
- [ ] `git diff --check` passes.

## Validation Expectations

- Prototype unit tests.
- Affected Broker Service cTrader mapper tests.
- Trading Core regression tests only if a shared contract is changed.
- `git diff --check`.

## Required Follow-up

The prototype must produce enough evidence to decide whether a future ADR or
ADR-085 amendment should define a versioned, independently validated
`BROKER_DERIVED` contract. This Story does not make that decision.
