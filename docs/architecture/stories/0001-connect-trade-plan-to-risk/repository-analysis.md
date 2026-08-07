# Repository Analysis

## Story Understanding

Story 0001 asks for the first safe application flow between an immutable, accepted Market Intelligence `TradePlan` version and the deterministic Risk Domain. A caller must be able to request evaluation of one exact plan, have the platform assemble the current account, portfolio, market and effective-rule context, execute deterministic pre-trade evaluation, and receive an explainable structured result traceable to that plan.

The flow ends at risk evaluation. It must not create an `ExecutionIntent`, call Broker Service, place an order, add a microservice, change risk algorithms, or implement frontend or human-approval behavior. Repeating an evaluation may evaluate again or return an existing evaluation according to the later application contract, but it must have no execution side effect.

## Repository Summary

Trading OS is a multi-service repository without a root Maven aggregator. Market Intelligence owns observations, opportunities and versioned Trade Plans. Trading Core is the business-orchestration service and owns accounts, risk rules, dashboard aggregation and the separate Execution Domain. `risk-domain` is an independent, framework-free Java 21 module containing the ADR-028 deterministic engine.

The required building blocks exist, but the cross-module application flow does not. No existing controller, service or client currently submits a Market Intelligence Trade Plan to `risk-domain`.

## Affected Modules

### `trading-core` — owning application service

Trading Core must own the orchestration because ADR-005 assigns deterministic business validation and cross-service orchestration to it, while ADR-014 places risk validation after Market Intelligence and before human validation. It already owns account/rule state, consumes Market Data and Broker services through ports/Feign clients, and is the appropriate location for the public application API and context assembly.

This Story must remain separate from the existing `execution` package. Its risk-evaluation flow must not call `CreateExecutionIntentService`, `ExecuteTradeService`, `BrokerExecutionPort`, or any execution pipeline step.

### `market-intelligence` — Trade Plan source

Market Intelligence owns `TradePlan`, its exact immutable version, lifecycle and repository. It must expose an appropriate read boundary for Trading Core to obtain one exact accepted snapshot together with the stable data needed to map it. It must not orchestrate account loading, deterministic risk evaluation or broker access.

### `risk-domain` — deterministic decision authority

`risk-domain` owns `RiskEvaluationRequest`, `ProposedTrade`, immutable snapshots, `RiskEvaluationContextBuilder`, `RiskEngine`, `DeterministicRiskEngine`, rule evaluation and `RiskValidationResult`. Its pure-domain architecture should remain unchanged except for a narrowly justified domain correction discovered during planning. Infrastructure and repository access must stay outside this module.

## Existing Implementation

### Trade Plan model and lifecycle

- `market-intelligence/.../domain/tradeplan/TradePlan` is immutable and identifies an exact `TradePlanId` and `TradePlanVersion`.
- `ExecutionParameters` contains broker-neutral instrument, direction, entry, stop, targets and `PositionSizing`. `PositionSizing` already supplies quantity, notional, expected monetary risk and currency, which map closely to `risk-domain`'s `ProposedTrade`.
- `TradePlanStatus` includes `ACCEPTED`, `RISK_VALIDATED` and `READY_TO_EXECUTE`.
- `TradePlanRiskValidationBoundary` and `DefaultTradePlanIntegrationBoundary` already model loading an exact accepted snapshot and recording a `RISK_VALIDATED` transition. They are in-process boundaries only; no cross-service endpoint or client uses them.
- `TradePlanRepository` supports exact-version, latest-version and history queries, but its configured implementation is currently in memory.
- `TradePlanController` exposes create, latest, versions and replan operations using `ResponseEntity`; it does not expose acceptance or risk evaluation.
- A `TradePlan` stores a `TradingContextReference`, while the referenced `TradingContext` contains owner, trading account, currency, capital, leverage and risk/rule profile data. Ownership is not directly carried by the `TradePlan` response.

### Risk Domain

- `RiskEvaluationRequest` supports `PRE_TRADE`, requires a `ProposedTrade`, and carries evaluation and correlation identifiers.
- `ProposedTrade` explicitly identifies the Trade Plan ID and version and holds instrument, direction, quantity, notional, expected loss at stop and required margin.
- `RiskEvaluationContextBuilder` requires coherent `TradingContext`, `AccountSnapshot`, `PortfolioSnapshot`, `MarketSnapshot` and `RuleSetSnapshot` inputs. It rejects inconsistent accounts/currencies and missing market prices.
- `DeterministicRiskEngine` is stateless, evaluates all applicable rules in stable order and returns `RiskValidationResult` with execution status, decision, violations, warnings, metrics and trace metadata.
- Completed decisions are `APPROVED`, `APPROVED_WITH_WARNINGS` or `REJECTED`; incomplete or failed evaluation has no authorization decision and must fail closed.
- `RiskEvaluationRecord` and `RiskEvaluationAuditPort` define an immutable audit artifact and persistence boundary, but no Trading Core adapter currently persists evaluations.
- `risk-domain` is not currently declared as a dependency of Trading Core or any other service. Each module builds independently.

### Trading Core context sources

- The legacy `trading_core.service.RiskEngine`/`RiskEngineImpl` implements older trade and dashboard checks. It is not ADR-028's `com.hope.trading.risk.engine.RiskEngine` and cannot be treated as the authority for this Story.
- `Account`, `Rules`, account balances, trades, broker-account integration and Dashboard aggregation provide portions of account, rule and portfolio state.
- `DashboardQueryService` already demonstrates orchestration across persisted accounts, Broker Service positions/balances and Market Data prices, including degraded dependency handling. Those dashboard projections are presentation-specific and are not ADR-028 snapshots.
- Existing Feign conventions (`MarketDataClient`, broker clients) provide a precedent for inter-service adapters.
- No mapper currently constructs ADR-028 `AccountSnapshot`, `PortfolioSnapshot`, `MarketSnapshot`, `TradingContext` or `RuleSetSnapshot` from Trading Core sources.
- No risk-evaluation API, evaluation repository/entity, Trade Plan client or structured response DTO currently exists in Trading Core.

### Persistence and APIs

The Story affects no existing database entity directly yet. A durable risk-evaluation record is required by ADR-028's auditability objective, but its persistence representation and retention/idempotency contract are not implemented. Any public controller added by the Story must return `ResponseEntity` and enforce account/plan ownership through the authenticated user rather than trusting caller-provided ownership.

## Relevant Documentation

- `AGENTS.md`
- Root `README.md`
- Story `docs/stories/0001-connect-trade-plan-to-risk/story.md`
- ADR-003 — AI-First Architecture
- ADR-004 — Market Intelligence Architecture
- ADR-005 — Trading Core Responsibilities
- ADR-009 — Risk Rules
- ADR-014 — Trading Decision Pipeline
- ADR-020 — Market Intelligence Foundation
- ADR-022 — Artifact Memory
- ADR-023 — Capabilities and DAG Planning
- ADR-026 — Opportunities
- ADR-027 — Trade Planning Model
- ADR-028 — Risk Engine Architecture
- ADR-029 — Execution Domain Architecture
- `risk-domain/README.md`
- Engineering Story workflow and Repository Analysis prompt

The workflow documents named `docs/workflow/ai-workflow.md`, `docs/workflow/ai-roles.md`, `docs/workflow/story-template.md` and `docs/workflow/prompts/common-principles.md` do not exist at the current repository revision. The repository instead contains `docs/stories/story-template.md`.

## Constraints

- Trading Core owns orchestration and deterministic business validation.
- Market Intelligence remains the source of truth for exact Trade Plan snapshots and lifecycle.
- `risk-domain` remains the sole authority for the decision and must stay stateless, deterministic, read-only, framework-free and infrastructure-free.
- The evaluated input must be a coherent immutable snapshot and must fail closed when required information is missing or inconsistent.
- Evaluation must preserve exact Trade Plan ID/version and account, portfolio, market, rule-set, policy, rule and engine-version traceability.
- A changed Trade Plan version requires a new risk evaluation; validation of one version cannot authorize another.
- Rejections must expose structured rule reasons, not only a boolean or message.
- Market Intelligence must not access Broker Service or create execution artifacts.
- No Execution Intent, execution attempt, broker request or order may be created.
- Public contracts must be broker-neutral and independent from Kraken.
- Controllers must return `ResponseEntity`.
- Tests must be deterministic and network-free; each touched Maven module must be validated independently.
- Existing ADRs, current user changes and module boundaries must be preserved; no commit, push or merge is allowed.

## Risks

- **Competing risk implementations:** Trading Core's legacy `RiskEngine` has the same conceptual name but different contracts and rules. Accidentally adapting it instead of ADR-028 would violate the Story.
- **Build integration:** `risk-domain` is standalone and Trading Core has no current dependency on it. The repository has no aggregator, so dependency resolution and repeatable module build order need an explicit solution during planning without duplicating domain classes.
- **Snapshot coherence:** account, broker portfolio, market prices and effective rule configuration come from different sources and timestamps. Reusing Dashboard DTOs would not automatically satisfy ADR-028 coherence and traceability.
- **Incomplete source data:** required ADR-028 values such as margin required, daily-start balance, portfolio versions and effective policy/rule versions are not all represented directly by the Trade Plan or current Trading Core account model.
- **Ownership/security:** the Trade Plan references a trading context, while account ownership resides elsewhere. Cross-service retrieval must prevent evaluating another user's plan or account.
- **Lifecycle ambiguity:** `recordRiskValidated` exists, but ADR-028 distinguishes approved, rejected, incomplete and failed outcomes. Only an approved completed result may justify `RISK_VALIDATED`; repeat behavior and concurrent version changes must not produce invalid transitions.
- **Audit durability:** an in-memory result would satisfy immediate response behavior but not ADR-028's long-term audit/replay objective. Adding persistence expands mapping and migration work.
- **Unavailable dependencies:** failure to load any mandatory snapshot must produce a controlled fail-closed response, not a false business rejection or accidental approval.

## Open Questions

1. What should be the stable idempotency/retrieval contract for repeated evaluation of the same exact Trade Plan version: return the existing official evaluation, or create a new evaluation artifact with no side effects? The Story forbids execution side effects but does not define evaluation identity semantics.
2. Must Story 0001 persist the complete `RiskEvaluationRecord` durably in Trading Core, or is an application repository port with an in-memory adapter acceptable for this increment? ADR-028 requires auditability and replay, while the acceptance criteria require traceability but do not explicitly state durability.
3. Which current source is authoritative for the complete portfolio snapshot: Trading Core persisted trades, Broker Service positions, or a reconciled combination? Dashboard currently favors broker facts, but that is not an accepted risk-context contract.
4. How should `marginRequired` be derived for `ProposedTrade`? `TradePlan.PositionSizing` provides notional and expected risk but no explicit required margin.
5. Which endpoint is the intended external application API and authentication context for evaluation? The repository conventions support a Trading Core API, but the URL and request identity are not specified.

These questions affect contract, persistence and context semantics. They can be resolved in an explicit Implementation Plan without changing the accepted ownership: Trading Core orchestrates, Market Intelligence supplies the accepted snapshot, and `risk-domain` decides.

## Recommendation

**Ready for planning**

The repository and accepted ADRs resolve business ownership and provide the essential domain models, but the Story crosses three modules and requires explicit decisions for contracts, dependency packaging, snapshot assembly, security, persistence and repeat-evaluation semantics. An Implementation Plan is required before implementation. The plan must resolve the open questions or surface any choice requiring architectural approval.

## Approval Required

Repository Analysis completed.

Awaiting human approval before Implementation Planning.
