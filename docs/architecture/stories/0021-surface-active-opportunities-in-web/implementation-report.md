# Implementation Report — Story 0021

Date: 2026-08-24 · Branch: `feature/story-0021-opportunities-surfacing` (base `main` @ `010ed05`)

## What was implemented

Frontend-only. Zero backend files modified (verified via `git status`).

### Models (`core/models/opportunity.model.ts`)

Exact mirror of `OpportunityResponse` / `OpportunityPageResponse` served by
Market Intelligence: string-literal unions for `status`, `direction`, `type`,
`origin`; nullable `validUntil` and `strategyMatchId`.

### Service (`core/services/opportunity.service.ts`)

* `findActive()` → `GET /api/v1/opportunities/active`
* `findById(id)` → `GET /api/v1/opportunities/{id}` (404 propagates)

No business logic: status, expiration, scores are backend truth displayed
verbatim.

### List view (`features/opportunities/`)

Reactive discriminated view-model stream
(`loading | error | loaded`) built from a refresh `Subject` →
`switchMap(findActive)` → `startWith(loading)` → `catchError(error)`, rendered
through one `async` pipe. No manual subscription, no `ChangeDetectorRef`.

Table columns use only contract-backed fields: instrument, direction badge,
type, timeframe, score, valid-until (`—` when open-ended), rationale excerpt
(full text in `title`). Refresh button re-triggers the stream.

States: spinner while loading; non-error empty card "No active trading
opportunities right now"; visible error card on failure (no technical leak).

### Detail view (`features/opportunities/opportunity-details/`)

`route.paramMap` → `switchMap(findById)` → same union plus `notFound`
(HTTP 404 mapped explicitly via `HttpErrorResponse.status`). Renders identity,
scenario, direction/status badges, facts grid (type, timeframe, score, origin,
version, evaluatedAt, validFrom, validUntil), full deterministic explanation,
and provenance section: strategy match id (or truthful "Not recorded"),
supporting observation ids (or "None recorded"), opportunity id, created-at.
Back link to the list. No AI wording anywhere.

### Routing & navigation

* `/opportunities` and `/opportunities/:opportunityId` behind the existing
  `authGuard`.
* One "Opportunities" sidebar entry added after "Markets".

## Tests added

| File | Covers |
|---|---|
| `core/services/opportunity.service.spec.ts` | URL construction, payload typing, 404 propagation |
| `features/opportunities/opportunities.spec.ts` | loading→loaded sequence, late-subscriber replay, row rendering, empty state, error state, navigation, refresh |
| `features/opportunities/opportunity-details/opportunity-details.spec.ts` | fetch by route param, field rendering, explanation, provenance presence AND truthful absence, 404 vs error states, loading emission |
| `app.routes.spec.ts` | new routes exist, guarded by `authGuard`; all trader routes authenticated |

## Validation executed

* `npm run test:ci` — **30 files, 184 tests, all passing** (baseline 159 + 25).
* `npm run build` — success. Initial bundle 567.67 kB (+14 kB vs pre-story
  553.46 kB); the 500 kB budget warning is pre-existing and unchanged in kind;
  increase is proportional to a full new feature (kept eager to match current
  routing conventions — no other route is lazy).
* Coverage of new sources (vitest lcov): service 100%, list ts 100%, detail ts
  90%, templates ≥93% LINE — above the LINE ≥80% gate.

## Deviations from plan

None material.

## Manual validation attempted

Local Docker stack was partially running (gateway/broker/market-data/MI up,
**trading-core down**), so login could not be performed and no authenticated
journey could be exercised end-to-end. Verified live instead:

* `GET :17080/api/v1/opportunities/active` through the running Gateway →
  **401**: route exists and is JWT-protected (routing + security wiring OK).

Browser-level validation could not run: Playwright's chrome distribution is
not installable in this environment (no sudo). Empty/error/loading states are
covered at unit level with DOM assertions instead. No production logic was
modified to fabricate data.
