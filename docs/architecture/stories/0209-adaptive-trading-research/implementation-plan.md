# Implementation Plan - Story 0209

## Plan Boundary

This plan implements one controlled adaptive research loop. It does not create
a generic agent framework, a new service or a second MCP architecture. The plan
is based on the approved Story 0209 Repository Analysis, ADR-088, ADR-089 and
the current repository implementation.

The plan was approved by the human engineer before implementation began.

## Vertical Slice

The workflow will use the existing research lifecycle and public MCP tools:

```text
POST research
  -> persist REQUESTED
  -> dispatch after commit
  -> assemble deterministic context
  -> AI proposes hypotheses and zero/one follow-up capability
  -> Java validates the proposal
  -> execute one existing deterministic capability
  -> AI performs one final synthesis
  -> persist report and terminal state
  -> status/report polling
```

The first implementation will support one follow-up request per research
execution. A proposal that does not require additional evidence completes with
the initial deterministic context. A rejected, malformed or budget-exhausted
proposal produces an explicit limited/partial result and never executes an
unauthorized capability.

## Phase 1 - Contract and Existing Capability Verification

Before implementation, confirm the exact current contracts and input artifacts
for the selected capabilities:

- `ResearchExecution`, `ResearchReport` and the JPA repository;
- `AiEnginePort`, `AiAnalysisCommand`, progress/result contracts and HTTP adapter;
- Python `InterpretationRequest`, `InterpretationDraft` and strict validation;
- `CapabilityMetadata`, `CapabilityRegistry`, `ExecutionPlanner` and
  `CapabilityAnalysisCoordinator`;
- artifact persistence and context assembly;
- existing lifecycle patterns for dispatch, claiming, cancellation and recovery.

Select the smallest allowlist from existing deterministic capabilities. The
initial candidate set is:

- `market-structure-analysis`;
- `ohlc-momentum-analysis`.

The final allowlist must be limited to capabilities whose required artifacts can
be assembled within the existing research context. Liquidity, spread,
tradeability and other capabilities remain excluded unless verification shows
they require no additional scope or architecture.

## Phase 2 - Adaptive Reasoning Contract

Extend the existing AI Engine request/result contract with bounded adaptive
fields rather than introducing a new provider or service:

- hypotheses with stable local IDs and evidence references;
- missing and contradictory evidence descriptions;
- zero or one `requestedAnalysis` item;
- requested capability ID and version;
- explicit market ID;
- reason and expected evidence;
- model limitations and invalidation conditions.

The Python models must remain strict (`extra="forbid"`) and validate:

- capability request count is at most one;
- market IDs are in the authorized research scope;
- evidence references belong to supplied evidence;
- requested analysis is not presented as executed evidence;
- output and list sizes remain bounded.

The Java adapter must reject unknown or fabricated references before persistence
as accepted reasoning. AI Engine errors, disabled mode, timeouts and malformed
responses must map to explicit lifecycle/report diagnostics.

## Phase 3 - Java Capability Authorization and Execution

Add a small research-owned capability catalogue or policy around the existing
registry. It must expose metadata without exposing internal tools through MCP.
Each allowlisted entry must define:

- capability identifier and version;
- required input artifacts;
- allowed market scope;
- timeout and cost budget;
- output artifact types;
- failure semantics.

Java validates every AI proposal against this catalogue, the original two-market
scope, the current execution budget and the proposal state. It then invokes the
existing deterministic planner/coordinator, preserving capability execution IDs,
artifact provenance, freshness and source cutoffs.

No Python code may invoke Java capabilities directly. No MCP tool is added for
individual capability execution.

## Phase 4 - Durable Asynchronous Lifecycle

Replace the current long-running synchronous research execution with the
smallest in-process durable worker boundary:

- the REST start operation validates and persists `REQUESTED`, then returns
  `202 Accepted` without waiting for research completion;
- dispatch occurs only after the initial persistence transaction commits;
- a worker claims one execution using an atomic status/claim transition;
- the claim includes a lease or equivalent timeout marker;
- recovery finds expired `RUNNING` claims and resumes or terminates them within
  the bounded policy;
- duplicate workers cannot execute the same adaptive step twice;
- every terminal transition is persisted exactly once at the application
  boundary;
- cancellation and expiry are cooperative and do not retry unsafe work.

Use existing Spring scheduling/executor conventions if they are sufficient. Do
not add Kafka, RabbitMQ, Redis or another broker. The exact claim and recovery
implementation must be confirmed against the current JPA transaction and
locking conventions during coding.

Extend `ResearchExecution` only with the minimum state needed for dispatch,
iteration, claim/lease, budget and terminal diagnostics. Keep the bounded report
JSON unless the verified decision trail exceeds safe row limits; if it does,
introduce only a small child table for requested/executed/rejected capabilities.

## Phase 5 - Report and Public Contract Compatibility

Extend `ResearchReport` with separate bounded collections for:

- hypotheses;
- requested capabilities;
- executed capabilities and execution IDs;
- rejected capabilities and rejection reasons;
- evidence references used by each conclusion;
- freshness, missingness, contradictions and limitations.

Preserve the existing three REST endpoints and MCP tool names. The start
response must remain a lifecycle response suitable for polling. The report
response must never imply that a proposed capability executed when it was
rejected, unavailable or budget-exhausted.

Update only the relevant MCP README and contract fixtures if fields change. Do
not expose internal reasoning steps or prompt payloads.

## Phase 6 - Security and Boundary Validation

Verify and test that:

- Gateway routes only public `/api/v1/intelligence/research/**` paths;
- JWT authentication and actor ownership remain enforced;
- actor-scoped idempotency is preserved for start requests;
- `/internal/**`, AI Engine, repository, provider, broker and risk paths are not
  reachable through MCP;
- AI Engine receives authorized context only, never JWTs or broker credentials;
- external text and AI-generated capability arguments are treated as untrusted
  data;
- no canonical Thesis, Opportunity, Strategy, Risk or Execution state mutates.

## Test Plan

### Market Intelligence Java

- adaptive request validation and strict one-request budget;
- allowlisted capability acceptance;
- unauthorized ID/version, wrong-market and missing-input rejection;
- fabricated/unknown evidence reference rejection;
- deterministic output provenance and freshness preservation;
- AI disabled, unavailable, timeout and malformed-output behavior;
- partial result after rejected request or exhausted budget;
- actor ownership and idempotency conflict behavior;
- after-commit dispatch and status polling after HTTP return;
- duplicate worker claim protection;
- interrupted worker recovery, lease expiry, cancellation and terminal states;
- no trade, risk, broker or canonical-state mutation.

### AI Engine Python

- strict adaptive request/result Pydantic validation;
- at most one requested capability;
- unknown evidence and unexecuted evidence claims rejected;
- malformed provider output, timeout and disabled provider fixtures;
- bounded hypotheses, arguments, evidence references and output size;
- deterministic mock responses for no-request, accepted-request and partial cases.

### Gateway and MCP

- public research route and authentication behavior;
- existing seven-tool discovery compatibility;
- start/status/report path and idempotency mapping;
- sanitized ownership and provider errors;
- no internal or direct AI Engine route exposure;
- stdio startup and source payload preservation.

## Documentation Reconciliation

Update only documentation affected by the new adaptive contract:

- Story 0209 implementation report;
- `mcp-adapter/README.md` if request/report behavior changes;
- any canonical API or operational documentation discovered during
  implementation.

No new ADR is expected. If implementation requires a new trust boundary,
service ownership change or remote MCP authorization, stop and propose an ADR
instead of changing ADR-089 implicitly.

## Risks and Tradeoffs

- In-process durable dispatch is smaller than a broker but needs explicit lease,
  recovery and duplicate-claim tests.
- One adaptive request proves control but limits research depth; deeper loops
  remain a later Story after operational evidence.
- Reusing the generic capability engine reduces duplication but requires a strict
  research allowlist so AI cannot select every registered capability.
- Keeping report details in bounded JSON is initially simpler; normalized child
  persistence should be introduced only if size or query requirements justify it.

## Validation Commands

```text
cd market-intelligence && mvn test
cd gateway && mvn test
cd ai-engine && .venv/bin/pytest
cd mcp-adapter && .venv/bin/pytest
git diff --check
```

Live Gateway, JWT, AI provider, broker and market-provider validation remains
optional and requires explicit environment, credential and budget authorization.

## Approval Gate

The implementation plan was approved and implementation is in progress. The
implementation report records the validated subset and remaining acceptance
criteria.
