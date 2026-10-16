# Story 0107C - Implementation Plan

## Status

Draft - requires human approval before implementation.

## 1. Implementation Goal

Implement the objective future market outcome boundary in Market Intelligence
without changing the historical T0 context, Market Data evidence ownership, or
any Strategy, Risk, Broker, Execution, ML, Gateway, or frontend behavior.

The implementation will connect:

```text
strict captured reference evidence <= T0
        +
bounded captured future range (T0, T0 + H]
        -> immutable ObservedMarketOutcome
```

## 2. Approved Analysis Constraints

- Market Data 0107C-0 is the sole owner of future evidence eligibility,
  correction selection, coverage, gaps, and range fingerprinting.
- Market Intelligence owns reference-price semantics, maturity, completeness
  interpretation, objective metrics, outcome definition, and outcome
  persistence.
- Future evidence must never enter `IntelligenceContext @ T0` or its
  fingerprint.
- The calculator must be pure and use `BigDecimal`.
- No provider fallback, current Kraken call, gap repair, scheduler, batch API,
  or public route is allowed.
- The outcome must not become an ML label, trade result, P&L, or strategy
  decision.

## 3. Architecture Decision Record

Create `docs/architecture/adr/ADR-059.md` before implementing the durable
outcome boundary.

ADR-059 will establish:

- objective outcome ownership by Market Intelligence;
- strict separation between analysis evidence `<= T0` and outcome evidence
  `> T0`;
- strict captured reference and bounded captured future evidence policies;
- V1 15-minute future interval;
- maturity and completeness requirements;
- strategy-neutral decimal metric semantics;
- BigDecimal authority and rounding policy;
- `market-outcome-v1` definition version;
- deterministic outcome fingerprint material;
- immutable/versioned persistence and correction behavior;
- idempotence and optional AnalysisExecution linkage;
- explicit non-authority over Strategy, Risk, Execution, ML labels, and trades;
- no scheduler or provider fallback in V1.

The ADR must be created and reconciled with the implementation before code
review. If repository inspection during implementation reveals a contradiction
with an accepted ADR, stop and request guidance rather than weakening the
contract.

## 4. Market Data Client Extension

Extend the existing `HistoricalOhlcClient` rather than creating a duplicate
Feign boundary.

Add a method for:

```text
GET /internal/v1/historical-ohlc/{marketId}/range
```

with:

```text
interval
fromExclusive
toInclusive
```

Add provider-neutral nested DTOs for the range response and evidence fields
required by the outcome calculator and provenance. Preserve:

- market and interval identity;
- requested boundaries and evidence policy;
- range status and reason;
- expected/observed counts;
- coverage bounds;
- gaps;
- range fingerprint;
- normalization versions;
- OHLC values and open/close timestamps;
- evidence fingerprint and reference provenance fields needed by V1.

Add a dedicated mapper/source for future-range evidence. Do not reuse the strict
as-of validation unchanged because future observations may have `knownAt > T0`.
The future source must still validate market, interval, captured origin, closed
state, non-synthetic state, complete OHLC values, and response identity.

Reuse the existing `MarketDataFeignConfiguration`, Eureka resolution, service JWT
header, and `ROLE_SERVICE` path. No credentials or security bypass changes are
planned.

Expected files:

- `market-intelligence/.../adapter/marketdata/HistoricalOhlcClient.java`
- new range response/evidence DTOs under `adapter/marketdata`;
- new future evidence source/mapper under `adapter/marketdata`;
- client wire-contract and source validation tests.

## 5. Outcome Domain Model

Add a focused `domain.outcome` package containing immutable types.

### `OutcomeHorizon`

- Backed by `Duration`.
- Rejects null, zero, negative, and values beyond the explicit V1 maximum.
- Provides named supported horizons for `1h`, `4h`, and `24h` without making
  future strategy configuration responsible for interval selection.
- Computes `maturityAt` from the evaluation cutoff.

### `ObservedMarketOutcomeStatus`

Use the explicit statuses:

```text
PENDING
AVAILABLE
INCOMPLETE
UNAVAILABLE
```

### `ObservedMarketOutcome`

The immutable record will retain only the required objective and provenance
fields:

- market ID;
- evaluation cutoff, horizon, and maturity;
- status and reason;
- nullable reference/final/high/low prices and returns;
- expected and observed counts;
- reference evidence fingerprint;
- future range evidence fingerprint;
- outcome definition version;
- deterministic outcome fingerprint;
- operational `computedAt`.

It must reject invalid combinations such as `AVAILABLE` without complete metrics
or final metrics on `PENDING`/`INCOMPLETE`/`UNAVAILABLE` results.

No direction, strategy ID, trade ID, P&L, risk, execution, ML label, or
probability field is permitted.

## 6. Pure Outcome Calculator

Add a stateless `MarketOutcomeCalculator` under the outcome domain/application
calculation boundary.

Inputs:

- reference evidence/price;
- ordered complete future observations;
- evaluation cutoff and horizon;
- expected/observed counts and future fingerprint;
- outcome definition version.

Behavior:

- require a complete future range before calculating final metrics;
- select final price from the chronologically final future candle close;
- calculate future high and low from all selected candles;
- calculate signed decimal fractional returns using `BigDecimal`;
- use explicit scale and rounding from ADR-059;
- calculate deterministic semantic fingerprint material;
- never read `Clock`;
- never call HTTP, repositories, persistence, Risk, Execution, Broker, News,
  ML, or LLM services.

The golden fixture must produce exactly:

```text
finalPrice = 102
futureHigh = 105
futureLow = 98
forwardReturn = 0.02
futureHighReturn = 0.05
futureLowReturn = -0.02
futureRange = 0.07
```

## 7. Application Evaluation Service

Add a focused `MarketOutcomeEvaluationService` responsible for orchestration.

The service will:

1. Validate market ID, cutoff, horizon, and request bounds.
2. Calculate `maturityAt`.
3. Read `Clock` only for the maturity decision and `computedAt`.
4. Return `PENDING` before maturity without final numeric metrics.
5. Retrieve the strict as-of reference evidence at or before T0.
6. Return `UNAVAILABLE` if reference evidence is missing.
7. Request the bounded `CAPTURED` 15-minute future range `(T0, T0 + H]`.
8. Return explicit `UNAVAILABLE` or `INCOMPLETE` without final metrics when
   future evidence is missing or coverage is incomplete.
9. Invoke the pure calculator for a complete matured range.
10. Calculate the outcome fingerprint from stable semantic material.
11. Persist the finalized result through an idempotent repository port.
12. Return the immutable outcome.

The service must not mutate an existing outcome. The service may accept an
optional `AnalysisExecution` reference, but evaluation must remain valid without
one.

## 8. Persistence Design

Add a new Flyway migration after the current V13, using the next repository
version after confirming no concurrent migration exists.

Add a focused immutable outcome table with PostgreSQL `TIMESTAMP WITH TIME ZONE`
for instants and `NUMERIC(38, 18)`-compatible precision for authoritative
financial values. The final schema must be defined by ADR-059 and should retain
equivalents of:

```text
outcome_id
market_id
horizon_seconds
maturity_at
status
reason
reference_price
reference_observation_fingerprint
final_price
future_high
future_low
forward_return
future_high_return
future_low_return
future_range
expected_observation_count
observed_observation_count
future_evidence_fingerprint
outcome_definition_version
outcome_fingerprint
computed_at
analysis_execution_id nullable
```

Use a unique constraint that prevents duplicate finalized artifacts for the same
semantic identity/fingerprint. Keep the outcome fingerprint independently
unique if compatible with the approved ADR and existing schema conventions.

Add:

- immutable JPA entity;
- Spring Data repository with bounded reads by market/cutoff/horizon and
  fingerprint;
- application repository port;
- mapper preserving exact decimal values and provenance;
- race-safe save/read behavior equivalent to the StrategyMatch and capability
  artifact patterns.

The persistence adapter must return an existing identical artifact, reject an
identity conflict with different payload, and handle a concurrent unique-key
race by reloading the authoritative row.

Persist matured `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE` outcomes when the
ADR confirms the preferred V1 policy. Do not persist `PENDING` as finalized
research truth.

## 9. AnalysisExecution Linkage

Keep `AnalysisExecution` out of the fundamental outcome identity.

Use a nullable reference from the outcome artifact or a separate linkage only if
the approved ADR demonstrates that multiple execution references are required.
Do not make identical market/T0/horizon/evidence outcomes duplicate per analysis.

No automatic outcome evaluation is added to the existing analysis dispatcher or
capability planner in this Story.

## 10. Controlled Internal Invocation

Implement the smallest controlled invocation boundary selected by the ADR and
repository review.

Preferred V1 option:

```text
POST /internal/v1/market-outcomes/evaluate
```

with `marketId`, `evaluationCutoff`, and `horizon`, returning the provider-neutral
outcome through `ResponseEntity`.

The endpoint must:

- remain under `/internal/**`;
- use the existing service authentication configuration;
- not be exposed through Gateway or frontend;
- not schedule work or call providers;
- map invalid requests to the existing bounded error convention.

If implementation confirms a direct application invocation is sufficient and an
endpoint would add no controlled caller, document that decision instead of
adding unnecessary HTTP surface.

## 11. Test Plan

### Pure domain tests

- horizon null/zero/negative/maximum validation;
- maturity before, at, and after cutoff plus horizon;
- golden fixture exact BigDecimal metrics;
- signed high/low returns and normalized range;
- no zero substitution for missing metrics;
- calculator has no clock or I/O dependency;
- outcome status/model invariants.

### Client and adapter tests

- exact range request boundaries and `CAPTURED` policy;
- provider-neutral wire serialization/deserialization;
- market/interval/status identity validation;
- incomplete/unavailable response mapping;
- no current-provider fallback;
- future evidence permits `knownAt > T0` while preserving capture metadata;
- non-aligned T0 and first complete candle semantics.

### Application tests

- `PENDING` before maturity;
- exact maturity evaluates with `now >= maturityAt`;
- missing reference returns `UNAVAILABLE`;
- future unavailable returns `UNAVAILABLE` without metrics;
- future incomplete returns `INCOMPLETE` without metrics;
- complete range delegates to calculator and persists the result;
- Market Data client is not called before maturity;
- no future evidence enters `IntelligenceContext` or its fingerprint;
- market and horizon isolation.

### Persistence tests

- save/reload preserves exact metrics and fingerprints;
- fingerprint lookup;
- market/cutoff/horizon bounded lookup;
- duplicate identical evaluation is idempotent;
- concurrent duplicate insert resolves through the unique constraint;
- changed correction creates a new row and preserves the old row;
- restart/reconstruction preserves provenance and values.

### API/security tests

- authenticated internal request succeeds;
- unauthenticated and wrong-service requests are rejected;
- request validation and response mapping;
- no Gateway/public route is introduced.

## 12. Runtime Validation

When local PostgreSQL, Market Data, and Market Intelligence are available:

1. Seed/use persisted reference evidence and a complete 1-hour future window.
2. Evaluate and verify the golden metrics and `AVAILABLE` status.
3. Omit one future candle and verify `INCOMPLETE` with no final metrics.
4. Repeat the complete evaluation and verify one artifact and the same
   fingerprint.
5. Restart Market Intelligence and verify the same persisted/recomputed result.
6. Confirm no external provider request is required.

If prerequisites are unavailable, report the exact limitation and do not claim
runtime success.

## 13. Validation Commands

Run targeted Market Intelligence tests first, then:

```text
mvn -q test
mvn -q verify
```

Run authenticated SonarQube analysis for `trading-os:market-intelligence` when
available. Report Quality Gate, new coverage, violations, security hotspots,
bugs, vulnerabilities, and duplicated lines.

## 14. Documentation Reconciliation

Update or create only the canonical artifacts required by the implementation:

- ADR-059;
- Story implementation report;
- any repository API/operation documentation directly changed by the internal
  endpoint or migration.

Record explicitly that future evidence remains separate from T0 analysis and
that the outcome is not an ML label or trade result.

The implementation report must include the full validation matrix, runtime
results, known limitations, and the final file list.

## 15. Expected File Groups

Expected files, subject to exact repository naming:

- `docs/architecture/adr/ADR-059.md`;
- Story implementation report;
- `adapter/marketdata/HistoricalOhlcClient.java` and range DTO/source files;
- `domain/outcome/*` immutable types and calculator;
- `application/outcome/*` service and request/result boundary;
- `application/port/*` outcome repository port;
- `adapter/persistence/*Outcome*` entity, repository, mapper, adapter;
- `adapter/web/*Outcome*` endpoint/request/response if selected;
- next Market Intelligence Flyway migration;
- focused unit, integration, persistence, security, and architecture tests.

No Market Data source files should change unless implementation uncovers a
contract defect in 0107C-0; such a defect must be reported before expanding
scope.

## 16. Stop Conditions

Stop and request human guidance if:

- the range contract cannot provide complete future evidence without fallback;
- strict reference evidence cannot be obtained at or before T0;
- persistence requires destructive overwrite;
- idempotence cannot be enforced with a durable unique identity;
- future evidence would enter the T0 context;
- outcome calculations require strategy direction or trade state;
- the required ADR materially conflicts with an accepted ADR;
- service authentication would need to be weakened or secrets exposed;
- a Market Data change is required beyond the approved 0107C-0 contract.

## 17. Implementation Sequence

1. Create and reconcile ADR-059.
2. Extend the provider-neutral range client and tests.
3. Implement horizon, status, outcome model, and pure calculator with golden
   tests.
4. Implement application service and maturity/completeness behavior.
5. Implement outcome persistence, migration, idempotence, and restart tests.
6. Add the minimal controlled internal invocation boundary if selected.
7. Run documentation reconciliation.
8. Run targeted and broad validation plus runtime cases.
9. Produce the Implementation Report and stop for Code Review.

## Approval Boundary

This plan translates the approved Story and Repository Analysis into an
implementation sequence. It does not constitute approval to modify code,
create the ADR, create migrations, commit, push, merge, or open a pull request.
