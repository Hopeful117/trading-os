# Story 0101 - Bounded Fresh Provider-Neutral Valuation for PRE_TRADE and T1

## Metadata

- **ID:** `0101`
- **Status:** APPROVED - IMPLEMENTATION IN PROGRESS
- **Related Stories:** `0008`, `0096`, `0097`, `0098`, `0100`
- **Related ADRs:** `ADR-028`, `ADR-051`, `ADR-052`, `ADR-053`, `ADR-054`, `ADR-055`

## Goal

Provide a bounded, provider-neutral fresh valuation path for derivative
`PRE_TRADE`/T0 and execution-time T1 decisions. A valid cached observation may
be reused, while a missing or stale observation must trigger bounded provider
acquisition without allowing stale provider data to authorize Risk.

## Context

The current Market Data valuation path refreshes a market through the provider,
then evaluates persisted `PriceObservation` records against `effectiveAt` and
the existing `PT5M` valuation policy. Kraken Futures can return a successful
REST response whose source timestamp is already stale. The current path then
fails closed even though a bounded Futures ticker WebSocket alternate could
potentially provide a newer observation.

The accepted architecture is recorded in `ADR-055`.

## Scope

- Add a provider-neutral bounded fresh-valuation acquisition contract.
- Preserve reuse of an observation that already satisfies the requested policy.
- Implement bounded REST-first acquisition for the existing Market Data path.
- Use the existing provider-neutral ticker subscription boundary as a bounded
  alternate where supported, with temporary reference cleanup.
- Preserve `PriceObservation` persistence and source-time/no-lookahead checks.
- Expose structured acquisition outcomes: `FRESH`, `STALE_PROVIDER`,
  `UNAVAILABLE`, and `TIMEOUT`.
- Pass explicit provider-neutral T0/T1 valuation requirements from Trading Core.
- Add local coalescing, deadline bounds, diagnostics, and focused tests.
- Validate the manual PAPER `CASH + LINEAR_PERPETUAL` lifecycle after the
  focused implementation checks pass.

## Out Of Scope

- Risk rule, Risk policy, Challenge, settlement, or execution lifecycle changes.
- Scanner strategy or Market Intelligence redesign.
- OHLC, Market Facts, Opportunity, or scan-result substitution for valuation.
- Permanent catalogue-wide Futures subscriptions.
- Live broker execution.
- Frontend redesign.
- Increasing the existing T0 freshness age to hide stale provider data.

## Constraints

- Provider-specific symbols, payloads, retry rules, and transport decisions stay
  inside Market Data adapters.
- Risk remains deterministic, read-only, provider-neutral, and fail-closed.
- Provider `effectiveAt` remains the primary freshness authority; recent local
  `capturedAt` cannot make an old observation fresh.
- T0 continues to use the existing `valuation.max-observation-age` policy.
- T1 must use `CONFIRM_OR_ACQUIRE` with the approved provider-neutral maximum
  source age of `PT30S`; it must not use a Kraken-specific policy.
- Acquisition must stop at the caller deadline and the Market Data safety
  maximum, whichever comes first.
- No execution side effect may be retried or duplicated by valuation
  acquisition.
- Existing untracked runtime reports must remain untouched and outside the
  production implementation diff.
- No commit or push is performed automatically.

## Acceptance Criteria

- [ ] A fresh cached source observation is reused without an unnecessary provider
      refresh.
- [ ] A missing observation triggers bounded provider acquisition and persists a
      fresh normalized observation before valuation completes.
- [ ] A stale local observation can be replaced by a newer policy-valid source
      observation.
- [ ] A stale REST response may use the bounded ticker alternate when supported.
- [ ] Provider-stale, unavailable, and timeout outcomes are distinct at the
      Market Data boundary and all fail closed through the existing Risk reason.
- [ ] T0 uses the existing maximum source-age policy and remains
      `CURRENT_MARKET_VALUATION_UNAVAILABLE` when required facts are not fresh.
- [ ] T1 independently confirms/acquires required current facts and uses the
      approved stricter provider-neutral policy.
- [ ] Future source timestamps, late captures, incomplete bid/ask facts, and
      stale conversion legs remain unavailable.
- [ ] Concurrent compatible requests coalesce without uncontrolled provider
      amplification; temporary subscriptions are released on every exit path.
- [ ] Existing Spot valuation behavior remains provider-neutral and unchanged.
- [ ] Focused Market Data and Trading Core tests pass.
- [ ] Manual PAPER runtime validation proves T0/T1, entry, ADR-054 outbox,
      mark-to-market, close, and Challenge convergence behavior.

## Expected Validation

- Focused `market-data` valuation, acquisition, provider, and subscription tests.
- Focused `trading-core` valuation-client and T0/T1 orchestration tests.
- Module-level Maven validation for affected services where executable.
- `git diff --check`.
- Runtime manual PAPER lifecycle with structured acquisition outcomes and
  valuation provenance retained.

## Definition Of Done

- [ ] ADR-055 accepted.
- [ ] Implementation completed within this Story boundary.
- [ ] Documentation reconciled.
- [ ] Focused and applicable broader validation reported accurately.
- [ ] Runtime PAPER acceptance completed or explicitly reported as blocked.
- [ ] Independent code review completed.
- [ ] Human commit created.
