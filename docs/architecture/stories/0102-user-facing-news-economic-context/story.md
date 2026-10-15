# Story 0102 - User-Facing News and Economic Context

## Metadata

- **ID:** `0102`
- **Status:** IMPLEMENTED - RUNTIME VALIDATED WITH PROVIDER-UNAVAILABLE STATE
- **Related Stories:** `0073`, `0074`, `0101`
- **Related ADRs:** `ADR-008`, `ADR-020`, `ADR-048`

## Goal

Expose the normalized economic context already produced by News Service directly
to the Trading OS user through the Angular frontend, so the human trader can
inspect decision-relevant macroeconomic information independently from future AI
recommendations.

## Product Problem

News and macroeconomic context must not exist solely as machine-consumable
information. Because Trading OS keeps the human user in the decision loop, the
user must be able to inspect relevant normalized source information
independently from AI or Market Intelligence recommendations.

The product invariant is:

```text
machine-readable context != machine-only context
```

Information that materially contributes to a trading recommendation should,
where practical and legally permitted, be inspectable by the user. The surface
must expose provider-neutral normalized context, not raw provider payloads,
internal transport DTOs, secrets, or debug traces.

## Current Contract

The existing authenticated endpoint is sufficient for this Story:

```text
GET /api/v1/news/events
```

It supports bounded `from`, `to`, `currency`, `impact`, `marketId`, and `limit`
queries and returns `status`, `items`, `fetchedAt`, `message`, and optional
`attribution`. Economic events expose normalized title, category, scheduled and
actual timestamps, currencies, impact, lifecycle status, previous, forecast,
actual, unit, source attribution, and provenance timestamps.

Financial-news presentation is excluded because no provider-backed financial
news feed is currently available.

## Scope

- Add typed Angular models for the existing economic-event response.
- Add a reactive Angular API service using the Gateway-backed News endpoint.
- Add an authenticated, discoverable Economic Context frontend route.
- Present events chronologically with timing, lifecycle status, importance,
  currencies, and available previous/forecast/actual values.
- Add useful V1 filters supported by the existing contract: date window,
  importance, and currency.
- Provide explicit loading, empty, unavailable, and request-error states.
- Display lightweight attribution and last-fetched information when available.
- Add restrained responsive Trading OS styling and accessible controls.
- Add focused frontend tests for loading, ordering, fields, filters, empty and
  unavailable states.
- Validate News Service and Gateway regressions, frontend tests/build, Compose
  startup, Gateway API flow, and the actual frontend user flow.

## Out Of Scope

- LLM calls, ML models, sentiment analysis, summarization, or recommendations.
- New News Service providers or economic-calendar providers.
- Financial-news scraping or a decorative empty news feed.
- News Service persistence, domain, or provider changes unless a genuine
  user-facing capability cannot be expressed through the current contract.
- Opportunity, scanner, risk, TradePlan, execution, or challenge changes.
- Automatic trade rejection or risk decisions based on events.
- Raw provider payloads, provider-specific DTOs, secrets, or debug details.
- A full month-calendar widget unless existing frontend conventions require it.

## Architecture

```text
Angular Economic Context
        |
        v
Gateway /api/v1/news/**
        |
        v
News Service normalized economic-event API
        |
        v
PostgreSQL-backed catalog
```

The frontend remains a human information surface. Market Intelligence may use
the same normalized context independently; this Story does not couple the UI to
AI recommendations.

## Acceptance Criteria

- [ ] The authenticated user can discover and open an Economic Context page.
- [ ] The frontend calls the existing Gateway News route and does not call the
      News Service container directly.
- [ ] Events render in chronological order with understandable timezone
      semantics.
- [ ] Event title, timing, currencies, normalized importance, and lifecycle
      status are visible without inspecting JSON.
- [ ] Previous, forecast, actual, and unit are displayed when present and are
      not invented when absent.
- [ ] Date-window, importance, and currency filters work through the existing
      API capabilities.
- [ ] Loading, no-results, unavailable-provider, and request-error states are
      distinct and user-understandable.
- [ ] Attribution and fetched-time provenance are displayed when supplied by
      the API.
- [ ] The surface remains usable at desktop, laptop, and tablet-width layouts.
- [ ] Controls and state indicators have basic semantic and keyboard-accessible
      behavior and do not rely only on color.
- [ ] Focused frontend tests cover successful loading, chronological rendering,
      importance, event values, filtering, empty state, and unavailable/error
      states.
- [ ] News Service tests and verification pass without regression.
- [ ] Gateway tests pass and `/api/v1/news/**` routing remains functional.
- [ ] Frontend tests and production build pass.
- [ ] Isolated Compose validation proves News Service starts against PostgreSQL
      and Flyway migrations complete.
- [ ] Runtime API validation proves Angular-facing Gateway to News Service flow.
- [ ] Runtime frontend validation proves navigation, loading, filtering, and
      state presentation.
- [ ] Live XOOMAR validation is reported accurately and is not represented as
      successful unless actually executed.

## Constraints

- Preserve the existing News Service and Gateway contracts.
- Keep provider-specific implementation details out of frontend models.
- Use standalone Angular components and existing RxJS/async-pipe conventions.
- Keep provider polling and synchronization responsibility in News Service.
- Do not fabricate events or silently present unavailable data as current.
- Do not modify unrelated untracked runtime reports.
- Do not commit, push, merge, or rewrite history automatically.

## Validation

- `cd news-service && mvn test && mvn verify`
- Gateway module tests.
- `cd trading-os-web && npm run test:ci && npm run build`
- `git diff --check`
- Isolated Compose startup and health checks.
- Gateway request to `/api/v1/news/events` with a representative response.
- Browser flow through the authenticated frontend route.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciled.
- [ ] Frontend and backend validation reported accurately.
- [ ] Runtime API and frontend validation completed or explicitly blocked.
- [ ] Independent code review completed.
- [ ] Human commit created.
