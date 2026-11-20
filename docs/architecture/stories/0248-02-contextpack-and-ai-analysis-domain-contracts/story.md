# Story 0248-02 - Define ContextPack and AI Analysis Domain Contracts

## Metadata

**ID:** `0248-02`
**Title:** Define ContextPack and AI Analysis Domain Contracts
**Status:** Draft - pending Repository Analysis approval.
**Priority:** P1 - Trading Intelligence
**Epic:** `0248 - AI Market Intelligence V1`
**Predecessor:** Epic work package `ES-01 - AI and Market Intelligence Architecture Audit`
**Related ADRs:** `ADR-076`, `ADR-089`, `ADR-101`, `ADR-102`, `ADR-103`

## Goal

Define the provider-neutral, immutable and traceable domain contracts required to interpret one
authorized Trading Opportunity version through the existing Market Intelligence to AI Engine
boundary.

This Story establishes the contract boundary only. It does not implement the complete orchestration,
durable business-history projection or provider activation.

## Product Value

The platform will have an explicit contract for what evidence the AI may interpret, which deterministic
mode was selected, how the result relates to an exact opportunity version, and how stale or ambiguous
results are rejected. This preserves human authority and prevents AI output from becoming an implicit
trading decision.

## Context

Market Intelligence already provides reusable deterministic context sections through
`IntelligenceContext`, context fingerprints, Market Thesis evidence, opportunity versioning and the
durable technical `AnalysisExecution` lifecycle. The existing AI boundary already receives an
authorized context and supports structured request metadata.

The repository does not yet expose the Epic-specific concepts `OPPORTUNITY_ASSESSMENT` and
`DIAGNOSTIC`, nor does it define a stable business result contract connecting those modes to an exact
opportunity version and immutable ContextPack.

ADR-103 accepts the reuse architecture and requires ES-02 to define these contracts before later
Stories implement orchestration and persistence.

## Problem

Without an Epic-specific ContextPack and result contract, the AI request boundary does not explicitly
guarantee temporal consistency, opportunity-version consistency, result provenance, diagnostic
eligibility or safe recovery semantics. This would allow later orchestration work to introduce
provider-shaped contracts, stale result acceptance or accidental AI authority.

## Scope

### In scope

- Define an immutable `ContextPack` contract owned by Market Intelligence.
- Define stable ContextPack identity, schema version and input fingerprint semantics.
- Bind a ContextPack to the exact opportunity identity and version when applicable.
- Reference authorized Market Thesis, observations, evidence, market context, objective, cutoff and
  provenance without copying authority into the AI Engine.
- Define the Java-owned analysis modes:
  - `OPPORTUNITY_ASSESSMENT` for deterministically eligible opportunity versions;
  - `DIAGNOSTIC` for exact opportunity versions with an explicit deterministic blocking reason.
- Define structured assessment and diagnostic result contracts.
- Define result acceptance invariants for mode, opportunity version, ContextPack identity/version and
  fingerprint.
- Define idempotency and technical recovery semantics using the existing `AnalysisExecution` boundary.
- Add the non-terminal `RECOVERY_REQUIRED` technical state for ambiguous or unreconciled AI execution.
- Define the durable-result contract needed by a later business-analysis history projection without
  implementing that projection in this Story.
- Add domain and contract tests for immutability, versioning, fingerprints, stale-result rejection,
  diagnostic eligibility and recovery transitions.

### Diagnostic eligibility policy

`DIAGNOSTIC` is allowed only when all of the following hold:

- the exact opportunity identity and version exist;
- the opportunity and its references are structurally coherent;
- deterministic evaluation produces at least one policy-approved blocking reason;
- the reason is explainable from authorized data and does not require the AI to infer eligibility.

Expired, inactive or invalidated opportunities may be diagnosed when their deterministic reason is
known and traceable. Missing opportunities, superseded versions, structurally corrupt references and
cases with no deterministic blocking reason are rejected rather than sent to the AI.

Diagnostics do not mutate opportunity status, Market Thesis, risk, TradePlan or execution state.

### Recovery policy

- A timeout or ambiguous AI response must not be retried blindly.
- Reconciliation uses the same `analysisExecutionId`, ContextPack fingerprint and idempotency key.
- Unresolved technical state is represented by `RECOVERY_REQUIRED`.
- Recovery does not create a second business result or change domain authority.
- An explicit later retry may reuse the immutable ContextPack and must remain linked to the original
  execution.

## Out of Scope

- Complete Java orchestration and mode-selection application service.
- Durable persistence or migration of the business-analysis history projection.
- AI Engine provider activation or model configuration.
- New service, generic orchestration platform or second AI boundary.
- Prompt redesign or model-quality evaluation.
- Frontend or user-interface changes.
- Changes to Market Thesis, deterministic opportunity status, Risk, TradePlan, Trading Core, Broker
  Service or execution authority.
- Automatic passive-to-active escalation.
- Broker calls, PAPER execution or LIVE execution.
- Historical backfill or analysis-history migration.

## Acceptance Criteria

- [ ] A ContextPack has immutable identity, explicit schema version and stable input fingerprint.
- [ ] A ContextPack records the exact opportunity identity and version when the analysis is
      opportunity-scoped.
- [ ] A ContextPack records authorized provenance, temporal cutoff and freshness information for its
      source evidence.
- [ ] A ContextPack exposes only authorized provider-neutral data to the AI boundary.
- [ ] `OPPORTUNITY_ASSESSMENT` is representable only for a deterministically eligible exact
      opportunity version.
- [ ] `DIAGNOSTIC` is representable only for an exact structurally coherent opportunity version with
      at least one approved deterministic blocking reason.
- [ ] Missing, superseded or structurally corrupt opportunity versions cannot produce an accepted
      diagnostic result.
- [ ] Assessment and diagnostic results are structurally distinct and include mode, execution identity,
      ContextPack identity/version, opportunity version and provenance references.
- [ ] A result with a mismatched mode, opportunity version, ContextPack version or fingerprint is
      rejected as stale or invalid.
- [ ] Accepted results cannot mutate the ContextPack, opportunity, Market Thesis, risk, TradePlan or
      execution authority.
- [ ] `RECOVERY_REQUIRED` is non-terminal, preserves idempotency and supports explicit reconciliation
      without blind duplicate submission.
- [ ] Technical retries and recovery remain linked to one durable `AnalysisExecution` identity and do
      not create duplicate business results for the same idempotency key.
- [ ] The result contract contains enough identity and provenance to support a later durable
      business-analysis projection.
- [ ] Tests prove deterministic fingerprints, input-order independence, stale-result rejection,
      diagnostic eligibility and recovery transition behavior.
- [ ] No provider-specific payload, prompt detail or AI-generated authority leaks into the domain
      contract.

## Constraints

- Market Intelligence owns ContextPack construction, authorization and result acceptance.
- Market Data remains authoritative for provider-neutral market identity and observed market facts.
- Market Thesis remains deterministic, evidence-bearing and immutable from the AI perspective.
- `PassiveCandidateEvaluator` remains the deterministic source for current opportunity eligibility and
  blocking diagnostics; ES-02 must not duplicate its rules.
- `AnalysisExecution` remains the technical lifecycle and idempotency boundary.
- The AI Engine receives only the ContextPack authorized by Market Intelligence.
- AI remains advisory and cannot create or mutate canonical domain decisions.
- Risk Domain remains authoritative for risk decisions.
- Trading Core remains authoritative for authorization and execution state.
- No broker or external provider operation is introduced.

## Relevant Artifacts

- `docs/architecture/epics/0248-ai-market-intelligence-v1/epic.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d1-ai-infrastructure-inventory.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d2-architecture-and-runtime-flows.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d3-epic-0248-fit-gap-matrix.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d4-reuse-adapt-build-matrix.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d5-integration-recommendation.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d6-audit-evidence-index.md`
- `docs/architecture/adr/ADR-076.md`
- `docs/architecture/adr/ADR-089.md`
- `docs/architecture/adr/ADR-101.md`
- `docs/architecture/adr/ADR-102.md`
- `docs/architecture/adr/ADR-103-proposed-ai-market-intelligence-v1-integration-architecture.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/IntelligenceContext.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/ContextSection.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/context/IntelligenceContextAssembler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveCandidateEvaluator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecution.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecutionStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/AiEngineAnalysisRequest.java`
- `ai-engine/app/domain/models.py`
- `ai-engine/app/infrastructure/execution_store.py`

## Validation Expectations

- Repository Analysis must verify the exact existing context, opportunity eligibility and execution
  lifecycle before implementation planning.
- Java domain and application contract tests for all ContextPack and result invariants.
- Tests for deterministic fingerprinting and input-order independence.
- Tests for eligible assessment, eligible diagnostic, missing version, superseded version, structural
  inconsistency and non-diagnosable cases.
- Tests for stale result rejection across mode, opportunity version, ContextPack version and
  fingerprint mismatches.
- Tests for `RECOVERY_REQUIRED` transition, idempotency and reconciliation behavior.
- Python contract compatibility tests without enabling a provider or changing model authority.
- Targeted `market-intelligence` Maven verification.
- Targeted `ai-engine` test validation through its project virtual environment when Python contracts
  are changed.
- `git diff --check`.

## Open Questions For Repository Analysis

- Which existing context sections are mandatory for each mode, and which remain optional with explicit
  quality diagnostics?
- Should the durable business-analysis result be a new aggregate or a projection over accepted
  `AnalysisExecution` results?
- Which existing failure codes map to policy-approved diagnostic reasons, and which must remain
  non-diagnosable data-integrity failures?
- What exact transition and recovery endpoints are supported by the current execution dispatcher?
- Which fields of the existing Python request/result models can represent the ContextPack contract
  without provider-specific leakage?
