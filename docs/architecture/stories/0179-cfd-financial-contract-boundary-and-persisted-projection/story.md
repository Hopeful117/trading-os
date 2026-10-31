# Story 0179 - CFD Financial Contract Boundary and Persisted Projection

## Metadata

**ID:** `0179`
**Title:** CFD Financial Contract Boundary and Persisted Projection
**Status:** Proposed - Awaiting Repository Analysis
**Related ADR:** `ADR-082`
**Related ADRs:** `ADR-081`, `ADR-045`, `ADR-042`, `ADR-043`, `ADR-051`
**Related Stories:** `0178`, `0173`, `0167`

---

## Goal

Complete the provider-neutral boundary that carries the validated CFD financial
contract from Market Data to Trading Core and preserve the contract snapshot
needed for truthful position reconstruction after restart.

The Story must make missing or incomplete contract facts fail closed while
preserving the existing LIVE/PAPER authority boundaries and the current
fail-closed CFD compatibility matrix.

---

## Context

Story 0178 defines and validates the provider-neutral
`InstrumentFinancialContract`, but deliberately does not implement its runtime
service boundary or persistence projection.

ADR-081 assigns canonical instrument identity and market constraints to Market
Data. ADR-082 requires Trading Core to receive complete provider-neutral
contract metadata before CFD financial projection, valuation, or later
settlement work can be implemented.

The current persistence model cannot retain all facts required to interpret a
CFD position after restart. Existing trade creation and broker synchronization
paths also do not yet resolve and persist a complete contract snapshot.

---

## Problem

The canonical contract currently exists as a Trading Core value object, but the
service boundary does not yet expose the complete normalized facts required by
that value object. Without an explicit boundary:

- Trading Core may persist a CFD position without its contract multiplier or
  provenance;
- a restart may not have enough information to reconstruct the position's
  financial meaning;
- missing Market Data could be mistaken for a non-CFD instrument;
- provider-specific identifiers could leak into the provider-neutral domain;
- direct or partial database writes could create semantically incomplete
  projections.

---

## Scope

- Extend the Market Data market contract with provider-neutral CFD financial
  metadata and provenance.
- Persist the normalized contract metadata in Market Data using an additive
  migration.
- Expose the metadata through the existing provider-neutral Market Data API.
- Keep provider-specific cTrader conversion and configuration inside the Market
  Data adapter.
- Require an explicit configured contract multiplier and source version for
  cTrader CFD metadata; never infer either from a symbol, provider, account
  environment, or execution mode.
- Add a Trading Core resolver for complete Market Data contract facts.
- Fail closed when the Market Data identity is missing, ambiguous, incomplete,
  or contradictory.
- Persist a complete provider-neutral contract snapshot on Trading Core trades
  that represent a CFD position.
- Preserve contract identity, currencies, quantity unit, multiplier, precision,
  quantity constraints, source identity, source version, and observation time.
- Populate the snapshot during broker-neutral LIVE position synchronization
  when Market Data identifies the position as CFD.
- Add all-or-none persistence constraints for contract and valuation provenance
  fields where the database can enforce them without rejecting legacy rows.
- Add focused API, mapper, resolver, migration, persistence, and fail-closed
  regression tests.

---

## Out of Scope

- Enabling `CASH + CFD` or changing `SettlementCompatibilityPolicy`.
- Implementing PAPER CFD entry, exit, settlement, or mark-to-market
  orchestration.
- Calling Broker Service from the PAPER path.
- Changing LIVE provider position, balance, margin, or reconciliation authority.
- Adding cTrader payloads, cTrader field names, or cTrader identifiers to
  Trading Core domain contracts or persisted provider-neutral provenance.
- Inferring contract multipliers, leverage, margin, financing, commission, or
  liquidation behavior.
- Changing Risk Domain formulas or risk authorization behavior.
- Introducing a universal `Position` aggregate.
- Historical backfill of existing CFD trades or market rows.
- Broker order placement, cancellation, mutation, or execution enablement.
- Changes to FTMO challenge policy or economic account classification.

---

## Acceptance Criteria

- [ ] Market Data exposes a complete provider-neutral contract projection for a
      configured CFD market, including product type, base and quote currencies,
      quantity unit, multiplier, precision, quantity constraints, and
      provenance.
- [ ] The Market Data migration preserves existing markets and rejects partial
      financial contract metadata.
- [ ] cTrader-specific volume normalization remains inside the Market Data
      adapter.
- [ ] The boundary does not expose cTrader protocol types, provider payloads,
      or cTrader-specific source identifiers to Trading Core.
- [ ] Trading Core resolves exactly one Market Data market for a position
      instrument and rejects missing or ambiguous identity.
- [ ] Missing multiplier, source version, timestamp, quantity unit, or quantity
      constraints fails closed with an unavailable-contract outcome.
- [ ] A synchronized CFD position receives a complete persisted contract
      snapshot on its `Trade` projection.
- [ ] Existing SPOT and `LINEAR_PERPETUAL` synchronization behavior remains
      unchanged.
- [ ] A CFD position cannot be persisted through the new path without a
      complete contract snapshot.
- [ ] Persisted contract facts survive repository reload and contain no
      provider-specific payload fields.
- [ ] `INVENTORY + CFD`, `CASH + CFD`, and `UNKNOWN + CFD` remain rejected.
- [ ] No PAPER broker call, external mutation, settlement change, risk decision,
      or execution authorization is introduced.
- [ ] `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default.
- [ ] Focused Market Data and Trading Core tests pass, including PostgreSQL
      migration/constraint tests where applicable.

---

## Constraints

- Market Data owns canonical instrument metadata and market constraints.
- Trading Core owns user-specific financial projections and orchestration.
- LIVE provider facts remain authoritative for current external positions and
  balances.
- PAPER positions remain Trading Core-authoritative and must not depend on
  Broker Service reconciliation.
- Contract metadata must be provider-neutral at the service boundary.
- Missing or contradictory financial facts fail closed.
- Exact decimal arithmetic must be preserved.
- Existing incompatible contracts must not be enabled casually.
- The Story must not silently rewrite historical market or trade rows.
- No broker or provider mutation may be performed during validation.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-081-ctrader-instrument-product-classification.md`
- `docs/architecture/adr/ADR-045.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-043.md`
- `docs/architecture/adr/ADR-051.md`

---

## Relevant Modules

- `market-data`
- `trading-core`

---

## Validation

- Run focused Market Data contract DTO, mapper, cTrader configuration, and
  product classification tests.
- Run focused Trading Core resolver, contract snapshot, synchronization, and
  compatibility tests.
- Run Market Data fresh-install and upgrade migration tests, including partial
  contract rejection.
- Run Trading Core fresh-install and upgrade migration tests, including partial
  projection rejection.
- Run `git diff --check`.
- Review the complete implementation diff against this Story and the related
  ADRs.
- Do not perform authenticated cTrader execution or broker mutation.

---

## Definition of Done

- [ ] Repository Analysis completed.
- [ ] Repository Analysis approved by the human engineer.
- [ ] Implementation Plan completed when required.
- [ ] Implementation Plan approved by the human engineer.
- [ ] Implementation completed within this Story's scope.
- [ ] Documentation reconciliation completed.
- [ ] Independent code review completed and findings addressed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
- [ ] Pull request validated and merged.
