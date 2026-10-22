# Trading OS V1 Acceptance

**Review date:** 2026-10-22
**Scope:** Trading OS V1 Operational & Workstation Closure
**Repository:** `/home/ludo/workspace/trading-os`
**DevLog:** unavailable; the single applicability check failed with provider quota exhaustion (`429 insufficient_quota`). Repository evidence, accepted reports, ADRs, Stories and tests were used instead.

## 1. Repository Baseline

| Item | Value |
|---|---|
| Branch | `main` |
| HEAD | `e6b5b785` (`Merge pull request #161 ... post-trade-decision-history-v1`) |
| `origin/main` | `e6b5b785` |
| Working tree before closure | Clean apart from three pre-existing untracked reports |
| Latest merged product Story | Post-trade Decision History V1 |
| Manual PAPER evidence | Story 0061, human accepted |
| Decision History ADR | ADR-070, Accepted |
| Current schema | Flyway migration V33 |
| Untracked files preserved | `s1-overnight-operational-health.md`, `s1-to-v1-development-gate.md`, `trading-os-v1-definition-of-done.md` |

The three pre-existing S1/V1 reports were not modified. The current repository is
authoritative over older wording in those reports and in the README.

## 2. V1 Definition

Trading OS V1 is a human-controlled PAPER trading workstation. It is not a claim
of live-trading readiness, profitable strategy performance, completed S1 research,
AI readiness, prop-firm readiness or SaaS readiness.

The accepted authority chain is:

```text
market evidence
  -> deterministic/analytical assistance
  -> human plan decision
  -> deterministic Risk authority
  -> explicit human execution authorization
  -> Trading Core execution state
  -> PAPER authority
  -> durable position and post-trade review
```

## 3. Architecture Map

| Capability | Backend owner/API | Frontend surface | Persistence/evidence | Acceptance |
|---|---|---|---|---|
| Authentication | Gateway JWT; Trading Core user login | `/login`, auth guard | JWT and security tests | VALIDATED |
| Account context | Trading Core `/api/v1/accounts` | Accounts, dashboard and account-aware workspace | Account ownership tests; Story 0061 | VALIDATED |
| Market investigation | Market Data catalogue, ticker, OHLC, order book and trades | `/markets`, `/markets/:marketId` | Market-data tests and accepted runtime evidence | VALIDATED |
| Deterministic intelligence | Market Intelligence analyses, context and opportunities | Decision Workspace and opportunity routes | MI tests and provenance contracts | VALIDATED |
| MANUAL TradePlan | Trading Core and Market Intelligence manual plan APIs | `/trade-planning/manual` | TradePlan persistence and Story 0061 | VALIDATED |
| Human plan decision | TradePlan decision endpoint | Plan page actions | Lifecycle tests | VALIDATED |
| Deterministic Risk | Risk Domain and Trading Core Risk orchestration | Plan/Risk UI | Immutable evaluations and fail-closed tests | VALIDATED |
| Explicit execution authorization | `/api/v1/executions/validate`, then `/execute` | Plan page execution action | Intent and attempt persistence | VALIDATED |
| PAPER execution | Trading Core simulated adapter and routing | Plan page | PAPER order, Trade and settlement | VALIDATED |
| Position lifecycle | Trading Core positions and close endpoints | `/positions` | Local `Trade` persistence and reload | VALIDATED |
| Decision History | `/api/v1/accounts/{accountId}/decision-history` | `/analytics`, post-trade decision section | `TradeOutcome.trade_id`, V33, authoritative Trade/exit links | VALIDATED after bounded correction |
| Research status | Market Intelligence research endpoints | `/research` | Explicit research states | RESIDUAL / non-blocking |

## 4. Capability Matrix

| Capability | Required | Implemented | Tested | Runtime proven | Status |
|---|---:|---:|---:|---:|---|
| Authentication | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Account context | YES | YES | YES | YES, Story 0061 | VALIDATED |
| PAPER account | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Market catalog | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Market tradability | YES | YES | YES | YES, accepted market evidence | VALIDATED |
| Ticker/OHLC | YES | YES | YES | YES, with freshness limitation recorded | VALIDATED |
| Deterministic market analysis | YES | YES | YES | YES, accepted MI evidence | VALIDATED |
| Decision Workspace | YES | YES | YES | YES, Story 0061 | VALIDATED |
| MANUAL TradePlan | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Opportunity TradePlan | desirable | YES | YES | YES, prior PAPER evidence | VALIDATED / desirable |
| Human plan acceptance | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Deterministic Risk | YES | YES | YES | YES, Story 0061 and regression suites | VALIDATED |
| Explicit execution authorization | YES | YES | YES | YES, Story 0061 | VALIDATED |
| PAPER execution | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Execution idempotency | YES | YES | YES | YES in tests and accepted path | VALIDATED |
| Position persistence | YES | YES | YES | YES after reload, Story 0061 | VALIDATED |
| Position reload | YES | YES | YES | YES, Story 0061 | VALIDATED |
| PAPER close | YES | YES | YES | YES, Story 0061 | VALIDATED |
| Settlement | YES | YES | YES | YES, Story 0061 and Trading Core tests | VALIDATED |
| Post-trade Decision History | YES | YES | YES | YES through persisted V33 projection | VALIDATED |
| Missing/uncertain truth semantics | YES | YES | YES | YES through explicit unavailable/uncertain states | VALIDATED |
| Account ownership | YES | YES | YES | YES in source and ownership tests | VALIDATED |
| S1 safe background collection | residual | PARTIAL | PARTIAL | NO long-window proof | INCONCLUSIVE residual |

## 5. Accepted Prior Evidence

Story 0061 is accepted evidence and was not rerun with a new PAPER mutation.
Its official authenticated Web journey recorded:

```text
PAPER account
  -> market and Decision Workspace
  -> MANUAL TradePlan
  -> human acceptance
  -> deterministic Risk approval
  -> explicit execution authorization
  -> PAPER fill
  -> persisted position
  -> reload
  -> full close
  -> reload
  -> history continuity
```

The recorded result included a MANUAL plan, accepted version, Risk evaluation,
execution intent, simulated order, persisted PAPER Trade and close execution.
The journey used no direct database mutation, hidden endpoint, manual HTTP bridge
or direct broker call.

Post-trade Decision History was merged with ADR-070 and migration V33. The
association is nullable and durable. Existing records without deterministic
association remain unavailable rather than being heuristically matched.

## 6. Human-Control Invariants

The following invariants remain enforced by source and tests:

- Risk approval is not execution authorization. Execution intent validation
  requires a completed approved Risk evaluation.
- Opportunity detection is not execution authorization. Opportunity and active
  scan paths converge on TradePlan and Risk services.
- TradePlan acceptance is not execution. A separate explicit execution action is
  required before an Execution Intent is created or submitted.
- Angular does not calculate Risk or override Risk rejection/unavailability.
- Unknown execution outcomes remain explicit and reconcilable.
- PAPER routing is selected from the persisted broker-account execution mode,
  not from a frontend-only flag.

## 7. PAPER Safety

The accepted PAPER path is backend-authoritative:

- the account has persisted `ExecutionMode.PAPER`;
- `RoutingBrokerExecutionAdapter` selects the simulated adapter only for PAPER;
- `SimulatedExecutionAdapter` rejects non-PAPER accounts;
- settlement and close services reject execution-mode mismatches;
- LIVE execution remains a separate broker-authoritative path and was not used
  for the accepted V1 evidence.

No accidental LIVE fallback was identified.

## 8. Ownership and Security

- Gateway and user-facing services require JWT authentication for protected
  routes.
- Account-scoped endpoints derive the actor from the authenticated principal.
- TradePlan, execution, position, close and Decision History paths validate
  account or actor ownership.
- Decision History verifies the requested account before projecting facts.
- Foreign Trade lineage is hidden instead of being exposed through an account
  history response.
- Internal service calls use configured service JWTs; no `permitAll` workaround
  was introduced.
- Broker credentials remain encrypted and provider payloads remain inside broker
  infrastructure.

## 9. Persistence and Historical Truth

The V33 association is the approved durable relationship:

```text
trade_outcome.trade_id -> trades.trade_id
```

The Decision History projection now also exposes, without inference:

- canonical MANUAL/OPPORTUNITY classification loaded from the authoritative
  TradePlan origin, or `UNAVAILABLE` when that origin cannot be proven;
- persisted provenance items;
- account ID and account name on every history item;
- the authoritative PAPER Trade when the association belongs to the requested
  account;
- the latest EXIT intent targeting that Trade, including its status;
- explicit unavailable values when those facts cannot be proven.

The close lookup uses `ExecutionIntent.targetTradeId` and account compatibility.
It does not match by symbol, price, quantity, timestamp, broker order or broker
position reference.

The frontend now provides an expandable read-only Decision History detail that
shows origin, Risk, entry execution, position, close execution and result state.
It keeps unavailable and uncertain facts explicit.

## 10. Workstation and Navigation

The primary surfaces are discoverable through authenticated navigation:

```text
Markets
  -> Decision Workspace
  -> Trade Planning / Plan
  -> Positions
  -> Trade History / Decision History
```

The existing execution-focused view remains intact. Decision History is exposed
within the existing Trade History surface rather than creating a competing
analytics architecture.

Loading, empty and error states are distinct for executions and Decision History.
Decision cards can be expanded without copying internal identifiers between
screens. The correction preserves the existing reactive Angular architecture.

## 11. Runtime and Operations

Accepted local runtime evidence proves the MANUAL PAPER lifecycle and post-trade
review path. This closure did not create a new PAPER trade because Story 0061
already provides accepted mutation evidence.

Repository-standard startup and persistence checks are covered by service tests,
Flyway migration tests and the accepted local Docker evidence. Compose requires
the configured JWT and service secrets rather than silently starting with unsafe
defaults.

Known non-blocking warnings remain:

- Angular bundle and component style budgets are exceeded.
- Test runs emit existing Mockito, H2, Hibernate and Spring documentation
  warnings.
- `npm ci` reports 35 dependency vulnerabilities (11 moderate, 21 high,
  3 critical); dependency remediation is outside this bounded closure scope.
- SonarQube was available. `risk-domain` and `eureka-server` passed, but the
  scan stopped at the `gateway` Quality Gate because of one pre-existing new-code
  issue (`java:S5976` in `GatewayMarketIntelligenceRouteTest`, created before
  this Story). No Gateway file is part of this closure diff, so this was not
  corrected as unrelated technical debt.

## 12. S1 Operational Residual

The latest unattended-collection report records healthy acquisition and storage
pressure false at its captured checkpoints, but does not prove the requested
T+2h/T+4h/T+8h window, real retirement, replay after retirement or provider
recovery.

Classification for this closure:

```text
S1 BACKGROUND STATUS = INCONCLUSIVE
```

This remains an operational residual, not a V1 product-loop blocker. The current
evidence does not show that S1 destabilizes normal PAPER workstation use, and S1
readiness/economic thresholds are explicitly outside V1 acceptance.

## 13. Tests and Builds

Executed after the bounded correction:

- `trading-core`: `DecisionHistoryServiceTest`, 2 tests passed.
- Angular Decision History/execution-history suite: 14 tests passed.
- Angular Prettier check: passed.
- `./scripts/quality-verify.sh`: passed.
- Backend coverage gate: passed for all business modules; Trading Core line
  coverage was `85.58%`.
- Frontend coverage: 507 tests passed; line coverage `84.15%`.
- Angular production build: passed with existing budget warnings.
- `git diff --check`: passed.

SonarQube was applicable but incomplete for this Story: its scan stopped at the
pre-existing Gateway Quality Gate failure above before the affected Trading Core
analysis. This is recorded as a validation limitation, not as a successful
SonarQube result.

Previously accepted evidence also includes Story 0061's Trading Core, Market
Intelligence and Angular suites plus its official authenticated runtime journey.

## 14. Defects Found and Fixes Applied

### Confirmed V1 blockers, resolved

Independent review also found that origin was incorrectly inferred from empty
provenance and that the multi-account UI flattened histories without displaying
the account context. Both were corrected without changing the execution or Risk
authorities.

### Bounded fix

- Extended the account-scoped Decision History DTO with origin, provenance, close
  execution identity, close execution status and account context.
- Loaded origin from the authoritative TradePlan contract; unavailable TradePlan
  origin is exposed as `UNAVAILABLE`, never inferred from an empty provenance list.
- Added deterministic close lookup through `targetTradeId`.
- Added an expandable read-only Decision History detail in the existing history
  page.
- Added regression coverage for origin unavailability, explicit MANUAL origin,
  foreign lineage protection, deterministic close discovery and multi-account
  account-context rendering.
- Reconciled stale README lifecycle and roadmap statements.

No new persistence relationship, execution pipeline, Risk authority or broker
behavior was introduced.

## 15. V1 Residual Register

| Residual | Classification | Impact | Required before V1? | Recommended timing |
|---|---|---|---:|---|
| S1 T+2h/T+4h/T+8h and retirement window not proven | RESEARCH / operational residual | Background research status remains inconclusive | No | Continue separately with monitoring |
| Angular bundle/style budgets | V1_RESIDUAL | Build warnings, no workflow failure | No | Operational hardening |
| npm dependency vulnerabilities | V1_RESIDUAL | Dependency maintenance risk | No for this closure | Dedicated dependency review |
| Pre-existing Gateway SonarQube violation | V1_RESIDUAL / unrelated quality debt | Gateway Quality Gate remains red | No for this closure | Dedicated Gateway cleanup |
| LIVE sandbox/deployed validation | POST_V1 | Does not affect PAPER V1 | No | Separate LIVE readiness work |
| No real AI Engine | POST_V1 | Deterministic workstation remains usable | No | AI and analytics foundation |
| Passive scanner and advanced position monitoring | POST_V1 | Not required for the complete manual loop | No | Product evidence first |
| Old records without deterministic Trade association | V1_RESIDUAL | Explicit unavailable lineage | No | Preserve truth; no heuristic backfill |

## 16. V1.1 and Post-V1 Candidates

The next phase should be selected deliberately rather than started automatically.
Evidence supports this order:

1. Real-world V1 PAPER usage and operational hardening.
2. S1 retention/replay continuation with the missing long-window evidence.
3. LIVE/prop-firm/cTrader investigation only after PAPER usage establishes the
   required operational contracts.
4. Trading Intelligence improvements, then AI and Analytics foundations.

## 17. Independent V1 Review

The final review rechecked the closure against the requested focus:

- Human control: preserved.
- Risk authority: deterministic, persisted and fail-closed.
- PAPER safety: persisted execution mode and backend routing enforced.
- Ownership: account-scoped resources and Decision History protected.
- Historical truth: no heuristic lineage, P&L or close inference.
- Persistence: TradeOutcome association, Trade lifecycle and migrations covered.
- Uncertainty: execution and unavailable result states remain explicit.
- Navigation: existing workstation surfaces remain coherent.
- Operational stability: repository application quality gates and accepted runtime
  evidence pass; the pre-existing Gateway SonarQube issue is recorded separately,
  and S1 remains explicitly inconclusive rather than overstated.

No V1 architecture blocker was found.

## 18. Final Verdict

The user can use Trading OS as a human-controlled PAPER workstation from market
investigation through deterministic Risk, explicit authorization, persisted
position, close/settlement and durable post-trade review without bypassing Risk
or manually correlating internal identifiers.

```text
TRADING_OS_V1_ACCEPTED_WITH_RESIDUALS
```

## 19. Final Summary

AUTHENTICATION:
VALIDATED

ACCOUNT CONTEXT:
VALIDATED

MARKET WORKSTATION:
VALIDATED

DECISION WORKSPACE:
VALIDATED

MANUAL TRADEPLAN:
VALIDATED

DETERMINISTIC RISK:
VALIDATED

HUMAN EXECUTION AUTHORIZATION:
VALIDATED

PAPER EXECUTION:
VALIDATED

POSITION LIFECYCLE:
VALIDATED

PAPER CLOSE/SETTLEMENT:
VALIDATED

POST-TRADE DECISION HISTORY:
VALIDATED

HISTORICAL TRUTH:
VALIDATED

OWNERSHIP/SECURITY:
VALIDATED

WORKSTATION NAVIGATION:
VALIDATED

OPERATIONAL STARTUP:
VALIDATED

S1 BACKGROUND STATUS:
INCONCLUSIVE

V1 BLOCKERS:
none

V1 RESIDUALS:
S1 long-window evidence; Angular budget warnings; npm dependency vulnerabilities; pre-existing Gateway SonarQube violation; unavailable lineage for historical records without deterministic association

PRODUCT ACCEPTANCE:
YES_WITH_RESIDUALS

TRADING OS V1:
ACCEPTED_WITH_RESIDUALS

STORY VERDICT:
TRADING_OS_V1_ACCEPTED_WITH_RESIDUALS

RECOMMENDED NEXT PHASE:
real-world V1 PAPER usage with operational hardening

NEXT ACTION:
Run a bounded real-world PAPER usage review and separately capture the pending S1 operational checkpoints without creating additional product scope.
