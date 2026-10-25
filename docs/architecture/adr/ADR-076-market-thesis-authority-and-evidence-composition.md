# ADR-076 - Market Thesis Authority and Evidence Composition

- **Status:** Accepted
- **Date:** 2026-10-25
- **Decision Type:** Market Intelligence Domain Architecture
- **Scope:** Market Intelligence
- **Related ADRs:**
  - ADR-006 - Market Data Service Responsibilities
  - ADR-020 - Market Intelligence Architecture
  - ADR-021 - Governance of Market Intelligence Analysis Executions
  - ADR-025 - Observation Model
  - ADR-027 - Trade Planning Model
  - ADR-028 - Risk Engine
  - ADR-034 - Strategy, StrategyMatch and Trading Opportunity Boundaries
  - ADR-038 - Empirical Strategy Validation Boundary

## 1. Context

The current repository has deterministic market-analysis primitives and a
durable analytical pipeline:

```text
Market Data
    -> AnalysisExecution / IntelligenceContext
    -> capability executions and artifacts
    -> Observation
    -> StrategyEvaluation
    -> StrategyMatch
    -> TradingOpportunity
    -> TradePlan
    -> Risk
    -> human authorization
    -> execution
```

`Observation` is durable, versioned analytical memory. `StrategyMatch` is an
immutable fact that one versioned strategy matched. `TradingOpportunity` is a
trader-facing candidate derived from a successful match. None of these concepts
owns the composition of a conditional market scenario across heterogeneous
evidence.

The Trading Intelligence and Market Data capability audit identified Market
Thesis Composition as the shallowest critical analytical layer. The platform
can calculate several market facts, but it cannot yet represent one auditable
interpretation that preserves supporting evidence, contradicting evidence,
missingness, freshness, confirmation, invalidation, and a truthful analytical
state.

This decision must not change existing analytical semantics, enable a strategy,
replace `FeatureProjectionV1`, add AI, or add cTrader market data.

## 2. Decision

Trading OS establishes **Market Thesis** as a new canonical Market Intelligence
domain authority for deterministic composition of market evidence into a
conditional, time-bounded market scenario.

Market Thesis is decision support. It is not a prediction, probability, Risk
verdict, TradePlan, execution command, or human decision.

The first implementation must be deterministic. Given identical authoritative
inputs, composer policy/version, and evidence versions, it must produce the same
thesis content and state.

The authority chain becomes:

```text
Authoritative market facts
    -> deterministic analyses
    -> durable observations / typed evidence
    -> Market Thesis
    -> StrategyEvaluation and/or human investigation
    -> StrategyMatch / TradingOpportunity where applicable
    -> TradePlan
    -> Risk
    -> explicit human authorization
    -> execution
```

This is an authority model, not a requirement for one synchronous call chain.
Existing strategy and opportunity semantics remain valid. A Market Thesis does
not automatically create an Opportunity.

## 3. Canonical Name and Ownership

The canonical term is **Market Thesis**.

`MarketAssessment` is too broad, `TradingScenario` is too narrow because a
thesis also records evidence and missingness, and `SetupAssessment` would
incorrectly make the concept strategy-specific. `DecisionContext` is already
represented by the broader `IntelligenceContext`.

Market Intelligence owns Market Thesis because it owns deterministic analysis
orchestration, observations, opportunities, and Trade Plans. Market Data owns
source market facts. Risk owns financial authorization. Trading Core owns
account-scoped workflows and execution lifecycle. Broker Service owns provider
facts and broker outcomes.

## 4. Domain Semantics

### 4.1 Fact

A Fact is a directly observed or narrowly, deterministically calculated value
with a defined owner and provenance. Examples include a normalized price,
confirmed swing, ATR value, market state, spread, or scheduled event timestamp.

The repository already has the required fact authorities. Market Data models
market facts and freshness; Market Structure and Trend Context expose typed
deterministic results. No generic `Fact` aggregate is introduced by this ADR.

### 4.2 Evidence

Evidence is a typed, provenance-bearing contribution that supports, contradicts,
or leaves unresolved a conditional thesis. Evidence may reference a Fact,
Observation, capability artifact, deterministic finding, or contextual source.

Existing `ObservationEvidence`, `TrendContextFinding`, and typed analysis
findings remain authoritative source evidence. They do not acquire a universal
polarity or thesis-state meaning. Market Thesis introduces only a
thesis-specific contribution wrapper when composition requires it.

The wrapper must preserve, at minimum:

- contribution identity and role: supporting, contradicting, or missing;
- source reference and source capability;
- market/instrument and timeframe when applicable;
- provider and provider symbol when available;
- observed-at and analysis cutoff timestamps;
- source status and freshness;
- analysis/rule/version provenance;
- evidence fingerprint or immutable reference;
- optional structured value/context and reason.

The wrapper references existing evidence and captures the selected composition
snapshot. It does not become a second source of market truth.

### 4.3 Supporting and contradicting evidence

Supporting and contradicting evidence are separate collections in a thesis.
Contradictions are never collapsed into a single confidence score.

Contradiction severity is explicit and policy-defined:

- `INFORMATIONAL`: relevant disagreement that does not affect eligibility;
- `WEAKENING`: reduces coherence and normally keeps the thesis in `WATCH`;
- `BLOCKING`: prevents a `CANDIDATE` state for the affected scenario.

Severity is not a probability or universal numeric score. A contradiction
between timeframes, an immediate opposing structural level, abnormal execution
conditions, or event proximity may be represented only when the contributing
analysis exposes the relevant semantics.

### 4.4 Missing evidence

Missing evidence is first-class and is never converted into support. The thesis
must distinguish:

- `AVAILABLE`;
- `UNAVAILABLE`;
- `STALE`;
- `UNKNOWN`;
- `NOT_APPLICABLE`;
- `NOT_IMPLEMENTED`.

Existing `ContextSectionStatus` remains the source convention for context
acquisition (`AVAILABLE`, `STALE`, `MISSING`, `UNAVAILABLE`, `UNSUPPORTED`,
`INCOMPLETE`). The thesis composition model maps those statuses into the
explicit thesis missingness semantics without changing the existing enum.

`UNSUPPORTED`, `MISSING`, and `INCOMPLETE` must not be presented as a negative
market fact. A missing Momentum analyzer, for example, remains
`NOT_IMPLEMENTED` or `UNAVAILABLE`; Trend does not fill it implicitly.

## 5. Market Thesis Responsibility

A Market Thesis composes one bounded market context into one conditional
scenario assessment. Its logical content includes:

- market identity and instrument provenance;
- evaluated-at, source cutoff, validity window, and freshness;
- one optional direction-specific scenario;
- supporting evidence references/snapshots;
- contradicting evidence references/snapshots;
- missing and stale evidence;
- analytical state;
- structured confirmation conditions;
- structured invalidation conditions;
- relevant level references;
- composer policy/version and input fingerprints;
- analysis execution and observation lineage where available.

It may state that no coherent scenario is currently available. It must not
claim a future price outcome or statistical edge.

## 6. Responsibility Boundaries

Market Thesis does not own or authorize:

- account balance, equity, drawdown, exposure, challenge progression, or
  position sizing;
- financial Risk evaluation or approval;
- broker order state, execution attempts, reconciliation, or positions;
- TradePlan lifecycle or executable entry/Stop/Target parameters;
- provider-specific payloads, cTrader IDs, or provider normalization;
- AI-generated canonical facts or canonical V1 thesis state;
- empirical strategy validation or FeatureProjectionV1 research semantics.

Analysis and Risk remain independent:

```text
Market Thesis: Is the market scenario coherent enough to investigate?
Risk: Is this exact TradePlan financially permitted?
```

Both `Market Thesis = CANDIDATE, Risk = REJECTED` and `Market Thesis = NO_TRADE`
with an otherwise permissible plan are valid outcomes.

## 7. Scope and Strategy Relationship

Market Thesis is **market-scoped**, not account-scoped. Account, challenge, and
portfolio context may be attached as separately authorized context for a later
consumer, but cannot alter the market truth or become part of the Market Thesis
identity.

Market Thesis is **strategy-neutral**. A thesis may contain a direction-specific
conditional scenario, but it does not assert that a named strategy matched.
Multiple strategies may evaluate the same thesis independently:

```text
Market Thesis M1
    -> Strategy A: NO_MATCH
    -> Strategy B: MATCH
```

`StrategyMatch` remains the immutable fact that a specific strategy version
matched. It contributes evidence to, or consumes, the thesis according to the
future orchestration policy; it does not become the thesis.

No separate `SetupThesis` aggregate is introduced at this stage.

## 8. Analytical States

The minimum V1 state set is:

### `NO_TRADE`

The deterministic composition does not currently identify a scenario that the
system can responsibly present as a candidate. Reasons must be structured and
visible, such as insufficient required evidence, blocking contradiction, stale
required evidence, invalidated scenario, poor conditions, event risk, or no
valid scenario. `NO_TRADE` is a successful analytical outcome, not a failure and
not the same as a human decision not to trade.

### `WATCH`

A coherent conditional scenario exists, but a required confirmation is pending
or a non-blocking limitation prevents candidate promotion. The thesis states
what would confirm the scenario and what would invalidate it. `WATCH` is not a
trade recommendation.

### `CANDIDATE`

The scenario has the minimum evidence required by its composition policy,
required evidence is available and fresh, confirmation conditions are satisfied,
and no blocking contradiction remains. This means worthy of human investigation
and possible planning only. It does not mean StrategyMatch, Risk approval, or
execution authorization.

No `CONFIRMED` state is introduced. Confirmation is represented as structured
scenario evidence and predicates; a further state would duplicate or blur
Opportunity semantics.

## 9. Scenario, Confirmation, Invalidation and Levels

### Scenario

A scenario is conditional, direction-specific market reasoning, not a
prediction. It must be representable structurally, for example as a direction,
conditions, and referenced levels. Human-readable explanation is a projection
of those structured values, not the only source of meaning.

One Market Thesis represents one scenario evaluation. Opposing long and short
scenarios are separate thesis lineages or separate scenario evaluations under a
composition execution; they must not be silently collapsed into one direction.

### Confirmation

Confirmation is a structured set of deterministic conditions or references that
answers what event changes the scenario from `WATCH` to `CANDIDATE`. It may
contain pending and satisfied conditions. Arbitrary free text alone is not a
confirmation contract.

### Invalidation

Invalidation is a structured condition that makes the scenario no longer valid.
It is distinct from an executable Stop. Existing `TrendInvalidation` and
Market Structure evidence may supply invalidation references without becoming
owned by the thesis.

### Levels

V1 references authoritative Market Structure levels and preserves their source,
timeframe, role, and observed value. It does not introduce a competing level
engine. A structural invalidation level or market objective may be referenced
by a thesis; actual executable Stop and Target remain downstream decisions.

## 10. Confluence and Contradiction

Confluence is a rule/state concept: relevant independent evidence dimensions
support the same conditional scenario and no unresolved blocking contradiction
exists. It is not an average of `confidenceContribution`, the current
Opportunity score, or a universal 0-100 quality score.

The first implementation should use explicit evidence roles, required evidence
policies, and contradiction severity. Numeric scoring is not required and is
not authorized by this ADR.

## 11. Relationships to Existing Domains

### Observation

Observation remains durable reusable analytical evidence. Market Thesis references
Observation IDs/versions and selected immutable evidence fingerprints, and stores
the composed snapshot required to reconstruct the thesis at its evaluation time.
It does not mutate or reinterpret an Observation.

### Trend Context

Market Thesis derives typed contributions from `TrendContextAssessment`, findings,
contradictions, exclusions, invalidations, cutoff, rule/profile versions, and
fingerprint. It references the assessment and selectively snapshots the values
used. Trend Context remains the authority for its own semantics.

### Market Structure

Market Thesis references swings, relations, protected levels, breaks, reclaims,
and structural invalidation evidence. Market Structure remains the authority for
those facts and is not duplicated as a new thesis-level structure engine.

### StrategyMatch

StrategyMatch remains strategy-specific, immutable, version-bound provenance. It
may consume a thesis or reference one when the strategy orchestration establishes
that relation, but it remains the authority for only the statement that a named
strategy matched.

### TradingOpportunity

TradingOpportunity remains derived from a successful StrategyMatch under ADR-034.
Market Thesis does not replace the match and does not automatically create an
Opportunity. V1 may add an explicit thesis reference to Opportunity provenance
only through a later implementation Story; the current score remains untouched.

### TradePlan

TradePlan may reference the exact Market Thesis ID/version used as rationale.
It may copy scenario, confirmation, invalidation, structural Stop basis, target
basis, and evidence references into its immutable rationale snapshot. Future
thesis reevaluation must never rewrite the entry context of an existing plan.

### Stop and Target

Market Thesis may provide structural invalidation and objective-level context.
TradePlan derives the actual executable Entry, Stop, Target, sizing, and
expiration under its planning policy. Risk evaluates the resulting economics.

### Risk

Risk consumes the exact TradePlan and its authoritative financial context. Market
Thesis cannot approve, reject, soften, or substitute for Risk evaluation.

### Challenge

FTMO or other challenge rules remain account/challenge and Risk context. They do
not change whether a market structure or scenario is bullish, bearish, coherent,
or invalid.

### Scanner

An active or passive scanner may identify markets requiring thesis evaluation. It
must not equate scanner ranking with a candidate thesis or directly imply a
trade-ready Opportunity. The future flow is:

```text
scanner scope -> thesis evaluation -> strategy evaluation/opportunity
```

### Position Intelligence

Position Intelligence may later compare the immutable entry thesis with a newly
evaluated current thesis. It must use structured scenario, evidence, and
invalidation references, not parse TradePlan free text or infer thesis validity
from price alone. No Position Intelligence behavior changes in this ADR.

## 12. Temporal, Freshness and Re-evaluation Semantics

Every thesis records `evaluatedAt`, source cutoff, `validFrom`, and `validUntil`
when determinable. Each evidence contribution retains its own `observedAt` and
source validity.

Thesis freshness cannot exceed the freshness of the required evidence. Optional
stale or unavailable evidence remains visible without automatically invalidating
the thesis. Stale or unavailable required evidence prevents a fresh `CANDIDATE`;
it produces `WATCH` or `NO_TRADE` according to the composition policy.

Re-evaluation creates a new immutable thesis version or lineage. It never mutates
the prior thesis. The composer must retain the input fingerprints, policy/rule
version, and source references needed to explain why the new version differs.

## 13. Persistence, Identity and Immutability

Market Thesis is **versioned persisted business evidence**, not a transient DTO
and not merely a derived read model. Persistence is required for:

- `NO_TRADE` and missing-data learning without selection bias;
- Opportunity and TradePlan provenance;
- post-trade reconstruction;
- future Position Intelligence comparison;
- future statistical evaluation without rewriting historical meaning.

Each thesis version is immutable. A new interpretation is a new version; an old
version may be superseded or expire through a new persisted state relation, never
by in-place rewriting.

Identity is market/scenario/evaluation based and does not include account
identity. The persisted identity must include or reference:

- thesis ID and lineage/version;
- market and instrument identity;
- analysis execution ID when available;
- evaluation cutoff and validity;
- composer policy/version;
- input/evidence fingerprints;
- source evidence references.

An implementation must not derive identity from only instrument, direction,
price, or timestamp because those values do not uniquely identify analytical
lineage.

## 14. Invariants

The implementation must preserve these invariants:

1. Missing required evidence is never supporting evidence.
2. Stale required evidence cannot silently produce a fresh `CANDIDATE`.
3. A blocking contradiction cannot be hidden by averaging confidence scores.
4. `NO_TRADE` is a valid successful analytical outcome.
5. Market Thesis cannot approve financial Risk.
6. Market Thesis cannot authorize or trigger execution.
7. `CANDIDATE` is not a human decision, StrategyMatch, Risk approval, or
   execution authorization.
8. Identical authoritative inputs and versions produce identical composition.
9. Historical thesis versions and entry context are immutable.
10. Provider-specific payloads and cTrader identifiers do not enter the thesis
    contract.
11. A high Opportunity score cannot establish thesis quality by itself.
12. Analytical coherence is not statistical strategy validation.

## 15. AI, Research, cTrader and Reference Market Boundaries

The deterministic V1 thesis is the canonical production interpretation. Future
AI may consume it, explain it, challenge it, expose contradictions, or propose
questions. AI output remains a proposal and cannot silently create or mutate the
canonical deterministic thesis.

FeatureProjectionV1 and research datasets remain research concerns. They may
consume thesis versions and outcomes later, but cannot redefine production
thesis semantics.

Market Thesis consumes provider-neutral facts only. cTrader Market Data is a
later provider adapter and is not a dependency of this ADR. A future reference
market must be represented by explicit market/provider/instrument provenance;
execution-symbol evidence and reference-market evidence must not be treated as
equivalent without an explicit mapping decision.

## 16. First Challenge Minimum

V1 does not require Momentum, advanced Volume, order-flow, or a statistically
validated edge. A useful first challenge thesis can use the existing:

- Trend Context;
- Market Structure and multi-timeframe roles;
- available volatility and spread context;
- available structural levels and invalidation;
- limited liquidity/order-book evidence with truthful availability;
- available news/event temporal context;
- explicit missingness and freshness.

Momentum, ambiguous volume, richer liquidity semantics, and macro direction
remain explicit unavailable or not-implemented evidence rather than synthesized
signals.

## 17. Alternatives Considered

### Extend Observation only

Rejected. Observation is the correct durable evidence boundary but does not
express scenario polarity, confirmation, contradiction severity, or analytical
state without overloading a reusable generic concept.

### Make TradingOpportunity the thesis

Rejected. ADR-034 defines Opportunity as a candidate derived from StrategyMatch.
It is strategy-specific and currently carries account/scan provenance; it cannot
become the market-scoped composition authority without collapsing distinct
responsibilities.

### Make StrategyMatch the thesis

Rejected. StrategyMatch states that one strategy version matched. It cannot
represent strategy-neutral market context or multiple strategies disagreeing on
the same market.

### Use one confidence or quality score

Rejected. A score hides missingness and contradiction and cannot distinguish
analytical coherence from financial authorization or empirical edge.

### Make the thesis AI-generated

Rejected for V1. AI can later explain or challenge deterministic composition, but
canonical market facts and deterministic state must remain reproducible and
auditable.

## 18. Recommended Engineering Story Boundary

Create one focused Market Intelligence Story for a deterministic, persisted,
versioned Market Thesis composition capability. The Story should define the
thesis-specific contribution model, compose existing Observation/Trend
Context/Market Structure/context-status references, persist immutable versions,
and expose read-only evidence/state for human investigation. It must not change
existing analyzer semantics, StrategyDefinition governance, FeatureProjectionV1,
Opportunity score semantics, TradePlan execution parameters, Risk behavior,
execution, AI, cTrader Market Data, or reference-market mapping.

## 19. Acceptance Criteria for the Story

- A Market Thesis is constructed only from identified authoritative inputs and
  records composer/version/fingerprint provenance.
- Supporting, contradicting, stale, unavailable, unknown, not-applicable, and
  not-implemented evidence remain structurally distinguishable.
- Trend Context contradictions, Structure invalidation, timeframe disagreement,
  and available context warnings are preserved without confidence averaging.
- `NO_TRADE`, `WATCH`, and `CANDIDATE` are deterministic analytical states with
  structured reasons and no Risk/execution meaning.
- Confirmation and invalidation are represented as structured references or
  conditions, not only free text.
- Market Thesis is market-scoped, strategy-neutral, immutable, and versioned.
- Re-evaluation creates a new version and never mutates historical thesis
  context.
- Missing Momentum/Volume/Liquidity semantics are represented honestly and are
  never inferred from unrelated Trend evidence.
- No Market Thesis result creates or authorizes an Opportunity, TradePlan, Risk
  decision, or execution command by itself.
- Existing Observation, StrategyMatch, TradingOpportunity, TradePlan, Risk,
  FeatureProjectionV1, and human-control semantics remain unchanged.
- Provider-specific and account/challenge-specific data do not leak into the
  market thesis authority.
- Focused domain and persistence tests prove deterministic replay, contradiction
  visibility, missingness, freshness, immutability, and no-authorization
  boundaries.

## 20. Consequences

Positive:

- Market Intelligence gains one explicit composition authority without creating
  a generic intelligence platform.
- Contradictions, uncertainty, and `NO_TRADE` become auditable outcomes.
- Opportunity, TradePlan, Risk, execution, and human authority remain distinct.
- Position Intelligence and future research gain stable entry-context lineage.

Trade-offs:

- A thesis-specific evidence wrapper and persistence model add domain complexity.
- Existing Observations and future Thesis versions require stable provenance joins.
- The first V1 thesis remains incomplete where analyzers are missing; this is
  intentional and visible.
- The ADR does not itself improve analytical depth; it enables focused analyzer
  and composition Stories.

## 21. Status and Required Human Decision

This ADR is accepted. It authorizes creation of a focused Market Thesis Story,
but does not authorize production code, migrations, strategy activation, an AI
capability, or a provider adapter by itself.
