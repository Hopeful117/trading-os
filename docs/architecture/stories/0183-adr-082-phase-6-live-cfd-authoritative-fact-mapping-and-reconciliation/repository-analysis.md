# Repository Analysis - Story 0183

## Baseline

- `REMOTE_MAIN`: `088ae83131f9e15ac82d6a270cfe0773aee4c295`
- `LOCAL_BASE`: `088ae83131f9e15ac82d6a270cfe0773aee4c295`
- `BRANCH`: `story/0183-adr-082-phase-6-live-cfd-reconciliation`
- Story 0182 is merged and hardens PAPER CFD persistence; no local changes were
  present before branching.
- DevLog applicability was attempted for `trading-os`, but the project lookup
  returned `404 ENTITY_NOT_FOUND`; repository artifacts are authoritative.

## Existing Boundary

`BrokerAccountSnapshotV2` already carries provider registration identity,
external account identity, balances, positions, observation timestamps,
completeness/freshness, and account facts. Broker Service maps cTrader
observations into this provider-neutral model. cTrader volume conversion is
currently performed in the cTrader adapter before the snapshot is emitted.

Trading Core already preserves durable broker position references and uses them
for synchronization. Its existing synchronization path must remain compatible,
but it does not expose explicit five-state CFD reconciliation semantics.

`ModeAwareRiskFactsProvider` already routes LIVE through the authoritative V2
snapshot and PAPER through local account/trade valuation. `RequiredMarginPort`
is a proposed-order margin port and must not receive current used margin.

## Gaps Addressed

- The cTrader snapshot parser currently derives account equity from unrealized
  PnL and does not map account free margin; Phase 6 must consume provider account
  fields directly and mark absent required facts unavailable.
- The cTrader snapshot mapper currently treats observation completeness as enough
  without checking required account facts.
- Existing Trading Core synchronization has no provider-neutral explicit
  reconciliation result for CFD identity/economic comparison.

## Constraints

No provider payload types may cross into Trading Core. No new authoritative LIVE
ledger or migration is justified. Existing synchronization linkage and
compatibility policy remain in place. No Risk Domain code is changed.
