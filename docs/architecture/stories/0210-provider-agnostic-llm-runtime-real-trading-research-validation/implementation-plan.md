# Implementation Plan - Story 0210

## Plan Boundary

This plan adds one real provider behind the existing AI Engine boundary and
validates the existing adaptive research workflow. It does not create another
orchestrator, provider family, MCP architecture or Core identity system.

The plan is conditional on human approval of the Story 0210 Repository Analysis
and the operator-managed AI Engine credential-reference assumption.

## Phase 1 - Contract and Configuration

1. Preserve the existing `LLMProvider` port and strict Pydantic research models.
2. Add a provider-neutral configuration model with validated fields for:
   - active provider;
   - active model;
   - authentication configuration reference/credential handle;
   - timeout;
   - maximum output tokens;
   - maximum research AI calls and adaptive budget;
   - explicit fallback policy, defaulting to no fallback.
3. Replace `main.py` provider branching with a registry/factory. Unknown or
   disabled providers fail closed; mock remains the default.
4. Keep provider/model metadata and usage in provider-neutral execution
   diagnostics without storing raw prompts or provider payloads.

## Phase 2 - ChatGPT OAuth Infrastructure

1. Adapt the DevLog AI `ChatGptOAuthService` pattern into an isolated Trading
   OS infrastructure package.
2. Implement short-lived single-use OAuth transactions with PKCE S256, state,
   nonce, redirect URI and issuer/audience/signing-key validation.
3. Store OAuth credentials only in an encrypted AI Engine SQLite store using a
   deployment-provided key and opaque handle. Do not add secrets to source,
   Java reports, MCP or logs.
4. Resolve the configured credential reference at provider-call time. Refresh
   an expired access token once; classify missing/invalid refresh material as
   explicit reauthorization required.
5. Keep OAuth bootstrap/callback operations internal and operator-managed. Do
   not add a frontend or infer a human delegation identity.

## Phase 3 - Provider Adapter

1. Add the ChatGPT adapter behind `LLMProvider`, without leaking provider types
   into `app.application` or `app.domain`.
2. Call the configured Responses endpoint with `store=false`, bounded timeout,
   explicit model and strict JSON Schema output generated from the requested
   Pydantic response model.
3. Parse streamed terminal events and reject empty, failed or incomplete
   responses. Map HTTP, rate-limit, timeout, authentication and incomplete
   output failures to bounded provider-neutral errors.
4. Apply only bounded retry policy for explicitly retryable transport/status
   failures. Never retry OAuth authorization failures or unsafe work
   indefinitely.
5. Record latency and token usage when the provider supplies reliable values;
   never record access/refresh tokens or raw provider payloads.

## Phase 4 - Research Integration

1. Reuse the existing `InterpretationRequest`, `InterpretationDraft`,
   `RequestedAnalysis`, `AiEnginePort`, `HttpAiAnalysisCapability` and Java
   `ResearchApplicationService` contracts.
2. Change only provider-neutral DTO/configuration fields needed for model,
   budget and usage provenance. Do not add ChatGPT branches to Java research
   code.
3. Confirm that the Java allowlist remains the only authority for follow-up
   capability execution and that one adaptive iteration remains enforced.
4. Preserve disabled, timeout, malformed, partial and reauthorization states in
   research reports. A provider failure must not make deterministic evidence
   unavailable.
5. Preserve actor ownership, idempotency, status polling, report persistence
   and the existing MCP lifecycle tools.

## Phase 5 - Fixture Evaluation

Create a reproducible fixture scenario using explicit canonical market IDs from
the existing test/application fixture conventions, expected to represent
BTC/USD and ETH/USD. The scenario must include deterministic supporting,
contradicting, missing and stale evidence where the fixtures support them.

Run two modes:

- Mock baseline: deterministic provider output through the complete research
  workflow.
- Provider-shaped fixture: a recorded/fake ChatGPT-shaped structured response
  through the same adapter and validation path, with no network call.

The evaluation report/checks must deterministically verify:

- schema and field bounds;
- every evidence reference exists and is in scope;
- no unexecuted capability is presented as executed;
- requested capability is allowlisted, relevant to the selected market and
  within the one-follow-up budget;
- stale/missing/contradictory evidence remains visible;
- conclusions reference supplied evidence and limitations;
- provider/model, request count, latency/usage fields are traceable when
  available.

Subjective usefulness is recorded as reviewer observations, not collapsed into
a single quality or profitability score.

## Phase 6 - Explicit Live Validation

Only after separate human authorization, run one bounded BTC-versus-ETH research
request with:

- approved OAuth account and model;
- approved exact context class and source cutoff;
- maximum request count equal to the research budget;
- explicit timeout and quota/spending ceiling;
- no broker/private account data and no execution capability.

Capture only sanitized run metadata: provider, model, request count, start/end
time, latency, usage if returned, terminal status and validation findings. Do
not persist tokens, full prompts, raw provider payloads or unsupported claims.

If credentials, scopes, model access or budget authorization are unavailable,
record the blocker instead of simulating a live result.

## Test Plan

### Provider-Independent Python

- registry defaults to mock and selects configured model/provider;
- unknown provider, missing required configuration and disabled provider fail
  closed;
- mock and ChatGPT-shaped providers satisfy the same port;
- strict structured output and evidence-reference validation;
- invalid evidence, invalid capability request, malformed output and output
  overflow;
- explicit timeout, authentication, rate-limit, incomplete and provider-error
  mapping;
- no fallback unless configured explicitly.

### ChatGPT OAuth Adapter

- PKCE/state/nonce transaction creation and single-use callback;
- issuer, audience, nonce and scope validation;
- encrypted credential storage and opaque-handle resolution;
- expired credential refresh and reauthorization failure;
- provider model/output-token selection;
- strict streamed response parsing, failed/incomplete events and bounded
  diagnostics;
- no token values in logs, exceptions, reports or request fixtures.

### Research Integration

- existing async research lifecycle and report persistence;
- mock/provider-shaped successful, partial, timeout and failed research;
- one adaptive request and Java allowlist enforcement;
- invalid evidence/capability requests rejected without execution;
- actor ownership, idempotency, MCP compatibility and no execution authority;
- BTC-versus-ETH fixture evaluation and truthful fixture/live distinction.

### Commands

```text
cd ai-engine && .venv/bin/pytest
cd market-intelligence && ../trading-core/mvnw test
cd gateway && ../trading-core/mvnw test
cd mcp-adapter && .venv/bin/pytest
git diff --check
```

The full repository scripts remain applicable if the changed modules and local
environment support them. Live provider validation is excluded from CI.

## Documentation Reconciliation

During implementation, update only canonical AI Engine configuration/runtime
documentation and MCP/API documentation if contracts or operational setup
change. Add the exact OAuth secret-injection and live-validation procedure
without including secret values. No new ADR is expected unless the credential
or identity boundary materially changes.

## Risks and Limitations

- Without a Trading OS Core credential-binding model, the first provider runtime
  is operator-scoped rather than user-configurable. This must not be presented
  as a multi-user OAuth solution.
- ChatGPT OAuth client registration and scopes may differ by environment and
  require explicit provider-approved configuration.
- In-process AI Engine tasks can be lost on restart; this Story does not make
  execution durable.
- A real LLM can produce plausible but unsupported conclusions. Deterministic
  evidence validation and Java capability authorization remain mandatory.
- Fixture evaluation demonstrates contract and reasoning workflow behavior, not
  market prediction, trading profitability or current market conditions.

## Approval Gate

This plan is a proposed implementation plan. It must receive explicit human
approval before any implementation file is modified.
