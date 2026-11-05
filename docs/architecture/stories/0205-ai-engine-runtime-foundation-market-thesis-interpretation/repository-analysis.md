# Repository Analysis - Story 0205

## Scope and Authority

This analysis is based on the canonical Story 0205, the current repository,
accepted ADR-020, ADR-021, ADR-034 and ADR-076, the Story 0204 implementation
report, and the current Git state.

DevLog was queried for bounded Story context. The first request used an invalid
empty Story UUID and was retried without `storyId` as permitted by the protocol.
The resumed request completed, but its projection was truncated and did not
include the Story 0205 file. Its useful evidence was limited to the shared
IntelligenceContext, Market Intelligence orchestration boundary and Story 0204
serialization outcome. The repository remains authoritative.

The Obsidian vault was not consulted. No transverse note was required to resolve
the current repository and architecture boundaries.

## Repository State

- Current branch: `main`.
- HEAD: `3b7fcba38461ac52e1a132e9d9e6cbf9b0499c0a`.
- Story 0204 is merged into `main`.
- Working tree was clean before creating the Story 0205 artifact.
- The canonical Story artifact now exists at
  `docs/architecture/stories/0205-ai-engine-runtime-foundation-market-thesis-interpretation/story.md`.
- No `ai-engine` service currently exists.
- No repository-wide Python service convention exists. The only Python project
  is the offline research module at `research/ml`, using setuptools and pytest
  tests but no HTTP service framework.
- No OpenAI, AI Engine or LLM configuration exists in the repository.

## Current Architecture

Trading OS separates deterministic market intelligence, AI interpretation, risk
and execution. AI may interpret structured context but cannot become a source of
truth or authorize execution.

Market Intelligence owns context assembly, deterministic capability execution,
Market Thesis composition, orchestration and AI capability coordination. The
AI Engine must remain a specialized service called by Market Intelligence and
must not access Java repositories or source services autonomously.

ADR-021 defines AI execution as an asynchronous, governed execution boundary.
The logical lifecycle includes request, acceptance, running, partial completion,
completion, failure, cancellation and expiry. The AI boundary must include
context classification, idempotence, time and resource limits, cancellation,
security and structured results.

## Existing Java Contracts

`AiEnginePort` already defines the intended integration boundary:

- `available()` reports whether the engine is configured.
- `submit(AiAnalysisCommand)` returns an `AiExecutionReference`.
- `progress(AiExecutionReference)` returns `AiExecutionProgress`.
- `result(AiExecutionReference)` returns an optional `AiStructuredResult`.
- `cancel(AiExecutionReference)` requests cooperative cancellation.

`AiAnalysisCommand` contains:

- execution ID;
- requested capability IDs;
- authorized `IntelligenceContext`;
- `AnalysisExecutionPolicy`;
- security and correlation `ExecutionTrace`.

`AnalysisExecutionPolicy` already carries maximum duration, capability timeout,
AI request budget, concurrency, context limits, retry policy, priorities and
degradation policy. The Python service must consume bounded values rather than
inventing a second timeout or retry policy.

`AiStructuredResult` currently wraps an `AiExecutionReference`, a
`CapabilityAnalysisResult` and an acceptance timestamp. `CapabilityAnalysisResult`
contains only `IntelligenceFinding` values and warnings. A Market Thesis
interpretation must therefore be represented as AI-originated structured
findings or require a narrowly scoped contract extension. It must not be mapped
into canonical `MarketThesis`, `StrategyMatch`, `TradingOpportunity`, Risk or
Execution objects.

`DisabledAiEngineAdapter` is the current Spring component. It reports
unavailable, never fabricates a result, rejects submission/progress/result
operations while disabled, and treats cancellation as a no-op. Story 0205 must
preserve disabled-by-default behavior and make the real adapter conditional.

`DisabledAiAnalysisCapability` already declares the AI capability ID
`ai-market-reasoning`, supports passive and active modes, requires public market
identity and snapshot context, and optionally requests historical OHLC in active
mode. Its current `analyze` method fails explicitly because asynchronous AI
execution is not configured.

## Existing Deterministic Context and Thesis

`IntelligenceContext` is the existing provider-neutral envelope. Story 0204 made
its selected public projection serializable while preserving the complete
domain `sections()` accessor. The JSON projection excludes legacy
`USER_PRIVATE` sections; the current Java contract must remain the source of
authorized data.

`ContextContributionDescriptor` uses the richer ADR-021 classifications:
`PUBLIC`, `INTERNAL`, `USER_CONFIDENTIAL`, `TRADING_SENSITIVE` and `RESTRICTED`.
The older `ContextSensitivity` field remains a compatibility projection. The
Java adapter must not treat a legacy `PUBLIC` value as sufficient authorization
when richer descriptor data marks content as restricted.

`MarketThesis` is deterministic and immutable. It preserves market identity,
state, scenario, evidence, reasons, timestamps, validity, cutoff, composer
version, input fingerprint and lineage. `MarketThesisEvidence` preserves role,
availability, contradiction severity, source reference, timeframe, explanation,
provenance and requiredness. AI output may explain this thesis but cannot mutate
or replace it.

The existing pipeline persists Market Thesis independently of AI. AI errors must
therefore degrade or skip the AI capability without interrupting deterministic
pipeline completion.

## Integration Conventions

Market Intelligence uses Spring Boot 4, Java 21, Spring Cloud OpenFeign and
Eureka for service-to-service communication. Existing clients use typed Feign
interfaces and provider-specific configuration for timeouts and service JWTs.
The new adapter should follow this pattern unless a focused contract requires a
different client.

The current Market Intelligence configuration has explicit active/passive
timeouts and service JWT settings. Any AI Engine URL, enable flag and bounded
transport settings should follow the existing environment-backed property
pattern and must default to disabled.

Docker Compose currently has no `ai-engine` service. Adding the service to
Compose would change runtime/deployment documentation and requires a focused
configuration update, including an internal network trust model and no public
frontend route.

## Python Foundation

The only existing Python package is `research/ml`:

- Python `>=3.11`.
- setuptools build backend.
- pinned scientific dependencies.
- package source under `src`.
- pytest-style tests.

This provides a packaging and test precedent but not a FastAPI service
convention. The Story explicitly authorizes a simple autonomous `ai-engine`
service with FastAPI, Pydantic and pytest. The implementation should avoid
additional infrastructure such as queues, caches or databases.

Current Context7 documentation confirms the relevant current library patterns:

- FastAPI request bodies are declared with Pydantic models and domain errors can
  use explicit exception handlers.
- Pydantic v2 supports strict field constraints and `model_dump`/JSON
  serialization for validated models.
- The OpenAI Python SDK supports bounded client timeouts and configurable
  `max_retries`; the implementation must configure both explicitly rather than
  rely on defaults.

## Required Boundaries

The implementation must preserve:

- deterministic Market Thesis as the analytical authority;
- AI as advisory/shadow-only output;
- no AI writes to canonical Market Thesis;
- no AI access to Risk, Execution, Broker Service or Java repositories;
- no broker credentials, user JWTs or private account data in prompts;
- no frontend-to-Python communication;
- no real LLM calls during automated tests;
- no external provider calls during offline validation;
- no automatic order, Trade Plan or risk authorization.

## Risks and Unknowns

### Confirmed risks

- The current Java result contract is generic and does not explicitly model a
  Market Thesis interpretation. A plan must define a minimal mapping that keeps
  evidence references and AI origin visible.
- ADR-021 requires an asynchronous boundary, while the first Python service may
  naturally perform a bounded provider call. The HTTP API must expose accepted
  execution semantics rather than bypassing the Java port with an unbounded
  synchronous request.
- Existing context classification is richer than the compatibility
  `ContextSensitivity` field. The adapter must use the authorized serialized
  projection and must not reconstruct authorization in Python.
- Full repository quality checks can be long-running; targeted checks and
  service-specific offline tests must be explicit.

### Unknowns requiring plan-level resolution

- Exact Python HTTP resource shape for submit, progress, result and cancel.
- Whether the Java adapter should be implemented with OpenFeign or a different
  existing Spring HTTP abstraction after checking timeout and service-JWT
  configuration patterns.
- Exact `IntelligenceFinding` fields available for representing structured AI
  arguments and decision diagnostics.
- Whether Docker Compose inclusion is required for the local-start acceptance
  criterion or whether a standalone local launch is sufficient for this Story.
- Whether adding a Python service requires CI workflow changes or a dedicated
  coverage command.

## Scope Recommendation

The smallest coherent implementation should include:

- a disabled-by-default FastAPI service with health, submit, progress, result
  and cancel endpoints aligned to the asynchronous Java boundary;
- strict Pydantic input/output models for authorized public context and Market
  Thesis interpretation;
- a provider interface, mock provider and one OpenAI adapter behind explicit
  configuration;
- evidence-reference validation and explicit invalid/partial failure states;
- a Java HTTP adapter implementing `AiEnginePort`, conditionally selected over
  `DisabledAiEngineAdapter`;
- offline Python, Java adapter and contract tests;
- narrowly scoped configuration and local runtime documentation only where
  required by the service.

No implementation should begin until the corresponding Implementation Plan is
written and explicitly approved by the human engineer.

## Validation Baseline

Before implementation, the repository has established:

- Story 0204 merged successfully.
- Market Intelligence targeted tests and broader quality checks previously pass
  in CI for the merged Story 0204 branch.
- Story 0204 independently reviewed the public context projection and found no
  material remaining issue.

The Story 0205 validation plan must add Python tests, Java adapter tests,
contract tests and affected Market Intelligence non-regression tests. A real
LLM call remains a separate, explicitly authorized validation step.

## Documentation Outcome

The Story 0205 implementation will change architecture, runtime configuration,
service startup and inter-service API behavior. Canonical README, Compose or
service documentation updates will be reconciled after the approved
implementation is complete. No ADR change is currently required by the
repository evidence.

## Vault Outcome

The Obsidian vault was not consulted. No vault proposal is created at this
stage.
