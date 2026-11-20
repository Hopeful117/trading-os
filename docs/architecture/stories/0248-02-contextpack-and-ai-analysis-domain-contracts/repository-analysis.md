# Repository Analysis - Story 0248-02

## Status

Complete - pending human approval.

This document is the Repository Analysis for Story `0248-02`. It does not authorize implementation,
provider activation, persistence migration or execution changes.

## Analysis Scope

The analysis verifies the current repository state against:

- `docs/architecture/stories/0248-02-contextpack-and-ai-analysis-domain-contracts/story.md`;
- accepted ADR-103 and its related ADRs;
- the existing Market Intelligence context, opportunity eligibility, AI adapter and execution
  lifecycle;
- the existing AI Engine request, result and process-local execution contracts.

## DevLog Context

The required DevLog applicability check was performed through the Story Context Agent for project
`trading-os`.

Result: `COMPLETED`, but the returned context was truncated (`selectedCount=60`, `usedTokens=5992`,
`candidateCount=1480`) and its freshness state was `UNKNOWN`. The retained evidence did not include
the complete ADR-103 or complete source contents. No additional historical decision was accepted from
that result. Current repository files and accepted repository ADRs remain authoritative.

No workspace-local DevLog lifecycle registration script or exact repository mapping was available in
`TOOLS.md`, so no registration side effect was performed.

## Current Repository State

### Existing deterministic context

`IntelligenceContextAssembler` already builds a context from explicit `ContextRequirement` values and
contributors. It:

- enforces an evaluation cutoff for `STRICT_OBSERVED` evidence;
- binds the cutoff to the request before loading sections;
- calculates `ContextQuality` from required and optional sections;
- records diagnostics for non-available sections;
- assigns a schema version and fingerprint;
- captures the request market, mode and objective.

Evidence: `market-intelligence/.../application/context/IntelligenceContextAssembler.java:36-68`.

`IntelligenceContext` is immutable at the object boundary: it copies the section map, diagnostics and
selected public sections. It supports selecting a subset of sections and recalculating quality,
diagnostics and fingerprint for the selected view.

Evidence: `market-intelligence/.../domain/IntelligenceContext.java:16-55` and `:115-145`.

`ContextSection` and `ContextProvenance` already represent section status, sensitivity, payload,
source, source version, evidence policy, evidence fingerprint, cutoff and capture timestamps.

Evidence: `market-intelligence/.../domain/ContextSection.java:3-9` and
`market-intelligence/.../domain/ContextProvenance.java:5-17`.

### Existing authorization boundary

Market Intelligence already has a classification-based disclosure policy. The policy only allows a
Market Intelligence caller, rejects classifications above the configured maximum and rejects
consumers not listed by the contribution descriptor.

Evidence: `market-intelligence/.../application/context/DefaultContextDisclosurePolicy.java:8-25`.

The current wire-level `ContextSensitivity` is a smaller compatibility model (`PUBLIC`,
`USER_PRIVATE`), while the newer authorization model uses `ContextClassification` with
`PUBLIC`, `INTERNAL`, `USER_CONFIDENTIAL`, `TRADING_SENSITIVE` and `RESTRICTED`.

Evidence: `market-intelligence/.../domain/ContextSensitivity.java:3-11` and
`market-intelligence/.../domain/context/ContextClassification.java:3-12`.

This is a contract boundary that ES-02 must reconcile explicitly. A ContextPack must not accidentally
reintroduce the compatibility sensitivity model as its authority model.

### Existing fingerprinting

`ContextFingerprintFactory` has schema version `analysis-context-v1`, includes market, execution mode,
evidence policy, objective, cutoff, sorted requirements and sorted sections, and returns an
`ArtifactFingerprint`.

Evidence: `market-intelligence/.../application/context/ContextFingerprintFactory.java:13-45`.

The section key and requirement ordering are canonicalized, but section payload, provenance and
message are converted through `String.valueOf`. Repository Analysis cannot yet claim that every nested
payload is canonical across insertion order or serialization implementations. ES-02 therefore needs a
contract-level canonicalization rule and tests for input-order independence before reusing this
fingerprint as the final ContextPack identity.

### Existing deterministic opportunity eligibility

`PassiveCandidateEvaluator` is the shared deterministic evaluator for passive awareness and
passive-to-active scan creation. It evaluates exact opportunity versions and adds explicit diagnostics
for inactive status, validity, missing or mismatched strategy matches, unavailable observations,
invalid thesis state, freshness problems, contradictions and non-positive required evidence.

Evidence: `market-intelligence/.../application/passive/PassiveCandidateEvaluator.java:35-40`,
`:65-91` and `:123-217`.

This evaluator is the correct source for ES-02 diagnostic eligibility. ES-02 must not duplicate its
rules or infer a blocking reason from an AI response. The repository currently returns a projection with
`eligible` and diagnostics; it does not expose Epic-specific `OPPORTUNITY_ASSESSMENT` or `DIAGNOSTIC`
mode contracts.

### Existing technical execution lifecycle

`AnalysisExecution` is an immutable technical aggregate with execution ID, idempotency key, policy,
provenance, trace metadata, retry metadata and optional consolidated result. It owns state transitions
and rejects transitions from terminal states.

Evidence: `market-intelligence/.../domain/execution/AnalysisExecution.java:8-16`, `:52-99` and
`:144-159`.

The current status enum is:

`REQUESTED`, `ACCEPTED`, `CONTEXT_BUILDING`, `RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`,
`CANCELLED`, `EXPIRED`.

Evidence: `market-intelligence/.../domain/execution/AnalysisExecutionStatus.java:3-16`.

`RECOVERY_REQUIRED` does not currently exist. Adding it will require explicit transition semantics and
tests. It must remain non-terminal and preserve the existing immutable aggregate behavior. Existing
retry metadata only permits retry when the last classification is `RETRYABLE` and the attempt budget is
not exhausted.

Evidence: `market-intelligence/.../domain/execution/RetryMetadata.java:6-26`.

`AnalysisExecutionService` already owns registration, idempotency, dispatch claiming, recovery of stale
accepted dispatches and provenance creation. It rejects uncontrolled historical reconstruction through
the normal boundary and preserves strict observed cutoff rules.

Evidence: `market-intelligence/.../application/execution/AnalysisExecutionService.java:21-71`,
`:104-130` and `:152-209`.

### Existing AI boundary

`AiEnginePort` is provider-independent and accepts an `AiAnalysisCommand` containing authorized
context, thesis, policy and trace. It supports submit, progress, result and cancel operations.

Evidence: `market-intelligence/.../application/port/AiEnginePort.java:5-18` and
`AiAnalysisCommand.java:11-24`.

`HttpAiAnalysisCapability` currently supports only `PASSIVE` and `ACTIVE` execution modes. It requires
a thesis for interpretation, submits the existing command, polls synchronously for up to thirty
seconds, cancels on interruption or timeout, and returns an empty `CapabilityAnalysisResult` warning
for failed, cancelled or timed-out AI execution.

Evidence: `market-intelligence/.../application/capability/HttpAiAnalysisCapability.java:34-45` and
`:76-118`.

This behavior is not yet compatible with ES-02's `RECOVERY_REQUIRED` semantics: an ambiguous timeout
is currently collapsed into a warning rather than preserved as an unresolved technical state.

`HttpAiEngineAdapter` maps the Python result into generic `IntelligenceFinding` values and
`CapabilityAnalysisResult`. It does not preserve an Epic-specific assessment or diagnostic result
contract.

Evidence: `market-intelligence/.../adapter/ai/HttpAiEngineAdapter.java:37-100`.

### Existing AI Engine contracts

The Python AI Engine uses strict Pydantic models with forbidden extra fields, camel-case wire aliases,
temporal validation for thesis evidence and a request fingerprint keyed by execution ID in the
process-local `ExecutionStore`.

Evidence: `ai-engine/app/domain/models.py:16-22`, `:88-119`, `:197-237` and
`ai-engine/app/infrastructure/execution_store.py:37-61`, `:165-167`.

The current Python request contract has `authorized_context`, thesis, generic `research_mode` values
(`THESIS_INTERPRETATION`, `ADAPTIVE_RESEARCH`, `HYPOTHESIS_DISCOVERY`) and interpretation phases. It
does not have Epic-specific opportunity identity/version, ContextPack identity/version, assessment or
diagnostic modes, or `RECOVERY_REQUIRED`.

Evidence: `ai-engine/app/domain/models.py:197-237`.

The Python result contract supports completed, partial, failed and cancelled outcomes and requires a
result for completed or partial outcomes. It does not persist records outside the process-local store.

Evidence: `ai-engine/app/domain/models.py:25-32`, `:293-312` and
`ai-engine/app/infrastructure/execution_store.py:37-46`, `:131-163`.

The AI service validates provider evidence references against thesis and authorized context evidence;
this is reusable grounding behavior, but it must remain downstream of the Java-owned ContextPack
authorization.

Evidence: `ai-engine/app/application/service.py:21-76`.

## Fit/Gap Assessment

| Story requirement | Current state | Assessment |
|---|---|---|
| Immutable ContextPack | Immutable `IntelligenceContext` exists | Adapt existing model; define Epic-specific identity and binding contract |
| Version and fingerprint | Context schema and fingerprint exist | Adapt canonicalization and include opportunity/version/provenance invariants |
| Authorized provider-neutral context | Disclosure policy and public section projection exist | Reuse boundary; resolve compatibility sensitivity versus classification authority |
| `OPPORTUNITY_ASSESSMENT` | No Epic-specific mode; only `PASSIVE`/`ACTIVE` | Build domain contract only in ES-02 |
| `DIAGNOSTIC` | No Epic-specific mode or result | Build bounded domain contract using evaluator diagnostics |
| Assessment/diagnostic result | Generic `CapabilityAnalysisResult` and `ConsolidatedIntelligence` exist | Define typed business result envelope without replacing technical execution result |
| Exact opportunity version acceptance | `TradingOpportunity` has ID/version; evaluator supports exact lookup | Reuse references and add result acceptance invariant |
| `RECOVERY_REQUIRED` | Missing from execution enum and transitions | Add explicit non-terminal state and transition tests |
| Idempotent AI submission | Java execution and Python execution ID fingerprinting exist | Reuse same execution identity; preserve mismatch rejection across boundary |
| Durable business-analysis result | Technical `AnalysisExecution` is durable, AI store is in-memory | Define projection-ready contract; defer persistence implementation |
| Python compatibility | Strict generic request/result contracts exist | Adapt wire mapping only after Java contract is approved |

## Affected Components

### Primary

- `market-intelligence` domain contracts for ContextPack, analysis mode, result identity and
  acceptance.
- `market-intelligence` execution status and transition model for `RECOVERY_REQUIRED`.
- `market-intelligence` context fingerprint and authorization mapping.
- `market-intelligence` tests for context, execution and opportunity eligibility.

### Secondary

- `market-intelligence` AI port and adapter wire mapping.
- `ai-engine` Pydantic request/result models and contract tests, only if the approved wire contract
  requires changes.

### Explicitly unaffected

- Trading Core, Risk Domain, Broker Service and Gateway.
- Broker credentials, provider activation and order paths.
- Frontend components.
- Market Thesis authority and deterministic opportunity status.

## Contract Risks

1. **Fingerprint false stability:** the existing fingerprint sorts container entries but does not prove
   canonical nested payload serialization. A ContextPack fingerprint must be deterministic across
   equivalent input orderings.
2. **Authority model split:** `ContextSensitivity` remains a compatibility field while
   `ContextClassification` governs newer disclosure decisions. The new contract must name the
   authoritative classification explicitly.
3. **Mode mismatch:** adding Epic modes beside `PASSIVE`/`ACTIVE` without defining their relationship
   could cause mode confusion in existing strategies and capabilities.
4. **Recovery collapse:** current synchronous capability behavior turns timeout into an empty warning;
   implementing ES-02 must preserve unknown technical state rather than treating it as a diagnostic or
   successful partial result.
5. **History ambiguity:** `AnalysisExecution` is a technical lifecycle aggregate, not yet a complete
   business-analysis history projection. ES-02 should define the projection-ready result contract but
   not silently introduce persistence semantics.
6. **Diagnostic overreach:** evaluator diagnostics include data-integrity and availability failures;
   not every diagnostic is necessarily product-approved for AI explanation. The policy mapping must be
   explicit and deterministic.

## Recommended Implementation Boundary

After Repository Analysis approval, the Implementation Plan should keep ES-02 contract-focused:

- define the smallest new domain types needed for ContextPack and typed results;
- extend existing fingerprinting only where required for canonical ContextPack identity;
- add `RECOVERY_REQUIRED` to the technical state machine with explicit allowed transitions;
- preserve `AnalysisExecution` as the lifecycle/idempotency boundary;
- keep orchestration, persistence projection and provider activation in later Stories;
- add tests before any adapter or Python wire change that would depend on the final contract.

This is an implementation direction, not an approved Implementation Plan.

## Validation Evidence

- Repository inspection completed against the Story, ADR-103, context assembly, eligibility,
  execution and AI boundary files.
- DevLog applicability check completed with truncated/unknown-freshness context; repository evidence
  was used as authority.
- No application tests were run because this stage produces Repository Analysis and does not change
  executable code.
- No code or configuration files were modified.
- Existing unrelated untracked ADR and Epic audit artifacts were preserved.

## Approval Gate

Repository Analysis is complete and requires explicit human approval before an Implementation Plan may
be prepared.
