# Repository Analysis — Story 0022

Date: 2026-08-25 · Branch base: `origin/main` @ `5687223` (Story 0021 merged as PR #17)

## Answer to the mandatory question (§40)

> **Smallest interaction that correctly triggers the existing Active Scan
> without moving business responsibility into Angular:**

A **"Run market scan" panel inside the existing Opportunities page** — the
trader picks one of their own accounts (the only field the contract requires),
optionally types an objective, and presses one button. The panel sends
`POST /api/v1/intelligence/scans` with a client-generated `Idempotency-Key`,
then polls `GET /api/v1/intelligence/scans/{scanId}` until the backend reports
a terminal status, displays the real progress counts returned by the
projection, and hands over to the already-existing opportunities list. No
analysis, eligibility, scoring or status logic is reproduced client-side.

## Backend contract audit (verified in code — Case A)

**Case A confirmed: the public API is already sufficient. Zero backend changes
required.**

### Trigger

`POST /api/v1/intelligence/scans`
(`MarketIntelligenceController.createScan`, prefix routed by Gateway since
Story 0019, no rewrite)

* Headers: `Idempotency-Key` (required), `X-Actor-Id` (**injected by the
  Gateway** `AuthenticatedActorHeaderFilter` from the JWT principal; the MI
  controller itself rejects missing/invalid actor with 401).
* Body `CreateActiveScanRequestDto`: `accountId: UUID` (`@NotNull`),
  `objective: String ≤500` optional, `requestedMarketIds: List<UUID>` optional.
* Response: **202 Accepted**, `ActiveScanResponse` + `Location:
  /api/v1/intelligence/scans/{scanId}`.

### Idempotency semantics (server-owned)

Same actor+key returns the existing scan if the request fingerprint matches;
same key with different fingerprint → `409 IDEMPOTENCY_CONFLICT`. Frontend
generates one UUID per logical scan attempt; retries of an accepted scan reuse
the GET endpoint instead of re-posting.

### Lifecycle (backend truth)

`ActiveScanStatus`: `READY_TO_DISPATCH, DISPATCH_REQUESTED, RUNNING,
PARTIALLY_COMPLETED, COMPLETED, FAILED, COMPLETED_NO_WORK` (+`isTerminal()`).
Creation is asynchronous by design (dispatch registered after commit) —
polling the GET projection is the intended follow-up.

### Projection (`ActiveScanResponse`)

`scanId, accountId, objective, status, requestedMarketIds, candidateMarketIds,
effectiveMarketIds, resolvedAt/createdAt/updatedAt, progress{totalCandidates,
eligible, excluded, running, completed, failed, opportunitiesFound},
markets[]{scanMarketId, ordinal, marketId, eligible, analysisStatus,
resultQuality, outcome, analysisExecutionId, exclusionReasons[], diagnostic{
code,message}, opportunity?, strategy{strategyMatchId,strategyId,strategyVersion}?}`

`COMPLETED_NO_WORK` covers "no eligible markets" (not an error);
`opportunitiesFound: 0` on COMPLETED covers "no setup matched" (not an error).

### Ownership & scope resolution

`ActiveScanScopeResolutionService.resolve` verifies the account belongs to the
actor via Trading Core (`requireOwnedAccount`) and evaluates market eligibility
deterministically against the Market Data catalogue (tradability etc. land in
`exclusionReasons`). Angular must not re-derive any of this.

### Identity chain

Angular JWT → Gateway validation → `X-Actor-Id: <userId>` injected → MI
controller. MI has no SecurityFilterChain (internal service); the Gateway is
the enforcement point (established pattern, Story 0019).

## Frontend conventions available for reuse

* `AccountService.getAccounts()` already returns the trader's accounts
  (`Account {accountId, name, …}`) — feeds the required account selector.
* Story 0021 patterns to mirror: typed models in `core/models`, service in
  `core/services`, discriminated view-model stream + single `async` pipe,
  dark-slate table/card styles, `data-testid` hooks, Vitest mocks.

## Risks / notes

* Polling must be bounded: stop on terminal status and on component teardown;
  poll errors end the session in an explicit error state (documented
  limitation rather than silent retry loops).
* Transient poll failures should not fabricate scan failure — the session
  shows "unable to track" honestly while the server-side scan continues.
