# Investigation - Trading Intelligence Layers Architecture

**Date:** 2026-09-23

**Repository baseline:** Story 0061 merged; accepted conservative trend-following
swing-trader investigation available

**Status:** Investigation complete; no code, Story, or ADR created

## 1. Executive Conclusion

Trading OS already contains most of the boundaries required for a safe layered
intelligence architecture. The repository has:

- Market Data ownership of normalized market facts and market state;
- Market Intelligence orchestration of deterministic capabilities, observations,
  opportunities, Trade Plans, and future AI work;
- durable `AnalysisExecution` and `IntelligenceObservation` concepts;
- a filterable `IntelligenceContext` and an asynchronous `AiEnginePort`;
- Trading Core ownership of user workflows, account context, Trade Planning
  profiles, execution lifecycle, positions, and history;
- a deterministic, fail-closed Risk Engine;
- explicit human authorization before financial execution;
- broker-neutral Broker Service boundaries;
- a frontend Decision Workspace that presents the current PAPER workflow.

The architecture is therefore not missing a new generic intelligence service.
It is missing a more precise **authority and evidence model** that prevents
different kinds of output from being treated as equivalent.

The recommended model is:

```text
Authoritative provider facts
    ↓
Deterministic derived evidence
    ↓
Statistical / ML evidence
    ↓
Agent / LLM interpretation and challenge
    ↓
Human trading decision
    ↓
Trade Plan
    ↓
Deterministic Risk authorization
    ↓
Human execution authorization
    ↓
Execution and reconciliation
```

This is an authority model, not necessarily a synchronous call chain. Some
deterministic and statistical capabilities may run independently, and an agent
may consume selected raw facts as well as derived evidence. The non-negotiable
rule is that later interpretation cannot rewrite earlier authoritative facts or
authorize an action owned by a higher-authority component.

The first implementation slice remains the accepted **deterministic
multi-timeframe Trend Context Assessment**. It should be introduced as a
versioned, provenance-rich Market Intelligence assessment and persisted as
durable analytical evidence. It should not require an ML service, an agent
platform, or a new microservice.

The recommended shared boundary is not one universal untyped `Intelligence`
object. Reuse the repository's existing concepts and strengthen their roles:

```text
IntelligenceContext
    ├── authoritative source sections
    ├── deterministic evidence
    ├── statistical / ML evidence
    ├── contextual source evidence
    └── selected user/account context

AnalysisExecution
    └── technical lifecycle, policy, attempts, freshness, trace

IntelligenceObservation / MarketContextAssessment
    └── durable business evidence with provenance and validity

TradingOpportunity / TradePlan / RiskEvaluation / ExecutionIntent
    └── progressively stronger business semantics and authority
```

`IntelligenceObservation` is the existing generic durable memory boundary.
`MarketContextAssessment` is a candidate typed observation for the first
deterministic trend capability, not a reason to replace the observation model
or create a platform-wide schema before the use case requires it.

The architecture should make these distinctions explicit:

- **factual authority:** who owns a fact and its source of truth;
- **analytical evidence:** what a deterministic or probabilistic capability
  derives from available facts;
- **interpretive proposal:** what an agent concludes or asks the human to
  consider;
- **financial authorization:** whether the exact Trade Plan is allowed by the
  Risk Domain;
- **execution authority:** the human-authorized command and its provider or PAPER
  outcome.

The current ADR set supports this direction, but several decisions should be
formalized before implementation. In particular, a future ADR should define the
authority/evidence contract and another should define statistical/ML and agent
governance if those capabilities move beyond experiments. This investigation
does not create either ADR.

## 2. Existing Architecture and Reusable Components

### Current responsibility map

| Component | Current responsibility | Reuse for intelligence layers | Boundary that must remain explicit |
| --- | --- | --- | --- |
| Market Data Service | Market catalogue, provider normalization, state, constraints, ticker, OHLC, order book, recent trades, snapshots, freshness | Source of normalized market facts and time-bounded data sections | No strategy semantics, recommendations, or account Risk authority |
| Market Intelligence | Context assembly, analysis orchestration, deterministic capabilities, observations, opportunities, Trade Plans, AI boundary | Host and orchestrate deterministic, ML, and agent capabilities initially | Must not assemble authoritative financial Risk snapshots or execute broker orders |
| Trading Core | Users, accounts, planning profiles, financial context, Risk orchestration, execution, PAPER settlement, positions, history | Source of account-scoped workflow context and downstream lineage | Must not become the owner of market opportunity analysis |
| Risk Domain / Engine | Pure deterministic evaluation of immutable financial context and rules | Final financial authorization for an exact Trade Plan | No AI, prediction, opportunity discovery, position management, or execution |
| Trade Planning | Converts opportunity/context into a concrete entry, stop, target, size, and execution proposal | Human-reviewable proposal boundary before Risk | Planning `RiskBudget` is not financial authorization |
| Broker Service | Provider credentials, capabilities, order placement, cancellation, reconciliation, error translation | Technical broker facts and execution adapter | No strategy, Risk authorization, or PAPER position authority |
| Execution | Trading Core lifecycle, human authorization, T0/T1 Risk gates, provider-neutral intent/attempt/outcome | Final command boundary and audit lineage | No execution before required Risk and human gates |
| Positions/history | Trading Core owns PAPER state; provider owns LIVE state; execution and analytics history are retained | Current thesis/position monitoring and post-trade linkage | Position projection is not analytical truth and LIVE/PAPER authority differs |
| Gateway | External entry point, JWT validation, routing, WebSocket routing | Authenticated access to user-facing intelligence and planning | Must not become an intelligence or authorization layer |
| Decision Workspace | Market context, scans, opportunities, Trade Plans, Risk, execution, positions, history | Human presentation of facts, evidence, proposals, contradictions, and gates | Must not visually equate an analytical recommendation with Risk approval |
| AI abstractions | `AiEnginePort`, `AiAnalysisCommand`, progress/reference/result, disabled capability | Controlled future AI/agent execution behind Market Intelligence | AI Engine has no autonomous access to source services and no execution authority |

### Relevant accepted architecture

ADR-014 already defines the layered trading decision pipeline and requires
human termination. ADR-020 defines Market Intelligence as an orchestrator over
deterministic and AI capabilities using a shared conceptual context. ADR-021
turns AI analysis into a durable, asynchronous, policy-governed execution and
separates `AnalysisExecution` from `IntelligenceObservation`.

ADR-028 establishes the Risk Engine as a deterministic authorization engine with
immutable inputs, no repository or external-service access, fail-closed
behavior, and auditable results. ADR-031 makes Trading Core the owner of
financial context assembly and keeps Market Intelligence in the planning and
analysis boundary.

ADR-033 separates intention-driven Active Scanner orchestration from future
batch-driven Passive Scanner orchestration. ADR-034 makes observations
strategy-independent, introduces versioned deterministic Strategy concepts, and
requires persisted immutable StrategyMatch lineage before a TradingOpportunity.
It also explicitly rejects letting AI determine deterministic strategy matches.

ADR-041 establishes human execution authority, T1 Risk revalidation, idempotent
execution, unknown-outcome reconciliation, and audit provenance. ADR-042 and
ADR-045 establish that LIVE position truth is broker-authoritative while PAPER
position truth and settlement are Trading Core-authoritative.

ADR-044 establishes service identity and authenticated service-to-service
boundaries. ADR-006 and ADR-012 keep market-domain facts provider-neutral and
owned by Market Data rather than Broker Service.

### Current implementation evidence

The current repository contains reusable technical boundaries:

- `market-intelligence/.../domain/IntelligenceContext.java` stores typed context
  sections and supports selecting an authorized subset;
- `.../application/context/IntelligenceContextAssembler.java` loads sections
  through registered contributors and represents missing/unavailable sections;
- `.../domain/execution/AnalysisExecution.java` models immutable asynchronous
  execution state, result quality, expiry, policy, retries, provenance, and
  consolidated results;
- `.../application/port/AiEnginePort.java` defines availability, submission,
  progress, structured result retrieval, and cancellation without exposing
  source-service access to an AI provider;
- `.../application/port/AiStructuredResult.java` and
  `CapabilityAnalysisResult.java` already separate an AI execution reference
  from findings and warnings;
- `.../domain/observation/Observation.java` already preserves identity,
  lineage, version, validity window, consolidation-rule version, evidence, and
  confidence;
- deterministic capabilities currently produce findings and warnings through
  the same orchestration pipeline;
- Trading Core contains the `RiskEngine`, risk evaluation services, immutable
  snapshot construction, planning profile ownership, T1 revalidation, and
  execution intent lifecycle;
- the frontend already has a Decision Workspace, scans, opportunities, planning,
  Risk, execution, position, and history flows.

### Actual gaps

The repository does not yet provide:

- a first-class multi-timeframe Trend Context Assessment;
- a generic but typed distinction between source facts, derived evidence,
  probabilistic evidence, and agent proposals at every consumer boundary;
- a future Python ML contract or model registry;
- a production AI/agent implementation; the current AI path is an adapter and
  governance boundary, not an active reasoning engine;
- a durable dataset that records all analytical reviews, including `NO_TRADE`
  and rejected analytical contexts, with stable joins to later outcomes;
- a position-monitoring thesis comparison model;
- a post-trade process-quality model separate from financial outcome.

These gaps should be filled incrementally. They do not justify introducing a
generic agent platform or a separate ML service in the first Trend Context
implementation.

## 3. Intelligence Layer Definitions

### Deterministic Intelligence

Deterministic Intelligence is a reproducible computation over explicitly
defined inputs and versioned rules. It may derive facts from market facts, but
it must not present a strategic interpretation as an authoritative financial
decision.

Examples for the accepted trader direction include:

- freshness and completeness;
- OHLC normalization;
- EMA and ATR;
- slope and persistence;
- confirmed swing points;
- higher-high/higher-low and lower-high/lower-low relations;
- regime and phase rules;
- timeframe contradiction detection;
- important structural levels;
- deterministic exclusions and `NO_SETUP` outcomes.

Deterministic Intelligence must provide:

- same inputs, same output;
- explicit input snapshot or source references;
- rule, algorithm, and configuration version;
- timestamp and valid-until semantics;
- completeness and missing-data state;
- evidence references for every material conclusion;
- deterministic `UNKNOWN`, unavailable, and excluded outcomes;
- no hidden network calls, mutable global state, or probabilistic fallback;
- replayability from retained references and versions.

Deterministic does not mean infallible. A deterministic rule can be weak,
poorly calibrated, or strategically inappropriate. The existing legacy OHLC
trend rule demonstrates this: it is reproducible but too permissive to be
treated as validated trading intelligence. Reproducibility is a guarantee of
behavior, not proof of edge.

### Statistical / ML Intelligence

Statistical / ML Intelligence derives evidence from learned relationships in a
historical dataset rather than only from explicitly authored market rules.

ML may estimate similarity, rank already eligible contexts, identify anomalies,
calibrate setup quality, or detect trader-specific patterns. Its output remains
evidence with uncertainty and cannot turn a rejected or incomplete deterministic
context into an authorized trade.

Every ML result must carry:

- model identity and model version;
- feature schema/version;
- input feature snapshot or immutable feature reference;
- training dataset identity/version;
- training time range and market/universe scope;
- label definition and label horizon where applicable;
- evaluation dataset and metrics;
- calibration method and calibration status;
- uncertainty or abstention state;
- drift/monitoring status;
- inference timestamp and valid-until policy;
- source provenance and correlation to the deterministic assessment.

An ML result may be unavailable, stale, out of distribution, or below minimum
data requirements. Those states must be explicit, not represented as a neutral
score or a zero probability.

### Agent / LLM Reasoning

Agent / LLM reasoning is contextual synthesis over selected heterogeneous
evidence. It is appropriate where the task is to compare explanations, expose
contradictions, identify missing information, challenge a thesis, or explain a
structured result in trader language.

Reasoning is not a replacement name for calculation or prediction. An agent
should not recalculate authoritative EMA, ATR, Risk, market state, or execution
status from prose when a structured source exists.

An agent may conclude:

- the evidence supports a human review of a thesis;
- the evidence is contradictory;
- a required context section is missing or stale;
- a thesis appears inconsistent with recorded evidence;
- additional information should be collected;
- the human should wait, compare alternatives, or inspect a specific risk.

An agent may propose:

- a structured explanation;
- a question for the trader;
- a candidate interpretation;
- a context-sensitive trade-plan hypothesis for human review;
- an alert or monitoring recommendation.

An agent may not conclude with authority that:

- a deterministic market fact is different from its source;
- a hard exclusion should be ignored;
- Risk approval is unnecessary or overridden;
- a Trade Plan is financially authorized;
- an order may be submitted;
- a position may be modified or closed without the established human command
  boundary;
- a strategy is validated or enabled without the required human governance.

### Human decision-making

Human decision-making is the discretionary authority to accept, reject, defer,
or refine an analytical proposal and to create or approve the Trade Plan within
the established workflow. The trader may disagree with an attractive analytical
assessment and may choose `NO_TRADE` even when Risk would approve a plan.

Human choice does not override deterministic Risk. It decides whether to pursue
a proposal and whether to authorize execution after the required Risk gates.

## 4. Authority and Trust Model

### Fact authority

```text
Provider / platform source facts
        ↓
Owning service normalized facts
        ↓
Deterministic derived evidence
        ↓
Statistical / ML evidence
        ↓
Agent interpretation
        ↓
Human discretionary interpretation
```

This is not a ranking of intelligence quality. It is a rule for resolving
conflicts about what a fact means and who may change it.

- Market Data owns normalized public market facts, state, constraints, and
  freshness.
- Broker Service owns provider technical facts and provider execution outcomes.
- Trading Core owns user/account workflow facts and PAPER financial state.
- Risk owns the deterministic authorization result for its immutable context.
- Deterministic Intelligence owns reproducible derived observations, not source
  facts.
- ML owns its model evidence and uncertainty, not the inputs it consumed.
- Agents own their proposals and explanations, not facts supplied by sources.
- The human owns the discretionary decision and explicit execution action.

If an agent says that a price, market state, position, or Risk result differs
from the structured authoritative source, the source wins and the contradiction
is recorded. The agent may identify that the source appears stale or
incomplete; it may not silently correct the source.

### Execution authority

```text
Market evidence and interpretation
        ↓
Human decision to form a Trade Plan
        ↓
Trade Plan
        ↓
Risk Engine evaluation
    ├── REJECTED / UNAVAILABLE → STOP
    └── APPROVED / approved with warnings
            ↓
         Human authorization
            ↓
       ExecutionIntent and execution pipeline
            ↓
       Broker/PAPER outcome and reconciliation
```

`Risk APPROVED` means only that the exact evaluated plan satisfies the current
financial rules and facts. It does not mean the market is attractive, the
trade will win, or the human must execute.

An agent ranking a setup has no relation to financial authorization. A high ML
score has no relation to execution permission. A human may reject an approved
plan; a human may not make the Risk Engine treat rejected facts as approved.

### Authority matrix

| Question | Authoritative owner | Evidence consumers may receive | Can ML/LLM override? |
| --- | --- | --- | --- |
| Is the market open/tradable? | Market Data normalized market state and constraints | Intelligence, planning, Risk, UI | No |
| What happened in the candles? | Market Data OHLC source plus deterministic normalization | Intelligence, research, agents | No |
| What structure follows the defined rule? | Deterministic Intelligence rule version | ML, agents, UI, research | No; disagreement may be reported |
| How useful historically is this context? | ML model evidence and its evaluation provenance | Agents, UI, research | No hard exclusions overridden |
| What does heterogeneous evidence suggest? | Agent proposal only | Human, UI, audit | Not an authorization decision |
| Is the Trade Plan financially allowed? | Risk Engine result | Trading Core, UI, execution gate | No |
| May a financial command be sent? | Human authorization after Risk gates | Trading Core execution | No component may substitute for human authorization |
| What actually happened at the broker? | Broker/provider for LIVE outcome, Trading Core for PAPER settlement | Trading Core, history, monitoring | No |

### Technical enforcement implications

The architecture should make unsafe substitution difficult:

- use typed result categories rather than one generic score or recommendation;
- require provenance references on every promoted observation;
- keep `RiskValidationResult` and execution commands in Trading Core/Risk
  boundaries unavailable to ML and agent adapters;
- pass agents curated context sections rather than service credentials or
  unrestricted APIs;
- allow agents to reference evidence IDs but not mutate evidence;
- require explicit status and authority metadata in frontend contracts;
- make hard exclusions and Risk rejection terminal for the relevant workflow;
- require human transitions for strategy lifecycle and execution authorization;
- fail closed when an authoritative fact required by Risk is unavailable.

## 5. Deterministic Intelligence Contract

### Reuse before introducing a new abstraction

The repository already has the correct conceptual pieces:

- `IntelligenceContext` for selected context sections;
- `AnalysisExecution` for technical execution history;
- `CapabilityAnalysisResult` for capability findings and warnings;
- `IntelligenceFinding` and `ObservationEvidence` for evidence;
- `IntelligenceObservation` for durable business memory;
- StrategyDefinition/StrategyEvaluation/StrategyMatch for versioned setup
  semantics;
- `TradePlanningContext` for planning only;
- RiskEvaluationContext/RiskValidationResult for financial authorization.

The first Trend Context Assessment should use these boundaries rather than
introducing a universal `IntelligenceEvidence` service or DTO. A typed
`MarketContextAssessment` may be justified as a domain-specific observation
payload when the assessment is implemented, provided it retains the generic
observation lineage and does not duplicate StrategyMatch or RiskEvaluation.

### Proposed logical contract

The first producer should expose a logical assessment with the following
categories:

```text
MarketContextAssessment
  assessmentId / observationId
  lineageId and version
  market identity
  assessment timestamp
  validFrom / validUntil
  context timeframe role definitions
  source snapshot references
  source data freshness and completeness
  regime
  direction
  confirmed structure
  phase and location
  strength evidence
  momentum evidence
  volatility state
  timeframe alignment / contradiction
  important structural areas
  candidate invalidation conditions
  hard exclusions
  conservative outcome
  deterministic rule/model version
  evidence references
```

The conservative outcome should remain analytical, for example:

```text
NO_SETUP
WATCH
CONTEXTUALLY_ATTRACTIVE
CONTEXTUALLY_DANGEROUS
UNKNOWN
```

It must not use `APPROVED`, `REJECTED`, or `AUTHORIZED`, which belong to Risk
and execution authority.

### Generic versus strategy-specific content

Generic observation metadata should include:

- identity and lineage;
- market and scope;
- timestamps and validity;
- source references;
- evidence references;
- completeness and warnings;
- producer type and version;
- supersession/invalidation relation;
- optional links to AnalysisExecution and StrategyEvaluation.

Trend-specific content should include:

- regime, direction, structure, phase, and volatility;
- timeframe roles and relations;
- swing and level evidence;
- trend-context exclusions and invalidation.

Account-specific financial context should not be embedded in a public market
assessment. If an account-scoped analysis needs exposure or planning context,
Market Intelligence should receive an authorized reference or selected
section, not become the owner of Risk snapshots. `TradePlanningContext` and
RiskEvaluationContext remain separate.

ML-specific content should be attached as a separate evidence contribution,
not mixed into deterministic fields. Agent outputs should reference both
contributions without overwriting them.

### Contract invariants

Every deterministic assessment must satisfy:

1. Inputs are identified by immutable source references or content digests.
2. The rule and parameter version is recorded.
3. All required data sections report present, stale, missing, or unavailable.
4. The result can be `UNKNOWN` or `NO_SETUP` without manufacturing a direction.
5. Each material conclusion has evidence or an explicit reason for absence.
6. Validity is explicit; expired evidence is not silently reused.
7. A newer assessment supersedes an older lineage version instead of mutating
   historical truth.
8. The assessment has no method to approve Risk or submit execution.

### Relationship to Strategy

The Trend Context Assessment should remain strategy-independent wherever it
describes reusable market state. A future `StrategyEvaluation` may consume it
and decide whether a versioned strategy matched. A `StrategyMatch` is stronger
than a context assessment: it says that a particular deterministic strategy
version's setup conditions were satisfied.

The following distinctions must remain:

```text
MarketContextAssessment  = what the market context says
Observation              = reusable durable market statement
StrategyEvaluation       = evaluation of one strategy against context
StrategyMatch            = immutable successful strategy fact
TradingOpportunity       = trader-facing candidate derived from match
TradePlan                = concrete proposal
RiskEvaluation           = financial authorization verdict
ExecutionIntent          = human-authorized command
```

A context assessment must not automatically become an opportunity. This
preserves ADR-034 and avoids repeating the previous high-opportunity-rate
problem caused by a permissive anonymous rule.

## 6. Historical Persistence Model

### Goal

The deterministic V1 must collect useful historical evidence for later ML,
agent reasoning, and trader learning without requiring those capabilities now.
The dataset must represent the decision process, not only executed trades.

### What should be persisted

#### Source references and deterministic assessments

Persist or reference:

- market and asset identity;
- source data provider and normalized source version;
- OHLC interval, covered time range, and candle snapshot/reference;
- market state, tradability, constraints, spread, and freshness references;
- context timeframe roles and selected intervals;
- deterministic feature/observation values used by the assessment;
- Trend Context Assessment payload or immutable content reference;
- rule, algorithm, parameter, and strategy versions;
- completeness, warnings, exclusions, and `UNKNOWN` state;
- assessment validity and supersession lineage.

Raw OHLC and order-book data should remain owned by Market Data unless a
retention or replay decision explicitly requires a durable immutable snapshot.
Market Intelligence should store content digests and source IDs where a full
duplicate is unnecessary. If a future model requires reproducible replay, the
source service must provide a versioned historical access contract rather than
silently duplicating provider payloads everywhere.

#### Analytical decisions

Persist:

- `NO_SETUP` and `WATCH` outcomes;
- contextually attractive or dangerous outcomes;
- hard exclusions and missing-data outcomes;
- active scan requested/effective scope and user intent;
- human review decision: accepted for planning, rejected, deferred, or ignored;
- reason category where the human provides one;
- agent and ML contribution references when present;
- whether the context was promoted to a StrategyMatch or opportunity.

No-trade decisions are first-class historical facts. Without them, future ML
would learn only from selected cases that survived the trader's attention and
execution process, creating severe selection bias.

#### Planning, Risk, and execution lineage

Preserve stable references from:

```text
source snapshots
    → AnalysisExecution
    → MarketContextAssessment / Observation
    → StrategyEvaluation / StrategyMatch
    → TradingOpportunity
    → TradePlan and planning-profile version
    → T0 RiskEvaluation
    → human decision / ExecutionIntent
    → T1 RiskEvaluation
    → ExecutionAttempt / broker outcome
    → order / fill / position
    → exit / realized outcome
```

The source IDs and versions should be stored as references, not by coupling all
domain aggregates to each other's internal tables. Each domain retains
ownership while integration records carry immutable correlation IDs.

#### Position and post-trade evidence

Later monitoring and coaching require:

- original thesis and invalidation as known before execution;
- the assessment and model versions used at decision time;
- changes in deterministic context during the hold;
- position and valuation snapshots according to LIVE/PAPER authority;
- plan adherence and discretionary amendments;
- exit reason, fees where available, and realized outcome;
- outcome horizon and whether the result was fully observed;
- post-trade human review.

The system must distinguish:

- no trade because no setup existed;
- no trade because evidence was missing or stale;
- no trade because the human rejected it;
- no trade because Risk rejected the plan;
- trade was authorized but not submitted;
- submission outcome was unknown;
- trade executed and later closed.

These are not interchangeable negative labels.

### Dataset joins

The minimum join keys should be:

- `analysisExecutionId`;
- `assessmentId` / `observationId` and lineage/version;
- `marketId` and instrument identity;
- `strategyId` and `strategyVersion` where applicable;
- `activeScanId` and child execution ID where applicable;
- `tradePlanId` and `tradePlanVersion`;
- `riskEvaluationId` plus T0/T1 phase;
- `executionIntentId`, attempt ID, provider correlation ID;
- position/trade/outcome IDs;
- a correlation/trace ID for technical reconstruction.

Every join must be temporal. An outcome label must state which future window it
uses and must not leak candles or account facts from after the decision into
features that claim to represent the decision time.

### Avoiding selection and leakage bias

The historical dataset should retain:

- every eligible deterministic assessment in the defined sampling policy;
- contexts excluded before opportunity creation;
- contexts that were reviewed but not planned;
- plans rejected by the human;
- plans rejected by Risk;
- executed and non-executed intents;
- expired and stale contexts;
- ML/agent unavailable cases.

Labels must be generated only after the intended observation horizon closes.
Train/test splits must be time-aware where market regimes or instruments could
otherwise leak across splits. A future ML experiment must document survivorship,
attention, execution, and missingness biases before interpreting metrics.

### Relationship to existing persistence decisions

ADR-021 already separates technical `AnalysisExecution` from durable
`IntelligenceObservation` and rejects persisting every raw prompt or full
context by default. This investigation supports that boundary. The extension
needed for the future dataset is not indiscriminate storage; it is durable
decision and outcome references, including no-trade and rejected states, plus
versioned immutable evidence snapshots where replay requires them.

## 7. Future ML Boundary and Contract

### Why a Python service is not required now

No Python ML service currently exists in the repository. The first deterministic
Trend Context implementation should remain in the existing Market Intelligence
boundary, where its domain semantics, provenance, and PAPER validation can be
established. Python becomes a useful deployment boundary when experimentation,
libraries, model lifecycle, or compute isolation genuinely require it.

A future Python service should consume an explicit feature/evidence contract,
not scrape Java database tables or request unrestricted access to Trading OS.

### ML input contract

The logical input should include:

- assessment ID and source snapshot IDs;
- market identity and asset metadata;
- deterministic feature schema/version;
- context/setup/refinement timeframe roles;
- structure, regime, phase, direction, and contradiction states;
- EMA/ATR/slope/persistence values and source windows;
- distances to structural levels and invalidation;
- volatility and liquidity/spread context;
- market freshness/completeness and exclusions;
- strategy identity/version if ranking a strategy-specific context;
- account context only when explicitly authorized and necessary;
- historical labels by reference, never future feature values;
- feature generation timestamp and valid-until;
- missingness and abstention flags.

The ML service should receive a feature snapshot or signed/versioned reference,
not infer feature semantics from arbitrary JSON fields. It must know whether a
missing value is genuinely absent, unavailable, not applicable, or zero.

### ML output contract

Useful outputs include:

- calibrated setup-quality evidence;
- similarity to historical contexts;
- anomaly or out-of-distribution evidence;
- candidate ranking evidence after deterministic eligibility;
- false-positive likelihood under a documented label definition;
- trader-specific behavioral pattern evidence;
- abstention or insufficient-data result.

The output must not be a universal `winProbability`. A single probability is
misleading because:

- the label may change with holding horizon, exit rule, fees, and market;
- it can hide regime changes and distribution drift;
- calibration can be poor even when ranking metrics look good;
- a high historical probability does not mean the current context is fresh or
  financially authorized;
- selection and execution bias can inflate the estimate;
- it encourages the UI and human to treat a statistical estimate as a promise;
- it cannot represent hard exclusions, missing facts, or unresolved thesis
  contradictions without additional state.

If a probability is ever used, it must be named by its exact event and horizon,
carry calibration and uncertainty metadata, and remain one evidence field among
others. An ordinal setup-quality or similarity assessment with abstention may be
safer for the first ML experiment.

### ML governance

ML may rank or explain only after deterministic eligibility and hard exclusions.
The orchestration must preserve:

- deterministic exclusion reasons even when ML ranks a context highly;
- model version and training provenance;
- feature snapshot and schema;
- evaluation metrics and calibration status;
- uncertainty and drift state;
- model availability and timeout;
- the exact output accepted by Market Intelligence;
- whether a human saw or acted on the evidence.

ML cannot:

- change market state or OHLC facts;
- remove a deterministic `NO_SETUP` or hard exclusion;
- produce a Risk approval;
- enable a StrategyDefinition;
- modify a Trade Plan after Risk without the existing workflow;
- authorize execution.

### Experiment boundary

Before production use, ML should first operate in shadow/PAPER analysis:

- consume historical or current deterministic assessments;
- produce evidence without changing candidate eligibility or execution;
- record outputs, uncertainty, and later labels;
- be evaluated for calibration, stability, false-positive reduction, and
  decision usefulness;
- require human review before promotion into user-visible ranking.

## 8. Multi-Agent Reasoning Architecture

The multi-agent direction is accepted as a future capability. The architectural
question is how to avoid turning every context contributor into an autonomous
agent or allowing agents to become an unbounded decision layer.

### Recommended shape

Agents should initially be **specialized reasoning capabilities under the
existing Market Intelligence orchestrator**, not independent business services
with direct access to all systems.

The existing ADR-020/021 model is the correct starting point:

- Market Intelligence owns context construction, classification, capability
  selection, execution policy, consolidation, and memory promotion;
- an AI Engine or agent runtime executes authorized reasoning capabilities;
- agents receive a targeted context pack;
- outputs are structured, provenance-aware proposals;
- only Market Intelligence promotes durable observations;
- Trading Core, Risk, and Broker Service remain outside agent authority.

Separate deployable agents become justified only when they have independent
security, scaling, ownership, or model/runtime requirements. Names such as
Market Analysis Agent and Macro Agent are useful roles, not an instruction to
create five new services.

### Candidate responsibilities

| Candidate role | Consume | Produce | Must not own |
| --- | --- | --- | --- |
| Market Analysis Agent | Deterministic Trend Context and ML evidence | Thesis-quality assessment, contradictions, missing technical context, questions | Candle facts, deterministic calculations, Risk, execution |
| Macro / News Agent | News/calendar/macro source evidence with provenance | Event relevance, competing macro interpretations, uncertainty | News truth, market structure, Risk, orders |
| Portfolio / Exposure Agent | Authorized position/exposure/correlation context and candidate evidence | Concentration and portfolio-context concerns | Account Risk authorization, limits, sizing, position mutation |
| Trade Review Agent | Candidate evidence, plan, deterministic Risk result, other proposals | Structured pre-human challenge and unresolved questions | Risk override, plan approval, execution |
| Position Monitor Agent | Original assessment/thesis, current evidence, position state | Alert/recommendation that thesis changed or requires human review | Stop/target mutation, automatic close, provider reconciliation |
| Post-Trade / Coach Agent | Immutable decision lineage, plan adherence, outcome and human notes | Process-quality review and learning questions | Rewriting historical facts, judging outcome as proof of edge |

The first agent capability should likely be the Trade Review or Market Analysis
role after deterministic evidence is useful. Macro, portfolio, position, and
coach roles depend on source contracts and historical lineage that are not yet
complete.

### Agent independence and disagreement

Agents may disagree with each other because they interpret heterogeneous
context. Their outputs should remain separate with explicit evidence references
and uncertainty. A consolidation step may summarize disagreement, but must not
average it into an authoritative truth or hide the dissenting proposals.

The system should distinguish:

- disagreement about interpretation;
- disagreement caused by different context timestamps;
- disagreement caused by missing sources;
- contradiction with an authoritative fact;
- contradiction with deterministic derived evidence.

The last two are not resolved by majority vote. They become visible warnings or
failures in the proposal.

## 9. Agent Orchestration and ContextPack

### Is a ContextPack justified?

Yes, as a logical, scoped orchestration concept. It should not be a new
cross-service database or an unrestricted snapshot of the platform.

The current `IntelligenceContext` is the reusable base. A future
`TradingContextPack` can be a policy-selected view over that context for a
particular reasoning capability and user workflow.

```text
TradingContextPack
  request purpose and correlation
  context timestamp and valid-until
  market source facts / references
  deterministic evidence
  ML evidence
  account context references or selected facts
  portfolio / position context
  strategy context
  news / macro context
  original thesis and Trade Plan where relevant
  historical relevant evidence references
  missing, stale, and contradictory sections
  classification and provenance for every section
```

It should contain selected values and references, not secrets, provider
credentials, unrestricted database access, or arbitrary service handles.

### Who builds and selects context

- The Market Intelligence orchestrator owns the business request and builds the
  candidate context.
- Context contributors declare what they can produce, classification,
  freshness, provenance, and authorized consumers, consistent with ADR-021.
- The agent capability declares requested sections as a request, not an
  authorization.
- The orchestrator applies policy, purpose, consumer identity, mode, freshness,
  data classification, and user/account permissions.
- The orchestrator records requested, transmitted, refused, stale, and missing
  sections.
- Trading Core supplies account and planning context through explicit service
  contracts; agents do not query Trading Core directly.
- Risk facts supplied for explanation remain copies/references of authoritative
  decisions, not a second Risk context assembled by Market Intelligence.

### Targeted access

Examples of context restrictions:

- Market Analysis may receive public market facts, deterministic assessments,
  and allowed ML evidence, but not credentials.
- Macro / News may receive market identity and time window, but not detailed
  account exposure unless a specific portfolio question requires it.
- Portfolio / Exposure may receive selected position and exposure facts, but not
  broker secrets or the ability to mutate positions.
- Trade Review may receive the Trade Plan and Risk result for challenge, but it
  cannot call Risk or modify the plan.
- Position Monitor may receive an original thesis and current position
  projection appropriate to LIVE/PAPER authority, but not execution commands.

### Context validity

Every ContextPack should carry:

- creation timestamp;
- section-level source timestamps;
- pack valid-until;
- model/strategy/evidence versions;
- context digest;
- stale/missing flags;
- correlation to the AnalysisExecution.

An agent result received after the pack or its required authoritative sections
expire cannot silently become the current consolidated result. It may be kept
as a late result for audit, consistent with ADR-021.

### Unsupported claims and hallucination handling

The orchestrator or result validator should require every material agent claim
to cite one or more evidence references, or mark it explicitly as:

- hypothesis;
- assumption;
- external-source interpretation;
- unsupported claim;
- missing information.

Unsupported claims are not promoted as durable facts. A natural-language
explanation may contain useful framing, but its structured claims must be
grounded or visibly uncertain.

## 10. Structured Agent Output Contract

An agent response must not be arbitrary prose only. The logical result should
contain:

```text
AgentReasoningResult
  resultId
  executionId / contextPackId
  conclusionType
  proposal or recommendation
  supportingEvidenceRefs
  contradictingEvidenceRefs
  uncertainty / confidence explanation
  missingInformation
  assumptions
  freshness and valid-until
  source provenance
  recommended human question/action
  agent role, model, provider, and version
  prompt/policy version reference where retained safely
  result quality and failure/degradation state
```

### Conclusion types

The result should distinguish:

- `SUMMARY`;
- `THESIS_SUPPORT`;
- `THESIS_CHALLENGE`;
- `CONTRADICTION_FOUND`;
- `MISSING_INFORMATION`;
- `WAIT_OR_REVIEW`;
- `PROPOSED_TRADE_CONTEXT`;
- `MONITORING_ALERT`;
- `PROCESS_REVIEW`.

These are proposal types, not Risk or execution statuses. `PROPOSED_TRADE_CONTEXT`
must not be named `APPROVED`.

### Natural language coexistence

The UI may display a concise narrative generated from the structured result,
but the narrative must remain linked to the same evidence references. The
structured result is the machine-consumable contract; prose is an explanation
view and may be regenerated without changing the underlying evidence.

### Promotion rules

Market Intelligence may promote an agent result to an `IntelligenceObservation`
only when:

- the result is complete enough for the declared observation type;
- evidence references resolve;
- freshness is acceptable;
- unsupported claims are excluded or marked as hypotheses;
- the producer/model/version is recorded;
- the observation's confidence and validity are explicit;
- promotion does not imply Risk or execution authority.

An agent result may remain a technical result without becoming business memory.
ADR-021's rule that not every technical result becomes an observation remains
important.

## 11. Human Validation Boundary

The human must be able to distinguish at least four visual and semantic layers:

1. **Facts:** source market/account/position facts and timestamps.
2. **Deterministic evidence:** rules, measurements, structure, exclusions, and
   `UNKNOWN` states.
3. **Statistical evidence:** model output, calibration, uncertainty, and drift.
4. **Agent interpretation:** explanation, challenge, assumptions, and proposed
   question/action.
5. **Financial authority:** Risk result for the exact plan.
6. **Execution authority:** explicit human command and resulting status.

The Decision Workspace should conceptually present these as separate sections or
badges. It must not show `Risk APPROVED` beside an agent's attractive proposal
in a way that implies the latter caused the former or that approval requires
execution.

The trader should be able to see:

- source timestamps and freshness;
- which deterministic rules produced each finding;
- which ML model and training provenance produced each estimate;
- which agent received which context sections;
- supporting and contradicting evidence;
- missing or stale data;
- whether the result is a fact, evidence, or proposal;
- the original thesis and invalidation;
- the Risk decision and its T0/T1 phase;
- the explicit action still required from the human.

Human actions should be preserved as domain events or immutable decision
records where possible:

- reviewed;
- rejected;
- deferred;
- accepted for planning;
- Trade Plan created;
- Risk accepted/rejected/unavailable;
- execution authorized;
- position review accepted/rejected.

The UI must not become a new source of Risk or market truth. It renders and
collects human decisions through Trading Core and Market Intelligence contracts.

## 12. Failure and Degradation Model

The core principle is: degrade optional interpretation, fail closed on facts
required for safe authorization, and never replace missing facts with model
confidence.

| Failure | Safe behavior | Can deterministic PAPER analysis continue? | Can execution continue? |
| --- | --- | --- | --- |
| Market Data unavailable | Mark source section unavailable; no fresh assessment; preserve last result as stale | Only for clearly non-trading historical review; no new current setup | Only if all authoritative Risk/execution facts independently remain valid; required current market facts fail closed |
| OHLC incomplete or stale | Produce `UNKNOWN`/`NO_SETUP` with missing intervals and validity expiry | Yes for historical inspection; not as a fresh setup | Risk/execution decides from its own required facts; intelligence cannot compensate |
| Deterministic capability fails | Mandatory capability causes assessment degraded/unavailable; optional capability warning | Yes if required deterministic contract remains complete; otherwise no fresh setup | No intelligence override; Risk still independently governs |
| ML service unavailable | Omit ML evidence, record unavailable; retain deterministic output | Yes | Yes if normal deterministic planning/Risk/human gates pass |
| ML model stale or drifted | Mark output invalid/degraded; do not use for ranking; retain deterministic evidence | Yes | Yes without ML, subject to normal gates |
| Agent/LLM unavailable | Return deterministic and ML evidence without interpretation; retain technical failure | Yes | Yes without agent, subject to normal gates |
| News/calendar unavailable | Mark macro section missing; do not claim event clearance | Yes, with caution/no-trade policy where the strategy requires it | Risk facts and human decision determine whether a plan proceeds; missing event context must remain visible |
| Agents disagree | Preserve separate proposals and contradiction; request human review or wait | Yes | No agent majority can authorize; human decides, then Risk still gates |
| ML conflicts with deterministic context | Deterministic hard exclusions and facts win; retain ML as conflicting evidence | Yes | ML cannot remove exclusions or affect Risk authority |
| Agent conflicts with deterministic facts | Mark unsupported/contradictory claim; source evidence wins | Yes | No override |
| Risk rejects highly ranked setup | Stop the financial workflow; retain analytical result and rejection reason | Yes for learning/review | No execution; rejection cannot be softened |
| T1 Risk unavailable | Fail closed before broker/PAPER financial command according to execution policy | Yes for analysis | No execution |
| Broker LIVE outcome unknown | Mark `UNKNOWN`, require reconciliation, no blind retry | Not relevant to market analysis | No unsafe retry |
| PAPER settlement failure | Roll back or explicit recoverable local state; no fake provider reconciliation | Yes for analysis | No assumed successful position |

The current ADR-021 `AnalysisExecution` status/quality separation and targeted
retry model should be reused. A degraded optional result must not be confused
with a completed authoritative result.

## 13. Observability and Auditability

The system must later answer both the business and technical questions below
without retaining unnecessary secrets or full sensitive prompts.

### Business reconstruction

- What market and source data did deterministic Java see?
- Which normalized facts and time ranges were used?
- Which rule, algorithm, strategy, and parameter versions applied?
- What assessment, observations, exclusions, and contradictions were produced?
- Which ML model and training dataset produced its evidence?
- Which agents ran, in what roles, and what context sections did they receive?
- What did each agent support, challenge, assume, or leave unknown?
- What did the human decide and when?
- Which Trade Plan and planning-profile version followed?
- What did T0 and T1 Risk decide using which policy and facts?
- Who authorized execution?
- What did PAPER settlement or the LIVE broker report?
- What happened to the position and what was learned afterward?

### Minimum trace fields

Retain or link:

- `analysisExecutionId`;
- request, trace, and correlation IDs;
- context digest and ContextPack version;
- source section IDs, timestamps, and valid-until;
- deterministic rule/model versions;
- assessment/observation IDs and lineage versions;
- strategy and StrategyMatch IDs where applicable;
- ML model, feature schema, dataset, and inference IDs;
- agent role, provider/model version, policy version, and result ID;
- requested/transmitted/refused context sections;
- degradation, timeout, retry, and late-result state;
- human review/authorization ID and actor;
- TradePlan/version;
- T0/T1 Risk evaluation IDs and decision reasons;
- ExecutionIntent/Attempt and provider/PAPER correlation IDs;
- position, fill, exit, and outcome references.

### Sensitive data policy

Do not log:

- API keys, passwords, JWTs, refresh tokens, or signing secrets;
- full broker payloads unless an approved redacted retention policy requires it;
- unrestricted account credentials;
- full prompts/contexts by default;
- private user information unrelated to the analysis.

Store references, hashes, redacted structured claims, and policy decisions where
they answer audit questions without duplicating restricted content. If a future
investigation requires prompt replay, it must define retention, redaction,
access control, and deletion policy separately.

### Metrics

Useful metrics include:

- deterministic assessment completeness and `UNKNOWN` rate;
- stale-source rate and assessment expiry rate;
- no-setup, watch, exclusion, and human-rejection rates;
- ML coverage, abstention, calibration, drift, and false-positive reduction;
- agent latency, failure, unsupported-claim, contradiction, and promotion rates;
- context-pack size and classification distribution;
- time from assessment to human review, plan, and execution;
- Risk rejection after analytical ranking;
- T1 rejection after T0 approval;
- outcome data completeness and plan-adherence coverage.

Metrics must not be used to infer authority. A high agent acceptance rate is not
evidence that the agent should receive more authority.

## 14. Trend Context Assessment Integration

The accepted product investigation identifies the deterministic multi-timeframe
Trend Context Assessment as the first implementation slice. It should become
the first producer of the layered architecture as follows.

### Producer

Market Intelligence hosts the deterministic capability in its existing
capability/context/observation architecture. It consumes normalized Market Data
sections and produces a typed assessment plus generic observation lineage.

It should not be a new Trend Intelligence microservice and should not move
market normalization into Market Intelligence.

### First contracts needed now

The implementation should establish only the contracts that later layers need:

- immutable source snapshot references and freshness/completeness;
- role-based timeframe selection;
- versioned deterministic rule/configuration identity;
- typed structural findings and evidence references;
- explicit contradiction and exclusion representation;
- conservative outcome including `NO_SETUP`/`UNKNOWN`;
- validity and supersession;
- assessment/observation identity and AnalysisExecution linkage;
- strategy link as optional metadata, not an implicit opportunity;
- no-trade and human-review outcome references.

Do not implement an ML feature store, model registry, agent registry, or generic
ContextPack platform as a prerequisite. The deterministic assessment should be
valuable alone and should produce enough stable lineage for later consumers.

### Downstream use

```text
Market Data
    ↓
Deterministic Trend Context Assessment
    ↓
IntelligenceObservation / assessment lineage
    ├── active scan / strategy evaluation
    ├── Decision Workspace
    ├── future ML feature snapshot
    └── future targeted agent ContextPack
```

An assessment can be `NO_SETUP` and still be persisted. It can be consumed by
future ML as a negative/abstention example and by an agent as an explanation of
why the trader should wait. It should not create a `TradingOpportunity` unless a
versioned StrategyDefinition separately matches.

### Validation target

Validate in PAPER that the trader can:

- review fewer markets;
- see why a market is excluded, watched, attractive, or dangerous;
- inspect supporting and contradictory deterministic evidence;
- define a thesis and invalidation;
- create a Trade Plan without hidden Risk duplication;
- proceed through the existing Risk and human execution path;
- later reconstruct why the decision was made, including no-trade decisions.

Only after this proves useful should the product evaluate ML ranking or agent
explanation against the deterministic baseline.

## 15. Recommended Architecture Sequence

### Stage 1 - Deterministic Trend Context

- Implement the accepted multi-timeframe deterministic capability in Market
  Intelligence.
- Persist versioned assessment/observation evidence and no-setup outcomes.
- Reuse AnalysisExecution, IntelligenceContext, Observation, Strategy, and
  existing provenance rather than adding a new service.
- Expose facts, evidence, contradiction, freshness, and invalidation to the
  Decision Workspace.

### Stage 2 - PAPER validation and dataset accumulation

- Record human review decisions, rejected contexts, Trade Plans, Risk outcomes,
  execution lineage, positions, exits, and plan adherence.
- Preserve non-executed and no-trade examples.
- Validate that the deterministic assessment improves selectivity and
  explanation without changing Risk authority.

### Stage 3 - Statistical / ML experiments

- Build time-aware datasets from the persisted evidence.
- Begin in shadow/PAPER mode with a narrow hypothesis such as false-positive
  reduction, similarity, or ranking after deterministic eligibility.
- Track model/training/feature provenance, uncertainty, calibration, and drift.
- Keep ML out of hard exclusions, Risk, and execution.

### Stage 4 - Structured agent reasoning

- Introduce one targeted agent role through the existing Market Intelligence to
  AI Engine boundary.
- Use a scoped ContextPack assembled by Market Intelligence.
- Require structured evidence references, contradictions, assumptions, missing
  information, and human questions.
- Show proposals separately from deterministic facts and Risk results.

### Stage 5 - ML and agents cooperating

- Let agents consume validated ML evidence alongside deterministic evidence.
- Keep models and agent proposals independently versioned and auditable.
- Add specialist roles only where a heterogeneous reasoning need is proven.
- Preserve disagreement rather than collapsing it into one authority score.

### Stage 6 - Position monitoring and post-trade intelligence

- Compare original thesis/assessment with current deterministic and contextual
  evidence during a position.
- Produce human alerts/recommendations only.
- Add post-trade/coach analysis that separates process quality from outcome.
- Respect LIVE broker authority and PAPER Trading Core authority for positions.

This sequence is preferable to building a generic agent platform first. It makes
the deterministic dataset and authority boundaries real before introducing
probabilistic complexity.

## 16. ADR Implications

No ADR is created by this investigation.

The conclusions are architecturally significant enough that ADR formalization is
recommended before implementation expands beyond the deterministic assessment.
The decisions should remain separate rather than become one large ADR.

### Proposed ADR 1 - Intelligence Evidence and Authority Boundaries

Formalize:

- fact ownership versus derived evidence versus interpretation;
- deterministic, ML, agent, human, Risk, and execution authority;
- typed outcomes and hard exclusion invariants;
- immutable provenance and validity requirements;
- prohibition on ML/LLM override of authoritative facts or Risk;
- relationship between `IntelligenceContext`, `IntelligenceObservation`,
  `MarketContextAssessment`, StrategyMatch, TradePlan, and RiskEvaluation.

### Proposed ADR 2 - Statistical / ML Intelligence Governance

Formalize when ML is introduced:

- feature snapshot and schema contract;
- model/training dataset/label provenance;
- calibration, uncertainty, drift, abstention, and time-aware evaluation;
- shadow/PAPER promotion path;
- data retention and leakage/selection-bias controls;
- ML non-authority over exclusions, Risk, strategy lifecycle, and execution.

### Proposed ADR 3 - Agent Reasoning and ContextPack Governance

Formalize when structured agents are introduced:

- Market Intelligence as the sole business orchestrator;
- scoped context construction and least privilege;
- agent capability roles versus deployable services;
- structured result and evidence-reference contract;
- unsupported-claim and stale-context handling;
- durable execution/audit and safe degradation;
- human-review boundary and prohibition on execution authority.

### Existing ADRs that should be clarified or cross-referenced

- ADR-014 should be clarified to distinguish the conceptual pipeline from the
  fact/evidence authority model and to include ML as optional evidence without
  making a mandatory synchronous chain.
- ADR-020 should clarify that shared `IntelligenceContext` does not grant equal
  authority to its sections and that deterministic hard exclusions remain
  binding for downstream probabilistic components.
- ADR-021 already supplies much of the AI execution, context classification,
  freshness, degradation, and persistence boundary; future ADRs should extend
  rather than duplicate it.
- ADR-034 should remain the authority for Observation versus StrategyMatch and
  must not be weakened by agent-generated opportunities.
- ADR-028, ADR-031, ADR-041, ADR-042, and ADR-045 remain authoritative for Risk,
  planning, execution, and position authority.

## 17. Recommended Next Design Task

The next design task should be narrow and implementation-adjacent:

> Define the domain contract and acceptance boundaries for the deterministic
> multi-timeframe Trend Context Assessment, including its immutable evidence,
> validity, no-setup outcomes, provenance, persistence references, and
> relationship to Observation and StrategyEvaluation.

That design task should answer:

- which Market Data source snapshots are authoritative and how they are
  referenced;
- the context/setup timeframe roles and freshness rules;
- the deterministic structure, phase, volatility, contradiction, exclusion,
  and invalidation semantics;
- which fields are generic observation metadata versus trend-specific payload;
- how `NO_SETUP`, `UNKNOWN`, and stale evidence are persisted;
- how the assessment is displayed before a Trade Plan;
- how a future ML feature snapshot can consume it without changing the model;
- how an agent may cite it without changing it;
- how human review and no-trade outcomes are joined to later planning and
  outcome data.

This task should not define the ML algorithm, create the Python service, create
the agent runtime, or redesign Risk. Those are later decisions informed by the
durable deterministic evidence.

## End Report

**INVESTIGATION**

COMPLETE

**DETERMINISTIC INTELLIGENCE ROLE**

Authoritative for reproducible derived market evidence: normalization-dependent
measurements, structure, regime, phase, volatility, timeframe contradictions,
levels, exclusions, freshness, completeness, and explicit `UNKNOWN`/`NO_SETUP`.
It does not authorize financial risk or execution.

**ML ROLE**

Produce versioned, calibrated, uncertain statistical evidence for historical
quality, similarity, anomaly detection, or ranking after deterministic
eligibility. ML cannot override hard exclusions, Risk, strategy governance, or
human execution authority.

**AGENT / LLM ROLE**

Reason across selected heterogeneous context, summarize, expose contradictions,
challenge theses, detect missing information, and propose questions or actions.
Agent results are structured proposals with evidence references, not facts,
Risk decisions, strategy promotion, or execution authorization.

**RISK AUTHORITY**

Trading Core assembles authoritative immutable financial context and the
deterministic Risk Engine produces the final account-specific authorization for
the exact Trade Plan. ML and agents cannot soften rejection or unavailable
authoritative facts.

**HUMAN AUTHORITY**

The trader decides whether to pursue an analytical context, confirms or rejects
the thesis and Trade Plan, accepts Risk, and explicitly authorizes execution.
The human remains the final discretionary decision-maker, but cannot bypass the
deterministic Risk and execution safety gates.

**SHARED INTELLIGENCE CONTRACT**

Reuse `IntelligenceContext`, `AnalysisExecution`, `IntelligenceObservation`,
Strategy lineage, and typed evidence contributions. Introduce a typed
`MarketContextAssessment` payload for the first deterministic trend capability
only if its domain need is confirmed. Preserve source references, timestamps,
validity, completeness, rule/model versions, contradictions, exclusions, and
provenance.

**HISTORICAL DATASET STRATEGY**

Persist or reference immutable deterministic assessments, `NO_TRADE` and
rejected contexts, human review decisions, Strategy/Opportunity/TradePlan
lineage, T0/T1 Risk results, execution and position outcomes, thesis changes,
and process-quality reviews. Use stable IDs and temporal joins. Avoid selection
and look-ahead bias; do not retain unrestricted secrets or duplicate all source
payloads by default.

**MULTI_AGENT DIRECTION**

Treat specialist agents initially as scoped Market Intelligence capabilities
under the existing AI Engine governance, not as unrestricted autonomous services.
Use targeted ContextPacks, structured outputs, evidence references, explicit
disagreement, least privilege, and human review. Add separate agent roles only
when their context and operational boundaries are proven.

**FAILURE / DEGRADATION PRINCIPLE**

Optional ML, agent, news, and macro failures degrade interpretation while
preserving deterministic PAPER analysis where possible. Missing, stale, or
contradictory authoritative market/Risk facts produce explicit unknown or
fail-closed behavior. No component replaces missing facts with confidence or
uses disagreement to override higher authority.

**FIRST IMPLEMENTATION SLICE**

Deterministic multi-timeframe Trend Context Assessment in Market Intelligence,
with versioned evidence, provenance, validity, no-setup outcomes, persistence
references, and Decision Workspace presentation before Trade Planning.

**ADR REQUIRED**

YES

**PROPOSED ADRS**

- Intelligence Evidence and Authority Boundaries.
- Statistical / ML Intelligence Governance.
- Agent Reasoning and ContextPack Governance.

Existing ADRs 014, 020, and 021 should be clarified or cross-referenced rather
than duplicated; ADRs 028, 031, 034, 041, 042, and 045 remain authoritative.

**RECOMMENDED NEXT DESIGN TASK**

Define the deterministic Trend Context Assessment domain contract, evidence and
provenance rules, persistence references, no-trade semantics, and boundaries to
Observation, StrategyEvaluation, future ML, agents, human review, and Risk.

**NEXT STORY READY TO DEFINE**

NO. The architectural direction and design task are clear, but human review of
this investigation and the proposed ADR decisions should precede Story scope.

**FILES CREATED**

- `docs/investigations/trading-intelligence-layers-architecture-2026-09-23.md`

**FILES MODIFIED**

- None
