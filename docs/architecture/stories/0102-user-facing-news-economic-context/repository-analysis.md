# Story 0102 - Repository Analysis

## Repository State

- Current branch: `main`.
- `HEAD`: `17e924cd900cf61345a0d5396bceadef5a9258da`.
- Local `origin/main`: `17e924cd900cf61345a0d5396bceadef5a9258da`.
- Story 0101 implementation changes are merged; no tracked or staged Story 0101
  work is present.
- `git fetch origin` was attempted and failed because the SSH agent refused the
  configured ED25519-SK key. The local remote ref was not changed.
- Existing untracked runtime/investigation reports were found and must remain
  untouched. The previously created Story 0074 investigation report is also
  untracked and is outside Story 0102 implementation scope.

## Relevant Existing Implementation

### News Service

`NewsController` exposes:

```text
GET /api/v1/news/events
GET /api/v1/news/items
```

The economic-event endpoint accepts `from`, `to`, `marketId`, `currency`,
`impact`, and bounded `limit` parameters. Its response includes availability
status, normalized event items, fetched time, message, and optional provider
attribution.

`EconomicEvent` already contains the required user-facing facts:

- title and category;
- scheduled and actual timestamps;
- currencies and market IDs;
- normalized impact and lifecycle status;
- previous, consensus/forecast, actual, and unit values;
- source name, source event ID, source update time, and fetched time.

`NewsAvailability` distinguishes `AVAILABLE`, `STALE`, `UNAVAILABLE`,
`UNSUPPORTED`, and `INCOMPLETE`. The frontend must preserve the distinction
between no matching events and unavailable context.

### Gateway

`GatewayRouteConfig` already routes `/api/v1/news/**` to `news-service`. No
Gateway change is currently required.

### Angular Frontend

The frontend is Angular 21 with standalone components, `provideRouter`,
`provideHttpClient`, and an authentication interceptor. Existing feature pages
use typed services, RxJS `BehaviorSubject`/`switchMap`/`shareReplay` flows, and
the async pipe. The sidebar is the current discoverability surface and can gain
one focused Economic Context link without restructuring navigation.

The frontend environment already provides the Gateway-relative `/api/` base
path. A new News API service should use that path rather than a direct service
URL.

## Proposed Change

Add one frontend feature composed of:

- provider-neutral economic-event response models;
- a News or Economic Calendar API service;
- a standalone page/container with reactive query state;
- a compact filter region;
- chronological event cards or responsive rows;
- explicit loading, empty, unavailable, and request-error presentation;
- a sidebar link and authenticated route;
- focused component/service tests.

The current contract supports date window, importance, and currency filters.
Country filtering is not available in the contract and should not be invented.
Financial news should not be rendered because its provider-backed capability is
currently unavailable.

## Backend Decision

No News Service backend change is justified by the current requirements. The
existing provider-neutral contract contains all required economic-event data,
availability semantics, and attribution needed by the UI. Any backend change
that introduces new domain semantics, persistence, or provider leakage would be
outside this Story and requires a separate decision.

## Risks And Validation Gaps

- Existing automated backend persistence tests use H2; Compose validation must
  verify PostgreSQL and Flyway startup.
- No live XOOMAR request was previously validated. Story 0102 must report live
  provider validation separately and must not imply it from fixture-backed UI
  data.
- A runtime frontend test requires an authenticated environment and reachable
  Gateway/News Service; if unavailable, the exact blocker must be recorded.
- DevLog context retrieval was attempted but unavailable because the configured
  LLM provider returned an exhausted-quota error. Repository sources remain
  authoritative.

## Architectural Conclusion

The correct implementation path is:

```text
existing News Service API
    -> existing Gateway route
    -> typed Angular service
    -> reactive Economic Context page
    -> human trader
```

This closes the direct-visibility product gap without reopening the accepted
News Service backend foundation or coupling normalized facts to future AI output.

## Approval

This analysis is complete and requires explicit human approval before an
Implementation Plan or frontend implementation begins.
