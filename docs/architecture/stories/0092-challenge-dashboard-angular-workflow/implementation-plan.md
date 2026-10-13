# Implementation Plan

## 1. Clarify the Backend Read Contract

- Add a typed `ChallengeNotStartedException` for the existing progress read
  when an account has no Challenge.
- Map that exception to `404 ProblemDetail` with a stable
  `code: CHALLENGE_NOT_STARTED`.
- Keep missing accounts, ownership failures, and missing historical definitions
  generic and non-disclosing.
- Add backend tests proving the distinction and preserving existing `400`,
  `409`, `500`, and generic `404` semantics.

This is a projection/error-contract clarification only. It does not alter Risk
calculation, Challenge evaluation, lifecycle transitions, or persistence.

## 2. Add Typed Angular Contracts and Service

- Create `core/models/challenge.model.ts` with exact TypeScript representations
  of `ChallengeDefinitionResponse`, `StartChallengeRequest`,
  `ChallengeInstanceResponse`, `ChallengeProgress`, and relevant enum unions.
- Include a typed ProblemDetail/error helper that preserves HTTP status and
  backend `code` without treating arbitrary `404` responses as absence.
- Create `core/services/challenge.service.ts` with:
  - `findCatalog()` -> `GET /api/v1/challenges`;
  - `startChallenge(accountId, request)` -> `POST /api/v1/accounts/{id}/challenge`;
  - `findProgress(accountId)` -> `GET /api/v1/accounts/{id}/challenge`.
- Add focused `HttpClientTestingModule` tests for URL, method, payload,
  response typing, and error propagation.

## 3. Build the Dedicated Challenge Page

- Add a standalone page under
  `features/challenges/pages/challenge-dashboard/`.
- Load accounts and catalog through Observables with explicit loading/error
  states and `shareReplay`.
- Track selected account with a `BehaviorSubject` and automatically switch the
  Challenge projection when the account changes.
- Interpret `404` as `no Challenge` only when `error.error.code` equals
  `CHALLENGE_NOT_STARTED`.
- Keep all other progress errors as visible unavailable/error states.
- Show the catalog only for the no-Challenge state.
- Start using the selected definition's exact ID/version and transition to the
  progress view from the successful response without reload.

## 4. Reactive Progress Dashboard

- Poll progress with the existing RxJS pattern:
  `timer(0, POLL_INTERVAL_MS)` -> `switchMap` -> `findProgress`.
- Stop polling after `PASSED` or `BREACHED`; retain the final projection.
- Render backend-provided status, values, target, profit, progress ratio,
  timestamps, and terminal values when available.
- Render daily/total safety margins as unavailable when the backend returns
  `null`; do not derive replacements in TypeScript.
- Use the selected catalog definition to display provider/product/plan and
  configuration context without provider-specific branching.
- Display the known Risk Day limitation only as neutral backend-context copy if
  needed; do not imply exact reset-boundary fidelity.

## 5. Route and Navigation

- Add the standalone page to `app.routes.ts` at `/challenges` with the existing
  `authGuard`.
- Add a `Challenges` RouterLink to the existing sidebar only.
- Do not change the general Dashboard structure.

## 6. Styling and Accessibility

- Follow existing dark slate cards, blue accents, controlled severity colors,
  rounded panels, responsive grids, and mobile breakpoints.
- Use semantic headings, labels, buttons, `role="status"`/`role="alert"`,
  textual status labels, and accessible progress text in addition to visual
  bars.
- Avoid color-only distinctions for ACTIVE, PASSED, BREACHED, warning, and
  unavailable states.

## 7. Tests

- Backend: typed no-Challenge error mapping and regression tests.
- Service: catalog, start, progress, payload, URL, and error tests.
- Component: loading, account failure, empty accounts, catalog failure,
  no-Challenge, catalog display, successful start, start rejection, generic
  progress failure, ACTIVE, PASSED, BREACHED, null metrics, account switching,
  and polling/terminal behavior.
- Run `npm run test:ci`, `npm run build`, and `npm run check` as applicable.
- Run targeted backend tests and `git diff --check` if backend code changes.

## Out of Scope

- Broker Account creation or account provisioning.
- Frontend Risk or Challenge calculations.
- Provider-specific or Kraken-specific logic.
- WebSockets, SSE, Kafka, realtime infrastructure, lifecycle mutation, reset,
  restart, funding, leverage, margin, liquidation, or general Dashboard
  redesign.
