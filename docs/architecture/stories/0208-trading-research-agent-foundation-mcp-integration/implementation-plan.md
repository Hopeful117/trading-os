# Implementation Plan - Story 0208

## Planned Vertical Slice

Implement one Java-controlled, two-market research workflow with a durable
research lifecycle and a structured advisory report. Reuse existing Market
Intelligence deterministic evidence and the Python AI Engine only through
`AiEnginePort`. Expose the workflow through public Gateway contracts and local
MCP tools.

## Phase 1 - Contract and Boundary Verification

Inspect and confirm the existing types before coding:

- `AnalysisExecution`, `AnalysisExecutionService` and persistence;
- `AiEnginePort`, command/progress/result contracts and the HTTP capability;
- `IntelligenceContext`, public context projection and Market Thesis;
- active-scan ownership/idempotency patterns;
- Gateway route and Market Intelligence security configuration;
- current MCP tool registration and client behavior.

If an existing aggregate can represent the research lifecycle without semantic
loss, reuse it. Otherwise introduce the smallest `ResearchExecution` and
immutable report/reference model inside Market Intelligence. Do not create a
new service during this Story.

## Phase 2 - Java Research Application Boundary

Add the application command/query boundary for:

- start research;
- read research status;
- read research report.

The boundary must authenticate the actor, validate explicit market IDs and
bounded depth, enforce actor ownership, compute a request fingerprint, enforce
idempotency and apply execution/context/AI budgets.

Research context must reference existing deterministic evidence rather than copy
large raw market data. Child analysis executions and AI execution references must
remain traceable.

## Phase 3 - AI Research Interpretation

Extend the existing AI Engine contracts only where needed for a specialized
research interpretation. Use strict Pydantic models, evidence-reference
validation, bounded context/output and explicit statuses for disabled, failed and
partial interpretation.

The Java boundary remains responsible for the authorized context and lifecycle.
The Python service must not receive user JWTs, broker credentials or unrestricted
account data. The mock provider remains the automated-test provider; no real LLM
call is required.

## Phase 4 - Public REST and MCP

Expose the Java application boundary through versioned public Gateway routes.
Update Gateway routing/security tests when required. Add typed MCP tools mapped
only to these public routes. Preserve all four existing direct tools and keep
MCP free of workflow or prompt logic.

Use bounded request fields and response-size limits. Preserve provenance,
contradictions, missingness and model metadata without semantic promotion.

## Phase 5 - Documentation and Workflow Validation

Document the OpenCode interaction:

1. discover canonical BTC and ETH markets;
2. submit the bounded research request;
3. poll status through the public contract;
4. retrieve the report;
5. present deterministic facts, AI interpretation, hypotheses, contradictions,
   missing evidence and limitations separately.

Document that no result authorizes a trade and that live validation is only
performed with explicit JWT, account, environment and provider-budget approval.

## Affected Modules

Expected modules, subject to Phase 1 verification:

- `market-intelligence` application/domain/persistence/web packages;
- `ai-engine` Pydantic/API/application packages only where the research contract
  requires extension;
- `gateway` route/security tests and configuration only for approved public
  routes;
- `mcp-adapter` server, client tests and README;
- Story 0208 documentation and ADR reconciliation.

No changes are expected in Broker Service, Risk Domain, execution workflows or
provider adapters.

## Test Plan

### Java

- request validation and explicit market scope;
- actor/account ownership;
- idempotency and request fingerprint conflicts;
- lifecycle transitions, expiry, cancellation and partial results;
- deterministic context/reference assembly;
- report persistence and retrieval;
- AI disabled, unavailable, timeout and malformed-result isolation;
- no mutation of Market Thesis, opportunities, strategy, Risk or Execution;
- Gateway route and authorization behavior.

### Python

- research request/response Pydantic validation;
- evidence reference validation;
- competing hypotheses and contradiction preservation;
- missing/stale evidence;
- disabled provider, timeout, malformed output and partial result;
- bounded context and output limits;
- secret and prompt-injection-safe handling.

### MCP

- tool discovery and typed schemas;
- public Gateway path mappings;
- authentication and ownership failures;
- idempotency/error projection;
- faithful report retrieval;
- no internal, provider, broker, risk or execution paths;
- stdio startup with all existing direct tools preserved.

All automated tests remain offline and use deterministic fixtures.

## Risks

- A new research aggregate could duplicate `AnalysisExecution`; resolve this
  before implementation and document the semantic distinction.
- The current AI Engine MVP is in-memory; durable research ownership must remain
  in Java and not be delegated to Python.
- Research commands expand ADR-088 beyond read-only access; security and human
  confirmation must be explicit.
- Multi-market reports can exceed context limits; enforce bounded scope and
  preserve explicit omission/missingness.
- AI hypotheses may be mistaken for deterministic facts; use typed origin and
  provenance fields throughout the report.

## Out of Scope Validation

No live Gateway, broker, Kraken, cTrader or paid LLM validation is executed by
default. Any real-provider test requires prior approval of the model, exact
context, call count, budget, account and expected output.

## Recommendation

The plan was approved before implementation. The resulting implementation is
documented in `implementation-report.md`; code review and remaining validation
gates are still required before Story closure.
