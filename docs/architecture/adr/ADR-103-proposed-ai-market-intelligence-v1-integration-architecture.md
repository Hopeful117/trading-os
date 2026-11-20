# ADR-103 - Proposed AI Market Intelligence V1 Integration Architecture

## Status

ACCEPTED - human architecture approval recorded 2026-11-20

Acceptance authorizes the architectural foundation and subsequent Story
preparation. It does not authorize production implementation, schema
migrations, AI provider activation, LIVE operations, broker operations or
execution. OD-01, OD-02 and OD-03 remain explicit decisions to resolve during
the applicable Story approval gates.

## Date

2026-11-20

## Decision Type

Epic 0248 Market Intelligence, AI integration boundary, deterministic analysis
authority and human-control architecture

## Scope

Epic 0248 - AI Market Intelligence V1, with particular application to ES-02
ContextPack and domain-contract preparation.

## Related ADRs

- ADR-020 - Market Intelligence Architecture
- ADR-021 - Governance of Market Intelligence Analysis Executions
- ADR-028 - Deterministic Risk Engine Architecture
- ADR-034 - Strategy, StrategyMatch and Trading Opportunity Boundaries
- ADR-076 - Market Thesis Authority and Evidence Composition
- ADR-089 - Native Trading Research Agent and MCP Responsibility Boundary
- ADR-101 - Autonomous Trading Research Lifecycle and Governance
- ADR-102 - Passive Market Discovery and Active Opportunity Investigation Architecture

## Related Evidence

- `docs/architecture/epics/0248-ai-market-intelligence-v1/d1-ai-infrastructure-inventory.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d2-architecture-and-runtime-flows.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d3-epic-0248-fit-gap-matrix.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d4-reuse-adapt-build-matrix.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d5-integration-recommendation.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d6-audit-evidence-index.md`

## 1. Context and Problem Statement

Epic 0248 introduces manually triggered AI interpretation of one existing
Trading Opportunity at a time. The interpretation is advisory and must remain
inside the existing Trading OS decision pipeline:

```text
Market Data
    -> deterministic Market Intelligence
    -> Trading Opportunity
    -> AI interpretation
    -> human review
    -> TradePlan
    -> Risk
    -> human authorization
    -> Trading Core execution
```

Two Epic-specific interpretation modes are required:

- `OPPORTUNITY_ASSESSMENT`: interpretation of an opportunity that satisfies
  deterministic evaluation prerequisites;
- `DIAGNOSTIC`: explanation of an authorized opportunity that is blocked,
  invalidated, or otherwise non-exploitable, when the available context is
  sufficient for diagnosis.

The current repository already contains a FastAPI AI Engine, a Java
Market Intelligence adapter, provider-neutral structured validation, a durable
Market Intelligence `AnalysisExecution` lifecycle, versioned opportunities and
deterministic opportunity eligibility. It does not yet prove the two Epic modes,
the Epic-specific ContextPack, or a complete durable business-analysis history.

The architectural decision must therefore establish the smallest safe
integration boundary without introducing a second AI runtime, orchestration
service, eligibility engine, or direct AI-to-broker path.

This ADR is architectural guidance for subsequent Stories. It does not
authorize production implementation, schema migration, provider activation,
LIVE operations, broker operations, or execution.

## 2. Verified Existing Architecture

### 2.1 AI Engine

The AI Engine exposes authenticated internal submit, progress, result and cancel
endpoints and constructs an in-memory `ExecutionStore` at application startup
(`D6 E-001`, `ai-engine/app/main.py:14-18,27-43,77-144`). It provides:

- mock, OpenAI and ChatGPT OAuth provider selection;
- provider fallback with provenance;
- strict Pydantic request and result models;
- evidence-reference validation;
- bounded timeout checks;
- cooperative cancellation;
- temporary technical execution state.

The AI Engine execution store is process-local. Its active provider interruption
and restart recovery are not guaranteed (`D6 E-005` to `E-008`). Provider
fallback is not equivalent to a durable execution retry policy.

### 2.2 Market Intelligence

Market Intelligence owns deterministic analysis orchestration and provides the
existing HTTP adapter and AI capability. The adapter submits, polls, maps
structured results and requests cancellation (`D6 E-008`). The current
capability exposes `PASSIVE` and `ACTIVE` analysis modes, not the Epic-specific
modes (`D6 E-009`, `E-010`).

Market Intelligence also provides:

- durable `AnalysisExecution` creation, idempotency, dispatch, cancellation and
  stale-accepted recovery (`D6 E-011`);
- explicit execution state transitions (`D6 E-012`);
- durable execution persistence (`D6 E-013`);
- durable research execution/report history (`D6 E-016`);
- versioned Trading Opportunity persistence (`D6 E-017`);
- deterministic passive candidate eligibility over opportunity, observations,
  Market Thesis, evidence, freshness and market identity (`D6 E-014`).

### 2.3 Existing Authority Model

ADR-076 keeps Market Thesis as deterministic, evidence-bearing market authority.
ADR-089 places specialized reasoning behind Java-owned Market Intelligence and
the existing AI Engine. ADR-101 keeps AI advisory, separates research objects
from operational authority, and preserves implementation approval gates.
ADR-102 keeps deterministic eligibility and human review authoritative for
passive-to-active investigation.

These decisions remain authoritative. This ADR adds an Epic-specific integration
boundary and does not redefine Market Thesis, Trading Opportunity, Risk,
TradePlan, Trading Core or Broker Service semantics.

## 3. Decision Drivers

1. Preserve deterministic Java authority over opportunity eligibility and mode
   selection.
2. Reuse existing Market Intelligence and AI Engine boundaries.
3. Keep factual context immutable, versioned, bounded and traceable.
4. Preserve evidence freshness, cutoff and provenance semantics.
5. Keep AI interpretation advisory and fail-closed on invalid output.
6. Keep Risk, human authorization and execution outside the AI boundary.
7. Provide durable business-analysis history without claiming cross-service
   exactly-once execution.
8. Keep Epic-specific policy decisions visible for human approval.
9. Avoid duplicate eligibility, orchestration, provider and persistence models.

## 4. Alternatives Considered

### Alternative A - Reuse Market Intelligence and AI Engine Boundaries

Market Intelligence remains the Java-owned application boundary. It loads and
authorizes the opportunity, evaluates deterministic prerequisites, selects the
Epic mode, assembles the ContextPack, invokes the existing AI Engine adapter,
validates the result and stores durable business-analysis metadata.

The AI Engine remains a provider-neutral interpretation runtime. Its temporary
execution state is not treated as the business history.

**Selected.** This aligns with ADR-076, ADR-089, ADR-101 and ADR-102, reuses
existing durable execution and security boundaries, and avoids duplicating
provider and orchestration infrastructure.

### Alternative B - Introduce a Dedicated AI Orchestration Service

A new service would own opportunity loading, eligibility, mode selection,
ContextPack construction, AI execution and result persistence.

**Rejected for V1.** It would duplicate Market Intelligence authority, create a
new security and persistence boundary, fragment opportunity provenance, and add
operational complexity without evidence that the existing boundary is
insufficient.

### Alternative C - Integrate AI Interpretation Directly into Trading Core

Trading Core would load opportunities, invoke AI and retain interpretation state
alongside account and execution workflows.

**Rejected.** It would move market interpretation into the account/execution
authority, blur the Market Intelligence and Trading Core boundary, and create a
path for advisory AI output to be confused with financial authorization.

## 5. Architectural Decisions

### 5.1 Integration Boundary

Epic 0248 SHALL reuse the existing Market Intelligence to AI Engine boundary.

Market Intelligence owns:

- opportunity loading and resource authorization;
- deterministic eligibility evaluation;
- Java-only analysis mode selection;
- ContextPack assembly and identity;
- business-level orchestration;
- result acceptance and domain validation;
- durable business-analysis history.

The AI Engine owns:

- provider and model invocation;
- prompt execution behind provider adapters;
- structured interpretation;
- technical schema and evidence-reference validation;
- temporary provider execution state and provenance.

Trading Core, Risk and Broker Service retain their existing account, financial,
authorization, broker and execution authorities.

No second AI runtime, orchestration service, eligibility engine or direct
AI-to-broker path is introduced by this ADR.

### 5.2 Deterministic Mode Selection

Java SHALL exclusively select the analysis mode before AI invocation:

```text
deterministically evaluable opportunity
    -> OPPORTUNITY_ASSESSMENT

authorized opportunity with sufficient diagnostic context,
but blocked/non-exploitable
    -> DIAGNOSTIC

missing resource, failed authorization, invalid identity,
or insufficient context for a legitimate request
    -> request rejection
```

The selection boundary SHALL reuse `PassiveCandidateEvaluator`, opportunity
lifecycle authorities, Market Thesis references and other existing deterministic
components. It SHALL not duplicate their rules.

The exact mapping from each opportunity status and blocking reason to
assessment, diagnostic or rejection is a separate domain-policy decision and
must be approved before implementation. This ADR recommends a conservative,
fail-closed policy but does not accept the mapping as final.

The AI Engine and LLM SHALL NOT select, reinterpret or override the mode.

### 5.3 ContextPack

Epic 0248 SHALL use an immutable, versioned and traceable ContextPack as the
sole factual context supplied to the AI interpretation operation.

The ContextPack SHALL identify or reference, where available:

- opportunity identity and immutable version;
- selected analysis mode;
- deterministic eligibility outcome and blocking reasons;
- Market Thesis identity/version and selected evidence;
- market identity and relevant market facts;
- data-quality, freshness and missingness information;
- risk-related facts from authoritative sources, without making AI the Risk
  authority;
- source references and provenance;
- evaluation cutoff, evaluation time and validity/freshness bounds;
- schema version and stable context fingerprint/identity.

The ContextPack SHALL preserve temporal consistency. Evidence observed after the
evaluation cutoff cannot silently enter the pack. Missing, stale, unavailable or
unsupported evidence remains explicitly represented and is not converted into
positive support.

Market Intelligence owns ContextPack construction and authorization. The AI
Engine receives only the authorized pack and must not fetch Trading OS services,
repositories, broker credentials or unrestricted account data.

This ADR defines invariants and ownership, not the final Java class structure.

### 5.4 Structured Results

The integration SHALL use a common result envelope with mode-specific semantics.

`OPPORTUNITY_ASSESSMENT` may contain qualitative assessment, supporting and
contradicting factors, uncertainties, invalidation conditions and evidence
references.

`DIAGNOSTIC` may contain deterministic blocking reasons, explanations, missing
prerequisites, uncertainties, conditions for reassessment and evidence
references.

A diagnostic SHALL NOT contain:

- a trade approval;
- a favorable trading signal presented as authority;
- an instruction to bypass a deterministic blocker;
- a Risk approval or execution command;
- an uncalibrated probability-of-success score.

The AI Engine SHALL validate structured shape and evidence references. Java
SHALL validate mode-specific domain invariants, opportunity/version identity,
ContextPack identity, temporal consistency and authority boundaries before
acceptance.

An invalid or unsupported result is rejected or recorded as a non-authoritative
failure/partial outcome. It cannot mutate deterministic opportunity status.

### 5.5 Execution Lifecycle and Persistence

Epic 0248 SHALL reuse the durable Market Intelligence `AnalysisExecution`
lifecycle for business orchestration and execution tracking. It SHALL not create
a second execution orchestrator.

The AI Engine process-local `ExecutionStore` remains temporary technical state.
The durable business record SHALL distinguish at least:

- technical provider execution reference;
- opportunity identity and version;
- selected mode;
- deterministic eligibility snapshot or reference;
- immutable ContextPack identity/version/fingerprint;
- provider/model and contract provenance;
- result quality and accepted result reference;
- failure, timeout, cancellation and reconciliation metadata;
- request fingerprint and idempotency key;
- requested, updated and completed timestamps.

The architecture SHALL not claim cross-service exactly-once provider execution
or atomicity between Market Intelligence persistence and the AI Engine.

The durable record must remain available after AI Engine restart. If the
temporary AI record is lost, the durable Market Intelligence execution must not
be interpreted as proof that the provider completed. Recovery must reconcile the
provider execution reference when possible, otherwise record an explicit
terminal/unknown outcome according to the approved policy.

### 5.6 Human Authority and Safety

AI outputs are advisory artifacts. They SHALL NOT:

- transition an opportunity into a tradable state;
- modify deterministic opportunity status;
- override Market Thesis state or evidence rules;
- modify Risk approval or risk limits;
- create or authorize a TradePlan;
- trigger a broker order, position change or execution intent;
- promote an interpretation into an authoritative trading fact.

All execution remains subject to deterministic checks and explicit human
authorization. AI failure, unavailability, stale context or malformed output
must not weaken risk controls or fail open into execution.

## 6. ContextPack and Result Invariants

The following invariants are architectural requirements for ES-02 and later
implementation Stories:

1. The ContextPack is immutable after acceptance.
2. A ContextPack has a stable identity, schema version and input fingerprint.
3. The pack references one exact opportunity version and one selected mode.
4. Java, not the AI Engine, determines the mode.
5. Evidence references resolve only to authorized source material.
6. Evidence cutoff, observation time and validity semantics remain explicit.
7. Missing, stale, unavailable and contradictory evidence remain distinguishable.
8. A diagnostic explains a deterministic blocker; it cannot remove the blocker.
9. Assessment and diagnostic results cannot change opportunity or Risk state.
10. No result contains an uncalibrated probability presented as trading authority.
11. A result is not accepted if its mode, opportunity version or ContextPack
    identity does not match the durable request.
12. Re-evaluation creates a new analysis record and does not rewrite prior
    ContextPack or result history.

## 7. Execution, Idempotency and Recovery Principles

The following principles apply before production implementation is considered:

| Scenario | Required architectural behavior |
|---|---|
| Duplicate request | Same actor/scope, idempotency key and fingerprint return the existing durable request; a conflicting fingerprint is rejected |
| AI Engine restart | Temporary provider state may be lost; durable Market Intelligence state survives and is reconciled, not blindly replayed |
| Market Intelligence restart | Durable execution is recoverable through the existing lifecycle and dispatch/recovery mechanisms |
| Provider succeeds but response delivery fails | Outcome is unknown until reconciled; do not assume failure or issue an unsafe duplicate invocation |
| Result received but persistence fails | Preserve the provider execution reference and record a recoverable persistence failure; no authority transition occurs |
| Opportunity changes during analysis | Result is accepted only against the requested immutable opportunity version; stale results cannot update current opportunity state |
| Execution reference is lost | Mark recovery as unresolved/failed according to approved policy; do not infer completion |
| Cancellation | Cancellation is bounded and best-effort; it does not authorize unsafe retry or erase durable history |

The exact reconciliation mechanism, retry limits and terminal outcome vocabulary
remain part of OD-03.

## 8. Security and Trust Invariants

- The request must be authenticated and authorized for the opportunity/resource.
- Client-supplied identifiers cannot establish ownership or authority by
  themselves.
- AI Engine receives only the ContextPack authorized by Market Intelligence.
- User JWTs, broker credentials, unrestricted account data and provider secrets
  do not enter AI prompts or public result contracts.
- Provider payloads and credentials remain inside AI Engine infrastructure.
- External market text is untrusted input and must not change authority or
  execution semantics.
- AI output is untrusted input until schema, evidence, identity, temporal and
  domain validation succeeds.
- Gateway, Trading Core, Risk and Broker Service boundaries remain unchanged.
- No live provider or broker operation is implied by this ADR.

## 9. Explicitly Unresolved Decisions

### OD-01 - Diagnostic Eligibility

Existing evidence provides opportunity lifecycle states and deterministic
blocking diagnostics, including inactive/out-of-validity opportunity,
unavailable strategy match, unavailable/current observations, missing or
non-candidate Market Thesis, stale/invalid evidence, blocking contradiction and
market identity mismatch (`D6 E-014`, `E-015`).

The conservative recommendation is:

- reject missing resources, failed authorization, invalid ownership, malformed
  identity and unavailable opportunity versions;
- allow `DIAGNOSTIC` only when the exact authorized opportunity version exists
  and the deterministic blocker plus sufficient evidence can be represented;
- never use a diagnostic to reinterpret or bypass the blocker;
- require explicit human approval of the final status-to-mode mapping before
  implementation.

This mapping is not accepted by this ADR.

### OD-02 - Durable Analysis History

Existing `AnalysisExecution` persistence can represent technical execution ID,
status, expiry, market, policy, provenance and serialized result payload. Existing
opportunity persistence stores immutable opportunity versions and analysis
execution references. Existing research persistence stores reports, attempts,
stages and failures (`D6 E-011` to `E-017`).

The recommended option is to reuse these boundaries and add only the minimum
Epic association needed to preserve mode, opportunity version, ContextPack
identity and result provenance. The ADR does not determine whether this is an
extension of `AnalysisExecution`, a separate business-analysis projection, or
an existing report projection. That decision requires ES-02 contract analysis
and human approval.

The existing serialized payload must not be assumed to satisfy all historical
traceability requirements without verifying its versioning and query needs.

### OD-03 - Cross-Service Recovery

The minimum safe policy is durable Market Intelligence state before dispatch,
idempotent request identity, explicit provider execution references, bounded
reconciliation, and fail-closed terminal handling. A lost AI Engine execution
must not be automatically retried when the provider outcome is unknown.

The recommended option is to reconcile first, then permit a new explicitly
identified attempt only when the approved policy establishes that the prior
invocation did not execute. Otherwise the durable record remains unknown or
failed with an auditable reason. Cross-service exactly-once execution is not
claimed.

The exact reconciliation and retry policy requires human approval before
implementation.

## 10. Consequences and Trade-offs

### Positive Consequences

- Existing Market Intelligence and AI Engine infrastructure is reused.
- Java remains the single authority for eligibility, mode, persistence and
  domain acceptance.
- Context and results become traceable without making AI a source of truth.
- Durable business history survives AI Engine process restarts.
- Risk, human authorization and broker execution boundaries remain intact.
- A diagnostic can explain a blocker without weakening fail-closed behavior.
- No new service or generic orchestration platform is required for V1.

### Costs and Risks

- ES-02 requires a carefully versioned ContextPack and result contract.
- Cross-service recovery remains more complex than a single transactional
  process.
- AI Engine temporary state can be lost during restart.
- Durable history may require a business-analysis projection beyond the current
  technical execution aggregate.
- Diagnostic semantics require explicit product/domain policy.
- Provider behavior, cancellation and retry semantics remain environment and
  adapter dependent.
- Human review is required before this proposed architecture becomes current
  architecture.

## 11. Relationship to Existing ADRs

### ADR-076

This ADR preserves Market Thesis as deterministic, evidence-bearing market
authority. AI may interpret the authorized ContextPack, but cannot create or
mutate canonical Market Thesis state. Market Thesis and Risk remain separate.

### ADR-089

This ADR applies ADR-089's Java-controlled workflow and existing Python AI
reasoning boundary to a single-opportunity interpretation flow. It does not
create a direct MCP-to-AI Engine path or move orchestration into a client.

### ADR-101

This ADR preserves ADR-101's advisory AI, durable Java-owned orchestration,
separate research/domain meanings, bounded execution and human approval gates.
It does not authorize strategy research, strategy promotion, autonomous trading
or provider enablement.

### ADR-102

This ADR reuses ADR-102's deterministic eligibility and human-review boundary.
An Epic diagnostic is not passive-to-active escalation and does not turn a
passive or blocked opportunity into an account-authorized trade.

No accepted ADR is superseded or modified by this proposal.

## 12. Implementation Implications for Subsequent Stories

ES-02 should define the ContextPack and domain contracts only after OD-01,
OD-02 and OD-03 are reviewed. It should specify:

- immutable identity and versioning;
- authorized source and provenance references;
- temporal/freshness invariants;
- Java-owned mode selection inputs;
- distinct assessment and diagnostic result semantics;
- result acceptance and stale-version rules;
- idempotency and durable history requirements.

Subsequent Stories may implement the approved contracts, but this ADR does not
authorize them. No Story may add AI authority to opportunity, Market Thesis,
Risk, TradePlan, Trading Core or Broker Service.

## 13. Status and Required Human Decision

This ADR is **ACCEPTED**. The following decisions remain explicitly open and
require resolution before the relevant implementation work:

1. acceptance of the reuse architecture;
2. the diagnostic eligibility policy in OD-01;
3. the durable analysis-history shape in OD-02;
4. the cross-service recovery policy in OD-03;
5. authorization to begin ES-02 contract work.

Acceptance of this ADR must not be interpreted as authorization for production
implementation, migrations, AI provider activation, LIVE operations, broker
operations or execution unless those permissions are separately approved by the
applicable Engineering Stories.
