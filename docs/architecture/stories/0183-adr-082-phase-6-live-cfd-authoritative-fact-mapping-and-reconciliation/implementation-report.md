# Implementation Report - Story 0183

## Result

Implemented locally and left uncommitted for human review. The Phase 6 change
keeps the existing provider-neutral V2 snapshot and durable provider-position
linkage, and adds a read-only Trading Core reconciliation boundary.

## Changes

- cTrader observation mapping now reads provider `equity`, `usedMargin`, and
  `freeMargin` directly. It no longer reconstructs equity from balance and
  unrealized PnL or used margin by summing position margins.
- cTrader position volume normalization remains inside Broker Service and emits
  signed canonical quantity plus provider position reference.
- V2 snapshot completeness now requires valuation currency, balance, equity,
  used margin, free margin, and a present position collection, while retaining
  existing freshness and unavailable-reason semantics.
- Added provider-neutral `LiveCfdReconciliationService`, which validates the
  canonical CFD contract, matches by provider position reference, preserves
  account provenance/facts, detects duplicate identities, and reports
  `MATCHED`, `PROVIDER_ONLY`, `LOCAL_ONLY`, `MISMATCH`, or `UNAVAILABLE`.
- Exposed the reconciler through the authenticated read-only V2 account boundary
  at `GET /api/v2/accounts/{brokerAccountId}/live-cfd-reconciliation`; PAPER
  accounts return unavailable without calling Broker Service.
- Rejected duplicate provider-position references before local synchronization
  mutates balances or positions.
- Freshness now uses provider observation time rather than local completion time.
- Matched exposures now require and compare the complete persisted canonical
  contract metadata and provenance.
- LIVE V2 synchronization no longer overwrites provider equity with local
  balance/trade recalculation. PAPER mode remains on its existing local path.
- LIVE risk-facts completeness now also requires free margin; no change was made
  to `RequiredMarginPort` or proposed-order margin semantics.
- Added focused cTrader and Trading Core tests for provider facts, signed
  quantities, reconciliation outcomes, contract/classification failures, and
  incomplete position observations.
- V2 broker synchronization now rejects PAPER accounts before any Broker
  Service snapshot call or local mutation; PAPER valuation remains on its local
  authority path.
- Reconciliation now treats missing required snapshot collections as
  unavailable instead of iterating malformed provider data.
- Added regression coverage for PAPER no-call/no-mutation behavior and malformed
  snapshot fail-closed behavior.

## Explicit Boundaries

```text
LIVE_PROVIDER_AUTHORITY = PRESERVED
PAPER_LOCAL_AUTHORITY = PRESERVED
LIVE_SETTLEMENT = NOT_IMPLEMENTED
RISK_INTEGRATION = NOT_IMPLEMENTED
CASH_PLUS_CFD = STILL_BLOCKED
```

No provider-specific type crosses into Trading Core. No LIVE ledger, migration,
settlement mutation, risk formula, compatibility policy, or broker write path
was added.

## Validation

- `./mvnw -q -Dtest=LiveCfdReconciliationServiceTest test` in `trading-core`:
  PASS, 5 tests.
- cTrader focused tests:
  `./mvnw -q -Dtest=JsonWebSocketCTraderOpenApiClientTest,CTraderAuthoritativeSnapshotCapabilityTest test`:
  PASS, 15 tests.
- `./mvnw -q test` in `trading-core`: PASS.
- `./mvnw -q test` in `broker-service`: PASS.
- Follow-up targeted tests after independent review fixes:
  `./mvnw -q -Dtest=BrokerSynchronizationServiceImplTest,LiveCfdReconciliationServiceTest test`:
  PASS.
- Synchronization persistence regression tests also pass after requiring
  complete provider account facts.
- `git diff --check`: PASS.

No real cTrader account or credentials were used. DevLog context lookup for
`trading-os` returned `404 ENTITY_NOT_FOUND`; repository artifacts and current
source were used as authority. Obsidian was not consulted because no relevant
vault context was required.

## Documentation Outcome

No product or runtime documentation update was required. This Story's canonical
architecture and Story artifacts were sufficient, and the implementation does
not change external setup or provider mutation behavior.

## Vault Outcome

No vault action. The vault was not consulted and no durable transverse-memory
proposal is warranted for this repository-local implementation.

## Delivery

Human code review, commit, push, pull request creation, and merge remain
pending.
