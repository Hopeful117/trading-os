# Story 0103B - Marketaux Financial News Provider

## Metadata

- **ID:** `0103B`
- **Title:** Add a bounded Marketaux financial-news provider
- **Status:** PROPOSED - INVESTIGATION COMPLETE, IMPLEMENTATION NOT APPROVED
- **Related Story:** `0103` - Alpha Vantage Financial News Provider
- **Related ADRs:** `ADR-008`, `ADR-020`, `ADR-022`, `ADR-048`

## Goal

Provide Trading OS with a bounded Marketaux-backed financial-news acquisition
path while preserving the provider-neutral News Service contract and keeping
Alpha Vantage available as a disabled future alternative.

## Context And Decision Gate

The Marketaux investigation found that its API contract maps to the existing
`FinancialNewsItem` model without domain changes. Marketaux provides a stable
article UUID, UTC publication timestamps, publisher domains, links, bounded
snippets and entity metadata.

The integration is not authorized for implementation until Marketaux provides
written clarification that the intended personal, non-commercial automated
polling, normalized storage, and any future processing are permitted. The
investigation identified an ambiguity between the API product and the broad
non-human-access restriction in the Terms of Use.

## Problem

The current Alpha Vantage path is provider-neutral and validated, but the local
Free runtime returned a quota or entitlement failure. A second provider could
provide usable development-time financial-news facts, but it must not create
ambiguous provider selection, unsafe polling, licensing exposure, or leakage of
provider-specific payloads into the domain.

## Scope

- Add a Marketaux infrastructure adapter implementing the existing
  `FinancialNewsSourcePort`.
- Use `GET /v1/news/all` with bounded filters, limits, time windows and page
  handling.
- Map Marketaux `uuid`, title, description or snippet, URL, publication time,
  source domain, and bounded entity metadata to `FinancialNewsItem`.
- Keep Marketaux DTOs, token handling, HTTP transport, error translation and
  rate-limit handling inside News Service infrastructure.
- Preserve deterministic provider identity using `sourceName=marketaux` and
  the provider UUID.
- Add explicit provider selection or another approved single-active-provider
  policy before enabling Marketaux, because the current catalog read path does
  not safely resolve multiple active financial-news providers.
- Add bounded opt-in synchronization with no unsafe automatic retry of write or
  acquisition operations.
- Add deterministic adapter, configuration, synchronization and provider-error
  tests without requiring a live Marketaux token.
- Document attribution, data-use restrictions, retention limits and the exact
  provider configuration required for activation.

## Out Of Scope

- Implementing or enabling Marketaux before written provider authorization.
- Removing, rewriting or re-enabling Alpha Vantage by default.
- Provider aggregation, failover or cross-provider reconciliation.
- Full article retrieval, scraping, raw payload persistence or redistribution.
- Mapping Marketaux sentiment to Trading OS impact.
- AI processing, embeddings, RAG, clustering, ML datasets or recommendations.
- New public APIs, frontend financial-news presentation, risk, opportunity,
  TradePlan, challenge or execution behavior.
- Historical backfill beyond an explicitly bounded synchronization window.

## Acceptance Criteria

- [ ] Written provider clarification covers automated polling, normalized
      persistence, personal/non-commercial use, retention and future
      processing before runtime enablement.
- [ ] Marketaux-specific request and response types remain inside the News
      Service infrastructure adapter.
- [ ] The adapter uses the documented Marketaux endpoint and bounded request
      parameters, including the Free-tier limit of three articles per request.
- [ ] Marketaux UUID is preserved as `sourceItemId` and produces stable,
      deterministic Trading OS identity across repeated synchronization.
- [ ] Publication timestamps are parsed as UTC and are never replaced by the
      local acquisition time.
- [ ] Title, bounded description or snippet, canonical URL, publisher domain and
      accepted entity metadata are normalized without provider leakage.
- [ ] Marketaux sentiment remains unmapped to Trading OS `ImpactLevel` and
      unsupported market mappings remain absent rather than guessed.
- [ ] Invalid tokens, usage limits, rate limits, restricted endpoints,
      transport failures and malformed responses produce explicit unavailable or
      incomplete results without deleting existing catalog rows.
- [ ] Only one financial-news provider is active for the current catalog read
      path, or an approved provider-selection design is implemented first.
- [ ] Marketaux synchronization is disabled by default and is bounded by safe
      configuration; Alpha Vantage remains disabled by default.
- [ ] Automated tests cover mapping, UTC timestamps, identity, empty responses,
      pagination limits, provider errors, configuration and synchronization.
- [ ] Existing Alpha Vantage behavior, provider-neutral APIs and Market
      Intelligence contracts remain unchanged.
- [ ] News Service verification, Compose startup, PostgreSQL/Flyway startup and
      `git diff --check` pass without live credentials.
- [ ] Any live validation uses the minimum number of calls and records only
      normalized evidence; credentials and tokens are never logged or stored in
      repository artifacts.

## Constraints

- Preserve News Service ownership of financial-news acquisition and normalized
  persistence.
- Keep provider-specific implementation details out of domain, API and Market
  Intelligence contracts.
- Preserve deterministic normalization, additive persistence and availability
  semantics.
- Redact the `api_token` from HTTP/access logs and diagnostics.
- Do not poll continuously until the provider authorization ambiguity is
  resolved.
- Do not infer cryptocurrency symbols or Trading OS market UUIDs from an
  undocumented mapping.
- Do not commit secrets, provider tokens or raw article payloads.
- Do not commit, push, merge or rewrite history automatically as part of future
  implementation work.

## Relevant Modules

- `news-service`
- `market-intelligence`
- `docker-compose.yml`

## Validation

- `cd news-service && mvn -B verify`
- Targeted adapter and synchronization tests with mocked HTTP responses.
- Compose startup against PostgreSQL with provider disabled by default.
- Verification that Alpha Vantage remains retained and disabled.
- Review of provider-neutral API and Market Intelligence contracts.
- `git diff --check`
- Separate, explicitly reported live validation only after written authorization.

## Definition Of Done

- [ ] Provider authorization and data-use decision recorded.
- [ ] Repository Analysis approved.
- [ ] Provider-selection decision approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Automated and runtime validation reported accurately.
- [ ] Independent code review completed.
- [ ] Human commit created.
