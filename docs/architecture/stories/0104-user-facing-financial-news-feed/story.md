# Story 0104 - User-Facing Financial News Feed

## Metadata

- **ID:** `0104`
- **Title:** User-Facing Financial News Feed
- **Status:** IMPLEMENTATION COMPLETE - HUMAN REVIEW PENDING
- **Related Stories:** `0102`, `0103`
- **Related ADRs:** `ADR-008`, `ADR-020`, `ADR-022`, `ADR-048`

## Goal

Expose normalized Financial News directly in the Trading OS Angular frontend so
the human trader can inspect the factual information consumed by future Market
Intelligence layers.

The page is a read-only source-fact surface. It must not interpret news,
generate recommendations, or expose provider-specific behavior.

## Product Principle

```text
source facts
    -> normalized FinancialNewsItem
    -> human-visible Financial News UI
    -> Market Intelligence
         -> future AI/ML
```

The user must be able to inspect what happened, when it was published, who
published it, which normalized currencies or markets are associated with it,
and where the original source can be inspected.

## Context

Story 0102 introduced the authenticated Angular Economic Context experience and
its established dashboard, navigation, API-service, reactive and visual
patterns. Story 0103 introduced the provider-neutral Financial News backend
contract and retained Alpha Vantage as a disabled provider implementation.

The frontend must consume the existing public Financial News API contract. It
must remain unchanged if the backend acquisition provider later changes from
Alpha Vantage to Marketaux or another provider.

Financial News provider acquisition is not part of this Story. Alpha Vantage
must remain disabled by default, and Marketaux must not be implemented while
provider-rights clarification is pending.

## Problem

Normalized Financial News currently exists as machine-readable backend context,
but the human trader cannot inspect those source facts directly through the
frontend. This prevents independent review of the factual context that future
AI or Market Intelligence recommendations may consume.

## Scope

- Add an authenticated, directly reachable Financial News frontend route.
- Integrate the route into the existing navigation without redesigning the
  global navigation system.
- Reuse the Economic Context page's established visual and architectural
  conventions where appropriate.
- Add typed provider-neutral frontend models for the actual public Financial
  News response contract.
- Add or extend a typed Angular API service for `/api/v1/news/items` or the
  actual repository endpoint.
- Present compact, scannable news cards or rows containing, when available:
  title, publisher, publication time, summary, categories, currencies or market
  identifiers, and a canonical source link.
- Represent loading, empty, unavailable, stale, incomplete and HTTP-error states
  according to the actual public API contract.
- Use reactive Observables and template binding/async-pipe conventions already
  established by the application.
- Add only filters supported by the actual backend contract and keep the first
  version intentionally small.
- Preserve responsive behavior at desktop, tablet and mobile widths.
- Add focused frontend tests for observable user behavior and API request
  mapping.
- Validate that Economic Calendar navigation and behavior do not regress.

## Out Of Scope

- Marketaux integration, credentials, polling or live requests.
- Changes to Alpha Vantage acquisition, `NEWS_SENTIMENT`, provider quotas or
  runtime enablement.
- Provider-specific frontend logic, DTOs, icons, parsing or fallback behavior.
- Provider aggregation or active-provider selection architecture.
- AI, LLM, ML, RAG, embeddings, sentiment analysis, relevance scoring or
  recommendations.
- Bullish/bearish interpretation or provider sentiment visualization.
- Risk, opportunity, TradePlan, Challenge, broker, account or execution changes.
- News Service persistence redesign, database migrations or provider changes.
- Fake client-side filtering over an incomplete bounded result set.
- Aggressive polling or a provider-synchronization button.
- A new frontend framework or state-management library.
- A large navigation or design-system redesign.

## Architecture

```text
Authenticated Angular Financial News page
              |
              v
Gateway /api/v1/news/**
              |
              v
News Service normalized Financial News API
              |
              v
FinancialNewsItem persistence/catalog
```

The Angular frontend owns presentation, route state and supported query
construction. Gateway and News Service remain responsible for authentication,
availability semantics, normalization, acquisition and persistence.

The frontend must not know about Alpha Vantage, Marketaux, provider tokens,
provider quotas, `NEWS_SENTIMENT`, raw provider payloads or provider-specific
response DTOs.

## User Experience

The page should provide:

- a clear `Financial News` heading;
- freshness or availability information when supplied by the API;
- compact supported filters, if the backend exposes useful capabilities;
- a dense but readable list of recent articles, newest first when guaranteed by
  the backend;
- visible title hierarchy with publisher and unambiguous publication time;
- readable summary/excerpt without presenting it as AI-generated analysis;
- compact category and currency/market metadata;
- a safe external link to the canonical source when available;
- intentional loading, empty, unavailable, stale, incomplete and retryable
  error states.

Unknown impact, when exposed, must remain unknown and must not be presented as
low, neutral or unimportant. Sentiment must not be displayed.

## Reactive Frontend Constraints

- Prefer typed `Observable` flows, RxJS operators, derived streams and the async
  pipe.
- Avoid unnecessary manual subscriptions, mutable lifecycle state and
  `ChangeDetectorRef`.
- Reuse the existing authentication interceptor and route guards.
- Keep API services responsible for typed requests and responses, not
  presentation state.
- Do not use `any` or unsafe casts for the Financial News contract.
- Follow the existing standalone component, SCSS, responsive and testing
  conventions.

## Accessibility And Security

- Use semantic headings and meaningful link labels.
- Preserve visible focus states and keyboard-accessible controls.
- Do not communicate status using color alone.
- Open external source links with safe browser attributes such as
  `target="_blank"` and `rel="noopener noreferrer"` where appropriate.
- Reuse the existing JWT and authentication flow; do not handle tokens in the
  page component.
- Never expose API keys, raw provider payloads or backend stack traces.

## Acceptance Criteria

### Navigation

- [ ] An authenticated user can open a dedicated Financial News route.
- [ ] The route fits the existing Trading OS information architecture.
- [ ] Economic Calendar remains accessible and unchanged in behavior.

### Data Contract And Feed

- [ ] The page consumes the actual normalized provider-neutral Financial News
      API contract.
- [ ] No provider DTO, API key, provider quota or provider-specific logic reaches
      Angular.
- [ ] Titles render as the primary article information.
- [ ] Publisher and publication time render when present, using existing
      timezone conventions without fabricating timestamps.
- [ ] Summary/excerpt renders when available and is not labeled as AI analysis.
- [ ] Categories and currencies/markets render compactly when available.
- [ ] Missing optional fields do not break the layout or create fake values.
- [ ] A canonical source link works when available and is absent when unavailable.
- [ ] The default ordering is newest publication time first when supported by the
      backend contract.

### States And Interaction

- [ ] Loading is explicit and does not require a large global spinner.
- [ ] An empty catalog is distinct from provider unavailability.
- [ ] HTTP/network failure is explicit and retryable according to existing UI
      conventions.
- [ ] `STALE` and `INCOMPLETE` are represented when exposed by the API.
- [ ] Availability messages do not expose secrets, stack traces or raw provider
      payloads.
- [ ] Filters are implemented only when supported by the actual backend
      contract, with request mapping covered by tests.
- [ ] Refresh, if provided, reloads normalized backend data and never directly
      triggers provider acquisition.

### Architecture And Boundaries

- [ ] API calls return typed Observables and reactive composition is preferred.
- [ ] No unnecessary manual subscriptions or `ChangeDetectorRef` are introduced.
- [ ] No Marketaux integration is added.
- [ ] Alpha Vantage remains retained and disabled by default.
- [ ] No provider aggregation, AI, LLM, ML, sentiment, risk, execution or
      trading side effect is added.
- [ ] No backend domain, persistence or provider change is made for frontend
      convenience.

### Visual, Responsive And Accessible Experience

- [ ] The page matches the dark slate Trading OS dashboard visual language.
- [ ] Multiple headlines remain efficiently scannable on a desktop viewport.
- [ ] Long titles and summaries remain readable and bounded safely.
- [ ] The layout remains usable at desktop, tablet and mobile widths.
- [ ] Headings, links, buttons, focus states and status indicators meet basic
      keyboard and semantic accessibility expectations.

### Tests And Validation

- [ ] Focused frontend tests cover successful feed rendering, title, publisher,
      publication time, summary, metadata and source link behavior.
- [ ] Tests cover missing optional fields, loading, empty, unavailable and HTTP
      error states.
- [ ] Filter request mapping is tested if filters are implemented.
- [ ] Frontend production build passes.
- [ ] Relevant Gateway and News Service validation passes if their contracts are
      changed; otherwise no unnecessary backend change is introduced.
- [ ] `docker compose config -q` passes when applicable.
- [ ] `git diff --check` passes.
- [ ] Browser/runtime validation reports route, authentication, Gateway request,
      state presentation, responsive behavior, source links and console status
      accurately, without claiming populated provider data if unavailable.

## Runtime Validation Expectations

Runtime validation must not require a live provider call. It should verify:

- authenticated navigation to the Financial News route;
- Gateway access to the normalized Financial News endpoint;
- correct empty or unavailable rendering when no production articles exist;
- safe rendering of deterministic local data only when an existing development
  or test mechanism provides it;
- continued Economic Calendar operation;
- no Alpha Vantage enablement and no Marketaux request.

The absence of production articles is not a Story failure when the endpoint
correctly reports an empty or unavailable provider state.

## Documentation

The implementation must include a Story-local implementation report and runtime
validation report following repository conventions. The final records must state
that live Financial News acquisition remains independent from this frontend
Story, Alpha Vantage remains disabled by default, and Marketaux is not
integrated pending provider-rights clarification.

## Validation Commands

Determine the exact repository commands before execution. At minimum, execute
the applicable equivalents of:

```bash
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
docker compose config -q
git diff --check
```

Run News Service or Gateway tests only when their contracts are changed or
regression validation requires them.

## Definition Of Done

- [ ] Repository Analysis completed and approved.
- [ ] Implementation Plan completed and approved when required.
- [ ] Financial News route and navigation implemented.
- [ ] Provider-neutral API models/service and reactive UI implemented.
- [ ] Loading, empty, unavailable, stale, incomplete and error states validated
      according to the actual contract.
- [ ] Frontend tests and production build pass.
- [ ] Runtime and browser validation reported accurately.
- [ ] Economic Calendar regression validation passes.
- [ ] Documentation reconciliation completed.
- [ ] Independent code review completed.
- [ ] Human commit created.
