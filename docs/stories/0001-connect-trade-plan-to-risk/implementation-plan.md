# Implementation Plan

## Overview

Implement Story 0001 as a Trading Core application flow with two boundaries:

1. Market Intelligence exposes one exact, accepted, broker-neutral Trade Plan snapshot through an internal read API.
2. Trading Core authenticates the caller, verifies plan/account ownership, assembles the current immutable ADR-028 context, invokes `risk-domain`, records the evaluation artifact idempotently, and returns a structured result.

The flow stops after risk evaluation. It never imports or calls the Execution Domain or Broker submission pipeline. An approved completed result may be reflected in the Market Intelligence Trade Plan lifecycle as `RISK_VALIDATED`; rejected, incomplete and failed results do not advance the plan.

An Implementation Plan is required because the Story introduces one cross-service contract, integrates the standalone Risk Domain library, and coordinates several snapshot sources.

### Resolved Planning Decisions

- **Business owner:** Trading Core owns evaluation orchestration and its application API.
- **Trade Plan owner:** Market Intelligence remains the source of the exact accepted plan version and lifecycle transition.
- **Decision authority:** `risk-domain` remains unchanged and is the only deterministic evaluator.
- **Evaluation identity:** require an `Idempotency-Key` on the Trading Core command. Repeating the same key with the same plan/version/account returns the previously recorded evaluation; reuse with different identity is rejected. A new key intentionally creates a new evaluation against a new current context.
- **Evaluation storage:** use a Trading Core application repository port with an in-memory adapter for this increment. This matches the current in-memory Trade Plan maturity and avoids an unrequested database migration. The response and stored record retain evaluation, plan/version and trace references. Durable audit persistence remains follow-up work.
- **Portfolio authority:** use current Broker Service account/position facts as the external position snapshot, reconciled through Trading Core's account ownership and mappings. Do not derive the risk portfolio from Dashboard view DTOs or persisted historical trades.
- **Required margin:** derive it deterministically as Trade Plan notional divided by the positive leverage captured in the accepted planning context. Reject missing, zero or inconsistent inputs before invoking the engine.
- **API:** expose a Trading Core authenticated command endpoint for an exact plan version. Keep Market Intelligence's snapshot/lifecycle endpoints internal.

## Planned Changes

### 1. Establish repeatable `risk-domain` consumption

- Add `com.hope.trading:risk-domain:0.0.1-SNAPSHOT` to Trading Core's Maven dependencies.
- Keep `risk-domain` framework-free and do not duplicate its classes in Trading Core.
- Validate/build `risk-domain` first with `mvn install`, because the repository has no root Maven reactor, then run Trading Core independently.

### 2. Add the Market Intelligence internal risk snapshot contract

- Introduce an immutable internal response representing exactly the information Trading Core needs: plan ID/version/status, owner ID, trading account ID, context version/time, account currency, leverage, instrument, direction, entry, quantity, notional and expected monetary loss.
- Add an application query service that uses `TradePlanRiskValidationBoundary.loadAcceptedSnapshot` and the exact `TradingContextRepository` snapshot referenced by the plan.
- Validate that the context exists, matches the exact reference, and remains coherent with position-sizing currency.
- Expose a read-only internal endpoint for an exact plan ID/version using `ResponseEntity`.
- Add an internal command endpoint or application operation for recording `RISK_VALIDATED` only after Trading Core reports an approved completed evaluation. Require the exact accepted version and evaluation reference; do not expose `READY_TO_EXECUTE` in this Story.
- Map missing plan/context, non-accepted status and stale version to explicit controlled responses.

### 3. Add a Trading Core Market Intelligence port and adapter

- Define an application-facing `TradePlanRiskSnapshotPort` independent from Feign and Market Intelligence implementation classes.
- Add a Feign client for the internal Market Intelligence endpoints and an adapter that maps the transport DTO to Trading Core's immutable application model.
- Propagate the authenticated authorization context using existing Feign conventions.
- Keep all contracts broker-neutral and do not share Market Intelligence domain classes across service boundaries.

### 4. Add risk context assembly in Trading Core

- Create an isolated risk-evaluation package rather than extending the legacy `trading_core.service.RiskEngine` or Dashboard risk projections.
- Add a `TradePlanToProposedTradeMapper` that maps exact plan/version, direction, quantity, notional and expected monetary risk; derive margin from notional/leverage.
- Add a `RiskEvaluationContextAssembler` application service with narrow ports for account/rules, broker portfolio and market-price facts.
- Reuse existing Trading Core account ownership, Broker Service and Market Data clients behind adapters, but map their facts into ADR-028 `AccountSnapshot`, `PortfolioSnapshot`, `PositionSnapshot`, `MarketSnapshot`, `TradingContext` and `RuleSetSnapshot`.
- Use coherent timestamps and explicit version identifiers for every snapshot. Reject unavailable balances, positions, price, currency conversion, rule configuration, leverage or ownership before engine invocation.
- Map current Trading Core rules to ADR-028 standard upper-bound rules without duplicating rule evaluation logic. Unsupported mandatory configuration fails closed.

### 5. Add the idempotent risk-evaluation application flow

- Define a command containing authenticated user ID, exact plan ID/version, account ID and idempotency key.
- Load the accepted snapshot from Market Intelligence and verify its owner/account against the authenticated principal and Trading Core account ownership.
- Check the evaluation repository for the idempotency key before loading volatile context. Return the prior result for an identical command and reject key reuse with a different identity.
- Generate evaluation/correlation identifiers, assemble the immutable context and call `RiskEngines.standard(...).evaluate(context)` through the ADR-028 interface.
- Store an immutable application evaluation record containing the command identity, exact Trade Plan reference, result, trace and creation time.
- Only when the result is `COMPLETED` with `APPROVED` or `APPROVED_WITH_WARNINGS`, call the Market Intelligence lifecycle port to record the exact plan version as risk validated.
- Never call execution services, repositories, ports or broker submission operations.

### 6. Expose a structured Trading Core API

- Add `POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations` in Trading Core.
- Require `Idempotency-Key`; take the account identifier from a validated request body while deriving the user from `Authentication`.
- Return a structured response with evaluation ID, plan ID/version, evaluation status, optional decision, violations, warnings, relevant metrics, evaluation timestamp and trace metadata.
- Use explicit controlled errors for missing plan/context/account/price/rules, ownership violations, stale/non-accepted versions, inconsistent snapshots and idempotency conflicts.
- Return `ResponseEntity` for every controller path. Do not expose Kraken-specific values.

### 7. Keep lifecycle and execution boundaries explicit

- Do not mark rejected, incomplete or failed plans as `RISK_VALIDATED`.
- Do not mark any plan `READY_TO_EXECUTE`.
- Do not create `RiskApprovalReference`, `ExecutionIntent`, `ExecutionAttempt`, domain execution events or broker orders.
- Add an architecture/interaction test proving the new package has no dependency on the Execution Domain and that no execution/broker mock is invoked by the evaluation flow.

## Files to Modify

Expected existing files; exact edits remain subject to nearby conventions discovered during implementation:

- `trading-core/pom.xml` — declare the local `risk-domain` library.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/TradePlanningConfiguration.java` — wire the internal snapshot/lifecycle query boundary if constructor discovery is not sufficient.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceExceptionHandler.java` — add controlled mappings for exact-plan risk snapshot errors.
- Existing Trading Core Feign authorization/configuration only if required to register the new client consistently.
- Relevant existing test fixtures/configuration in Market Intelligence and Trading Core when reuse is clearer than duplication.

`risk-domain` production files should not change. Any required domain change must stop implementation and request architectural review.

## Files to Create

Names may be adjusted to match exact local naming conventions, but responsibilities must remain separate.

### Market Intelligence

- Internal risk snapshot response DTO.
- `TradePlanRiskSnapshotQueryService`.
- Internal Trade Plan risk integration controller.
- Explicit exceptions for snapshot not found, invalid lifecycle/status and context inconsistency if existing exceptions are insufficient.
- Focused query-service and controller tests.

### Trading Core

- `risk/application` command, result record and orchestration service.
- `risk/application/port/TradePlanRiskSnapshotPort` and evaluation repository port.
- Immutable Trade Plan risk snapshot application model.
- `risk/application/RiskEvaluationContextAssembler` and `TradePlanToProposedTradeMapper`.
- Narrow account, portfolio, market and rule snapshot ports where existing services cannot be consumed without presentation coupling.
- `risk/infrastructure/marketintelligence` Feign DTO/client/adapter.
- Broker/account/market/rule snapshot adapters using existing clients and repositories.
- In-memory idempotent evaluation repository adapter.
- Risk engine configuration wiring `RiskEngines.standard` with an injected `Clock` and version string.
- API controller, request/response DTOs and exception handler.
- Unit tests for mapping, assembly, orchestration and idempotency.
- Controller test for response and controlled errors.
- Architecture or focused dependency test preventing Execution Domain coupling.

### Risk Domain

- No new production files expected.
- Reuse existing fixtures where possible; add a focused integration test only if the cross-module mapping exposes a missing documented engine case.

## Dependencies

### Internal

- Trading Core → Market Intelligence internal HTTP contract for exact accepted Trade Plan snapshot and approved lifecycle acknowledgment.
- Trading Core → Broker Service facts for current balances and open positions through existing broker-neutral clients.
- Trading Core → Market Data for current instrument price/state through existing clients.
- Trading Core → `risk-domain` Maven artifact for pure deterministic evaluation.
- Trading Core account/rule repositories for ownership and effective configuration.

### External

No new external service or library is required. Existing Spring MVC, Validation, OpenFeign, JPA/test infrastructure and the local `risk-domain` artifact are sufficient.

### Database

No schema or migration change is planned. Evaluation records are held through an application repository port with an in-memory adapter. This is an explicit V1 limitation and must not be described as durable audit persistence.

## Test Plan

### Market Intelligence focused tests

- Exact accepted plan/version returns the complete broker-neutral risk snapshot.
- Snapshot includes owner/account/context identity and maps execution parameters exactly.
- Missing plan or referenced context produces a controlled not-found error.
- Non-accepted or stale/non-latest version is rejected.
- Approved lifecycle acknowledgment transitions only the exact latest accepted version to `RISK_VALIDATED`.
- Rejected/incomplete/failed outcomes cannot invoke that lifecycle operation through the Trading Core service tests.
- Controller returns `ResponseEntity` with expected status/body.

### Trading Core unit/application tests

- Map long and short Trade Plan directions to ADR-028 direction correctly.
- Map quantity, notional and expected loss; derive required margin from positive leverage.
- Reject missing/invalid leverage, price, currency, rules, account or portfolio context before evaluation.
- Assemble coherent immutable account, portfolio, market, trading and rule snapshots.
- Accepted engine result returns structured approval and records `RISK_VALIDATED` once.
- Rejected result contains structured blocking reasons and does not advance lifecycle.
- `INCOMPLETE` and `FAILED` fail closed and do not advance lifecycle.
- Same idempotency key plus identical command returns the stored evaluation without a second engine/lifecycle invocation.
- Same key plus different plan/version/account returns an explicit conflict.
- Account/plan ownership mismatch is forbidden.
- No execution service, execution repository or broker-execution port is called or injected.
- Controller covers accepted, rejected, missing input, not found, forbidden and idempotency-conflict responses.

### Risk Domain regression

- Run the complete standalone `risk-domain` suite.
- Existing accepted/rejected and context-consistency tests remain green.

### Existing module regression

- Run the complete Market Intelligence suite.
- Run the complete Trading Core suite after installing the local Risk Domain artifact.
- No frontend, Broker Service or Market Data production code is expected to change.

## Risks

- Local Maven artifact resolution can make Trading Core validation order-sensitive because there is no root reactor. Validation must install the exact working-tree `risk-domain` artifact first and report this explicitly.
- Broker positions may omit values required by ADR-028 snapshots, especially loss at stop or margin. The adapter must fail closed rather than invent data; if existing broker contracts cannot supply them, implementation must stop and request scope/architecture guidance.
- Current Market Intelligence persistence is in memory. A service restart can remove the source plan, and the evaluation repository is also intentionally non-durable in this increment.
- A lifecycle update after evaluation is a distributed two-service operation. Idempotent acknowledgment and exact-version guards are required; no distributed transaction is introduced.
- Authentication propagation and ownership checks must be tested at both the Trading Core boundary and snapshot mapping boundary.
- The legacy Trading Core `RiskEngine` name creates accidental-import risk. New code must use fully separated packages and ADR-028 types.
- Serializing the full ADR-028 result may expose implementation-heavy metrics. The API DTO must preserve structured reasons and traceability while remaining a stable application contract.

## Validation Checklist

- [ ] Only Story-scoped files in `market-intelligence`, `trading-core`, `risk-domain` tests, and Story documentation are modified.
- [ ] No frontend, Broker Service, Market Data collection or Gateway behavior is modified unless routing is proven necessary and separately approved.
- [ ] `risk-domain` remains Spring-, persistence-, broker- and AI-free.
- [ ] Market Intelligence performs no account loading, risk decision or broker call.
- [ ] Trading Core uses ADR-028 and does not extend the legacy risk implementation.
- [ ] Exact Trade Plan ID/version and authenticated ownership are verified.
- [ ] Missing/inconsistent context fails closed with controlled errors.
- [ ] Approved and approved-with-warning results are distinguished from rejected/incomplete/failed results.
- [ ] Rejected responses contain structured reasons.
- [ ] Idempotent retries do not repeat logical evaluation/lifecycle side effects.
- [ ] No `ExecutionIntent`, execution attempt or broker request is created.
- [ ] `cd risk-domain && mvn test` passes.
- [ ] `cd risk-domain && mvn install -DskipTests` makes the local artifact available for independent service validation.
- [ ] `cd market-intelligence && ../trading-core/mvnw test` passes.
- [ ] `cd trading-core && ./mvnw test` passes.
- [ ] Focused tests pass independently.
- [ ] `git diff --check` passes.
- [ ] Final diff is reviewed for scope and pre-existing user changes are preserved.

## Recommendation

**Ready for implementation after human approval.**

The plan preserves the accepted service ownership and avoids a new service, new risk rules, execution coupling and database changes. Implementation must stop if Broker/Market/Account sources cannot provide a coherent ADR-028 snapshot without inventing values, or if consuming the standalone `risk-domain` artifact requires a broader build-architecture decision.
