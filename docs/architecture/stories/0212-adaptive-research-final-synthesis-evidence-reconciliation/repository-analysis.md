# Repository Analysis - Story 0212

## Status

Repository analysis completed and explicitly approved before implementation.

## Repository Baseline

- Repository: `Hopeful117/trading-os`
- Current branch: `story/0211-evidence-quality-market-research`
- HEAD: `1c76a35d187c5e03b47e0f1085de6fbc71350802`
- Working tree: clean at analysis time
- Story 0211 is merged into `main` through PR #255, but the local checkout is
  still on its dedicated branch.
- Required instructions read: `AGENTS.md`, `TOOLS.md`, ADR-088, ADR-089 and
  predecessor Story 0208-0211 artifacts.

## Context Sources

- Repository source, accepted ADRs and canonical Story artifacts were used as
  authoritative current context.
- DevLog MCP applicability was considered, but no DevLog MCP capability is
  available in the current tool session; no DevLog claim is used as a basis for
  this analysis.
- Obsidian was not consulted. The task is fully grounded in repository code,
  accepted ADRs and predecessor Story artifacts.

## Current Workflow

`ResearchApplicationService` owns the durable bounded research lifecycle. It
validates actor ownership and idempotency, persists `REQUESTED`, claims and
executes research, assembles deterministic market evidence, invokes the optional
AI capability and persists a bounded `ResearchReport` JSON document.

The current adaptive path in `buildReport` is:

1. Assemble a market thesis and deterministic analysis.
2. Call `HttpAiAnalysisCapability.analyzeAdaptive` once.
3. Record any AI-requested capability as proposed.
4. Validate the request against Java's allowlist, market scope and one-follow-up
   budget.
5. Execute the authorized deterministic follow-up through
   `CapabilityAnalysisCoordinator`.
6. Record capability status and execution ID in `executedCapabilities`.
7. Persist the original AI interpretation in `aiInterpretation`.

There is no step 8 that sends the follow-up result back to the AI Engine.

## Confirmed Limitation

`ResearchReport.AdaptiveCapabilityRecord` has `interpretationEffect`, but its
compatibility constructor defaults it to `NOT_REEVALUATED`. The current service
never updates it. Story 0211's evaluator therefore correctly reports Scenario F
as `INSUFFICIENT_EVIDENCE` for follow-up effect.

The follow-up result itself is not included in the market report's evidence or
interpretation context. Only its status and generated execution ID are retained.
This prevents an auditor from comparing initial and final evidence and prevents
the system from demonstrating a meaningful revision.

## Existing Contracts

### Java AI Boundary

- `AiEnginePort` is an asynchronous provider-independent boundary with submit,
  progress, result and cancel operations.
- `AiAnalysisCommand` carries authorized context, thesis, adaptive mode, policy
  and execution trace.
- `HttpAiAnalysisCapability` currently submits one command with
  `researchMode=ADAPTIVE_RESEARCH` and polls for up to 30 seconds.
- `CapabilityAnalysisResult` carries findings, warnings and one optional
  `RequestedAnalysis`; it has no phase or reconciliation model.
- The adapter maps Python structured results into `IntelligenceFinding` objects,
  preserving evidence references only on supporting and contradiction arguments.

### Python AI Engine

- `InterpretationRequest` carries one thesis, authorized context, execution
  policy, trace, idempotency key and `THESIS_INTERPRETATION` or
  `ADAPTIVE_RESEARCH` mode.
- `InterpretationDraft` contains summary, primary scenario, arguments,
  contradictions, missing information, conditions, uncertainty, decision and an
  optional requested analysis.
- `InterpretationService` validates supporting/contradictory evidence IDs
  against the supplied thesis evidence.
- The current model has no explicit initial interpretation, follow-up evidence,
  final-synthesis phase or hypothesis reconciliation fields.

## Architecture Fit

The smallest compatible change is an extension of existing typed contracts and
the Java research orchestration. It does not require a new service, framework,
MCP tool, persistence aggregate or ADR because responsibility remains inside
ADR-089's existing Java-controlled workflow and Python advisory reasoning
boundary.

Java must build the final context from authoritative initial and follow-up data,
enforce the terminal no-more-follow-up rule, validate references and persist the
reconciled report. Python may classify and explain changes only from that bounded
context.

## Required Design Decisions for the Plan

The implementation plan must resolve these details without broadening scope:

- Whether final synthesis reuses `InterpretationRequest` with an explicit phase
  and bounded `follow_up_evidence`, or introduces the smallest separate typed
  request/result records.
- How deterministic follow-up findings are converted into bounded evidence items
  with stable IDs, provenance and freshness without fabricating Market Thesis
  evidence.
- How initial and final hypotheses are represented and matched deterministically
  enough for Java to reject unsupported changes.
- Which small interpretation-effect state set preserves legacy
  `NOT_REEVALUATED` deserialization and distinguishes failure, inconclusive,
  no-change and supported revision.
- How final synthesis output is rejected or downgraded when it requests another
  capability or references evidence outside the complete authorized set.
- How one total AI-call budget is enforced across initial and final calls while
  preserving existing timeout and cancellation behavior.

## Persistence and Compatibility

`ResearchExecution` persists the report as bounded JSON and already supports
partial/failed lifecycle states. The report is returned through existing REST
and MCP retrieval. Additive report fields and compatibility defaults are
preferred; no migration is justified unless the current JSON contract cannot
represent the required audit trail.

The MCP adapter should remain unchanged unless a compatibility test demonstrates
that the additive report projection cannot be transported safely.

## Evaluation Integration

Story 0211's `ResearchEvaluation` currently checks follow-up governance and
whether `interpretationEffect` remains `NOT_REEVALUATED`, but it does not compare
initial and final evidence. Scenario F is the direct regression target.

The next evaluator extension should cover deterministic fixtures for:

- supported revision;
- supported strengthening or confirmation;
- contradictory follow-up evidence;
- inconclusive follow-up;
- final synthesis failure with preserved partial result;
- fabricated final evidence reference;
- no-follow-up path without an unnecessary final AI call.

These fixtures must remain provider-independent and must not claim market truth
or predictive performance.

## Risks and Constraints

- The existing AI capability is synchronous from the Java caller's perspective,
  although the AI Engine boundary is asynchronous; adding a second bounded call
  must not make the total research lifecycle exceed its existing operational
  budget.
- Follow-up `ConsolidatedIntelligence` is a deterministic domain result, not a
  `MarketThesisEvidence` instance; provenance mapping must be explicit and must
  not promote it into canonical thesis state.
- AI output is untrusted. Java must enforce evidence references and final-phase
  capability restrictions rather than relying on prompts.
- A failed final call must not replace or erase the initial interpretation or
  actual follow-up result.
- No hidden reasoning trace or raw provider payload may be persisted.

## Proposed Affected Files

- `market-intelligence/.../application/research/ResearchApplicationService.java`
- `market-intelligence/.../domain/research/ResearchReport.java`
- `market-intelligence/.../application/capability/HttpAiAnalysisCapability.java`
- `market-intelligence/.../application/port/AiAnalysisCommand.java` and related
  typed AI contracts
- `market-intelligence/.../adapter/ai/AiEngineAnalysisRequest.java` and response
  mapping if required
- `ai-engine/app/domain/models.py`
- `ai-engine/app/application/service.py`
- focused Java/Python/MCP tests
- Story-local implementation plan and later implementation report

No Gateway or broker/risk/trading code is expected to change.

## Approval Request

Approve this Repository Analysis to allow preparation of the detailed
Implementation Plan. Approval does not authorize implementation.
