# Story

## Metadata

**ID:** `0074`

**Title:** Integrate XOOMAR economic calendar data

**Status:** CLOSED - HUMAN ACCEPTED

The XOOMAR adapter, event-source preservation, bounded synchronization,
attribution documentation and automated validation were independently reviewed
and accepted by the human engineer. No live XOOMAR request was executed. The
final human Git commit remains pending under the repository workflow; this
status does not authorize another implementation, commit, push, or merge
operation.

---

## Goal

Provide Trading OS with a first usable production economic calendar for macroeconomic events that can affect crypto markets, while keeping the news-service domain independent from XOOMAR.

The initial provider is XOOMAR because its calendar endpoint is available without an API key, exposes UTC timestamps and stable event identifiers, and provides a free personal-use licence with attribution.

---

## Context

Story 0073 introduced the news-service economic-event domain, persistence model, freshness reporting and internal consumption contract.

The current service has no production economic-calendar adapter. Its existing `NewsSourcePort` is a combined fixture-oriented contract for economic events and financial news.

The selected XOOMAR endpoint is:

```text
GET https://xoomar.com/api/markets/calendar
```

The endpoint returns an envelope containing `data`, `updatedAt`, `source`, attribution information and economic records. Records include `source`, `eventName`, `importance`, `scheduledAt`, `periodLabel`, `previous`, `forecast`, `actual` and `unit`.

XOOMAR documents a free unauthenticated limit of 10 requests per minute per IP and a free personal/internal-use licence requiring attribution. The integration must therefore remain bounded, cache through the existing persistence layer and expose the attribution in provider configuration or operational documentation where appropriate.

Relevant external references:

* https://xoomar.com/markets/api/calendar
* https://xoomar.com/terms

---

## Problem

The news-service currently cannot retrieve or persist economic events from a production source. As a result, Market Intelligence can only consume fixture or manually populated data and cannot reliably include upcoming macroeconomic context in decision analysis.

The integration must also avoid treating provider data as strategic advice. It should normalize source facts only; interpretation remains the responsibility of Market Intelligence and deterministic risk decisions remain outside this service.

---

## Scope

* Add a dedicated provider-neutral economic-calendar source contract.
* Implement the XOOMAR HTTP adapter inside the news-service infrastructure boundary.
* Request a bounded UTC date window and enforce the existing application query limits.
* Map XOOMAR records to `EconomicEvent` without exposing XOOMAR DTOs through domain or API contracts.
* Preserve XOOMAR's stable event identifier as the source identity.
* Map XOOMAR `importance` values to the existing impact model, with unknown values handled safely.
* Preserve previous, forecast and actual values as source values without lossy numeric conversion.
* Persist events idempotently using the existing source identity constraint.
* Add opt-in synchronization configuration with safe defaults and bounded request frequency.
* Handle empty responses, malformed records, timeouts, non-success responses and rate limiting without corrupting existing events.
* Add tests covering mapping, timestamps, null values, provider errors, source identity and repeated synchronization.
* Preserve the existing financial-news path and fixture-based tests unless compilation requires a minimal adapter change.

---

## Out of Scope

* AI interpretation, sentiment or bullish/bearish classification.
* Automatic risk-rule changes or trade execution.
* Changes to the `EconomicEvent` public meaning solely to mirror XOOMAR fields.
* Financial-news provider integration.
* Frontend calendar presentation.
* Historical backfill beyond the configured synchronization window.
* Provider failover or multi-provider reconciliation.
* Redistributing raw XOOMAR data as a separate public feed.
* Introducing an API key or secret for XOOMAR in the repository.

---

## Acceptance Criteria

* [x] A dedicated provider-neutral economic-calendar port exists and can be tested without XOOMAR network access.
* [x] XOOMAR-specific request and response types remain inside the infrastructure adapter.
* [x] The adapter calls the documented XOOMAR calendar endpoint with a bounded `from`/`to` window.
* [x] XOOMAR `scheduledAt` values are parsed as UTC instants.
* [x] XOOMAR's authoritative event identifier (`id`, falling back to `eventId` only when required by the documented response) is preserved as `sourceEventId` and remains stable across repeated synchronization.
* [x] XOOMAR `eventName`, `source`, `importance`, `previous`, `forecast`, `actual` and `unit` are normalized without provider-specific fields leaking into the domain API.
* [x] Null actual, forecast and previous values remain absent rather than being converted to zero or an invented value.
* [x] Unknown importance values map to `UNKNOWN` and do not fail the complete synchronization.
* [x] Repeating synchronization for the same provider response does not create duplicate persisted events.
* [x] HTTP failures, timeouts and HTTP 429 responses are classified as unavailable provider results and do not delete existing catalog data.
* [x] Synchronization is disabled by default or requires an explicit provider-enabled configuration.
* [x] The implementation respects the documented free request limit and does not perform unsafe automatic retries.
* [x] XOOMAR attribution and the personal-use limitation are documented in the runtime configuration or operational documentation.
* [x] Targeted unit and integration tests pass without requiring a live XOOMAR credential.
* [x] Existing news-service API contracts and financial-news behavior remain unchanged.

---

## Constraints

* Preserve existing service responsibilities and the decision pipeline.
* Keep XOOMAR payloads, HTTP details and error translation inside infrastructure adapters.
* Keep economic-event normalization deterministic.
* Do not expose XOOMAR credentials or introduce secrets that are not required by the provider.
* Do not treat XOOMAR data as a risk or execution decision.
* Do not poll more frequently than the provider's documented free limit allows.
* Do not use scraping or undocumented XOOMAR endpoints.
* Preserve idempotency and existing persistence invariants.
* Respect accepted ADRs.
* Do not commit, push, merge or rewrite history automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-008.md`
* `docs/architecture/adr/ADR-020.md`
* `docs/architecture/adr/ADR-022.md`
* `docs/architecture/adr/ADR-048.md`

---

## Relevant Modules

* `news-service`
* `market-intelligence`

---

## Validation

* Run the targeted `news-service` Maven test suite.
* Verify mapping with representative XOOMAR fixtures, including upcoming and released events.
* Verify UTC parsing, stable identity and repeated synchronization.
* Verify provider error and rate-limit handling with mocked HTTP responses.
* Verify `git diff --check`.
* Review the complete diff for provider-specific leakage and unintended API changes.
* Perform a manual opt-in request against XOOMAR only when the local environment explicitly enables it.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [ ] Implementation completed
* [ ] Relevant validation executed
* [ ] Diff reviewed in IntelliJ
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created

## Documentation Reconciliation

The XOOMAR adapter and synchronization path were introduced by commit `9de805b`
and subsequently corrected to preserve event-level `source` through the domain
and persistence layers. The implementation report, independent review and
validation record are now present. Human Git commit remains pending.
