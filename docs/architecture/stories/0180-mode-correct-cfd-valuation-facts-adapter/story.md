# Story 0180 - Mode-Correct CFD Valuation Facts Adapter

## Metadata

**ID:** `0180`

**Title:** Mode-Correct CFD Valuation Facts Adapter

**Status:** Draft

**Related ADR:** `ADR-082`

**Related ADRs:** `ADR-078`, `ADR-081`, `ADR-051`, `ADR-043`, `ADR-042`, `ADR-028`

**Related Stories:** `0086`, `0101`, `0178`, `0179`

---

## Goal

Assemble the complete authoritative, provider-neutral valuation facts required
to value one persisted CFD exposure without mutating Trading Core financial
state, executing broker operations, or changing CFD settlement compatibility.

The result must be a read-only, mode-correct valuation capability that supplies
an immutable valuation context to `CfdFinancialCalculator` and produces a
deterministic gross unrealized PnL projection.

---

## Context

ADR-082 defines CFD mark-to-market semantics and requires Trading Core to
assemble complete provider-neutral facts before deterministic calculation.

Stories 0178 and 0179 provide the validated CFD contract and the durable
provider-neutral CFD exposure projection on `Trade`.

The repository already contains:

- `CfdFinancialCalculator` and directed `CurrencyConversionFact` support;
- `MarketValuationPort` with explicit price-use and freshness policies;
- Market Data valuation facts containing source timestamps and provenance;
- mode-specific account and broker-account authority concepts.

The existing account valuation service mutates account state and is specific to
`LINEAR_PERPETUAL`; it is not the CFD adapter for this Story. The existing
Market Data valuation snapshot path also persists valuation snapshots, so the
Phase 4 capability must not introduce a database mutation for each valuation.

---

## Problem

Trading Core can persist a complete CFD exposure and can calculate CFD PnL from
explicit inputs, but it does not yet provide one mode-correct read-only boundary
that combines:

- the persisted CFD contract and exposure;
- an authoritative timestamped mark price;
- the explicit mark-price policy and provenance;
- the account reporting currency;
- a directed FX conversion fact when quote and reporting currencies differ.

Without this boundary, a caller could use an unqualified price, silently assume
an FX identity or zero conversion, use stale evidence, or accidentally promote a
persisted Trading Core exposure to LIVE provider authority.

---

## Scope

- Add or reuse the smallest provider-neutral read-side valuation contract needed
  to obtain a complete timestamped mark fact and explicit provenance.
- Reconstruct `InstrumentFinancialContract` from the persisted Story 0179 CFD
  projection; do not fetch mutable current contract metadata to reinterpret the
  exposure.
- Assemble the valuation context for one persisted CFD exposure and invoke only
  the pure `CfdFinancialCalculator` for financial arithmetic.
- Preserve explicit mark policy. For executable-side valuation, a long exposure
  uses the conservative sell side and a short exposure uses the conservative buy
  side, using the existing repository price-use semantics.
- Preserve mark observation time, source identity, source version or policy
  version where available, quote currency, and reporting currency.
- Require a directed `CurrencyConversionFact` when quote and reporting
  currencies differ. Reuse the existing identity-currency behavior without
  creating a synthetic external rate.
- Reuse existing freshness and conversion-coherence policies where they are
  authoritative. Missing, stale, invalid, incomplete, or directionally
  incompatible facts must remain unavailable.
- Make PAPER and LIVE authority explicit:
  - PAPER may combine Trading Core persisted exposure with Market Data marks and
    must not call Broker Service for positions, balances, margin, or PnL.
  - LIVE must not treat the persisted Trading Core exposure as current provider
    authority before the Phase 6 reconciliation capability exists; it must fail
    closed or return the established unavailable result.
- Return an immutable provider-neutral valuation result containing the exposure
  identity, signed quantity, entry and mark prices, mark policy, timestamps,
  provenance, gross quote-currency PnL, reporting currency and converted gross
  PnL when available.
- Keep the capability read-only. Valuation must not persist ticks or valuation
  rows, mutate `Trade`, `TradePlan`, `Account`, cash, balances, position state,
  settlement state, or execution state.
- Add focused unit, integration, restart-reconstruction, authority, provenance,
  currency, and no-mutation regression tests.

---

## Out of Scope

- Enabling `CASH + CFD` or changing `SettlementCompatibilityPolicy`.
- CFD PAPER entry, exit, settlement, realized PnL, cash mutation, or equity
  mutation.
- LIVE position, balance, margin, cost, or reconciliation implementation.
- Broker Service calls from the PAPER valuation path.
- Broker orders, close requests, provider mutations, or execution enablement.
- Required-margin derivation, leverage inference, financing, commission,
  spread-cost settlement, liquidation, or challenge-policy changes.
- Changes to Risk Domain rules or adding CFD branches to Risk Domain code.
- Persisting every mark, quote, FX fact, or valuation result as a new database
  row.
- Reconstructing exposure from TradePlan, provider order payloads, cTrader
  payloads, or symbol heuristics.
- Automatic FX inversion or chained conversion unless the existing approved
  valuation contract explicitly supports and supplies that path.
- Public REST API, frontend, Gateway, or Angular changes.
- Database migrations. If a schema change is unexpectedly required, stop and
  document the missing Phase 3 fact instead of adding a migration implicitly.
- Reopening the established FTMO/cTrader identity distinction or EURUSD product
  classification.

---

## Acceptance Criteria

- [ ] A complete persisted CFD exposure can be reconstructed after clearing the
      persistence context and valued from supplied authoritative facts.
- [ ] A long EUR/USD CFD with quantity `+1000`, entry `1.10000`, mark `1.10100`,
      and multiplier `1` produces gross unrealized PnL of `+1 USD`.
- [ ] A short EUR/USD CFD with quantity `-1000`, entry `1.10000`, mark `1.09900`
      produces gross unrealized PnL of `+1 USD`.
- [ ] Long and short losing cases preserve the `-1 USD` loss sign.
- [ ] The valuation result identifies the mark policy and does not expose a
      naked price without its meaning.
- [ ] Missing mark, invalid mark, missing timestamp, missing provenance, stale
      mark, or incomplete market evidence returns the existing fail-closed
      unavailable outcome and never returns zero PnL as a substitute.
- [ ] A USD quote valued for a USD reporting account uses currency identity and
      requires no external FX rate.
- [ ] A USD quote valued for a EUR reporting account requires an explicit
      directed USD-to-EUR conversion fact and converts both positive and negative
      PnL with the correct sign.
- [ ] Missing FX, invalid FX, missing FX timestamp or provenance, and an
      unsupported FX direction return the unavailable outcome without silent
      inversion or a one-to-one assumption.
- [ ] Mark and FX timestamps and provenance are retained in the immutable result
      whenever both facts are used.
- [ ] A non-CFD persisted projection is rejected and is never evaluated with CFD
      arithmetic.
- [ ] PAPER valuation performs zero Broker Service financial-fact calls and
      uses only persisted Trading Core exposure plus Market Data valuation facts.
- [ ] LIVE valuation cannot promote a local persisted exposure to current
      provider-authoritative state before Phase 6 reconciliation and fails closed
      or returns the established unavailable result.
- [ ] Valuation does not mutate `Trade`, `TradePlan`, `Account`, cash, balances,
      position state, settlement state, execution state, or valuation storage.
- [ ] `CfdFinancialCalculator`, `CurrencyConversionFact`, Story 0178 contract
      tests, and Story 0179 persistence tests remain green.
- [ ] Existing SPOT and `LINEAR_PERPETUAL` valuation and risk behavior remains
      unchanged and does not route through the CFD adapter.
- [ ] `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default and no broker
      order, close request, provider mutation, or trade-plan mutation occurs.

---

## Constraints

- Market Data owns current market marks, FX valuation facts, freshness evidence,
  and provider-specific normalization.
- Trading Core owns account linkage, persisted exposure interpretation, mode
  authority selection, and read-only valuation orchestration.
- The Risk Domain remains provider-neutral, deterministic, and free of CFD
  branches.
- LIVE provider facts remain authoritative for current external exposure and
  balances; PAPER facts remain authoritative inside Trading Core.
- Exact decimal arithmetic must be preserved.
- Every dynamic fact must carry an observation timestamp and sufficient
  provider-neutral provenance.
- No current clock value may replace a missing provider observation timestamp.
- Existing approved mark, freshness, and conversion policies must be reused;
  arbitrary new thresholds or accounting defaults require a separate decision.
- Provider payloads, cTrader identifiers, and provider-specific field names must
  not cross the financial domain boundary.
- Missing or contradictory facts fail closed.
- No implementation may commit, push, or merge without explicit human
  authorization.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- `docs/architecture/adr/ADR-081-ctrader-instrument-product-classification.md`
- `docs/architecture/adr/ADR-051.md`
- `docs/architecture/adr/ADR-043.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-028.md`

---

## Relevant Stories

- `docs/architecture/stories/0086-paper-derivative-mark-to-market-equity-valuation/story.md`
- `docs/architecture/stories/0101-bounded-fresh-provider-neutral-valuation/story.md`
- `docs/architecture/stories/0178-canonical-cfd-financial-contract/story.md`
- `docs/architecture/stories/0179-cfd-financial-contract-boundary-and-persisted-projection/story.md`

---

## Relevant Modules

- `trading-core`
- `market-data`
- `risk-domain` (regression validation only)

---

## Validation

- Focused Trading Core tests for the valuation adapter, mark policy, calculator
  integration, FX direction, provenance, mode authority, and no mutation.
- Focused Market Data tests for the provider-neutral valuation fact boundary and
  freshness/provenance propagation.
- Restart reconstruction integration test combining Story 0179 persistence with
  Phase 4 valuation.
- Negative tests for missing marks, stale facts, invalid provenance, missing FX,
  wrong FX direction, non-CFD input, and LIVE authority unavailability.
- Regression tests for SPOT, `LINEAR_PERPETUAL`, Story 0178, and Story 0179.
- `git diff --check` and relevant module compilation/tests.
- No authenticated cTrader execution, broker mutation, settlement mutation, or
  controlled trade.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed within this Story boundary.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed and reported accurately.
- [ ] Independent code review completed and findings addressed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
- [ ] Pull request validated and merged.
