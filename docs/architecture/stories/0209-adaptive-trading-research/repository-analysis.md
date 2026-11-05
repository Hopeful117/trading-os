# Repository Analysis - Story 0209

## Scope of Analysis

This analysis covers the current Story 0208 research workflow, ADR-088 and
ADR-089, the Java deterministic capability system, the AI Engine contract, the
Gateway boundary and the MCP adapter. It does not authorize implementation.

## Current Repository Facts

- `main` is clean at the merged Story 0208 result.
- `market-intelligence` owns the public research endpoints and persists
  `ResearchExecution`/`ResearchReport` through Flyway migration `V19`.
- `ResearchApplicationService.start` validates actor, exactly two distinct
  market IDs, objective length, depth, fingerprint and actor-scoped idempotency.
  It persists `REQUESTED`, then `RUNNING`, executes both markets synchronously
  and persists `COMPLETED`, `PARTIALLY_COMPLETED` or `FAILED`.
- `findOwned` enforces actor ownership for status and report retrieval.
- Gateway already routes `/api/v1/intelligence/**` to Market Intelligence and
  does not route `/internal/**`. Existing route and downstream integration tests
  cover the research lifecycle paths.
- The MCP adapter currently registers exactly seven tools: four direct
  application tools and three research lifecycle tools. It calls only public
  Gateway paths, propagates a caller-supplied JWT and requires a stable
  idempotency key for research start.
- The adapter has no research orchestration, prompts, AI client, persistence or
  direct service access. Its server identity is now `trading-os-research`.
- `AiEnginePort` is an asynchronous provider-independent port with submit,
  progress, result and cancel operations. `HttpAiAnalysisCapability` adapts it
  to the existing `AiAnalysisCapability` contract and currently waits up to 30
  seconds per market. The disabled capability remains the default when the AI
  Engine feature flag is missing or false.
- The Python AI Engine validates strict Pydantic requests, bounded context and
  output, and rejects provider evidence references not present in the supplied
  thesis. Its current provider contract interprets one supplied Market Thesis;
  it has no capability-request field or adaptive loop.
- Market Intelligence already has typed capability metadata, a registry,
  planner, execution engine, artifact requirements, timeouts and provenance.
  Existing candidates include `market-structure-analysis`, momentum,
  tradeability, liquidity and spread capabilities. Their actual input artifact
  requirements must be verified before selecting the Story 0209 allowlist.

## Architectural Reconciliation

ADR-089 explicitly selects Java-controlled orchestration with specialized
reasoning in the existing Python AI Engine. It requires Java to own
authorization, persistence, idempotency, timeouts, context classification and
public contracts. The AI Engine may propose hypotheses and missing evidence but
must not access Trading OS services or mutate canonical state.

ADR-088 remains the MCP anti-corruption boundary: MCP can expose the approved
application lifecycle, but it must not expose individual internal agent steps,
repositories, providers or AI Engine routes.

No new ADR is required for the proposed vertical slice if it remains within
these decisions. A new ADR would be required if the implementation changes the
MCP trust model, service ownership or introduces a new orchestration service.

## Concrete Findings From Story 0208

The bounded workflow is correct for its initial scope, but its synchronous
execution means an AI-enabled request can hold an HTTP client call for up to
roughly two per-market AI time budgets. A client disconnect does not provide a
durable cooperative cancellation boundary. Adaptive follow-up work would make
this risk materially worse.

The current lifecycle persistence and unique actor/idempotency constraint are a
useful base, but adaptive processing needs a durable dispatch/claim state or
equivalent protection against duplicate follow-up execution. This can be solved
with the existing persistence model and a small worker boundary; a broker is not
justified by current evidence.

The report already preserves deterministic market results, provenance,
freshness, evidence categories, invalidation conditions, AI status and
diagnostics. Story 0209 must add explicit requested/executed/rejected capability
records and evidence-reference validation without replacing those fields.

## Smallest Valuable Vertical Slice

The smallest measurable adaptive increment is one bounded loop:

1. Java assembles the existing two-market deterministic context.
2. The AI Engine returns hypotheses, missing evidence and zero or one typed
   follow-up capability request with reasons.
3. Java validates the request against an explicit allowlist, market scope,
   required inputs and one-request budget.
4. Java executes the selected existing deterministic capability through the
   current planner/execution infrastructure.
5. Java sends the bounded updated context back for one final interpretation.
6. Java persists a report containing both the decision trail and the final
   advisory result.

This proves adaptive value without implementing recursive planning, multiple
agents, unrestricted tool calling or new analysis algorithms. The existing MCP
start/status/report tools remain sufficient.

## Required Contract Decisions For The Plan

The implementation plan must resolve, using existing types where possible:

- the Java/Python adaptive request and response DTOs;
- capability identifier/version representation and allowlist ownership;
- evidence reference format and validation source;
- how capability requests are recorded before execution;
- dispatch-after-commit and claim/recovery semantics;
- terminal state behavior for timeout, cancellation, malformed AI output and
  budget exhaustion;
- context and output byte/token limits;
- whether the existing `ResearchExecution` JSON report can safely hold the
  bounded decision trail or needs a small normalized child persistence model.

## Risks and Tradeoffs

- Extending the AI Engine contract without Java validation would create an
  unauthorized tool-calling path; this is a blocking design constraint.
- Reusing the generic capability registry is preferable to duplicating analysis,
  but the research allowlist must not accidentally expose every registered
  capability.
- A worker implemented with an in-process executor is smaller than a message
  broker but requires recovery scanning and duplicate-claim tests.
- Keeping the first adaptive budget to one request makes the result less
  powerful, but gives a reviewable proof of control and avoids a generic agent
  platform.

## Validation State

- Story 0208 full Market Intelligence suite: 848 tests passed.
- Gateway full suite: 51 tests passed.
- MCP adapter suite: 29 tests passed.
- No live Gateway/JWT/AI Engine/provider validation was executed.
- No implementation for Story 0209 has been started.

## Gate

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is produced. The next permitted artifact after approval is
the Story 0209 Implementation Plan, followed by a separate implementation
approval gate.
