# Story 0068 - User Market Discovery V1

## Metadata

**ID:** `0068`
**Title:** User Market Discovery V1
**Status:** IMPLEMENTED - HUMAN REVIEW REQUIRED
**Size:** MEDIUM
**Implementation Risk:** MEDIUM

## Goal

Make the existing market catalogue practical for human exploration by providing
reusable, reactive, user-controlled filtering and stable sorting over canonical
`MarketResponse` fields.

Story 0068 implements **Level 1 — User Market Discovery** only.

```text
User Market Scope
    ↓
future Market Candidate Selection
    ↓
Active Scan
    ↓
Market Intelligence
    ↓
Trend Context → StrategyEvaluation → TradingOpportunity
    ↓
TradePlan → Risk → human execution authority
```

Market Candidate Selection is a future Level-2 capability. It is not implemented
or introduced by this Story.

## Context

Story 0067 demonstrated that manually finding a useful market is inefficient in
a catalogue of approximately 1,436 observed Kraken markets. High volume did not
guarantee an analytically aligned context, and the system correctly returned a
conflicting `NO_SETUP` result for PEPE/USD.

The completed Two-Level Market Filtering Investigation established that current
canonical catalogue data safely supports provider, symbol, base-asset,
quote-asset, and tradability/status discovery. Current raw volume is not
semantically normalized for cross-market comparison.

## Problem

The Markets page has text search but does not apply the existing optional
provider/tradable filter fields. Decision Workspace and Active Scan contain
separate market search/selection behavior. The application needs one small
reusable discovery boundary without changing account eligibility, Active Scan
scope authority, or downstream trading semantics.

## Scope

### Included

- A reusable typed frontend Market Discovery boundary.
- Reactive derived visible-market state from catalogue, filter, and sort state.
- Text search over symbol, base asset, quote asset, and provider.
- Provider filtering using canonical provider values.
- Base-asset filtering.
- Quote-asset filtering.
- Trading status filtering.
- Tradability filtering.
- Stable deterministic sorting using existing catalogue identity fields.
- Clear/reset behavior for discovery controls.
- Markets page integration as the primary complete consumer.
- Stable catalogue sorting reuse in Active Scan specific-market selection.
- Shared search matching reuse in Decision Workspace without bypassing
  account-scoped eligibility.
- Focused deterministic tests for filtering, sorting, reactivity, and unchanged
  catalogue input.

### Out of Scope

- Market Candidate Selection.
- `AnalysisPriority` or any cross-market priority score.
- Raw-volume filtering or sorting.
- Normalized activity, liquidity, spread, or volatility metrics.
- Market Data API changes, pagination, or backend discovery queries.
- Asset-class taxonomy or inference.
- MarketDataReadiness.
- Watchlists or persisted user preferences.
- Passive Scanner changes.
- Market Structure, SwingPoint, HH/HL/LH/LL, BOS/CHOCH, or TrendLineCandidate.
- Strategy, Trend Context, Opportunity, TradePlan, Risk, or execution changes.

## Canonical Data and Semantics

The implementation reuses `GET /api/v1/markets` and existing `MarketResponse`
fields only. No provider-specific or inferred asset-class meaning is added.

User filters are presentation/selection controls. They are not:

```text
User filters != account eligibility
User filters != backend tradability authority
User filters != Candidate Selection
User filters != StrategyMatch / TradingOpportunity / Risk / execution
```

Decision Workspace remains account-scoped and backend-authoritative. Active Scan
continues to preserve `SPECIFIC` and `ALL_ELIGIBLE`; frontend discovery does not
replace backend candidate/effective scope resolution.

Raw provider volume is deliberately absent because its unit, window, quote/base
semantics, normalization, and catalogue-wide freshness contract are incomplete.

## Acceptance Criteria

- [ ] The Markets page exposes reactive text, provider, base-asset, quote-asset,
      status, and tradability filters.
- [ ] Text search uses symbol, base asset, quote asset, and provider with
      case-insensitive matching.
- [ ] Multiple active filters use AND semantics.
- [ ] The user can clear/reset all discovery filters and sorting.
- [ ] Stable sorting is available using existing catalogue fields and always
      uses a deterministic market-ID tie-breaker.
- [ ] Empty results are represented clearly without mutating the source
      catalogue.
- [ ] The implementation does not add volume, asset-class, liquidity, spread,
      volatility, readiness, candidate-selection, or trading semantics.
- [ ] Active Scan preserves `SPECIFIC` and `ALL_ELIGIBLE` semantics and backend
      scope authority.
- [ ] Decision Workspace preserves `DecisionContext`, `eligibleMarketIds`, and
      unavailable-market reasons.
- [ ] Focused frontend tests cover filters, sorting, reset, reactivity, null
      optional values, and source immutability.
- [ ] Frontend production build succeeds.
- [ ] `git diff --check` passes.

## Constraints

- Reuse the existing full catalogue endpoint; approximately 1,436 markets is an
  acceptable V1 client-side catalogue size.
- Use Observables/reactive form streams, derived state, and the async pipe where
  practical.
- Do not introduce manual derived-state subscriptions or `ChangeDetectorRef`.
- Do not expand user scope or override account/backend eligibility.
- Do not create a new ADR; existing ADR authority is sufficient.
- Preserve all unrelated worktree changes.

## Relevant Investigation and ADRs

- `docs/architecture/reports/two-level-market-filtering-investigation.md`
- `docs/architecture/adr/ADR-010.md` — Market State Domain Model
- `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline
- `docs/architecture/adr/ADR-033.md` — Active and Passive Market Intelligence
  Orchestration
- Story 0005 — account-aware Active Scan scope resolution
- Story 0035 — explicit Active Scan scope and per-market results

## Likely Files

- `trading-os-web/src/app/core/models/market-filter.model.ts`
- `trading-os-web/src/app/core/services/market-discovery.service.ts`
- Markets feature and toolbar
- Decision Workspace market search integration
- Active Scan market catalogue integration
- Focused frontend specifications

## Validation

- Targeted Market Discovery and Markets tests.
- Targeted Decision Workspace and Active Scan tests if affected.
- Angular production build.
- Broader frontend test suite where practical; preserve/document unrelated
  pre-existing failures.
- `git diff --check`.

## Definition of Done

- [ ] Story implementation completed within this scope.
- [ ] Focused tests pass.
- [ ] Production build passes.
- [ ] Implementation report records architecture, validation, limitations, and
      the recommended next Story.
- [ ] Human code review completed.
- [ ] Human commit created.
