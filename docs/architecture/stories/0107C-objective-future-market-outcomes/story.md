# Story 0107C - Objective Future Market Outcomes

## Status

Draft - requires human approval before implementation.

## Objective

Establish the objective evaluation side of the historical Market Intelligence
research pipeline.

The existing historical pipeline provides:

```text
captured evidence <= T0
        -> strict historical replay
        -> IntelligenceContext @ T0
        -> deterministic analyses @ T0
```

Story 0107C-0 provides:

```text
captured OHLC evidence in a bounded future window
        -> coverage
        -> gaps
        -> deterministic correction selection
        -> evidence fingerprint
```

This Story consumes those foundations to answer:

> What objectively happened in the market after T0 over horizon H?

The result is objective market evolution. It is not a prediction, ML label,
trade result, strategy backtest, or financial outcome.

## Context

The temporal boundary is fundamental:

```text
PAST / ANALYSIS SIDE

Historical evidence <= T0
        -> IntelligenceContext @ T0
        -> deterministic analysis @ T0

================ T0 ================

FUTURE / EVALUATION SIDE

Historical OHLC range (T0, T0 + H]
        -> objective market outcome
```

Future observations must never enter the historical context, its fingerprint,
feature inputs, or deterministic analysis at T0. Outcome evaluation happens only
after the analysis boundary and preserves separate provenance.

## Architectural Authority

Relevant accepted decisions are:

- ADR-035 - Strategy Evaluation Context and Strategy-Agnostic Evaluation Pipeline.
- ADR-048 - Intelligence Evidence and Authority.
- ADR-049 - Reusable Deterministic Market Structure Capability.
- ADR-056 - Deterministic Market Intelligence Evidence and Analyzer Boundary.
- ADR-057 - Historical Market Evidence, Known-At Semantics, and Source Retention.
- ADR-058 - Historical Market Intelligence Replay and Evidence Acquisition Boundary.

If repository inspection confirms that objective outcome ownership is not already
covered by an accepted ADR, implementation must create the next available ADR
for Objective Future Market Outcomes before finalizing the implementation. The
ADR must establish outcome ownership, temporal separation, evidence policy,
versioning, fingerprinting, correction behavior, and non-authority over strategy,
risk, execution, and ML labels.

## Ownership

Market Data remains authoritative for normalized historical market facts,
eligibility, correction selection, bounded reads, coverage, gaps, and evidence
fingerprints.

Market Intelligence owns the objective outcome domain/application boundary,
reference-price semantics, maturity, completeness interpretation, metric
calculation, outcome definition version, provenance, and immutable outcome
artifact identity.

Do not create a Research microservice. Do not move outcome calculation into
Market Data.

## Scope

- Extend the existing Market Intelligence internal Market Data client with the
  bounded historical OHLC range endpoint from Story 0107C-0.
- Use the existing Feign, Eureka, service JWT, and `ROLE_SERVICE` conventions.
- Call Market Data directly through its internal contract; do not use Gateway,
  direct PostgreSQL access, Kraken, or another provider.
- Add the smallest provider-neutral wire DTO for the merged 0107C-0 range
  contract, including request boundaries, observations, status, counts,
  coverage, gaps, evidence fingerprint, and normalization versions.
- Introduce a validated `OutcomeHorizon` value object backed by `Duration`.
- Support at least `1h`, `4h`, and `24h` research horizons.
- Use `FIFTEEN_MINUTES` as the explicit V1 future path interval.
- Introduce an immutable provider-neutral `ObservedMarketOutcome` model.
- Introduce a pure stateless `MarketOutcomeCalculator`.
- Introduce a focused application boundary equivalent to
  `MarketOutcomeEvaluationService`.
- Use an injected `Clock` only for maturity and `computedAt` orchestration.
- Obtain the reference price from the latest eligible strict captured closed,
  non-synthetic OHLC candle at or before T0.
- Obtain future evidence exclusively from the bounded `CAPTURED` range
  contract with `fromExclusive = T0` and `toInclusive = T0 + H`.
- Define explicit `PENDING`, `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE`
  outcome statuses.
- Calculate objective BigDecimal metrics only for a complete future range.
- Persist finalized research outcomes as immutable/versioned artifacts when
  repository conventions and the approved design require persistence.
- Make repeated evaluation idempotent without silently overwriting prior
  evidence versions.
- Preserve a clean future linkage/reference to `AnalysisExecution` without
  making it the outcome identity.
- Provide a small internal evaluation API only if existing application
  invocation patterns do not already provide a sufficient controlled boundary.
- Add the required unit, integration, controller/client, persistence, temporal,
  correction, fingerprint, and restart tests.
- Perform controlled runtime validation when local prerequisites are available.
- Update canonical Story/ADR/implementation documentation required by the
  approved design; do not add frontend or Gateway work.

## Temporal Invariants

The analysis side is strictly bounded:

```text
all feature and analysis evidence <= T0
```

The outcome side is strictly future-only:

```text
all future outcome evidence > T0
```

The future range uses the existing Market Data semantics and must not duplicate
or reinterpret its eligibility predicate. Eligible future observations are:

```text
openTime >= T0
closeTime <= T0 + H
closed = true
synthetic = false
origin = CAPTURED
```

The range is `(T0, T0 + H]` at the window level while a candle whose
`openTime == T0` is eligible under the established full-candle predicate. A
non-aligned T0 must not include an overlapping candle whose open time precedes
T0. The gap between the reference candle close and the first complete future
candle remains explicit; no price path is fabricated through it.

Do not reimplement range selection in Market Intelligence.

## Market Data Client Contract

The conceptual internal request is:

```text
GET /internal/v1/historical-ohlc/{marketId}/range
    ?interval=FIFTEEN_MINUTES
    &fromExclusive=T0
    &toInclusive=T0+H
    &evidencePolicy=CAPTURED
```

The provider-neutral response must preserve the equivalent of:

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
normalizationVersions
```

The wire contract must not expose JPA entities, provider DTOs, Kraken fields,
provider URLs, pagination details, or database implementation details.

## Horizon and Maturity

`OutcomeHorizon` is a validated positive bounded duration. It must reject zero,
negative, and unreasonably large values according to an explicit versioned
policy. Initial supported research horizons are at least:

```text
1h
4h
24h
```

For every request:

```text
maturityAt = evaluationCutoff + horizon
```

The application service must use `now >= maturityAt` as the recommended maturity
rule. Before maturity, it returns `PENDING`, does not calculate final metrics,
and does not persist a finalized immutable outcome.

## Reference Evidence

The reference observation is selected through the existing strict historical
Market Data contract. It must be:

```text
CAPTURED
closed
non-synthetic
eligible at T0
```

The V1 reference price is the `close` of the latest eligible reference candle at
or before T0. Preserve reference provenance as appropriate, including:

```text
observationFingerprint
closeTime
knownAt
normalizationVersion
```

Do not use a future candle close, future candle open, current ticker, current
provider state, or any provider fallback as the reference price.

If no strict reference observation exists, return `UNAVAILABLE` with no invented
reference price and no complete numeric outcome metrics.

## Future Evidence and Completeness

Future evidence comes only from the bounded range endpoint with:

```text
interval = FIFTEEN_MINUTES
fromExclusive = T0
evidencePolicy = CAPTURED
```

Do not query Kraken, repair gaps, interpolate values, or use current provider
state. A finalized `AVAILABLE` outcome requires:

```text
reference evidence available
future range status = AVAILABLE
expectedObservationCount > 0
observedObservationCount == expectedObservationCount
```

If the range is incomplete or unavailable, do not calculate finalized metrics.
Missing evidence must never be represented as zero.

Status semantics:

- `PENDING`: `now < maturityAt`.
- `AVAILABLE`: reference exists and the matured future range is complete.
- `INCOMPLETE`: the horizon matured and future evidence exists, but coverage or
  gaps prevent trustworthy complete metrics.
- `UNAVAILABLE`: reference evidence is absent or no eligible future evidence is
  available.

The implementation must deliberately decide whether matured `INCOMPLETE` and
`UNAVAILABLE` results are persisted as immutable research artifacts. The
preferred V1 behavior is to persist them when they are finalized because
coverage failure is useful research information, while not persisting
`PENDING` as final truth.

## Outcome Metrics

The domain model must remain provider-neutral, strategy-neutral, and meaningful
without a trade direction. It must not contain trade P&L, stop-loss, take-profit,
fees, slippage, funding, leverage, position size, account equity, risk-adjusted
return, or strategy success.

For a complete future window:

```text
finalPrice = close of the chronologically final selected future candle
futureHigh = maximum HIGH across the selected future candles
futureLow = minimum LOW across the selected future candles

forwardReturn = (finalPrice - referencePrice) / referencePrice
futureHighReturn = (futureHigh - referencePrice) / referencePrice
futureLowReturn = (futureLow - referencePrice) / referencePrice
futureRange = (futureHigh - futureLow) / referencePrice
```

Returns are decimal fractional values: `0.0125` means `+1.25%` and `-0.03`
means `-3%`. `futureLowReturn` remains signed and is not converted into an
adverse-excursion or direction-dependent metric. Do not use MFE or MAE
terminology. `futureRange` is normalized price span, not volatility or P&L.

All authoritative calculations must use `BigDecimal` with explicit repository
precision and rounding conventions. Do not use `double` or `float` in the
domain calculation.

## Outcome Domain Model

Introduce the smallest immutable model equivalent to:

```text
ObservedMarketOutcome
    marketId
    evaluationCutoff
    horizon
    maturityAt
    status
    reason
    referencePrice
    referenceObservationFingerprint
    finalPrice
    futureHigh
    futureLow
    forwardReturn
    futureHighReturn
    futureLowReturn
    futureRange
    expectedObservationCount
    observedObservationCount
    futureEvidenceFingerprint
    outcomeDefinitionVersion
    outcomeFingerprint
    computedAt
```

Exact names may follow repository conventions. Optional metrics must remain
explicitly absent for non-final or incomplete statuses, not zero-substituted.

The pure `MarketOutcomeCalculator` receives reference evidence, complete future
OHLC observations, horizon metadata, and the definition version. It must not
perform HTTP, repository access, persistence, clock reads, Risk access,
Execution access, Broker access, News access, or mutable global-state access.

The application service validates the request, checks maturity, retrieves the
reference and future evidence, validates completeness, invokes the pure
calculator, calculates provenance/fingerprint, and persists an immutable result
when applicable.

## Definition Version and Fingerprinting

Introduce an explicit V1 definition version, conceptually:

```text
market-outcome-v1
```

The version owns at least the reference semantics, future interval, window
boundaries, evidence policy, completeness requirements, final-price semantics,
metric formulas, and rounding semantics. Changing those semantics requires a
new definition version.

The deterministic outcome fingerprint must be derived from stable semantic
material including, as appropriate:

```text
outcomeDefinitionVersion
marketId
evaluationCutoff
horizon
referenceObservationFingerprint
futureEvidenceFingerprint
calculated outcome values
status
```

Do not include `computedAt`, execution duration, database IDs, random UUIDs, or
other operationally variable values. Identical inputs and evidence must produce
the same fingerprint after restart. Changing the horizon, T0, selected
reference, future evidence, definition version, status, or calculated metrics
must change the fingerprint.

## Persistence and Versioning

Persist objective outcomes as immutable/versioned research artifacts when the
approved design confirms persistence is required. The identity is fundamentally
based on:

```text
marketId + evaluationCutoff + horizon + outcomeDefinitionVersion + evidence
```

An `AnalysisExecution` may reference an outcome but must not be its fundamental
identity. Several analyses may reference the same objective outcome.

Use deterministic identity/fingerprint and database uniqueness to make repeated
evaluation idempotent. Re-evaluating identical evidence must not create a
duplicate artifact.

If a later captured correction changes the selected future evidence, preserve the
old outcome and create a new immutable outcome version with a new future evidence
fingerprint and outcome fingerprint. Never silently overwrite historical truth.

At minimum, outcome provenance must preserve:

```text
marketId
evaluationCutoff
horizon
referenceObservationFingerprint
futureEvidenceFingerprint
outcomeDefinitionVersion
outcomeFingerprint
```

Provide bounded repository reads for future research, including equivalents of
`findByMarketAndCutoffAndHorizon(...)` and `findByFingerprint(...)`, without
building batch dataset infrastructure.

## Internal API and Integration

An internal controlled endpoint is acceptable, conceptually:

```text
POST /internal/v1/market-outcomes/evaluate
```

with:

```text
marketId
horizon
```

Use existing service authentication and `ResponseEntity` conventions. Keep the
endpoint under `/internal/**`, do not expose it through Gateway or frontend, and
do not automatically schedule it for live analyses. A direct application
invocation is acceptable if it is consistent with existing repository patterns;
the implementation must document that choice.

## Explicit Non-Goals

- ML labels, label policies, feature projections, feature vectors, datasets, or
  Python ML services.
- LLM, RAG, embeddings, agent orchestration, or semantic recommendations.
- Strategy backtesting, trade simulation, trade P&L, entry/exit simulation,
  stop-loss, take-profit, fees, slippage, funding, leverage, or account results.
- Opportunity scoring, TradePlan, Risk, Execution, Broker, or Gateway changes.
- Historical News, economic calendars, quotes, order books, or trade-tape replay.
- Provider fallback, provider backfill, current provider calls, or synthetic gap
  repair.
- Automatic schedulers, Quartz, Kafka, delayed jobs, workers, large batch
  evaluation, research dashboards, or frontend changes.
- A new Research microservice or a parallel persistence authority.
- Changing `IntelligenceContext @ T0`, its fingerprint, or deterministic analysis
  semantics by introducing future observations.

## Acceptance Criteria

### AC1 - Provider-Neutral Future Range Consumption

Market Intelligence consumes the Story 0107C-0 bounded range contract through the
existing internal Feign/service-auth path. It does not access Market Data
PostgreSQL directly, route through Gateway, or call a current provider.

### AC2 - Temporal Separation

Future evidence cannot enter the T0 `IntelligenceContext`, context fingerprint,
feature projection, or deterministic analysis. Reference evidence is obtained
from the strict as-of side and future evidence from the bounded CAPTURED range
side only.

### AC3 - Validated Horizons and Maturity

`1h`, `4h`, and `24h` are supported with a validated duration value object.
Before `T0 + H`, evaluation returns `PENDING` without finalized metrics or final
immutable persistence. At exact maturity, evaluation is permitted.

### AC4 - Reference Semantics

The reference price is the close of the latest eligible captured, closed,
non-synthetic observation at or before T0. Missing reference evidence returns
`UNAVAILABLE` without provider fallback or invented values.

### AC5 - Completeness Semantics

Only a complete, matured, CAPTURED future range produces `AVAILABLE` and numeric
outcome metrics. `INCOMPLETE` and `UNAVAILABLE` remain explicit and do not use
zero substitution.

### AC6 - Golden Fixture

For `T0 = 2026-10-15T10:00:00Z`, `H = 1h`, reference price `100`, and the four
15-minute future candles from the 0107C-0 fixture, exact BigDecimal assertions
are:

```text
finalPrice = 102
futureHigh = 105
futureLow = 98
forwardReturn = 0.02
futureHighReturn = 0.05
futureLowReturn = -0.02
futureRange = 0.07
```

### AC7 - Strategy Neutrality

The outcome contains objective market facts and no LONG/SHORT direction,
strategy result, trade result, P&L, risk, or execution semantics. It does not
create ML labels.

### AC8 - Deterministic Calculator

The pure calculator uses BigDecimal, performs no I/O or clock access, and yields
the same material result for the same evidence, horizon, cutoff, and definition
version.

### AC9 - Versioned Fingerprint

Identical semantic inputs produce the same outcome fingerprint across repeated
evaluation and service restart. Changes to T0, horizon, definition version,
selected reference evidence, selected future evidence, status, or metrics change
the fingerprint. `computedAt` alone does not.

### AC10 - Immutable Correction Versioning

When a later captured correction changes selected future evidence, the old outcome
remains retrievable and a new immutable version is produced. The new evidence and
outcome fingerprints differ. No destructive overwrite occurs.

### AC11 - Idempotence

Evaluating identical T0, horizon, definition, reference evidence, and future
evidence twice produces one persisted artifact with the same fingerprint.

### AC12 - Isolation

Market, interval, horizon, cutoff, and evidence filters prevent cross-market,
cross-interval, or cross-horizon evidence leakage.

### AC13 - Internal Boundary

Any evaluation API remains internal, authenticated, provider-neutral, and not
exposed through Gateway or frontend. No scheduler is introduced.

### AC14 - Documentation and ADR

The implementation report documents temporal semantics, statuses, formulas,
precision, persistence, idempotence, correction behavior, limitations, and
runtime validation. A new accepted ADR is created if repository inspection shows
that the objective outcome architectural decision is not already owned.

## Required Tests

- `PENDING` before maturity.
- Exact maturity using `now >= maturityAt`.
- Missing strict reference returns `UNAVAILABLE`.
- Future range `UNAVAILABLE` returns no finalized metrics.
- Future range `INCOMPLETE` returns no finalized metrics.
- Complete future range calculates the golden fixture exactly with BigDecimal.
- No provider fallback during reference or future evaluation.
- Non-aligned T0 uses only complete future candles with `openTime >= T0`.
- Temporal separation prevents future evidence from changing the T0 context or
  context fingerprint.
- Correction selection changes future evidence and outcome fingerprints while
  preserving the old immutable outcome.
- Repeated identical evaluation is idempotent.
- Different horizons produce distinct outcomes without evidence leakage.
- BTC and ETH outcomes remain isolated.
- Fingerprint stability and sensitivity for evidence, horizon, T0, definition,
  metrics, and `computedAt`.
- Persistence restart reproduces metrics, provenance, definition, and
  fingerprint.
- Internal client/controller contract and service authentication behavior.
- PostgreSQL persistence/integration tests using the repository's established
  approach.

## Golden Fixture

Use the factual 0107C-0 fixture:

```text
T0 = 2026-10-15T10:00:00Z
interval = FIFTEEN_MINUTES
H = 1h
referencePrice = 100

10:00-10:15  O=100 H=103 L=99 C=102
10:15-10:30  O=102 H=105 L=101 C=104
10:30-10:45  O=104 H=104 L=99 C=100
10:45-11:00  O=100 H=102 L=98 C=102
```

The fixture must be evaluated through the provider-neutral contracts. Tests must
also prove that the fixture's future candles do not alter the historical context
at T0.

## Runtime Validation

When local prerequisites are available, perform controlled validation using
persisted deterministic evidence and no external provider:

- `AVAILABLE`: complete 1-hour window and expected metrics.
- `INCOMPLETE`: omit one future candle and verify explicit incomplete status with
  no finalized metrics.
- Repeat `AVAILABLE` evaluation and verify the same fingerprint and one artifact.
- Restart the relevant service and verify the same deterministic result.

If prerequisites are unavailable, report the exact blocked dependency. Do not
claim runtime success without executing it.

## Validation Expectations

Run targeted Market Intelligence and relevant Market Data tests first, followed
by the broadest applicable module validation:

```text
mvn -q test
mvn -q verify
```

Run SonarQube when the authenticated repository configuration supports it.
Report unavailable or blocked checks explicitly and never treat missing
validation as success.

## Security and Operations

- Preserve service JWT authentication and `ROLE_SERVICE` conventions.
- Keep evaluation endpoints under `/internal/**`.
- Do not log or expose credentials, JWTs, provider secrets, or raw sensitive
  authentication material.
- Do not introduce current-provider fallback or unsafe external writes.
- Keep observability bounded to market, cutoff, horizon, status, counts, gaps,
  and deterministic identifiers according to repository logging conventions.

## Expected Deliverables

- Provider-neutral Market Intelligence range client DTO/adapter extension.
- Validated horizon value object.
- Immutable outcome domain model and explicit statuses.
- Pure deterministic outcome calculator.
- Application evaluation service and controlled internal invocation boundary.
- Immutable/versioned persistence and bounded repository reads where approved.
- Tests and runtime validation report.
- Objective Future Market Outcomes ADR if required by repository inspection.
- Story implementation report covering the complete validation matrix.

## Recommended Follow-Up

Story 0108 - Research Dataset and Feature Projection Foundation.

This follow-up may derive versioned features or labels from immutable objective
outcomes. It must not move outcome semantics into the feature or ML boundary.

## Approval Boundary

This Story artifact defines scope and acceptance criteria only. It does not
constitute human approval for repository analysis, architecture, implementation,
commit, push, merge, or pull request creation.
