# Implementation Plan - Story 0224

## Status

`READY_FOR_HUMAN_APPROVAL`

This plan is based on the approved Story 0224 and its approved Repository
Analysis. It does not authorize code or runtime changes until this plan is
explicitly approved.

## Implementation Strategy

Make the smallest correction at the existing Market Data configuration and
Trading Core resolver boundaries:

```text
Accepted Market Data contract reference
    -> complete canonical EUR/USD CFD market projection
    -> product-aware Trading Core resolution
    -> existing provider-neutral contract mapper
    -> existing synchronization persistence
```

No new service, database reset, historical row deletion, risk change, or
provider mutation is planned.

## 1. Market Data Contract Configuration

Use the existing `CTraderMarketDataProperties.ContractReference` path as the
authoritative configuration input for the active cTrader EUR/USD CFD market.

The configuration must explicitly provide:

- provider `CTRADER`;
- external account scope `49411967` where required by the existing reference
  model;
- environment matching the active cTrader connection;
- provider symbol `EURUSD`;
- canonical instrument `EUR/USD`;
- product type `CFD`;
- quantity unit `UNDERLYING_UNITS`;
- the already verified contract multiplier `1`;
- a reviewed provider-neutral source ID and positive source version using the
  repository's existing contract provenance convention;
- effective time and `ACCEPTED` review status.

The implementation must not infer the multiplier, source version, or source ID
from the provider symbol, leverage, account environment, or database row. If
the authoritative source ID/version cannot be established from existing
repository evidence, implementation must stop and report the missing required
decision instead of inventing values.

The existing symbol mapping and explicit CFD product mapping remain unchanged.
The cTrader adapter will continue to normalize provider volume constraints into
canonical underlying units.

## 2. Market Data Projection Verification

Verify that `CTraderMarketData.toMarket(...)` materializes the complete
provider-neutral fields from the accepted reference and that the existing
Market Data synchronization updates the CFD row without changing the separate
historical SPOT row.

Do not delete, relabel, merge, or backfill the SPOT row. Do not add a migration
unless the current source model proves that the required fields are absent from
the deployed schema; the runtime evidence indicates the fields already exist
but are null.

Add or update focused tests to prove:

- accepted EURUSD reference produces `EUR/USD`, `CFD`,
  `UNDERLYING_UNITS`, multiplier `1`, and complete provenance;
- incomplete or unaccepted reference remains unavailable;
- the existing SPOT row is not converted to CFD;
- canonical quantity conversion and existing contract constraints remain
  unchanged.

## 3. Trading Core Resolver Correction

Update `InstrumentFinancialContractResolver` so the CFD synchronization path
does not treat a confirmed SPOT row with the same canonical symbol as a CFD
candidate.

The resolver must:

- preserve the existing provider-neutral `MarketResponse` boundary;
- select only a confirmed CFD market for `resolveIfCfd(...)`;
- reject multiple CFD candidates as ambiguous;
- reject missing or incomplete CFD metadata through the existing mapper;
- return no contract for a confirmed non-CFD instrument where the existing
  caller semantics require that behavior;
- never fall back from CFD to SPOT.

If changing shared `resolve(...)` semantics would affect unrelated callers,
keep the product-aware filtering localized to `resolveIfCfd(...)` or introduce
the smallest private helper needed to preserve existing behavior.

## 4. Synchronization and Persistence

Do not change `BrokerSynchronizationServiceImpl` persistence policy unless
focused tests demonstrate that the current contract is inconsistent with Story
0224. The existing path must continue to:

- acquire the broker snapshot through Broker Service;
- preserve `INCOMPLETE` completeness when mandatory financial facts are absent;
- resolve and apply a complete CFD financial contract before persisting an open
  CFD position;
- fail closed when the contract remains unavailable;
- keep risk consumers unable to use incomplete mandatory financial evidence.

Add only the focused synchronization regression needed to prove that a complete
EUR/USD CFD contract no longer blocks the existing snapshot persistence path.
Do not run a risk evaluation.

## 5. Focused Tests

Run or add only tests relevant to the confirmed defect:

- Market Data cTrader contract-reference and market projection tests;
- Market Data contract metadata mapper tests where needed;
- Trading Core `InstrumentFinancialContractResolverTest` extensions for
  CFD/SPOT duplicate identity, complete metadata, ambiguity, and fail-closed
  behavior;
- Trading Core synchronization test covering EUR/USD CFD contract application
  and incomplete snapshot semantics;
- existing contract multiplier and quantity-unit tests;
- historical SPOT Trade Plan immutability/read-only verification if an existing
  focused fixture supports it;
- `git diff --check`.

Do not run the full repository test suite.

## 6. Runtime Validation Sequence

1. Confirm the reviewed contract source ID/version and active runtime
   configuration without printing secrets.
2. Build only `market-data` and `trading-core` as affected services.
3. Redeploy only affected services with existing database volumes preserved.
4. Verify the Market Data API exposes one complete `CTRADER / EUR/USD / CFD`
   contract and that the SPOT row remains unchanged.
5. Use the authenticated Gateway application path to execute one controlled
   read-only synchronization for broker account
   `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`.
6. Record synchronization timestamp, snapshot identity, contract identity and
   version, completeness, freshness, persistence result, and unavailable
   financial fields.
7. Verify risk evidence remains insufficient when mandatory financial facts are
   absent, without creating a risk evaluation.

No Gateway, Eureka, JWT, or full-stack restart is part of this plan.

## 7. Explicitly Unchanged Areas

- `MAX_EXPOSURE=3%` and `MAX_POSITION_RISK=1%`.
- Risk profiles, challenges, and assignments.
- Historical Trade Plan `0cc15278-53a3-3fc4-8b52-54e419df369b`, version `2`.
- Provider quantity conversion `providerVolume = canonicalQuantity * 100`.
- Margin, leverage, stop-risk, exposure aggregation, and risk formulas.
- Broker positions, orders, execution intents, approvals, and provider state.
- Existing incomplete-snapshot completeness validation.
- Separate historical SPOT market identity.

## 8. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Source provenance is not authoritative | Stop before implementation; do not invent source ID/version |
| CFD and SPOT rows remain ambiguous | Filter only confirmed CFD candidates for the CFD resolver and reject multiple CFD rows |
| Contract metadata remains incomplete | Preserve mapper fail-closed behavior and do not persist an economically uninterpretable CFD position |
| Existing SPOT behavior changes | Add explicit SPOT regression coverage and avoid modifying the SPOT row |
| Snapshot lacks margin/equity facts | Preserve `INCOMPLETE`; never synthesize zero or force `COMPLETE` |
| Runtime validation mutates provider state | Use synchronization only; do not execute orders or risk workflows |

## 9. Expected Deliverables

- Focused Market Data configuration/projection correction, if required.
- Focused Trading Core resolver correction, if required.
- Relevant regression tests only.
- This plan and the approved Repository Analysis.
- Implementation report with documentation reconciliation and runtime evidence.

## 10. Human Approval

Implementation must not begin until this plan receives explicit human approval.
