# Implementation Plan — Story 0021

Safe to implement from the approved Story, the repository analysis and existing
conventions; this plan fixes names and file placement only.

## Files to create

```text
trading-os-web/src/app/core/models/opportunity.model.ts        # contract types
trading-os-web/src/app/core/services/opportunity.service.ts    # typed API client
trading-os-web/src/app/features/opportunities/opportunities.ts/.html/.scss/.spec.ts
trading-os-web/src/app/features/opportunities/opportunity-details/
    opportunity-details.ts/.html/.scss/.spec.ts
```

## Files to modify

```text
trading-os-web/src/app/app.routes.ts      # /opportunities, /opportunities/:opportunityId
trading-os-web/src/app/layout/sidebar/sidebar.html   # one "Opportunities" entry
```

## Design decisions

1. **Models** — `OpportunityResponse` interface mirroring the JSON exactly
   (nullable `validUntil`/`strategyMatchId`), string-literal unions for
   status/direction/type/origin, `OpportunityPageResponse` for completeness.
2. **Service** — only what the story needs:
   `findActive(): Observable<OpportunityResponse[]>` and
   `findById(id): Observable<OpportunityResponse>` (404 → error propagation,
   handled by the detail view). No business logic.
3. **List view model (reactive)** — refresh `Subject` →
   `switchMap(findActive)` mapped into a discriminated view-model stream
   `{status: 'loading'} | {status: 'error'} | {status: 'loaded', opportunities}`
   rendered via a single `async`. No manual subscribe, no `ChangeDetectorRef`.
4. **Detail view model** — `route.paramMap` → `switchMap(findById)` → same
   discriminated union plus `'notFound'`; back link to the list. 404 maps to a
   truthful not-found state.
5. **Routes** — flat convention: `/opportunities` and
   `/opportunities/:opportunityId`, both behind the existing `authGuard`.
6. **Navigation** — single sidebar anchor after "Markets"; no navbar redesign.
7. **Design** — reuse current dark-slate card/typography patterns from markets;
   subtle blue accents; no new design tokens.

## Test plan

* Service: active list + detail calls hit correct URLs (HttpTestingController).
* List page: loading first, then loaded rows; empty stays non-error; HTTP
  failure shows error state; navigation on row selection.
* Detail page: renders fields for an id; not-found state on 404; error state on
  failure; back link present.
* Sidebar: entry navigates to `/opportunities`.

## Validation

`npm run test:ci`, `npm run build`, then repository quality pipeline commands
that apply to frontend changes. No backend module is modified, so backend
suites are not re-run beyond confirming zero backend diffs (`git diff --stat`).
