# Implementation Report - Story 0210

## Delivered

- Added provider registry construction behind the existing Python `LLMProvider`
  port.
- Preserved `mock` as the default and retained the existing API-key `openai`
  adapter.
- Added explicit `openai_chatgpt` provider selection, model, timeout, output
  limit, credential-reference and opt-in fallback configuration.
- Added encrypted SQLite credential storage with schema upgrade handling.
- Added bounded ChatGPT OAuth start/callback services using PKCE, state, nonce,
  ID-token issuer/audience/signature validation and refresh-before-call.
- Added internal OAuth start/callback routes. They are not routed through
  Gateway or exposed by MCP.
- Added strict ChatGPT Responses API streaming, JSON Schema output, scope
  checks, incomplete-response handling and bounded provider diagnostics.
- Added offline provider, credential, OAuth, fallback and structured-output
  tests.
- Updated the root runtime documentation with non-secret configuration guidance.

## Boundary Verification

- Trading Research Agent and Market Intelligence remain provider-neutral.
- Java `AiEnginePort`, adaptive capability allowlist, one-follow-up budget,
  research lifecycle, persistence and MCP contracts were not changed.
- OAuth tokens remain inside AI Engine credential storage and are not returned by
  OAuth routes, sent through MCP, included in reports or logged.
- No Risk, Broker, Execution, position, Strategy or canonical Market Thesis
  authority was changed.

## Validation

- `cd ai-engine && .venv/bin/pytest`: **16 passed**
- `cd market-intelligence && ../trading-core/mvnw -q -Dtest=ResearchApplicationServiceTest,HttpAiEngineAdapterTest test`: **passed**
- `cd market-intelligence && ../trading-core/mvnw test`: **853 passed**
- `git diff --check`: **passed**

## Live Provider Validation

Executed locally with explicit operator authorization through the ChatGPT OAuth
consent flow. ChatGPT displayed and authorized the application name `Trading OS`.
No access or refresh token was logged or included in this report.

Successful bounded run:

- Provider: `openai_chatgpt`
- Model: `gpt-5.6-luna`
- Research input: deterministic fixture request with one evidence item
- Provider call budget: one call
- Local maximum duration: 120 seconds
- Observed elapsed time: approximately 10,068 ms
- Result: `COMPLETED`
- Structured result: present and accepted by Pydantic and evidence validation
- Orders, broker calls and live market-data calls: none

Additional bounded diagnostics during setup were truthful and fail-closed:

- `gpt-5.1-mini`: rejected by the provider as `model_not_found`.
- Raw Pydantic schema: rejected by the provider as `invalid_json_schema`; the
  adapter now uses strict schema normalization.
- A provider response with an unknown evidence reference: rejected locally as
  `InvalidInterpretation`.
- A five-second fixture budget: rejected locally as `TimeoutError`.

This validates provider connectivity and the structured research contract. It
does not claim current market knowledge, predictive accuracy or profitability;
the successful request used deterministic fixture evidence rather than live
market conditions.

## Limitations

- The first implementation is operator-scoped through an AI Engine credential
  handle. Trading OS does not yet have a Core-owned multi-user AI configuration
  and task-binding model equivalent to DevLog AI.
- OAuth transaction and credential stores are local AI Engine SQLite stores;
  in-flight AI execution remains an in-process background task and can be lost
  across restart.
- The live callback requires the configured provider OAuth client and accepted
  scopes in the target environment. One authorized account/model path was
  validated; revocation, refresh failure and broader model access remain
  unvalidated.
- Usage accounting is not added to the existing Java research report contract;
  provider execution exposes no raw usage payload when unavailable.

## Documentation Outcome

`README.md` was updated with the non-secret provider and OAuth operational
configuration. No ADR was added because the implementation preserves ADR-088 and
ADR-089 responsibility boundaries.

## Vault Outcome

The Obsidian vault was not consulted. This Story was fully grounded in the
repository, accepted ADRs, predecessor Stories and the local DevLog AI source;
no vault action is proposed.
