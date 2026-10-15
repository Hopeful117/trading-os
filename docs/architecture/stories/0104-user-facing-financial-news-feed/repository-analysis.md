# Story 0104 - Repository Analysis

## Analysis Status

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan or frontend implementation begins.

## Repository State

- Current branch: `main`.
- `HEAD`: `b986ab14f6007804f79fff27263e7de6c3f6883b`.
- Local `origin/main`: `b986ab14f6007804f79fff27263e7de6c3f6883b`.
- Story 0103 is already merged in the current repository, contrary to the
  older prompt context describing it as uncommitted. Its Alpha Vantage support
  is present in `news-service` and remains disabled by default.
- The tracked worktree is clean. Untracked reports for Stories 0073, 0074,
  0098, 0099 and 0100 are present and must remain untouched.
- No Story 0104 implementation files exist yet.
- DevLog context retrieval was attempted and failed because the configured LLM
  provider has exhausted its quota. The current repository remains authoritative.
- The Obsidian vault was not consulted. This Story is bounded by current Angular,
  Gateway and News Service contracts and does not require transverse vault
  guidance.

## Frontend Architecture

The Angular application is Angular 21 with standalone components, `provideRouter`,
`provideHttpClient`, RxJS 7 and Vitest/JSDOM tests. The application uses the
existing `authInterceptor` globally through `app.config.ts`; feature pages do
not handle JWTs themselves.

The current authenticated route pattern is direct component registration in
`trading-os-web/src/app/app.routes.ts` with `authGuard`. The existing Economic
Context route is:

```text
/economic-context -> EconomicContext
```

The sidebar is the discoverability surface. It currently contains one direct
Economic Context link and does not have a nested navigation component. The
smallest coherent change is a second `Financial News` link and an authenticated
`/financial-news` route; no navigation redesign is justified.

## Story 0102 Reusable Patterns

`EconomicContext` establishes the relevant implementation pattern:

- typed feature models under `src/app/core/models`;
- `NewsService` methods returning typed `Observable` responses;
- `BehaviorSubject` filter and refresh state;
- `combineLatest`, `switchMap`, `startWith`, `catchError` and `shareReplay`;
- view-model unions for `loading`, `error`, `unavailable` and `ready` states;
- standalone component imports of `AsyncPipe`, `DatePipe` and `FormsModule`;
- Angular template control flow with `@if`, `@switch` and `@for`;
- local SCSS matching the dark slate dashboard language;
- focused component tests using `provideHttpClientTesting` and
  `HttpTestingController`.

The Financial News page should follow these patterns while using compact article
cards rather than calendar event cards. It should not introduce NgRx, another
state library, manual subscriptions or `ChangeDetectorRef`.

## Existing Financial News API Contract

`news-service/src/main/java/com/hope/trading/news/api/NewsController.java`
already exposes:

```text
GET /api/v1/news/items
```

Supported query parameters are:

- `from`: optional ISO date-time;
- `to`: optional ISO date-time;
- `marketId`: optional UUID;
- `currency`: optional string;
- `impact`: optional `ImpactLevel`;
- `limit`: default 100, bounded by the controller to 500 and by `NewsQuery` to
  the same safe range.

The response is the provider-neutral `NewsReadResponse<FinancialNewsItem>`:

```text
status: AVAILABLE | STALE | UNAVAILABLE | UNSUPPORTED | INCOMPLETE
items: FinancialNewsItem[]
fetchedAt: Instant | null
message: string | null
attribution: string | null
```

The public `FinancialNewsItem` fields are:

- `id`;
- `sourceName` and `sourceItemId`;
- `title`;
- nullable `summary`;
- nullable `canonicalUrl`;
- `publishedAt`;
- nullable `publisher`;
- `categories`;
- `currencies`;
- `marketIds`;
- `impact`;
- nullable `sourceUpdatedAt`;
- `fetchedAt`;
- `normalizationVersion`.

The frontend should type the public JSON shape directly, without importing
Java/persistence concepts or provider payloads. `sourceName`, `sourceItemId`
and `normalizationVersion` are available for provenance but do not need to be
prominent in the first user-facing card. `publisher`, title, publication time,
summary, categories, currencies and canonical URL are the primary display data.

The API sorts persisted news by `publishedAt` descending. The frontend should
preserve that server order rather than inventing a relevance sort.

## Availability Semantics

`NewsCatalogService.findNews` distinguishes provider availability from an empty
catalog:

- `AVAILABLE` with zero items means a valid empty catalog;
- `AVAILABLE` with items means current normalized data exists;
- `STALE` can be returned when stored facts exceed the configured freshness
  policy;
- `UNAVAILABLE`, `UNSUPPORTED` and `INCOMPLETE` can carry a provider-neutral
  message and currently return no items when the provider is unavailable before
  catalog querying;
- HTTP or network errors are handled separately by the Angular error state.

The component should keep `AVAILABLE` empty distinct from provider unavailable,
show stale data with a warning when items are present, and display an explicit
partial/unavailable message for `INCOMPLETE`. It must not show raw backend error
objects.

No backend change is justified. The current response already contains the
availability and freshness information required by the Story. Financial News
attribution is not currently calculated by the controller for `/items`, but the
article-level `publisher` is present and is the more relevant provenance for
this page. Adding backend attribution is not necessary for the first version.

## Gateway And Authentication

`gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`

```text
/api/v1/news/** -> lb://news-service
```

No Gateway change is required. The frontend environment uses the Gateway-relative
base `/api/`, so the API service must call:

```text
${environment.gatewayUrl}v1/news/items
```

The global auth interceptor adds the user JWT and redirects existing 401/403
responses through the established error route. The Financial News component
must not duplicate that behavior.

## Recommended Frontend Design

### Models And Service

Add a provider-neutral Financial News model and query/response types under
`trading-os-web/src/app/core/models`, reusing the existing availability type or
extracting that shared type only if the implementation requires it.

Extend `NewsService` with a typed `findFinancialNews` method that constructs only
the supported `from`, `to`, `marketId`, `currency`, `impact` and `limit` query
parameters. Do not add category or publisher filters because the current API
does not support them.

### Route And Navigation

Add an authenticated `/financial-news` route and a sidebar link adjacent to
`Economic context`. Keep Economic Calendar as a separate route and page.

### Reactive Page

Add a standalone Financial News feature component with:

- a small recent-window selector mapped to a backend `from`/`to` range;
- optional currency filtering, only if it improves the first version;
- a refresh action that reloads `/items`, never the external provider;
- an observable view-model union for loading, error, unavailable, empty,
  incomplete/stale and ready states;
- compact article cards using `title`, `publisher`, `publishedAt`, `summary`,
  `categories`, `currencies` and `canonicalUrl`;
- no sentiment visualization and no interpretation of `impact` or provider
  fields;
- safe external links with `target="_blank"` and
  `rel="noopener noreferrer"`.

The backend already returns newest-first, so no client-side sorting is needed.
The first version should remain bounded by the existing `limit` and should not
invent pagination or infinite scrolling.

### Visual And Responsive Design

Reuse the Economic Context page's local dark-slate styling, blue accent,
rounded panels, restrained borders and visible focus states. Financial News
cards should be denser than calendar events so multiple headlines fit in a
desktop viewport. Responsive rules should stack metadata and actions at the
existing mobile breakpoints.

## Validation Plan

Applicable frontend commands from `trading-os-web/package.json` are:

```bash
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
```

Also run:

```bash
docker compose config -q
```

News Service and Gateway tests are not required unless implementation changes
their contracts. Existing `news.service.spec.ts` and
`economic-context.spec.ts` provide the testing conventions. New focused tests
should cover:

- request path and supported query mapping;
- successful rendering of title, publisher, publication time, summary,
  categories, currencies and source link;
- missing optional fields;
- loading state;
- available empty state;
- unavailable, stale/incomplete and HTTP error states;
- retry/refresh behavior if included.

Browser validation should verify the authenticated route, Gateway request,
empty or unavailable production state, safe link behavior, desktop/mobile
layout, long text wrapping, no console errors, and unchanged Economic Calendar
navigation. It must not claim populated provider data unless deterministic local
data is already available through an existing safe mechanism.

## Risks And Decisions

- No backend API redesign is needed based on the current contract.
- No provider-specific frontend branch or display logic is allowed.
- The current provider-unavailable runtime state is expected because Alpha
  Vantage remains disabled and Marketaux is not integrated.
- `impact` exists in the public item contract, but should not be emphasized or
  interpreted in the first UI because `UNKNOWN` is not a meaningful user
  classification for financial news.
- Date filtering should use UTC ISO instants and follow the existing Economic
  Context convention; no local-time reinterpretation should be introduced.
- The current repository state shows Story 0103 merged, so the implementation
  must preserve the merged provider code rather than treating it as a pending
  uncommitted dependency.

## Architectural Conclusion

The Story can be implemented entirely in the Angular frontend:

```text
existing Financial News API
    -> existing Gateway route and auth interceptor
    -> typed NewsService Observable
    -> reactive Financial News page
    -> authenticated human trader
```

No stop-at-first-real-blocker condition is present. Implementation can proceed
after explicit human approval of this Repository Analysis. An Implementation
Plan remains the next workflow artifact because the Story introduces a new
frontend feature, route, service surface and test coverage.
