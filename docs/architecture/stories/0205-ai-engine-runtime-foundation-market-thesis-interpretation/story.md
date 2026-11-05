# Story 0205 - AI Engine Runtime Foundation and Market Thesis Interpretation

## Metadata

**ID:** `0205`
**Title:** AI Engine Runtime Foundation and Market Thesis Interpretation
**Priority:** `P0 - Trading Intelligence`
**Approach:** Implementation-first, reuse existing contracts, production-oriented MVP.

## Mission

Implement the first operational AI Engine for Trading OS.

The engine receives deterministic context from Market Intelligence, interprets a
Market Thesis using an LLM, and returns a structured, explainable and validated
analysis.

The service is developed in Python, FastAPI and Pydantic. It integrates with the
existing Java architecture without becoming a business source of truth.

The first use case is Market Thesis Interpretation.

Do not build an agent framework, RAG system, multi-agent system or autonomous
trading engine.

## Repository and ADR Review

Before implementation:

- Inspect Git status, branch and HEAD.
- Verify that Story 0204 is merged.
- Verify availability of Story number 0205.
- Read repository `AGENTS.md`.
- Read ADR-020, ADR-021, ADR-034 and ADR-076.
- Read Story 0204 documents and validated criteria.
- Examine existing microservice architecture and conventions.

Examine and reuse where appropriate:

- `AiEnginePort`
- `AiAnalysisCommand`
- `AiExecutionReference`
- `AiExecutionProgress`
- `AiStructuredResult`
- `AiAnalysisCapability`
- `DisabledAiEngineAdapter`
- `DisabledAiAnalysisCapability`
- `ProductionIntelligencePipeline`
- `MarketThesis`
- `MarketThesisComposer`
- `IntelligenceContext`

Do not invent a second AI architecture or violate existing responsibility
boundaries.

## Scope

### AI Engine Service Foundation

Create an autonomous Python service following repository conventions. The
service uses Python, FastAPI, Pydantic, HTTP/JSON, a configurable LLM client and
pytest. Keep the structure simple and do not introduce LangGraph, Celery, Redis,
Kafka or a dedicated database.

Possible structure:

```text
ai-engine/
  app/
    main.py
    api/
    domain/
    application/
    infrastructure/
    config/
  tests/
  pyproject.toml
  Dockerfile
```

Adapt this structure to repository conventions.

Provide `GET /health` and an internal Market Thesis analysis contract aligned
with `AiEnginePort` and existing asynchronous execution mechanisms. Do not add
an arbitrary synchronous API that bypasses ADR-020/021 guarantees.

### Market Thesis Analysis Contract

Define Pydantic contracts containing only the information required for
interpretation:

- Analysis ID.
- Market ID.
- Timestamp and cutoff.
- Timeframes.
- Market Thesis.
- Trend Context.
- Market Structure.
- Momentum.
- Tradeability.
- Supporting, contradicting and missing evidence.
- Invalidation conditions.
- Provenance.
- Rule versions.
- Freshness and completeness indicators.

Do not invent absent values or convert missing values into neutral values.
Preserve the distinction between absent, unknown, invalid, contradictory and
available data. Python DTOs must remain compatible with Java contracts and must
be covered by contract tests.

### LLM Provider Abstraction

Introduce a minimal provider abstraction:

```text
MarketThesisInterpreter
        -> LLMProvider
        -> Concrete Provider Adapter
```

Implement one real provider for V1, preferably OpenAI unless repository
configuration indicates otherwise. Configure the API key through an environment
variable and support configurable model ID, bounded timeout, limited retries,
token limits, explicit errors, secret-free logging, disabled mode by default and
a mock provider for tests.

Do not implement a multi-provider catalogue or expose keys to the frontend.
Never include broker credentials in prompts.

### Market Thesis Interpretation

Implement `MarketThesisInterpretationService` to:

1. Receive validated context.
2. Verify required data availability.
3. Build a controlled LLM input.
4. Request a structured interpretation.
5. Validate the response.
6. Return the result through the defined contract.

The AI output must contain:

- Market summary.
- Primary scenario.
- Supporting arguments.
- Contradictions and risks.
- Missing information.
- Confirmation conditions.
- Invalidation conditions.
- Uncertainty and limitations.
- A consultative suggested decision.

Suggested decisions may include `WAIT`, `MONITOR`, `REVIEW_CANDIDATE` and
`INSUFFICIENT_DATA`, subject to verification against existing enums.

The AI must not produce `BUY` or `SELL` decisions authorizing orders, invent
entry prices, stop-losses or take-profits, or convert ranking into a success
probability.

### Structured Output and Validation

Use Pydantic for typed, serializable and traceable responses. Each analytical
argument must reference supplied evidence where applicable. Unknown evidence
references must be rejected. Deterministic contradictions and missing
information must remain visible. Invalid results must not be published as valid
analysis; provide an explicit failure state.

### Java Integration

Implement the Java adapter connecting Market Intelligence to the Python service,
respecting `AiEnginePort`, existing orchestration, idempotence, execution
references and states, cancellation, time limits, context classification and
ADRs 020/021.

Use an HTTP client compatible with existing Spring conventions. Angular must not
communicate directly with Python, and Python must not access Java repositories.
Java remains responsible for business persistence.

AI errors must not make the deterministic scanner unavailable. Disabled or
unavailable AI must allow the deterministic pipeline to continue.

### Shadow Mode

The first AI Engine runs only in shadow mode. It may interpret Market Thesis,
produce summaries and diagnostics, identify contradictions and propose a reading
of context.

It must not modify canonical Market Thesis, deterministic observations,
opportunity scores, Trade Plans or risk rules; authorize orders; trigger
execution; or modify broker data.

Provide an explicit feature flag. The real engine remains disabled by default.

### Security and Privacy

The AI service receives only data needed for analysis. Enforce strict request
validation, context size limits, bounded timeouts, controlled errors, secret
protection and prompt logging policies that exclude sensitive content.

Do not expose an unprotected public AI endpoint. Document the internal trust
model instead of assuming Docker network isolation is sufficient. External data
instructions are untrusted content and never system instructions.

### Observability

Provide useful logs and metrics for analysis ID, market ID, model ID, provider,
duration, status, error type, token usage when available and reliable cost
estimates when possible. Never log API keys and preserve Java/Python execution
correlation.

Do not add a complete observability stack if existing tools are sufficient.

## Out of Scope

- Agentic framework, RAG, multi-agent orchestration or autonomous trading.
- Direct frontend-to-Python communication.
- Python access to Java repositories or business persistence.
- Modification of deterministic Market Thesis, risk, execution, broker or cTrader behavior.
- BUY/SELL authorization, order creation or execution triggering.
- Broker private calls, Kraken private calls, cTrader calls or orders.
- Real LLM calls during automated validation.
- Angular implementation for the next Story.

## Testing

### Python

Add pytest coverage for:

- Input validation.
- Missing data.
- Contradictory context.
- Valid structured responses.
- Invalid JSON.
- Unknown evidence references.
- Provider timeout.
- Provider unavailability.
- Incomplete responses.
- Disabled configuration.

### Java

Add tests for:

- HTTP adapter.
- DTO mapping.
- Idempotence.
- Timeout and error handling.
- Disabled engine.
- Scanner non-regression.
- Risk and Execution isolation.

### Contract and Offline Tests

Verify Java/Python contract compatibility. All automated tests must run without
OpenAI keys, Kraken, cTrader, Broker Service, orders or external network access.

## Runtime Validation

Prepare a minimal E2E scenario using an existing Market Thesis or representative
fixture. It must demonstrate:

1. Market Intelligence provides the context.
2. Java sends only authorized context.
3. Python validates the input.
4. The provider returns a response.
5. Pydantic validates the structure.
6. Java receives the result.
7. The result is recorded according to existing contracts.
8. Market Thesis and deterministic decisions remain unchanged.

Do not make a real LLM call automatically. Before any real OpenAI call, request
human authorization specifying model, transmitted data, maximum call count,
maximum estimated budget and expected result. No broker private call or order is
allowed.

## Documentation

Create the usual Story artifacts and document:

- Python service architecture.
- Java/Python contracts.
- LLM provider.
- Environment variables.
- Disabled and shadow modes.
- Error handling.
- Data policy.
- Local launch procedure.
- E2E validation procedure.
- Known limitations.

Do not introduce an ADR unless an existing decision is contradicted. Stop and
request human validation if implementation conflicts with existing ADRs.

## Acceptance Criteria

- The Python/FastAPI service starts locally.
- One configurable real LLM provider is implemented.
- Disabled mode is the default behavior.
- A Market Thesis can be interpreted through existing contracts.
- The result is strictly structured and validated.
- Evidence references are verified.
- Java and Python communicate through tested contracts.
- AI errors do not interrupt the deterministic pipeline.
- No Risk or Execution component is modified.
- No order is created.
- Automated tests pass offline.
- Real LLM validation remains subject to human authorization.

## Implementation Report

The final report must cover:

1. Repository state.
2. Existing architecture reused.
3. Python service implementation.
4. Java integration.
5. LLM provider adapter.
6. Structured output validation.
7. Security controls.
8. Shadow mode.
9. Tests executed.
10. Remaining limitations.
11. Exact procedure for the first authorized real LLM test.
12. Readiness for Angular integration.

Do not start the next Angular Story before this foundation is validated.

Do not commit, push or merge without explicit authorization.
