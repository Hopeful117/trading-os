# Implementation Plan - Story 0160

## Plan Status

Prepared after explicit approval of the Story 0160 Repository Analysis.
Implementation requires explicit human approval of this plan.

## Scope

Implement one backend-owned, on-demand projection endpoint and one PlanPage
read-only presentation. No persistence, execution, Risk command, Challenge
mutation, or provider-specific behavior is added.

## Architecture

```text
TradePlanRiskPort exact id/version
        ↓
Trading Core read-only fact assembly
        ↓
Risk Domain pure scenario projection
        ↓
Trading Core typed response
        ↓
PlanPage rendering
```

The existing Gateway wildcard `/api/v1/trade-plans/**` route already targets
Trading Core, so no Gateway route change is expected.

ADR-028 remains the financial authority. The Risk Domain receives immutable
scenario inputs and returns deterministic facts. Trading Core remains the
adapter that loads the authenticated Trade Plan, account/broker facts, market
valuation, Risk configuration, and Challenge definition.

## Implementation Order

### 1. Define Risk-Domain Scenario Types

Add typed immutable records for:

- scenario kind: `CURRENT`, `STOP`, `TARGET`;
- gross scenario P/L and projected account values;
- daily and total drawdown/headroom consequences;
- monetary Risk, Reward, and numeric Risk/Reward;
- availability and unavailable reasons;
- economics basis and observation/projection provenance.

Keep UI formatting out of the domain model. Do not add probability,
recommendation, approval, or execution fields.

### 2. Add Pure Scenario Calculation

Add a Risk-Domain service/engine that consumes immutable account, plan, and
Risk-boundary inputs and:

- resolves Entry according to the persisted entry semantics;
- validates LONG/SHORT geometry through one authoritative validation path;
- computes gross Entry-to-Stop and Entry-to-first-Target outcomes;
- returns unavailable instead of guessing when Stop, Target, quantity, or
  currency facts are missing;
- calculates Reward/Risk only when Risk is strictly positive;
- applies the exact existing daily-loss, maximum-loss, Risk-day, and
  `STRICT`/`INCLUSIVE` semantics;
- never calls repositories or external services;
- never mutates snapshots or domain aggregates.

The existing `ProjectionEngine` remains reusable for portfolio mechanics but is
not treated as sufficient for a closed hypothetical outcome. Its behavior must
remain covered by existing tests.

### 3. Extract Read-Only Trading Core Fact Assembly

Introduce the smallest read-only application boundary needed to assemble:

- authenticated account ownership and account currency;
- exact Trade Plan version through `TradePlanRiskPort.load`;
- broker facts and Risk-day boundaries through the existing facts provider;
- complete market valuation and currency conversion through
  `MarketValuationPort`;
- assigned Risk profile/rule versions and current Risk configuration;
- active Challenge definition/instance when present.

If code is extracted from `TradePlanRiskEvaluationService`, preserve the
existing command/preflight persistence behavior. The new projection path must
not call `evaluate()` or `preflight()` because both persist evaluation state.

### 4. Add Challenge Read Projection

Add a pure Challenge projection adapter that reuses:

- the active Challenge definition and exact phase;
- `ProgressionValueSource` semantics;
- current Challenge target and progress calculations;
- Risk headroom semantics and policy-version compatibility.

It must return `NOT_APPLICABLE` without a Challenge and `UNAVAILABLE` when
authoritative equity, valuation, Risk evaluation context, or policy versions
are incomplete. It must never call `ChallengeEvaluator` or mutate an instance.

### 5. Expose Trading Core API

Add a read-only endpoint:

```text
GET /api/v1/trade-plans/{tradePlanId}/versions/{version}/projection
```

The authenticated principal is resolved server-side. The response includes:

- Trade Plan id/version and account id;
- `observedAt`, `projectedAt`, source snapshot versions/timestamps;
- `economicsBasis: GROSS`;
- `current`, `stop`, and `target` typed scenario objects;
- monetary Risk, Reward, numeric Risk/Reward;
- availability/reasons and provenance;
- Challenge projection where applicable.

Use `ResponseEntity`, existing authentication and ownership conventions, and
fail-closed HTTP responses for missing or mismatched facts. Do not add a
database table or migration.

### 6. Add Frontend Consumer and PlanPage Presentation

Extend the typed TradePlan model and `TradePlanService` with the projection
response and GET call. Load the projection only after the exact plan/version is
loaded. Render a compact `CURRENT | IF STOP | IF TARGET` panel before decision
actions.

The component may format values and states, but must not calculate P/L,
drawdown, Challenge progress, Risk, Reward, or Risk/Reward. It must render:

- loading;
- current available/partial/unavailable;
- Stop available/unavailable;
- Target available/unavailable;
- gross basis and freshness/provenance;
- Challenge not applicable/unavailable;
- no approval or execution language.

Projection refresh is tied to PlanPage plan/version reloads only. No market-data
frequency polling is introduced.

## Expected Files

Likely affected components:

- `risk-domain/.../domain` or `risk-domain/.../projection` for immutable scenario
  types and pure calculation.
- `risk-domain/.../metric` for shared deterministic financial derivation if
  extraction is justified.
- `trading-core/.../risk/application` for read-only fact assembly and service.
- `trading-core/.../risk/api` for the projection controller/response.
- `trading-core/.../challenge/application` for pure Challenge projection.
- `trading-os-web/src/app/core/models/trade-plan.model.ts`.
- `trading-os-web/src/app/core/services/trade-plan.service.ts`.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`.
- matching PlanPage stylesheet and tests.

Exact class names remain subject to repository conventions discovered during
implementation. No unrelated modules should change.

## Test Plan

### Risk Domain

- LONG Stop and Target gross outcomes.
- SHORT Stop and Target gross outcomes.
- missing Stop, missing Target, missing quantity, zero Risk.
- contradictory geometry and entry semantics.
- ratio precision and no divide-by-zero behavior.
- currency mismatch and unavailable valuation.
- inclusive and strict boundary equality.
- Risk-day reset behavior.
- explicit no-mutation/pure input tests.

### Trading Core

- authenticated owner can project the exact version.
- another account/user cannot project the plan.
- stale or incomplete facts fail closed.
- account without Challenge returns account/Risk projection with Challenge
  `NOT_APPLICABLE`.
- Challenge target/progress projection does not mutate phase, trading-day
  evidence, provider confirmation, or instance status.
- projection does not create Risk evaluations, TradePlan versions, Trades,
  ExecutionIntents, ExecutionAttempts, or broker calls.
- contract and controller error handling.

### Frontend

- typed service request uses exact id/version.
- loading and partial availability states.
- gross Risk, Reward, and Risk/Reward rendering from backend values.
- no frontend financial arithmetic.
- PlanPage remains able to accept/reject/evaluate Risk independently.
- projection load failures do not trigger mutations or block existing actions.

## Validation Commands

Targeted first:

```text
cd risk-domain && ./mvnw test
cd trading-core && ./mvnw -Dtest=<scenario and projection tests> test
cd market-intelligence && ./mvnw -Dtest=<affected contract tests> test
cd trading-os-web && npm run test:ci -- --include=<affected tests>
cd trading-os-web && npm run build
git diff --check
```

Then run the full affected Maven suites, full Angular tests, production build,
and authenticated PAPER PlanPage runtime validation if the local environment is
available.

## Risks and Controls

- **Duplicated financial formulas:** centralize gross derivation in Risk Domain;
  keep Angular as a consumer.
- **Mutation through Risk command paths:** use a dedicated read-only fact path;
  never invoke official/preflight evaluation for projection.
- **Challenge lifecycle mutation:** use pure progression/headroom derivation;
  never call `ChallengeEvaluator`.
- **Untruthful costs:** return `economicsBasis=GROSS` and disclose excluded
  costs; do not estimate unavailable fees or slippage.
- **Stale or mixed snapshots:** include timestamps/versions and fail closed on
  incomplete currency/valuation/Risk context.
- **Multiple targets:** project the first target in V1 because that is the
  current Risk handoff contract; disclose the basis rather than silently
  aggregating allocation semantics.
- **New authority boundary:** stop and request an ADR if implementation requires
  a new financial authority or changes `RiskValidationResult` semantics.

## Persistence and Documentation

- No database entity, migration, or persisted projection.
- Existing Gateway route requires no change unless verification shows a more
  specific route is needed.
- Update canonical API/frontend documentation only if the repository contains
  relevant documentation that describes the affected contract.
- Implementation Report must record validation and the proposal-only vault
  outcome; no Obsidian write is performed.

## Approval Gate

This plan is implementation-ready subject to explicit human approval. No
implementation changes may begin until the plan is approved.
