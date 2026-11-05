# Implementation Plan - Story 0205

## Operating Mode

The implementation is offline-first and shadow-only. No OpenAI call, external
network call, broker call, Kraken call, cTrader call, order or credential use is
authorized during automated validation. A real LLM validation remains a later
human-approved action with an explicit model, payload, call budget and cost
budget.

Preserve Stories 0198-0204, deterministic Market Thesis authority, Risk,
Execution, Broker Service, cTrader protections and Angular boundaries. Do not
commit, push or merge without explicit human authorization.

## Design Decisions

1. Reuse `AiEnginePort` as the Java asynchronous boundary. The Python HTTP API
   will model submit, progress, result and cancel as resources even if the first
   provider call is bounded within the Python execution worker.
2. Keep `DisabledAiEngineAdapter` as the default Spring bean behavior. Select the
   HTTP adapter only when an explicit AI Engine enabled flag is true and its
   endpoint is configured.
3. Add only the minimum wire contract needed for Market Thesis interpretation.
   The Java adapter will send an explicit provider-neutral interpretation input
   containing the authorized context and a serialized deterministic thesis
   snapshot. It will not send service handles, credentials, JWTs or private
   account data.
4. Extend `AiAnalysisCommand` only if current call sites cannot carry the thesis
   snapshot without reconstructing it from unrestricted services. The extension
   must remain a value object and preserve existing callers through an explicit
   migration of all constructors and tests, not compatibility shims.
5. Represent AI output as AI-originated `IntelligenceFinding` values inside the
   existing `CapabilityAnalysisResult` contract. Do not write or mutate
   `MarketThesis`, `Observation`, `StrategyMatch`, `TradingOpportunity`,
   `TradePlan`, Risk or Execution state.
6. Use a strict Pydantic v2 contract. Missing, unknown, invalid, stale and
   contradictory inputs remain explicit. No absent value becomes a neutral value
   and no unknown evidence reference is accepted.
7. Implement one OpenAI provider adapter behind a small `LLMProvider` interface,
   but keep the feature disabled by default. A deterministic mock provider is the
   only provider used in automated tests.
8. Use bounded timeout and retry settings from configuration. Configure the
   OpenAI client explicitly rather than relying on SDK defaults, and never log
   prompts, API keys or provider credentials.
9. Treat provider output as untrusted data. Validate its JSON and semantic
   evidence references before returning a completed AI result. Invalid provider
   output becomes an explicit failed or invalid result and is never published as
   valid analysis.
10. Keep the public Python surface internal-only. Do not add a Gateway route or
    frontend client. The service will be addressable only through the internal
    service network and Java adapter, with authentication/trust configuration
    matching existing service-to-service conventions.

## 1. Python Service Foundation

Create `ai-engine/` using Python 3.11+, FastAPI, Pydantic v2, pydantic-settings,
httpx/OpenAI SDK and pytest. Keep the service stateless; execution state is
bounded in-process for the MVP and is not a replacement for Java persistence.

Planned components:

- `app/main.py`: FastAPI application and health endpoint.
- `app/config.py`: environment-backed settings, disabled by default, bounded
  context/request limits and provider configuration.
- `app/api/routes.py`: internal submit, progress, result and cancel endpoints.
- `app/domain/models.py`: strict request, context, thesis, evidence and result
  models.
- `app/domain/errors.py`: explicit invalid-context, provider and execution
  errors.
- `app/application/service.py`: validated interpretation orchestration.
- `app/application/ports.py`: provider and execution-store protocols.
- `app/infrastructure/providers/mock.py`: deterministic offline provider.
- `app/infrastructure/providers/openai.py`: configurable real provider adapter.
- `app/infrastructure/execution_store.py`: bounded in-memory execution state.
- `tests/`: unit, API and contract tests.
- `pyproject.toml` and `Dockerfile`: local and container startup.

Endpoints:

- `GET /health`: service status and enabled/disabled state without secrets.
- `POST /internal/v1/analyses`: validate and accept an analysis request, return
  an execution reference and `202` semantics.
- `GET /internal/v1/analyses/{execution_id}`: return execution progress/status.
- `GET /internal/v1/analyses/{execution_id}/result`: return a validated result or
  a non-terminal/failed state.
- `POST /internal/v1/analyses/{execution_id}/cancel`: request cancellation.

The service will enforce request body size, maximum evidence count, maximum text
length and bounded execution duration before invoking a provider.

## 2. Python Contract

Define an explicit `MarketThesisInterpretationRequest` containing:

- execution ID and request/correlation IDs;
- market ID, instrument and provider/product identity;
- analysis cutoff, evaluated-at, validity timestamps;
- deterministic thesis state, scenario, reasons and conditions;
- supporting, contradicting and missing evidence;
- authorized public context sections and provenance;
- rule/composer/capability versions and fingerprints;
- completeness/freshness/diagnostic statuses;
- bounded execution policy values required by the service.

Define `MarketThesisInterpretationResult` containing:

- execution reference and status;
- result quality (`COMPLETE`, `PARTIAL`, `DEGRADED` or explicit failure);
- market summary and primary scenario interpretation;
- supporting arguments, contradictions, missing information;
- confirmation and invalidation conditions;
- uncertainty and diagnostics;
- advisory decision enum restricted to `WAIT`, `MONITOR`,
  `REVIEW_CANDIDATE` and `INSUFFICIENT_DATA`;
- evidence references for every argument where applicable;
- model/provider metadata without secrets;
- timestamps and correlation identifiers.

Pydantic validators will enforce:

- UUID and timestamp consistency;
- no future observed evidence for the cutoff;
- evidence references resolve to supplied evidence IDs;
- no BUY/SELL decision values;
- no invented price levels or risk parameters;
- contradictions and missing evidence remain represented;
- context and result size limits.

## 3. Provider and Interpretation Service

`MarketThesisInterpretationService` will:

1. Validate the authorized input.
2. Reject or degrade when required deterministic context is unavailable.
3. Build a bounded prompt from structured data with explicit untrusted-content
   delimiters and fixed system instructions.
4. Invoke the configured `LLMProvider` only when enabled.
5. Parse and validate structured provider output with Pydantic.
6. Validate all evidence references and advisory decision constraints.
7. Return a completed, partial, degraded or failed execution result.

The mock provider will return deterministic fixtures for offline tests. The
OpenAI adapter will use the current SDK's structured-output capability where
available, explicit timeout and limited retries, and a model ID from settings.

## 4. Java Integration

Add a typed HTTP adapter in Market Intelligence implementing `AiEnginePort`.
The adapter will:

- map `AiAnalysisCommand` to the Python submit request;
- serialize only the authorized public context projection and explicit thesis
  snapshot;
- attach service-to-service authentication using the existing internal pattern,
  never the user JWT;
- map references, progress, results, cancellation and failures to existing Java
  port types;
- preserve idempotence using execution ID and an idempotency key;
- enforce connect/read timeouts from environment-backed configuration;
- classify timeout/unavailable/invalid responses without failing deterministic
  scanning;
- return AI-originated findings and warnings without promoting them to canonical
  state.

If the current `AiStructuredResult` cannot carry the validated interpretation
without losing evidence references, add a focused contract value object or
extend the existing result mapping. Do not introduce a second AI port or a
parallel persistence model.

Wire the AI capability only behind the explicit feature flag. The disabled path
must remain the default and all deterministic capabilities must continue when the
adapter is unavailable or fails.

## 5. Configuration and Runtime Documentation

Add only the required settings, with disabled-safe defaults:

- AI Engine enabled flag.
- Internal base URL.
- Internal service authentication configuration.
- Connect/read and execution timeouts.
- Maximum context/evidence sizes.
- Provider name and model ID.
- OpenAI API key variable name, never its value.
- Provider max retries and token limit.
- Shadow mode flag.

Document local startup and internal trust assumptions. Add Docker Compose
service wiring only if required by the existing local-start convention and only
with no public port or Gateway route.

## 6. Tests

### Python tests

- health endpoint in disabled mode;
- strict input validation and size limits;
- missing, stale, invalid and contradictory evidence;
- valid structured mock response;
- unknown evidence reference rejection;
- invalid JSON and incomplete provider output;
- timeout, provider-unavailable and bounded retry behavior;
- disabled configuration does not invoke provider;
- cancellation and idempotent resubmission;
- no secret/prompt leakage in logs.

### Java tests

- request/response DTO mapping;
- serialized public context and thesis snapshot;
- submit/progress/result/cancel HTTP behavior;
- timeout, invalid response and provider-unavailable mapping;
- idempotence and correlation headers;
- disabled adapter selection and feature flag behavior;
- AI finding mapping without Market Thesis/Risk/Execution mutation;
- deterministic scanner non-regression when AI is unavailable or fails.

### Contract tests

Use shared JSON fixtures or an equivalent checked-in contract representation to
verify field names, enum values, timestamps, evidence references and failure
states between Python and Java without a running external provider.

## 7. Validation Commands

Run after implementation:

- Python project installation and `pytest` offline.
- Python FastAPI contract/API tests.
- Market Intelligence targeted Java tests for the adapter, capability,
  orchestration, disabled mode and non-regression.
- `cd market-intelligence && ../trading-core/mvnw test` when affected scope
  justifies the full module suite.
- `git diff --check`.
- `docker compose config` if Compose is changed.
- Repository quality workflow or applicable service checks.

No validation command may require OpenAI, Kraken, cTrader, Broker Service,
credentials or external network access.

## 8. Documentation Reconciliation

Update only canonical documentation affected by the implementation, likely:

- `README.md` service/status table if `ai-engine` becomes runnable;
- `docker-compose.yml` only if service wiring is added;
- Story-local implementation report with the exact local and authorized-runtime
  procedures.

Do not modify ADRs unless implementation reveals a material contradiction. Do
not modify the Obsidian vault.

## 9. Deliverables

- Python `ai-engine` service and tests.
- Java adapter and focused tests.
- Contract fixtures/tests.
- Disabled/shadow configuration.
- Required runtime documentation.
- Story-local `implementation-report.md`.
- Independent code review and final validation report.

## 10. Stop Conditions

Stop and request guidance if:

- the existing Java contracts cannot carry the required interpretation without
  changing authoritative semantics;
- implementing the HTTP adapter requires bypassing ADR-021 asynchronous
  execution governance;
- context classification cannot prove that only authorized data is transmitted;
- the provider contract requires secrets or private account data;
- Python service deployment requires a new architectural decision or public
  exposure;
- real LLM validation is requested without explicit model, data, call and cost
  authorization;
- implementation would modify Risk, Execution, Broker Service, cTrader or
  deterministic Market Thesis behavior.
