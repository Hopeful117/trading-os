# Trading OS Quantitative Research Readiness Investigation

**Date:** 2026-10-17
**Scope:** overnight repository, runtime, data, architecture, and methodological investigation
**Mode:** read-only investigation; no production code, runtime state, database data, or user changes were modified

```text
TRADING_OS_QUANT_RESEARCH_READINESS
status: RED_FOR_PROFITABILITY_CLAIMS
data_collection: CONTINUE_WITH_GUARDRAILS
research_pipeline: NOT_READY
execution_realism: NOT_VALIDATED
capital_allocation: NO-GO
```

## Executive Decision

`Trading OS is not ready for credible quantitative trading research or profitability claims.`

The platform has useful foundations for a data-accumulation phase: Kraken market ingestion exists, historical OHLC evidence is persisted, research lifecycle scheduling exists, temporal evidence carries provenance fields, and the architecture separates Market Data, Market Intelligence, Risk, and execution concerns.

The current system is not yet a trustworthy research engine. Runtime evidence shows that passive research is active but degraded, `feature_snapshots` contains zero rows, order-book capture is disabled, funding data is not available in the runtime database, and the current evidence volume is far below what is needed for statistically meaningful conclusions. The present system must therefore be treated as an engineering/data-collection platform, not as a validated strategy, backtester, or profitability engine.

The correct operating model is two clocks:

1. **DATA CLOCK:** collect and preserve future research inputs passively, with complete timestamps, provenance, quality status, and immutable raw evidence.
2. **ENGINEERING CLOCK:** build and validate feature generation, execution realism, cost models, dataset versioning, replay, backtesting, and statistical evaluation without claiming economic validity before the data and evaluation contracts are satisfied.

## Evidence Classification

This report distinguishes evidence as follows:

- **FACT:** directly observed in the repository or runtime during this investigation.
- **INFERENCE:** conclusion derived from one or more facts.
- **RECOMMENDATION:** proposed next action, not current system behavior.
- **UNKNOWN:** not measurable with the available runtime, repository, or data.

## Investigation Boundaries and Constraints

### Facts

- Current branch is `main` and current `HEAD` matches `origin/main` at `2d7a23331f1d40e7f030a9af84c486c518c7db96`.
- `git fetch origin` could not complete because the SSH signing/agent operation was refused. No remote freshness beyond the existing local refs was assumed.
- DevLog context was attempted but unavailable because the provider quota was exhausted. Repository state and runtime evidence are therefore the authoritative sources for current behavior in this report.
- Existing local modifications were preserved and not changed:
  - `docker-compose.yml`
  - `market-data/src/main/java/com/hope/trading/market_data/service/MarketSynchronizationImpl.java`
- No service was restarted, rebuilt, stopped, reset, or reconfigured during the investigation.

### Unknowns

- Whether the running containers were built from exactly the current local source tree rather than an older image.
- Whether additional data exists outside the inspected PostgreSQL runtime database.
- Whether any external broker, exchange, or research environment has a separate dataset not represented in this repository.
- Whether the current local `origin/main` ref is current with the remote because fetch authentication failed.

## Current Runtime Snapshot

The runtime snapshot was collected on 2026-10-17. Counts are observations of the inspected PostgreSQL instance and are not extrapolations.

| Area | Observed state | Interpretation |
|---|---:|---|
| Market catalogue | 1,668 markets | Broad catalogue, but not a research sample by itself |
| Historical OHLC evidence | 740 rows | Small and short-lived evidence set |
| OHLC intervals | 308 x 15m, 226 x 1h, 206 x 4h | Sparse and uneven by interval |
| OHLC status | 740 `CAPTURED`, closed, non-synthetic | Positive evidence quality for these rows |
| OHLC time span | Approximately 2026-09-12 to 2026-10-17 | About five weeks, with unknown continuity across markets |
| OHLC latest `knownAt` | 2026-10-17 00:58:16 UTC | Evidence arrival is not aligned with candle close |
| Order Book runtime table | Missing | Runtime schema does not contain current order-book persistence |
| Funding runtime table | Missing | Runtime schema does not contain current funding persistence |
| Feature snapshots | 0 rows | Research feature projection is not producing usable snapshots |
| Passive analysis executions | 5 total: 4 completed, 1 failed | Lifecycle is active but not healthy |
| Completed passive executions | Results marked degraded in logs | Completion does not imply research-grade output |
| Observed market outcomes | 7 total: 6 available, 1 unavailable | Too small for evaluation |
| Outcome horizon | Observed 1-hour outcome records | No meaningful multi-horizon evaluation base yet |

Strict temporal eligibility checks showed that all 740 inspected OHLC rows were eligible relative to the current time, while 734 were eligible relative to a one-hour-old cutoff. No inspected row had `knownAt <= closeTime`; this is expected for a live-arriving observation but means that a research query must never use `closeTime` as a substitute for knowledge time.

## What Exists Today

### Market Data

The repository contains a Kraken data-stream integration and historical OHLC evidence persistence. The implementation recognizes ticker, OHLC, trade, and order-book stream concepts. Historical OHLC evidence includes fields suitable for temporal filtering and provenance, including interval, open/close boundaries, `knownAt`, status, and synthetic/closed indicators.

The current runtime proves only the OHLC path. It does not prove that the Order Book or funding paths are deployed or collecting.

### Research Lifecycle

Market Intelligence has a scheduled research lifecycle with configurable cadence and horizons. Runtime configuration showed:

- research lifecycle enabled;
- cadence `PT1H`;
- horizons `PT1H`, `PT4H`, and `PT24H`;
- OHLC capture active for the inspected market and intervals `FIFTEEN_MINUTES`, `ONE_HOUR`, and `FOUR_HOURS`.

This is a useful orchestration skeleton. It is not yet an end-to-end research dataset pipeline because the feature projection stage is failing or producing no persisted feature snapshots.

### Execution and Costs

The repository contains the beginnings of broker-neutral execution contracts, PAPER concepts, fee-related configuration, and realistic-execution research artifacts. These are important boundaries, but the runtime evidence does not establish a validated cost-aware backtest.

The current architecture does not yet prove complete coverage of:

- spread at decision time;
- order-book depth and market impact;
- latency between decision, submission, acknowledgement, and fill;
- partial fills;
- rejected and unknown broker outcomes;
- maker/taker fee selection from actual execution facts;
- funding or borrow costs;
- slippage distributions by instrument and liquidity regime;
- cancellation and replacement behavior;
- reconciliation of ambiguous execution outcomes.

### Research Consumer

The repository contains a Python research dataset consumer and a first ML-baseline story/notebook. The consumer and validation direction are appropriate, but current runtime evidence does not show a populated, versioned dataset suitable for model selection or profitability analysis.

## Capability Matrix

| Capability | Repository evidence | Runtime evidence | Readiness |
|---|---|---|---|
| Market catalogue | Present | 1,668 markets | Foundation only |
| OHLC ingestion | Present | 740 persisted rows | Data accumulation |
| OHLC temporal provenance | Present | `knownAt` and close times observed | Promising, requires enforcement |
| OHLC continuity monitoring | Partial | Not demonstrated | Not ready |
| Order Book ingestion | Code/contracts present | Disabled; table absent | Not operational |
| Funding ingestion | Story/code direction present | Table absent; no evidence | Not operational |
| Feature snapshot projection | Present in architecture | 0 rows; lifecycle errors | Not operational |
| Outcome labeling | Present in architecture | 7 rows | Smoke-test scale |
| Dataset manifest/versioning | Partial | No credible populated manifest observed | Not ready |
| Python research consumer | Present | No validated production-scale dataset | Not ready |
| Backtest engine | Partial execution concepts | No evidence of validated replay/backtest | Not ready |
| Spread/slippage model | Partial contract direction | No empirical calibration | Not ready |
| Fee model | Partial configuration/contracts | No validated round-trip integration | Not ready |
| Funding/borrow model | Planned/partial | No runtime data | Not ready |
| PAPER execution | Present as architecture direction | No end-to-end validation evidence | Not ready |
| Broker reconciliation | Required contracts exist | No realistic ambiguous-outcome validation | Not ready |
| Risk authority | Explicit architectural boundary | No evidence of research bypass | Foundation |
| Statistical evaluation | Notebook/story direction | Sample too small | Not ready |

## Temporal Integrity Audit

### Required event clocks

A research-grade observation must distinguish at least:

1. event time at the venue;
2. local receipt time;
3. persistence time;
4. feature availability time;
5. decision time;
6. order submission time;
7. acknowledgement time;
8. fill time;
9. outcome observation time.

The current OHLC evidence model has enough direction to preserve candle boundaries and `knownAt`, but the full end-to-end clock contract is not demonstrated for features, decisions, orders, fills, and outcomes.

### Leakage risks

The following are material risks unless enforced at query and dataset-build boundaries:

- using candle close time instead of `knownAt` for feature eligibility;
- creating a feature snapshot from a market state that was persisted after the decision cutoff;
- labeling an outcome from a candle whose final value was not known when the prediction was made;
- using current market metadata or current symbol status when reconstructing historical decisions;
- including data from a later lifecycle run in an earlier dataset version;
- joining multiple evidence rows by event time without preserving the latest-known-at cutoff;
- treating an unavailable outcome as a negative outcome;
- using future order-book levels or future funding rates in a historical execution estimate.

The current runtime finding that `knownAt` is later than `closeTime` confirms why event time alone is insufficient. A row can describe an earlier candle while only becoming known later.

### Recommended temporal contract

Every feature and outcome query should require an explicit `asOf` or `decisionKnownAt` value and enforce:

```text
evidence.knownAt <= decisionKnownAt
```

For a feature vector, the effective availability time should be the maximum `knownAt` across all input evidence. The feature vector must be unavailable if any required input is unavailable, stale beyond policy, or arrives after the decision cutoff.

For an outcome, the label should include its own `observedAt`, `availableAt`, and status. An unavailable label must remain censored/unavailable, not become a loss.

## Sample Independence and Overlap

The active research horizons include 1h, 4h, and 24h while the cadence is 1h. This naturally creates overlapping labels. Overlap is not inherently invalid, but it changes the effective sample size and invalidates naive random train/test splitting.

### Risks

- adjacent hourly decisions can share most of the same future return window;
- the same market can contribute highly correlated observations across intervals;
- multiple symbols can share common market beta and regime exposure;
- a random split can place near-identical temporal regimes in train and test;
- repeated lifecycle executions can appear as more samples without adding independent information;
- tuning on overlapping validation windows can overstate model stability.

### Required evaluation policy

- Use chronological walk-forward splits.
- Purge observations whose label windows overlap the training or validation boundary.
- Apply an embargo after the training boundary where required by the horizon.
- Report the number of raw observations and the effective independent sample estimate separately.
- Group or block by instrument and regime for sensitivity analysis.
- Never describe the 740 OHLC rows as 740 independent research samples.

## Execution Realism Audit

### Fee model

The code and stories indicate that fee-aware execution is an intended concern. Runtime evidence does not establish that every backtest or PAPER decision uses a fee schedule selected by venue, product, account tier, maker/taker role, and execution timestamp.

The minimum economic calculation must include both entry and exit costs:

```text
net PnL = gross price PnL
          - entry fee
          - exit fee
          - spread cost
          - slippage/impact cost
          - funding or borrow cost
          - conversion/transfer cost where applicable
```

Fee configuration alone is not evidence of correctness. Actual fills or a deterministic fee assumption must be stored with the simulated trade and included in the dataset provenance.

### Funding and borrow

Funding persistence is not available in the inspected runtime database. The repository contains migration/story direction for funding evidence, but the running database lacks the corresponding table. Therefore funding-adjusted profitability cannot currently be measured for derivatives or leveraged positions.

For spot, borrow may be irrelevant to an unleveraged long but becomes relevant for margin, shorting, or financing. The model must make the product and financing assumption explicit rather than treating absent funding as zero cost universally.

### Spread and slippage

OHLC alone cannot identify the cost of crossing the spread or the cost of consuming depth. A backtest based only on candle prices must use an explicitly conservative execution rule and must not describe the result as fill-realistic.

The currently disabled Order Book capture prevents empirical calibration of:

- quoted spread;
- top-of-book depth;
- depth at the intended order size;
- queue position;
- market impact;
- adverse selection;
- liquidity regime changes.

### Latency and partial fills

No runtime evidence was found that supplies a validated latency distribution or a partial-fill replay model. These are essential for short-horizon strategies and for any claim that PAPER behavior approximates live behavior.

### Rejected and unknown orders

Broker rules require rejected, acknowledged, and unknown outcomes to remain distinct. An ambiguous response after a write must trigger reconciliation rather than being treated as a definitive rejection. This is an architectural requirement, but it has not been validated by the inspected runtime evidence.

## Data Quality and Missingness

The current OHLC rows are marked captured, closed, and non-synthetic, which is positive. It does not establish completeness.

The following quality dimensions remain required:

- expected cadence versus observed cadence per market and interval;
- duplicate and overlapping candle detection;
- gap detection;
- out-of-order arrival handling;
- stale stream detection;
- symbol delisting or market-status transitions;
- quote/base currency consistency;
- precision and tick-size changes;
- malformed or partial provider messages;
- data availability by market regime;
- missingness that is correlated with volatility or liquidity.

Missingness must be modeled as a state, not silently imputed. In particular:

- `UNAVAILABLE` outcome is not a negative label;
- missing Order Book is not zero liquidity;
- missing funding is not zero funding;
- missing feature snapshot is not a neutral feature vector;
- unavailable historical evidence must remain unavailable in the dataset manifest.

## Universe and Survivorship Risk

The 1,668-market catalogue is not automatically a historical universe. A historical study requires the market membership and metadata as known at each decision time.

Risks include:

- using today’s market list for historical dates;
- excluding delisted or inactive markets from the denominator;
- selecting only markets with currently available evidence;
- ignoring symbol renames, product migrations, quote-currency changes, or contract changes;
- using current liquidity filters in past periods.

The research dataset must preserve point-in-time universe membership and include delisted/inactive instruments when they were eligible at the historical decision time.

## Current Research Readiness Scorecard

Scores are capability maturity, not quality grades.

| Dimension | Score | Status | Blocking evidence |
|---|---:|---|---|
| OHLC acquisition | 2/5 | Partial | 740 rows, narrow history, continuity not proven |
| Temporal provenance | 2/5 | Partial | `knownAt` exists, end-to-end enforcement unproven |
| Order Book evidence | 0/5 | Absent at runtime | Capture disabled; table absent |
| Funding evidence | 0/5 | Absent at runtime | Table absent; no observations |
| Feature snapshots | 0/5 | Broken | Zero rows; projection errors |
| Outcome labels | 1/5 | Smoke-test only | Seven rows, one unavailable |
| Dataset versioning | 1/5 | Partial | Consumer direction exists; populated immutable dataset not proven |
| Backtesting | 1/5 | Not validated | No credible replay/economic validation evidence |
| Fees | 1/5 | Partial | Contracts/configuration direction, empirical integration unproven |
| Spread/slippage | 0/5 | Absent | No active Order Book evidence |
| Latency/partial fills | 0/5 | Absent | No validated model or replay evidence |
| Risk integration | 2/5 | Architectural foundation | Deterministic boundary exists; end-to-end research validation incomplete |
| PAPER realism | 1/5 | Partial | Concepts exist; runtime validation incomplete |
| Statistical evaluation | 0/5 | Absent for claims | Sample too small and overlapping |

**Overall decision: RED for profitability claims; AMBER for continued passive data collection; RED for live capital allocation based on current research output.**

## Priority Risks

### P0: False confidence from empty or degraded research output

The lifecycle runs, but `feature_snapshots` is empty and logs show degraded/error paths. A successful scheduler execution must not be presented as a successful research observation.

**Required control:** expose health metrics and fail the research run when required feature inputs cannot produce a persisted snapshot; distinguish `COMPLETED`, `DEGRADED`, and `FAILED` in all dashboards and datasets.

### P0: Leakage through availability-time violations

The presence of `knownAt` is not sufficient unless every feature and label query enforces it. The observed difference between candle close and knowledge time makes this a concrete risk.

**Required control:** immutable point-in-time query APIs and tests that insert late-arriving evidence and verify it is excluded from earlier decisions.

### P0: Economic invalidity from missing execution costs

Without spread, slippage, fees, funding/borrow, latency, and partial-fill rules, a positive result is not a trading result.

**Required control:** every simulated execution must persist the cost assumptions and a net-of-cost result; reject research reports with incomplete cost provenance.

### P1: No independent sample base

Hourly cadence with 1h/4h/24h labels creates overlapping observations. Naive sample counts and random splits will overstate evidence.

**Required control:** walk-forward evaluation with purging, embargo, and effective-sample reporting.

### P1: Runtime/source schema drift

The source tree contains Order Book and funding migration direction, while the runtime database is at schema 5 and lacks those tables.

**Required control:** add startup/schema readiness checks and an explicit deployment verification for migrations and feature flags.

### P1: Universe survivorship

The market catalogue count cannot be used as a historical universe without point-in-time membership.

**Required control:** persist universe snapshots and market metadata versions with each dataset manifest.

### P2: Missingness bias

Unavailable outcomes and absent liquidity/funding observations can correlate with volatility, outages, or illiquid instruments.

**Required control:** publish coverage and missingness by market, interval, regime, and lifecycle run; do not impute missing economic evidence as zero.

### P2: Operational blindness

The current lifecycle can complete in a degraded state while producing no feature snapshots.

**Required control:** alert on zero-output runs, stale evidence, missing intervals, and repeated projection errors.

## Required Architecture for a Credible Research Loop

```text
Venue event
  -> raw immutable evidence
  -> normalized evidence with eventTime and knownAt
  -> point-in-time feature builder(asOf)
  -> feature snapshot with input manifest and availabilityTime
  -> decision/proposal
  -> deterministic Risk evaluation
  -> execution simulator or PAPER adapter
  -> fill/partial-fill/rejection/unknown result
  -> cost and funding attribution
  -> future outcome observation
  -> immutable dataset manifest
  -> chronological walk-forward evaluation
```

Each stage should produce an auditable record, not only an in-memory object. The dataset builder should be able to answer:

- Which evidence was known at the decision time?
- Which version of the feature logic produced the vector?
- Which universe and market metadata were active?
- Which fee/funding/slippage assumptions were applied?
- Which execution events occurred or were simulated?
- Which labels were available, censored, or invalid?
- Can the exact sample be replayed later?

## Roadmap

### DATA CLOCK: accumulate without pretending to know profitability

#### Phase D1: make current collection observable

- Add per-market and per-interval coverage metrics.
- Detect gaps, duplicates, out-of-order rows, stale streams, and late arrivals.
- Expose lifecycle output counts by status: usable, degraded, failed, unavailable.
- Keep raw provider evidence immutable and retain normalized records separately.

#### Phase D2: deploy missing evidence paths

- Enable and validate Order Book capture in a controlled environment.
- Apply and verify the Order Book migration in the runtime database.
- Add funding/borrow evidence for the supported product types where applicable.
- Persist venue/product metadata and historical universe membership.

#### Phase D3: accumulate a research-grade history

- Collect enough months and market regimes to cover calm, volatile, trending, and dislocated conditions.
- Define minimum coverage thresholds before a market/interval enters a dataset.
- Preserve missingness rather than filling missing economic fields with zeros.

### ENGINEERING CLOCK: make research reproducible and economically meaningful

#### Phase E1: repair the feature lifecycle

- Diagnose the `IllegalStateException` projection failures.
- Produce and persist `FeatureSnapshot` rows only when all required inputs satisfy the `asOf` cutoff.
- Store feature code version, input evidence IDs, availability time, and quality status.
- Add tests for late data, missing data, stale data, and duplicate evidence.

#### Phase E2: create immutable dataset manifests

- Version source evidence, feature logic, outcome logic, universe, cost assumptions, and code revision.
- Store exact sample IDs and split definitions.
- Make dataset builds deterministic and replayable.

#### Phase E3: implement execution realism

- Add spread and depth-aware fill rules.
- Add calibrated slippage and impact assumptions.
- Add fees for entry and exit with maker/taker and product context.
- Add funding/borrow accrual.
- Model latency, partial fills, cancellations, rejects, and unknown outcomes.
- Persist cost attribution per simulated or PAPER trade.

#### Phase E4: validate research methodology

- Use chronological walk-forward splits.
- Purge overlapping labels and apply embargo.
- Report raw and effective sample sizes.
- Compare against simple baselines and no-trade controls.
- Report confidence intervals, drawdown, turnover, capacity, and sensitivity to costs.
- Require out-of-sample and regime-level results before any profitability language.

#### Phase E5: validate PAPER and broker reconciliation

- Replay decisions through PAPER without bypassing Risk.
- Compare simulated fills with observed market evidence.
- Exercise ambiguous broker responses and reconciliation.
- Validate idempotency and state transitions under retries and restarts.

## No-Go Criteria for Profitability Claims

Do not claim that a strategy is profitable, robust, or production-ready until all of the following are true:

- Feature snapshots are populated and demonstrably point-in-time correct.
- Dataset manifests are immutable, versioned, and replayable.
- Order Book/depth or a consciously conservative alternative is available for the strategy’s horizon.
- Fees, spread, slippage, funding/borrow, latency, and partial fills are represented.
- Historical universe membership is point-in-time correct.
- Labels and feature windows are leakage-tested.
- Walk-forward evaluation uses purging and embargo where required.
- Results survive cost and parameter sensitivity analysis.
- Results include out-of-sample periods and multiple market regimes.
- Risk and execution state remain deterministic and authoritative.
- PAPER behavior has been compared against the same assumptions used in research.

## Recommended Immediate Sequence

1. Treat the system as a passive data collector and engineering prototype, not a strategy validator.
2. Fix and instrument the feature projection path before adding ML complexity.
3. Verify deployment/migration drift and activate Order Book evidence only after operational checks are in place.
4. Add funding/borrow evidence for supported instruments before evaluating leveraged or derivative strategies.
5. Implement point-in-time feature and label contracts with adversarial late-arrival tests.
6. Build immutable dataset manifests and chronological evaluation before running model selection.
7. Implement cost-aware replay and PAPER validation before interpreting any returns.
8. Revisit statistical claims only after sufficient history and coverage have accumulated.

## Final Assessment

Trading OS has a credible direction and several correct architectural boundaries, especially the separation between market data, market intelligence, deterministic risk, and broker execution. The current implementation is valuable as an early accumulation and integration platform.

It is not yet a credible quantitative research platform. The most urgent work is not a more sophisticated model. It is making the research loop produce complete, point-in-time, versioned, cost-aware, replayable evidence and making degraded or empty outputs impossible to mistake for valid samples.

The appropriate decision is:

```text
CONTINUE PASSIVE COLLECTION
PAUSE PROFITABILITY CLAIMS
REPAIR RESEARCH DATA LIFECYCLE
ADD EXECUTION REALISM
VALIDATE WITH WALK-FORWARD OUT-OF-SAMPLE TESTS
```
