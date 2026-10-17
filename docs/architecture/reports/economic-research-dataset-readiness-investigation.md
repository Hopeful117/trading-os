# Trading OS - Economic Research Dataset Readiness Investigation

**Date:** 2026-10-17
**Mode:** investigation only; no production code, migration, runtime data, Python consumer, notebook, commit, push, PR, or merge was changed.

## TASK

Economic Research Dataset Readiness Investigation.

The investigation preserves the distinction between:

```text
FeatureSnapshot      = what the market looked like at T0
ObservedMarketOutcome = what objectively happened after T0
StrategyIntent       = what hypothetical action is evaluated
SimulatedExecution   = what that action could execute against evidence
EconomicResult       = what economic consequences are legitimately derivable
```

## REPOSITORY

```text
HEAD = d407e9ab8f6bef828867a6950264112bfb17dc3b
BRANCH = main
ORIGIN_MAIN = d407e9ab8f6bef828867a6950264112bfb17dc3b
FETCH = succeeded
WORKTREE = clean before and after investigation
```

Latest relevant merges:

```text
Deterministic MARKET Execution Replay
  merge 66caaf95
  implementation a790a2a2

Economic Research Result Integration
  merge 6ec40935
  implementation fd48bf93

Research runtime readiness follow-up
  merge d407e9ab
  implementation 77c6b0e0
```

`Economic Research Result Integration` is present in the current repository. The
implementation is the Trading Core `HistoricalEconomicEvaluationService`, not a
persisted dataset integration. It evaluates entry replay, exit replay, costs,
and lifecycle economics, but there is no EconomicResult repository, export, or
Market Intelligence dataset join.

Unrelated worktree changes preserved: none were present. The repository was not
reset, stashed, cleaned, or checked out over user changes.

DevLog applicability was checked for prior architecture context. The provider
returned an exhausted-credit error, so this report relies on repository and
read-only runtime evidence. The current repository is authoritative.

## ADR STATUS

| ADR | Repository status | Relevance |
|---|---|---|
| ADR-059 - Objective Future Market Outcomes and Evaluation Truth | Accepted | Keeps objective future market evolution separate from strategy economics. |
| ADR-060 - Versioned Feature Projection and Immutable FeatureSnapshot | Accepted | Defines the immutable T0 feature projection and identity. |
| ADR-061 - Explicit Selection of Immutable Outcome Versions | Proposed | Requires exact outcome fingerprint selection; not yet an accepted standalone ADR. |
| ADR-062 - Versioned Research Dataset Boundary | Proposed | Defines `research-dataset-v1`; not yet an accepted standalone ADR. |
| ADR-063 - Historical Order Book Evidence and Replay Semantics | Accepted | Defines strict known-at Order Book replay and visible evidence ownership. |
| ADR-064 - Historical Execution and Transaction-Cost Research Architecture | Accepted | Defines Trading Core economic authority and separate future economic dataset boundary. |

Architecture contradiction: **NONE** in the accepted decisions. Governance gap:
ADR-064 treats the ADR-061/062 boundaries as reaffirmed, while the files for
ADR-061 and ADR-062 still say `Proposed`. This should be reconciled before a
future dataset contract is treated as a new accepted public boundary.

## CURRENT OBJECTIVE RESEARCH MODEL

### FeatureSnapshot

Source: `market-intelligence/.../domain/feature/FeatureSnapshot.java`.

```text
identity = marketId + evaluationCutoff + evidencePolicy + projectionVersion
           + contextFingerprint + semantic analyzer provenance
market identity = marketId
T0 = evaluationCutoff
evidence policy = STRICT_OBSERVED for research
projection version = featureProjectionVersion
values = ordered typed FeatureValue list
groups = FeatureGroupStatus list with explicit missingness
provenance = analyzer/evidence/policy/result provenance
fingerprint = ordered semantic feature content and provenance
```

`analysisExecutionId`, `snapshotId`, and `createdAt` are retained for
provenance/persistence but are not the semantic snapshot identity. The record
copies its lists, and PostgreSQL persists it append-only through
`saveIfAbsent`, checking both identity and content conflicts.

The exact semantic meaning is:

> deterministic information from trusted artifacts that was legitimately
> available at `evaluationCutoff`.

`FeatureSnapshotProjectionService` requires `STRICT_OBSERVED`, matching context
fingerprints, matching cutoffs, and artifact linkage. `FeatureProjectionV1`
does not consume future outcomes.

### FeatureProjection version and inventory

```text
featureProjectionVersion = feature-projection-v1
feature count = 43
```

Inventory:

- OHLC: 2 features, `ohlc.range_fraction` and `ohlc.simple_return`.
- Structure: 15 features across `bias`, `setup`, and `trigger` roles.
- Trend: 26 features across the same three roles plus alignment and attention.
- Order Book liquidity: not included.
- Funding: not included.

`order-book-liquidity-v1` remains a separate capability/artifact boundary. It
has not been added to `feature-projection-v1`.

### ObservedMarketOutcome

Source: `market-intelligence/.../domain/outcome/ObservedMarketOutcome.java`.

```text
identity = marketId + evaluationCutoff + horizon + outcomeDefinitionVersion
           + outcomeFingerprint
T0/reference time = evaluationCutoff
horizon = OutcomeHorizon
maturity = evaluationCutoff + horizon
reference observation = reference price and reference observation fingerprint
future window = bounded OHLC interval after T0
metrics = finalPrice, futureHigh, futureLow, forwardReturn,
          futureHighReturn, futureLowReturn, futureRange
status = PENDING, AVAILABLE, INCOMPLETE, or UNAVAILABLE
version = market-outcome-v1
persistence = immutable PostgreSQL rows keyed by fingerprint
```

ADR-059 defines the reference as the latest eligible closed, captured,
non-synthetic observation at or before T0. Future evidence is bounded to
`(T0, T0 + H]`. Missing evidence is never zero. Corrections create a new
immutable outcome fingerprint.

`ObservedMarketOutcome` explicitly excludes direction, quantity, entry/exit,
fees, funding, PnL, Risk, strategy success, and execution status. The boundary
is intact in the current code and persistence model.

### ResearchSample

Source: `market-intelligence/.../domain/research/ResearchSample.java`.

`ResearchSample` is the V1 objective pair:

```text
Dataset definition version
+ marketId
+ evaluationCutoff
+ one OutcomeHorizon
+ feature projection version
+ FeatureSnapshot
+ selected ObservedMarketOutcome
+ eligibility/exclusion status
+ exclusion reason
```

Its fingerprint includes the dataset definition, market, cutoff, horizon,
projection version, feature fingerprint, outcome fingerprint, status, and
reason. Its deterministic UUID is derived from that fingerprint. It is not an
economic sample and contains no StrategyIntent or EconomicResult.

The current builder creates one sample per distinct FeatureSnapshot and
configured horizon. Duplicate semantic snapshots become excluded samples; they
are not silently collapsed from manifest accounting.

### DatasetManifest and ResearchPopulationSpec

`ResearchPopulationSpec` currently contains:

- population policy version;
- market set and cutoff interval;
- feature projection version;
- strict feature evidence policy;
- outcome definition version;
- horizons;
- one-per-feature-snapshot cadence;
- accepted feature missingness;
- exact selected outcome fingerprints by market/cutoff/horizon.

`DatasetManifest` contains the population, feature schema, objective outcome
schema, counts, exclusion counts, creation time, deterministic fingerprint,
and fingerprint material. It does not contain a strategy policy, execution
model, cost model, or EconomicResult.

The Java bundle exporter emits:

```text
dataset-manifest.json
samples.jsonl
README.md
```

The JSONL row has separate `features`, `outcome`, `featureProvenance`, and
`lineage` objects. The Python validator is fail-closed and checks checksums,
schema, fingerprints, strict evidence, missingness, outcome completeness,
selected outcome identity, and counts.

### research-dataset-v1

```text
research-dataset-v1
  = immutable FeatureSnapshot facts
  + explicitly selected immutable objective outcomes
  + versioned population and exclusion accounting
```

It is not a strategy dataset, label authority, execution dataset, or
profitability dataset. ADR-062 says V1 is one sample per FeatureSnapshot and
horizon. ADR-064 explicitly says economic results must not be retrofitted into
V1. Modifying its schema would break the accepted economic separation and the
Python validator contract.

## CURRENT ECONOMIC MODEL

### StrategyIntent

Source: `trading-core/.../domain/economic/StrategyIntent.java`.

```text
instrument
side = BUY or SELL
orderType = MARKET or LIMIT
quantity > 0
decisionAt
```

The current type does not contain a market UUID, horizon, exit policy, strategy
hypothesis ID, strategy version, account, Risk policy, or a dedicated
fingerprint field. The application request supplies `marketId`, entry cutoff,
exit cutoff, research cutoff, transaction costs, execution model version, and
transaction-cost model version around the intent.

Therefore multiple intents can legitimately share one FeatureSnapshot/T0:
different side, quantity, order type, or decision/exit policy. The current
architecture supports this conceptually, but the future dataset contract still
needs an explicit canonical hypothesis/policy identity outside the low-level
intent fields.

### SimulatedExecution

Source: `trading-core/.../domain/economic/SimulatedExecution.java`.

```text
side
requestedQuantity
filledQuantity
averageExecutionPrice
executedAt
status
executionModelVersion
sourceFingerprint
fills
reason
executionFingerprint
```

It exposes `unfilledQuantity()`. Constructor invariants require filled quantity
to be between zero and requested quantity, require `FILLED` to fill all, require
`PARTIALLY_FILLED` to retain a positive remainder, and require fill quantities
to sum to filled quantity.

Historical V1 execution is MARKET-only, zero modeled latency, visible-depth
book walking at depth 25, and strict `knownAt <= executionCutoff` replay. The
result retains sequence, source evidence, provider, symbol, normalization, and
visible levels in `ReferenceMarketState`.

### EconomicResult

Source: `trading-core/.../domain/economic/EconomicResult.java` and
`EconomicLifecycleEvaluator.java`.

```text
status = AVAILABLE, DEGRADED, INSUFFICIENT_DATA, STALE, UNSUPPORTED,
         INVALID_INPUT, EXECUTION_UNAVAILABLE
completeness = FULLY_COSTED, PARTIALLY_COSTED, GROSS_ONLY,
               INSUFFICIENT_EVIDENCE
referenceNotional
executionNotional
legacy execution field, normally the entry execution for lifecycle results
TransactionCosts
StrategyIntent
entry and exit ReferenceMarketState
entry and exit SimulatedExecution
aggregate executionStatus
referenceMarketReturn
grossStrategyPnl
executionAdjustedPnl
netPnl
netReturn
executionModelVersion
transactionCostModelVersion
calculationFingerprint
reason
closedQuantity
residualExposure
```

`HistoricalEconomicEvaluationService` performs:

```text
entry replay at entryIntent.decisionAt
  -> opposite-side MARKET exit intent at exitExecutionCutoff
  -> exit replay
  -> EconomicLifecycleEvaluator
```

The service rejects cost evidence known after the research cutoff. The evaluator
checks market/instrument/product identity, sides, time ordering, state known-at
eligibility, policy versions, exit quantity not exceeding entry exposure, and
cost evidence eligibility at both execution times.

There is no EconomicResult persistence, repository, JSONL export, dataset
builder integration, or production caller that connects this service to
Market Intelligence research samples.

`netPnl` is only authoritative when the lifecycle is fully closed and
`FULLY_COSTED`. `netReturn` is currently required to follow `netPnl`, but the
lifecycle evaluator does not currently calculate a non-null net return. This is
an implementation-contract gap for the future economic dataset, not a reason to
change `research-dataset-v1`.

### Partial lifecycle semantics

| State | Current behavior | Research interpretation |
|---|---|---|
| Entry fully filled, exit fully filled | `AVAILABLE` only when all applicable costs are fully known. | Eligible for fully-net analysis only if `FULLY_COSTED`. |
| Partial entry | Exit quantity is based on filled entry quantity; lifecycle is not fully closed because entry is not `FILLED`; result is `DEGRADED` when economic values exist. | Valuable execution sample; not a fully executed intent. Preserve requested, filled, and residual quantities. |
| Partial exit | Exit quantity is below entry exposure; result is `DEGRADED`, with positive residual exposure. | Valuable execution/liquidity sample; not a closed strategy result. |
| Zero entry fill | `EXECUTION_UNAVAILABLE`, closed quantity zero, no exposure. | Preserve as an execution failure/exclusion category, not as a zero-PnL trade. |
| Exit evidence unavailable | `EXECUTION_UNAVAILABLE`, closed quantity zero, residual exposure equal to entry filled quantity. | Preserve as censored/unavailable exit evidence; never silently discard. |
| Applicable cost unavailable | `TransactionCosts` can represent `UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`, or `UNSUPPORTED` without an amount; completeness is not full. | Execution-adjusted/gross views may remain useful; net profitability is unavailable. |
| Funding not applicable | `NOT_APPLICABLE` contributes zero explicitly. | Distinct from unavailable funding. |

The current evaluator returns `DEGRADED` for an evaluable but not fully closed
or fully costed lifecycle. `CostCompleteness` has no separate `EXECUTION_UNAVAILABLE`
value; execution availability is a separate result status.

## CARDINALITIES

```text
FeatureSnapshot -> ObservedMarketOutcome = 1 -> N
  one objective snapshot can have independent 1h, 4h, and 24h outcomes;
  corrections create additional immutable outcome versions.

FeatureSnapshot -> StrategyIntent = 1 -> N
  direction, quantity, order type, hypothesis, and exit policy may differ.

StrategyIntent -> SimulatedExecution = 1 -> N over evaluation runs/policies
  one entry intent has an entry execution and, for a lifecycle, an opposite-side
  exit execution; changed evidence or execution policy produces a distinct result.

StrategyIntent -> EconomicResult = 1 -> N
  changes in exit policy, execution model, cost model, admissible evidence, or
  evaluation run change semantic result identity.

FeatureSnapshot -> EconomicResult = 1 -> N
  the result belongs to a strategy hypothesis evaluated against the state, not
  to the market state itself.

ObservedMarketOutcome -> EconomicResult = 0 -> N
  an EconomicResult can be computed from entry and exit Order Book replay plus
  cost evidence without an ObservedMarketOutcome. An outcome can be joined as
  an objective comparison, but it is not a prerequisite for economic accounting.
```

The required size test is therefore valid: one FeatureSnapshot may support
multiple LONG/SHORT intents, quantities, and horizons, each producing different
depth consumption, executions, and economics while reusing the same market
truth.

## DATASET OPTIONS

### OPTION A - Extend ResearchSample

Concept:

```text
ResearchSample = FeatureSnapshot + ObservedMarketOutcome + EconomicResult
```

Advantages:

- one apparent row shape for a notebook;
- simple initial join for one strategy and one size;
- existing manifest/export machinery could be reused superficially.

Disadvantages:

- breaks the current one-snapshot-per-horizon V1 meaning;
- cannot represent N strategy intents without duplicating objective features and
  outcomes or changing row cardinality incompatibly;
- couples objective outcomes to strategy-specific execution and cost versions;
- makes economic sample exclusion contaminate objective sample eligibility;
- causes dataset explosion when direction, size, horizon, and hypotheses vary;
- risks turning missing economic costs into objective sample absence;
- would require changing the Python `research-dataset-v1` validator and ADR-062.

Cardinality: structurally wrong for the required 1-to-N strategy relation unless
the existing sample ceases to mean what ADR-062 defines.

Versioning: mixes projection, outcome, strategy, execution, and cost versions in
one contract and makes independent reuse difficult.

Verdict: **REJECTED**.

### OPTION B - Separate EconomicResearchSample containing market facts

Concept:

```text
EconomicResearchSample = FeatureSnapshot + StrategyIntent
                         + entry/exit executions + EconomicResult
                         + economic dataset definition
```

Advantages:

- explicit separation from objective `ResearchSample`;
- convenient row for one economic experiment;
- supports partial execution and cost completeness in one envelope.

Disadvantages:

- embeds or duplicates the same FeatureSnapshot for every strategy/size row;
- still couples market truth materialization to each economic hypothesis;
- creates large exports and duplicate feature payloads;
- encourages a single mixed row to be treated as both feature input and target;
- makes correction and objective-outcome reuse less normalized.

Cardinality: can model 1 FeatureSnapshot to N economic samples, but only by
duplicating or embedding the immutable market fact repeatedly.

Versioning: better than A, but the economic row still carries two independent
contracts and invites accidental coupled version changes.

Verdict: **NOT PREFERRED**. It is acceptable only as a derived convenience view
over normalized source bundles, not as the canonical boundary.

### OPTION C - Independent joinable datasets

Concept:

```text
Market Research Dataset
  FeatureSnapshot identity/fingerprint
  + ObservedMarketOutcome selections

Economic Strategy Dataset
  FeatureSnapshot identity/fingerprint/reference
  + StrategyIntent candidate
  + entry/exit SimulatedExecution
  + EconomicResult
  + economic dataset definition
```

Advantages:

- preserves the exact ADR-062 objective V1 contract;
- naturally represents one FeatureSnapshot to N economic samples;
- avoids duplicating authoritative market truth in each economic artifact;
- allows Python to discover hypotheses and submit candidate intents;
- lets Java evaluate execution/economics without embedding Python strategy logic;
- permits economic research without an objective outcome and permits later
  objective joins without making them prerequisites;
- versions market projection/outcome and economic policy independently;
- makes missing costs explicit in the economic dataset without dropping the
  objective market sample;
- supports compact normalized JSONL plus optional derived analytical rows.

Disadvantages:

- requires stable cross-dataset identity and a join operation in Python;
- requires a second manifest/export contract;
- notebooks must load two validated bundles when comparing objective and
  economic behavior;
- requires explicit handling of economic rows that reference unavailable or
  corrected market facts.

Cardinality: correct. One immutable market snapshot/outcome population is
reusable by N candidate intents and economic results.

Versioning: cleanly separates feature/outcome versions from strategy,
execution, cost, evidence, and economic model versions.

Verdict: **PREFERRED**.

### OPTION D - Single normalized multi-file research bundle

This is a transport refinement of Option C rather than a new semantic model:

```text
market-samples.jsonl
economic-intents.jsonl
economic-results.jsonl
dataset-manifest.json
```

It is useful for atomic export and Python ergonomics, but the files remain
independent datasets with explicit join keys. It must not become a new
production aggregate or a mutable warehouse schema.

Verdict: **USE AS AN EXPORT FORM OF C**, not as a different domain boundary.

## RECOMMENDATION

```text
Preferred architecture = C
Independent joinable objective and economic datasets
```

Why:

1. ADR-064 already accepts conceptual separation and explicitly prohibits
   retrofitting EconomicResult into `research-dataset-v1`.
2. The required cardinality is one FeatureSnapshot to many StrategyIntents and
   EconomicResults.
3. Economic execution can exist without an ObservedMarketOutcome, while an
   objective dataset must remain strategy-independent.
4. Size, execution model, cost policy, and hypothesis identity must vary without
   duplicating or redefining market truth.
5. The design supports the accepted Java/Python workflow:
   factual Java datasets -> Python hypotheses -> Java evaluation -> Python
   statistics.

Why not A: it violates the current V1 semantic boundary and cannot represent N
strategy results without dataset explosion or schema breakage.

Why not B: it is semantically viable but duplicates immutable market facts and
couples a reusable economic result to a convenience row shape. It can be a
derived view, not the canonical dataset boundary.

Why not D as a separate choice: it is a transport/export packaging choice over
Option C, not a domain model.

## PROPOSED ECONOMIC SAMPLE IDENTITY

One canonical economic sample should be identified by deterministic semantic
material, not a database UUID or export timestamp:

```text
economicDatasetDefinitionVersion
marketId
T0 / StrategyIntent.decisionAt
FeatureSnapshot identity fingerprint
FeatureSnapshot content fingerprint when selected as input
strategyHypothesisId and strategyPolicyVersion, when supplied
StrategyIntent semantic fingerprint
exit policy/horizon identity
entry execution fingerprint
exit execution fingerprint
EconomicResult calculation fingerprint
execution model version
transaction-cost model version
research cutoff
selected objective outcome fingerprint(s), only when this economic experiment
  explicitly joins objective outcomes
```

The canonical StrategyIntent fingerprint must include instrument, side, order
type, quantity, decision time, and any future immutable intent policy fields.
Quantity is mandatory in identity because visible depth makes economics
non-linear. Order type remains in identity even though V1 accepts MARKET only,
so future LIMIT semantics cannot collide with MARKET results.

The exit horizon belongs to the immutable exit policy/dataset definition. It
should not be copied into several unrelated fields; the derived `Texit - T0`
must agree with that policy. Random IDs, `createdAt`, and operational database
IDs must not change identity.

## PROPOSED ECONOMIC DATASET DEFINITION

Minimum immutable definition:

```text
economicDatasetDefinitionVersion
market universe and product/instrument scope
T0 population interval and sampling cadence
FeatureSnapshot identity/projection version and evidence policy
strategy hypothesis ID/version or externally supplied intent population version
direction policy
quantity/size policy and size version
entry execution policy/version
exit policy/version and horizon
execution model version
transaction-cost model version
fee policy/scope and evidence policy
funding policy and applicability
research cutoff rule
required cost completeness
accepted execution statuses
selected objective outcome definition/version/fingerprints when joined
source market/evidence population fingerprint
```

Do not embed Python strategy code in Java. A hypothesis is identified by a
stable external ID and version, while the actual provider-neutral StrategyIntent
candidate records the concrete action Java must evaluate. Java should validate
the candidate and preserve the hypothesis metadata; Python remains free to
discover or modify hypothesis logic offline.

## JAVA / PYTHON BOUNDARY

Java owns:

- provider-neutral historical evidence eligibility;
- `knownAt` and cutoff rules;
- FeatureSnapshot and objective outcome truth;
- StrategyIntent validation contract;
- reference market state;
- visible-depth fills, VWAP, precision, and remaining quantity;
- fee and funding evidence application;
- cost completeness;
- gross, execution-adjusted, and authoritative net economics;
- deterministic fingerprints and provenance;
- exclusion and missingness accounting for factual exports.

Python owns:

- validated bundle loading;
- hypothesis discovery and candidate StrategyIntent generation;
- feature selection and statistical transforms;
- chronological splits, purge, and embargo;
- walk-forward and OOS evaluation;
- aggregate economic metrics, break-even and cost sensitivity;
- stress analysis, visualization, and ML experiments.

```text
Python may generate StrategyIntents = YES
Java remains accounting authority = YES
```

Python must not recalculate canonical fills, VWAP, commissions, funding, PnL,
cost completeness, or temporal eligibility.

## RECOMMENDED RESEARCH LOOP

```text
1. Java exports validated FeatureSnapshot/objective market truth.
2. Python/Jupyter explores hypotheses and produces provider-neutral candidate
   StrategyIntent artifacts with hypothesis ID/version.
3. Java validates candidates against the selected FeatureSnapshot population.
4. Trading Core replays entry and exit MARKET executions against strict Order
   Book evidence.
5. Trading Core applies eligible fee/funding evidence or explicit unavailable /
   not-applicable states.
6. Java exports a versioned economic result bundle, preserving every candidate,
   execution status, residual exposure, cost status, and exclusion reason.
7. Python joins objective and economic bundles by stable snapshot/outcome
   identities when the experiment requires both.
8. Python runs chronological OOS, purged walk-forward, robustness, and only
   later ML analysis.
```

The loop should be candidate-driven and on-demand/cacheable, not a precomputed
Cartesian product of every strategy, size, and horizon.

## CURRENT DATA CLOCK

Read-only measurements from the local PostgreSQL containers at
`2026-10-17T16:23:10Z`:

| Dataset | Count | Earliest | Latest | Notes |
|---|---:|---|---|---|
| Historical OHLC | 821 | 2026-09-12 16:00Z | 2026-10-17 16:00Z | 369 fifteen-minute, 242 one-hour, 210 four-hour rows. |
| Historical Order Book | 2,605,339 | occurred 2026-10-17 12:26:58Z; known 12:26:58Z | occurred 14:56:26Z; known 15:23:49Z | 37 snapshots, 2,605,302 deltas; one observed sequence. |
| FeatureSnapshot | 19 | 2026-10-16 22:00Z | 2026-10-17 16:00Z | All `feature-projection-v1`, `STRICT_OBSERVED`, one BTC/USD perpetual market. |
| ObservedMarketOutcome | 48 | 2026-10-15 10:00Z | 2026-10-17 15:00Z | 1h: 24 available, 4 incomplete, 1 unavailable; 4h: 15 available, 4 incomplete. |
| ResearchSample | not persisted | - | - | No database table; built in memory/export-only. |
| EconomicResult | not persisted | - | - | No database table or export integration. |
| HistoricalFeeSchedule | 0 | - | - | Table exists, no rows. |
| Funding Evidence | 0 | - | - | Table exists, provider acquisition remains blocked. |

The runtime market represented by the current feature/order-book chain is:

```text
market = BTC/USD
product = LINEAR_PERPETUAL
provider = KRAKEN
provider symbol = PF_XBTUSD
```

The Order Book history is currently only a short live capture window. The OHLC
history is broader but sparse for the bounded research chain. No durable
EconomicResult population exists.

## ECONOMIC REPLAY COVERAGE

The following is a conservative timestamp-window calculation from the 19
FeatureSnapshot cutoffs and the trusted depth-25 Order Book known-at range. It
does not claim that a full replay service invocation was run for each row; it
only identifies candidates whose timestamps fit the captured evidence window.

| Horizon | Candidate T0 snapshots | Entry replay timestamp-compatible | Exit replay timestamp-compatible | Economic-evaluable now |
|---|---:|---:|---:|---:|
| 1h | 19 | 4 | 2 | 0 materialized; 2 could be replay candidates pending cost/economic export wiring |
| 4h | 19 | 4 | 0 | 0 |
| 24h | 19 | 4 | 0 | 0 |

The four entry-compatible T0s are the hourly snapshots from 13:00 through
16:00Z on 2026-10-17. Only 13:00 and 14:00 have a one-hour exit cutoff before
the latest known Order Book evidence. No 4h or 24h exit has sufficient captured
Order Book time. This is not a dataset-ready sample count.

## CURRENT RESEARCH CAPABILITIES

```text
Market movement research          = LIMITED
Execution research                = LIMITED
Execution-adjusted economic       = LIMITED in domain, NOT READY as dataset
Fully-net economic research       = NOT READY
OOS research                      = NOT READY
Walk-forward                      = NOT READY
ML                                = NOT READY
```

Reasoning:

- Objective research has 19 feature snapshots and 48 outcomes, enough for
  smoke/exploratory inspection only, not meaningful statistical inference.
- Execution replay has only four timestamp-compatible entry candidates and two
  one-hour entry/exit windows in the current capture.
- No fee schedule rows and no funding rows prevent fully costed perpetual
  economics.
- The existing Python baseline itself requires 90 days, 5,000 eligible samples,
  500 approximate non-overlapping 4h windows, 100 test windows, and no gap over
  the 15-minute cadence. The current runtime is far below those gates.

## COST COMPLETENESS

```text
Commission evidence = no HistoricalFeeSchedule rows; acquisition/runtime truth unavailable
Funding evidence = zero rows; PROVIDER_BLOCKED
Fully-costed historical perpetual samples currently possible = NO
Execution-adjusted samples possible = YES as an explicitly non-net domain view,
  but not currently materialized at research scale
```

The economic model correctly distinguishes `AVAILABLE`, `UNAVAILABLE`,
`NOT_APPLICABLE`, `INSUFFICIENT_EVIDENCE`, and `UNSUPPORTED`. It does not turn
missing commission or funding into zero. A derivative holding interval that
crosses applicable funding events cannot be `FULLY_COSTED` without Funding
Evidence.

Future fee sensitivity should be represented as an explicit versioned
`ResearchFeePolicy` or `ResearchCostScenario`, clearly marked as an assumption
and never confused with provider evidence. This is not implemented here.

## TEMPORAL MODEL

```text
T0 = FeatureSnapshot.evaluationCutoff = StrategyIntent.decisionAt
Tentry = T0 under the versioned zero-modeled-latency policy
Texit = HistoricalEconomicEvaluationRequest.exitExecutionCutoff
Cost cutoff = researchCutoff, plus eligibility at both entry and exit times
```

Feature evidence must satisfy `knownAt <= T0`. Entry Order Book replay must
satisfy its own execution cutoff. Exit replay may use future evidence at Texit,
but it cannot feed back into T0 features. Cost evidence must be known by the
research cutoff and effective at both execution times.

Current leakage controls are strong in the Java domain, but the exported JSONL
places `features` and `outcome` in the same row. The Python consumer currently
selects the outcome explicitly, but a future economic bundle should use clear
namespaces such as `market`, `features`, `objectiveOutcome`, `strategy`,
`entryExecution`, `exitExecution`, `costs`, `economic`, and `metadata` so future
consumers cannot accidentally train on future/economic fields.

## FEATURE READINESS

```text
FeatureProjectionV1 sufficient for first economic experiment = YES
Order Book liquidity currently inside FeatureProjection = NO
FeatureProjectionV2 needed now = NO
```

`feature-projection-v1` already provides stable T0 features and is the correct
baseline for validating the economic dataset boundary. There is no concrete
need to add Order Book liquidity to V2 before proving:

1. strict economic sample identity;
2. execution and cost completeness handling;
3. export/join semantics;
4. a simple economic baseline.

Order Book liquidity can be joined as an independent artifact or added later in
a new projection version for a specific hypothesis. It should not be added
merely because the evidence exists.

## POSITION SIZE

```text
Recommended V1 sizing policy = fixed base quantity with an explicit policy version
Size part of economic sample identity = YES
```

A fixed base quantity avoids mutable account state and makes comparisons
interpretable. It must be chosen from a prior calibration window or bounded
research policy, not from future visible depth at each T0. The dataset must
exclude or preserve `INSUFFICIENT_DEPTH` outcomes rather than silently increase
size or extrapolate liquidity.

Quantity cannot be omitted: 0.1 BTC and 10 BTC consume different visible levels,
produce different VWAPs, may have different partial fills, and therefore do not
share an EconomicResult identity even if the FeatureSnapshot is identical.

Percentage-of-liquidity or account-equity sizing should be later experiments,
with their own policies and identities. Account-coupled risk sizing is outside
the first economic truth dataset.

## EXPORT

```text
Recommended format = separate deterministic JSONL files in one atomic bundle
```

Recommended V1 files:

```text
market-samples.jsonl       # existing objective bundle remains compatible
economic-intents.jsonl     # optional candidate population
economic-results.jsonl     # one row per evaluated candidate/result
economic-dataset-manifest.json
README.md
```

This is Option C in transport form. It avoids duplicating feature payloads while
remaining easy to load in Python. Nested domain-oriented JSON is preferable for
authoritative export because it preserves status, provenance, and fills. A
derived flat analytical frame may be created in Python after validation; it must
not replace the canonical nested facts.

Minimum economic manifest requirements:

- export schema and economic dataset definition versions;
- population and FeatureSnapshot identity selection;
- hypothesis/intent population version;
- feature projection and objective outcome versions when used;
- execution, transaction-cost, fee, funding, and sizing policy versions;
- source evidence and model fingerprints;
- sample/result counts;
- counts by execution status and cost completeness;
- explicit exclusion/censoring categories;
- source bundle fingerprints and code/repository version;
- ordered row/result fingerprints and file checksums.

Missingness representation:

- preserve `UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`, `UNSUPPORTED`, and
  `NOT_APPLICABLE` as statuses;
- do not emit null numeric values as implicit zero;
- keep partial fills and residual exposure in result rows;
- include every candidate in accounting, including excluded/censored rows.

## PERSISTENCE

```text
Persist EconomicResult = LATER / export-only for V1
Persist EconomicResearchSample = NO as a canonical production table
```

The current EconomicResult is deterministically recomputable from the selected
intent, evidence, execution states, cost evidence, and policy versions. The
first implementation should therefore be an on-demand, deterministic,
cacheable/exportable evaluation rather than a new database migration. A
content-addressed export bundle can preserve results for reproducibility without
creating a mutable production table.

Persistence becomes justified when there is a demonstrated need for:

- large repeated candidate evaluations;
- durable experiment listing/materialization;
- asynchronous evaluation lifecycle;
- cross-run result retrieval;
- operational audit independent of source evidence retention.

Those needs require a separate Story and schema decision.

## FIRST ECONOMIC EXPERIMENT

```text
Name = Always-LONG visible-depth execution-adjusted baseline
Hypothesis = At every eligible BTC/USD perpetual FeatureSnapshot T0, buy a
             fixed quantity and close at the selected horizon.
Market = BTC/USD, KRAKEN LINEAR_PERPETUAL, PF_XBTUSD
Direction policy = always LONG / BUY entry, opposite-side SELL exit
Position sizing = fixed base quantity, policy-versioned, pre-calibrated below
                  the selected visible-depth eligibility threshold
Entry policy = zero-modeled-latency MARKET visible-depth replay
Exit policy = opposite MARKET at one independently defined horizon
Horizon = PT1H first; PT4H and PT24H are separate dataset definitions later
Required data = FeatureSnapshot, valid entry and exit Order Book replay,
                provenance, explicit execution status
Required cost completeness = not required for the first execution-adjusted
                             baseline, but the result must visibly be non-net
Baseline comparisons = no-trade, objective forward return, always-SHORT,
                       and fixed reference-price movement
Success/failure interpretation = validate dataset identity, replay determinism,
                                  missingness, and temporal/statistical behavior;
                                  not profitability
```

This experiment should remain blocked until the evidence window is materially
larger and the fee/funding statuses are explicit. It is deliberately
non-ML and falsifiable.

## STATISTICAL READINESS

```text
Descriptive analysis = LIMITED; objective counts and coverage can be inspected
Exploratory economic analysis = NOT READY at meaningful scale; only two current
                                1h timestamp-compatible lifecycle windows
OOS = NOT READY
Walk-forward = NOT READY
ML = NOT READY
Primary limiting factor = insufficient continuous, multi-clock, cost-complete
                          historical evidence
```

The existing Python rule of no random shuffle remains correct. Horizons must be
separate experiment definitions. For a horizon H, training rows whose outcome
or economic windows overlap the test start require purging; an embargo should
be added when the strategy/evidence process has additional lookback or latency
dependencies. Raw row count must be reported separately from effective
non-overlapping sample count.

## RESEARCH CAPABILITY LEVELS

```text
LEVEL 0 - Objective market movement
  Current state: LIMITED; available for smoke/exploratory analysis only.

LEVEL 1 - Execution-adjusted economics
  Current state: DOMAIN-SUPPORTED but runtime/data LIMITED; two possible 1h
  timestamp windows, no exported economic population.

LEVEL 2 - Partially costed economics
  Current state: DOMAIN-SUPPORTED by explicit cost statuses, but no runtime
  fee/funding evidence and no materialized dataset.

LEVEL 3 - Fully costed economics
  Current state: NOT READY; zero fee schedules and zero Funding Evidence.
```

The current BTC/USD linear-perpetual runtime should be described as Level 0
limited and Level 1/2 contract-capable, not as economically research-ready.

## SECOND-PASS FALSIFICATION

The recommendation was independently checked against the requested failure
modes:

| Risk | Result |
|---|---|
| ResearchSample cardinality mismatch | Confirmed risk for A; C preserves one-to-many. |
| FeatureSnapshot reused across strategies | Supported by immutable identity and required by C. |
| Outcome treated as strategy result | Prevented by ADR-059/064 and current fields. |
| EconomicResult treated as objective truth | Prevented by separate Trading Core economic authority. |
| Size omitted from identity | Current StrategyIntent includes quantity; future identity must retain it. |
| Horizon duplicated inconsistently | Current intent lacks horizon; request owns exit cutoff. Future contract must make one exit policy authoritative. |
| Execution model version omitted | Present in SimulatedExecution/EconomicResult and must enter dataset identity. |
| Cost model version omitted | Present in TransactionCosts/EconomicResult and must enter dataset identity. |
| Missingness lost during export | Existing V1 preserves objective missingness; economic export must preserve cost/execution status separately. |
| UNAVAILABLE converted to zero | Current ExplicitCost rejects amounts for unavailable states; NOT_APPLICABLE is explicit zero only. |
| Partial fills discarded | Current model preserves requested/filled/remainder; future export must not exclude silently. |
| Residual exposure discarded | Current EconomicResult exposes residualExposure; include it in economic rows. |
| Future evidence leakage | Current cutoffs and known-at checks are strong; export namespaces remain a future safety requirement. |
| Economic result persistence explosion | Avoided by on-demand/export-only V1 and candidate-driven evaluation. |
| Python accounting duplication | ADR-064 explicitly rejects it; Java remains authority. |
| Strategy logic embedded in Java | Avoided by external hypothesis IDs and provider-neutral candidate intents. |
| Premature FeatureProjectionV2 | Rejected; V1 is sufficient for the first boundary experiment. |
| research-dataset-v1 regression | Avoided by C and separate manifests/files. |
| Objective/economic coupling | Avoided by independent joinable datasets. |

The second pass did not falsify Recommendation C.

## BLOCKERS

1. `EconomicResult` has no persistence/export contract and no Market Intelligence
   population integration.
2. `StrategyIntent` has no hypothesis identity/version, exit policy, or market
   identity field; the surrounding request supplies some of these but the
   future dataset contract must make them explicit and deterministic.
3. The aggregate economic lifecycle is partial: `netReturn` is not calculated,
   and the result mixes legacy single-leg compatibility with lifecycle fields.
4. The runtime has zero `HistoricalFeeSchedule` rows.
5. Funding Evidence remains provider-blocked with zero rows.
6. Current Order Book history is only a short live window and supports no 4h or
   24h exit replay for the available FeatureSnapshots.
7. Current FeatureSnapshot/outcome populations are far below meaningful
   descriptive, OOS, walk-forward, or ML sample sizes.
8. ADR-061 and ADR-062 are still marked `Proposed` even though ADR-064 relies on
   their boundaries.

## NEXT STORY

```text
Title = Provider-Neutral Economic Research Dataset Contract

Objective = define the separate joinable economic research boundary without
             changing research-dataset-v1 or implementing a production dataset
             migration.

Exact scope =
  - define EconomicResearchDatasetDefinition and manifest semantics;
  - define candidate StrategyIntent/hypothesis identity and exit policy;
  - define normalized joins to FeatureSnapshot and optional outcomes;
  - define economic sample identity and version material;
  - define execution/cost status and exclusion accounting;
  - define deterministic JSONL bundle shape and Python consumption contract;
  - define on-demand/export-only V1 lifecycle;
  - add no production migration, persistence, replay, fee acquisition, funding
    acquisition, FeatureProjectionV2, or Python accounting.

Why now = the current replay/economic classes exist, but their dataset boundary
          is not defined. Fee/funding readiness and future economic export need
          this contract before further implementation.
```

The Story should first reconcile the Proposed status of ADR-061/062 or record
the new accepted decision as the successor contract.

## DO NOT BUILD YET

- Do not extend `ResearchSample` or modify `research-dataset-v1`.
- Do not add `EconomicResearchSample` as a production aggregate before the
  normalized join contract is accepted.
- Do not create a database migration or persist EconomicResult in V1.
- Do not change `FeatureProjectionV1` or create V2 without a concrete hypothesis.
- Do not add Order Book liquidity to V1 feature truth merely for convenience.
- Do not treat current/predicted funding as realized funding.
- Do not fill missing fees/funding with zero.
- Do not discard partial fills, residual exposure, or unavailable exits.
- Do not use the operational PAPER adapter as a historical simulator.
- Do not move fill, fee, funding, PnL, or completeness accounting to Python.
- Do not precompute the Cartesian product of all strategies, sizes, and horizons.
- Do not claim net profitability, OOS validity, walk-forward validity, or ML
  readiness from the current runtime counts.

## FINAL DECISION

```text
RECOMMENDATION = C
Independent joinable objective and economic datasets
```

Keep `research-dataset-v1` as the immutable market-truth dataset:

```text
FeatureSnapshot + explicitly selected ObservedMarketOutcome
```

Create a future separate, versioned economic export contract:

```text
FeatureSnapshot identity/reference
+ candidate StrategyIntent and hypothesis identity
+ entry/exit SimulatedExecution
+ EconomicResult
+ cost/execution completeness
+ policy/evidence/model provenance
```

The economic dataset may reference objective outcomes but must not absorb them
or require them for economic accounting. It must preserve all statuses and
exclusion counts, including the honest state:

```text
FeatureSnapshot = AVAILABLE
ObservedMarketOutcome = AVAILABLE
Entry/exit execution = AVAILABLE
ExecutionAdjustedPnL = AVAILABLE
Commission = UNAVAILABLE
Funding = UNAVAILABLE
NetPnL = UNAVAILABLE
```

This architecture satisfies the cardinality, temporal honesty, economic
honesty, provider-neutrality, reproducibility, and Python workflow requirements
without reopening the accepted separation between market truth, objective
outcomes, strategy hypotheses, execution replay, and economic accounting.
