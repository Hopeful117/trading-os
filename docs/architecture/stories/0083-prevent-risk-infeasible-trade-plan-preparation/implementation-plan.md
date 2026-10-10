# Implementation Plan - Story 0083

## Objective

Add a server-side Risk feasibility gate to the new Opportunity preparation
journey so that a newly generated candidate is not presented to the trader when
the current authoritative Risk context already makes it infeasible.

The final acceptance-time Risk evaluation remains mandatory and authoritative.
The feasibility gate is an earlier, retryable decision-flow guard; it is not an
authorization shortcut.

## Design Decision

Use an additive Trading Core/Risk preflight operation immediately after a new
Opportunity TradePlan is generated and before the preparation response is
returned to the frontend.

The flow becomes:

```text
Opportunity row
  -> Market Intelligence creates or reuses TradePlan
  -> if reused: return existing TradePlan unchanged
  -> if newly created: Trading Core/Risk performs feasibility preflight
  -> if blocking: return structured retryable Risk refusal; do not navigate to plan
  -> if feasible: return TradePlan reference and navigate normally
  -> explicit acceptance
  -> existing authoritative Risk evaluation
  -> explicit execution authorization
```

This preserves the requirement that existing TradePlans remain openable at any
lifecycle stage. It also avoids duplicating Risk formulas in Market Intelligence
or Angular.

The preflight will reuse the current Risk context assembly and deterministic
engine semantics, but it will have explicit preview semantics:

* it may evaluate a proposed TradePlan without accepting it;
* it must not create an execution intent;
* it must not authorize or acknowledge execution;
* it must not change Risk rules or thresholds;
* it must return blocking reasons and metrics;
* it must fail closed for unavailable or stale facts;
* it must be safely retryable with a new idempotency key.

## Backend Changes

### 1. Preserve New-versus-Reused Preparation Outcome

Extend the internal Opportunity generation response with an explicit
`reused` indicator.

Affected areas:

* `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/OpportunityTradePlanGenerationService.java`
* `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalOpportunityTradePlanController.java`
* `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/infrastructure/MarketIntelligenceTradePlanningClient.java`
* related generation service and controller tests

The existing persisted-plan lookup remains authoritative for reuse. The
response must distinguish a newly generated plan from an existing plan so the
preflight is not applied retroactively to an existing TradePlan.

The public response may expose the flag additively or keep it internal to
Trading Core, provided the frontend receives the same existing plan identifier
and version on success.

### 2. Introduce Explicit Risk Feasibility Semantics

Refactor the current Risk application path so context assembly and deterministic
evaluation can be reused by both:

* normal accepted-plan Risk evaluation; and
* proposed-plan feasibility preflight.

The exact extracted abstraction should remain inside Trading Core. It must keep
the Risk Domain stateless and repository-free.

The preflight command should carry:

* actor identity;
* TradePlan identifier and version;
* account identifier;
* idempotency key;
* requested timestamp;
* explicit preview/preflight mode.

The existing `TradePlanRiskEvaluationService` or a focused sibling service may
own this operation. The implementation must avoid making the existing accepted
evaluation endpoint accept proposed plans accidentally.

### 3. Add the Orchestration Gate

Update `OpportunityTradePlanOrchestrationService` so that:

* account ownership and planning profile checks remain unchanged;
* reused plans are returned immediately;
* newly created plans are sent through the Risk feasibility operation;
* blocking Risk reasons are translated into a structured retryable API error;
* feasible plans preserve the current response contract and navigation;
* no execution or acceptance side effect occurs during preparation.

The error contract must preserve at least:

* a stable feasibility error code;
* blocking Risk rule codes;
* relevant authoritative metrics;
* whether retry may be attempted.

Do not collapse `DAILY_DRAWDOWN`, `MAX_EXPOSURE`, or other blocking codes into a
generic frontend-only message.

### 4. Preserve Idempotency and Persistence Semantics

Preflight requests must be idempotent for the same actor, account, plan,
version, and key. A repeated request must replay the same persisted or
deterministically reconstructed result rather than creating an execution
acknowledgment or a second evaluation side effect.

The plan creation/reuse path must remain idempotent. Existing plan lookup must
continue to return the latest version for every lifecycle status.

If the implementation persists preflight results, it must use an explicit
preview/preflight status or record type and must not make them appear as
authorization acknowledgments. If persistence is not required by the existing
Risk audit model, the implementation must still preserve traceability in the
returned result and prevent duplicate side effects.

## Frontend Changes

Update the Opportunity preparation state to consume the authoritative server
feasibility response.

Affected areas:

* `trading-os-web/src/app/core/services/trade-plan.service.ts`
* `trading-os-web/src/app/core/utils/trade-flow-error.ts`
* `trading-os-web/src/app/features/opportunities/opportunities.ts`
* `trading-os-web/src/app/features/opportunities/opportunities.html`
* any shared preparation/error state used by the compatibility prepare route

Expected behavior:

* feasible new or reused plan: navigate directly to the existing plan page;
* infeasible new plan: remain on the Opportunity journey and render the server
  reason and metrics with a retry action;
* stale/unavailable facts: render an unavailable/retry state, not a successful
  preparation;
* existing plan: remain directly openable regardless of current lifecycle
  status;
* no client-side computation of exposure, drawdown, sizing, or Risk limits.

The existing TradePlan page Risk rendering remains authoritative and is not
replaced by the preflight message.

## Tests

### Market Intelligence

* response identifies new versus reused Opportunity TradePlans;
* existing-plan reuse remains unchanged for every lifecycle status;
* generation response remains compatible with existing callers.

### Trading Core

* preflight evaluates a proposed TradePlan without requiring acceptance;
* `MAX_EXPOSURE` produces a blocking structured refusal;
* `DAILY_DRAWDOWN` produces a blocking structured refusal;
* feasible candidate returns a successful feasibility result;
* missing, stale, or incomplete facts fail closed;
* actor/account ownership remains enforced;
* repeated preflight command is idempotent;
* preflight does not create acceptance, execution intent, or execution
  acknowledgment;
* final accepted-plan Risk evaluation remains unchanged;
* Opportunity preparation does not create an execution intent.

### Risk Domain

* projected exposure uses the existing portfolio projection semantics;
* projected drawdown uses the existing daily baseline semantics;
* preview mode does not alter deterministic rule outcomes;
* existing rule and engine tests remain green.

### Angular

* feasible row preparation navigates to the plan page;
* blocking preflight response renders rule codes and metrics;
* retry issues a fresh request;
* unavailable facts render retryable state;
* existing plan reuse navigates regardless of status;
* no duplicate clicks create duplicate preparation requests.

## Validation Commands

Expected validation, adjusted after implementation to repository reality:

```text
market-intelligence: mvn -q test
trading-core: mvn -q test
risk-domain: mvn -q test
trading-os-web: npm run test:ci
trading-os-web: npm run build
trading-os-web: npx prettier --check <changed frontend files>
git diff --check
```

Runtime validation must cover both a feasible and an infeasible PAPER account
state and verify that the infeasible path does not navigate to an actionable
new TradePlan or create execution state.

## Documentation Reconciliation

After implementation, inspect the canonical API and architecture documentation
for the new feasibility contract. Update only documentation that describes the
changed user-facing flow, API response, or Risk orchestration behavior.

No ADR is expected unless implementation reveals a new responsibility boundary
or materially changes the accepted TradePlan/Risk architecture.

## Risks and Mitigations

* **Stale preflight:** keep final acceptance-time Risk evaluation mandatory.
* **Accidental authorization side effect:** separate preview mode and test that
  no acknowledgment or execution intent is created.
* **Existing plan lockout:** bypass preflight for reused plans and preserve
  direct plan loading at every lifecycle status.
* **Duplicate calculations:** reuse the Trading Core/Risk context and engine,
  never implement formulas in frontend or Market Intelligence.
* **Unclear user response:** return stable codes, metrics, and retryability from
  the server.

## Expected Deliverables

* additive server-side feasibility contract and orchestration;
* frontend handling for authoritative feasibility refusal and retry;
* backend, Risk Domain, and Angular regression tests;
* updated canonical documentation if the API or user flow requires it;
* implementation report, documentation outcome, and validation evidence.
