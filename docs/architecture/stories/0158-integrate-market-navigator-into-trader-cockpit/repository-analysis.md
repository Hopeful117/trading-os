# Repository Analysis - Story 0158

## Baseline

- `main` and `origin/main` are both at `3f08650c31e9b13335231ccec2ff50b5bce3380b`.
- Story 0157 is present and merged through PR 177.
- The worktree was clean before this analysis.
- DevLog applicability was checked for the Market Navigator context but the
  Story Agent failed because its upstream LLM provider was rate-limited. No
  DevLog evidence is used as project authority.
- The Obsidian vault was queried for targeted Market Navigator and Decision
  Workspace context; no relevant notes were found. No vault action is required.

## Current Scanner Capability

The Active Scanner already exists and must be reused.

### Frontend orchestration

`trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.ts` owns the
existing presentation flow:

- account selection;
- explicit `SPECIFIC` or `ALL_ELIGIBLE` scope selection;
- market catalogue loading and tradability filtering;
- selected-market eligibility preflight through `resolveScope`;
- idempotent scan creation through `createScan`;
- polling through `findScan` until a canonical terminal status;
- truthful error mapping for conflict, authorization, ineligible scope, and
  unavailable backend states;
- per-market result filtering and Opportunity selection.

`ActiveScanService` consumes the existing gateway contracts:

```text
POST /api/v1/intelligence/scans/scope
POST /api/v1/intelligence/scans
GET  /api/v1/intelligence/scans/{scanId}
GET  /api/v1/intelligence/scans
```

No new scan API is required for this Story.

### Backend and ownership

`MarketIntelligenceController` exposes the scan API and derives actor ownership
from the authenticated `MiUserPrincipal`. `ActiveScanApplicationService` reads
owned projections and preserves idempotency. The backend projection already
returns account identity, effective scope, progress, per-market analysis state,
outcome, diagnostics, and Opportunity projections.

## Canonical Status Semantics

The current contracts provide enough truth for a Navigator without a new mapping
authority.

### Scan lifecycle

```text
READY_TO_DISPATCH
DISPATCH_REQUESTED
RUNNING
PARTIALLY_COMPLETED
COMPLETED
FAILED
COMPLETED_NO_WORK
```

`isActiveScanTerminal` already defines terminal behavior. The UI should preserve
these meanings and use readable labels only for presentation.

### Per-market outcomes

```text
EXCLUDED
RUNNING
COMPLETED_NO_OPPORTUNITY
OPPORTUNITY_FOUND
FAILED
CANCELLED
EXPIRED
```

The Navigator must keep `FAILED`, `CANCELLED`, `EXPIRED`, and unavailable or
excluded states distinct from a completed rejection/no-opportunity result.
`exclusionReasons` and `diagnostic` are existing backend evidence and must not be
reconstructed in Angular.

## Current Cockpit Coordinator

`DecisionWorkspace` already owns:

- account-first context resolution;
- `accountId`, `marketId`, and `opportunityId` route restoration;
- active-market selection and account eligibility checks;
- Opportunity loading and validation against the selected market;
- OHLC, ticker, Order Book, and Recent Trades lifecycle;
- Trade Plan navigation.

The current selection methods are the correct integration seam:

- `selectMarket(marketId, context)` updates the active market and URL.
- Route query changes load the selected Opportunity through the existing service.
- The existing stream observables remain the only live market-stream owner.

The implementation should add Navigator intent handling at this coordinator
boundary rather than let a presentation component call market services or mutate
stream state.

## Smallest Coherent Vertical Slice

The smallest useful slice is frontend-only:

1. Reuse the existing `ScanPanel` capability inside the account-scoped Cockpit,
   adapting its account input/result selection seam as needed.
2. Keep account selection in `DecisionWorkspace` authoritative and prevent a
   nested Navigator account from drifting from it.
3. Emit market-result selection from the Navigator to the coordinator.
4. Route eligible result selection through `selectMarket`.
5. Route Opportunity selection through the existing `marketId` and
   `opportunityId` query context so the current evidence panel updates.
6. Reset or replace scan state on account changes to prevent cross-account
   results.
7. Preserve the existing Account/Risk strip and current stream lifecycle.

This does not require a new backend projection, persistence layer, ranking model,
or domain decision.

## Evidence Available for Investigation

The current Opportunity contract already exposes deterministic trader-facing facts:

- direction, scenario, timeframe, type, status, and score;
- explanation;
- validity and evaluation timestamps;
- strategy match provenance;
- observation and analysis execution references;
- optional market, account, and scan provenance;
- optional setup reference price and triggers.

The existing DecisionWorkspace already renders the Opportunity explanation and
preserves the existing `Prepare from opportunity` boundary. Story 0158 should
make this evidence reachable through candidate selection, not create a second
Opportunity detail authority.

## Account and Security Findings

- The current scan creation and scan inspection endpoints are actor-owned on the
  backend.
- The frontend must pass the selected account context but must not treat frontend
  filtering as authorization.
- Account changes must clear or replace the previous scan session before showing
  the next account's results.
- A scan result may select only an account-eligible market through the existing
  DecisionWorkspace guard.
- No credentials, auth bypass, or new public endpoint is required.

## Persistence Assessment

- Opportunity persistence is `FOLLOW_UP_REQUIRED`, not a blocker for this Story:
  scan results and current Opportunity projections are already returned by the
  existing backend contract, while restart restoration beyond URL context is not
  needed for the smallest navigator slice.
- Observation persistence is `FOLLOW_UP_REQUIRED`, not a blocker: the Navigator
  can display existing Opportunity and scan evidence without adding an
  observation repository or changing investigation ownership.

## Performance and Stream Findings

- The existing scan panel polls one selected scan and already stops at terminal
  state; no new polling loop should be added.
- The Navigator must not open live market streams for listed candidates.
- Only `DecisionWorkspace` should own the selected market's stream subscriptions.
- The implementation should verify there is no duplicate scan polling caused by
  retaining both a hidden Opportunities panel and a Cockpit panel on the same
  route. The Cockpit route should instantiate one Navigator instance.

## Documentation Outcome

The implementation changes a user-visible Cockpit capability and therefore
requires reconciliation of this Story's Implementation Report. Existing scanner
and Opportunity concept documents do not need broad rewrites unless the
implementation changes their documented responsibility boundaries. No vault
write is proposed.

## Risks and Mitigations

- **Nested account drift:** pass the DecisionWorkspace account into the Navigator
  and reset its scan state when the account changes.
- **Stale scan result:** replace/cancel the prior scan view on account change and
  guard result selection through the current account context.
- **Duplicate streams:** keep all stream activation in `DecisionWorkspace`.
- **False rejection semantics:** render backend outcomes and diagnostics without
  converting failed or unavailable analysis into no-opportunity.
- **Opportunity route mismatch:** require the result market and selected market
  to agree before setting `opportunityId`.
- **God component growth:** keep scan rendering and interaction in the focused
  Navigator/ScanPanel presentation component; keep context ownership in the
  coordinator.

## Approval State

This Repository Analysis is ready for human approval. Implementation must not
begin until the current analysis has been explicitly approved.
