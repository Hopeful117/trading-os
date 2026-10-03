# Story 0069: Market Facts Foundation

**Status:** Runtime fix required - human review required  
**Size:** Large  
**Risk:** High  
**Predecessor:** Story 0068 - User Market Discovery  
**Successor:** Story 0070 - Candidate Selection  
**Investigation:** `docs/architecture/reports/market-facts-foundation-investigation.md`

**Runtime validation:** `docs/architecture/stories/0069-market-facts-foundation/runtime-validation.md`

## Goal

Provide a deterministic, reusable Market Facts capability that derives bounded
market activity and data-readiness facts from normalized OHLC history without
implementing candidate selection, ranking, or trade recommendations.

The capability must give downstream consumers a stable factual basis for
market discovery while preserving the distinction between:

- what the provider actually returned;
- what Market Data normalized or synthesized for continuity; and
- whether the resulting evidence is sufficient for a specific downstream use.

## Context

Story 0068 establishes user-scoped market discovery as a deterministic,
provider-neutral workflow. Its downstream filtering requires factual answers
about recent activity and usable historical data. Those answers must not be
reimplemented independently by Candidate Selection, Trend Context, or future
consumers.

The current repository already provides the relevant acquisition and
normalization boundaries:

- `market-data` owns `OhlcEvent`, including provider, symbol, interval,
  volume, VWAP, close state, timestamps, `synthetic`, `sourceId`, and
  `fetchedAt`.
- `OhlcHistoryNormalizer` sorts and deduplicates OHLC events and can synthesize
  continuity candles.
- `MarketHistoryService` validates bounded OHLC requests and delegates to the
  broker-neutral `MarketDataProvider` port.
- `KrakenMarketData` and `KrakenRestOhlcMapper` keep Kraken transport and
  payload mapping inside the Kraken adapter.
- `market-intelligence` currently acquires OHLC through `MarketDataClient` and
  contains existing Trend Context logic that must remain separate from Market
  Facts.

The implementation now provides the bounded Market Facts capability described
by this Story. Production use remains subject to human review and deployment
configuration approval.

## Problem

Without a shared fact layer:

- activity calculations can be duplicated with inconsistent volume semantics;
- provider-specific fields can leak into selection or intelligence logic;
- synthetic candles can be mistaken for observed provider evidence;
- stale, incomplete, unavailable, and unsupported data can collapse into one
  ambiguous failure state;
- repeated discovery requests can repeat expensive provider history calls;
- later consumers can accidentally turn activity facts into ranking logic.

## Scope

### In Scope

- Define a bounded Market Facts capability inside `market-data`.
- Define a deterministic Market Activity fact for one configured activity
  window.
- Define a deterministic, request-aware Market Data Readiness fact.
- Use quote-notional activity semantics based on `baseVolume * VWAP`.
- Preserve provider-local and quote-currency-local comparison domains.
- Preserve raw provider completeness separately from normalized continuity.
- Track freshness and provenance sufficiently for deterministic reuse and
  diagnosis.
- Reuse the existing provider-neutral OHLC acquisition and normalization
  boundaries.
- Add a stable broker-neutral contract for authorized downstream consumers,
  if an inter-service boundary is required by the approved implementation.
- Define bounded acquisition, reuse, failure, and observability behavior.
- Add unit, component, contract, and cost-bound tests required by the
  acceptance criteria.

### Out of Scope

- Candidate Selection, ranking, scoring, or top-N selection.
- Spread, order-book depth, slippage, liquidity, volatility, or trend facts.
- Market Structure, Trend Context, AI analysis, opportunity generation, or
  trade-plan generation.
- Account-specific filters, risk limits, execution decisions, or broker order
  placement.
- Global currency conversion or cross-currency normalization.
- Provider-specific payloads, symbols, errors, or credentials in public
  contracts.
- Replacing or broadening the existing Trend Context implementation.
- A new Market Data microservice, separate shared library, or new service
  boundary.
- General-purpose event sourcing or an unbounded market-history repository.
- Changes to Story 0068 behavior beyond the minimal fact-consumption contract
  needed by a later Story.
- Any implementation of Story 0070.

## Approved Design Decisions

### Market Activity

Market Activity is a deterministic fact over one configured rolling window.
The activity measure is quote-notional activity:

```text
activity = sum(observed base volume * observed VWAP)
```

The implementation must:

- use completed OHLC evidence only;
- exclude synthetic candles from the observed activity numerator;
- define how missing or unusable volume/VWAP values affect completeness and
  status;
- compare only within the same provider, quote-currency, and configured
  window domain;
- retain the raw measure and its evidence metadata;
- avoid interpreting activity as liquidity, volatility, trend, or quality.

The exact default window duration is intentionally not fixed by this Story.
The implementation must present the proposed configured default and obtain
human approval before coding if repository evidence does not establish one.

### Market Data Readiness

Readiness is request-aware. It must evaluate the evidence required by a
consumer, including at least:

- requested interval and lookback;
- number of completed provider-observed candles;
- temporal coverage and gaps;
- freshness relative to an explicit observation boundary;
- whether normalization introduced synthetic continuity candles;
- provider/source availability;
- the consumer's minimum evidence requirement.

Raw completeness and normalized continuity are separate dimensions. A
normalized series must not be reported as fully provider-complete merely
because synthetic candles fill its time range.

The contract must distinguish at least these outcomes, or semantically
equivalent typed states:

- `AVAILABLE`: evidence satisfies the requested requirement;
- `INSUFFICIENT_DATA`: the source responded, but evidence does not satisfy the
  requirement;
- `STALE`: evidence exists but exceeds the permitted freshness boundary;
- `UNAVAILABLE`: acquisition failed or the source returned no usable evidence;
- `UNSUPPORTED`: the provider or capability cannot satisfy the request.

The contract must include enough structured detail for a consumer to explain
the outcome without parsing provider payloads or log messages.

### Persistence and Reuse

The implementation must use a bounded, maintained fact/read-model approach.
Repeated discovery calls must reuse facts within an explicit freshness and
configuration boundary rather than performing an unbounded provider scan per
request.

The implementation may use a compact durable read model, a bounded cache, or a
hybrid. The choice must be justified against the current repository's
persistence and caching conventions. It must not introduce a general-purpose
history store.

Facts must be invalidated or recomputed when relevant inputs change,
including provider, quote domain, activity configuration, requested
readiness requirement, or freshness boundary.

### Provider Neutrality

Provider-specific behavior remains inside `MarketDataProvider` implementations
and infrastructure adapters such as `KrakenMarketData` and
`KrakenRestOhlcMapper`. Market Facts must consume normalized domain data and
must not expose Kraken response shapes, pair syntax, error payloads, or
credentials.

## Acceptance Criteria

### AC1 - Activity Fact Semantics

- Given a market with completed observed OHLC events, the activity fact is
  calculated deterministically as quote-notional activity using `volume * VWAP`.
- The fact records its provider, market, quote domain, interval/window,
  observation boundary, source evidence, and calculation/version identity.
- Synthetic candles do not contribute observed activity.
- The implementation handles missing, zero, or unusable volume/VWAP according
  to an explicit documented rule and exposes the resulting completeness/status.
- Activity from different providers, quote currencies, windows, or incompatible
  configuration domains is not directly compared by the fact capability.
- Repeating the calculation with the same inputs produces the same result.

### AC2 - Readiness Fact Semantics

- Given a readiness request, the result evaluates requested interval, lookback,
  completed candles, temporal coverage, gaps, freshness, source availability,
  and synthetic continuity explicitly.
- Raw provider completeness is reported separately from normalized continuity.
- The result distinguishes available, insufficient, stale, unavailable, and
  unsupported outcomes with typed/status fields rather than exception-message
  inspection.
- A consumer can determine why evidence is not ready without accessing
  provider-specific payloads.
- Repeating the evaluation with the same events, boundary, and requirement
  produces the same result.

### AC3 - Bounded Acquisition

- Market Facts requests use the existing bounded OHLC acquisition path through
  `MarketHistoryService` and `MarketDataProvider` or an explicitly approved
  equivalent within `market-data`.
- A request has explicit limits for interval, lookback, provider calls, and
  returned history.
- The implementation never scans all markets or requests unbounded provider
  history to answer one fact request.
- A provider failure cannot be silently converted into an empty successful
  fact.
- An ambiguous or unavailable source result is represented as an appropriate
  non-available readiness outcome and remains diagnosable.

### AC4 - Reuse and Freshness

- Repeated equivalent requests within the configured freshness boundary reuse a
  maintained fact/read model rather than repeating equivalent provider work.
- Facts are not reused across incompatible provider, quote-domain, window,
  requirement, or freshness inputs.
- Freshness and provenance are visible in the fact contract and logs/metrics
  without logging secrets or raw provider credentials.
- The implementation defines behavior for expired, invalidated, and missing
  facts.

### AC5 - Broker-Neutral Contract

- The Market Facts contract contains domain facts and typed statuses only.
- No Kraken payload, Kraken symbol convention, provider credential, or
  provider-specific error type is exposed to downstream consumers.
- Any new internal HTTP contract follows existing versioning conventions and
  uses `ResponseEntity` at controller boundaries where applicable.
- The contract is usable by the existing `market-intelligence` adapter without
  duplicating activity or readiness calculations.

### AC6 - Explicit Non-Goals

- No ranking, score, top-N selection, liquidity conclusion, volatility
  conclusion, trend conclusion, opportunity, trade plan, risk decision, or
  execution intent is produced by Market Facts.
- Existing Trend Context remains responsible for its own trend and volatility
  semantics; Market Facts does not replace or modify it.
- Story 0068 remains behaviorally unchanged except for an explicitly reviewed
  future integration point.

### AC7 - Validation and Cost Bound

- Unit tests cover normal activity, zero activity, missing values, synthetic
  candles, mixed provider/quote domains, duplicate/conflicting evidence, and
  deterministic repeatability.
- Readiness tests cover complete, gapped, synthetic-filled, stale, empty,
  provider-failure, and unsupported cases.
- Component tests verify bounded acquisition through the provider-neutral port,
  reuse behavior, invalidation, and failure classification.
- Contract tests verify that downstream consumers receive only broker-neutral
  facts and typed statuses.
- Tests verify the maximum provider-request and history-size bounds for one
  fact request.
- `git diff --check` and the relevant independent Maven module test suites pass.

## Implementation Plan

The implementation plan is intentionally constrained to existing boundaries:

1. Add the Market Facts domain model and deterministic calculation services in
   the `market-data` service, alongside the existing model and service packages.
   Reuse `OhlcEvent` rather than creating a second OHLC representation.
2. Integrate acquisition through `MarketHistoryService` and the
   `MarketDataProvider` port. Preserve the existing Kraken adapter path through
   `KrakenMarketData`, `KrakenRestOhlcMapper`, and
   `OhlcHistoryNormalizer`.
3. Establish the raw-versus-normalized evidence boundary before calculating
   facts. The implementation must not infer provider completeness from a
   normalized list alone.
4. Add the smallest maintained reuse mechanism consistent with existing
   repository persistence and caching conventions. If durable facts are chosen,
   define ownership, keys, expiry/invalidation, and migration behavior before
   implementation. Do not create a general-purpose OHLC archive.
5. Expose only the approved broker-neutral fact contract to downstream
   consumers. Extend `market-intelligence`'s `MarketDataClient` only when the
   approved integration requires it; do not duplicate the calculations in
   `TrendContextRoleHistoryContextContributor` or other intelligence classes.
6. Add focused tests at the domain, service, adapter/contract, and cost-bound
   levels. Use fixed clocks and deterministic fixtures for time-sensitive
   behavior.
7. Document the selected default activity window, freshness boundary, and
   reuse policy in configuration and tests. If repository evidence does not
   justify defaults, stop for human approval before implementation.

## Affected Repository Areas

### Existing Components to Reuse

- `market-data/src/main/java/com/hope/trading/market_data/model/OhlcEvent.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OhlcHistoryNormalizer.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/MarketHistoryService.java`
- `market-data/src/main/java/com/hope/trading/market_data/brokerClient/MarketDataProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/KrakenMarketData.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/helper/KrakenRestOhlcMapper.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/TrendContextRoleHistoryContextContributor.java`

### Expected Change Area

- New Market Facts domain model, calculation, readiness, and reuse components
  inside `market-data`.
- Tests in the corresponding `market-data` test packages.
- A narrowly scoped `market-intelligence` adapter/contract change only if the
  approved consumer integration requires it.
- Configuration, persistence, or migration files only if the selected reuse
  approach requires them and the change remains within this Story's scope.

No file in the affected area may be overwritten wholesale. Existing behavior
and unrelated user changes must be preserved.

## Failure and Observability Requirements

- Provider errors, empty responses, unsupported requests, stale facts, and
  insufficient evidence remain distinguishable.
- Logs include request/fact identifiers, market identity, provider, interval,
  bounded request size, status, and failure category where safe.
- Logs do not include broker credentials, API keys, private data, or raw
  provider payloads unless an existing approved diagnostic convention requires
  a sanitized representation.
- Metrics should make provider-call count, reuse hit/miss, fact status, and
  bounded acquisition failures observable if the service's existing metrics
  conventions support them.

## Dependencies and Sequencing

- Story 0068 provides the user-scoped market discovery context and must remain
  the predecessor contract.
- Story 0070 may consume Market Activity and Market Data Readiness but must not
  be implemented as part of this Story.
- Existing Market Data APIs and OHLC normalization are the implementation
  foundation.
- No new provider integration is required.
- No AI capability is required.

## ADR Assessment

**ADR_REQUIRED: NO**

This Story stays within the existing Market Data bounded context and uses the
responsibility boundaries established by:

- ADR-006 - service and domain boundaries;
- ADR-010 - deterministic business authority;
- ADR-014 - Market Data responsibility and provider isolation;
- ADR-033 - Market Intelligence and decision-pipeline boundaries;
- ADR-048 - Market Data freshness/provenance and artifact handling.

An ADR becomes required before implementation if the selected design introduces
any of the following:

- a new service or shared library boundary;
- a new authoritative ownership boundary for market facts;
- a durable storage strategy that materially changes the approved Market Data
  persistence architecture;
- a cross-currency normalization authority;
- a change to the responsibility of Risk, Market Intelligence, Trading Core,
  or Broker Service.

## Definition of Done

- Human approval has been obtained for this Story and any unresolved default
  configuration decisions.
- Market Activity and Market Data Readiness are implemented as deterministic,
  broker-neutral Market Data capabilities.
- Raw completeness and normalized continuity are both represented.
- Acquisition and reuse are bounded and observable.
- Acceptance criteria and relevant module tests pass.
- No candidate ranking, AI recommendation, risk, or execution behavior is
  included.
- The implementation report identifies changed files, validation commands,
  results, unresolved risks, and any deviation from this Story.

## Current Implementation State

- H1-H7 findings from the independent audit are remediated.
- The default Market Facts service caller remains `trading-core`.
- `market-intelligence` access requires explicit deployment configuration and
  its trusted service JWT secret; it is not enabled by the default profile.
- Runtime Kraken sandbox and deployed end-to-end validation remain pending.
- Human review, acceptance, and Git commit remain pending.
