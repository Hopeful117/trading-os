# ADR-101 - Autonomous Trading Research Lifecycle and Governance

## Status

ACCEPTED - final architecture review completed 2026-11-13; implementation
remains subject to the Engineering Story approval gates

## Date

2026-11-13

## Acceptance Record

Human architecture approval is recorded for the lifecycle and governance
direction defined here. Acceptance does not authorize production implementation,
schema migration, AI provider enablement, strategy promotion, LIVE operations or
execution. Those remain subject to the bounded Engineering Stories and their
separate approval gates.

## Decision Makers

- Trading OS Architecture
- Human Engineer
- Project Owner

## Decision Type

Market Intelligence research domain, quantitative research governance and AI
autonomy boundary

## Related Decisions

- ADR-020 - Market Intelligence Architecture
- ADR-021 - Governance of Market Intelligence Analysis Executions
- ADR-034 - Strategy, StrategyMatch and Trading Opportunity Boundaries
- ADR-035 - Strategy Evaluation Live and Backtest Compatibility
- ADR-036 - Split Strategy Governance into Validation and Operational Dimensions
- ADR-037 - Persisted Strategy Definitions as Runtime Source of Truth
- ADR-038 - Empirical Strategy Validation Boundary
- ADR-057 - Historical Market Evidence, Known-At Semantics, and Source Retention
- ADR-060 - Versioned Feature Projection and Immutable FeatureSnapshot
- ADR-062 - Versioned Research Dataset Boundary
- ADR-076 - Market Thesis Authority and Evidence Composition
- ADR-089 - Native Trading Research Agent and MCP Responsibility Boundary
- ADR-092 - Proposed MCP Research Operational Control Boundary
- ADR-090 - Agent Identity, Permissions and Delegated Authorization
- ADR-093 - ML-01 Unsupervised Market-Regime Discovery Protocol
- ADR-095 - ML-01 Exploratory Research Admissibility Protocol
- ADR-100 - Capability-Aware Forex Quote-Based LIVE Tradeability

## Related Evidence

- `docs/architecture/reports/trading-research-engine-audit-2026-11-13.md`
- Stories 0009-0016: strategy domain, evaluator and governance foundation
- Story 0113: realistic execution and transaction-cost research foundation
- Story 0205: AI Engine runtime foundation and Market Thesis interpretation
- Story 0209: controlled adaptive trading research
- Story 0222: agent-native historical research inspection and export
- Story 0236: Forex quote tradeability evidence and active-scan observability

## 1. Context And Problem Statement

Trading OS currently has a deterministic Market Intelligence pipeline that can
acquire and compose evidence, create observations and evaluate a bounded,
developer-defined strategy catalogue. It also has a durable research workflow,
versioned historical dataset contracts, bounded offline ML experiments and an
optional shadow-mode AI Engine.

The current architecture does not yet provide the complete research lifecycle
needed to move from an observed market behavior to a reproducible, quantitatively
reviewed and human-approved operational strategy. In particular:

- strategy discovery is not a production capability;
- generalized backtesting and cost-complete economic evaluation are not
  implemented;
- `ResearchExecution`/`ResearchReport` coordinate bounded research but do not
  represent hypotheses, strategy proposals or accepted validation evidence;
- the AI Engine is advisory and its current execution state is process-memory;
- `StrategyDefinition` represents executable operational semantics, not an
  exploratory research hypothesis;
- `MarketThesis` is a deterministic decision-support authority, not proof of
  profitability;
- the current runtime Forex evidence and account facts may be incomplete.

Without an explicit lifecycle, an AI-generated idea could be mistaken for a
strategy, an experiment result for validation evidence, or a contextual Thesis
for an operational authorization. This ADR proposes the minimum architecture
that keeps those distinctions explicit while enabling future research
automation.

This ADR defines the accepted architecture. It does not itself authorize
implementation, a new service, a schema migration, a strategy promotion, a
LIVE scan or any broker operation.

## 2. Verified Current Capabilities

The following are existing capabilities, not decisions introduced by this ADR:

### 2.1 Evidence and deterministic analysis

- Market Data owns normalized historical evidence and known-at semantics under
  ADR-057.
- Feature projections and immutable snapshots are versioned under ADR-060.
- Research dataset population is explicitly versioned and does not add strategy
  or profitability semantics under ADR-062.
- Market Intelligence owns deterministic analysis, observations, opportunities
  and Market Thesis composition under ADR-076.
- Current Thesis composition preserves supporting, contradicting, missing,
  stale, unknown and not-implemented evidence rather than converting missingness
  into support.

### 2.2 Strategies and governance

- `StrategyDefinition`, `StrategyEvaluation`, `StrategyMatch` and
  `TradingOpportunity` are existing domain concepts under ADR-034.
- The production pipeline reads persisted strategy definitions under ADR-037.
- Governance eligibility is separate from market applicability and evaluator
  execution.
- `ValidationStatus.VALIDATED` requires accepted empirical or deterministic
  validation evidence bound to the exact strategy ID and version under ADR-038.
- `VALIDATED` does not imply `ENABLED`; both remain explicit human decisions.

### 2.3 Research and AI

- `ResearchExecution` and `ResearchReport` already represent the current
  bounded, Java-owned research workflow described by ADR-089 and ADR-092.
- The current `ResearchRequest` carries a list of market IDs, objective and
  depth, and the current application service iterates over the requested
  markets. ADR-089 gives that existing comparative workflow a multi-market
  semantic and this ADR does not change it.
- `AnalysisExecution` remains reusable for technical child work but is not the
  sole aggregate for a multi-market research project or durable research report.
- The AI Engine receives bounded context through `AiEnginePort`; it does not own
  canonical evidence, persistence, strategy promotion, Risk or execution.
- The current AI result supports findings, evidence references, missing
  information, contradictions and adaptive follow-up requests. It does not yet
  provide a dedicated, typed hypothesis-discovery proposal containing a
  measurable outcome, evaluation horizon and falsification contract.
- Story 0209 permits at most one bounded adaptive follow-up capability request
  in its defined workflow. It does not define strategy discovery or backtesting.
- The research ML package provides validated offline experiments and benchmarks,
  but explicitly disclaims profitability claims when fees, slippage, funding,
  execution and tradeability are absent.

### 2.4 Runtime limitations

The 2026-11-13 audit observed an authorized cTrader account as connected but
with incomplete financial facts. Risk and execution were blocked. EUR/USD was
catalogued as an open, tradable cTrader CFD, but the observed position valuation
was unknown. Research architecture must therefore remain account-independent
unless account state is explicitly required, and must not treat current LIVE
availability as historical evidence.

## 3. Architectural Drivers

The proposed architecture is driven by the following requirements:

1. Preserve deterministic Java ownership of canonical market facts, research
   orchestration, governance and reproducible calculation.
2. Permit deterministic, statistical, ML, AI-assisted and hybrid hypothesis
   generation without making an LLM the exclusive source of hypotheses.
3. Make every research conclusion falsifiable, provenance-bearing and
   reproducible from immutable inputs.
4. Prevent an exploratory result from becoming an operational strategy without
   separate quantitative acceptance and human approval.
5. Reuse existing Market Intelligence, research dataset, AI and MCP boundaries.
6. Avoid introducing a research microservice, vector database or event broker
   before a concrete operational requirement exists.
7. Preserve fail-closed evidence semantics and all existing Risk, human
   authorization and broker boundaries.
8. Support both user-triggered research and future bounded background agents.
9. Keep research account-independent where account state is not necessary.
10. Make invalid, inconclusive, stale, superseded and expired research explicit.

## 4. Decision Proposal

Trading OS should evolve the existing Java-owned Market Intelligence research
workflow into a bounded research lifecycle with distinct domain objects for:

```text
market evidence
    -> ResearchHypothesis
    -> StrategyProposal
    -> ResearchExperiment / ExperimentManifest
    -> ExperimentResult
    -> ValidationEvidence
    -> human ResearchDecision
    -> versioned operational StrategyDefinition
    -> deterministic operational Market Thesis
```

The objects are related, but none may be silently substituted for another.

The first architectural evolution should extend Market Intelligence rather than
introduce a dedicated Research Service. A separate service remains a future
option if isolation, scale or ownership requires it after the domain and
contracts are proven.

## 5. Proposed Domain Model

The following model is the minimum coherent boundary. Names are proposed and
may be refined during Story analysis.

### 5.1 `ResearchExecution` / `ResearchRun`

**Responsibility:** Durable orchestration of one bounded research request,
including actor, objective, market scope, budgets, child work and terminal
status.

**Existing form:** `ResearchExecution` already exists and should remain the
primary execution aggregate. A separate `ResearchRun` entity is not initially
required. `ResearchRun` is a conceptual name for a research execution that may
contain several hypotheses or experiments.

**Identity:** Existing research ID, actor/scope identity and idempotency
fingerprint. A rerun with a changed manifest is a new run.

**Lifecycle:** `REQUESTED`, `RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`,
`FAILED`, `CANCELLED`, `EXPIRED`.

**Invariants:** Scope, objective, budgets and request fingerprint are immutable
after acceptance. Terminal runs cannot acquire new child work. Cancellation
does not authorize unsafe retries.

**Persistence:** Existing Market Intelligence persistence; durable before work
dispatch as required by ADR-092.

**Provenance:** Actor, research scope, market IDs, objective version, child
execution IDs, dataset/manifest references, model and prompt versions when AI
participates.

**Owner and authorization:** Market Intelligence owns lifecycle and research
authorization. Trading Core owns agent identity and grants where an agent is
the caller. MCP and Angular remain clients.

### 5.2 `ResearchHypothesis`

**Responsibility:** A structured, falsifiable claim about an observed market
behavior. It describes what may be true, under which conditions, what future
observable would support it, and what would falsify it.

**Identity:** Hypothesis ID plus immutable version. A materially changed claim,
scope, outcome, condition or evidence contract is a new version, not an update.

**Lifecycle:**

```text
DISCOVERED
  -> HYPOTHESIS_PROPOSED
  -> ACCEPTED_FOR_TESTING
  -> EXPERIMENT_PLANNED
  -> TESTED
```

Terminal or side states are `REJECTED`, `INCONCLUSIVE`, `INVALIDATED`,
`SUPERSEDED` and `EXPIRED`. A hypothesis may be accepted for testing without
being accepted as true.

**Invariants:** It must reference a research run, canonical market identity,
timeframe, evidence cutoff and generation method. It must contain an observable
expected outcome and falsification conditions. Subjective AI confidence is not
stored as a calibrated trading probability.

**Persistence:** Market Intelligence research persistence. The hypothesis is
not a `StrategyDefinition`, `MarketThesis`, `TradingOpportunity` or Risk fact.

**Provenance:** Evidence IDs/fingerprints, observation and artifact lineage,
dataset references, algorithm/model version, prompt template version if
applicable, generator type and research-run ID.

**Owner and authorization:** Market Intelligence owns the canonical proposal
and validation of references. AI may propose content but cannot publish an
authoritative hypothesis without Java validation and the applicable human or
agent approval checkpoint.

**Minimum contract:**

- market ID, canonical instrument and provider/product identity;
- research timeframe, evaluation cutoff and regime/context scope;
- observed behavior and supporting evidence references;
- proposed statistical or causal explanation, explicitly marked as a
  hypothesis rather than a fact;
- expected measurable outcome and horizon;
- applicability conditions and falsification conditions;
- required, available, missing and contradictory evidence;
- limitations and uncertainty statement;
- generation method, algorithm/model version and research-run identity;
- immutable input fingerprint.

### 5.3 `StrategyProposal`

**Responsibility:** A research-only deterministic specification of how a
hypothesis could be operationalized and tested. It translates a claim into
entry, exit, invalidation and position-management semantics without granting
execution eligibility.

**Identity:** Proposal ID plus immutable version, linked to one hypothesis
version. A change to semantics, parameters, applicability or cost assumptions
creates a new proposal version.

**Lifecycle:** `DRAFT`, `SPECIFIED`, `READY_FOR_EXPERIMENT`, `REJECTED`,
`SUPERSEDED`, `EXPIRED`, `CONVERTED_TO_STRATEGY_CANDIDATE`.

**Invariants:** Every rule must reference an approved deterministic primitive
or a typed observation. Entry, exit, invalidation and applicability must be
complete enough for an experiment or be explicitly marked incomplete. Arbitrary
LLM-generated code is forbidden. A proposal cannot be evaluated as an
operational strategy merely because it has a name or an ID.

**Persistence:** Market Intelligence research persistence. It may reference
existing `StrategyDefinition` semantics for comparison, but it is not itself a
runtime strategy definition.

**Provenance:** Originating hypothesis version, primitive library versions,
feature/projection versions, parameter values, cost/execution assumptions and
generator/reviewer identity.

**Owner and authorization:** Market Intelligence owns the proposal contract.
Human approval is required before an experiment that could consume bounded
compute or produce candidate evidence, unless an explicit research grant
authorizes the bounded operation.

### 5.4 `ResearchExperiment` and `ExperimentManifest`

**Responsibility:** `ResearchExperiment` is the lifecycle of one execution of a
specified hypothesis or strategy proposal. `ExperimentManifest` is the
immutable description of exactly what was executed.

**Identity:** Experiment ID and immutable manifest fingerprint. A rerun with
different input, code, model, parameter, seed, split or policy is a new
experiment.

**Lifecycle:** `PLANNED`, `READY`, `RUNNING`, `COMPLETED`, `INVALID`,
`INCONCLUSIVE`, `FAILED`, `CANCELLED`, `EXPIRED`.

**Invariants:** The manifest is complete before execution. The experiment must
  fail closed when required evidence, temporal eligibility or cost assumptions
  are unavailable. Future outcomes cannot enter features or fitting decisions.
  A successful process exit does not imply a valid scientific experiment.

**Persistence:** Market Intelligence owns orchestration and manifest metadata.
Large datasets and model outputs remain controlled artifacts behind existing
dataset/export boundaries. No unrestricted filesystem path is exposed to MCP.

**Minimum manifest:**

- dataset IDs, versions, population specification and dataset fingerprint;
- canonical market/provider/instrument identity;
- interval, historical range, cutoff and known-at policy;
- data quality and continuity constraints;
- feature projection and analyzer versions;
- hypothesis and strategy proposal versions;
- parameters and approved primitive versions;
- cost, spread, slippage, latency, funding and fill assumptions;
- position lifecycle and protection semantics;
- chronological train/validation/test partitions, purge and embargo rules;
- random seeds and dependency versions where applicable;
- Java code/config fingerprints;
- Python package/model/prompt fingerprints when applicable;
- resource budget, creator, authorization and timestamps;
- result and artifact references.

Immutable manifest fields cannot be amended after the experiment starts.

### 5.5 `ExperimentResult`

**Responsibility:** Immutable output of an experiment, including raw metrics,
quality diagnostics, exclusions, uncertainty and limitations.

**Identity:** Result ID plus experiment/manifest fingerprint. A corrected or
recomputed result is a new result linked to the prior result; existing results
are not overwritten.

**Lifecycle:** `PRODUCED`, `QUALITY_CHECKED`, `EXPLORATORY`, `INCONCLUSIVE`,
`INVALID`, `SUPERSEDED`.

**Invariants:** It must state the effective sample, exclusions, missingness,
partition usage, model/strategy parameters and whether costs are complete. It
must distinguish exploratory metrics from simulated economic results and from
accepted validation evidence.

**Persistence:** Market Intelligence metadata and immutable research artifact
references. Python produces bounded statistical outputs, but Java validates the
experiment contract and stores canonical lifecycle/provenance metadata.

**Owner and authorization:** Market Intelligence owns interpretation of result
status. AI may summarize it but cannot change status or certify it.

### 5.6 `ValidationEvidence`

**Responsibility:** Immutable, reviewable evidence package that may justify
`StrategyDefinition.validationStatus = VALIDATED` for one exact strategy ID and
version.

**Identity:** Validation evidence ID, strategy ID/version, experiment/result
references and acceptance fingerprint.

**Lifecycle:** `SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`, `EXPIRED`,
`REVOKED`.

**Invariants:** It must reference completed valid experiments and their exact
manifests/results. It must include quantitative acceptance checks, limitations,
sample sizes, robustness dimensions and reviewer decision. It cannot be
created solely from an AI response, a passing software test, a Market Thesis,
or a gross return proxy that omits required costs.

**Persistence:** Market Intelligence owns evidence metadata and review
workflow. The immutable underlying artifacts use existing artifact and dataset
boundaries. The opaque `validationEvidenceRef` on `StrategyDefinition` may
point to this accepted evidence package.

**Authorization:** Human approval is mandatory for `ACCEPTED`. Acceptance does
not enable the strategy.

### 5.7 `ResearchDecision`

**Responsibility:** Immutable audit record for a human or explicitly authorized
agent checkpoint: investigate, test, reject, accept evidence, approve strategy,
suspend or retire.

**Identity:** Decision ID, subject type/ID/version, actor, decision type and
decision fingerprint.

**Invariants:** Decisions are append-only and must include the evidence/version
being decided upon. A client or AI response cannot impersonate a human
decision. Agent decisions require an explicit grant with the exact permission
and scope.

**Persistence:** Market Intelligence for research decisions; Trading Core
remains authoritative for account-scoped execution authorization. The decision
record cannot mutate Risk or broker state.

### 5.8 `ResearchArtifact`

No new generic `ResearchArtifact` aggregate is proposed at this time. Existing
artifact execution, dataset manifest, fingerprint and controlled export
boundaries should be reused. A new artifact type is justified only when an
immutable research object cannot be represented without weakening provenance or
retention semantics.

## 6. Hypothesis Generation Architecture

Hypothesis generation is a bounded research capability, not a single prompt.
The input is a deterministic, cutoff-bounded `ResearchContext` assembled by
Market Intelligence from approved evidence. Generators may include:

1. **Deterministic discovery:** change-point, regime-transition, conditional
   association, seasonality or cross-market relationship algorithms with
   versioned parameters.
2. **Statistical/ML discovery:** offline models over validated feature and
   outcome contracts, following ADR-093/095-style chronological and
   no-leakage rules.
3. **AI-assisted interpretation:** the existing AI Engine proposes structured
   explanations, relationships, missing evidence and falsification conditions
   over supplied context.
4. **Hybrid discovery:** deterministic candidate findings are interpreted and
   prioritized by AI, while Java validates all references and the final
   hypothesis schema.

The generator output is untrusted until Java validates:

- canonical market and evidence identities;
- cutoff and known-at semantics;
- required evidence and missingness;
- schema completeness and falsifiability;
- generator/model/prompt provenance;
- scope, time and compute budgets;
- absence of claims unsupported by supplied evidence.

The AI's confidence field, if retained for explanation, is qualitative and
explicitly non-calibrated. It is never a trading probability or acceptance
score.

Candidate discovery must preserve null and negative findings. A run that finds
no falsifiable relationship is a valid `NO_HYPOTHESIS` or `INCONCLUSIVE` result,
not a reason to lower evidence requirements.

### 6.1 First workflow: genuine AI-assisted discovery

The first vertical workflow must use the AI Engine as a hypothesis-formulation
component, not merely as a summarizer of a deterministic pattern. The workflow
is:

```text
User selects one canonical EUR/USD market and a research horizon
    -> Java validates scope and historical availability
    -> Java assembles deterministic ResearchContext at cutoff T
    -> optional deterministic discovery produces candidate observations
    -> AI receives only the bounded ResearchContext and discovery output
    -> AI proposes zero or more structured relationships/hypotheses
    -> Java validates schema, evidence references and temporal claims
    -> Java persists versioned proposals and a bounded ResearchReport projection
    -> human reviews the proposal; no experiment is implicitly authorized
```

The deterministic discovery output is an input and comparison baseline, not a
required answer. The AI may formulate a relationship that was not supplied as
a predefined strategy, provided it is expressed as a falsifiable claim over
the supplied evidence. The AI must not be instructed with a fixed expected
hypothesis, strategy name, entry rule or direction.

The first demonstration is successful only when all of the following are
visible in the persisted report or proposal projection:

- the exact research request contains no predefined strategy semantics;
- the input fingerprint identifies the deterministic context and cutoff;
- every supporting claim references supplied evidence IDs;
- the relationship and explanation are marked as a hypothesis, not a fact;
- the expected outcome is measurable and has an explicit horizon;
- applicability and falsification conditions are present;
- missing, contradictory and insufficient evidence remain visible;
- provider/model/prompt/contract provenance is recorded;
- malformed, unknown-reference or unsupported claims are rejected or marked
  `INCONCLUSIVE` rather than silently accepted;
- a run may validly return `NO_HYPOTHESIS`, `INCONCLUSIVE`,
  `INSUFFICIENT_EVIDENCE` or `AI_UNAVAILABLE`.

A deterministic pattern selected from `BuiltinStrategies` without an AI
formulation is not evidence of this capability. An AI restatement of a supplied
pattern is also not sufficient. The test fixture must distinguish:

1. AI-generated formulation from a context with no prescribed hypothesis;
2. deterministic candidate discovery only;
3. LLM summarization with no structured hypothesis;
4. hallucinated or unsupported evidence references;
5. insufficient evidence;
6. malformed AI output;
7. unavailable AI provider;
8. valid zero-hypothesis output.

The AI is not required to produce a positive hypothesis on every run. A null
result is scientifically preferable to an invented relationship.

### 6.2 Minimum `ResearchHypothesis` contract for the first slice

The first implementation needs only one new research object and must not
implement the complete long-term lifecycle. Its minimum immutable proposal
fields are:

| Field | Requirement |
|---|---|
| `hypothesisId` and `version` | Stable identity; material revisions create a new version |
| `researchExecutionId` | Parent durable execution |
| `marketId`, canonical instrument, provider and product type | Exact market identity |
| `timeframe`, evaluation horizon and `sourceCutoff` | Temporal scope and measurable future window |
| `observedBehavior` | Evidence-grounded description of what was observed |
| `proposedRelationship` | Explicit testable relationship, marked hypothetical |
| `expectedOutcome` | Measurable outcome and direction/threshold semantics where applicable |
| `applicabilityConditions` | Conditions under which the claim is intended to hold |
| `falsificationConditions` | Observable conditions that would weaken or reject the claim |
| `supportingEvidenceReferences` | References validated against the ResearchContext |
| `missingEvidence` and `contradictoryEvidence` | First-class unresolved limitations |
| `limitations` | Scientific and data limitations |
| `generatorType` and `generatorVersion` | Deterministic, statistical, AI or hybrid provenance |
| `model`, `promptVersion` and AI execution reference | Required only when AI participates |
| `inputFingerprint` | Fingerprint of context, cutoff, policy and generator inputs |
| `proposalValidationStatus` | Java-owned status of the proposal contract |

The first slice may use these proposal validation statuses:

- `SCHEMA_VALID`: the typed structure is valid;
- `EVIDENCE_GROUNDED`: Java verified the referenced evidence and temporal
  constraints;
- `REJECTED`: schema, identity, provenance or claim validation failed;
- `INCONCLUSIVE`: the result is structurally usable but cannot support a
  sufficiently grounded proposal;
- `NO_HYPOTHESIS`: the run produced no proposal and is a run outcome, not a
  hypothesis version.

These statuses do not mean that the market relationship is true. In particular:

- `EVIDENCE_GROUNDED` is not empirical support;
- `ACCEPTED_FOR_TESTING` is a later human/research authorization state;
- `ExperimentResult` requires a later experiment lifecycle;
- `ValidationEvidence` requires quantitative review;
- `VALIDATED` and `ENABLED` remain the strategy governance states from
  ADR-036/038.

The first slice must not expose `SUPPORTED`, `PROFITABLE`, `VALIDATED` or
`ENABLED` as hypothesis statuses. Proposal revisions are append-only and must
retain the prior input fingerprint and provenance.

### 6.3 Additive AI contract evolution

The existing `AiEnginePort`, asynchronous execution, shadow-mode restriction,
provider provenance and Java validation boundary should be reused. A new Python
service or direct MCP-to-Python path is not required.

The smallest contract evolution is:

1. Add an explicit research mode such as `HYPOTHESIS_DISCOVERY` beside the
   existing `THESIS_INTERPRETATION` and `ADAPTIVE_RESEARCH` modes.
2. Add a bounded `hypothesisProposals` collection to the structured AI draft,
   with the fields in the minimum contract above that are suitable for model
   output.
3. Carry an explicit discovery contract version and research execution ID in
   the authorized request/context. Java remains the source of the market ID,
   cutoff, input fingerprint and provider/model provenance.
4. Preserve the existing `findings`, `missingInformation`, `contradictions`
   and `requestedAnalysis` fields for comparative and adaptive research.
5. Validate every evidence UUID, cutoff, market identity, maximum length and
   required field in Java before persistence.

The AI response must not be allowed to assert its own `EVIDENCE_GROUNDED`,
`SUPPORTED`, `VALIDATED` or `ENABLED` status. The Java adapter maps a valid
response to the proposal validation status and records the existing
`AiProvenance`/execution reference. If the current Pydantic/Java contract cannot
carry the additional fields, the change is a versioned additive DTO contract;
it is not a second AI architecture.

The AI Engine remains shadow-mode compatible: the first workflow may use the
existing mock provider and the shadow-mode real-provider contract, but it does
not enable non-shadow execution, grant new trading permissions or change the
AI Engine's authority.

### 6.4 First workflow and ADR-089 compatibility

ADR-089's existing workflow is comparative: it accepts explicitly selected
multiple markets, assembles a multi-market report and preserves its existing
semantics. ADR-101 does not reinterpret that workflow or lower its market-count
validation.

The recommended compatibility-preserving extension is a distinct typed research
objective, for example:

```text
COMPARATIVE_MARKET_RESEARCH       // existing ADR-089 semantics; multi-market
SINGLE_MARKET_HYPOTHESIS_DISCOVERY // new bounded capability; exactly one market
```

The objective type is part of the request fingerprint. Server-side validation
enforces the cardinality and context policy:

- comparative requests retain the existing multi-market requirement;
- hypothesis-discovery requests accept exactly one canonical market;
- both use the same actor/scope authorization, idempotency, budgets,
  persistence and bounded report retrieval;
- the single-market mode is account-independent and cannot request account,
  broker, Risk or execution context;
- the single-market mode produces hypotheses and evidence projections, not
  comparative conclusions or operational opportunities.

This is preferred over a free-form objective string because the semantic
cardinality and authorization boundary are server-enforced and fingerprinted.
It is also preferred over a new dedicated capability endpoint because it reuses
the existing Java `ResearchExecution`/`ResearchReport` orchestration and MCP
application boundary. The public contract extension is additive: existing
comparative clients retain their current defaults and behavior.

The exact request field name and route representation remain implementation
details for the future Story. The architecture decision is the distinct,
typed objective and its cardinality policy.

## 7. Strategy Proposal Representation

### 7.1 Recommended approach

Use a constrained declarative strategy specification interpreted by a
deterministic evaluator over an approved versioned primitive library.

The proposal should reference primitives such as typed observations,
comparisons, temporal windows, conjunction/disjunction, entry/exit conditions,
invalidation conditions and position-management policies. Each primitive has a
stable identity, version, input contract and deterministic semantics.

An approved proposal may later be materialized as a versioned
`StrategyDefinition`, but conversion is a governed projection, not an automatic
promotion. The evaluator must be usable by both future historical evaluation
and operational scanning to avoid semantic drift.

### 7.2 Alternatives

| Approach | Safety | Testability | Expressiveness | Recommendation |
|---|---|---|---|---|
| Constrained declarative DSL and approved primitives | High | High | Bounded | Recommended |
| Generate evaluator source code and require review | Medium | Medium/High after review | High | Offline research only; not first path |
| Arbitrary LLM-generated code in production | Unacceptable | Low | Unbounded | Rejected |
| Store only a `StrategyDefinition` record | Medium | High for existing semantics | Low for novel behavior | Insufficient |
| Hardcoded templates | High | High | Very low | Existing baseline, not discovery |

Generating a `StrategyDefinition` row is not sufficient. A genuinely new
strategy requires executable semantics, typed inputs, evaluator support,
deterministic tests, historical evaluation and governance evidence. A proposal
with an unknown primitive or evaluator is `NOT_EXECUTABLE` and cannot enter the
operational pipeline.

### 7.3 Existing code-defined strategies

`BuiltinStrategies` remains the software-owned seed catalogue under ADR-037.
Existing strategy identities and semantics are not replaced by this ADR.

Future declarative proposals may be compared with or compiled to the same
`StrategyDefinition`/`StrategyEvaluator` contracts only after explicit
compatibility validation. Existing code-defined strategies retain their
current governance states. This ADR does not validate, enable, disable or
rewrite them.

## 8. Research Lifecycle And Governance

The lifecycle is a set of related state machines, not one enum that collapses
discovery, experiment validity, validation and operational activation.

### 8.1 Hypothesis lifecycle

```text
DISCOVERED
    -> HYPOTHESIS_PROPOSED
    -> ACCEPTED_FOR_TESTING
    -> EXPERIMENT_PLANNED
    -> TESTED
```

At any appropriate point:

```text
REJECTED | INCONCLUSIVE | INVALIDATED | SUPERSEDED | EXPIRED
```

`ACCEPTED_FOR_TESTING` means only that the claim may consume the approved
research budget. It does not mean the claim is true or useful.

### 8.2 Experiment lifecycle

```text
PLANNED -> READY -> RUNNING -> COMPLETED
                       |          |
                       v          v
                   CANCELLED   INCONCLUSIVE / INVALID / FAILED
```

An experiment is `COMPLETED` only when the execution produced a result. It is
not necessarily scientifically valid or quantitatively positive.

### 8.3 Validation lifecycle

```text
ExperimentResult
    -> ValidationEvidence SUBMITTED
    -> UNDER_REVIEW
    -> ACCEPTED | REJECTED | EXPIRED | REVOKED
```

Only `ACCEPTED` evidence can be referenced by `recordValidation`. Acceptance is
an explicit human decision and is bound to the exact strategy ID/version.

### 8.4 Operational lifecycle

ADR-036 remains authoritative:

```text
UNVALIDATED / DISABLED
VALIDATED / DISABLED
VALIDATED / ENABLED
RETIRED
```

`VALIDATED` does not imply `ENABLED`. No research lifecycle transition may
automatically enable a strategy, create an Opportunity, create a TradePlan,
request Risk evaluation or submit broker execution.

### 8.5 Required approval checkpoints

The architecture distinguishes:

1. Approval to investigate a market or research objective.
2. Approval to accept a hypothesis for bounded testing.
3. Acceptance or rejection of quantitative evidence.
4. Approval to create or adopt an operational strategy version.
5. Operational enablement of that strategy.
6. Human approval of any eventual TradePlan/execution intent.

An agent may be authorized for the first two checkpoints only if the exact
research grant, scope and budget permit it. Acceptance of validation and
operational enablement remain human-governed unless a future ADR explicitly
changes that rule.

## 9. Quantitative Validation Boundary

The future quantitative evaluator must be deterministic and manifest-driven.
At minimum it must represent or explicitly reject:

- chronological event ordering and known-at eligibility;
- no look-ahead and no future-outcome leakage;
- train/validation/test separation, purging and embargo;
- walk-forward evaluation where required;
- bid/ask spread, fees, funding and slippage as separate non-duplicated costs;
- latency and fill assumptions, with missingness when unsupported;
- position lifecycle, protection, exits and partial fills when relevant;
- drawdown, exposure and risk-adjusted results;
- effective sample size and dependence/overlap diagnostics;
- parameter sensitivity and perturbation analysis;
- market-regime and cross-market robustness;
- multiple-hypothesis and selection-bias controls;
- uncertainty intervals and scientific limitations.

The architecture distinguishes four result classes:

| Class | Meaning | Can justify `VALIDATED`? |
|---|---|---|
| Exploratory metric | Descriptive or discovery output | No |
| Backtest result | Result of a specified historical simulation | No, by itself |
| Robustness evidence | Repeated or out-of-sample stability evidence | Not automatically |
| Accepted validation evidence | Human-reviewed package satisfying approved criteria | Yes |

The authority split is:

- Market Data: historical evidence and temporal truth.
- Market Intelligence Java: manifest validation, deterministic orchestration,
  lifecycle, provenance and accepted evidence boundary.
- Python research package: bounded statistical/ML computation over validated
  exports; it cannot redefine canonical features, outcomes or governance.
- Human reviewer: acceptance or rejection of validation evidence.
- Strategy domain: exact binding of accepted evidence to `StrategyId + version`.
- Trading Core/Risk/Broker: all account, financial, authorization and execution
  decisions.

No AI output can manufacture metrics, repair missing costs, certify a result or
change an experiment status.

The quantitative thresholds, minimum samples, confidence procedures and
mandatory robustness dimensions are intentionally not fixed by this ADR. They
require a separate scientific and product approval because inventing them here
would turn an architecture proposal into an unreviewed trading policy.

## 10. Market Thesis Relationship

### 10.1 No duplicate canonical Thesis entity

The existing `MarketThesis` remains the canonical deterministic operational
composition. This ADR does not introduce `ExploratoryMarketThesis` as a second
canonical entity.

Exploratory research should use `ResearchHypothesis`, its evidence snapshot and
the existing advisory `ResearchReport`. This preserves the distinction between
an investigative claim and a current-market operational assessment.

If product language needs to call a research narrative a "thesis", the API and
UI must label it explicitly as an exploratory hypothesis/report and must not
reuse the canonical `MarketThesis` state or identity.

### 10.2 Operational Thesis

An operational `MarketThesis` may consume:

- approved and operationally eligible strategy semantics;
- current deterministic market evidence;
- strategy-specific evidence requirements;
- current trend, momentum, tradeability and other capabilities;
- explicit missingness, contradictions and freshness.

An approved research result can influence which strategy definition or
strategy-specific evidence contract is available to the operational pipeline,
but it cannot override current evidence or directly create a Thesis state.

### 10.3 Evidence and conditions

The existing conditions remain deterministic and fail closed:

- `TREND_DIRECTIONAL` is a current directional condition, not a proof of edge;
- `MOMENTUM_CONFIRMATION` is strategy/context confirmation and may remain
  pending or contradictory;
- `TRADEABILITY_CONTEXT` is bounded by the applicable profile and does not turn
  a quote into Level II liquidity suitability;
- `RANGE_CONTEXT_STALE`, `NEWS_CONTEXT_UNAVAILABLE`,
  `SPREAD_POLICY_UNAVAILABLE` and `LIQUIDITY_UNAVAILABLE` remain explicit
  missing/stale/unavailable evidence, never positive evidence.

Universal safety constraints include identity, cutoff, freshness, data
integrity, tradeability predicates required by the active profile and Risk/
execution boundaries. Strategy-specific research requirements include the
features, regimes, indicators, horizons and invalidation rules needed by one
proposal. A strategy may require more evidence than the universal minimum, but
research must not weaken universal requirements to increase candidate count.

Thesis versions, input fingerprints, evidence roles and source provenance remain
those defined by ADR-076 and the current `MarketThesisComposer`.

## 11. Service Ownership And Contracts

### Market Data Service

- Owns provider-neutral market facts, historical evidence, known-at semantics,
  market identity and source provenance.
- Supplies read-only evidence to research.
- Does not generate hypotheses, select strategies or interpret profitability.

### Market Intelligence

- Owns research scope validation, `ResearchExecution`, hypotheses, proposals,
  experiment manifests/results metadata, validation evidence workflow,
  deterministic context assembly and Market Thesis.
- Coordinates Java deterministic capabilities and the AI Engine through typed
  ports.
- Owns research authorization at the application boundary and persists the
  lifecycle before dispatch.
- Must not become a broker or account execution engine.

### Existing `research/ml` package

- Consumes validated dataset bundles and executes approved offline statistical
  and ML logic.
- Produces bounded result artifacts and diagnostics.
- Does not acquire provider data, grant itself authorization, define canonical
  Market Data semantics or promote strategies.

### AI Engine

- Provides bounded hypothesis formulation, interpretation, contradiction and
  missing-evidence analysis over authorized context.
- May not access Trading OS repositories, brokers, credentials or unrestricted
  accounts.
- May not persist canonical research state, certify metrics, promote strategies
  or call Market Intelligence autonomously.

### Trading Core

- Owns agent identity, grants and account-scoped authorization.
- Remains authoritative for execution intentions, account workflows and
  human-authorized execution.

### Risk Domain

- Owns deterministic financial authorization and remains independent of
  research conclusions.

### Broker Service

- Owns provider credentials, provider connectivity, broker facts, orders,
  positions and reconciliation.

### Gateway and MCP

- Gateway authenticates and routes.
- MCP exposes bounded research commands and reads through public Gateway
  contracts, as defined by ADR-089/092.
- Neither owns research orchestration, prompt logic, persistence or scientific
  validity.

### Future Research Service

No dedicated service is required by this ADR. A future service may be justified
only if experiment compute isolation, independent scaling, ownership or failure
containment cannot be achieved within Market Intelligence plus the existing
Python consumer boundary.

## 12. AI Autonomy And Orchestration

The following levels are proposed:

### Level 0 - Observation

AI interprets supplied evidence. No persistent hypothesis is created.

### Level 1 - Proposal

AI creates a structured, provenance-bearing `ResearchHypothesis` proposal.
Java validates references and budgets. Human review is required before testing
unless an explicit bounded grant permits hypothesis acceptance.

### Level 2 - Bounded Experimentation

An accepted hypothesis and proposal execute an immutable manifest against
validated offline evidence. AI may summarize the result but cannot alter it.

### Level 3 - Research Automation

An agent may initiate bounded research runs under explicit market scope,
permission, compute/time/output budgets, concurrency limits, expiration and
cancellation. It cannot accept validation evidence or enable strategies.

### Level 4 - Operational Recommendation

Human-approved validated strategy versions may be consumed by operational
Market Intelligence. Current evidence and Thesis gates remain authoritative.
There is no autonomous trading authority.

All levels require:

- authenticated actor or service identity;
- explicit research scope and permission;
- idempotency and request fingerprinting;
- durable status before dispatch;
- cancellation and expiration;
- bounded retries and no blind retry of ambiguous writes;
- prompt/model/provider provenance when AI participates;
- bounded context and output disclosure;
- audit records for every approval checkpoint;
- no broker, Risk, TradePlan or execution capability.

## 13. Alternatives Considered

### Alternative A - Extend Market Intelligence and reuse the AI Engine

**Description:** Add research hypotheses, proposals, experiment manifests and
validation workflow to the existing Java Market Intelligence research boundary.
Reuse `ResearchExecution`, `ResearchReport`, existing dataset/export contracts,
`AiEnginePort`, PostgreSQL persistence and the Python research package.

**Advantages:**

- aligns with ADR-089 and ADR-092;
- preserves one Java authority for lifecycle, authorization and provenance;
- avoids another microservice and network contract;
- reuses existing evidence, Thesis and strategy boundaries;
- supports the first EUR/USD vertical slice with low deployment complexity.

**Costs:**

- Market Intelligence grows broader and must preserve clear module boundaries;
- long-running statistical workloads need explicit isolation and budgets;
- persistence and API contracts must be extended carefully.

### Alternative B - Dedicated Research Service

**Description:** Introduce a separate Research Service owning research projects,
hypotheses, experiments and validation, with typed contracts to Market Data,
Market Intelligence and AI Engine.

**Advantages:**

- stronger compute and deployment isolation;
- independent scaling and potentially clearer research ownership;
- suitable if research becomes a large multi-team product.

**Costs:**

- new service, persistence, authentication, routing and operational lifecycle;
- duplicates or fragments existing `ResearchExecution` and report boundaries;
- increases provenance and migration complexity;
- creates pressure to duplicate Thesis/strategy semantics across services.

**Recommendation:** Defer. The current evidence does not show a requirement
that cannot be satisfied by a bounded Market Intelligence extension.

### Alternative C - OpenCode/Agent-Owned Research Workflow

**Description:** Let the client or agent coordinate evidence, prompts,
experiments and persistence.

**Rejected:** It makes lifecycle, authorization, retries, provenance and state
depend on a client session, contradicts ADR-089/092 and cannot safely support
future Angular or background agents.

### Alternative D - AI-Generated Production Code

**Description:** Allow an LLM to generate evaluator code dynamically and execute
it in the production scanner.

**Rejected:** It is not reproducible or safely bounded, creates an execution
authority outside governance, complicates code review and allows arbitrary
semantics to enter the trading path. Generated code may be an offline research
artifact only, followed by explicit human-reviewed implementation.

## 14. Consequences And Trade-Offs

### Positive consequences

- Hypotheses, strategies, experiments, results and validation acquire distinct
  meanings and audit trails.
- Existing deterministic and AI boundaries remain intact.
- Research can evolve from user-triggered to bounded background operation.
- A future backtest can share strategy semantics with operational evaluation.
- Market Thesis remains authoritative for contextual interpretation without
  becoming a profitability certificate.
- No new infrastructure is required for the first vertical slice.

### Costs and risks

- Market Intelligence persistence and domain complexity increase.
- Quantitative acceptance criteria require separate scientific approval.
- A declarative primitive library limits expressiveness until safely expanded.
- Research results may remain inconclusive for long periods when evidence is
  incomplete; this is an intended fail-closed behavior.
- Python/Java contract versioning and artifact retention require discipline.
- A new lifecycle does not by itself produce a statistically valid edge; data
  quality and execution realism remain hard prerequisites.

## 15. Security And Governance Invariants

The following invariants are mandatory if this proposal is accepted:

1. Research is non-trading by default and has no implicit broker capability.
2. Account-independent research must not require account state or credentials.
3. Agent permissions are server-side, exact, scoped and revocable.
4. Client-supplied actor, account, market, dataset, provider or evidence IDs
   cannot establish authority.
5. AI output is untrusted input and must be schema-, reference- and budget-
   validated in Java.
6. No LLM output can create arbitrary production code or an executable strategy.
7. Missing, stale, contradictory or unknown evidence never becomes support.
8. Current LIVE quotes cannot be reused as historical evidence.
9. Export completion is not scientific validity.
10. Experiment completion is not quantitative validation.
11. Accepted validation evidence is bound to exact strategy ID and version.
12. `VALIDATED` never automatically becomes `ENABLED`.
13. Operational Thesis, Risk, TradePlan and execution authorities remain
    unchanged.
14. Ambiguous external outcomes are not blindly retried.
15. Research artifacts contain no credentials, bearer tokens or unrestricted
    filesystem paths.

## 16. Migration And Phased Implementation

This ADR proposes no immediate implementation. If accepted, work should be
sequenced as follows.

### Phase 1 - Research contract and first vertical slice

Extend the existing research boundary with a durable, non-trading
`ResearchHypothesis` proposal and evidence provenance for one
`SINGLE_MARKET_HYPOTHESIS_DISCOVERY` EUR/USD research run. The workflow must
assemble a deterministic `ResearchContext`, invoke the structured AI
hypothesis-discovery mode when available, validate the result in Java and
expose a bounded read-only proposal projection. No strategy creation, backtest
or operational Thesis mutation.

The first slice deliberately does not introduce the complete hypothesis state
machine, `StrategyProposal`, experiment lifecycle or `ResearchDecision`
aggregate. It may expose a `REVIEW_REQUIRED` report status and a read-only
human review surface. Persisted accept/reject decisions belong to the next
Story unless the existing authorization and persistence boundary can support a
small append-only decision without introducing a generic workflow framework.

### Phase 2 - Durable hypothesis lifecycle

Add accepted/rejected/inconclusive/superseded lifecycle states, actor decisions,
budgets and retrieval projections. Preserve existing `ResearchExecution` and
`ResearchReport` contracts.

### Phase 3 - Constrained strategy proposals

Define approved deterministic primitives and a research-only declarative
proposal contract. Prove that proposals cannot execute unless an evaluator and
typed inputs exist.

### Phase 4 - Manifest-driven historical experiments

Extend validated dataset boundaries with experiment manifests, controlled
execution and immutable result artifacts. Start with objective research metrics
and explicit non-profitability semantics.

### Phase 5 - Economic execution realism

Implement only the evidence-supported execution and cost policies from the
follow-up Stories for ADR-0113. Do not claim net profitability before spread,
fees, funding, slippage, latency and fill assumptions are supported.

### Phase 6 - Quantitative validation review

Approve scientific acceptance criteria, robustness tests, uncertainty and
multiple-testing policy. Introduce `ValidationEvidence` acceptance bound to an
exact strategy version.

### Phase 7 - Operational strategy integration

Project an accepted proposal into a versioned `StrategyDefinition`, preserve
ADR-036/037 governance, require human approval for validation and enablement,
and make operational Market Thesis consume only eligible strategy semantics.

### Phase 8 - Bounded background research

Allow explicitly granted agents to initiate bounded runs under quotas,
expiration, cancellation and review checkpoints. This phase must not add
autonomous trading authority.

## 17. Proposed Engineering Story Sequence

These are proposed future Stories, not created by this ADR.

### Story A - Evidence-Based EUR/USD Hypothesis Formulation

**Objective:** Demonstrate one genuine, non-trading AI-assisted hypothesis
formulation generated from selected EUR/USD evidence without manually
supplying a strategy template, while preserving ADR-089's comparative
workflow unchanged.

**Scope:** Add the typed
`SINGLE_MARKET_HYPOTHESIS_DISCOVERY` objective to the existing research
request boundary, build a deterministic cutoff-bounded `ResearchContext`, add
the versioned hypothesis proposal projection, and invoke the existing AI
Engine through `AiEnginePort` using the additive hypothesis-discovery contract.
The AI must be able to formulate a candidate relationship not supplied as a
predefined strategy. Deterministic discovery may provide complementary
candidate observations, but the workflow must preserve whether the final
proposal was AI-formulated, deterministic-only or hybrid. No strategy
execution, DSL, backtest or quantitative validation.

**Dependencies:** ADR-089/092 research lifecycle, ADR-057/060/062 evidence
contracts, existing `ResearchExecution` and `ResearchReport`.

**Acceptance criteria:**

- User selects EUR/USD and a research horizon.
- The request uses an explicit single-market hypothesis-discovery objective;
  existing comparative research still requires and behaves as multi-market.
- The service obtains a bounded historical population or returns an explicit
  insufficient-data result.
- A deterministic, versioned ResearchContext is built from cutoff-valid
  evidence, with an input fingerprint and no account/broker state.
- When the AI capability is available, the AI receives no predefined strategy,
  hypothesis text, entry rule or expected direction and may return zero or more
  structured proposals.
- At least one integration fixture demonstrates an AI-formulated relationship
  that was not supplied by the request or selected from `BuiltinStrategies`.
- The proposal includes measurable outcome, evaluation horizon, applicability,
  falsification, missing evidence, contradictions, limitations, provenance and
  generator version.
- Java rejects unknown evidence references, post-cutoff claims, malformed
  fields, unsupported certainty and provider/model claims that are not in the
  authorized provenance.
- A deterministic-only discovery result is labeled as such and cannot be
  reported as AI-assisted discovery.
- LLM summarization without a structured proposal is not counted as discovery.
- The run returns valid `NO_HYPOTHESIS`, `INCONCLUSIVE`,
  `INSUFFICIENT_EVIDENCE` and `AI_UNAVAILABLE` outcomes where applicable.
- The report exposes a bounded `REVIEW_REQUIRED` projection. Persisted
  accept/reject decision is either implemented as a minimal append-only
  decision with the existing authorization boundary or explicitly deferred to
  Story B; it must not be implied by report retrieval.
- A proposal cannot create `StrategyDefinition`, `MarketThesis`, Opportunity,
  TradePlan, Risk evaluation or broker state.

**Existing components to reuse:** `ResearchExecution`,
`ResearchExecutionRepository`, `ResearchApplicationService`,
`ResearchReport`, `MarketThesisComposer` only as deterministic evidence input,
`AiEnginePort`, `HttpAiAnalysisCapability`, `AiProvenance`, existing Gateway
and MCP research status/report projections, ADR-060/062 dataset contracts and
the Python Pydantic strict-model boundary.

**Minimal domain changes:** A versioned `ResearchHypothesis` proposal and the
typed single-market objective/discovery outcome. Do not add `StrategyProposal`,
`ResearchExperiment`, `ExperimentManifest`, `ExperimentResult` or
`ValidationEvidence` in this Story.

**AI contract changes:** Add the versioned `HYPOTHESIS_DISCOVERY` mode and a
bounded structured proposal collection to the existing request/result DTOs.
Keep `AiEnginePort`, asynchronous execution, shadow mode, provider provenance,
context limits and Java-side validation. No new Python service or direct MCP
path.

**Persistence and projection:** Persist the immutable proposal and its
fingerprint/provenance in the existing Market Intelligence research boundary;
reference evidence and AI execution IDs rather than copying raw evidence or
prompts. Expose only an owner/grant-authorized, bounded read-only projection.

**Tests and demonstration:**

- contract tests for the new AI request/result version and strict fields;
- proof that the request contains no predefined strategy semantics;
- proof that an AI-generated relationship differs from deterministic template
  selection and is retained as a hypothesis, not a fact;
- evidence-reference, market-identity, cutoff and provenance rejection;
- unsupported-claim and qualitative-confidence tests;
- malformed AI output, unavailable provider and shadow-mode tests;
- insufficient evidence and valid zero-hypothesis tests;
- deterministic context and fingerprint replay tests;
- comparative two-market regression tests proving ADR-089 behavior is unchanged;
- authorization, ownership, idempotency, bounded output and no-secret tests;
- a non-trading EUR/USD fixture or authorized read-only demonstration.

**Security:** Research-only permission, bounded market scope, no account or
broker access unless separately required, no raw filesystem/model credential
exposure.

**Relative complexity:** Medium.

### Story B - Durable Hypothesis Review Lifecycle

**Objective:** Persist hypothesis versions, explicit human accept/reject
decisions, supersession, expiration, idempotency and audit projections after
the first proposal-generation slice has proved its contract.

**Dependencies:** Story A.

**Acceptance criteria:** Every state transition is authorized, append-only and
version-bound; rejected/inconclusive/superseded hypotheses cannot be tested
without a new approved transition; reports survive the initiating session.

**Complexity:** Medium.

### Story C - Constrained Strategy Proposal Contract

**Objective:** Represent research-only strategy semantics using approved typed
primitives and deterministic applicability/entry/exit/invalidation rules.

**Dependencies:** Story B; existing StrategyDefinition/evaluator contracts;
ADR-034/035/036/037/038.

**Acceptance criteria:** Unknown primitives, arbitrary code and incomplete
semantics are rejected; proposals are not operational definitions; a proposal
can be compared with a future evaluator contract without dynamic execution.

**Complexity:** Medium/High.

### Story D - Experiment Manifest And Immutable Result Boundary

**Objective:** Persist reproducible experiment manifests and bounded result
metadata over validated datasets.

**Dependencies:** Story C; ADR-057/060/062; existing Python research package.

**Acceptance criteria:** Manifest fingerprints are deterministic; reruns with
changed inputs create new experiments; future leakage, missing evidence and
invalid partitions fail closed; exploratory results cannot set `VALIDATED`.

**Complexity:** High.

### Story E - Evidence-Supported Historical Evaluation

**Objective:** Add the smallest deterministic replay/evaluation slice justified
by available evidence, with explicit event ordering and cost semantics.

**Dependencies:** Story D; ADR-0113 follow-up evidence Stories; historical
spread/fee/funding/slippage availability.

**Acceptance criteria:** Unsupported realism is reported as unavailable rather
than simulated silently; objective outcomes and economic results remain separate;
all cost components are versioned and non-duplicated.

**Complexity:** High.

### Story F - Quantitative Validation Evidence Review

**Objective:** Define and implement human review of robustness evidence bound to
an exact strategy version.

**Dependencies:** Story E; scientific approval of thresholds and testing policy.

**Acceptance criteria:** `ValidationEvidence` acceptance is explicit, auditable,
immutable and never automatically enables a strategy; expiration/revocation and
supersession are represented.

**Complexity:** High.

### Story G - Operational Strategy Projection And Thesis Integration

**Objective:** Permit accepted research to produce a new governed strategy
version and make operational Market Intelligence consume it without weakening
current Thesis evidence gates.

**Dependencies:** Story F; human approval; ADR-036/037/038 compatibility review.

**Acceptance criteria:** Exact strategy ID/version, validation evidence,
applicability and evaluator semantics are preserved; `VALIDATED` and `ENABLED`
remain separate; current missing/contradicting evidence still fails closed.

**Complexity:** High.

### Story H - Bounded Background Research Agent

**Objective:** Allow explicit research agents to initiate bounded research runs
  with quotas and review checkpoints.

**Dependencies:** Stories A-F; ADR-090/092; operational grant and quota design.

**Acceptance criteria:** Agent-triggered runs are scoped, idempotent,
cancellable, auditable, non-trading and unable to accept validation or enable a
strategy.

**Complexity:** Medium/High.

## 18. Conflicts And Compatibility With Accepted ADRs

No direct conflict is intended. The proposal extends existing boundaries:

- ADR-034/036/037/038 remain authoritative for operational strategy identity,
  persistence, validation and enablement.
- ADR-076 remains authoritative for canonical deterministic Market Thesis. This
  ADR deliberately uses `ResearchHypothesis`/`ResearchReport` instead of a
  duplicate canonical exploratory Thesis.
- ADR-089/092 remain authoritative for Java-owned research orchestration, MCP
  boundaries, agent permissions and non-trading research.
- ADR-057/060/062/093/095 remain authoritative for historical evidence,
  projections, datasets, chronology and ML-specific admissibility.
- ADR-100 remains authoritative for capability-aware current Forex evidence and
  does not authorize research to infer Level II liquidity from quotes.
- Story 0209's one-follow-up adaptive capability limit remains unchanged; this
  ADR does not turn it into a generic multi-agent or strategy-discovery engine.

Potential architectural tension requires explicit human resolution rather than
silent interpretation:

1. ADR-038 leaves evidence-producer ownership open between Market Intelligence,
   Quant Research and a future service. This proposal recommends Market
   Intelligence as the lifecycle authority and a bounded Python consumer, but
   does not make that ownership accepted.
2. ADR-089's existing comparative research workflow requires multiple selected
   markets. ADR-101 proposes an additive typed objective with exactly one
   canonical market for hypothesis discovery. This is compatible without
   changing ADR-089 because the cardinality policy is objective-specific and
   server-enforced. Human approval is still required for the exact public field
   name and authorization mapping, not for weakening the comparative contract.
3. ADR-034/038 require an exact strategy version for validation, while the first
   vertical workflow intentionally stops at hypothesis formulation. No bridge
   to `StrategyDefinition` is implied before the later proposal and evaluation
   Stories.

### Compatibility classification

| Concern | Classification | Refinement |
|---|---|---|
| Java-owned research orchestration | Compatible without changes | Reuse `ResearchExecution` and the existing Market Intelligence boundary |
| Existing comparative multi-market research | Compatible without changes | Preserve ADR-089 cardinality and report semantics |
| Single-market hypothesis discovery | Compatible with an additive extension | Add a typed objective and server-enforced one-market policy |
| AI shadow-mode authority | Compatible without changes | Add a versioned result mode; keep Python advisory and Java authoritative |
| Existing structured AI output | Compatible with an additive extension | Add typed hypothesis proposals; retain findings and adaptive fields |
| Research persistence/provenance | Compatible with an additive extension | Persist proposal identity, evidence references and AI execution provenance |
| Dataset versioning and chronology | Compatible without changes | Reuse ADR-057/060/062 contracts and fail closed on missing evidence |
| Strategy evaluator and governance | Requires a separate future Story/ADR | Do not create a DSL, evaluator or `StrategyDefinition` in the first slice |
| Validation evidence ownership | Requires clarification in this ADR's approval | Proposed lifecycle authority is Market Intelligence; scientific acceptance remains human |
| MCP permissions | Compatible with an additive extension | Reuse research grants; add no trading, broker or Risk permission |

No genuine conflict with an accepted ADR has been identified. The only
remaining approval-sensitive items are the additive public request shape, the
single-market permission mapping and the future scientific owner/criteria for
accepted validation evidence.

## 19. Open Questions Requiring Human Approval

1. Should Market Intelligence remain the long-term owner of validation evidence,
   or should a future Quant Research component own scientific execution while
   Market Intelligence retains lifecycle authority?
2. What minimum sample sizes, confidence procedures, robustness tests and
   multiple-hypothesis controls are required before `ValidationEvidence` may be
   accepted?
3. Which deterministic discovery operators are admissible in the first EUR/USD
   vertical workflow?
4. Which strategy primitives are safe and sufficiently expressive for the first
   declarative proposal contract?
5. Is paper evaluation mandatory before `ENABLED`, or is accepted quantitative
   evidence plus explicit human activation sufficient?
6. What expiry/revocation policy applies to accepted validation evidence?
7. Which historical Forex data, spread, fee, funding and execution evidence is
   sufficient for the first non-exploratory economic evaluation?
8. Which research outputs may be retained, and for how long, under the existing
   artifact and dataset retention policies?
9. What agent grants and quotas are appropriate for background research?

## 20. Decision Proposal And Rationale

If approved, Trading OS should:

1. Extend the existing Java-owned Market Intelligence research lifecycle rather
   than introduce a new Research Service now.
2. Add a distinct, immutable and provenance-bearing `ResearchHypothesis`
   contract before adding strategy generation or backtesting.
3. Represent research-only strategy semantics with constrained declarative
   primitives, not arbitrary AI-generated production code.
4. Reuse `ResearchExecution`, `ResearchReport`, dataset manifests, existing
   artifact fingerprints and `AiEnginePort`.
5. Treat experiment manifests/results and accepted validation evidence as
   separate immutable boundaries.
6. Preserve `MarketThesis` as the operational deterministic authority and use
   research reports/hypotheses for exploratory conclusions.
7. Keep human approval mandatory for quantitative evidence acceptance and
   operational enablement.
8. Deliver the first useful capability as a non-trading EUR/USD AI-assisted
   hypothesis formulation workflow using an explicit single-market objective,
   while preserving ADR-089 comparative research unchanged.

This is the smallest coherent architectural change because it creates the
missing research meaning and provenance boundary before introducing the much
larger and scientifically consequential backtesting and promotion machinery.

## 21. Explicit Non-Decision

This ADR does not:

- implement any code, schema, service, endpoint or UI;
- create an Engineering Story;
- define final quantitative thresholds or trading policy;
- validate or enable an existing strategy;
- create or mutate a Market Thesis, Opportunity, TradePlan, Risk evaluation,
  position or broker order;
- authorize LIVE scans, synchronization or reconciliation;
- introduce a vector database, event broker or new microservice;
- make AI an authority for market facts, quantitative evidence or trading.

## 22. References

- `docs/architecture/reports/trading-research-engine-audit-2026-11-13.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/ProductionIntelligencePipeline.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/application/BuiltinStrategies.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/domain/StrategyDefinition.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/research/ResearchExecution.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/research/ResearchReport.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AiEnginePort.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AiAnalysisCommand.java`
- `ai-engine/app/domain/models.py`
- `ai-engine/app/infrastructure/execution_store.py`
- `ai-engine/app/main.py`
- `research/ml/README.md`
- `trading-os-web/src/app/features/research/research-overview.html`
