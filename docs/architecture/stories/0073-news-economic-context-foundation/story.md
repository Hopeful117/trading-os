# Story 0073 - News and Economic Context Foundation

## Metadata

**ID:** `0073`
**Title:** News and Economic Context Foundation
**Status:** CLOSED - HUMAN ACCEPTED

The implementation, automated validation, and independent code review were
reviewed and accepted by the human engineer. The final human Git commit remains
pending under the repository workflow; this status does not authorize another
implementation, commit, push, or merge operation.
**Predecessor:** None
**Size:** Large
**Implementation Risk:** High
**ADR:** ADR-008 - News Service Responsibilities
**Related ADRs:** ADR-020 - Market Intelligence Architecture, ADR-022 - Artifact
Management, ADR-048 - Intelligence Evidence and Authority

This Story establishes the provider-neutral foundation for News Service. It does
not select or integrate a production news provider.

## Goal

Introduce the dedicated News Service boundary and expose normalized, persisted
economic-event and financial-news facts that downstream consumers can use without
depending on provider payloads.

The target boundary is:

```text
external provider adapter(s) [future Story]
             |
             v
       News Service
   normalize, persist, expose
             |
             +--> Market Intelligence context contributor
             +--> future Trading Core rules
             +--> future scanners and monitoring
```

The service provides facts only. It does not recommend trades, rank
opportunities, evaluate risk, or execute orders.

## Context

The repository architecture already defines News Service as the authoritative
owner of:

- economic calendar data;
- macroeconomic events;
- financial news;
- market-impact metadata.

Market Intelligence already declares `NEWS` as an optional context section for
active analysis, but no News Service, normalized model, persistence adapter,
provider port, or context contributor exists. The current repository therefore
has an architectural extension point but no implementation.

Provider selection is intentionally not decided by this Story. ADR-048 leaves
news-provider selection and macro-event semantics to a future domain decision.

## Problem

Without this boundary, consumers would either retrieve external news directly,
duplicate normalization logic, or expose provider-specific payloads. That would
make provenance, freshness, historical retention, and downstream failure
handling inconsistent.

## Scope

### Included

- Create the dedicated `news-service` Spring Boot service following repository
  service conventions.
- Define provider-neutral economic-event and financial-news domain models.
- Represent scheduled time, affected currencies or markets, source identity,
  publication/update timestamps, impact classification, event status, and
  provenance.
- Define explicit statuses for available, stale, unavailable, unsupported, and
  incomplete source data where required by the read contract.
- Define ports for economic-calendar synchronization and financial-news
  ingestion without introducing a production provider implementation.
- Persist normalized facts with bounded query support and deterministic identity
  or deduplication semantics.
- Expose authenticated, versioned read APIs for upcoming and historical economic
  events and normalized financial-news items.
- Preserve provider-neutral public contracts; provider payloads, credentials,
  symbols, errors, and transport details remain inside future adapters.
- Add a deterministic fixture or test adapter sufficient to validate the
  service, persistence, query, and normalization contracts without external
  credentials.
- Add a Market Intelligence `NEWS` context contributor that consumes the
  provider-neutral News Service contract and reports unavailable or stale data
  explicitly without making News mandatory for existing analysis paths.
- Preserve source timestamps, fetch timestamps, source identifiers, and
  normalization version metadata for traceability.
- Add unit, persistence, contract, and integration tests for the new boundary.

### Out of Scope

- Selecting a production news or economic-calendar provider.
- Production provider credentials, API quotas, paid subscriptions, or external
  synchronization scheduling.
- Provider-specific adapters or provider payloads in public contracts.
- Semantic interpretation of news, sentiment scoring, trade recommendations,
  opportunity ranking, risk decisions, or execution decisions.
- AI/LLM summarization, embeddings, RAG, or agent workflows.
- Passive scanning, position monitoring, or alert automation.
- Changing existing Trend Context, Market Structure, Strategy, Opportunity,
  TradePlan, Risk, or Execution semantics.
- Making News a required dependency for existing Market Intelligence analyses.
- Trading Core pass-through APIs that add no business value.
- Frontend news pages or dashboard presentation.
- Cross-currency conversion or unsupported market-identifier inference.
- Unbounded historical ingestion or a general-purpose event-sourcing platform.

## Normalized Facts

### Economic Event

The normalized contract must support at least:

- stable event identity and source identity;
- title and event category;
- scheduled release time and optional actual release time;
- affected currency codes and provider-neutral market references;
- impact classification: `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN`;
- lifecycle status such as `SCHEDULED`, `RELEASED`, `CANCELLED`, or
  `UNKNOWN`;
- optional consensus, previous, and actual values without provider-specific
  units leaking into the domain;
- source timestamp, fetched timestamp, and normalization version.

The model must distinguish an unknown value from a zero, a missing event from an
event with no impact classification, and a stale event from an unavailable
source.

### Financial News Item

The normalized contract must support at least:

- stable item identity and source identity;
- title, summary or excerpt, canonical URL, and publication time;
- source/publisher metadata;
- categories and affected currencies or market references;
- optional informational impact classification;
- source timestamp, fetched timestamp, and normalization version.

The service stores facts and metadata. It does not claim that an article's
impact classification is a trading conclusion.

## Acceptance Criteria

### AC1 - Dedicated ownership boundary

- A dedicated `news-service` module exists and is registered using the existing
  service-discovery and deployment conventions.
- News and economic-calendar acquisition, normalization, persistence, and read
  APIs belong to News Service.
- Market Intelligence does not acquire provider data directly.
- Trading Core does not become the owner of news or economic-event persistence.

### AC2 - Provider-neutral contracts

- Public contracts contain no provider payload, provider symbol, provider error,
  credential, or provider transport type.
- Economic events and financial-news items preserve source identity and
  provenance without exposing source-specific transport details.
- Unknown, unavailable, stale, unsupported, and incomplete data states are
  distinguishable in the service and consumer contracts.

### AC3 - Normalized economic events

- Events support scheduled time, affected currencies or markets, category,
  impact classification, lifecycle status, and source timestamps.
- Event identity and deduplication are deterministic for repeated source data.
- Historical events remain queryable within an explicit bounded retention/query
  contract.
- No event classification is exposed as a risk, strategy, or execution decision.

### AC4 - Normalized financial news

- News items support title, summary/excerpt, canonical URL, publication time,
  publisher, categories, affected references, and provenance.
- Repeated source items are deduplicated deterministically.
- The service does not generate recommendations, sentiment decisions, or trade
  plans.

### AC5 - Read API

- Versioned authenticated endpoints expose bounded queries for upcoming and
  historical events and news items.
- Queries support explicit time boundaries and bounded result limits.
- Filters for impact and affected references are applied deterministically when
  supplied.
- Empty, unavailable, and stale results are represented explicitly rather than
  silently converted into fabricated data.

### AC6 - Market Intelligence integration

- Active Market Intelligence analysis can request the existing `NEWS` context
  section through a provider-neutral client.
- The contributor preserves source and fetch provenance and reports freshness.
- News remains optional for existing active analysis unless a later Story makes
  a specific capability depend on it.
- News-service unavailability degrades the context explicitly and does not
  cause fabricated factual findings.
- No Market Intelligence capability turns news facts directly into a risk or
  execution decision.

### AC7 - No production provider assumption

- Tests run with a deterministic fixture/test adapter and require no external
  credentials or network access.
- The production configuration makes the absence of a configured provider
  explicit.
- A future provider integration can implement the defined ports without
  changing normalized public contracts.

### AC8 - Validation and boundaries

- Unit tests cover normalization, identity, deduplication, status handling,
  freshness, and bounded filtering.
- Persistence tests cover reload, historical queries, and deterministic identity.
- Contract tests cover News Service reads and the Market Intelligence contributor.
- Existing Market Intelligence tests continue to pass with News unavailable.
- `git diff --check` passes.
- No strategy match, opportunity, trade plan, risk result, execution intent, or
  broker operation is created by News Service or its context contributor.

## Constraints

- Follow ADR-008 for ownership and responsibilities.
- Follow ADR-020 for modular Intelligence Context integration.
- Follow ADR-022 for source ownership and provenance of primary data.
- Follow ADR-048 for factual evidence, authority, temporal integrity, and
  explicit provider-neutral boundaries.
- Do not invent a production provider or macro-event semantic taxonomy beyond
  the fields required by this Story.
- Keep provider-specific code inside future infrastructure adapters.
- Keep News facts informational; deterministic Risk remains authoritative for
  risk decisions.
- Preserve unrelated worktree changes.
- Do not commit, push, merge, or deploy automatically.

## Dependencies

- Existing Eureka, Gateway, security, and PostgreSQL service conventions.
- Existing Market Intelligence `ContextContributor` and `NEWS` section type.
- Existing repository patterns for versioned REST APIs, JWT/service JWT,
  persistence migrations, and integration tests.
- A future provider integration Story is required before claiming live News or
  live economic-calendar data availability.

## Validation

- Run focused News Service tests.
- Run Market Intelligence tests including unavailable/stale News behavior.
- Run service contract and persistence integration tests.
- Validate service discovery, authentication, bounded read APIs, and local
  fixture behavior in the compose environment.
- Run `git diff --check`.
- Report explicitly that no production external provider was exercised.

## Definition of Done

- [ ] Human approval of Story 0073 scope.
- [ ] Repository Analysis and Implementation Plan accepted if required.
- [ ] Dedicated News Service boundary implemented.
- [ ] Provider-neutral economic-event and financial-news contracts implemented.
- [ ] Normalized facts persisted and queryable with deterministic identity.
- [ ] Bounded authenticated read APIs implemented.
- [ ] Market Intelligence consumes optional News context through its own client.
- [ ] Fixture/test adapter validates the boundary without external credentials.
- [ ] Existing analysis behavior remains valid when News is unavailable.
- [ ] Relevant tests and integration validation pass.
- [ ] No trading decision or execution side effect is introduced.
- [ ] Human review completed before integration.
