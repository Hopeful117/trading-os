# Story 0107C-0 - Bounded Future OHLC Range Foundation

## Status

Draft - requires human approval before implementation.

## Objective

Extend the durable Market Data historical OHLC foundation so internal consumers
can query which eligible captured observations exist inside a precise bounded
historical window and how complete that window is.

The Story establishes the factual prerequisite for Story 0107C:

```text
HistoricalOhlcEvidence
        -> bounded range selection
        -> deterministic correction/version selection
        -> coverage analysis
        -> gap detection
        -> HistoricalOhlcRange
```

The result remains a Market Data fact contract. It must not calculate market
performance or outcome semantics.

## Context

Story 0107A added immutable provider-neutral historical OHLC evidence and a
repository-first strict as-of query. Story 0107B consumes that evidence to
reconstruct historical Market Intelligence context at an analysis cutoff.

The current as-of query answers:

```text
latest N captured candles with closeTime <= asOf and knownAt <= asOf
```

It cannot prove coverage of a future evaluation window, identify missing
intervals, or distinguish a complete range from a range containing only one
observation.

## Scope

- Add a bounded internal provider-neutral historical OHLC range query.
- Use one explicit V1 evidence policy selecting `CAPTURED` evidence only.
- Select at most one deterministic observation per logical candle:
  `marketId + interval + openTime`.
- Define full-candle eligibility using:
  `openTime >= fromExclusive` and `closeTime <= toInclusive`.
- Include a candle whose close equals `toInclusive`.
- Exclude candles overlapping the start boundary or exceeding the end boundary.
- Calculate expected and observed complete-candle counts.
- Detect and return explicit gaps without synthetic repair.
- Expose actual coverage start and end when observations exist.
- Return explicit `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE` semantics.
- Reject invalid and unbounded requests according to repository conventions.
- Return observations in deterministic chronological order.
- Return a deterministic aggregate evidence fingerprint.
- Represent selected normalization versions honestly when multiple versions are
  present.
- Preserve append-only evidence and reuse the existing historical evidence
  model, repository, fingerprinting, security, and Flyway conventions.
- Add unit, repository/integration, controller, and repeatability tests.
- Document the contract and its limitations for Story 0107C.

## Temporal Contract

The requested range is:

```text
(fromExclusive, toInclusive]
```

A stored candle is eligible only when its complete interval is inside the
requested range:

```text
openTime >= fromExclusive
closeTime <= toInclusive
```

The query must not use `knownAt <= fromExclusive` or `knownAt <= toInclusive`
as an analysis-side cutoff. Future captured evidence is naturally known after
the analysis instant; `knownAt` remains provenance and captured evidence must
still satisfy the V1 source policy.

## Evidence Policy

V1 implements exactly one range evidence policy:

```text
CAPTURED
```

The selected evidence must satisfy:

```text
origin = CAPTURED
closed = true
synthetic = false
marketId matches the request
interval matches the request
```

Backfill, reconstructed truth, provider-latest truth, and other policies are
not implemented in this Story.

The query is repository-first and must never call a provider, trigger a
backfill, fabricate a candle, or silently repair an incomplete range.

## Range Contract

The internal endpoint should follow existing service API conventions. The
conceptual contract is:

```text
GET /internal/v1/historical-ohlc/{marketId}/range
    ?interval=FIFTEEN_MINUTES
    &fromExclusive=<instant>
    &toInclusive=<instant>
    &evidencePolicy=CAPTURED
```

The response must remain provider-neutral and must not expose provider DTOs,
provider URLs, pagination details, JPA entities, or database implementation
details.

The response should contain the equivalent of:

```text
marketId
interval
requestedFromExclusive
requestedToInclusive
observations
status
reason
expectedObservationCount
observedObservationCount
coverageStart
coverageEnd
evidenceFingerprint
normalizationVersion(s)
```

Exact type names may follow existing repository conventions.

## Coverage and Gaps

Expected observation count must use the canonical `OhlcInterval` duration and
boundary semantics. It must not duplicate interval arithmetic inconsistently.

For a fixed interval:

```text
expected = number of complete candle intervals inside the requested range
observed = number of selected evidence observations
```

Each missing complete interval must be represented explicitly enough for a
consumer to identify its expected open and close times.

No gap may be filled with:

- a synthetic candle;
- a previous close;
- zero volume;
- interpolation;
- a provider request.

Status semantics:

- `AVAILABLE`: every expected complete candle exists and is valid.
- `INCOMPLETE`: at least one eligible observation exists, but expected coverage
  is incomplete or contains an unacceptable gap.
- `UNAVAILABLE`: no eligible observation exists for the requested range.

An empty range with no complete interval must have explicit semantics and must
not be confused with a complete market range. Invalid ranges where
`toInclusive <= fromExclusive` must be rejected.

## Correction Selection

Historical evidence remains append-only. Multiple observations for one logical
candle must be resolved deterministically using the existing repository
selection policy or the smallest explicit provider-neutral extension required
by the current model.

Selection must not depend on insertion order, physical database order, or
destructive overwrite. The selected observation and its fingerprint must be
reproducible after restart.

This Story must document whether the existing ordering by eligible version
metadata is sufficient. It must not introduce a generic event-sourcing
framework. A large correction-lineage redesign is outside scope and is a
blocking architectural finding rather than an implementation workaround.

## Boundedness

The range query must reject requests that exceed the configured or repository
approved maximum size. It must not silently truncate a requested range or load
unbounded market history into Java.

The implementation must support at least the intended V1 use cases:

- 15-minute interval over 1 hour;
- 15-minute interval over 4 hours;
- 15-minute interval over 24 hours.

## Fingerprinting and Ordering

Observations must be returned in deterministic `openTime ASC` order, with an
explicit tie-breaker if required.

The aggregate evidence fingerprint must include the request boundaries, market,
interval, evidence policy, selected observation fingerprints, gap representation,
selection policy/version, and relevant normalization version information.

It must be:

- stable for identical request and selected evidence;
- independent of database row order;
- changed when selected evidence or gaps change;
- unchanged when irrelevant evidence outside the range changes.

## Ownership and Boundaries

Market Data owns:

- historical OHLC facts;
- evidence eligibility;
- correction selection;
- bounded range reads;
- coverage and gaps.

Market Intelligence owns later:

- reference-price semantics;
- maturity;
- forward returns;
- future extrema returns;
- objective outcomes;
- labels.

This Story must not introduce outcome, strategy, trade, risk, execution, ML, or
frontend semantics into Market Data.

## Non-Goals

- `ObservedMarketOutcome`.
- `OutcomeEvaluationService` or `OutcomeCalculator`.
- Reference price or final price decisions.
- `forwardReturn`, `futureHighReturn`, `futureLowReturn`, or `futureRange`.
- Outcome maturity, persistence, or versioning.
- ML labels, `FeatureProjection`, `FeatureVector`, datasets, or `LabelPolicy`.
- Strategy backtesting or trade simulation.
- P&L, fees, slippage, funding, leverage, Risk, TradePlan, execution, or Broker
  changes.
- News, economic events, quote replay, order-book replay, or trade-tape replay.
- Automatic provider backfill, a general scheduler, or a batch research API.
- Gateway, frontend, or public API changes.
- Kafka, Redis, ClickHouse, TimescaleDB, Spark, data lakes, or a new Research
  microservice.
- Destructive mutation of historical evidence.

## Acceptance Criteria

### AC1 - Exact Aligned Range

For a 15-minute range from `10:00` exclusive to `11:00` inclusive containing
the four complete candles opening at `10:00`, `10:15`, `10:30`, and `10:45`,
the response returns. Under the explicit eligibility predicate, equality at
`openTime = fromExclusive` is included; the predicate is the normative rule
for candle selection.

```text
expectedObservationCount = 4
observedObservationCount = 4
gaps = empty
status = AVAILABLE
```

### AC2 - Non-Aligned Start

For `fromExclusive = 10:07`, the `10:00-10:15` candle is excluded and the
`10:15-10:30` candle is the first eligible candle.

### AC3 - Exact End Boundary

A candle ending exactly at `toInclusive` is included. A candle ending after
`toInclusive` is excluded.

### AC4 - Explicit Gaps

When one expected candle is absent, the response reports the missing interval,
the expected count, the observed count, and `INCOMPLETE` status.

### AC5 - Empty and Invalid Ranges

No observations returns `UNAVAILABLE`. A range with no complete interval has
explicit `NO_COMPLETE_INTERVAL`-equivalent semantics. `toInclusive <=
fromExclusive` is rejected.

### AC6 - Evidence Eligibility

The V1 query selects only captured, closed, non-synthetic evidence and never
selects backfilled evidence.

### AC7 - Deterministic Version Selection

Multiple immutable observations for one logical candle produce one deterministic
selected observation, independent of insertion order.

### AC8 - Isolation

Market, interval, and policy filters prevent cross-market, cross-interval, or
unsupported-policy mixing.

### AC9 - Fingerprint

Repeated identical queries produce the same ordered observations and aggregate
fingerprint. Relevant evidence or gap changes modify the fingerprint; irrelevant
evidence outside the range does not.

### AC10 - Bounded Query

Requests beyond the approved maximum are rejected explicitly rather than
silently truncated or executed as unbounded scans.

### AC11 - No Provider Fallback

The repository range query remains provider-free when evidence is absent or
incomplete.

### AC12 - Provider-Neutral API

The internal response exposes domain evidence and coverage only, without Kraken
DTOs, URLs, JPA entities, or provider pagination semantics.

### AC13 - Persistence Safety

The range read does not update, delete, overwrite, or destructively collapse
historical evidence.

### AC14 - Runtime Repeatability

When runtime prerequisites are available, repeated queries and a service restart
produce the same result and fingerprint.

## Required Tests

- Exact aligned range.
- Non-aligned start.
- Exact end boundary.
- Missing middle candle.
- Missing first candle.
- Missing last candle.
- No observations.
- No complete interval.
- Closed-only selection.
- Synthetic exclusion.
- Captured versus backfill isolation.
- Market isolation.
- Interval isolation.
- Deterministic correction selection.
- Deterministic ordering.
- Invalid temporal range.
- Maximum bounded range.
- Aggregate fingerprint stability and sensitivity.
- Repository/provider non-invocation.
- PostgreSQL integration using the repository's established test approach.

## Golden Fixture

Use this reusable factual fixture without calculating outcomes:

```text
T0 = 2026-10-15T10:00:00Z
interval = FIFTEEN_MINUTES
fromExclusive = 2026-10-15T10:00:00Z
toInclusive = 2026-10-15T11:00:00Z

10:00-10:15  O=100 H=103 L=99  C=102
10:15-10:30  O=102 H=105 L=101 C=104
10:30-10:45  O=104 H=104 L=99  C=100
10:45-11:00  O=100 H=102 L=98  C=102
```

Assertions for this Story are limited to four selected observations, complete
coverage, no gaps, chronological ordering, and stable fingerprint.

## Security and Operations

- Keep the endpoint under `/internal/**`.
- Preserve existing service authentication.
- Do not expose credentials, JWTs, provider payloads, or request secrets.
- Use `ResponseEntity` and existing error handling conventions.
- Add only bounded observability for market, interval, duration, counts, gaps,
  and status.
- Avoid high-cardinality metric labels where repository conventions prohibit them.

## Relevant ADRs

- ADR-035 - Strategy Evaluation Context and Strategy-Agnostic Evaluation Pipeline.
- ADR-048 - Intelligence Evidence and Authority.
- ADR-049 - Reusable Deterministic Market Structure Capability.
- ADR-056 - Deterministic Market Intelligence Evidence and Analyzer Boundary.
- ADR-057 - Historical Market Evidence, Known-At Semantics, and Source Retention.
- ADR-058 - Historical Market Intelligence Replay and Evidence Acquisition Boundary.

No new Objective Outcome ADR is required by this Story unless implementation
reveals a durable Market Data decision not covered by ADR-057. Outcome semantics
belong to the later Story 0107C.

## Validation Expectations

Run targeted Market Data unit, controller, repository, and PostgreSQL integration
tests first. Then run the broadest applicable Market Data validation, including:

```text
mvn -q test
mvn -q verify
```

Perform controlled runtime validation only when local PostgreSQL and Market Data
prerequisites are available. Do not claim runtime validation when prerequisites
are unavailable.

## Implementation Size

Medium. The Story is limited to Market Data bounded range facts, coverage,
gaps, selection, fingerprinting, API, persistence queries, and tests. It must
not expand into the later outcome calculation Story.

## Deferred Follow-Up

Story 0107C - Objective Future Market Outcomes may consume this contract to
define reference price, maturity, future returns, objective outcomes, and
versioned outcome persistence.

## Approval Boundary

This Story artifact defines scope and acceptance criteria only. It does not
constitute human approval for implementation, architecture, commit, push, or
merge.
