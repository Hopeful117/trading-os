# Story 0210 - Provider-Agnostic LLM Runtime & Real Trading Research Validation

## Metadata

**ID:** `0210`
**Title:** Provider-Agnostic LLM Runtime & Real Trading Research Validation
**Status:** Implementation completed - awaiting final human review
**Priority:** P1 - Trading Intelligence
**Predecessors:** `0205`, `0208`, `0209`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-028`, `ADR-034`, `ADR-076`, `ADR-088`, `ADR-089`

## Goal

Connect the existing Java-controlled Trading Research workflow to one real LLM
provider, ChatGPT through the established OAuth approach, without coupling
research business logic to ChatGPT, OpenAI SDK types or OAuth details. Validate
whether real model reasoning improves evidence-driven BTC-versus-ETH research
beyond the deterministic mock provider.

## Scope

- Introduce or complete a provider registry behind the existing Python
  `LLMProvider` port.
- Keep `mock` as the default and add one explicitly configured
  `openai_chatgpt` OAuth adapter.
- Adapt the DevLog AI OAuth, encrypted credential, refresh, structured-output,
  bounded-error and testing mechanisms without copying DevLog business logic.
- Add explicit configuration for active provider, model, authentication
  reference, timeout, output-token limit, research AI-call budget and optional
  fallback policy.
- Preserve the existing strict adaptive reasoning contract, Java capability
  allowlist, one-follow-up limit, asynchronous research lifecycle, report
  persistence and MCP tools.
- Add fixture-based BTC/USD versus ETH/USD research scenarios and deterministic
  evaluation checks.
- Add a separately identified, explicitly authorized live-provider validation
  procedure and report format.

## Out of Scope

- Additional providers, model catalog UI or frontend configuration.
- A general agent framework, recursive planning or increased adaptive budget.
- Direct MCP-to-AI Engine/provider calls.
- Broker credentials, orders, positions, Risk, TradePlan or execution changes.
- AI mutation of Market Thesis, Observation, Opportunity or authoritative facts.
- Provider-specific branches in Market Intelligence or research domain code.
- Core user configuration, multi-user credential association and MCP human
  delegation unless a separate approved decision authorizes them.
- Live provider calls in CI or claims of predictive accuracy/profitability.
- Shared code or runtime dependency on DevLog AI.

## Architecture

```text
Research Application / Agent
          |
          v
AI application service and strict contracts
          |
          v
LLMProvider port + provider registry
          |
          +--> Mock adapter
          +--> ChatGPT OAuth adapter
                    |
                    +--> OAuth service and encrypted credential store
                    +--> ChatGPT Responses transport
```

The Research Agent knows only provider-neutral contracts. Java remains the
authority for context, capability requests, budgets and persistence. The AI
Engine owns provider execution and receives only authorized context. OAuth
credentials remain inside AI Engine infrastructure.

## Acceptance Criteria

- [x] Provider construction is registry-based and the Research Agent has no
  direct ChatGPT, OpenAI SDK or OAuth dependency.
- [x] `mock` remains the offline/default provider; enabling ChatGPT requires
  explicit provider, model and authentication configuration.
- [x] ChatGPT OAuth uses PKCE/state/nonce, encrypted credential storage,
  refresh-before-call, explicit expiry/reauthorization failures and no token
  leakage in logs, prompts, reports or MCP responses.
- [x] Provider/model/timeout/output-token/research-budget/fallback settings are
  bounded, validated and not hardcoded in research business logic.
- [x] Structured adaptive outputs remain strict and validate evidence
  references, hypotheses, contradictions, missing information, invalidation
  conditions and one authorized follow-up request.
- [x] Java continues to authorize and execute follow-up capabilities; an LLM
  cannot invoke arbitrary endpoints or tools.
- [x] Existing asynchronous research status/report lifecycle and MCP contracts
  remain compatible.
- [x] Fixture BTC-versus-ETH validation demonstrates schema compliance, valid
  evidence references, contradiction and missing-data handling, relevant
  follow-up selection, budget compliance and evidence-traceable conclusions.
- [x] Fixture results distinguish historical/fixture evidence from live market
  facts and make no profitability or predictive-accuracy claim.
- [x] A live-validation procedure records provider, model, request count,
  latency, usage when available, budget and limitations, or explicitly records
  the external-validation blocker.
- [x] Offline provider-independent, OAuth adapter and research integration tests
  pass without external credentials or network access.
- [x] No Risk, Execution, Broker, position, strategy-promotion or canonical
  Market Thesis authority is delegated to the LLM.

## Human Decisions Required

- Approve the Repository Analysis and the operator-managed credential-reference
  assumption for the first vertical slice.
- Approve the exact OAuth client/scopes and secret-injection configuration for
  the target validation environment.
- Separately authorize any live provider test with model, context, maximum
  request count and external quota/spending budget.

## Validation Expectations

Use targeted Python, Market Intelligence, Gateway and MCP tests plus
`git diff --check`. Live validation is never part of automated CI and must not
be reported unless actually executed.

## Delivery Constraint

Do not implement, commit, push or merge before the Repository Analysis and then
the Implementation Plan receive explicit human approval at their respective
workflow gates.
