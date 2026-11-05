# Implementation Report - Story 0205

## Repository State

- Story 0204 is merged into `main`.
- Implementation branch: `story/0205-ai-engine-runtime-foundation-market-thesis-interpretation`.
- No external provider, broker, Kraken, cTrader or real LLM call was used.
- The worktree contains the Story 0205 implementation and its canonical artifacts;
  no commit, push or merge has been performed.

## Existing Architecture Reused

- `AiEnginePort` remains the Java boundary for asynchronous submit, progress,
  result lookup and cancellation.
- `IntelligenceContext` remains the authorized context envelope and its public
  JSON projection is reused.
- `MarketThesis` and `MarketThesisEvidence` are serialized as an explicit
  deterministic snapshot; the AI result cannot mutate them.
- `DisabledAiEngineAdapter` remains selected by default.
- AI findings map to the existing `CapabilityAnalysisResult` and
  `IntelligenceFinding` contracts with `AnalysisOrigin.AI`.

## Python Service

Added `ai-engine/`, a standalone FastAPI/Pydantic service with:

- `/health` endpoint;
- asynchronous in-process execution resource endpoints for submit, progress,
  result and cancellation;
- strict Pydantic v2 request and result models;
- explicit thesis evidence, missingness and contradiction fields;
- evidence-reference validation;
- bounded context size, evidence count, output token, timeout and retry settings;
- disabled-by-default and shadow-mode configuration;
- deterministic mock provider for offline tests;
- configurable OpenAI provider adapter;
- service JWT validation on all internal analysis routes;
- no database, queue, broker access or frontend route.

## Java Integration

Added a conditional Spring Cloud OpenFeign adapter implementing `AiEnginePort`.
It maps the Java command, authorized context, deterministic thesis snapshot,
policy limits and trace metadata to the Python contract. It maps validated AI
interpretation output back to AI-originated findings without promoting the
result to Market Thesis, Risk, Trade Plan or Execution authority.

The HTTP adapter is enabled only with `ai.engine.enabled=true`; otherwise the
existing disabled adapter remains active. Service JWT configuration and bounded
timeouts are environment-backed. No user JWT or broker credential is forwarded.
The AI analysis capability follows the same feature flag: disabled mode keeps the
existing unavailable capability, while enabled mode submits the explicit Market
Thesis through `AiEnginePort` and waits for a bounded advisory result.
The production `Capability` planner receives the persisted latest thesis and
authorized context as execution parameters, and projects AI findings directly
into consolidated intelligence without requiring deterministic measurements.

## LLM Provider

The provider boundary is `LLMProvider`. The mock provider is used for automated
validation. The OpenAI adapter is configurable through `AI_ENGINE_API_KEY`,
`AI_ENGINE_MODEL`, `AI_ENGINE_TIMEOUT_SECONDS`, `AI_ENGINE_MAX_RETRIES` and
`AI_ENGINE_MAX_OUTPUT_TOKENS`. No real API key was used.

## Structured Output and Security

- Pydantic rejects unknown top-level fields and validates nested contracts.
- Evidence references returned by a provider must resolve to submitted evidence.
- Missing, unavailable, stale and contradictory evidence remains visible.
- Advisory decisions are limited to `WAIT`, `MONITOR`, `REVIEW_CANDIDATE` and
  `INSUFFICIENT_DATA`.
- No BUY/SELL decision, price level, risk parameter or execution command is
  accepted by the Python result model.
- Internal routes require an HS256 service JWT with issuer and audience checks.
- The internal JWT is carried in the repository-standard `X-Service-Authorization`
  header used by Java service clients.
- Prompts, API keys and service credentials are not logged.
- The OpenAI prompt excludes execution policy and trace internals from provider
  content while retaining correlation in the service resource.

## Shadow Mode

The service reports and preserves shadow mode, and the Java adapter remains
disabled unless explicitly enabled. The implementation does not write canonical
Market Thesis, Observation, Opportunity, Trade Plan, Risk or Execution state.

## Tests Executed

- `/tmp/opencode/trading-os-ai-engine-venv/bin/pytest`: passed, 8 tests.
- `python3 -m compileall -q app`: passed.
- `../trading-core/mvnw -q -DskipTests compile` in `market-intelligence`: passed.
- `../trading-core/mvnw -q -Dtest=HttpAiEngineAdapterTest,IntelligenceContextTest,IntelligenceOrchestratorTest test`: passed.
- `../trading-core/mvnw -q test` in `market-intelligence`: passed.
- `../trading-core/mvnw -q -Dtest=HttpAiEngineAdapterTest test` after final mapping changes: passed.
- `git diff --check`: passed.

The repository-wide quality workflow and a deployed service-to-service E2E test
were not run. No real LLM validation was attempted.

## Documentation Reconciliation

- Updated `README.md` to describe the standalone AI Engine foundation and local
  launch procedure.
- Updated `.gitignore` for local Python virtual environments.
- Docker Compose and Gateway were not modified: the service remains standalone
  and internal until an explicit deployment integration is approved.
- No ADR was changed because the implementation follows ADR-020, ADR-021,
  ADR-034 and ADR-076.

## Limitations

- Execution state is in-memory in the Python MVP; durable ownership remains with
  Java and the current adapter contract is the foundation for that integration.
- The existing Java `AiStructuredResult` exposes generic capability findings, so
  interpretation fields are represented as AI-originated findings and warnings
  rather than a new canonical aggregate.
- The current production pipeline does not automatically submit every persisted
  Market Thesis to the AI Engine; submission remains behind the existing
  `AiEnginePort` and explicit feature boundary.
- A real OpenAI call, cost measurement and deployed service-to-service E2E test
  require separate human authorization.

## First Authorized Real LLM Test

Before execution, obtain explicit approval for:

1. OpenAI model ID.
2. Exact Market Thesis fixture and public context fields transmitted.
3. Maximum number of calls.
4. Maximum estimated budget.
5. Expected structured result and evidence references.

Then enable the Python service and Java adapter with service JWT configuration,
run one bounded request against a non-sensitive fixture, verify the structured
result and confirm that Market Thesis, Risk and Execution state are unchanged.

## Angular Readiness

No Angular change is required. The AI result remains advisory and provider
neutral; a future Angular Story may consume it only through a Java projection
that preserves its AI origin, evidence references, uncertainty and non-authority.

## Vault Outcome

The Obsidian vault was not consulted. No vault action or proposal is required.
