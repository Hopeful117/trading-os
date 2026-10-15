# Story 0107A - Implementation Report

## 1. Repository State

- Branch: `main`
- `HEAD`: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
- `origin/main`: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
- `git fetch origin`: passed
- Existing untracked Story 0107 investigation artifacts were preserved.

## 2. ADR

Created `ADR-057 - Historical Market Evidence, Known-At Semantics, and Source
Retention`.

It establishes Market Data ownership, immutable/versioned evidence, explicit
`knownAt`, captured/backfill provenance, correction retention, strict repository-
first reads, no synthetic source evidence, and no silent provider fallback.

## 3. Historical Evidence Model

Added `HistoricalOhlcEvidence` with logical candle identity, observation ID,
OHLCV values, closure/synthetic flags, provider/source provenance, acquisition
timestamps, `knownAt`, origin, normalization version, and deterministic
fingerprint.

`OhlcEvent` remains the acquisition/event representation. `OhlcPoint` remains a
Market Intelligence projection and is not persisted.

## 4. Temporal Semantics

V1 persists only closed, non-synthetic observations. `knownAt` is conservative:
for captured evidence it is the later of event fetch time and durable capture
time. The strict as-of predicate requires closed evidence, `closeTime <= asOf`,
`knownAt <= asOf`, and non-synthetic evidence.

## 5. Origin and Corrections

`HistoricalOhlcEvidenceOrigin` distinguishes `CAPTURED` and
`HISTORICAL_BACKFILL`. Backfill can be represented but is not implemented as a
strict-read fallback.

Observation fingerprints exclude acquisition timestamps that alone would create
unbounded duplicates. Material source/content changes create an appended
observation version. Prior observations are not overwritten.

## 6. PostgreSQL Schema

Added Flyway migration `V5__historical_ohlc_evidence.sql` with:

- immutable observation row fields;
- market foreign key;
- unique observation fingerprint;
- OHLC, time, volume, and trade-count checks;
- market/interval/open-time index;
- market/interval/known-at/close-time index.

## 7. Write Path

`HistoricalOhlcEvidenceWriter` maps normalized events to evidence, rejects
forming and synthetic events, calculates captured `knownAt`, and delegates
append/idempotence to the persistence adapter.

## 8. Forward Capture

Added a disabled-by-default bounded scheduler and capture service. It uses
configured market IDs, defaults to 15-minute, 1-hour, and 4-hour intervals, and
limits each provider request to 200 observations. Provider calls occur before
the short persistence transaction. Empty market scope means no capture.

Configuration:

```properties
historical-ohlc.capture.enabled=false
historical-ohlc.capture.market-ids=
historical-ohlc.capture.intervals=FIFTEEN_MINUTES,ONE_HOUR,FOUR_HOURS
historical-ohlc.capture.limit=200
historical-ohlc.capture.fixed-delay=PT15M
```

## 9. As-Of Read Contract

Added the internal endpoint:

```text
GET /internal/v1/historical-ohlc/{marketId}
    ?interval=FIFTEEN_MINUTES
    &asOf=2026-10-15T10:20:00Z
    &limit=200
```

The repository query uses a window function to select one deterministic latest
eligible version per logical candle, orders the bounded result chronologically,
and never calls a provider.

## 10. Tests Added

- Evidence writer accepts closed observations and rejects forming/synthetic ones.
- Fingerprint stability across acquisition-time changes.
- H2/Flyway persistence and migration validation.
- Strict cutoff selection and correction version selection.
- Exact fingerprint idempotence.
- Market isolation and bounded limit behavior.
- Internal as-of controller contract.

The existing Market Data suite remains covered by the module test run.

## 11. Validation Results

Passed:

```text
mvn -q -DskipTests compile
mvn -q -Dtest=HistoricalOhlcEvidenceWriterTest,HistoricalOhlcEvidencePersistenceTest,InternalHistoricalOhlcControllerTest test
mvn -q test
mvn -q verify
git diff --check
```

`mvn verify` includes the configured JaCoCo check and passed after the Sonar
remediation and independent-review fixes. The final module run executed 196
tests.

## 12. SonarQube Remediation

The open `trading-os:market-data` issue list was reviewed and remediation was
implemented for the reported complexity, nested-conditional, duplicated-literal,
empty-block, synchronization, constructor, test-style, import, and package-name
issues. The `brokerClient` packages were renamed to the Java-conventional
`brokerclient` spelling across production and test sources.

The authenticated SonarQube scan was rerun using the repository `.env` without
printing or persisting its secrets. The Market Data Quality Gate passed:

- new violations: `0`;
- new-code coverage: `80.0%`;
- new-code duplication: `0.0%`;
- open issues: `0`.

## 13. Independent Review Remediation

The independent review findings were corrected:

- strict observed reads now require `origin = CAPTURED`;
- duplicate evidence insertion is atomic in PostgreSQL and preserves the first
  observation under the H2 test path;
- captured evidence enforces `fetchedAt <= capturedAt <= knownAt`;
- provider symbols are carried through `OhlcEvent` and persisted;
- persistence, provenance, backfill-exclusion, and temporal-boundary tests were
  added or strengthened.

## 14. Runtime Validation

No live Kraken or Compose runtime validation was performed. Capture is disabled
by default and no provider credentials or runtime environment were available or
required for the deterministic test validation. PostgreSQL production behavior
was not exercised; the repository convention uses H2/Flyway integration tests.

## 15. Explicit Non-Goals

Not implemented:

- historical Market Intelligence replay;
- IntelligenceContext reconstruction;
- outcomes or labels;
- ML, LLM, feature vectors, or datasets;
- Risk, TradePlan, execution simulation, fees, slippage, or funding;
- order-book or trade-tape history;
- general provider backfill/synchronization platform;
- frontend changes.

## 16. Files Created

- `docs/architecture/adr/ADR-057.md`
- `docs/architecture/stories/0107A-durable-historical-ohlc-evidence-foundation/story.md`
- `docs/architecture/stories/0107A-durable-historical-ohlc-evidence-foundation/implementation-report.md`
- Market Data historical evidence model, repository, writer, query service,
  controller, capture configuration, scheduler, and migration files.
- Focused unit, controller, and persistence tests.

## 17. Files Modified

- `market-data/src/main/resources/application.properties`

Existing Story 0107 investigation artifacts were not modified.

## 18. Story 0107B Readiness

Story 0107B can consume the new Market Data internal evidence boundary after
human review. It must still define its own historical context assembly and
must not use the current public provider-backed OHLC endpoint for strict replay.

## 19. Remaining Limitations

- Backfill is representable but deferred.
- Forward capture is disabled until market IDs are configured.
- No production PostgreSQL runtime test was executed.
- SonarQube validation depends on the local authenticated `.env` environment.

## 20. Git Status

No commit, push, merge, pull request, reset, stash, clean, revert, or discard
was performed. The implementation remains visible for human review.
