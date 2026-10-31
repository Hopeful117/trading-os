# Repository Analysis - Story 0179

## Status

`READY_FOR_HUMAN_APPROVAL`

This analysis is based on the approved Story 0179, the current repository at
`17dff3701a3efafed6a3a5a8d9e2cd5c328a9762`, accepted ADRs, and targeted source
and test inspection. No new implementation was performed while producing this
analysis.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `17dff3701a3efafed6a3a5a8d9e2cd5c328a9762` |
| Working tree | Dirty; uncommitted CFD boundary and persistence changes are present and must be reviewed as existing work, not silently discarded |
| Affected modules | `market-data`, `trading-core` |
| DevLog | Unavailable because authentication is required; analysis continues from repository and ADR evidence |
| Obsidian vault | Not consulted; repository ADRs and Story artifacts are sufficient for this analysis |
| Current compatibility | `INVENTORY + CFD`, `CASH + CFD`, and `UNKNOWN + CFD` remain incompatible |

## Story Understanding

Story 0179 is a provider-neutral contract-boundary and persisted-projection
story. It follows Story 0178, which already defines and validates the immutable
`InstrumentFinancialContract` but intentionally excludes runtime boundary,
database migration, and position persistence work.

The implementation must make Market Data the source of canonical instrument
financial metadata, make Trading Core consume only normalized facts, and persist
an immutable contract snapshot on CFD trade projections. It must not implement
CFD settlement, valuation orchestration, risk integration, or compatibility
changes.

## Architectural Authority

### ADR-081

Market Data owns explicit, instrument-specific product classification. Product
identity must not be inferred from provider, execution mode, account environment,
or textual symbol alone. Provider-specific classification and conversion remain
inside the Market Data adapter; the boundary exposes only
`InstrumentProductType` and normalized facts.

### ADR-082

The required authority chain is:

```text
cTrader/provider facts -> provider-neutral Broker Service facts
Market Data            -> canonical prices, contract metadata, conversions
Trading Core           -> account linkage, projection, orchestration
Risk Domain            -> deterministic interpretation and decision
```

For this Story, Market Data must expose complete contract metadata. Missing
multiplier, provenance, constraints, or conversion facts fail closed. Contract
metadata must be persisted additively where the current projection cannot retain
the facts truthfully. `CASH + CFD` remains disabled until a separate decision.

### ADR-045, ADR-042, ADR-043

Market Data owns instrument metadata and constraints. Trading Core owns PAPER
financial state and orchestration. LIVE positions and balances remain provider-
authoritative through Broker Service. PAPER must remain local and must not call
Broker Service reconciliation to establish position truth. Account and
BrokerAccount identities remain distinct.

## Current Market Data State

- `market-data` persists canonical markets in the `markets` table through Flyway
  migrations and JPA `Market`.
- `Market` already owns provider, canonical symbol, base/quote assets, product
  type, market state, and embedded market constraints.
- `MarketResponse` is the existing provider-neutral REST DTO consumed by Trading
  Core through `MarketDataClient`.
- The cTrader adapter normalizes provider volume constraints from centi-units to
  canonical quantities and rejects missing or invalid product classification.
- The current cTrader configuration contains explicit symbol mappings and
  product-type mappings but no persisted contract multiplier or source-version
  boundary.
- Market Data therefore has the correct ownership boundary, but lacks persisted
  contract multiplier, quantity unit, contract provenance, and an API projection
  for those facts.

## Current Trading Core State

- Trading Core already contains the immutable provider-neutral
  `InstrumentFinancialContract`, `FinancialContractProvenance`, `QuantityUnit`,
  and `InstrumentFinancialContractMapper`.
- The mapper currently requires an authoritative multiplier and provenance as
  inputs, which is consistent with ADR-082, but no runtime resolver obtains those
  inputs from Market Data.
- `MarketDataClient` consumes market catalogue responses and price snapshots.
- `Trade` persists symbol, side, quantity, entry/current/exit prices, PnL,
  lifecycle, and account ownership, but the baseline projection does not retain
  product type, currencies, contract multiplier, quantity constraints, or
  contract provenance.
- `Account` persists balances, equity, realized balance, and valuation timestamps,
  but does not retain valuation-source identity/version.
- `BrokerSynchronizationServiceImpl` imports or updates provider positions into
  Trading Core trades. The V2 path is provider-neutral and read-only, but the
  current position DTO does not carry the financial contract.
- `PaperSettlementService` is the local PAPER financial mutation boundary. It
  currently rejects all CFD combinations through `SettlementCompatibilityPolicy`.

## Existing Uncommitted Work

The working tree already contains changes related to this Story, including:

- Market Data contract metadata fields and migration `V24`;
- Trading Core trade/account projection fields and migration `V47`;
- a Trading Core contract resolver;
- broker synchronization population hooks;
- migration, mapper, resolver, and projection tests.

These changes are not treated as approved implementation merely because they are
present. They must be reconciled with this Story, corrected for provider-neutral
provenance, and covered by the approved implementation plan.

## Confirmed Gaps

1. Market Data does not yet have an authoritative runtime source for configured
   CFD contract multipliers and source versions.
2. The current working changes derive a source identifier from a cTrader symbol,
   which violates the provider-neutral boundary and must be replaced by a
   neutral source identity.
3. The resolver must distinguish confirmed non-CFD markets from unavailable
   Market Data; missing market identity must not silently persist a CFD-like
   legacy trade.
4. The Market Data migration needs direct fresh-install and upgrade coverage,
   including partial-fact rejection.
5. Trading Core migration and JPA reload coverage must prove that all contract
   fields survive persistence without provider payload leakage.
6. Complete cTrader adapter-to-DTO-to-resolver propagation is not yet tested.
7. The current `Trade` projection additions and synchronization hooks require
   scope reconciliation and must not alter PAPER settlement or LIVE position
   authority.

## Required Implementation Shape

The implementation plan should preserve this flow:

```text
configured provider facts / reviewed registry
    -> Market Data normalization and validation
    -> persisted provider-neutral market contract
    -> Market Data REST response
    -> Trading Core resolver
    -> complete immutable Trade contract snapshot
    -> repository reload reconstruction
```

The plan must explicitly decide:

- the authoritative source and versioning policy for contract multiplier;
- the neutral source identity exposed at the Market Data boundary;
- whether missing contract metadata rejects the market, rejects only CFD
  synchronization, or marks the market unavailable;
- how existing trades and markets without contract snapshots remain compatible;
- how to prevent a missing Market Data response from being interpreted as a
  confirmed non-CFD instrument;
- whether valuation-source fields belong in this Story or a later valuation
  story;
- how the API contract remains backward-compatible for SPOT and
  `LINEAR_PERPETUAL` markets.

## Risks

| Risk | Required treatment |
|---|---|
| Provider-specific provenance leakage | Define neutral source identity/version before implementation; no cTrader symbol IDs in Trading Core |
| Missing multiplier | Require explicit configuration or reviewed registry fact; fail closed, never infer |
| Missing Market Data | Abort or quarantine the CFD synchronization path; do not persist an incomplete CFD projection |
| Existing legacy rows | Keep new fields nullable for historical rows and prohibit historical backfill in this Story |
| PAPER boundary regression | Do not call Broker Service or enable CFD settlement from this Story |
| Migration safety | Test fresh install, V46/V23 upgrade boundaries as applicable, JPA validation, and all-or-none constraints |
| Scope expansion | Keep risk, valuation, settlement, margin, financing, and execution work out of this Story |

## Validation Requirements

- Market Data unit tests for contract mapping, cTrader configuration, neutral
  provenance, and incomplete metadata failure.
- Market Data migration tests for fresh install, upgrade, JPA validation, and
  partial metadata rejection.
- Trading Core resolver tests for complete, missing, ambiguous, and
  contradictory market metadata.
- Trading Core synchronization tests proving complete CFD snapshot population
  and fail-closed behavior.
- Trading Core persistence/reload tests for contract fields and legacy rows.
- Regression tests proving SPOT, `LINEAR_PERPETUAL`, and compatibility policy
  behavior remains unchanged.
- `git diff --check` and complete diff review.

## Analysis Conclusion

The Story is implementable, but the authoritative multiplier/source-version
policy must be fixed in the Implementation Plan before code changes are
approved. The existing uncommitted changes provide a starting point but contain
known boundary and fail-closed issues; they are not ready for acceptance or
merge.

**Recommendation:** approve this Repository Analysis only after confirming that
the implementation plan will use a neutral Market Data source identity and will
reject incomplete CFD synchronization rather than silently persisting a legacy
trade.
