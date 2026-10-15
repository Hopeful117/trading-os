# Story 0107C-0 - Implementation Plan

## Plan Status

Implementation is not started. This plan requires explicit human approval before
production code or tests are modified.

## Implementation Boundary

Extend only the `market-data` module. Reuse the existing immutable historical
OHLC evidence model, correction selection policy, repository adapter, SHA-256
fingerprinting style, internal security boundary, and Flyway/JPA conventions.

Do not modify Market Intelligence, outcomes, strategy, risk, execution, broker,
gateway, frontend, or provider acquisition behavior.

## Domain Contract

Add provider-neutral range types under the existing Market Data model package:

- `HistoricalOhlcRange` containing market, interval, request boundaries, evidence
  policy, observations, status, reason, expected/observed counts, coverage
  bounds, gaps, aggregate fingerprint, and normalization versions.
- `HistoricalOhlcGap` containing an expected open and close time.
- A range-specific status with `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE`.

Keep the existing `HistoricalOhlcSeries` and its as-of semantics unchanged.

Add `CAPTURED` to the existing evidence-policy vocabulary while retaining
`STRICT_OBSERVED` for the current as-of query. The range service accepts only
`CAPTURED` and rejects unsupported policies.

The explicit candle eligibility rule is normative:

```text
openTime >= fromExclusive
closeTime <= toInclusive
closed = true
synthetic = false
origin = CAPTURED
```

Equality at `openTime = fromExclusive` is included, as required by the approved
AC1 fixture. The API documentation and tests will make this behavior explicit.

## Bounded Range Calculation

Add a small pure calculator or service-local helper that:

- rejects null boundaries, `toInclusive <= fromExclusive`, unsupported policy,
  and ranges exceeding the approved maximum;
- uses `OhlcInterval.duration` as the only interval-duration source;
- derives expected candle opens on the canonical interval grid using epoch-based
  interval alignment;
- includes the first expected open at or after `fromExclusive`;
- includes only expected candles whose close is at or before `toInclusive`;
- computes expected and observed counts;
- derives missing expected intervals as explicit `HistoricalOhlcGap` values;
- derives coverage start from the first selected observation open and coverage
  end from the last selected observation close;
- returns deterministic chronological ordering.

Use an approved maximum of 720 expected candles, matching the existing bounded
historical OHLC limit. Reject oversized requests before querying persistence;
never truncate them.

For an interval with no complete expected candle, return `UNAVAILABLE` with an
explicit no-complete-interval reason. For a valid range with expected candles
but no selected evidence, return `UNAVAILABLE`. For partial evidence, return
`INCOMPLETE`; for complete evidence, return `AVAILABLE`.

## Persistence

Extend `SpringDataHistoricalOhlcEvidenceRepository` with a bounded native query
that:

- filters market, interval, open/close boundaries, closed, synthetic, and
  `CAPTURED` origin;
- selects one row per `marketId + interval + openTime` using the existing
  deterministic ordering `knownAt DESC, capturedAt DESC, observationId DESC`;
- orders selected rows by `openTime ASC` and a deterministic tie-breaker;
- does not call a provider, write data, or alter append-only evidence.

Extend `JpaHistoricalOhlcEvidenceRepository` with `findRange(...)` that performs
request validation, invokes the bounded query, maps entities to domain evidence,
and delegates coverage/gap/fingerprint construction to deterministic domain
logic.

No database migration is expected because the existing evidence schema and
indexes contain all required fields. If query-plan inspection proves an index
extension necessary, add only a focused migration and document the reason.

## Fingerprinting

Add a deterministic aggregate fingerprint helper following the existing SHA-256
implementation style. Canonical material will include:

- contract/version identifier;
- market ID, interval, boundaries, and evidence policy;
- expected and observed counts;
- ordered selected observation fingerprints;
- ordered gap open/close times;
- correction-selection policy/version;
- sorted normalization-version values.

Use unambiguous field delimiters and deterministic sorting. The fingerprint must
be stable across repeated reads and database row order, must change when selected
evidence or gaps change, and must ignore evidence outside the requested range.

## Service and HTTP Contract

Add `findCapturedRange(...)` to `HistoricalOhlcHistoryService` and expose:

```text
GET /internal/v1/historical-ohlc/{marketId}/range
    ?interval=FIFTEEN_MINUTES
    &fromExclusive=<instant>
    &toInclusive=<instant>
    &evidencePolicy=CAPTURED
```

Return `ResponseEntity<HistoricalOhlcRange>` and preserve the existing
`ResponseStatusException(HttpStatus.BAD_REQUEST, ...)` convention for invalid
requests. Keep the endpoint under `/internal/**`; do not add Gateway or public
routes.

## Tests

Add or extend tests in the existing packages:

- Domain/service tests for exact aligned ranges, non-aligned starts, end
  boundaries, expected counts, gaps, empty ranges, invalid ranges, bounds, and
  status/reason semantics.
- Fingerprint tests for repeatability, order independence, evidence sensitivity,
  gap sensitivity, and outside-range isolation.
- Controller tests for request binding, successful range responses, validation
  errors, and service delegation.
- Persistence integration tests for captured-only selection, closed/synthetic
  filtering, backfill exclusion, correction selection, market/interval isolation,
  chronological ordering, and range boundaries.
- Repeatability assertions across repeated reads and, where the test setup
  supports it, application restart behavior.

Use the existing H2/PostgreSQL-mode test profile and persistence fixtures. Do not
add provider mocks to the range repository path; absence/incompleteness must be
proven repository-only.

## Documentation Reconciliation

Update the Story artifact only if implementation details require a correction to
the approved contract. Otherwise record in the Implementation Report that no
additional canonical documentation update is required because the Story already
defines the new internal contract and ADR-057/058 cover the architecture.

No new ADR is planned. Create one only if implementation exposes a durable
Market Data architectural decision not covered by the Story and ADR-057/058.

## Validation Plan

Run targeted Market Data tests first, then:

```text
mvn -q test
mvn -q verify
```

Inspect the full diff, verify no unrelated files changed, and perform controlled
runtime/PostgreSQL validation only when the required local services are
available. Report unavailable runtime prerequisites explicitly.

## Explicit Non-Goals

- No objective outcome model or future return calculations.
- No provider backfill, provider fallback, scheduler, or synchronization job.
- No Market Intelligence client change in this Story.
- No strategy, ML, LLM, risk, execution, broker, gateway, frontend, or public API
  change.
- No destructive update or deduplication of historical evidence.
