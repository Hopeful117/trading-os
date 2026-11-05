# Story 0209 - Controlled Adaptive Trading Research

## Metadata

**ID:** `0209`
**Title:** Controlled Adaptive Trading Research
**Status:** Implementation in progress - Plan approved
**Priority:** P1 - Trading Intelligence
**Predecessor:** `0208`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-028`, `ADR-034`, `ADR-076`, `ADR-088`, `ADR-089`

## Goal

Extend the Java-owned Trading Research workflow with one bounded adaptive
investigation loop. The research agent may identify one useful missing analysis,
request it through a typed contract, and receive the deterministic result before
producing the final advisory report.

The result must demonstrate measurable value beyond a single fixed AI
interpretation while preserving deterministic evidence authority and human
control.

## Context

Story 0208 introduced a durable two-market research lifecycle, deterministic
evidence assembly, optional AI interpretation and seven MCP capabilities. Its
current execution is synchronously orchestrated and supports no AI-requested
follow-up analysis.

ADR-089 assigns durable orchestration and authorization to Java Market
Intelligence, specialized reasoning to the Python AI Engine and client
interaction to MCP/REST. This Story implements the smallest vertical slice of
that boundary rather than a generic agent framework.

## Problem

The current workflow can interpret supplied evidence but cannot determine which
additional approved evidence would reduce uncertainty or test a contradiction.
Adding unrestricted tool calling would bypass Java authorization, deterministic
capability governance and execution budgets.

## Scope

- Add a versioned, typed adaptive research reasoning contract behind the existing
  `AiEnginePort` boundary.
- Allow the AI Engine to return hypotheses, evidence references, missing
  evidence and at most one requested follow-up capability per research
  execution.
- Validate every requested capability in Java against a small explicit allowlist,
  the two-market research scope and a single follow-up budget.
- Execute only existing deterministic Market Intelligence capabilities; do not
  duplicate indicators or analysis algorithms in Python.
- Persist requested and executed capability records, evidence references,
  rejection reasons and final report provenance.
- Make the research execution durable across the potentially long adaptive
  operation without holding the initiating HTTP request open.
- Preserve the existing `start_market_research`, `get_research_status` and
  `get_research_report` MCP tools by extending their application contracts only
  where required.
- Add deterministic offline fixtures for accepted, rejected, malformed, stale,
  partial and budget-exhausted adaptive results.

## Out of Scope

- Generic multi-agent or LangGraph-style orchestration.
- More than one adaptive follow-up iteration in this Story.
- New market-data providers, indicators, backtesting or strategy discovery.
- StrategyDefinition creation, promotion or activation.
- TradePlan creation, Risk evaluation, execution intent or broker operations.
- Direct AI Engine, repository, database, provider or broker access from MCP or
  the Python service.
- Kafka, RabbitMQ, Redis or a new orchestration service.
- Remote MCP, OAuth or a conversational Angular interface.
- Live LLM, broker or provider access in CI.

## Proposed Capability Boundary

The initial allowlist should be limited to existing deterministic capabilities
whose inputs can be assembled from the current public research context. The
implementation plan must select the smallest verified set, likely including
market structure or momentum analysis, and define for each:

- stable identifier and version;
- required input artifacts;
- market-scope rule;
- execution cost and timeout;
- output/provenance contract;
- failure and rejection semantics.

AI-generated capability IDs, market IDs and evidence references are untrusted.
Unknown or fabricated values must be rejected without execution.

## Execution Budgets

The Java boundary must enforce explicit limits for:

- one adaptive iteration;
- one follow-up capability request;
- bounded concurrent work;
- bounded context and report size;
- bounded AI calls and output tokens;
- deterministic capability timeout;
- no automatic retry of rejected or unsafe requests.

Budget exhaustion produces an explicit partial or limited report.

## Acceptance Criteria

- [ ] A research execution can receive a structured adaptive proposal without
  exposing internal Java or Python implementation details to MCP.
- [ ] Java accepts only allowlisted, versioned capabilities within the original
  two-market scope and rejects unauthorized, malformed or fabricated requests.
- [ ] Deterministic capability output preserves source identity, freshness,
  provenance, contradictions and missingness.
- [ ] The final report distinguishes hypotheses, requested capabilities,
  executed capabilities, rejected requests, evidence-supported scenarios and
  limitations.
- [ ] The workflow returns an explicit partial/limited result when the adaptive
  budget, timeout or provider availability is exhausted.
- [ ] Interrupted or duplicated processing cannot create duplicate follow-up
  execution or inconsistent terminal lifecycle state.
- [ ] Status polling and report retrieval work after the initiating HTTP request
  has ended.
- [ ] AI Engine validation rejects unknown evidence references and does not allow
  the provider to claim unexecuted evidence.
- [ ] No Market Thesis, Opportunity, Strategy, Risk, TradePlan, ExecutionIntent,
  position or broker state is mutated.
- [ ] Existing seven MCP tools remain compatible and no internal capability is
  exposed as a separate MCP tool.
- [ ] Automated tests cover accepted/rejected capability selection, malformed
  AI responses, stale/missing/contradictory evidence, budget exhaustion,
  duplicate processing, ownership and interrupted execution recovery.

## Constraints

- Preserve ADR-089 responsibility boundaries.
- Keep deterministic validation, authorization, persistence and capability
  execution in Java.
- Keep the AI Engine advisory and context-limited.
- Reuse existing Market Intelligence capability metadata and execution engine.
- Preserve actor ownership and idempotency from Story 0208.
- Do not weaken Spring Security or expose `/internal/**` routes.
- Use deterministic fixtures and mocked AI responses in CI.

## Relevant Modules

- `market-intelligence`
- `ai-engine`
- `gateway` only if public contract or route tests require adjustment
- `mcp-adapter` only for compatible request/report schema changes

## Validation

- Targeted Java unit and persistence concurrency tests.
- AI Engine Pydantic contract and provider-validation tests.
- Gateway route/security tests when contracts change.
- MCP compatibility and stdio tests.
- Full tests for affected modules.
- No live LLM, broker, provider or authenticated environment is required for CI.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Documentation reconciled.
- [ ] Relevant validation executed.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit and delivery approval completed.
