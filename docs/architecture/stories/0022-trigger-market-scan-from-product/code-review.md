# Code Review — Story 0022

Date: 2026-08-25 · Reviewer: implementation agent (human review still required)

## Scope compliance

* Frontend-only; no backend/Gateway/MI change (Case A confirmed during audit).
* No trade-plan/risk/execution UI, no passive scanner, no presets, no AI
  wording. Footer "AI Engine ● Online" left untouched as required.

## Backend-logic duplication check

None found: statuses come verbatim from responses (`isActiveScanTerminal`
only re-states backend `isTerminal()` values); eligibility, ownership,
fingerprint/idempotency conflict and scoring all stay server-side. The
frontend never re-derives market tradability or scan outcome.

## RxJS / race-condition review

* `exhaustMap` chosen deliberately: clicks during an active session are
  ignored (tested). A second logical run is possible only after the session
  stream completes (terminal or error).
* Polling: `switchMap` on the timer means a slow poll response cannot pile up;
  `takeWhile(…, true)` guarantees exactly one terminal emission then
  completion → async pipe unsubscribes → timer released. Verified by the
  "stops polling" test (no further `findScan` calls after terminal).
* One shared `view$` (`shareReplay`, refCount) feeds both state rendering and
  `busy$`; no manual subscriptions in the component class.
* `scanCompleted` emitted via `tap` exactly once per session (terminal-only,
  inclusive takeWhile).

## Error handling review

* Creation errors classified by HTTP status (409/401/403/other); poll errors
  land in UNAVAILABLE with polling stopped — an honest "tracking lost" rather
  than fake failure or infinite retry.
* Backend scan FAILURE is rendered as a terminal result, not conflated with
  client errors (dedicated test).

## Contracts & types

* Models mirror DTOs field-for-field incl. nullables; enums are string-literal
  unions copied from Java sources (documented origin).
* Idempotency-Key generated per attempt; accepted scans never re-posted —
  consistent with server fingerprint semantics (409 surfaced if ever hit).

## Findings

1. **(Accepted)** `requestedMarketIds` unused by UI this story — deliberate
   minimal scope; contract already typed for a future market-selection story.
2. **(Accepted)** A lost tracking session does not offer "re-attach to scanId"
   recovery; the scan continues server-side but the panel restarts fresh.
   Acceptable for V1; noted in limitations.
3. **(Fixed during story)** Accounts error state initially unreachable due to
   falsy `null` through `@if (…; as …)`; replaced with explicit
   `{loaded, accounts}` state (covered by tests).
4. **(Observation)** Per-market results exist in the projection but only
   aggregates are displayed — intentional scope limit, data available later
   without contract change.

## Verdict

Conforms to Story scope, reactive conventions and product-honesty rules.
Ready for human review and merge decision.
