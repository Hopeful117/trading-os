# Repository Analysis - Story 0210

## Scope

This analysis covers the current Trading OS `main` branch, Stories 0205, 0208
and 0209, ADR-088 and ADR-089, the AI Engine, Market Intelligence research
workflow, MCP boundary and the local DevLog AI implementation reference. It
does not authorize implementation.

## Repository State

- Branch: `main`
- HEAD: `d4a453aef2541a474df8c3255ba9570e179e8bf4`
- HEAD message: merged Story 0209 adaptive trading research
- Worktree: clean
- Story 0210 is not present; `0210` is the next available Story number.

## Current Trading OS Facts

- `ai-engine` is a Python 3.11+, FastAPI and Pydantic service. It is disabled
  by default and only supports shadow mode.
- `app.application.ports.LLMProvider` is the provider port, but it currently
  exposes only synchronous `interpret` and provider/model attributes.
- `main.py` selects `MockLLMProvider` by default and directly branches to
  `OpenAILLMProvider` when `AI_ENGINE_ENABLED=true` and provider is `openai`.
  There is no registry, explicit provider configuration object, OAuth provider,
  credential store or authentication lifecycle.
- The existing OpenAI adapter requires `AI_ENGINE_API_KEY`, uses the OpenAI
  Python SDK, requests JSON object output and validates the resulting
  `InterpretationDraft`. It is an API-key adapter, not a ChatGPT OAuth adapter.
- Pydantic models use `extra="forbid"`, camel-case aliases and bounded list and
  string sizes. The current adaptive contract already contains hypotheses via
  thesis/scenario fields, missing information, contradictions, invalidation
  conditions and one optional `RequestedAnalysis`.
- `InterpretationService` verifies evidence references in supporting arguments
  and contradictions against supplied thesis evidence. Java remains the
  authority for capability allowlisting, market scope and follow-up execution.
- Market Intelligence owns `AiEnginePort`, Java HTTP adaptation, context
  classification, research persistence and report construction. `HttpAiAnalysisCapability`
  invokes the AI Engine through the port and bounds each AI call to one request
  and approximately 30 seconds.
- `ResearchApplicationService` already compares exactly two explicit canonical
  markets, persists a durable research execution/report, records requested,
  executed and rejected adaptive capabilities and enforces one follow-up
  request across the execution. The existing lifecycle and MCP tools must be
  extended, not replaced.
- The research report preserves deterministic analysis, thesis evidence,
  freshness, AI status, diagnostics and advisory limitations. It does not make
  an LLM authoritative and does not authorize Risk, TradePlan or Execution.
- MCP exposes the application research lifecycle through Gateway. It does not
  call the AI Engine, provider, repository or database directly.
- Offline tests exist for Python contracts/provider behavior and Java research
  behavior. There is no live-provider test in the standard validation suite.
- Current fixtures use explicit UUIDs and BTC/USD plus ETH/USD symbols in
  Market Intelligence tests. They are suitable for fixture-based comparison,
  but do not prove that those UUIDs are canonical in a running deployment.

## Architectural Reconciliation

ADR-089 requires Java-controlled orchestration and specialized reasoning in the
existing Python AI Engine. Java owns authorization, evidence assembly,
budgets, persistence, provenance and public contracts. Python may reason over
authorized context but must not access Trading OS services, receive user JWTs
or broker credentials, mutate canonical state or execute tools.

ADR-088 requires MCP to remain an anti-corruption layer over public Gateway
contracts. Story 0210 must not expose OAuth routes, provider calls, prompts or
internal AI Engine routes through MCP.

No new ADR is required if the implementation keeps these boundaries. An ADR is
required before implementation if OAuth provisioning introduces a new deployed
identity/delegation boundary, changes MCP authorization, or moves credential
ownership outside the AI Engine.

## Proposed Responsibility Boundaries

### Trading Research Agent and Application Contracts

Remain provider-neutral. They consume and produce the existing structured
research contracts and do not import ChatGPT, OpenAI SDK types, OAuth types or
provider identifiers beyond opaque configuration metadata.

### AI Engine Application Layer

Owns provider-independent reasoning orchestration, strict output validation,
evidence-reference checks, research budgets, provider-neutral failure
classification and traceable provider/model metadata.

### Provider Registry and Adapters

The registry selects one configured provider. The first adapter is ChatGPT OAuth
only. It owns OAuth token use, provider request payloads, model identifiers,
stream parsing, usage mapping, rate-limit classification and provider errors.
The mock adapter remains the offline default. No alternative provider is added
in this Story.

### OAuth and Credential Lifecycle

OAuth state, PKCE verifier, nonce, callback transactions and encrypted access/
refresh tokens remain inside AI Engine infrastructure. The application and MCP
layers see only provider selection, model configuration and bounded failure
categories. Credentials are never logged, persisted in reports, sent through
MCP or included in prompts.

The first implementation should use an operator-managed authentication
configuration reference, not a configuration UI. Any OAuth bootstrap/callback
surface must be internal and explicitly protected; the Story must not infer a
human user identity or create a new MCP delegation model.

### Market Intelligence Java

Remains the only authority for research scope, evidence, capability requests,
adaptive budgets, lifecycle and report persistence. It selects provider/model
through bounded configuration passed to the AI Engine, but does not branch on
ChatGPT or OAuth behavior.

## DevLog Context Check

The DevLog Story Agent was queried with a bounded Story 0210 request. The
request failed with `GROUNDING_REQUESTED_FILE_SCOPE` and was marked terminal,
so no DevLog context was relied upon as authoritative. The local
`/home/ludo/workspace/devlog-ai` repository was available and inspected as the
requested primary technical reference. Current Trading OS source and accepted
ADRs remain authoritative for this analysis.

## Smallest Valuable Vertical Slice

1. Replace direct provider construction in `main.py` with a provider registry
   behind the existing `LLMProvider` port.
2. Preserve the mock provider as the default and add explicit provider/model,
   timeout, output-token, research-budget and authentication-reference
   configuration.
3. Add a ChatGPT OAuth adapter using the DevLog pattern: PKCE/state/nonce,
   encrypted credential storage, refresh-before-call, strict Responses API
   structured output, bounded errors and no token leakage.
4. Keep the existing adaptive Pydantic contract and Java allowlist unchanged
   except for the minimum metadata needed to record provider/model/usage.
5. Add deterministic fixture evaluation for BTC/USD versus ETH/USD and a
   separately opt-in live validation procedure. The live procedure must record
   actual model, provider, request count, latency and usage without claiming
   predictive accuracy.

## Blocking Risks and Unknowns

- Trading OS has no existing Core-side AI configuration, credential association
  or task-scoped binding equivalent to DevLog AI. Implementing those broadly
  would exceed the smallest slice and may require a new architecture decision.
- The exact ChatGPT OAuth client registration, scopes and operator provisioning
  contract are environment-specific. They must be supplied through configuration
  and verified against the approved DevLog mechanism, not guessed.
- Current AI Engine execution is in-process and can lose an in-flight task on
  restart. Story 0210 must not silently promise durable provider execution.
- The current Java HTTP adapter uses an existing fixed request contract. Any
  provider/model selection field must be added provider-neutrally and bounded;
  OAuth details must not cross the Java/MCP boundary.
- Real provider validation requires explicit human approval of credentials,
  model, transmitted context, request count and spending/quota budget.

## Gate

Repository Analysis is complete. Human approval is required before this plan is
treated as authorized implementation scope. No application code has been
changed.
