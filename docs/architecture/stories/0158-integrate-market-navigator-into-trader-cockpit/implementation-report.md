# Implementation Report - Story 0158

## Outcome

The account-scoped Decision Workspace now embeds the existing Active Scanner as
a Market Navigator. A trader can run an account-scoped scan, inspect truthful
scan and per-market outcomes, and select a result through the existing
DecisionWorkspace context when the market is also eligible for the current
account decision context.

## Changes

- Reused `ScanPanel` and `ActiveScanService`; no second scanner or scan
  orchestration path was created.
- Added an embedded account context input so `DecisionWorkspace` remains the
  only account selector on the Cockpit route.
- Added account-change reset/cancellation for the embedded scan session to avoid
  showing results from a previous account.
- Added Market Navigator market selection output and an accessible per-market
  `Inspect market` action.
- Added Opportunity selection routing through the existing `marketId` and
  `opportunityId` query context.
- Added a primary `Decision Cockpit` navigation link to return to the workspace
  from other authenticated application routes.
- Embedded one Navigator in the DecisionWorkspace selector rail while leaving
  live ticker, OHLC, Order Book, and Recent Trades ownership in
  `DecisionWorkspace`.
- Kept scanner lifecycle and per-market outcome semantics sourced from the
  existing backend projection.
- Replaced per-market broker capability requests with account/provider batch
  capability endpoints and batch Feign calls.
- Added Kraken `AssetPairs` catalog reuse with a configurable five-minute cache
  so capability resolution does not issue one Kraken request per market.
- Prevented results that are scanner-eligible but not eligible in the current
  account Decision Context from presenting an action that would be rejected by
  the existing account guard. The compact DecisionWorkspace layout does not
  render a separate unavailable-market detail block.

## Boundary Review

- Backend changes remain inside broker capability query contracts and the
  existing Trading Core risk-infrastructure client boundary. No Gateway,
  persistence, execution, or schema files changed.
- No score, ranking, confidence, AI, or financial calculation was introduced.
- No new market stream, scanner poller, global store, dependency, or ADR was
  introduced.
- Active Scan and Decision Context remain separate deterministic authorities:
  scanner outcomes describe analysis scope/results; DecisionWorkspace account
  context controls whether a market can be investigated in that account.

## Scanner Semantics

The implementation preserves the existing statuses:

- scan lifecycle: `READY_TO_DISPATCH`, `DISPATCH_REQUESTED`, `RUNNING`,
  `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`, `COMPLETED_NO_WORK`;
- market outcomes: `EXCLUDED`, `RUNNING`, `COMPLETED_NO_OPPORTUNITY`,
  `OPPORTUNITY_FOUND`, `FAILED`, `CANCELLED`, and `EXPIRED`.

`COMPLETED` with zero Opportunities remains a successful no-opportunity state,
not an error. Failed and unavailable/excluded results are not converted into
rejections.

## Persistence Assessment

- Opportunity persistence: `FOLLOW_UP_REQUIRED`; current scan projections and
  Opportunity provenance are sufficient for this bounded navigation slice.
- Observation persistence: `FOLLOW_UP_REQUIRED`; no observation repository or
  investigation authority was changed.

## Documentation Reconciliation

Updated this Story's canonical implementation report because the implementation
adds a user-visible Cockpit capability. Existing scanner and Opportunity concept
documentation did not require a responsibility-boundary change.

## Vault Outcome

No vault action. Targeted Obsidian searches for Market Navigator, Active Scanner,
and Decision Workspace context returned no relevant notes. No curated note was
created or modified.

## Validation

- Focused DecisionWorkspace and ScanPanel tests passed: 84 tests.
- Full Angular suite passed: 519 tests across 54 files.
- Angular production build passed; existing Angular bundle and stylesheet budget
  warnings remain non-blocking.
- Prettier check passed for modified files.
- `git diff --check` passed.
- Broker Service and Trading Core container images rebuilt successfully after the
  batch capability change.
- Broker Service health returned `200 OK` after restart; database migrations
  validated successfully.
- Authenticated runtime validation passed after rebuilding the local services:
  - the paper account resolved successfully;
  - `1,476` of `1,672` catalog markets were eligible;
  - `BTC/USD` was searchable and selectable;
  - market context loaded with `OPEN` tradability;
  - the existing manual-trade proposal action was available;
  - the backend capability response completed with one Kraken `AssetPairs`
    provider call rather than one request per market.
- One pre-existing live-data subscription request returned `501 Not Implemented`;
  live quote, chart, order-book, and recent-trade surfaces therefore remained
  `UNAVAILABLE`, without blocking account capability eligibility or market
  selection.

## Runtime Limitation

The previous authenticated runtime account's `DecisionContext` returned zero
markets eligible for investigation because it applied broker-capability checks,
while the Active Scan `ALL_ELIGIBLE` projection returned scanner-eligible
markets. The backend now resolves capabilities in batch, returns the broker
provider identity expected by Market Intelligence, and reuses the Kraken market
catalog to avoid the per-market rate-limit failure. Authenticated validation
confirmed the repaired account context and market selection behavior. No
frontend authorization bypass was introduced.

## Independent Review

Pending independent code review after final diff review.
