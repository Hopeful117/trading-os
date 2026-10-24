# Trading OS V1 Definition of Done

**Investigation date:** 2026-10-22
**Scope:** current repository product state and smallest coherent human-controlled V1
**Implementation performed:** none
**DevLog:** applicability check timed out; current repository, accepted ADRs, Stories, and runtime reports were used.

## 1. Repository Baseline

The repository is on `story/0143-operational-replay-artifact-population` at
`a707bb8`, with a clean worktree before report creation. `origin/main` is
`907a614`. The current source is newer than several older V1 investigations and
therefore supersedes stale statements where current code and accepted runtime
evidence disagree.

The most important discrepancy is PAPER lifecycle status: the README still says
close/settlement remain to be built, while Story 0048 and the 2026-09-23 product
investigation record a successful official opportunity-origin PAPER open, reload,
close, reload, and history path. Current source also contains explicit PAPER close
and settlement services. The remaining gap is durable post-trade product history,
not the existence of a close command.

## 2. Product Mission and Authority Model

The repository supports this mission:

> Trading OS is a human-controlled trading workstation that centralizes market
> evidence, account context, deterministic analysis, risk validation, explicit
> execution, position handling, and review of what happened. It is not an
> autonomous trading bot.

The non-negotiable chain is:

```text
market evidence
  -> deterministic/analytical assistance
  -> human plan decision
  -> deterministic Risk authority
  -> explicit human execution authorization
  -> Trading Core execution state
  -> broker/PAPER authority
```

AI, scanners, frontend state, and Market Intelligence cannot approve or execute
orders.

## 3. Architecture Map

| Service/module | Classification | Current role |
|---|---|---|
| Gateway | V1_REQUIRED | JWT boundary, public REST prefixes, WebSocket routing |
| Trading Core | V1_REQUIRED | users, accounts, TradePlans, Risk orchestration, execution, PAPER state, positions, close, outcomes |
| Broker Service | V1_REQUIRED for account/LIVE foundations | credentials, Kraken adapter, broker-neutral account/order contracts, reconciliation, fee evidence |
| Market Data | V1_REQUIRED | market catalogue, ticker, OHLC, Order Book, recent trades, freshness and valuation facts |
| Market Intelligence | V1_REQUIRED | deterministic context, active scan, opportunities, TradePlans, provenance, research |
| News Service | V1_SUPPORTING | economic context foundation; real financial-news provider is gated/disabled |
| Trading OS Web | V1_REQUIRED | authenticated workstation and human workflow |
| `research/` Python | V1_SUPPORTING | validation/experimentation helpers; not required for product operation |
| Eureka/PostgreSQL/Docker | V1_REQUIRED supporting infrastructure | discovery, persistence, local reproducible deployment |
| AI Engine | POST_V1 | ports/disabled adapter only; no real engine |

## 4. Capability Inventory

| Capability | Current state | V1 requirement | Gap | Blocking? |
|---|---|---|---|---|
| Authentication/JWT/ownership | FUNCTIONAL | required | deployed matrix and hardening remain | P1 |
| Account and PAPER provisioning | FUNCTIONAL | required | runtime evidence exists; keep profile setup clear | P2 |
| Kraken account connectivity | FUNCTIONAL | supporting | sandbox/deployed write proof incomplete | not for PAPER V1 |
| Market catalogue/tradability | FUNCTIONAL | required | market state presentation can improve | P1 |
| Ticker/OHLC/chart | FUNCTIONAL | required | explicit stale/unavailable state must remain | P2 |
| Order Book/recent trades | FUNCTIONAL | desirable | runtime reconnect/latency residuals | P2 |
| Deterministic Trend Context | FUNCTIONAL/VALIDATED | desirable | provenance presentation continuity | P1 |
| Active scanner | FUNCTIONAL | desirable | bounded scan UX and provenance polish | P1 |
| Passive scanner | NOT_STARTED as user-facing capability | not required | defer recurring awareness product | POST-V1 |
| Opportunities | FUNCTIONAL | desirable | filtering, expiry, source navigation | P1 |
| Decision Workspace | FUNCTIONAL | required | validate manual path and cross-screen continuity | P1 |
| Manual/opportunity TradePlan | FUNCTIONAL | required | manual approved runtime proof remains limited | P1 |
| Deterministic Risk | FUNCTIONAL/VALIDATED | required | profile UX and runtime matrix | P0 if bypassed; otherwise P1 |
| Human execution authorization | FUNCTIONAL in source/UI | required | complete official runtime evidence for all intended paths | P1 |
| PAPER entry/fill | FUNCTIONAL and runtime-proven for opportunity path | required | manual path runtime proof | P1 |
| PAPER position valuation | FUNCTIONAL | required | freshness/unavailable states | P2 |
| PAPER close/settlement | FUNCTIONAL and runtime-proven for validated path | required | durable close association for history | P1 |
| Position monitoring | PARTIAL, read-only | minimum required | current position, valuation, close exists; no recommendation agent needed | P1 |
| Execution history | FUNCTIONAL | supporting | currently execution-centric rather than complete decision history | P1 |
| Trade outcomes | FOUNDATIONAL backend | required for durable feedback | frontend and complete close association incomplete | P0 |
| Analytics | PARTIAL | minimal read-only review only | `/api/analytics` is legacy statistics; frontend is execution history | P1 |
| Economic context | FUNCTIONAL foundation | desirable | provider freshness/configuration | P2 |
| Financial news | PARTIAL/disabled by default | not required | provider authorization and implementation unresolved | POST-V1 |
| AI | NOT_STARTED | optional | no real engine | POST-V1 |
| LIVE execution | FOUNDATIONAL, not fully runtime-proven | not required for first V1 | sandbox/deployed validation and operational hardening | POST-V1 |
| Prop firms/cTrader | FOUNDATIONAL/partial | not required | provider-specific scope | POST-V1 |
| S1 research collection | FUNCTIONAL but operationally degraded | required as background support | retention completion and long-window proof | P1 operational |

## 5. Current User Journey

| Transition | Current evidence | V1 status |
|---|---|---|
| login -> account | Angular routes and JWT APIs | available |
| account -> market awareness | dashboard, account selector, market catalogue | available |
| market -> investigate | chart, ticker, Order Book, trades, Trend Context, workspace | available with provenance/state gaps |
| investigate -> opportunity | active scan and opportunity detail | available |
| opportunity/manual -> TradePlan | both backend/UI paths exist | available; manual runtime proof incomplete |
| TradePlan -> human accept/reject | PlanPage and decision controller | available |
| accepted plan -> Risk | deterministic, fail-closed Risk path | available |
| approved Risk -> explicit execution | `/api/v1/executions/validate`, then execute; UI flow exists | available in source; runtime proof must remain current |
| PAPER execution -> position | persisted local Trade and position query | available and validated for opportunity path |
| position -> monitor/value | account-scoped positions page with 10-second refresh | available minimum |
| position -> close/settlement | explicit PAPER close endpoint and UI | available and validated for the accepted path |
| closed position -> learn | execution history and backend outcomes exist, but no unified durable decision history view | missing V1 slice |

## 6. Smallest Coherent V1 Loop

```text
ORIENT
  -> INVESTIGATE
  -> DECIDE
  -> VALIDATE RISK
  -> AUTHORIZE AND EXECUTE PAPER
  -> MONITOR
  -> CLOSE
  -> REVIEW THE AUTHORITATIVE RESULT
```

This is smaller than a full AI trading platform and valuable without profitable
S1 results. It ends in durable feedback, not merely an execution confirmation.

### Orient and Investigate

The user can select an account, browse markets, inspect tradability and current
market facts, and open a Decision Workspace. Market detail and workspace provide
the core evidence. The remaining V1 requirement is coherent freshness,
provenance, stale, unavailable, and blocked state presentation across the same
workflow, not another global frontend redesign.

### Decide and Risk

Manual and opportunity TradePlans converge on the same canonical model. Human
acceptance/rejection is explicit. Trading Core/Risk owns deterministic evaluation,
immutable snapshots, and fail-closed behavior. The frontend must render the
authoritative result and never recompute or soften it.

### Execute and PAPER Lifecycle

The repository now includes the explicit execution path, ownership checks,
idempotency, revalidation, execution attempt state, PAPER settlement, persisted
positions, valuation, and explicit close. The complete opportunity-origin PAPER
journey has runtime evidence. A MANUAL-origin journey should still receive a
fresh official runtime acceptance because existing evidence identifies that as a
validation gap, not an architectural need for a second path.

### Review and Feedback

Execution history currently links executions to TradePlans where possible. The
backend also has `TradeOutcome` contracts, but the product cannot yet answer in
one account-scoped history item: what was seen, what was proposed, what Risk
decided, what happened, what position resulted, and how it closed. This is the
highest-value remaining V1 capability.

## 7. Opportunities and Scanners

Opportunities are usable for discovery and link to TradePlan preparation. V1 only
needs bounded discovery, expiry, market identity, provenance, and a route into
the workspace/plan. AI ranking is not required.

Active Scanner is V1-desirable rather than a P0 dependency: it is user-triggered,
bounded, deterministic, and already present. Hardening can continue without
changing the decision chain.

The passive S1 research lifecycle is not the same thing as a user-facing passive
scanner. A recurring passive market-awareness scanner should remain POST-V1 until
the product has evidence about the desired awareness workflow.

## 8. News, AI, LIVE, and Provider Scope

- Economic context is a useful supporting capability with explicit unavailable/stale states.
- A financial-news provider is not a V1 blocker; Story 0103B remains gated on authorization and data-use decisions.
- AI is optional for V1 and should remain POST-V1 until deterministic evidence, provenance, and outcome feedback are mature.
- LIVE trading is not V1-required. APIs and broker foundations do not equal production-safe runtime readiness.
- Kraken market data and PAPER-compatible account/product foundations support V1, but provider-specific semantics must remain behind broker-neutral boundaries.
- cTrader, FTMO, and other prop-firm integrations are V1.1 or POST-V1, not assumptions of the primary product.

## 9. Frontend Workstation

The current Angular application has authenticated dashboard, account, market,
opportunity, Decision Workspace, planning, positions, execution-history,
economic-context, financial-news, and research routes. It uses Observables and
async-pipe composition in the affected workstation surfaces. Recent workstation
Stories provide shared context-strip/panel primitives.

Another global redesign is not justified. V1 frontend work should be narrow:

- preserve account and market context across investigation, plan, position, and history;
- expose provenance and freshness without implying analytical approval;
- distinguish loading, empty, stale, unavailable, error, blocked, and uncertain states;
- add the decision-history surface rather than a second analytics architecture.

## 10. Operations, Security, and Testing Baseline

### Operations

V1 requires independent services to start, Flyway migrations to work, authenticated
PAPER persistence to survive restart, and no uncontrolled storage growth on the
product path. S1 retention is an operational residual, but it must not be hidden
behind a product readiness claim. Useful health diagnostics and reproducible
Docker deployment are sufficient; SaaS-grade HA is not.

### Security

The minimum is JWT validation, account/position/outcome ownership, encrypted broker
credentials, provider payload isolation, service authentication, idempotent writes,
and explicit human execution authorization. LIVE production hardening, distributed
rate limiting, and HA discovery are separate follow-up work.

### Testing

The V1 gate is evidence-based, not an arbitrary global percentage:

- deterministic Risk nominal, boundary, unavailable, and refusal tests;
- ownership and service-authentication tests for account, execution, position, close, and history;
- persistence/reload tests for PAPER entry, position, close, and outcome association;
- one official UI/runtime PAPER journey from accepted plan through close and review;
- unknown/uncertain broker execution remains uncertain and reconcilable;
- affected Angular tests and production build;
- independent Maven tests for Trading Core, Market Intelligence, Market Data, Broker Service, Gateway, and relevant cross-service security;
- `git diff --check` and migration/startup validation.

## 11. V1 Non-Goals

- autonomous trading or AI execution authority;
- real AI Engine, RAG, embeddings, ML strategy generation, or agent runtime;
- S1 economic evaluation, profitable strategy claims, or completed research results;
- passive autonomous scanner;
- advanced position agent, automatic stops, or proactive trade mutation;
- full financial-news intelligence provider unless separately authorized;
- cTrader, FTMO coupling, or multiple broker expansion;
- mature portfolio analytics, Sharpe/optimization, or strategy optimization;
- SaaS commercialization, distributed event infrastructure, HA Eureka, and mobile-first redesign;
- Bloomberg-style terminal customization or plugin ecosystem.

## 12. P0/P1/P2/Post-V1 Gap Classification

### P0 - V1 blockers

- No authoritative, account-scoped post-trade history that joins plan, Risk,
  execution, position, close, and outcome facts.
- No truthful outcome semantics for missing close association or unavailable
  realized result. The product must not display an implied result.

### P1 - Important

- Fresh official runtime proof for the MANUAL PAPER path.
- Decision Workspace to history/provenance continuity.
- Explicit market tradability/freshness state in the decision flow.
- S1 storage/retention remediation for safe unattended collection.
- Runtime security and persistence matrix across the V1 path.
- Opportunity filters/expiry/source navigation hardening.

### P2 - Polish

- unified labels and stale-state presentation;
- richer read-only execution/trade statistics after authoritative outcomes exist;
- broader frontend error/reconnect affordances;
- account recovery actions and operational diagnostics in the UI.

### POST-V1

- real AI, passive scanner, advanced monitoring, LIVE production readiness,
  provider expansion, prop-firm coupling, advanced analytics, and SaaS concerns.

## 13. Testable Definition of Done

### Account and Context

- An authenticated user can select an owned PAPER account.
- Account context is preserved through market investigation, plan, execution,
  positions, close, and history links.
- Ownership failures are explicit and do not reveal another account's facts.

### Market Investigation

- The user can inspect a tradable market with authoritative fresh ticker/chart
  context and explicit stale/unavailable states.
- Decision Workspace shows the relevant deterministic context and provenance or an
  explicit unavailable state.

### Decision and Risk

- The user can create or derive a canonical TradePlan.
- Plan origin and source provenance are preserved; MANUAL is not represented as a
  fake Opportunity.
- The user explicitly accepts or rejects the plan.
- Approved/rejected Risk is persisted, deterministic, explainable, and fail-closed.
- The frontend never converts Risk rejection or unavailable facts into approval.

### PAPER Execution

- An approved plan requires a separate explicit human execution action.
- Execution Intent, attempts, idempotency, and final/uncertain state persist.
- A rejected or unavailable Risk result creates no execution intent.
- PAPER fill/settlement persists the resulting position and survives reload.

### Position and Close

- The current PAPER position is queryable with entry, quantity, current value,
  freshness, and explicit unavailable valuation state.
- The user can explicitly close the PAPER exposure.
- Close and settlement persist correctly; after reload the closed position is not
  presented as open.
- LIVE positions remain broker-authoritative and are not silently treated as PAPER.

### History and Feedback

- A read-only account-scoped history item reconstructs the original evidence or
  explicit missing provenance, Opportunity/MANUAL origin, TradePlan version, Risk
  decision, execution state, position lifecycle, close state, and authoritative
  outcome when available.
- Uncertain execution and unavailable realized facts remain explicit, not zero or
  false success.
- A PAPER journey can be reviewed after restart without copying internal IDs between
  screens.

### Research and Operations

- S1 collection can continue in the background with fresh persistence, progressing
  snapshots/outcomes, bounded trusted replay, safe retention, and storage warning or
  fail-before-full behavior.
- S1 experimental readiness and economic results are explicitly not required for
  product V1.
- Services, migrations, authentication, persistence/reload, critical security
  boundaries, and the official PAPER journey are validated in the supported local
  deployment.

## 14. DoD Exclusions

| Required | Explicitly not required |
|---|---|
| PAPER execution | LIVE production trading |
| explicit human authorization | autonomous execution |
| deterministic Risk | AI-generated authority |
| complete close/review lineage | profitable strategy or S1 results |
| read-only outcome review | advanced analytics or optimization |
| market freshness/provenance | full news-provider coverage |
| safe background S1 collection | experimental readiness thresholds |
| one Kraken-backed product path | cTrader/FTMO/multi-broker expansion |

## 15. Candidate Remaining Stories

### Story A - Close S1 Unattended Collection Gate

**Purpose:** complete and validate replay/retention runtime behavior without
changing S1 semantics. **Why V1 needs it:** it prevents background research from
consuming unsafe storage. **Dependencies:** current Story 0143 and ADR-067/069.
**Scope:** bounded replay proof, artifact/reuse metrics, dry-run and reviewed
retirement execution, storage pressure validation. **User-visible result:** honest
Research health and safe unattended accumulation.

### Story B - Validate the MANUAL PAPER Daily Driver

**Purpose:** prove the existing canonical manual path from workspace to accepted
Risk, PAPER execution, reload, close, and history. **Why V1 needs it:** the primary
human workflow must be proven for both manual and opportunity origins.
**Dependencies:** current Decision Workspace, TradePlan, Risk, and execution path.
**Scope:** runtime acceptance and smallest contract/UI fixes only if evidence proves
them necessary. **User-visible result:** a trader can use the workstation without
an opportunity-specific workaround.

### Story C - Post-trade Decision History V1

**Purpose:** provide one durable account-scoped read-only lineage from evidence and
plan through Risk, execution, position, close, and outcome. **Why V1 needs it:** it
is the missing feedback loop and the current highest-value blocker.
**Dependencies:** Story B evidence and the existing authoritative identifiers;
ADR required if a new close-association persistence model is introduced.
**Scope:** contract, provenance/unavailable semantics, backend projection, Angular
history/detail view, and persistence/reload tests. **User-visible result:** the
trader can understand what happened without reconstructing it across screens.

### Story D - V1 Operational and Workstation Closure

**Purpose:** close only the remaining P1 continuity, freshness, security, and
frontend state gaps discovered by A-C. **Why V1 needs it:** it turns a proven
vertical slice into a repeatable workstation. **Dependencies:** A-C findings.
**Scope:** targeted diagnostics, state semantics, migration/startup and runtime
validation; no global redesign. **User-visible result:** clear, trustworthy
operation under stale, unavailable, uncertain, and restart conditions.

## 16. Critical Path and Parallel Work

```text
current repository
  -> Story A: S1 operational gate (parallel)
  -> Story B: manual PAPER runtime validation
  -> Story C: post-trade Decision History V1
  -> Story D: targeted V1 closure
  -> Trading OS V1
```

Story A can proceed independently of product work. Stories B and C can proceed
while S1 accumulates, provided the runtime gate remains visible and no source
changes alter S1 semantics. Frontend continuity, contract tests, migration tests,
and read-only history composition can be developed in parallel after their
authoritative data contracts are approved.

Work that should wait: real AI, passive scanning, advanced position intelligence,
LIVE production claims, broker expansion, advanced analytics, and commercialization.

## 17. Architecture Debt Classification

| Issue | Classification | Reason |
|---|---|---|
| S1 retention not proven complete | V1_BLOCKER for unattended research, not product-loop blocker | storage safety is operationally material |
| readiness calls still slow | ACCEPTABLE_V1_DEBT with S1 residual | research diagnostics need bounded latency; product PAPER loop does not depend on readiness |
| in-memory selected MI artifacts | ACCEPTABLE_V1_DEBT | do not make durable research memory a prerequisite for first workstation V1 |
| legacy `/api/analytics` contract | POST_V1_REFACTOR | do not broaden history scope before authoritative outcome contract |
| no real AI Engine | POST_V1_REFACTOR/capability | not needed for deterministic human-controlled V1 |
| no HA/distributed rate limiting | POST_V1 | production hardening beyond local V1 |

## 18. Verdict

```text
V1_SCOPE_CLEAR
TRADING OS V1 MATURITY = LATE_V1
```

The core human-controlled PAPER loop exists in the repository and has runtime
evidence for the opportunity-origin path. The remaining product boundary is
durable post-trade decision history and a fresh manual-path runtime proof, not a
new AI or scanner architecture.

```text
POST-TRADE DECISION HISTORY = STILL_NEXT
```

PAPER close/settlement precedes the complete history claim: history can expose
open or unavailable states, but it cannot truthfully claim a completed realized
outcome until the close association is authoritative. The close implementation
exists; the history projection should consume it rather than reinvent settlement.

**Exact next product Story:** `Post-trade Decision History V1`, after the MANUAL
PAPER runtime acceptance is either completed or recorded as a bounded validation
dependency. S1 operational remediation remains a parallel operational Story and
does not redefine V1 scope.
