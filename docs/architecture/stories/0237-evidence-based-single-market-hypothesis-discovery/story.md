# Story 0237 - Evidence-Based Single-Market Hypothesis Discovery

## Metadata

**ID:** `0237`

**Title:** Evidence-Based Single-Market Hypothesis Discovery

**Status:** Proposed - implementation not authorized

**Priority:** P1 - Trading Intelligence

**Related ADRs:** `ADR-057`, `ADR-060`, `ADR-062`, `ADR-076`, `ADR-089`,
`ADR-090`, `ADR-092`, `ADR-100`, `ADR-101`

**Predecessors:** `0208`, `0209`, `0221`, `0222`

## Business Motivation

Trading OS currently supports a Java-owned comparative research workflow over
explicitly selected markets. It can assemble deterministic Market Intelligence
evidence and optionally ask the AI Engine for advisory interpretation, but it
does not yet persist a distinct, structured hypothesis that is formulated from
evidence without a predefined trading strategy.

This Story adds the smallest independently useful research capability: a user
or authorized research agent selects one canonical market, initially EUR/USD,
and receives a versioned, evidence-grounded, falsifiable hypothesis proposal or
an explicit non-positive outcome.

This is a non-trading research workflow. It does not prove an edge, validate a
strategy, create a Market Thesis, create an Opportunity, perform Risk or
modify broker/account state.

## Product Objective

Enable a bounded request using the explicit objective:

```text
SINGLE_MARKET_HYPOTHESIS_DISCOVERY
```

The workflow is:

```text
User / MCP / Angular
        |
        v
Research request with typed objective
        |
        v
Existing ResearchExecution lifecycle
        |
        v
Historical Market Data evidence and deterministic context
        |
        v
AI Engine HYPOTHESIS_DISCOVERY through AiEnginePort
        |
        v
Java schema, provenance and evidence validation
        |
        v
Durable versioned ResearchHypothesis proposal projection
        |
        v
Authorized read-only status/report retrieval
```

The existing comparative workflow remains:

```text
COMPARATIVE_MARKET_RESEARCH
```

It continues to require its existing multi-market scope and retains its current
semantics. This Story adds an objective-specific cardinality policy; it does
not reinterpret or weaken ADR-089.

## Architectural Context

### Existing components to reuse

- `ResearchExecution` and `ResearchExecutionRepository` for durable lifecycle,
  actor ownership, idempotency, attempts and status.
- `ResearchApplicationService` for Java-owned orchestration, deterministic
  context assembly, AI delegation, budgets and report persistence.
- `ResearchReport` and `ResearchExecutionResponse` for bounded report/status
  projections, extended additively with hypothesis proposals and outcomes.
- `Market Data` historical evidence and market identity contracts.
- Existing deterministic analysis, observations, Market Thesis evidence and
  provenance references where semantically appropriate.
- `AiEnginePort`, `AiAnalysisCommand`, `AiStructuredResult`,
  `HttpAiAnalysisCapability` and existing AI provenance.
- Existing Python strict Pydantic contract and shadow-mode execution store.
- Existing Gateway REST routes and MCP tools
  `start_market_research`, `get_research_status` and `get_research_report`.
- ADR-060/062 versioned feature/dataset boundaries when a historical dataset is
  used as the ResearchContext source.

### Current implementation facts

- `ResearchExecutionRequest` currently requires exactly two market IDs.
- `ResearchApplicationService.validate` currently rejects any scope other than
  exactly two distinct market IDs.
- The current request fingerprint includes actor, research scope, market IDs,
  objective and depth, but has no objective-type field because none exists.
- The current MCP `start_market_research` tool validates exactly two IDs before
  calling the existing REST endpoint.
- The current authorization call uses the existing `research:run` permission
  and scope/market authorization boundary.
- The current AI contract supports Market Thesis interpretation, findings,
  evidence references, missing information, contradictions and one adaptive
  follow-up, but not a dedicated typed hypothesis-discovery proposal.

## Scope

### In scope

- Add a typed research objective with a backward-compatible default of
  `COMPARATIVE_MARKET_RESEARCH`.
- Permit exactly one canonical market only for
  `SINGLE_MARKET_HYPOTHESIS_DISCOVERY`.
- Preserve exactly-two validation for comparative requests.
- Add the minimum single-market request fields for timeframe, horizon and
  historical cutoff or bounded historical range.
- Assemble a typed, bounded, versioned and reproducible `ResearchContext` from
  authorized historical evidence.
- Add the capability-specific AI mode `HYPOTHESIS_DISCOVERY` through the
  existing Java `AiEnginePort` boundary.
- Validate structured AI proposals in Java before canonical persistence.
- Persist an immutable/versioned `ResearchHypothesis` proposal projection with
  evidence references, provenance, limitations and validation status.
- Persist explicit non-positive research outcomes on `ResearchExecution` or
  `ResearchReport` without manufacturing a hypothesis entity.
- Extend existing authorized REST/MCP read projections with the objective,
  hypothesis proposals and bounded provenance.
- Keep the workflow account-independent and non-trading.

### Out of scope

- `StrategyProposal` and any strategy DSL or primitive library.
- `ResearchExperiment`, `ExperimentManifest` and `ExperimentResult`.
- `ValidationEvidence`, quantitative acceptance or profitability claims.
- StrategyDefinition creation, validation, promotion, enablement or suspension.
- Operational Market Thesis mutation, Opportunity creation or scanner changes.
- Generalized backtesting, replay-based execution simulation or economic P&L.
- New Research Service, vector database, event broker or generic agent framework.
- Autonomous background research agents.
- New trading, broker, account, Risk, TradePlan or synchronization permission.
- Current LIVE quote substitution for historical evidence.
- Direct MCP-to-AI Engine access or unrestricted Python/file-system access.
- Persisted accept/reject hypothesis decisions, unless the existing boundary can
  support a minimal append-only decision without introducing a generic workflow;
  the preferred follow-up is the next Story.
- A large Angular research dashboard or conversational UI.

## Research Request Contract

Extend the existing request contract additively. The exact DTO names may follow
repository conventions, but the semantic contract is mandatory:

| Field | Requirement |
|---|---|
| `objectiveType` | Enum; defaults to `COMPARATIVE_MARKET_RESEARCH` for existing clients |
| `scopeId` | Existing explicit research scope; server validated |
| `marketIds` | Exactly two distinct IDs for comparative; exactly one for single-market discovery |
| `provider` / `productType` | Optional client constraints only; server resolves and verifies canonical values |
| `timeframe` | Required for single-market discovery; versioned accepted value |
| `horizon` | Required measurable evaluation horizon; no claim of performance |
| `historicalCutoff` | UTC cutoff, or explicit bounded `from`/`to` historical range |
| `objective` / `researchQuestion` | Optional bounded question; cannot contain executable strategy code or required answer text |
| `depth` | Existing bounded `STANDARD` or `DEEP` budget |
| actor identity | Derived from authenticated user/agent, never request-controlled |
| idempotency key | Existing actor-scoped header and conflict behavior |

The server derives provider, product type, canonical instrument and market
identity from `marketId`. Client-supplied identity is a constraint to verify,
not an authority source.

Objective-specific validation is mandatory:

- `COMPARATIVE_MARKET_RESEARCH`: preserve the current exactly-two distinct
  market rule and existing report semantics.
- `SINGLE_MARKET_HYPOTHESIS_DISCOVERY`: require exactly one canonical market,
  historical context fields and an account-independent research scope.
- unknown objective types: reject before persistence or dispatch.

The objective type, normalized market IDs, scope, objective/question, depth,
timeframe, horizon, cutoff/range, provider/product constraints and contract
version must participate in the request fingerprint. Existing comparative
requests preserve their current effective fingerprint semantics with the new
default objective type included deterministically.

Repeated idempotency keys with the same fingerprint return the existing
`ResearchExecution`. Reuse with a different objective type, cardinality,
market, cutoff, question, depth or contract version returns
`IDEMPOTENCY_CONFLICT`.

## Authorization

Use the existing `research:run` permission and research-scope authorization for
the first implementation. No new permission is required because this Story is
the same bounded, non-trading research capability family already authorized by
ADR-089/092.

The authorization boundary must nevertheless enforce:

- authenticated human or explicitly provisioned agent identity;
- owner/scope ownership and active grant/credential where applicable;
- the requested canonical market is within the authorized scope;
- one-market cardinality for the new objective;
- no account context is requested or required;
- research depth, duration, context, AI request and output budgets;
- authorization before enqueue and ownership/authorization on status/report
  retrieval.

The permission decision may continue to receive the authorized market set as
today. Market Intelligence validates the objective-specific cardinality and
historical constraints after authorization and before persisting/enqueueing.
Do not broaden `research:run` to broker, account, Risk or execution authority.

## Historical Evidence And ResearchContext

Market Intelligence must assemble a `ResearchContext` from server-authorized,
historical evidence only. The context must contain:

- canonical market ID, instrument, provider and product type;
- timeframe, evaluation horizon and source cutoff/range;
- dataset/evidence policy and context schema version;
- deterministic observations and feature references used by the discovery
  request;
- evidence IDs/fingerprints, occurrence/known-at timestamps and source
  provenance;
- available, stale, missing, contradictory and insufficient evidence statuses;
- deterministic context fingerprint;
- research execution ID and objective type;
- bounded diagnostics and limitations.

The context must be cutoff-consistent under ADR-057. Evidence known after the
cutoff, future outcomes, current provider responses and current LIVE quotes
must not enter the historical context. The implementation must not silently
replace missing historical evidence with current quotes, zero values, neutral
values or synthetic observations.

If the required historical population or evidence quality is unavailable, the
execution must persist `INSUFFICIENT_EVIDENCE` and must not call the AI Engine
as if the context were complete.

The context is an authorized projection, not a new market-data authority. It
references existing data and artifacts rather than duplicating large raw OHLC,
order-book, prompt or provider payloads.

## AI Hypothesis Discovery Contract

Add a versioned `HYPOTHESIS_DISCOVERY` mode to the existing AI contract. Reuse
the asynchronous `AiEnginePort`, AI execution reference, cancellation,
timeouts, provenance and shadow-mode restrictions.

The authorized AI request must contain only the bounded ResearchContext and
the explicit research question/objective. It must not contain a predefined
strategy name, entry rule, expected direction or target hypothesis supplied by
the caller.

The structured response may contain zero or more proposals. Each proposal must
include:

- `observedBehavior`;
- `proposedRelationship` explicitly marked as hypothetical;
- `expectedOutcome` with a measurable quantity and evaluation horizon;
- `applicabilityConditions`;
- `falsificationConditions`;
- `supportingEvidenceReferences`;
- `contradictions`;
- `missingEvidence`;
- `limitations` and qualitative uncertainty;
- bounded proposal identity/version fields if generated by the provider.

Java owns and adds authoritative:

- canonical market identity;
- research execution ID;
- cutoff, timeframe and horizon normalization;
- input fingerprint;
- generator type/version;
- AI execution reference, provider, model, prompt and contract provenance;
- proposal validation status.

AI output cannot assert `EVIDENCE_GROUNDED`, `SUPPORTED`, `PROFITABLE`,
`VALIDATED` or `ENABLED`. AI-generated UUIDs and evidence references are
untrusted until validated against the authorized context.

The following outcomes are distinct:

| Outcome | Owner | Meaning |
|---|---|---|
| `HYPOTHESIS_PROPOSED` | ResearchExecution/Report | At least one Java-validated proposal persisted |
| `NO_HYPOTHESIS` | ResearchExecution/Report | AI returned no falsifiable proposal |
| `INCONCLUSIVE` | ResearchExecution/Report or proposal projection | Output/context cannot support a grounded proposal |
| `INSUFFICIENT_EVIDENCE` | ResearchExecution/Report | Required historical context unavailable or invalid |
| `AI_UNAVAILABLE` | ResearchExecution/Report | AI was disabled, unavailable or timed out and no AI proposal exists |
| `SCHEMA_VALID` | ResearchHypothesis | Proposal shape is valid; not scientific support |
| `EVIDENCE_GROUNDED` | ResearchHypothesis | Java verified references and temporal eligibility; not truth or profitability |
| `REJECTED` | ResearchHypothesis | Proposal failed structural, authorization or evidence validation |

A deterministic discovery result may be retained as `DETERMINISTIC_ONLY` in
generator provenance. It must never be labeled `AI_ASSISTED` or
`AI_DISCOVERED`. A response that only summarizes existing findings without the
required hypothesis fields is not a discovery result and must be rejected or
reported as `INCONCLUSIVE`.

AI-disabled behavior must be explicit. A deterministic fallback may provide
deterministic context or candidate observations, but it must not manufacture an
AI hypothesis or label its output as AI discovery.

## Java Validation Boundary

Market Intelligence validates every proposal before persistence:

- strict DTO schema and bounded collection/string sizes;
- canonical market, provider and product identity;
- objective type, research scope and one-market cardinality;
- source cutoff, timeframe and horizon consistency;
- evidence reference existence and authorization;
- evidence `knownAt`/observed-at eligibility and source fingerprint;
- required measurable outcome and evaluation horizon;
- applicability and falsification conditions;
- contradictions, missingness and limitations;
- generator type/version and AI model/prompt/contract provenance;
- unsupported factual or statistical claims;
- context and output budgets.

Unknown, fabricated, unauthorized, future or post-cutoff references are
rejected. Java must not silently repair unsupported claims. Structural
validation and scientific validation remain separate: `EVIDENCE_GROUNDED` only
means that the proposal is traceable to the supplied context.

## Minimum ResearchHypothesis Contract

Persist the smallest immutable proposal object required by this Story:

- `hypothesisId`;
- immutable `version`;
- parent `researchExecutionId`;
- canonical `marketId`, instrument, provider and product type;
- research objective type;
- timeframe, evaluation horizon and evidence cutoff;
- observed behavior;
- proposed relationship;
- expected measurable outcome;
- applicability conditions;
- falsification conditions;
- supporting evidence references;
- missing evidence;
- contradictory evidence;
- limitations and qualitative uncertainty;
- generator type and version;
- model, prompt and AI execution provenance when applicable;
- deterministic input/context fingerprint;
- proposal validation status;
- immutable creation timestamp.

Material changes create a new version and retain the prior proposal and
fingerprint. No Story-level entity is introduced for `StrategyProposal`,
`ResearchExperiment`, `ExperimentManifest`, `ExperimentResult`,
`ValidationEvidence` or generic `ResearchArtifact`.

## Lifecycle And Persistence

Reuse `ResearchExecution` states and persistence. Extend the report/projection
without replacing the existing comparative report:

- `HYPOTHESIS_PROPOSED`: terminal/partial research outcome with one or more
  persisted validated proposals;
- `NO_HYPOTHESIS`: terminal research outcome with no proposal entity;
- `INCONCLUSIVE`: terminal/partial outcome where context or output cannot
  support a grounded proposal;
- `INSUFFICIENT_EVIDENCE`: deterministic pre-AI outcome;
- `AI_UNAVAILABLE`: explicit AI disabled/unavailable/timeout outcome.

The existing execution states (`REQUESTED`, `RUNNING`,
`PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`) remain
the lifecycle authority. The outcome is a report/result field, not a replacement
execution state.

The first Story does not persist `ACCEPTED_FOR_TESTING` or human accept/reject
decisions. The report is exposed as `REVIEW_REQUIRED`; the following Story may
add a small append-only decision projection and lifecycle. No experiment may be
started from a proposal in this Story.

Persistence must preserve actor, scope, grant/credential provenance where
applicable, request fingerprint, objective type, context fingerprint, AI
execution reference, proposal version and evidence references. Retries remain
bounded and idempotent; terminal executions cannot acquire new work.

## REST, MCP And Frontend Boundaries

Reuse the existing public REST and MCP research application boundary.

### REST

Extend `POST /api/v1/intelligence/research` additively with `objectiveType` and
single-market historical-context fields. Existing clients omitting the field
retain `COMPARATIVE_MARKET_RESEARCH` and exactly-two validation.

Reuse:

- `GET /api/v1/intelligence/research/{researchId}` for status;
- `GET /api/v1/intelligence/research/{researchId}/report` for proposals,
  provenance and limitations.

The response must remain actor/scope authorized and bounded. A separate
hypothesis endpoint is not required for this Story unless the existing report
projection cannot safely expose proposal details; if introduced, it must be a
read-only child projection under the research ID and reuse the same ownership
check.

### MCP

Extend the existing `start_market_research` tool with:

- `objective_type`, defaulting to `COMPARATIVE_MARKET_RESEARCH`;
- conditional one-market/two-market validation;
- bounded timeframe, horizon and cutoff fields for single-market discovery;
- the existing scope and idempotency key.

Do not add a direct AI tool or a separate persistence/orchestration tool.
`get_research_status` and `get_research_report` remain the read-only retrieval
surface. MCP continues to call only public Gateway contracts and exposes no
secrets, raw filesystem paths, internal endpoints or provider credentials.

### Angular

No dashboard is required. If a minimal projection is added, use the existing
research reactive service/Observable and async-pipe conventions to display
loading, unavailable, insufficient-evidence, proposal and limitation states.
Do not add manual orchestration or direct Python calls.

## Acceptance Criteria

### Request and compatibility

- A valid single-market request accepts exactly one canonical market and the
  explicit `SINGLE_MARKET_HYPOTHESIS_DISCOVERY` objective.
- A comparative request with the default or explicit
  `COMPARATIVE_MARKET_RESEARCH` objective still requires exactly two distinct
  markets.
- Unknown objective types, invalid cardinality and duplicate markets are
  rejected before persistence/enqueue.
- Provider/product constraints are server-resolved and mismatches are rejected.
- Timeframe, horizon and cutoff/range are required and bounded for single-market
  discovery.
- Objective type and all material request fields participate in the fingerprint.
- Same actor/scope/idempotency/fingerprint replays the existing execution;
  changed objective type or material request data returns a deterministic
  idempotency conflict.
- Existing comparative clients and reports remain compatible.

### Evidence and context

- The ResearchContext is typed, bounded, versioned, cutoff-consistent and
  reproducibly fingerprinted.
- Historical evidence uses existing Market Data known-at/provenance semantics.
- Current LIVE quotes are never substituted for historical evidence.
- Missing, stale, contradictory, insufficient and unavailable evidence remain
  explicit.
- Insufficient historical evidence produces `INSUFFICIENT_EVIDENCE` without an
  AI call pretending the context is complete.

### Genuine AI discovery

- The AI Engine receives `HYPOTHESIS_DISCOVERY` context through `AiEnginePort`.
- The caller does not provide a predefined strategy, rule, direction or target
  hypothesis.
- A fixture demonstrates a structured AI-formulated relationship not selected
  from `BuiltinStrategies` or another predefined strategy template.
- LLM summarization without proposal fields is not accepted as discovery.
- Deterministic-only output is labeled `DETERMINISTIC_ONLY`.
- Proposals contain observed behavior, relationship, measurable outcome,
  horizon, applicability, falsification, evidence, contradictions, missingness
  and limitations.
- Valid `NO_HYPOTHESIS`, `INCONCLUSIVE`, `AI_UNAVAILABLE` and timeout results
  are persisted without fabricating a proposal.

### Java validation and persistence

- Malformed, unknown, unauthorized, future, post-cutoff or unsupported
  evidence references are rejected or produce explicit `INCONCLUSIVE` status.
- Java assigns authoritative identity, version, provenance, fingerprint and
  proposal validation status.
- Material revisions create new immutable versions.
- No hypothesis status implies empirical support, profitability, validation or
  operational enablement.
- ResearchExecution, proposal and report persistence survive the initiating
  request and preserve actor/scope/idempotency provenance.

### Authority and safety

- No `MarketThesis`, Observation, Opportunity, StrategyDefinition, TradePlan,
  Risk evaluation, position, order, synchronization or reconciliation is
  mutated.
- AI cannot call Trading OS services directly, access brokers/credentials or
  promote a strategy.
- Existing `research:run` authorization, scope ownership and retrieval checks
  remain enforced.
- MCP and Angular do not own orchestration.
- Comparative research regression tests remain green.

## Test Plan

### Domain and contract tests

- Valid single-market request and conditional cardinality validation.
- Comparative two-market regression with omitted/default objective type.
- Unknown objective and duplicate-market rejection.
- Objective type, cutoff, horizon, scope and market participation in the
  fingerprint.
- Stable actor-scoped idempotency and changed-request conflict.
- Canonical provider/product identity verification.
- Immutable proposal versions and provenance retention.
- Actor/scope ownership enforcement.
- Invalid, unknown, unauthorized, future and post-cutoff evidence rejection.
- Missingness, contradiction, limitation and unsupported-claim preservation.
- Malformed AI DTO rejection.

### AI behavior tests

- Valid structured `HYPOTHESIS_DISCOVERY` response.
- AI-formulated novel relationship not present in a predefined strategy
  template.
- Response that only summarizes deterministic findings is rejected or marked
  `INCONCLUSIVE`.
- Deterministic discovery is labeled `DETERMINISTIC_ONLY`.
- `NO_HYPOTHESIS`, `INCONCLUSIVE`, `INSUFFICIENT_EVIDENCE` and
  `AI_UNAVAILABLE` outcomes.
- AI timeout, cancellation, provider failure and bounded retry behavior.
- Model, prompt, contract and execution provenance persistence.
- AI attempts to invent evidence, claim significance, claim profitability or
  assert validation are rejected or downgraded without silent repair.
- Shadow-mode and mock-provider behavior remain explicit.

### Integration and boundary tests

- ResearchExecution persistence and dispatch race/idempotency behavior.
- ResearchHypothesis persistence and authorized report retrieval.
- REST Gateway authentication and ownership.
- MCP request mapping, conditional cardinality, bounded output and sanitized
  errors.
- Existing comparative research workflow and report compatibility.
- Existing Market Thesis and strategy governance regression tests.
- No-secret, no-account-context and no-trading-authority assertions.

No test requires a real broker, LIVE scan, account synchronization or external
LLM quota. A real provider demonstration is optional and must remain
read-only/non-trading.

## Operational Validation Plan

Using authorized historical EUR/USD evidence only:

1. Submit one bounded single-market research request with no predefined
   strategy semantics.
2. Confirm the server resolves canonical EUR/USD identity and builds a bounded
   ResearchContext with cutoff/provenance.
3. Confirm the AI request uses `HYPOTHESIS_DISCOVERY` and receives only the
   authorized context.
4. Retrieve status and report through REST or existing MCP tools.
5. Confirm a validated proposal, or the exact explicit non-positive outcome, is
   persisted with evidence references and limitations.
6. Confirm no Market Thesis, Opportunity, Strategy, TradePlan, Risk, position,
   order, account synchronization or reconciliation changed.

If historical evidence or AI capability is unavailable, record the exact
blocker and demonstrate `INSUFFICIENT_EVIDENCE` or `AI_UNAVAILABLE`. Never
fabricate a successful hypothesis or treat a fixture as runtime proof.

## Dependencies

- Accepted ADR-101.
- Existing Stories 0208/0209 research lifecycle and AI boundary.
- Historical evidence/dataset availability under ADR-057/060/062.
- Existing `research:run` scope/grant authorization.
- A versioned additive AI DTO contract for `HYPOTHESIS_DISCOVERY`.
- Human approval of this Story and its implementation plan.

## Risks And Open Questions

- The current EUR/USD historical population may be insufficient for a valid
  context; the correct result is explicit insufficiency.
- AI availability is disabled by default and must not be enabled as a side
  effect of this Story.
- The exact AI prompt template and field limits require contract-level review,
  but may not add a prescribed hypothesis.
- The persisted proposal may initially live in the existing report projection;
  the exact relational shape must not duplicate raw evidence and is an
  implementation-plan decision.
- Persisted human accept/reject decisions are intentionally deferred to the
  next Story unless a minimal append-only extension is demonstrated.
- Scientific validation thresholds, strategy DSL, experiment ownership and
  background agents remain governed by later decisions/Stories.

No open question blocks this Story's non-trading hypothesis formulation if the
existing research scope and `research:run` authorization are confirmed during
implementation planning. Any request to add new permissions, account context,
strategy promotion or LIVE operations is outside this Story and requires a new
decision.

## Definition Of Done

- [ ] Repository Analysis and implementation plan are approved.
- [ ] Objective-specific request validation preserves comparative behavior.
- [ ] Fingerprint and idempotency include objective type and material context.
- [ ] Deterministic ResearchContext contract and provenance are implemented.
- [ ] Versioned `HYPOTHESIS_DISCOVERY` AI contract is implemented and tested.
- [ ] Java proposal validation rejects fabricated/unauthorized evidence and
      unsupported claims.
- [ ] Durable ResearchHypothesis proposal/report projection is implemented.
- [ ] Status/report retrieval remains authorized and bounded through Gateway and
      existing MCP boundaries.
- [ ] Non-positive AI/evidence outcomes are explicit and tested.
- [ ] Comparative research, Market Thesis and strategy governance regressions
      pass.
- [ ] Non-trading EUR/USD operational or fixture demonstration is documented
      truthfully.
- [ ] No broker, account, Risk, TradePlan, strategy activation or Market Thesis
      mutation occurred.
- [ ] Relevant Java, Python, Gateway/MCP and frontend tests pass where changed.
- [ ] `git diff --check` passes.
- [ ] Implementation report and code review are complete.
- [ ] Human commit and delivery approval are complete.

## Explicit Non-Authorization

This Story does not authorize:

- production code changes before Story approval;
- schema migration before an approved implementation plan;
- real LLM/provider enablement or external quota spending;
- LIVE scans or broker operations;
- account synchronization or reconciliation;
- TradePlan, Risk, execution or position changes;
- strategy validation, promotion or enablement;
- Market Thesis mutation;
- commit, push, merge or pull request creation.
