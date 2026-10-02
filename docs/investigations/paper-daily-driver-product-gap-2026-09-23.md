# PAPER Daily Driver - Product Gap Investigation

**Date:** 2026-09-23
**Scope:** PAPER daily discretionary trading workflow
**Repository revision:** `f04e06a` (`feature/market-manual-trade-ticket`)
**Status:** Investigation complete; implementation deliberately not performed

## Executive Conclusion

The repository already contains the deterministic PAPER engine and the shared
Trade Plan -> Risk -> human execution pipeline. The opportunity-origin journey
has an official runtime pass through open, reload, close and reload.

The nearest product gap is not a second manual Risk or execution architecture.
It is proving and completing the same end-to-end journey for a MANUAL TradePlan
from the normal Web workflow. The current worktree contains an uncommitted
Story 0060 implementation that embeds a manual ticket in the account-scoped
Decision Workspace. That work must remain human-reviewed before it is treated as
the accepted product baseline.

The immediate next Story is therefore **0061 - Validate the MANUAL PAPER Daily
Driver**, not a rewrite of Story 0059.

## Evidence And Confidence

| Conclusion | Confidence | Evidence |
| --- | --- | --- |
| Story 0048 opportunity-origin PAPER lifecycle still has repository runtime evidence | CONFIRMED | Story 0048 engineering and implementation reports; runtime report lines 289-317 records the successful second journey |
| Account-first Decision Workspace and market context exist | CONFIRMED | Stories 0056/0057 and current `DecisionWorkspace` implementation |
| MANUAL TradePlan creation exists and uses the canonical TradePlan model | CONFIRMED | Story 0058, `ManualTradePlanOrchestrationService`, Market Intelligence `TradePlanOrigin.MANUAL` tests |
| Story 0059 changes manual risk provenance, but is not a complete approved-risk runtime proof | HIGH_CONFIDENCE | Commit `e00b26e`; shared risk handoff tests and current runtime evidence |
| Manual plans can use the existing risk and execution APIs without a second pipeline | HIGH_CONFIDENCE | `TradePlanRiskHandoffService`, `TradePlanRiskEvaluationService`, `ValidateAndCreateService`, and `PlanPage` are origin-neutral |
| Manual full lifecycle is currently runtime-proven | UNKNOWN / NEEDS_RUNTIME_VALIDATION | Current Story 0060 evidence reaches a deterministic risk rejection due unavailable valuation; no approved MANUAL fill is recorded |
| Current worktree Story 0060 UI is accepted product baseline | NEEDS_HUMAN_REVIEW | Files are uncommitted and the Story remains human-review pending |

## Current Product Baseline

| Capability | Classification | Current evidence |
| --- | --- | --- |
| PAPER account provisioning | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 official journey and account onboarding contracts |
| PAPER account persistence and reload | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 and PAPER persistence tests |
| Effective PAPER Trade Planning Profile | IMPLEMENTED_AND_RUNTIME_VALIDATED | Stories 0053/0055; successful Story 0048 final journey used compatible sizing |
| Risk Profile resolution | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 runtime and Trading Core risk service |
| Market selection | IMPLEMENTED_AND_RUNTIME_VALIDATED | Stories 0056/0057 and Decision Workspace |
| Account-scoped Decision Workspace | IMPLEMENTED_BUT_NOT_RUNTIME_VALIDATED for accepted baseline | Story 0057 is complete in repository history; current worktree extends it with the manual ticket |
| Ticker, OHLC, order book, recent trades, market state, freshness | IMPLEMENTED_AND_RUNTIME_VALIDATED for workspace display | Story 0057; current UI renders explicit waiting/error/freshness states |
| Active Scan and opportunity selection | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 official scan and current opportunity routes |
| Opportunity-origin TradePlan | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 runtime journey and existing planning route |
| MANUAL TradePlan creation | IMPLEMENTED_AND_RUNTIME_VALIDATED for creation | Story 0058 tests and current Story 0060 runtime evidence; full lifecycle remains unproven |
| MANUAL TradePlan persistence and provenance | IMPLEMENTED_AND_RUNTIME_VALIDATED | `TradePlanOrigin.MANUAL`, authenticated author, no synthetic Opportunity, persistence tests |
| TradePlan acceptance | IMPLEMENTED_AND_RUNTIME_VALIDATED for shared lifecycle | `TradePlanDecisionController`, `PlanPage`, Story 0048 pattern |
| MANUAL Risk Evaluation | IMPLEMENTED_BUT_NOT_RUNTIME_VALIDATED | Manual snapshots and provenance are supported; current Story 0060 run was rejected by `CURRENT_MARKET_VALUATION_UNAVAILABLE` |
| Execution Intent from approved MANUAL plan | IMPLEMENTED_BUT_NOT_RUNTIME_VALIDATED | `ValidateAndCreateService` loads TradePlan and evaluation without checking origin; no approved MANUAL runtime evidence |
| PAPER execution and settlement | IMPLEMENTED_AND_RUNTIME_VALIDATED for opportunity origin | Story 0048 filled execution and persisted position |
| PAPER execution and settlement for MANUAL origin | IMPLEMENTED_BUT_NOT_RUNTIME_VALIDATED | Same origin-neutral services, but not demonstrated with an approved manual plan |
| Position persistence and valuation | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0048 and `Positions` page; valuation is explicit about unavailable data |
| Full PAPER close | IMPLEMENTED_AND_RUNTIME_VALIDATED for the validated journey | Story 0048 full exposure close and reload |
| Trade/history continuity | IMPLEMENTED_AND_RUNTIME_VALIDATED | Story 0049 and execution history links to TradePlan when available |
| Frontend manual ticket in Decision Workspace | PARTIALLY_IMPLEMENTED | Current uncommitted Story 0060 adds it and records runtime creation, but human review/acceptance is pending |
| Gateway routing | IMPLEMENTED_AND_RUNTIME_VALIDATED for the existing public prefixes | Current route table covers trade plans, intelligence, accounts and executions; the current worktree adds profile routing |

## Proven Runtime Capabilities

Story 0048 remains valid repository evidence. Its final recorded journey was:

```text
login -> PAPER account -> official scan -> opportunity -> TradePlan
-> accept -> APPROVED risk -> explicit execution -> Filled
-> persisted position -> reload -> full close -> reload -> empty state
```

The successful execution was `c89ffab1-a12b-4056-896d-b9aae5b2b568`; the
position remained visible after reload and disappeared after the official close
action. A prior limit rejection was retained as negative evidence rather than
being counted as a pass.

Later Stories did not remove the supporting components. Stories 0056 and 0057
added a separate account-scoped Decision Workspace, while Stories 0058 and 0059
added MANUAL provenance and risk-handoff compatibility. The opportunity PlanPage
still performs acceptance, risk evaluation, explicit execution validation and
execution polling through the existing services.

## Backend/UI Gaps

### Backend capability without a missing product contract

The backend already exposes:

* authenticated MANUAL TradePlan creation through Trading Core;
* authoritative account ownership and market tradability checks;
* server-derived planning context;
* shared TradePlan lifecycle and risk snapshot contracts;
* shared Execution Intent validation and PAPER settlement;
* account-scoped positions, valuation and close behavior.

No backend-only capability requires a new domain model for the manual path.

### UI convergence gap

The accepted Story 0057 workspace originally stopped at market context. The
standalone MANUAL page duplicated account and market selection and removed the
trader from live context. Story 0060's current uncommitted changes address this
by extracting `ManualTradeTicket` and embedding it in `DecisionWorkspace`.

The remaining gap is downstream workflow evidence and small workflow continuity:

* an approved MANUAL risk result must expose the existing execution action;
* the resulting PAPER position must be visible after reload;
* the trader must reach full close and history without identifiers copied by
  hand;
* the official runtime must prove no Execution Intent is created after a risk
  rejection.

The current PlanPage already supplies the acceptance, risk and execute actions.
This is a validation and targeted-blocker Story, not permission to invent a
manual execution path.

## Manual Trading Gap

MANUAL plans are first-class canonical TradePlans, not a second aggregate:

* `ManualTradePlanOrchestrationService` validates owned account, market,
  tradability, planning profile and supported entry type.
* The authenticated principal supplies author and ownership identity.
* The plan contains instrument, direction, entry, quantity, notional, monetary
  risk, stops, targets and planning context.
* Opportunity and observation provenance may be empty without creating a fake
  Opportunity.
* `TradePlanRiskHandoffService` returns the same risk snapshot shape and carries
  `origin=MANUAL` in the preserved source payload.

Creation is therefore confirmed. The missing evidence is the complete manual
journey from an approved risk verdict through PAPER fill, reload, close and
history continuity. Current Story 0060 runtime evidence created and accepted a
MANUAL plan, but risk returned `CURRENT_MARKET_VALUATION_UNAVAILABLE`; execution
was correctly not attempted.

## Risk Gap

Story 0059 is partially overtaken by repository reality. Its essential
compatibility work is present in commit `e00b26e`: the risk snapshot and client
preserve manual origin, and Market Intelligence tests prove an accepted MANUAL
plan can be loaded without Opportunity provenance.

The authoritative Trading Core risk evaluator is origin-neutral. It validates
the authenticated actor, account ownership, accepted plan, planning context,
currency, current broker facts, market valuation, margin and effective rules.
The Risk Domain remains the authority and fails closed for unavailable facts.

What is not established is a successful MANUAL evaluation with current market
facts in the official runtime. That is a validation gap, not evidence that Risk
rules should be weakened. The observed valuation rejection is a valid blocking
verdict.

## Execution Gap

The desired convergence is already represented in code:

```text
OPPORTUNITY TradePlan ─┐
                       ├-> shared Risk -> human -> shared Execution
MANUAL TradePlan ──────┘
```

`ValidateAndCreateService` loads the authoritative TradePlan and RiskEvaluation,
checks approved decision, owner, account identity, version and execution
parameters, then calls the common `CreateExecutionIntentService`. It does not
branch on `TradePlanOrigin` or require an Opportunity.

`ExecutionTimeRiskRevalidationService` and `PaperSettlementService` also use
TradePlan references and authoritative account/broker identity rather than
Opportunity provenance. PAPER settlement remains Trading Core authority; LIVE
execution remains behind the broker boundary.

Therefore no exact architectural blocker to convergence was found. The only
unresolved item is runtime proof that a MANUAL plan can reach the approved branch
with usable market facts and complete the existing PAPER path.

## Position Lifecycle

The V1 daily workflow is sufficiently covered after an entry:

* account-scoped positions read persisted local PAPER trades;
* market valuation and explicit unavailable states are displayed;
* full exposure close is available through the normal UI;
* reload preserves the open or empty state;
* execution history can load the execution and linked TradePlan.

Partial close, stop modification, take-profit automation, active monitoring and
advanced analytics are not prerequisites for the first Paper Daily Driver.

## Daily-Driver UX Gaps

| Finding | Classification | Evidence |
| --- | --- | --- |
| Standalone manual form loses live workspace context | BLOCKER | Prior investigation; Story 0060 current worktree embeds the ticket to address it |
| Current accepted baseline has no manual-ticket action from the workspace | BLOCKER | Story 0057 explicitly excluded TradePlan creation; Story 0060 is uncommitted |
| Manual PlanPage -> risk -> execution action exists but has no manual runtime pass | HIGH_FRICTION | Shared `PlanPage`; no approved MANUAL fill evidence |
| Position route requires account context but preserves it through the existing link/query flow | MINOR | Story 0048 and `Positions` implementation |
| Market data can be STALE/UNAVAILABLE and correctly blocks risk | HIGH_FRICTION | Story 0060 runtime evidence; this is a product availability issue, not a Risk bypass target |
| Opportunity path does not yet converge into Decision Workspace | MINOR for this milestone | Opportunity path already works and manual daily usage does not require redesigning it |

No cosmetic work is included in the immediate path.

## Deferred Features

The following are intentionally not prerequisites for Paper Daily Driver V1:

* real AI Engine;
* News Service and economic calendar;
* Passive Scanner and advanced Active Scanner;
* position-monitoring agent;
* cTrader, FTMO and additional brokers;
* SaaS capabilities and distributed event infrastructure;
* advanced analytics;
* automatic trading;
* partial close, stop modification and take-profit automation.

## Critical Path

1. Human review and acceptance of the existing uncommitted Story 0060
   market-integrated MANUAL ticket, including preservation of unrelated worktree
   changes.
2. Implement and validate Story 0061: create a MANUAL plan from the official
   workspace, accept it, obtain an explicit deterministic Risk verdict, authorize
   execution only when approved, fill PAPER, reload, close and verify history.
3. If Story 0061 finds a concrete market-data or contract blocker, create the
   smallest follow-up Story for that blocker; do not weaken Risk or create a
   manual execution path.
4. Declare the Paper Daily Driver usable only after the complete official UI
   journey has runtime evidence and human review.

## Immediate Next Story

**Story 0061 - Validate the MANUAL PAPER Daily Driver**

Story 0059 should not be restarted as a duplicate implementation. Its current
scope is substantially represented by the shared risk flow and the provenance
compatibility changes. Story 0061 is the smallest vertical slice that converts
the current manual UI/backend capability into validated daily use.

## Validation Performed

* `git status --short`
* `git branch --show-current`
* `git log -20 --oneline`
* `git diff --check`
* Repository and story/ADR inspection across Stories 0048-0060.
* Targeted source inspection of Trading Core risk, execution, PAPER settlement,
  position close, Market Intelligence TradePlan handoff and Angular routes.
* DevLog project resolution and targeted history searches for Stories 0048,
  0057, 0058 and 0059.

No broad test suite or runtime journey was rerun because this task is an
investigation and the repository already contains current runtime evidence. The
worktree was preserved unchanged.

## PAPER DAILY DRIVER INVESTIGATION

CURRENT PAPER ENGINE:
Implemented and runtime-validated for the opportunity-origin journey. Manual
origin is implemented but its complete lifecycle is not yet runtime-validated.

DECISION WORKSPACE:
Account-first market context and market data are implemented. The current
worktree contains the market-integrated MANUAL ticket, pending human review.

MANUAL TRADE PLAN:
Implemented with canonical `MANUAL` provenance and authenticated ownership.
Creation has runtime evidence; full lifecycle does not.

MANUAL RISK:
Shared deterministic Risk path is implemented. Story 0059 is overtaken in part;
an approved MANUAL runtime verdict still needs validation.

MANUAL EXECUTION:
Shared Execution Intent and PAPER settlement paths are origin-neutral and
implemented. An approved MANUAL fill is not yet runtime-proven.

POSITION LIFECYCLE:
Implemented and runtime-validated for PAPER opportunity flow: valuation,
persistence, reload, full close and empty state. Manual-origin continuity needs
the Story 0061 journey.

DAILY-DRIVER UX:
The accepted baseline lacks a reviewed workspace-integrated manual ticket. The
current uncommitted Story 0060 addresses the blocker; downstream manual runtime
continuity remains to be proven.

ADVANCED FEATURES REQUIRED BEFORE PAPER USE:
NO

IMMEDIATE NEXT STORY:
0061 - Validate the MANUAL PAPER Daily Driver

WHY THIS STORY:
It closes the nearest end-to-end product gap using the existing TradePlan, Risk,
human authorization, Execution Intent, PAPER settlement, position and history
contracts without speculative architecture.

CRITICAL PATH:
1. Review and accept Story 0060.
2. Validate the complete official MANUAL PAPER lifecycle in Story 0061.
3. Create only narrowly evidenced blocker Stories, then declare daily-driver readiness.

READY TO IMPLEMENT NEXT STORY:
NO - Story 0060 requires human review first, and Story 0061 requires approval.

BLOCKERS:
Human review of the existing uncommitted Story 0060 changes; a runtime session
with usable current market valuation is required to prove an approved MANUAL
path.
