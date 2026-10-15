# Story 0102 - Implementation Plan

## Approved Analysis Basis

The existing News Service contract and Gateway route are sufficient. The
implementation is limited to the Angular frontend and Story 0102 documentation;
no News Service domain, persistence, provider, or Gateway changes are planned.

## Implementation Steps

### 1. Add typed frontend contracts

Create provider-neutral TypeScript types for:

- normalized economic events;
- impact and lifecycle status values;
- availability status;
- query parameters;
- the News Service read response.

Use the backend field names and preserve nullable values for actual, forecast,
previous, unit, source update time, and attribution.

### 2. Add the API service

Create an injectable Angular service under the existing core-services structure.
It will call:

```text
${environment.gatewayUrl}v1/news/events
```

It will expose an Observable and contain no presentation or intelligence logic.
Query parameters will be serialized from the selected date window, impact, and
currency filters.

### 3. Add the Economic Context feature

Create one standalone page component under a new focused feature directory. The
page will own reactive filter state and refresh state using the existing RxJS
patterns. It will derive a default current-day/near-future UTC window, request
the API through `switchMap`, and expose a view model through the async pipe.

The template will provide:

- semantic page heading and context summary;
- date-window controls;
- impact and currency filters;
- refresh control;
- loading state;
- unavailable/error state based on response status and request failure;
- distinct no-results state;
- chronological event cards/rows;
- readable event timing and timezone indication;
- visible impact and lifecycle status;
- previous, forecast, actual, and unit only when present;
- source attribution and fetched time when provided.

### 4. Add navigation and routing

Add an authenticated `/economic-context` route and one sidebar link. Do not
restructure the existing navigation or add a separate frontend architecture.

### 5. Add styling and accessibility

Follow existing dark dashboard styling with restrained accents, rounded panels,
clear hierarchy, and responsive event cards rather than a narrow desktop-only
table. Use labels, semantic controls, keyboard-accessible interaction, and
state indicators that do not rely only on color.

### 6. Add focused tests

Cover:

- successful API loading;
- chronological rendering;
- impact and lifecycle presentation;
- actual/forecast/previous rendering and omission of absent values;
- filter interaction and query propagation;
- empty result state;
- unavailable response state;
- HTTP error state;
- service URL and query construction where appropriate.

Use mocked HTTP/API data and do not require a live provider.

### 7. Reconcile documentation

Add an implementation report and runtime validation report under the Story
directory after implementation. Update only canonical documentation that is
directly affected by the new user-facing route or validation outcome. Do not
rewrite the historical Story 0074 investigation.

## Validation Plan

Run the following after implementation:

```text
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
cd news-service && mvn test
cd news-service && mvn verify
cd gateway && mvn test
docker compose config -q
git diff --check
```

Then perform isolated runtime validation where the environment permits:

- verify PostgreSQL and Flyway startup for News Service;
- verify News Service and Gateway health;
- request `/api/v1/news/events` through Gateway;
- open the authenticated frontend route;
- verify loading, events, chronological display, filtering, empty, and
  unavailable/error states.

Live XOOMAR validation will be reported separately and will not be inferred
from fixture or persisted-data validation.

## Risks And Mitigations

- **Contract drift:** keep TypeScript fields aligned with `NewsController` and
  test representative response fixtures.
- **Unavailable versus empty confusion:** map response availability separately
  from `items.length` and test both states.
- **Timezone ambiguity:** preserve ISO instants and label the displayed timezone.
- **Frontend runtime unavailable:** report exact environment blockers without
  weakening authentication or adding direct service URLs.
- **Scope expansion:** stop rather than modify News Service if the existing
  contract proves insufficient.

## Expected Files

- Story 0102 documentation and reports.
- Angular core models for news/economic events.
- Angular News/economic-calendar service and tests.
- Economic Context page component, template, styles, and tests.
- Angular route and sidebar navigation updates.

No backend production files are expected to change.

## Approval

This plan is ready for explicit human approval. Implementation must not begin
until the plan is approved.
