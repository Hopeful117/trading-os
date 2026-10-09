# Story 0080 - Base-Currency Multi-Asset Equity Valuation

## Metadata

**ID:** `0080`
**Title:** Base-Currency Multi-Asset Equity Valuation
**Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
**Related ADRs:** `ADR-019`, `ADR-028`, `ADR-042`, `ADR-044`

---

## Goal

Make the Trading Core Dashboard report a truthful account equity value in the
account base currency when an account contains balances or holdings in several
assets.

The valuation must remain deterministic, traceable, freshness-aware, and
owned by Trading Core. The frontend must continue to display the aggregate
contract without performing financial calculations.

## Context

The current Dashboard equity path selects broker-reported equity when it is
fresh and coherent, otherwise it calculates:

```text
base-currency balance + unrealized position PnL
```

The fallback balance is currently selected from the account base-currency
balance. Other asset balances are not converted into the reporting currency.
Consequently, a multi-asset account can display an incomplete equity value.

Market Data already provides an internal valuation-snapshot capability with
direct and inverse conversion support, explicit observation freshness, fact
statuses, conversion legs, and policy provenance. Trading Core does not yet
consume that capability for Dashboard account equity.

Story 0079 deliberately excluded full dynamic multi-asset valuation and
continuous PAPER monitoring. This Story addresses the missing Dashboard
valuation capability without changing Challenge progression semantics.

## Problem

The Dashboard can show a value that omits non-base-currency account assets or
values them inconsistently with the available market data. This undermines:

- account equity and drawdown interpretation;
- risk percentages derived from equity;
- comparison between broker-reported and Trading Core calculated equity;
- user trust in the Dashboard as an operational overview.

The implementation must also avoid double-counting assets and open-position
PnL when broker account facts expose both balances and positions.

## Scope

- Define and implement a provider-neutral base-currency equity aggregation path
  in Trading Core.
- Include all supported account assets in the calculated equity result.
- Reuse or minimally extend the existing Market Data valuation capability for
  direct and inverse asset-to-base-currency conversion.
- Preserve explicit valuation timestamp, freshness, status, source, and
  conversion provenance in the internal aggregation path.
- Define deterministic behavior for identity conversions, unavailable markets,
  stale observations, unsupported assets, and incomplete valuation batches.
- Preserve the existing broker-equity preference when the broker value is
  explicitly total, fresh, and coherent with the normalized account facts.
- Ensure balances and open-position PnL are combined according to explicit
  account-fact semantics without double counting.
- Expose sufficient Dashboard metadata for the frontend to distinguish complete,
  incomplete, degraded, and unavailable equity data.
- Add focused Trading Core, Market Data, and contract tests for multi-asset
  aggregation, conversion direction, freshness, incomplete valuation, broker
  divergence, and double-count prevention.
- Update the Angular Dashboard only as required to present the existing
  aggregate and its valuation status/source; financial calculations remain out
  of the frontend.

## Out of Scope

- Continuous Challenge progression or risk monitoring between authoritative
  PAPER state changes.
- WebSocket-driven equity monitoring.
- Autonomous trading, order placement, or broker mutation.
- Changes to risk thresholds, drawdown formulas, or Challenge lifecycle rules.
- Provider-specific valuation business logic in Trading Core.
- Synthetic prices, manual price injection, or silently using stale prices as
  current.
- Historical equity time series or performance analytics.
- Portfolio optimization, tax lots, settlement accounting, or margin-model
  redesign.
- Mandatory support for assets or conversion paths for which no approved market
  observation exists.

## Acceptance Criteria

- [ ] Dashboard calculated equity includes every supported account asset that
      can be valued in the account base currency.
- [ ] Base-currency balances use identity conversion and are not lost from the
      aggregate.
- [ ] Direct and inverse conversion paths use the declared deterministic price
      policy and preserve conversion provenance.
- [ ] Stale, missing, unsupported, or invalid conversion data never appears as
      a current complete valuation.
- [ ] Incomplete valuation produces an explicit status and does not silently
      report a complete total.
- [ ] Broker-reported equity is used only when its total-value semantics,
      freshness, and coherence are established; otherwise the deterministic
      calculated path is used.
- [ ] Balances and open-position PnL are combined without double-counting under
      the supported Broker Account fact semantics.
- [ ] Equity source, valuation status, valuation timestamp, and relevant policy
      provenance are available to the Dashboard contract where applicable.
- [ ] Risk and drawdown calculations consume the selected Dashboard equity
      consistently with the existing deterministic ownership boundaries.
- [ ] Angular displays the backend result and status without implementing
      cross-asset financial calculations.
- [ ] Missing market data remains fail-closed for the affected valuation fact
      and is visible as degraded or unavailable Dashboard data.
- [ ] No provider-specific business branch or broker payload leaks into the
      public Dashboard contract.
- [ ] Focused automated tests cover complete, identity, direct, inverse, stale,
      missing, invalid, broker-preferred, broker-divergent, and double-count
      scenarios.
- [ ] Relevant Trading Core and Market Data validation passes, including
      `git diff --check`.

## Constraints

- Trading Core remains the owner of the Dashboard aggregation and public
  Dashboard contract.
- Market Data remains responsible for market observations and valuation facts;
  it must not become an account or user workflow service.
- Broker Service reports provider-neutral account facts and must not own
  Dashboard or risk interpretation.
- Risk calculations remain deterministic and must consume an explicit resolved
  equity value rather than independently recalculating it.
- The implementation must preserve human authority and must not enable
  autonomous execution.
- Any change to service responsibility boundaries, valuation policy, or public
  API semantics requiring a new architectural decision must stop for review
  rather than being inferred during implementation.

## Relevant ADRs

- `docs/architecture/adr/ADR-019.md` - Dashboard orchestration and ownership.
- `docs/architecture/adr/ADR-028.md` - Deterministic Risk Domain.
- `docs/architecture/adr/ADR-042.md` - PAPER position authority and valuation
  boundary.
- `docs/architecture/adr/ADR-044.md` - Service-to-service trust and internal
  valuation endpoint protection.

## Relevant Stories

- `docs/architecture/stories/0026-dashboard-operational-overview/story.md`
- `docs/architecture/stories/0041-paper-local-position-query-valuation/story.md`
- `docs/architecture/stories/0054-paper-valuation-runtime-readiness/story.md`
- `docs/architecture/stories/0079-paper-challenge-progression-risk-monitoring/story.md`

## Relevant Modules

- `market-data`
- `trading-core`
- `broker-service`
- `trading-os-web`
- `risk-domain`

## Validation

- Focused Market Data valuation snapshot tests for asset identity, direct and
  inverse conversion, freshness, and incomplete facts.
- Focused Trading Core Dashboard tests for multi-asset aggregation, source
  selection, risk/drawdown consistency, divergence, and double counting.
- Contract or integration coverage for the Trading Core to Market Data internal
  valuation call, including service authentication where applicable.
- Angular tests and production build when the Dashboard contract or rendering
  changes.
- `git diff --check` and complete diff inspection.
- Runtime evidence using an isolated account containing at least one
  base-currency balance and one non-base-currency asset, including a negative
  stale or unavailable conversion case.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Multi-asset equity aggregation is implemented within the approved scope.
- [ ] Deterministic freshness, completeness, source, and provenance behavior is
      covered by tests.
- [ ] Dashboard and risk consumers use the resolved equity consistently.
- [ ] Documentation reconciliation is complete.
- [ ] Code Review completed.
- [ ] Human review and commit completed.
