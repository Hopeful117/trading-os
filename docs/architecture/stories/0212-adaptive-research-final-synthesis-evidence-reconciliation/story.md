# Story 0212 - Adaptive Research Final Synthesis & Evidence Reconciliation

## Metadata

**ID:** `0212`
**Title:** Adaptive Research Final Synthesis & Evidence Reconciliation
**Status:** Implementation completed - reviewed and ready for delivery
**Priority:** P1 - Trading Intelligence
**Predecessors:** `0208`, `0209`, `0210`, `0211`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-028`, `ADR-034`, `ADR-076`, `ADR-088`, `ADR-089`

## Goal

Complete one bounded adaptive research loop in which the authorized deterministic
follow-up evidence is passed to a final provider-independent AI synthesis and the
report explains whether that evidence confirmed, strengthened, weakened,
invalidated or failed to resolve the initial interpretation.

## Existing Limitation

The current Java workflow performs an initial adaptive AI interpretation, executes
at most one authorized deterministic follow-up, and records the follow-up result.
It does not send the actual follow-up evidence through a final AI interpretation
step. Consequently, adaptive reports retain `NOT_REEVALUATED` and cannot
demonstrate evidence-driven research value.

## Scope

- Extend the existing provider-neutral AI request/result contracts minimally for
  an explicit final synthesis phase.
- Assemble initial interpretation, authorized follow-up metadata, actual returned
  evidence and failure information in Java-owned final-synthesis context.
- Execute at most one initial AI interpretation, one authorized deterministic
  follow-up and one final AI synthesis.
- Prevent final synthesis from requesting another capability in Java.
- Validate final evidence references against the complete authorized evidence set.
- Reconcile initial and final hypotheses with explicit, evidence-backed effects.
- Persist enough bounded report data to audit the evolution without raw prompts,
  provider payloads or chain-of-thought.
- Extend the Story 0211 deterministic evaluator and fixtures for supported
  revision, confirmation/strengthening, contradiction, inconclusive follow-up,
  failed synthesis, fabricated references and no-follow-up cases.
- Preserve existing REST, MCP, ownership, idempotency and execution boundaries.

## Out of Scope

- New providers, OAuth changes, model configuration redesign or agent frameworks.
- More than one adaptive follow-up or recursive planning.
- New MCP tools, frontend workspace, broker integration, trade execution, risk
  changes, strategy activation or canonical Market Thesis mutation.
- Statistical proof, profitability claims or autonomous trading authority.
- Live LLM calls in standard CI; live validation remains opt-in.
- Persistence of raw prompts, credentials, raw provider payloads or hidden
  reasoning traces.

## Architecture Constraints

- Java Market Intelligence remains authoritative for lifecycle, authorization,
  deterministic evidence, validation, provenance, persistence and budgets.
- Python AI Engine remains advisory and provider-independent, owning initial
  interpretation and final synthesis only over Java-authorized context.
- The final synthesis is a terminal interpretation phase and cannot request a
  further capability.
- MCP remains a thin adapter exposing only the existing research lifecycle tools.
- A final synthesis may explain an interpretation change but may not promote a
  hypothesis into a canonical fact, risk decision or execution instruction.

## Proposed Interpretation Effects

The report should distinguish the following bounded states, reusing existing
semantics where possible:

- follow-up not executed;
- follow-up executed but not reevaluated;
- final synthesis completed with no material change;
- final synthesis completed with a supported revision;
- final synthesis inconclusive;
- final synthesis failed or unavailable.

Each hypothesis change must reference supplied initial or follow-up evidence.
Repeated wording without new evidence is not a research improvement.

## Acceptance Criteria

- [x] A valid adaptive request executes at most one initial AI call, one authorized
  deterministic follow-up and one final AI synthesis.
- [x] Final synthesis receives the original objective, initial interpretation,
  authorized follow-up metadata, actual follow-up result, provenance, freshness
  and failure/missingness information.
- [x] Java rejects or limits final outputs that request another capability.
- [x] Final evidence references are validated against the complete authorized
  evidence set; fabricated references cannot be persisted as valid findings.
- [x] Reports distinguish initial hypotheses, follow-up evidence, final
  hypotheses, reconciliation effects, remaining uncertainty and invalidation.
- [x] Supported revision, supported strengthening/confirmation, contradiction,
  inconclusive evidence and no meaningful change are distinguishable.
- [x] Follow-up failure preserves the initial interpretation and records the
  actual failure without invented evidence.
- [x] Final synthesis failure preserves the initial interpretation and actual
  follow-up result while marking final reevaluation unavailable.
- [x] Backward-compatible report deserialization remains supported.
- [x] Story 0211 evaluation coverage distinguishes the seven requested adaptive
  cases without live LLM calls.
- [x] Existing research ownership, idempotency, MCP and REST contracts remain
  compatible.
- [x] No trading, risk, broker, strategy or canonical Market Thesis authority is
  changed.

## Affected Modules

- `market-intelligence` research application, report domain, AI adapter and tests.
- `ai-engine` typed interpretation contracts, service validation and tests.
- `mcp-adapter` only if existing serialized report compatibility requires tests.
- Story-local architecture and implementation artifacts.

## Validation Expectations

- Focused Java tests for lifecycle sequencing, budgets, final context assembly,
  reconciliation and partial-result preservation.
- Python contract/service tests for final synthesis constraints and evidence
  validation.
- MCP compatibility tests if report serialization changes.
- Full affected-module suites and `git diff --check`.
- Optional bounded ChatGPT OAuth validation, clearly separated from CI.

## Workflow Gate

Implementation is not authorized until the Repository Analysis and the
Implementation Plan receive explicit human approval.
