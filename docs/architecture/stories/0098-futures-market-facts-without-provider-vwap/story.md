# Story 0098: Futures Market Facts Without Provider VWAP

## Metadata

**ID:** `0098`
**Title:** Futures Market Facts Without Provider VWAP
**Status:** APPROVED - IMPLEMENTATION IN PROGRESS
**Size:** Medium
**Risk:** Medium
**Predecessor:** Story 0097 - Complete Kraken LINEAR_PERPETUAL Market Data
**Related:** Story 0069 - Market Facts Foundation; Story 0070 - Market Eligibility Filtering

## Goal

Allow supported Kraken `LINEAR_PERPETUAL` markets to pass deterministic Market
Facts activity validation when the provider supplies complete OHLCV candles but
does not supply a candle VWAP, without fabricating a VWAP value or weakening
the existing evidence gates.

The result must make the Futures scanner usable for complete provider evidence
while preserving the distinction between provider VWAP and a derived activity
price basis.

## Context

The Futures Charts API currently returns candles containing `time`, `open`,
`high`, `low`, `close`, and `volume` for the `trade` tick type. The observed
payload does not contain `vwap` or `trades`. The current adapter maps the
missing value to `OhlcEvent.vwap = null`.

`MarketFactsService` currently calculates activity as the sum of completed
`volume * vwap` and treats a candle with a missing VWAP as incomplete. The
Active Scan eligibility policy consequently returns:

```text
marketFactsStatus = AVAILABLE
activityStatus = INSUFFICIENT_DATA
readinessStatus = AVAILABLE
eligibility reason = INSUFFICIENT_HISTORY
```

This was reproduced for a live `LINEAR_PERPETUAL` market. The same symbol's
Spot market passes the eligibility preflight with the existing calculation.

The raw provider evidence is authoritative for this limitation. OHLC values
cannot reconstruct a true volume-weighted average price, and a live trade feed
cannot provide a reliable bounded historical backfill for a cold scanner
request.

## Problem

The scanner reports `INSUFFICIENT_HISTORY` even when Futures historical OHLCV
coverage is present. The message is technically consistent with the current
fact calculation but does not identify that the blocking evidence is a missing
provider VWAP.

Writing `close`, typical price, or another OHLC proxy into the `vwap` field
would create a false provider fact and make the existing `quoteNotional`
meaning ambiguous.

## Chosen Direction

Use an explicit Futures activity calculation based on provider OHLCV evidence:

```text
derived quote notional = sum(completed candle volume * completed candle close)
```

This is an activity price-basis fallback, not a VWAP. The implementation must:

- preserve `OhlcEvent.vwap = null` when Kraken does not provide VWAP;
- identify the calculation basis and version in the provider-neutral Market
  Facts contract or equivalent provenance;
- use the fallback only for the supported Futures product and only when volume,
  close, candle completion, and temporal coverage are valid;
- keep incomplete, stale, synthetic, conflicting, or malformed evidence
  non-available;
- preserve the existing Spot `volume * vwap` calculation and behavior;
- avoid using the OHLC typical price as a claimed VWAP;
- avoid adding a Spot endpoint fallback or provider-specific details to Market
  Intelligence.

If this changes the authoritative semantics of the existing generic
`MarketActivityFact.quoteNotional` contract, implementation must add or update
the required ADR before changing that contract. A contract version change is
required whenever consumers could otherwise confuse the derived close-basis
value with a true VWAP-based value.

## Scope

### In Scope

- Document the Kraken Futures OHLCV limitation and the selected activity basis.
- Define a provider-neutral representation of the activity calculation basis
  and its version.
- Implement Futures activity evaluation using complete `volume * close` data
  without populating the VWAP field.
- Preserve the existing Spot VWAP-based activity calculation.
- Keep readiness and activity completeness gates deterministic and explicit.
- Ensure Active Scan eligibility can accept complete Futures activity evidence.
- Preserve `INSUFFICIENT_DATA` for missing volume, missing close, missing
  candles, stale evidence, synthetic continuity, or conflicting duplicates.
- Add focused unit, integration, contract, and runtime validation tests.
- Verify that the scanner exposes a supported Futures market as eligible when
  the configured evidence requirements are met.

### Out of Scope

- Claiming or reconstructing a true historical Futures VWAP from OHLC data.
- Historical trade replay or a durable tick-level trade store.
- Kraken derivative order placement, leverage, margin, funding, liquidation,
  or settlement behavior.
- Changes to scanner ranking, strategy evaluation, risk, or execution rules.
- Changes to Spot OHLC acquisition or Spot Market Facts semantics.
- Relaxing freshness, completeness, synthetic-candle, duplicate, or conflict
  safeguards.
- Introducing a provider-specific public API or leaking Kraken DTOs.

## Domain Constraints

- Market Data remains the owner of provider acquisition, normalized OHLC, and
  derived Market Facts.
- Market Intelligence consumes only the typed provider-neutral Market Facts
  contract and must not inspect Kraken payloads or choose the fallback basis.
- A derived close-basis activity value must never be serialized or labelled as
  provider VWAP.
- The same inputs and calculation basis must produce the same result.
- Existing consumers must be able to distinguish true VWAP-based activity from
  close-basis activity.
- The implementation must not turn missing analytical evidence into `AVAILABLE`
  without recording the selected calculation basis.

## Acceptance Criteria

- [ ] The raw Kraken Futures Charts payload and its missing-VWAP behavior are
      covered by an adapter-level test.
- [ ] Futures OHLC events preserve `vwap=null` when the provider omits VWAP.
- [ ] A complete Futures OHLCV history produces a deterministic activity fact
      using the explicit close-basis calculation.
- [ ] The activity fact exposes a provider-neutral calculation basis and version
      sufficient for consumers to distinguish close-basis activity from true
      VWAP-based activity.
- [ ] Spot activity continues to use provider VWAP and remains behaviorally
      unchanged.
- [ ] Missing volume or close, incomplete temporal coverage, stale evidence,
      synthetic candles, and conflicting duplicates remain non-available.
- [ ] Active Scan eligibility accepts a Futures market only when the selected
      activity basis has complete and fresh evidence.
- [ ] Active Scan continues to report explicit `INSUFFICIENT_HISTORY` or
      `DATA_UNAVAILABLE` when the required Futures evidence is not available.
- [ ] No test or implementation writes close, typical price, or another proxy
      into `OhlcEvent.vwap`.
- [ ] Provider-specific DTOs, symbols, and fallback decisions do not leak into
      Market Intelligence or public APIs.
- [ ] At least one live tradable Kraken perpetual passes the Market Facts
      preflight and selected Futures scanner path with runtime evidence.
- [ ] Existing Spot Market Data and scanner tests remain operational.
- [ ] No Risk, Challenge, or execution authorization rule is weakened.

## Relevant Decisions and Artifacts

- ADR-048 - Trading OS Product and Architecture Principles.
- ADR-053 - Provider-Neutral Market Identity and Multi-Product Adapters.
- Story 0069 - Market Facts Foundation.
- Story 0070 - Market Eligibility Filtering.
- Story 0097 - Complete Kraken LINEAR_PERPETUAL Market Data.
- `docs/architecture/reports/story-0069-audit-remediation.md` - explicit
  unusable-metric and zero-volume semantics.

If the selected calculation basis requires a durable generic contract change
not covered by these decisions, an ADR must be proposed and accepted before
implementation proceeds.

## Validation

- Official Kraken Futures historical-data documentation review with captured
  payload evidence.
- Focused Futures DTO and mapper tests for omitted VWAP.
- Market Facts tests for VWAP-based Spot activity and close-basis Futures
  activity.
- Tests for missing close/volume, incomplete history, stale data, synthetic
  candles, duplicate conflicts, and calculation-version provenance.
- Market Intelligence eligibility contract tests for eligible and non-evaluable
  Futures decisions.
- Full `market-data` module validation with `mvn verify`.
- Relevant `market-intelligence` module validation.
- `git diff --check`.
- Runtime validation through Market Data, Gateway, and the frontend scanner.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Any required ADR approved.
- [ ] Implementation completed.
- [ ] Documentation reconciled.
- [ ] Relevant validation executed.
- [ ] Live Futures scanner acceptance completed.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
