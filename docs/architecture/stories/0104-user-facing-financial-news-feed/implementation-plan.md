# Story 0104 - Implementation Plan

## Plan Status

Implementation Plan prepared after explicit approval of the Story 0104
Repository Analysis and subsequently approved by the human engineer.
Implementation has completed; the historical approval boundary below records
what the plan authorized and what it explicitly excluded.

## Implementation Boundary

Implement the user-facing Financial News experience entirely in
`trading-os-web`.

Do not modify:

- News Service domain, persistence, provider adapters or synchronization;
- Alpha Vantage configuration or runtime enablement;
- Marketaux code, credentials, fixtures or requests;
- Gateway routes;
- Market Intelligence contracts;
- authentication behavior;
- Risk, execution, account or trading workflows.

## Design Decisions

### Route And Navigation

- Add an authenticated `/financial-news` route in `app.routes.ts`.
- Add a `Financial News` sidebar link immediately after `Economic context`.
- Keep Economic Calendar at `/economic-context` as a separate page.

### Shared Availability Type

The backend availability states are currently declared in
`economic-event.model.ts`. Add a small shared `news-availability.model.ts` type
and update the economic-event model to import it. The Financial News response
and the existing Economic Context response will then share the same explicit
provider-neutral state type without coupling the new feature to an unrelated
item model.

### API Model And Service

Add `financial-news.model.ts` containing typed public-contract models:

- `FinancialNewsItem` with nullable `summary`, `canonicalUrl`, `publisher` and
  `sourceUpdatedAt`;
- `FinancialNewsQuery` with only backend-supported `from`, `to`, `marketId`,
  `currency`, `impact` and `limit` fields;
- `FinancialNewsResponse` with `status`, `items`, `fetchedAt`, `message` and
  `attribution`.

Extend `NewsService` with `findFinancialNews(query)`:

- call `${environment.gatewayUrl}v1/news/items`;
- construct `HttpParams` only for non-empty supported query fields;
- return `Observable<FinancialNewsResponse>`;
- leave state and presentation decisions to the page component.

No category, publisher, search, pagination or provider-specific query parameter
will be added because the backend does not expose them.

### Reactive Page State

Add a standalone `FinancialNews` component following `EconomicContext`:

- use `BehaviorSubject` for the selected recent window, currency and refresh;
- derive the request through `combineLatest` and `switchMap`;
- use `startWith` for loading and `catchError` for a safe retryable error state;
- expose one `shareReplay` view-model Observable to the template;
- avoid manual subscriptions and `ChangeDetectorRef`.

The first UI filter set will be intentionally small:

- recent window: `24h`, `48h`, `7d`;
- optional currency text filter, normalized to uppercase before request mapping.

The recent-window values map to UTC `from` and `to` instants. `7d` remains well
inside the backend 31-day query limit. The request uses a bounded `limit` of
100. The page refresh reloads the normalized API catalog only.

View-model states:

- `loading`;
- `error` with a user-safe retry message;
- `unavailable` for `UNAVAILABLE` or `UNSUPPORTED` without usable items;
- `incomplete` for `INCOMPLETE` without usable items;
- `ready` containing response and items for `AVAILABLE`, `STALE` or
  `INCOMPLETE` responses with items;
- `empty` for `AVAILABLE` with zero items.

`STALE` data with items remains visible with a warning. `INCOMPLETE` data with
items remains visible with a warning. No status will be inferred from article
content.

### Template And Styling

Add:

- `financial-news.html` with semantic heading, filter panel, state regions and
  compact article cards;
- `financial-news.scss` reusing the Economic Context dark-slate palette,
  rounded panels, restrained blue accent and focus styles.

Each article card will show when available:

- title as the primary element;
- publisher and an unambiguous browser-local publication time, following the
  existing Angular date formatting convention;
- summary as source context, never as AI analysis;
- bounded category and currency metadata;
- a safe `Open source` external link only when `canonicalUrl` exists.

The UI will not display sentiment, provider identity as a headline feature,
normalization internals, raw IDs, or an interpreted impact badge. Missing
optional fields will remove the corresponding region rather than render fake
placeholder facts.

Responsive behavior will use the existing feature-local breakpoint style:

- desktop: dense single-column feed with multiple visible headlines;
- tablet: flexible card metadata and filter wrapping;
- mobile: stacked header, filters, metadata and source action.

### Tests

Add `financial-news.spec.ts` using the existing Vitest/JSDOM and
`HttpTestingController` pattern. Cover:

- initial request path and UTC recent-window parameters;
- currency request mapping;
- successful rendering of title, publisher, date, summary, categories,
  currencies and canonical source link;
- safe omission of missing optional fields and source link;
- loading state while request is pending;
- available empty state distinct from unavailable state;
- stale and incomplete warnings with visible items;
- unavailable/unsupported provider state;
- HTTP failure and retry action;
- refresh issuing a normalized `/items` request without provider-specific calls.

Extend `news.service.spec.ts` with typed `/items` request mapping coverage.
Update existing Economic Context model imports only if required by the shared
availability-type extraction, and rerun its existing tests without changing its
behavior.

### Documentation Reconciliation

After implementation, add Story-local:

- `implementation-report.md` with changed files, documentation outcome, tests,
  quality-validation result, and vault outcome;
- `runtime-validation-report.md` with the exact route, authentication,
  Gateway/state, browser and provider-call evidence.

Do not create backend or provider documentation because backend behavior is not
changed by this plan.

## File Change Set

Expected implementation files:

- `trading-os-web/src/app/app.routes.ts`
- `trading-os-web/src/app/layout/sidebar/sidebar.html`
- `trading-os-web/src/app/core/models/news-availability.model.ts`
- `trading-os-web/src/app/core/models/economic-event.model.ts` (shared type
  import only)
- `trading-os-web/src/app/core/models/financial-news.model.ts`
- `trading-os-web/src/app/core/services/news.service.ts`
- `trading-os-web/src/app/core/services/news.service.spec.ts`
- `trading-os-web/src/app/features/financial-news/financial-news.ts`
- `trading-os-web/src/app/features/financial-news/financial-news.html`
- `trading-os-web/src/app/features/financial-news/financial-news.scss`
- `trading-os-web/src/app/features/financial-news/financial-news.spec.ts`

Expected documentation files after implementation:

- `docs/architecture/stories/0104-user-facing-financial-news-feed/implementation-report.md`
- `docs/architecture/stories/0104-user-facing-financial-news-feed/runtime-validation-report.md`

No other repository files should be modified without a newly identified,
evidence-based Story requirement.

## Validation Sequence

1. Run focused Financial News and News Service frontend tests while iterating.
2. Run `cd trading-os-web && npm run test:ci`.
3. Run `cd trading-os-web && npm run build`.
4. Run `docker compose config -q`.
5. Run `git diff --check`.
6. If the local stack permits, validate authenticated navigation to
   `/financial-news`, the Gateway `/api/v1/news/items` request, the actual
   empty/unavailable state, safe source links, responsive layouts and browser
   console output.
7. Verify Economic Calendar navigation and existing component tests remain
   functional.

News Service or Gateway Maven tests are not part of the default sequence because
this plan does not change their contracts. Run them if implementation reveals a
contract change or a regression requiring backend verification.

## Stop Conditions

Stop and report rather than expanding the plan if any of the following becomes
necessary:

- a News Service DTO, domain, persistence or API redesign;
- a provider or provider-selection change;
- a Gateway or authentication redesign;
- client-side filtering that cannot be expressed by the backend contract;
- populated runtime data requiring a new seed mechanism;
- a new frontend framework or state-management library;
- a Marketaux or Alpha Vantage runtime call;
- any AI, sentiment, risk or execution behavior.

## Expected Outcome

The authenticated trader can inspect normalized Financial News through a dense,
responsive and provider-neutral frontend page. Provider acquisition remains a
backend concern, the page remains useful when the provider is unavailable, and
the existing Economic Calendar behavior is preserved.

## Approval Boundary

This plan was explicitly approved for the implementation stage described above.
That approval did not authorize provider integration, backend redesign, commit,
push, merge or pull-request creation.
