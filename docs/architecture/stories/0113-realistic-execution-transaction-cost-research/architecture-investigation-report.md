# Story 0113 - Realistic Execution and Transaction Cost Research
## Architecture Investigation Report

## Scope

This report is an architecture and research investigation only. No production
code, migration, API, FeatureProjection version, Python experiment, collector,
or runtime configuration was changed during this investigation. No commit,
push, merge, restart, reset, stash, or database mutation was performed.

## Evidence Classification

- **FACT:** directly observed in current source, runtime, or accepted ADRs.
- **INFERENCE:** conclusion supported by multiple repository facts.
- **RECOMMENDATION:** proposed future design decision, not current behavior.
- **UNKNOWN:** not established by the current repository evidence.

## Repository and Runtime Baseline

### Git

- HEAD: `2d7a23331f1d40e7f030a9af84c486c518c7db96`
- Branch: `main`
- `origin/main`: `2d7a23331f1d40e7f030a9af84c486c518c7db96`
- `git fetch origin`: previously attempted and timed out after 120 seconds.
- Existing worktree changes were preserved.
- DevLog applicability check was attempted and failed with exhausted provider
  quota (`insufficient_quota`). The repository was used as the current-state
  authority.

### Current passive data clocks

Runtime measurements during this investigation:

- OHLC: `805` persisted evidence rows and scheduler logs show continued capture.
- Order Book: `905,480` persisted evidence rows in the latest measurement,
  including `905,469` DELTA rows and `11` SNAPSHOT rows; the count was still
  growing during the investigation.
- Funding Evidence: `0` rows.
- FeatureSnapshot: `16` rows, `16` unique identity fingerprints, `16` unique
  snapshot fingerprints, all `STRICT_OBSERVED` and `feature-projection-v1`.
- Analysis executions: `17 COMPLETED`, `1 FAILED`.

The single failed execution is the previously known required
`trend-context-analysis` timeout/context-unavailable path. It is not evidence
of a persistence or temporal-leakage defect.

### Runtime migration drift

- Market Data Flyway runtime is at v7, including Order Book and Funding schema.
- Market Intelligence runtime is at v16.
- Broker Service runtime is at v2.
- Broker source contains V3/V4 historical fee-schedule migrations, but the
  runtime database has no `historical_fee_schedule` table and has not applied
  those migrations.

This is a **FACT** and a material readiness gap. It was not corrected because
this Story is investigation-only.

### Order Book temporal clarification

The current Order Book rows have `knownAt > occurredAt` for most observations.
Observed delay distribution was approximately:

- minimum: 6.978 ms;
- median: 20.63 s;
- maximum: 110.004 s.

This is expected under the repository contract: `occurredAt` is the provider
event time and `knownAt` is the local acceptance time. The domain explicitly
rejects `knownAt < occurredAt`, not the reverse. Therefore the previously stated
condition "no knownAt > occurredAt" was not a valid integrity requirement.

Order Book DELTA payloads often contain an empty bid or ask side. This is
consistent with sparse updates and is not by itself missing evidence. The
current repository decodes empty sides as empty update lists.

DELTA-only sequence ordinals had no duplicates in the inspected runtime data.

## Current Architecture Map

### Trading Core

Trading Core owns:

- execution intents and attempts;
- broker order state and fills;
- execution state transitions and idempotency;
- PAPER settlement and account mutation;
- account-scoped positions and realized P&L projection;
- deterministic Risk orchestration.

Relevant current boundaries include:

- `execution/application/pipeline/`;
- `execution/domain/aggregate/ExecutionIntent`;
- `execution/domain/aggregate/ExecutionAttempt`;
- `execution/domain/aggregate/BrokerOrder`;
- `execution/application/service/PaperSettlementService`;
- `execution/infrastructure/adapter/SimulatedExecutionAdapter`.

### Broker Service

Broker Service owns:

- provider-neutral live order/fill contracts;
- broker credentials and provider authentication;
- Kraken-specific order and position adapters;
- provider-reported execution facts;
- the source boundary for account/provider fee schedules.

Observed live fill fees are facts about an actual execution. They are not a
historical simulation fee policy.

### Market Data

Market Data owns:

- provider acquisition and provider-specific symbols;
- normalized market facts;
- OHLC evidence;
- Order Book evidence and replay;
- future Funding Evidence acquisition when the provider blocker is resolved.

It must remain the owner of historical provider truth, not strategy economics.

### Market Intelligence

Market Intelligence owns:

- deterministic context assembly and analysis;
- liquidity and spread interpretation;
- FeatureSnapshot projection;
- objective `ObservedMarketOutcome` evaluation;
- research dataset construction and export.

`ObservedMarketOutcome` is objective market evolution and must not become a
strategy-specific execution or P&L result.

### Python Research

Python currently consumes validated Java-produced dataset bundles. It owns
statistical preprocessing, chronological splits, model experiments, and
visualization. It is not authoritative for market facts, fills, fees, funding,
Risk, or economic accounting.

## Existing Execution Behavior

### PAPER execution

`SimulatedExecutionAdapter`:

- reads the current Market Data catalogue and current price snapshot;
- fills market BUY at current ask;
- fills market SELL at current bid;
- accepts a LIMIT order immediately when the current quote satisfies its limit;
- creates a synthetic acknowledged order;
- does not model historical evidence, latency, partial fills, queue position,
  depth consumption, or fill uncertainty;
- does not apply a historical fee schedule.

This is a bounded operational PAPER behavior, not a historical simulator.

### PAPER settlement

`PaperSettlementService`:

- owns PAPER account mutation;
- applies fills, balances, trades, positions, equity, and realized P&L;
- subtracts the fill fee when present;
- handles spot and linear-perpetual settlement compatibility;
- has no Funding Evidence or funding settlement integration;
- is not connected to the economic evaluator or research dataset.

### Broker fills

`BrokerOrder.Fill` carries:

- fill ID;
- quantity;
- price;
- non-negative fee;
- execution timestamp.

It does not carry maker/taker role, fee schedule fingerprint, spread
attribution, slippage attribution, funding, latency, market-data provenance,
or a complete cost classification.

## Existing Economic Foundation

Trading Core already contains a deterministic economic foundation:

- `ReferenceMarketState`;
- `StrategyIntent`;
- `SimulatedExecution`;
- `TransactionCosts`;
- `CostEvidence`;
- `EconomicResult`;
- `MarketOrderEconomicEvaluator`.

The foundation is not an end-to-end runtime path. Repository search found no
production caller for `MarketOrderEconomicEvaluator`, no economic-result
persistence, no PAPER integration, and no dataset export of economic results.

### Current strengths

- `BigDecimal` is used for canonical monetary values.
- Market BUY/SELL execution impact is derived from ask/bid versus a reference
  price.
- Cost evidence contains fingerprint, source, effective interval, and
  `knownAt`.
- `CostEvidence.eligibleAt` enforces effective-time and known-at eligibility.
- The evaluator rejects non-MARKET order types.
- Calculation fingerprints include execution and evidence inputs.
- Missing, stale, unsupported, invalid, and degraded conditions are not silently
  converted into a successful result.

### Current gaps

- `EconomicResult` represents one simulated execution leg, not a complete entry
  and exit lifecycle.
- It exposes notionals and cost components but no explicit gross P&L, net P&L,
  net return, or completeness classification.
- `TransactionCosts` requires a numeric funding value, so it cannot itself
  distinguish `NOT_APPLICABLE` from `UNAVAILABLE` without an additional status
  boundary.
- The evaluator accepts a precomputed funding amount and fee rate; it does not
  select or calculate them from evidence repositories.
- There is no reference-market-state assembler combining strict quote, Order
  Book, constraints, cutoff, and provenance.

## Five-Layer Truth Model

The five-layer model is architecturally appropriate, with one refinement:
cost completeness must be a first-class result dimension rather than inferred
from nullable numeric values.

### 1. Reference Market State

Objective market facts available at `T0` or at the modeled execution time:

- market identity and product type;
- bid, ask, reference price;
- replayed Order Book and visible depth;
- market constraints and precision;
- evidence fingerprints and `knownAt` bounds.

Market Intelligence can assemble context, but a future deterministic execution
boundary should consume an immutable provider-neutral state object rather than
current provider state.

### 2. Strategy Intent

The existing operational `TradePlan` and execution intent carry risk,
authorization, account, and human-control concerns. They are not the smallest
research representation.

**RECOMMENDATION:** use a separate research intent concept, provisionally
`ResearchStrategyIntent`, containing:

- market identity;
- decision time `T0`;
- side;
- order type;
- quantity or notional;
- optional limit constraints;
- holding/exit policy reference;
- strategy definition/version fingerprint.

It may reuse typed side/order/quantity value objects, but must not reuse
operational TradePlan semantics wholesale.

### 3. Simulated Execution

The first policy should support MARKET orders only:

```text
intent at T0
    -> modeled submission/execution time
    -> eligible strict market state
    -> deterministic opposite-side book walking
    -> fills, filled quantity, remainder, VWAP, status
```

LIMIT execution should remain unsupported until queue position, order lifetime,
trade-through, cancellation, and fill-probability evidence exist.

### 4. Explicit Costs

Costs should be represented as separately attributable components:

- commission/fee from a versioned historical fee policy;
- funding only for eligible funding events and position exposure;
- other explicit costs only when evidence and policy exist.

Spread crossing, visible depth consumption, and modeled slippage should be
represented in execution prices or an execution-impact attribution, not charged
again as arbitrary costs.

### 5. Economic Result

The future aggregate result should combine two or more simulated execution legs
with objective market outcomes and cost evidence. It should expose separately:

- reference market return;
- gross strategy P&L;
- execution-adjusted P&L;
- commission cost;
- funding cost;
- other explicit costs;
- net P&L and net return when complete;
- execution status;
- cost completeness;
- execution, cost, strategy, and evidence fingerprints.

## Cost Completeness and Profitability Claims

The existing result statuses are useful for availability but insufficient to
make net-profit claims precise. A separate completeness vocabulary is
recommended:

```text
FULLY_COSTED
PARTIALLY_COSTED
GROSS_ONLY
EXECUTION_UNAVAILABLE
INSUFFICIENT_EVIDENCE
```

This is a **RECOMMENDATION**, not a production type currently implemented.

Required interpretation:

- `FULLY_COSTED`: execution and every applicable explicit cost are supported by
  eligible evidence and versioned policy.
- `PARTIALLY_COSTED`: some applicable costs are known, but at least one
  applicable cost is incomplete.
- `GROSS_ONLY`: market movement or strategy gross result exists, but execution or
  costs are unavailable.
- `EXECUTION_UNAVAILABLE`: a deterministic fill cannot be established.
- `INSUFFICIENT_EVIDENCE`: required market or cost evidence is absent or stale.

Only `FULLY_COSTED` may be described as a fully net result. A partial result may
discuss gross or execution-adjusted observations only with its completeness
status visible.

`NOT_APPLICABLE` and `UNAVAILABLE` must remain distinct:

- unleveraged SPOT funding: `NOT_APPLICABLE`;
- a perpetual position crossing funding events while Funding Evidence is
  unavailable: `UNAVAILABLE`;
- missing fee schedule: `UNAVAILABLE`, never zero fee by default.

## Spread and Execution Impact

### Recommended accounting rule

Use one representation for each execution effect:

```text
reference price
    -> simulated fill prices
    -> execution impact attribution
    -> explicit fee/funding costs
    -> net result
```

For a BUY, consuming the ask already embeds bid/ask crossing in the fill price.
For a SELL, consuming the bid does the same. There must be no additional
`spreadCost` subtraction for the same leg.

Spread and slippage attribution may be exposed as diagnostics, but the
diagnostics must not be added to P&L a second time.

## Order Book and Slippage

The current durable Order Book evidence and replay boundary justify a first
depth-aware MARKET execution policy, subject to its limitations:

- replay only evidence satisfying `knownAt <= executionCutoff`;
- use one valid active sequence and trusted starting snapshot;
- consume captured opposite-side levels deterministically;
- return fills, VWAP, filled quantity, remainder, source fingerprint, and model
  version;
- do not infer liquidity beyond captured depth.

If requested quantity exceeds visible depth-25 evidence:

- do not extrapolate infinite liquidity;
- do not label the result fully filled;
- return `INSUFFICIENT_DEPTH` or an explicitly partial diagnostic;
- do not call the result fully costed unless the chosen policy explicitly
  permits and labels partial execution.

Level comparison:

| Level | Semantics | Assessment |
| --- | --- | --- |
| 0 | zero slippage | useful baseline only, not a realism claim |
| 1 | fixed basis points | reproducible stress assumption, weak evidence |
| 2 | bid/ask execution | currently supported and appropriate baseline |
| 3 | visible-depth book walking | recommended first research realism level |
| 4 | calibrated impact/latency/queue model | deferred; evidence unavailable |

Visible-book consumption is a counterfactual replay of recorded liquidity. It
is not proof that the hypothetical order would have left the same levels in the
market. Results should describe this as visible-book execution, not guaranteed
market impact.

## Fees and Commissions

`HistoricalFeeSchedule` already models maker/taker rates, product/instrument,
settlement asset, effective interval, observed/known times, source, origin,
normalization version, and fingerprint. It is conceptually reusable, but its
current `brokerAccountId` scope couples it to an account-specific schedule.

**RECOMMENDATION:** retain Broker Service ownership of provider/account fee
truth and expose a provider-neutral strict read port to the economic authority.
For research not tied to an account, introduce an explicit research fee-policy
scope rather than silently selecting an account's current schedule.

The runtime fee schedule migration gap must be resolved in a separate bounded
implementation Story before fee-aware runtime research can claim operational
readiness.

For MARKET orders, taker treatment is a reasonable initial policy only when
documented and versioned. LIMIT orders must not be assumed maker orders without
fill and queue semantics.

## Funding

Funding remains `PROVIDER_BLOCKED` from Story 0114:

- runtime Funding Evidence count is `0`;
- Kraken historical endpoint verification failed for tested symbols;
- current/predicted ticker semantics did not provide a safe historical effective
  interval.

For future economic evaluation:

- SPOT funding is `NOT_APPLICABLE`;
- a derivative holding interval crossing funding events requires realized or
  otherwise semantically valid Funding Evidence;
- absent required funding evidence yields `UNAVAILABLE` or
  `INSUFFICIENT_EVIDENCE`, not zero;
- `funding_rate_prediction` may later be a decision-time feature if explicitly
  modeled, but it is not realized Funding Cost.

Actual funding cost additionally requires settlement time/rate, position
exposure at settlement, sign/direction semantics, notional/base, and provider
formula. These are not currently available as a complete research path.

## PAPER Relationship

### Options

**A - Reuse the existing PAPER adapter directly**

- Advantage: minimal duplication.
- Risk: couples historical replay to current provider snapshots, operational
  account checks, current freshness, and PAPER-specific settlement behavior.
- Decision: not recommended.

**B - Share pure deterministic execution primitives**

- Advantage: one definition of book walking, fills, VWAP, precision, and status
  transitions while allowing different evidence adapters.
- Risk: requires strict separation between pure calculation and acquisition,
  account/Risk, settlement, and persistence.
- Decision: recommended.

**C - Build an independent historical simulator**

- Advantage: local research autonomy.
- Risk: divergent fill, quantity, fee, and partial-fill semantics; duplicated
  deterministic authority.
- Decision: not recommended.

The recommended boundary is B: share pure deterministic primitives later, but do
not make research call the operational PAPER adapter or make PAPER settlement
consume historical research artifacts implicitly.

## Risk Boundary and Strategy Truth

Trading Core Risk answers whether an operational TradePlan is allowed. Research
should be able to evaluate a raw strategy hypothesis without applying a live
account RiskRule, then optionally run a separate strategy-plus-Risk experiment.

The research intent must not inherit operational authorization, account
mutation, or human-validation requirements merely to calculate a hypothetical
result.

`ObservedMarketOutcome` remains untouched. It must not gain direction, entry,
exit, fees, spread, slippage, funding, quantity, strategy success, or P&L.

## Java and Python Authority

### Java

Java should own:

- strict evidence selection;
- reference market-state assembly;
- deterministic book walking and fills;
- VWAP, rounding, and quantity/price precision;
- fee/funding application;
- cost completeness;
- economic result and calculation fingerprints.

### Python

Python should consume validated Java-produced economic samples and perform:

- strategy experimentation;
- statistical analysis;
- walk-forward/OOS splitting;
- cost sensitivity and break-even studies;
- visualization and ML.

Python must not independently reimplement fee, funding, fill, or P&L semantics.

## Versioning and Provenance

Every economic sample should retain, at minimum:

- strategy definition/version and intent fingerprint;
- FeatureSnapshot identity/fingerprint;
- reference market-state fingerprint;
- Order Book sequence and source evidence fingerprint;
- fee schedule fingerprint and scope;
- Funding Evidence fingerprints or explicit `NOT_APPLICABLE`/unavailable
  status;
- execution model version;
- transaction-cost model version;
- normalization versions;
- dataset/experiment manifest fingerprint;
- repository/code version when exported.

Execution and transaction-cost policies must be versioned independently from
market evidence. A change to either policy creates a new result identity.

## Research Dataset and Metrics

The current `research-dataset-v1` contains FeatureSnapshot plus objective
outcomes. It has no economic result section and must remain unchanged until a
separate dataset contract is approved.

Prediction metrics and economic metrics must remain separate.

Recommended first economic metrics:

- net P&L and net return when `FULLY_COSTED`;
- gross P&L and execution-adjusted P&L with explicit status otherwise;
- profit factor;
- expectancy;
- maximum drawdown;
- turnover;
- trade count and exposure;
- cost divided by gross profit.

Break-even cost analysis and cost sensitivity are recommended future Python
experiments, not part of the first deterministic Java contract.

## Horizon Economics

The current `PT1H`, `PT4H`, and `PT24H` outcome horizons remain suitable for the
first economic research boundary. They should not be changed by this
investigation.

Shorter Order Book horizons such as 1m, 5m, 15m, and 30m may later support
microstructure research, but require explicit sampling, evidence coverage, and
execution-time policy. They are not added here.

## Backtest Architecture

### Vectorized

Best for broad feature/strategy screening and fast hypothesis iteration, but
weak for book walking, order state, and execution uncertainty.

### Event-driven

Best for detailed order lifecycle simulation, but would be premature before
latency, queue, cancellation, and partial-fill evidence exists.

### Hybrid recommendation

Use a hybrid architecture:

1. Java produces strict FeatureSnapshot and objective-outcome datasets.
2. A strategy experiment selects candidate intents at `T0`.
3. Java deterministic primitives replay MARKET execution/costs for those
   candidates.
4. Python evaluates economic outputs, walk-forward behavior, stress, and
   sensitivity.

This is the smallest architecture that improves economic realism without
building a full exchange simulator.

## Candidate Architectures

### Option A - Trading Core economic authority

```text
Market Data strict evidence
        -> provider-neutral input ports
Trading Core deterministic execution/cost/economic domain
        -> versioned EconomicResult
Market Intelligence dataset assembly
        -> Java export
Python statistical research
```

Advantages:

- preserves Trading Core authority for execution and economic calculation;
- aligns with current economic classes;
- permits shared pure primitives with PAPER;
- keeps provider DTOs out of the research contract.

Risks:

- requires clear ports to Market Data and Broker Service;
- Trading Core must not become responsible for Market Intelligence feature
  interpretation or provider acquisition.

### Option B - Market Intelligence economic research engine

```text
Market Data evidence
        -> Market Intelligence replay/simulation
        -> economic dataset
Python research
Trading Core remains operational only
```

Advantages:

- close to research datasets and FeatureSnapshots;
- easy candidate-trade batch orchestration.

Risks:

- duplicates Trading Core execution semantics;
- invites divergence from PAPER fills and Risk boundaries;
- risks turning Market Intelligence into a second execution engine;
- conflicts with current responsibility boundaries.

### Recommendation

**Option A**, with Market Intelligence assembling research context/dataset
membership and Trading Core owning deterministic execution/economic primitives.
No new microservice is required.

## Temporal Integrity

### Decision time

At `T0`, strategy features and intent inputs must satisfy:

```text
knownAt <= T0
```

Future outcome evidence must not enter the intent, FeatureSnapshot, or decision
fingerprint.

### Execution time

If latency is explicitly modeled:

```text
submissionAt = T0 + modeled latency
executionAt >= submissionAt
```

Market evidence after T0 may be used only as execution-time evidence under that
explicit policy. It must not feed back into the decision.

### Outcome time

Future OHLC/outcome evidence after T0 may evaluate what happened later. It must
remain separate from decision and execution inputs unless the explicit execution
model consumes events after submission.

## Fail-Closed Semantics

Future results should expose explicit outcomes rather than synthetic fills:

- filled;
- partially filled;
- unfilled;
- insufficient market evidence;
- insufficient depth;
- cost data unavailable;
- unsupported order type;
- stale evidence;
- invalid input.

The following assumptions must be impossible to hide:

- missing fee != zero fee;
- missing funding != zero funding;
- missing depth != infinite liquidity;
- limit touched != guaranteed fill;
- visible book != guaranteed counterfactual liquidity;
- predicted funding != realized funding;
- partial result != net profit.

## Second-Pass Independent Review

The following risks were explicitly rechecked:

- **Spread double counting:** prevented by embedding crossing in fill prices and
  forbidding a second spread deduction.
- **Funding as zero:** blocked; Funding runtime is empty and provider-blocked.
- **Predicted as realized:** blocked by separate provider fields and explicit
  non-use of prediction for cost.
- **Missing evidence hidden:** current economic foundation has explicit statuses,
  but needs a separate completeness dimension before net claims.
- **Provider leakage:** existing ADRs and current boundaries preserve adapter
  isolation.
- **Future leakage:** `knownAt`/cutoff rules exist for features, outcomes, and
  cost evidence; the future simulator must apply them to every input.
- **Infinite liquidity:** must be blocked by depth-aware status and no
  extrapolation.
- **Perfect fills:** LIMIT behavior must remain unsupported; MARKET depth replay
  must return remainder/status.
- **PAPER divergence:** shared pure primitives are recommended; direct adapter
  reuse is rejected.
- **TradePlan over-coupling:** separate research intent is recommended.
- **ObservedMarketOutcome contamination:** current ADR boundary forbids it.
- **Python duplication:** Java remains deterministic authority.
- **Unversioned assumptions:** execution/cost/fee/dataset policies require
  versions.
- **Premature backtester complexity:** hybrid candidate execution is preferred;
  queue/latency/partial-fill realism remains deferred.

## ADR Decision

`ADR_REQUIRED`.

The decision crosses Trading Core, Market Data, Broker Service, Market
Intelligence, PAPER, and Python responsibilities. It defines a new authority
boundary for hypothetical execution and economic results and should be accepted
before further implementation.

Proposed title:

```text
ADR - Historical Execution and Transaction-Cost Research Architecture
```

## Next Implementation Stories

1. **Accept the architecture ADR and define the provider-neutral economic
   contract**, including cost completeness, two-leg result semantics, policy
   versions, and strict evidence ports.
2. **Complete evidence readiness**, separately resolving Broker runtime fee
   migrations/acquisition and the Story 0114 Kraken Funding blocker. Do not
   claim fully costed derivative economics while either required evidence path is
   unavailable.
3. **Implement deterministic MARKET execution replay**, using strict Order Book
   evidence and visible-depth book walking with explicit insufficient-depth
   outcomes.
4. **Integrate versioned EconomicResult into research exports**, then add Python
   read-only economic evaluation, break-even cost, and sensitivity experiments.

Limit orders, latency calibration, queue position, dynamic market impact,
funding settlement, and PAPER integration remain later stories.

## Primary Next Engineering Action

Write and accept the ADR `Historical Execution and Transaction-Cost Research
Architecture` before adding more implementation. The current code contains a
partial economic foundation, but the runtime fee migration gap, Funding blocker,
single-leg result shape, and absent integration make incremental implementation
without an accepted authority boundary unsafe.

## Continue Passive Collection

- OHLC: `CAPTURING_AND_PERSISTING`.
- Order Book: `CAPTURING_AND_PERSISTING`, with sequence-based trust and the
  current checksum limitation.
- FeatureSnapshots: `PRODUCING`, `16` strict observed snapshots at measurement.
- Funding: `BLOCKED` / not operational.

## Do Not Build Yet

- full event-driven exchange simulator;
- limit-order queue/fill model;
- latency or market-impact calibration;
- Funding Cost or settlement integration;
- FeatureProjection v2;
- Python-owned fill/fee/funding calculations;
- public economic-result APIs;
- catalog-wide or multi-provider cost capture.

## Final Decision

Trading OS has the correct conceptual boundary and a useful partial Java
economic foundation, but it is not yet allowed to claim economically realistic
historical profitability.

The recommended architecture is:

```text
strict Market Data evidence
    -> point-in-time ReferenceMarketState
    -> separate research StrategyIntent
    -> Java deterministic MARKET execution replay
    -> explicit versioned costs and completeness
    -> EconomicResult
    -> Java research dataset export
    -> Python walk-forward/OOS analysis
```

Until the fee schedule runtime gap and Funding blocker are resolved, derivative
results must be explicitly incomplete and no result may be labeled fully net or
fully costed.

## Required Final Template

```text
STORY_0113_REALISTIC_EXECUTION_COST_RESEARCH

Repository HEAD = 2d7a23331f1d40e7f030a9af84c486c518c7db96

CURRENT DATA CLOCK
OHLC = CAPTURING_AND_PERSISTING; 805 observed
Order Book = CAPTURING_AND_PERSISTING; 905,480 observed at latest measurement
FeatureSnapshots = PRODUCING; 16 strict observed, unique
Funding = PROVIDER_BLOCKED; 0 observed

CURRENT EXECUTION ARCHITECTURE
Trading Core = execution, PAPER settlement, Risk orchestration, economic foundation
Broker Service = provider execution facts and fee-schedule ownership
PAPER = current bid/ask immediate simulation, zero/default fee behavior
Market Data = provider-neutral market evidence and strict replay
Market Intelligence = deterministic interpretation, outcomes, datasets
Python Research = validated dataset consumer and statistical experimentation

FIVE-LAYER MODEL
Reference Market State = supported concept, assembly missing
Strategy Intent = partial foundation; separate research intent recommended
Simulated Execution = single-leg market evaluator; historical replay missing
Explicit Costs = partial contracts; fee/funding runtime evidence missing
Economic Result = partial single-leg contract; lifecycle/dataset integration missing
Recommended = YES, MODIFIED with explicit completeness

STRATEGY INTENT
Reuse TradePlan = NO
Recommended research concept = separate ResearchStrategyIntent

EXECUTION MODEL
Recommended initial order types = MARKET only
Recommended slippage realism level = LEVEL 3 visible-depth book walking
Order Book depth-aware execution = Recommended
Depth-25 insufficient behavior = explicit INSUFFICIENT_DEPTH / partial diagnostic
Visible-book consumption vs market-impact semantics = visible replay is not guaranteed counterfactual impact

SPREAD
Embedded in execution price = YES
Explicit spread deduction = NO
Double-count prevention rule = one effect, one representation

COMMISSIONS
Current model = observed fill fee plus dormant historical schedule foundation
FeeSchedule required = YES
Temporal fee versioning required = YES
Maker/taker handling = MARKET is taker by explicit versioned policy; LIMIT deferred

FUNDING
Historical evidence = UNAVAILABLE / PROVIDER_BLOCKED
Predicted rate usable as realized cost = NO
Funding required for fully-costed derivative result = YES when holding crosses funding applicability
Recommended incomplete-cost behavior = UNAVAILABLE or INSUFFICIENT_EVIDENCE, never zero

ECONOMIC RESULT
Recommended completeness model = separate explicit completeness status
ReferenceMarketReturn = objective market/reference calculation
GrossStrategyPnL = strategy-level result before explicit costs
ExecutionAdjustedPnL = result after simulated fills
CommissionCost = eligible versioned fee schedule component
FundingCost = eligible realized funding component only
OtherExplicitCosts = only evidenced and versioned components
NetPnL = available only when all applicable components are complete
NetReturn = available only with a defined denominator and complete result
Can partial result be called NET = NO

PAPER RELATIONSHIP
A/B/C = B
Recommended = shared pure deterministic primitives, separate evidence/acquisition and settlement adapters

JAVA / PYTHON
Java authority = factual evidence selection, fills, costs, economic accounting, fingerprints
Python authority = statistical research, walk-forward/OOS, sensitivity, visualization, ML
Duplicate deterministic accounting risk = high if Python reimplements semantics

VERSIONING
ExecutionModel version required = YES
TransactionCostModel version required = YES
FeeSchedule version required = YES
Required provenance = all evidence, policy, strategy, feature, execution, cost, dataset, and code fingerprints

BACKTEST ARCHITECTURE
Vectorized = broad candidate strategy screening
Event-driven = deferred for queue/latency realism
Hybrid = recommended
Recommended initial architecture = hybrid

FEATURE PROJECTION
V1 unchanged = YES
V2 timing recommendation = after execution/economic contract and baseline comparison
Liquidity comparative experiment = V1 versus V2 after deterministic economic outputs exist

ECONOMIC RESEARCH
Prediction metrics separated = YES
Recommended initial economic metrics = net/gross PnL, net return, profit factor, expectancy, drawdown, turnover, exposure, cost/gross profit
Break-even cost analysis = Recommended later
Cost sensitivity = Recommended later

TEMPORAL INTEGRITY
Decision-time rule = every decision input knownAt <= T0
Execution-time rule = post-T0 evidence only through explicit modeled submission/latency policy
Outcome-time rule = future evidence evaluates outcomes but never feeds decisions
Leakage risks = current provider fallback, future feature evidence, current fee/funding state, unversioned execution assumptions

FAIL-CLOSED SEMANTICS
Missing fee = UNAVAILABLE
Missing funding = UNAVAILABLE for applicable derivatives
Missing depth = INSUFFICIENT_DEPTH
Insufficient Order Book = explicit non-FULLY_COSTED result
Unsupported order type = UNSUPPORTED

ADR
ADR_REQUIRED
Proposed title = Historical Execution and Transaction-Cost Research Architecture

PRIMARY NEXT ENGINEERING ACTION
Write and accept the ADR before further implementation.

CONTINUE PASSIVE COLLECTION
OHLC = CAPTURING_AND_PERSISTING
Order Book = CAPTURING_AND_PERSISTING
FeatureSnapshots = PRODUCING
Funding = BLOCKED

OVERALL DECISION
Do not claim realistic net profitability yet. Establish the ADR and evidence/runtime readiness first, then implement narrow Java MARKET replay with explicit completeness.
```
