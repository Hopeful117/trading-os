# Story 0107C-0 - Repository Analysis

## Analysis Status

Repository analysis is complete for the current repository state. During plan
preparation, the temporal wording was clarified: the explicit candle eligibility
predicate is normative and includes `openTime = fromExclusive`. Implementation
planning may proceed after the normal analysis approval gate is renewed for this
clarification.

## Repository State

- Branch: `main`.
- `HEAD` and `origin/main`: `1e348764284c88be2cc3da6b10d8e46292427ca4`.
- Worktree was clean before this analysis, except for the approved Story artifact
  created in this workflow.
- `git fetch origin` succeeded.
- DevLog context was unavailable because the connected provider reported exhausted
  quota. Repository evidence and accepted ADRs are therefore the source of truth.
- The Obsidian vault was not consulted; current repository contracts and ADRs are
  sufficient for this bounded Market Data change.

## Relevant Current Implementation

Market Data already contains the durable OHLC evidence foundation from Story
0107A and the strict replay consumer from Story 0107B:

- `HistoricalOhlcEvidence` is immutable provider-neutral evidence with
  `marketId`, interval, open/close times, OHLCV values, closure, synthetic flag,
  provenance timestamps, origin, normalization version, and observation
  fingerprint.
- `HistoricalOhlcEvidenceOrigin` distinguishes `CAPTURED` and
  `HISTORICAL_BACKFILL`.
- `HistoricalOhlcEvidencePolicy` currently exposes only `STRICT_OBSERVED` for
  the existing as-of contract.
- `OhlcInterval` is the canonical source of interval duration.
- `HistoricalOhlcEvidenceFingerprint` provides SHA-256 observation fingerprints
  and the `historical-ohlc-v1` normalization version.
- `JpaHistoricalOhlcEvidenceRepository` validates bounded as-of reads and maps
  selected rows into the existing `HistoricalOhlcSeries` domain result.
- `SpringDataHistoricalOhlcEvidenceRepository` selects one row per logical
  candle using:

  ```text
  marketId + interval + openTime
  ORDER BY knownAt DESC, capturedAt DESC, observationId DESC
  ```

- `InternalHistoricalOhlcController` exposes the existing internal endpoint:
  `GET /internal/v1/historical-ohlc/{marketId}`.
- `V5__historical_ohlc_evidence.sql` persists append-only evidence with indexes
  on market/interval/open time and market/interval/known/close time.
- Existing tests cover persistence, correction selection, captured versus
  backfilled evidence, market/interval isolation, idempotent fingerprints,
  service delegation, and controller wiring.

## Architectural Constraints

ADR-057 requires:

- Market Data ownership of durable historical OHLC evidence;
- logical candle identity `marketId + interval + openTime`;
- immutable correction observations;
- explicit `knownAt`, capture, closure, synthetic, and origin semantics;
- captured evidence distinguished from historical backfill;
- repository-first strict reads with no silent provider fallback;
- gaps remaining gaps rather than synthetic continuity candles.

ADR-058 requires strict historical replay to consume only captured evidence and
keeps provider acquisition outside the strict replay path.

ADR-048 and ADR-056 require source ownership, deterministic reproducibility,
explicit unavailable/incomplete states, and no promotion of Market Data facts
into strategy, risk, execution, ML, or outcome authority.

## Intended Implementation Boundary

Subject to resolving the temporal contradiction, the smallest coherent change is
limited to `market-data`:

- a bounded range domain result and gap representation;
- a CAPTURED-only range repository query reusing existing correction selection;
- deterministic coverage and gap calculation using `OhlcInterval.duration`;
- deterministic aggregate fingerprinting;
- service and internal controller wiring;
- focused unit, controller, persistence/integration, and repeatability tests.

No Market Intelligence, outcome, gateway, frontend, broker, risk, or execution
change is required by the current Story scope.

## Resolved Temporal Contract Discrepancy

The Story originally defined both:

```text
requested range: (fromExclusive, toInclusive]
eligibility: openTime >= fromExclusive and closeTime <= toInclusive
```

and acceptance criterion AC1:

```text
fromExclusive = 10:00
toInclusive = 11:00
15-minute candles: 10:00-10:15, 10:15-10:30,
                   10:30-10:45, 10:45-11:00
expectedObservationCount = 4
```

Under the explicit eligibility rule, the `10:00-10:15` candle has
`openTime = 10:00` and is excluded by the exclusive lower boundary. The range
therefore contains three eligible candles, not four.

The same ambiguity appears in the Golden Fixture, which starts at 10:00 but does
not specify a `fromExclusive` value that would include the first candle.

The earlier analysis incorrectly stated that equality would exclude the
`10:00-10:15` candle. The explicit predicate uses `openTime >= fromExclusive`,
so `fromExclusive = 10:00` includes that candle and the AC1 fixture correctly
expects four observations. The implementation must treat the explicit predicate
as normative and document this equality behavior in the API contract.

This clarification changes the approved Story artifact materially. The analysis
approval must therefore be renewed before implementation planning.

## Risks and Open Questions

- The existing correction ordering is deterministic and sufficient for the
  current repository model, but it does not provide a separate correction-lineage
  identifier. A new lineage model is outside this Story unless required by
  acceptance tests.
- The repository test profile uses H2 in PostgreSQL mode, while the Story calls
  for PostgreSQL integration. The existing persistence test convention should be
  reused; true PostgreSQL runtime validation may remain environment-dependent.
- The Story requests `CAPTURED` as an evidence policy while the current enum
  names the strict as-of policy `STRICT_OBSERVED`. The implementation plan must
  decide whether to extend the enum additively or introduce a range-specific
  policy type without exposing an incompatible public contract.
- The Story requires explicit `UNAVAILABLE` and no-complete-interval semantics,
  while the existing as-of series only has `AVAILABLE` and `INCOMPLETE`. This is
  an additive range-result contract and must not alter existing as-of behavior.

## Validation Baseline

The affected Maven module is `market-data`, using Java 21, Spring Boot 4.1.0,
Flyway, JPA, H2 tests, and PostgreSQL runtime support. The module enforces an
80% JaCoCo bundle line-coverage threshold during `verify`.

After the temporal contract is clarified and the implementation plan approved,
validation should include targeted Market Data tests, `mvn -q test`,
`mvn -q verify`, `git diff --check`, and controlled PostgreSQL/runtime checks
when prerequisites are available.

## Clarification Outcome

The previous option-A recommendation was withdrawn after detecting that it was
inconsistent with the Story's explicit `openTime >= fromExclusive` predicate.
The current artifact preserves `fromExclusive = 10:00` for AC1 and explicitly
records equality-at-the-lower-bound as included. Human approval of this revised
analysis and Story wording is required before the implementation plan.
