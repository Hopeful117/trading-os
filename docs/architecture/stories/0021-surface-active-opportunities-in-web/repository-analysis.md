# Repository Analysis — Story 0021

Date: 2026-08-24 · Branch base: `main` @ `010ed05` (origin/main up to date)

## 1. Backend contract (verified in code, authoritative)

`market-intelligence/adapter/web/OpportunityController.java` — public prefix
`/api/v1/opportunities` (routed by Gateway since Story 0019, no rewrite):

| Endpoint | Returns | Notes |
|---|---|---|
| `GET /api/v1/opportunities/active` | `List<OpportunityResponse>` | Only `status == ACTIVE`; server-side guarantee |
| `GET /api/v1/opportunities/{id}` | `OpportunityResponse` or **404** | Latest version of one opportunity lineage |
| `GET /api/v1/opportunities` | `OpportunityPageResponse` | Search: filters + pagination + sort (unused in this story) |
| `GET /api/v1/opportunities/history/{id}` | list or 404 | Version history (out of scope) |

`OpportunityResponse` fields (JSON): `id` UUID, `version` long, `status`
(`DETECTED|ANALYZED|ACTIVE|CONSUMED|EXPIRED`), `instrument`, `direction`
(`LONG|SHORT|NEUTRAL`), `scenario`, `timeframe`, `type`
(`SCALPING|INTRADAY|SWING|POSITIONAL`), `origin`
(`PASSIVE_SCAN|ACTIVE_SCAN|USER_REQUEST|SYSTEM_REEVALUATION`), `score`
number, `explanation` string, `observationIds` UUID[], `aiAnalysisIds` UUID[],
`evaluatedAt`, `validFrom`, `validUntil?`, `createdAt` (ISO-8601 instants),
`strategyMatchId?` UUID (null only for pre-0012 rows).

Expiration is backend-owned (`OpportunityExpirationDriver`, Story 0018). The
frontend must display validity but never re-derive status.

## 2. Frontend conventions (verified)

* Angular standalone components; pages live in `features/<domain>/` with
  `<page>.ts/.html/.scss/.spec.ts` (e.g. `features/markets/markets.ts`).
* Typed models in `core/models/`, services in `core/services/` using
  `inject(HttpClient)` and `${environment.gatewayUrl}v1/...` where
  `gatewayUrl = '/api/'`.
* Reactive style: `BehaviorSubject`/`Subject` + `switchMap` + `shareReplay`,
  rendered through `AsyncPipe`; detail pages derive state from
  `route.paramMap` (see `markets-details.ts`). `DatePipe` used for timestamps.
* Routes are flat and guarded with `authGuard`: `/dashboard`, `/accounts`,
  `/markets`, `/markets/:marketId`. Sidebar lists trader entries.
* Tests use Vitest (`vi.fn()`), TestBed with mocked providers,
  `fixture.whenStable()`.
* Quality baseline: LINE ≥ 80%, enforced by CI; current suite = 159 tests.

## 3. Gaps this story fills

* No model, service, route, view or navigation entry exists for opportunities
  (`grep -ri "opportunit" trading-os-web/src/app` → 0 hits).
* Existing pages do not implement explicit loading/error states; the mission
  requires them here, implemented reactively (view-model stream), without
  introducing manual subscriptions.

## 4. Risks

* Empty data in local environments (no scan has run) → empty state must be a
  first-class outcome, not an error.
* Honest wording required: deterministic strategy provenance, no AI claims;
  bootstrap strategy remains UNVALIDATED (ADR-038).
