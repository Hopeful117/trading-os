# Repository Analysis - Story 0083

## Scope Reviewed

This analysis covers the current Opportunity-to-TradePlan preparation path, the
TradePlan-to-Risk handoff, the deterministic Risk Domain calculations, PAPER
risk facts, and the frontend surfaces involved in direct Opportunity
navigation.

The current worktree contains uncommitted implementation changes from Story
`0082`, the Story `0082` closure note, and the new Story `0083`. Those changes
are pre-existing for this analysis and were not reverted.

DevLog context retrieval was attempted for the `trading-os` project but failed
with provider quota exhaustion. The analysis therefore relies on the current
repository, accepted ADRs, and existing Story artifacts.

## Current Behavior

The current primary flow is:

```text
Opportunity row
  -> Trading Core creates or reuses an Opportunity TradePlan through Market Intelligence
  -> frontend loads the proposed TradePlan
  -> user accepts the TradePlan
  -> Trading Core evaluates deterministic Risk
  -> user may authorize execution only after approval
```

Story `0082` now reuses the latest persisted TradePlan linked to the
Opportunity. Existing plans remain openable at every lifecycle stage.

The current Opportunity creation path is:

* `trading-os-web/src/app/features/opportunities/opportunities.ts`
  invokes `TradePlanService.createFromOpportunity` on row activation.
* `trading-os-web/src/app/core/services/trade-plan.service.ts` calls the
  public Opportunity TradePlan endpoint.
* `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/api/OpportunityTradePlanController.java`
  authenticates the actor and accepts the account identifier.
* `OpportunityTradePlanOrchestrationService` validates account ownership,
  resolves the effective Trade Planning Profile, and delegates TradePlan
  construction to Market Intelligence.
* `market-intelligence` constructs the TradePlan from the active Opportunity,
  planning context, and a fresh market price.

TradePlan creation currently answers whether a planning proposal can be
constructed. It does not evaluate whether the resulting candidate can pass the
current account and portfolio Risk context.

## Risk Authority

The accepted ADRs establish the following boundaries:

* ADR-014 places Trading Rules Validation after Market Intelligence and before
  Human Validation in the decision pipeline.
* ADR-027 defines TradePlan as a proposal between Market Intelligence and Risk;
  it may be rejected, expire, be executed later, or never execute.
* ADR-028 defines Risk as a deterministic, explainable, auditable,
  fail-closed engine over immutable snapshots. The engine does not access
  repositories or external services and does not create or modify TradePlans.
* ADR-031 assigns financial authorization to Trading Core/Risk and explicitly
  excludes account equity, existing exposure, projected exposure, and rule
  profiles from `TradePlanningContext`.

Therefore, the feasibility check must not be implemented by:

* duplicating `MAX_EXPOSURE` or `DAILY_DRAWDOWN` formulas in Angular;
* adding financial snapshots to Market Intelligence planning context;
* letting Market Intelligence infer account feasibility;
* weakening the existing Risk evaluation or configured thresholds.

## Current Risk Calculation

`TradePlanRiskEvaluationService` assembles the authoritative Risk context in
Trading Core:

1. validates account ownership and Risk configuration;
2. loads the TradePlan snapshot;
3. loads current account and portfolio facts through `RiskFactsProvider`;
4. obtains current market valuations;
5. builds `AccountSnapshot`, `PortfolioSnapshot`, `MarketSnapshot`, and
   `RuleSetSnapshot`;
6. invokes the deterministic Risk Domain;
7. persists the evaluation and returns structured reasons, metrics, and trace
   metadata.

The existing public evaluation endpoint is:

`POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations`

The current service deliberately requires an `ACCEPTED` TradePlan before
evaluation. This protects the existing human-acceptance boundary, but it also
means that it cannot be reused unchanged as a pre-acceptance feasibility
operation.

## Deterministic Metrics

The Risk Domain uses `DeterministicRiskEngine` and `ProjectionEngine`.

For a proposed trade:

* existing portfolio positions are aggregated by instrument;
* the proposed trade is applied to the projected portfolio;
* projected exposure is calculated from projected positions;
* projected drawdown is calculated from the daily baseline and projected equity;
* `DerivedMetricsCalculator` derives:
  * `exposureRatio = projectedExposure / observedEquity`;
  * `dailyDrawdownRatio = projectedDrawdown / dailyBaseline`;
  * position risk and total drawdown ratios;
* the configured Risk rules evaluate those derived metrics.

This is the correct calculation for the reported PAPER rejection. A feasibility
check must reuse this pipeline or a shared application boundary around it. It
must not approximate the result using only the TradePlan notional.

## Observed Runtime Evidence

The PAPER evaluation returned:

```text
DAILY_DRAWDOWN
  current: 0.030003252967300658...
  maximum: 0.030000000000

MAX_EXPOSURE
  current: 0.059669695431366249...
  maximum: 0.030000000000
```

The result was `COMPLETED` with decision `REJECTED`. The trace included the
account, portfolio, market, rule-set, and context snapshot versions. This
confirms a valid deterministic rejection rather than a missing-facts or
calculation failure.

## Existing PAPER Facts

`ModeAwareRiskFactsProvider` uses local Trading Core account and trade state for
PAPER accounts. It maps open `Trade` records into Risk positions and derives
their protection status from the local stop loss. It maps closed trades inside
the current Risk day into closed PnL facts.

The provider is authoritative for the PAPER snapshot consumed by Risk. A
preflight must use this same provider and timestamped snapshot path so that the
result cannot disagree with the subsequent authoritative Risk evaluation due to
different fact sources.

## Existing Frontend State

The TradePlan page currently renders the authoritative Risk response, including
blocking reason codes and metrics. The frontend already has retryable error and
Risk states, but the current primary Opportunity path only learns about
`DAILY_DRAWDOWN` or `MAX_EXPOSURE` after a plan is created and accepted.

The frontend must receive a server response for feasibility. It must not decide
locally whether a plan is possible.

## Candidate Design Options

### Option A - Frontend Pre-calculation

Rejected. It would duplicate financial rules, use incomplete facts, and violate
ADR-028 and ADR-031.

### Option B - Market Intelligence Feasibility

Rejected. Market Intelligence does not own account equity, portfolio exposure,
drawdown, Risk profiles, or broker/PAPER facts.

### Option C - Reuse Existing Evaluation Endpoint Before Acceptance

Not directly compatible. The current endpoint requires an `ACCEPTED` TradePlan
and its side effects persist an evaluation and may deliver an acknowledgment.
Removing that requirement or reusing the endpoint without separating preview
semantics would blur human acceptance and authorization state.

### Option D - Add a Trading Core/Risk Feasibility Operation

Recommended for implementation planning. Add an additive server-side operation
that evaluates the exact candidate against the same authoritative Risk context
and deterministic engine, but has explicit preview semantics:

* it does not accept the TradePlan;
* it does not create an execution authorization;
* it does not acknowledge execution readiness;
* it returns structured blocking reasons and metrics;
* it fails closed for unavailable or stale facts;
* it is idempotent or safely retryable for the preparation request.

The final implementation shape remains to be decided during the approved
Implementation Plan. It may be exposed as a dedicated preflight endpoint or as
an orchestration operation behind the existing Opportunity preparation
endpoint, but the financial calculation must remain in Trading Core/Risk.

## Recommended Flow

The recommended behavior for a new Opportunity journey is:

```text
Opportunity row
  -> server builds or resolves the candidate TradePlan
  -> Trading Core/Risk evaluates candidate feasibility with current facts
  -> if blocking: return structured retryable Risk refusal; do not present an actionable plan
  -> if feasible: return the TradePlan reference and open the existing plan page
  -> explicit human acceptance
  -> authoritative Risk evaluation remains required
  -> explicit execution authorization remains separate
```

An existing TradePlan at any lifecycle stage must bypass new-plan feasibility
creation and remain directly openable, as required by the user and Story
`0082`.

Feasibility is a point-in-time guard, not a guarantee: account and portfolio
facts can change between preflight and acceptance. The normal accepted-plan Risk
evaluation must therefore remain authoritative and unchanged.

## Required Repository Changes

Expected affected areas, subject to the Implementation Plan:

* Trading Core Opportunity orchestration and a Risk feasibility application
  boundary.
* Risk evaluation context assembly or a shared internal evaluation path that
  supports preview semantics without accepting the plan.
* Additive DTOs/controllers/client contracts for structured feasibility results.
* Market Intelligence only if the preparation response must carry an explicit
  new-versus-existing plan outcome.
* Angular Opportunity and TradePlan preparation states for blocking reasons and
  retry.
* Trading Core, Risk Domain, Market Intelligence contract, and Angular tests.

No broker provider change is indicated by the current evidence.

## Risks

* A preflight can become stale before acceptance; final Risk evaluation must not
  be removed.
* Reusing the full evaluation path could accidentally persist authorization or
  deliver execution acknowledgments.
* A new response contract must preserve idempotency and actor/account ownership.
* Existing plans must not become inaccessible because the current account has
  since crossed a Risk limit.
* The implementation must distinguish blocking infeasibility from unavailable
  Risk context so the user receives an actionable retry rather than a false
  rejection.

## Validation Plan

The next stages should validate at minimum:

* deterministic `MAX_EXPOSURE` preflight rejection;
* deterministic `DAILY_DRAWDOWN` preflight rejection;
* feasible candidate allowing the existing plan page and acceptance flow;
* unavailable or stale facts failing closed;
* retry after changed PAPER facts;
* existing TradePlan reuse for every lifecycle status;
* no TradePlan acceptance, execution intent, or broker order as a preflight side
  effect;
* existing authoritative Risk evaluation still rejecting when facts change
  after preflight;
* focused Maven tests, Angular tests, production build, formatting, and
  `git diff --check`.

## Conclusion

The repository confirms the Story is a design correction around a real
responsibility gap, not a request to change Risk semantics. The current Risk
engine and PAPER facts are authoritative and calculate the observed rejection
correctly. The recommended next artifact is an Implementation Plan for an
additive Trading Core/Risk feasibility operation, followed by a separate human
approval gate.
