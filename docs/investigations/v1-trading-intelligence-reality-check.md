# Investigation — V1 Trading Intelligence Reality Check

## Status

COMPLETE

## Executive Summary

Trading OS has made substantial progress since its last resumption investigation. The V1 frontier now extends through scan triggering, opportunity creation, trade-plan preparation, plan accept/reject, and deterministic risk evaluation — all surfaced in the Angular frontend and publicly routed through the Gateway. However, the product cannot yet help a user complete a trading decision because it stops exactly at the risk result: there is no execution intent, no execution action, no Gateway route for executions, and no order submission. The user can see opportunities and risk decisions but cannot act on them.

The critical gap is not infrastructure — it is the missing vertical slice from approved risk result to explicit human execution action to controlled broker order. Closing this slice would transform Trading OS from a market-analysis demo into a tool that materially helps a real trader.

## Product Vision Boundary

**V1 — Trading Intelligence OS** is the current priority. Its purpose is to become a genuinely useful trading assistant: market data → deterministic analysis → opportunity → trade plan → risk evaluation → human decision → execution.

**V2 — Quant Research & Trading OS** (backtesting, strategy evaluation, feature engineering, portfolio risk) is NOT the current implementation target.

Scope heuristic from AGENTS.md: useful to almost every trader → V1. Primarily required for systematic quant research → V2.

## Method

1. DevLog MCP: engineering context + project history + freshness
2. Git safety check (status, branch, worktrees, log, fetch)
3. Repository archaeology via task agents (trading-core, risk-domain, broker-service, market-data, market-intelligence, frontend, gateway, docs/ADRs)
4. End-to-end user journey trace through code
5. Capability matrix construction
6. Next-slice ranking and recommendation

## DevLog Context & Freshness

- **Observed/Ingested Revision:** `8e5edc730601b6fb156a5c31976c8c427925c2d5`
- **Knowledge Baseline Revision:** `21b1d529e63a1c037bca1538495f0b00febe08c9`
- **Freshness Status:** `PARTIALLY_FRESH`
- **Guidance:** `REFRESH_RECOMMENDED`
- **Checked:** `2026-08-27T10:50:04.518249Z`
- **Pending Reviews:** 6 (none accepted)
- **Evidence:** 60/156 candidates selected; content fields null (no diff/document bodies)

**DevLog Usefulness:** MEDIUM

Strong on: Stories 0028/0029 (active-scan dashboard, opportunity setup snapshots), commit targeting, scan history, dashboard evolution.

Weak on: Trading Core internals, risk enforcement, broker abstraction, full system reconstruction, runtime AI, story completion status, test results.

Gaps: 6 pending unreviewed insights; context budget truncated 156→60; no ADR body content surfaced; no code content in evidence.

## Repository State

- **Branch:** `main` tracking `origin/main`
- **HEAD:** `8e5edc7` (Merge PR #25 — Story 0029)
- **Working Tree:** Clean
- **Recent Activity:** Stories 0025–0029 (Aug 24–25, 2026)
- **Remote Fetch:** Unavailable (SSH hardware key agent refused signing)

## Runtime Topology

```
Angular (port 17085 via nginx)
    ↓ same-origin /api/ → http://gateway:8080
    ↓ same-origin /ws/ → Gateway WebSocket
Gateway (port 17080)
    ↓ Spring Cloud Gateway + Eureka discovery
    ↓ JWT validation + authenticated actor header
    ↓→ Trading Core (REST, user/account/trade-plan/risk/execution)
    ↓→ Broker Service (REST, credentials, broker operations)
    ↓→ Market Data (REST + WebSocket, catalogue/OHLC/orderbook/ticker)
    ↓→ Market Intelligence (REST, scans/opportunities)
Eureka Server (service discovery)
```

Services share `trading-network`. Docker Compose orchestrates all services. Backend modules are independently built Maven projects.

**Databases:** PostgreSQL per service (trading-core, broker-service, market-data, market-intelligence). Flyway migrations.

**External APIs:** Kraken (REST + WebSocket) via Broker Service and Market Data Service.

## Trading Core

| Capability | Status |
|---|---|
| User registration/login/JWT | IMPLEMENTED |
| Account CRUD | IMPLEMENTED |
| Broker Account lifecycle | IMPLEMENTED |
| Trade Plan creation from opportunity | IMPLEMENTED |
| Trade Plan accept/reject | IMPLEMENTED |
| Trade Plan risk evaluation orchestration | IMPLEMENTED |
| Execution intent/attempt/order pipeline | IMPLEMENTED (backend only) |
| Gateway route for executions | NOT_EXPOSED |
| Angular execution surface | NOT_IMPLEMENTED |
| Legacy trade opening | BROKEN (null entry price) |
| Legacy account synchronization | BROKEN (global, non-persistent) |

**Security Defects:**
- `GET /api/v1/users/{id}` exposes JPA User including password hash (`:51-55`)
- JWT verification does not require configured issuer (`:62-87`)
- Trade endpoints lack owner-scoping (`:48-69`)
- Development JWT secret fallback in application.properties (`:22-24`)

## Risk Engine

**Deterministic risk-domain module:**
- Three rules: maximum position risk, maximum exposure, daily drawdown
- Immutable inputs, fixed-clock orchestration, projection before evaluation
- Fail-closed: missing config → no decision
- Replay and trace support

**Runtime integration (Trading Core):**
- TradePlanRiskEvaluationService constructs full context from plan + account + broker snapshot + market data + risk-day ledger
- Persisted snapshots and approval outbox

**Missing:**
- No seed data or API to create risk profiles, account configurations, or assignments
- Required margin defaults to unavailable adapter
- No total/account-lifetime drawdown rule
- No deterministic position sizing (returns budget/utilization only)

**Can Trading OS prevent a violating trade?**
Through the intended new Core path: yes in design. But the path currently cannot normally reach execution due to account-ID mismatch in ValidateAndCreateService (`:73-77`), missing risk profile provisioning, and unavailable margin provider.

## Broker Service

| Capability | Status |
|---|---|
| Broker-neutral domain model | REAL |
| Kraken infrastructure adapter | REAL |
| Parallel legacy Kraken stack | REAL (stale) |
| Credential encryption (AES-256-GCM) | REAL |
| Credential rotation/revocation | REAL |
| Account balances | REAL |
| Positions | PARTIAL (no stable ID, margin, PnL) |
| Order placement | REAL |
| Order cancellation | PARTIAL (no acknowledged/rejected/unknown contract) |
| Execution outcomes (ack/rej/unk) | REAL |
| Unknown-outcome reconciliation | REAL |
| Idempotency (Core) | REAL (global unique, not actor-scoped) |
| Ownership enforcement on account/order/execution | MISSING (serious security gap) |
| Broker-neutral market catalogue | NOT_IMPLEMENTED |

## Account Data

- `Account` (legacy): balances, equity, peak equity, optional Rules, user, local trades
- `BrokerAccount` (new): broker reference, connection state, opaque credential reference
- No domain relationship between `Account` and `BrokerAccount`
- Account synchronization uses global legacy path, not per-BrokerAccount
- Positions read-only in dashboard projection

## Market Data

| Data Type | Provider | Canonical Model | API | Frontend |
|---|---|---|---|---|
| Market catalogue | Kraken | Normalized | REST | List + filter |
| Ticker | Kraken | Normalized | REST + WS | Detail page |
| OHLC history | Kraken | Normalized intervals | REST | Chart (lightweight-charts) |
| Order book | Kraken | Normalized | WS | Detail page |
| Recent trades | Kraken | Normalized | WS | Detail page |
| Market state/tradability | Kraken | TradingStatus | Model field | Not displayed |

**Domain boundary:** Market Data owns catalogue, metadata, constraints, state, real-time streams. Provider normalization in infrastructure. Business decisions NOT in Market Data. ADR-006: market is OPEN only when order can execute immediately.

## Market Analysis

- OHLC Range Analysis capability implemented
- Strategy Evaluation Service evaluates persisted strategy definitions
- StrategyMatch → TradingOpportunity creation
- OpportunitySetupSnapshot preserves trigger facts
- OpportunityExpirationDriver enforces validity windows
- Two strategies: one active, one disabled/UNVALIDATED

## Passive Scanner

**NOT_FOUND** as a persistent recurring scanner. `PassiveAnalysisStrategy` is an execution-plan selector, not the recurring batch-driven passive scanner described in ADR-033. ADR-033 explicitly states passive scanning is non-blocking for V1.

## Active Scanner

**IMPLEMENTED** with substantial investment (Stories 0005–0008, 0022, 0025, 0027–0028):
- Persistent orchestration foundation
- Async dispatch (202 response before completion)
- Account-aware scope resolution
- Lifecycle reconciliation
- Result projection
- Dashboard exposure
- Frontend scan panel with account selection, objective, polling
- ~7-minute full-universe scan (~1,436 eligible markets)

**Limitation:** No UI market/scope selection despite backend support for `requestedMarketIds`.

## AI

**NOT_IMPLEMENTED.** Disabled adapter only. No AI engine, LLM, RAG, agent runtime, or analysis endpoint exists. Sidebar "AI Engine Online" is a false status indicator. Homepage AI claims are misleading.

## News / Macro

**NOT_FOUND.** ADR-008 establishes News Service conceptually; no module or implementation exists.

## Position Monitoring

**NOT_IMPLEMENTED.** Dashboard displays open positions read-only. No `/positions` route exists. Dead sidebar link. No monitoring, alerting, or recommendation for open positions.

## Frontend

| Page | Status |
|---|---|
| Login | FUNCTIONAL (no loading state, navigates to home not dashboard) |
| Register | FUNCTIONAL |
| Dashboard | FUNCTIONAL (accounts, equity, risk, MI summary, active scan) |
| Accounts | FUNCTIONAL (list, broker connect, sync) |
| Market list | FUNCTIONAL (filter, no loading/error states) |
| Market detail | FUNCTIONAL (chart, ticker, orderbook, trades — weak error/reconnect) |
| Opportunities list | FUNCTIONAL |
| Opportunity detail | FUNCTIONAL (setup snapshot, provenance) |
| Scan panel | FUNCTIONAL |
| Plan preparation | FUNCTIONAL (success path; broken failure state) |
| Plan decision/risk | FUNCTIONAL through risk result; no post-risk action |
| /positions | DEAD_LINK |
| /analytics | DEAD_LINK |
| AI/Analytics pages | NOT_IMPLEMENTED |

**Architecture:** Observables, async pipe, reactive composition substantially implemented. Manual subscriptions mostly on command side effects.

**Critical UX defects:**
- Plan creation failure swallowed → page stuck on "Creating"
- `deciding`/`evaluatingRisk` states defined but never emitted
- Any plan/decision/risk failure shows generic "Market Intelligence unavailable"
- Post-risk action is only "Back to opportunities"

## End-to-End User Journeys

### A. Open → see real account state
**PARTIAL.** Login works, accounts load, broker connection succeeds, dashboard polls. But: account synchronization is broken (global, non-persistent), `lastSynchronizedAt` never set, positions read-only.

### B. Select market → see market info
**WORKING.** Market catalogue loads, detail shows ticker/chart/orderbook/trades. Weak: no loading/error states, WebSocket errors terminate without reconnect.

### C. Ask "What opportunities are interesting now?"
**PARTIAL.** Active scan can be triggered, runs asynchronously, results appear in opportunities list and dashboard. But: no market/scope selection, full universe takes ~7 minutes, no AI interpretation.

### D. Inspect opportunity → understand why interesting
**PARTIAL.** Opportunity detail shows setup description, reference price, detected triggers, strategy match ID. But: "supporting observations" are raw IDs, strategy metadata stops at match ID, no navigable evidence.

### E. Evaluate risk before trade
**PARTIAL.** Trade plan can be created, accepted/rejected, risk evaluated, and decision displayed with reasons/warnings. But: risk configuration is not user-configurable, required margin unavailable, evaluation depends on manually provisioned profile.

### F. Inspect open positions
**NOT_WORKING.** Dashboard shows read-only positions. No dedicated positions page. No management, monitoring, or alerting.

**Critical chain stops after: view risk decision.** No validate-execution, no execute action, no Gateway route for executions, no order submission, no execution result visibility.

## Build & Test Health

Not validated during this investigation (SSH fetch unavailable, investigation-only constraint). Evidence from commits indicates:
- Backend JaCoCo >=80% LINE gates configured
- Frontend 159 tests, LINE >=80% coverage threshold
- Quality gate SonarQube integration configured
- Broker test profile disables Flyway (`ddl-auto=create-drop`)

Existing `target/` artifacts may be stale. No runtime validation performed.

## ADR / Roadmap Alignment

| Decision | Status | Implemented? | Still Relevant? | Evidence |
|---|---|---|---|---|
| ADR-001 (human authority) | Accepted | Partially | Yes | Plan accept/reject exists; post-risk action missing |
| ADR-002 (microservice split) | Accepted | Yes | Yes | Services exist and communicate |
| ADR-006 (market data) | Accepted | Yes | Yes | Full implementation |
| ADR-008 (news service) | Accepted | No | Future | No module exists |
| ADR-014 (explicit user action) | Accepted | Partially | Yes | No execution trigger in UI |
| ADR-020 (market intelligence) | Accepted | Yes | Yes | Deterministic pipeline operational |
| ADR-024 (credentials) | Accepted | Yes | Yes | Encrypted per-account storage |
| ADR-028 (risk domain) | Accepted | Yes | Yes | Deterministic engine functional |
| ADR-029 (execution domain) | Accepted | Backend only | Yes | No product surface |
| ADR-030 (broker isolation) | Accepted | Yes | Yes | Neutral contracts, Kraken in infra |
| ADR-031 (responsibility split) | Accepted | Yes | Yes | Correct service ownership |
| ADR-032 (human validation) | Proposed | Implemented | Governance gap | Accepted as authoritative despite not being "Accepted" |
| ADR-033 (scanner) | Proposed | Partially implemented | Governance gap | Active scanner exists; passive not implemented |
| ADR-034 (strategy domain) | Accepted | Yes | Yes | Persisted strategy definitions |
| ADR-037 (strategy source of truth) | Accepted | Yes | Yes | Persisted definitions runtime authority |
| ADR-038 (empirical validation) | Accepted | No | Yes/V2 boundary | Zero validated strategies; V2 concern |

## Architecture Drift

**IMPLEMENTATION_DEBT:**
- ADR-032 and ADR-033 remain "Proposed" despite their decisions being implemented and used. Repository lifecycle says only "Accepted" ADRs are authoritative.
- V1 Definition of Done still claims no scan UI, no opportunity UI, no plan/risk UI — all implemented by Stories 0021–0023.
- Story statuses are stale (0009/0010 say "In Progress" despite merged PRs; 0021–0023 say "Approved" despite engineering reports).
- Resumption investigation recommendation (Story 0021) is now historical.

**BENIGN DRIFT:**
- Legacy trade opening workflow is broken but superseded by trade-plan-based flow.
- `Account` and `BrokerAccount` not formally linked; acceptable given separate ownership models.

**STALE DOCUMENTATION:**
- Multiple previous "next story" recommendations were implemented.
- ADR-034's original story sequence (0013–0015) was superseded by different implementations.

## V1 Capability Matrix

| Capability | Status |
|---|---|
| Account visibility | PARTIAL — list/load/sync exists but sync broken |
| Broker connectivity | PARTIAL — credentials work; execution path blocked |
| Market discovery | USABLE — catalogue, filter, detail |
| Live market data | USABLE — REST + WebSocket |
| Historical market data | USABLE — OHLC history |
| Risk configuration | NOT_STARTED — no UI, no provisioning API |
| Risk enforcement | PARTIAL — deterministic engine exists; not operational end-to-end |
| Trade representation | PARTIAL — legacy broken; trade-plan represents intent |
| Trade execution | FOUNDATION_ONLY — backend pipeline exists; no product surface |
| Trade analytics | PARTIAL — legacy calculators exist; some correctness issues |
| Market analysis | USABLE — deterministic strategy evaluation |
| Passive scanning | NOT_STARTED |
| Active scanning | USABLE — persistent orchestration, async dispatch |
| Opportunity ranking | PARTIAL — exists but all scores = 100 |
| AI interpretation | NOT_STARTED — disabled adapter only |
| News/macro context | NOT_STARTED |
| Open-position monitoring | NOT_STARTED |
| Dashboard | USABLE — operational overview with real data |
| Alerts | NOT_STARTED |
| User configuration | NOT_STARTED — risk profiles, scanner scope, preferences |

## What the User Can Do Today

Today, a user can:
- Register and log in
- Connect a Kraken broker account with encrypted credentials
- Browse a live market catalogue with filter and detail
- View real-time ticker, chart (OHLC), order book, and recent trades per market
- Trigger an active market scan from the product
- View scan progress and results
- Browse active opportunities with deterministic setup explanation
- Inspect opportunity details including trigger facts and provenance
- Create a trade plan from an opportunity
- Accept or reject a trade plan proposal
- Evaluate deterministic risk and view approval/refusal with reasons and warnings

## What the User Cannot Do Today

Today, a user cannot:
- Submit an execution order after risk approval
- See execution results, order status, or reconciliation
- Configure risk rules, profiles, or account assignments
- Select specific markets or instruments for scanning
- Receive AI-assisted interpretation of opportunities
- Monitor open positions with meaningful change detection
- View analytics, performance metrics, or trade history
- Access news or economic calendar context
- Verify strategy legitimacy (all scores = 100, no validation evidence exposed)
- Exercise post-risk execution authority (the critical missing product action)

## Infrastructure Without User Value

- Eureka Server: running but no user value beyond service discovery
- Risk profile/configuration tables: exist but no creation API or provisioning
- Execution pipeline backend: exists but no Gateway route or Angular surface
- AI adapter interface: exists but always disabled
- `lastSynchronizedAt` field: modeled but never written
- Dashboard "Performance" panel: placeholder with no data
- Dead `/positions` and `/analytics` sidebar links

## Shortest Path to Useful Trading Intelligence

The minimal missing chain for ONE useful trading decision surface:

```
Approved risk result (EXISTS)
    → post-risk human execution decision (MISSING in UI)
    → ExecutionIntent creation (BACKEND EXISTS, no route)
    → public Gateway route (MISSING)
    → execution UI/state (MISSING)
    → controlled broker order submission (UNVERIFIED)
    → order status visibility (MISSING)
    → unknown-outcome reconciliation drill (UNVERIFIED)
```

## Candidate Next Slices

### Candidate 1: Connect Risk Decision to Human-Controlled Execution

**USER VALUE:** After reviewing a risk-approved plan, the user can explicitly choose to execute or cancel — and see the order result.

**EXISTING FOUNDATION:** ExecutionController (backend), ValidateAndCreateService, ExecuteTradeService, BrokerSubmissionStep, unknown-outcome reconciliation, Gateway JWT/auth, Angular plan-page.

**MISSING PIECES:**
- Resolve RISK_VALIDATED vs ACCEPTED plan-state mismatch
- Post-risk human action (execute/cancel) in plan-page UI
- Versioned public `/executions` Gateway route
- Execution state/result Angular surface
- Validate broker least-privilege trading credentials
- One controlled Kraken sandbox order proof
- Unknown-outcome → reconciliation → retry drill
- Ownership enforcement on Broker Service execution endpoints

**COMPLEXITY:** MEDIUM
**ARCHITECTURAL RISK:** MEDIUM (external Kraken dependency, credential permission change, account-ID mismatch bug)

**DEPENDENCIES:** Broker Service ownership enforcement, Gateway route, Kraken sandbox credentials with trading permissions.

**WHY NOW:** This is the single missing link between existing capability and a useful tool. Every upstream piece exists. Without it, the entire risk/planning chain is a dead end.

**WHY NOT NOW:** Broker Service ownership enforcement is a security prerequisite; Kraken sandbox credentials with trading permissions needed; account-ID mismatch must be resolved.

### Candidate 2: Fix Account Synchronization and Surface Real Positions

**USER VALUE:** See accurate broker account balances, open positions, and trade history.

**EXISTING FOUNDATION:** Broker Account domain, broker capabilities (balances, positions, orders), dashboard projection, Account model.

**MISSING PIECES:**
- Per-BrokerAccount synchronization (currently global/legacy)
- Position persistence and management
- Positions page with monitoring
- Trade history page
- `lastSynchronizedAt` write path

**COMPLEXITY:** MEDIUM
**ARCHITECTURAL RISK:** LOW

**DEPENDENCIES:** Execution slice should land first so positions include broker orders.

**WHY NOT NOW:** Account sync is important but not the highest-value missing piece. A trader who cannot act on risk decisions does not benefit from better position visibility. Positions become meaningful after execution works.

### Candidate 3: Add User-Configurable Risk Profiles

**USER VALUE:** Set risk limits, daily loss caps, and prop-firm constraints per account.

**EXISTING FOUNDATION:** risk-domain rules, risk_profile/account_risk_configuration tables, versioned profiles, profile assignment schema.

**MISSING PIECES:**
- Risk profile creation API
- Account risk configuration API
- Profile assignment API
- Risk configuration UI page
- Gateway routes for profile management

**COMPLEXITY:** LOW-MEDIUM
**ARCHITECTURAL RISK:** LOW

**DEPENDENCIES:** None blocking.

**WHY NOT NOW:** Risk profiles are required for operational use but not for proving the value chain. The current hardcoded profile (max position risk, max exposure, daily drawdown) is sufficient for initial sandbox testing. User configuration becomes valuable once the execution chain works.

## Recommended Next Slice

**RECOMMENDATION:** Candidate 1 — Connect Risk Decision to Human-Controlled Execution

**USER VALUE AFTER SLICE:** After reviewing a risk-approved trade plan, the user can explicitly trigger execution from the product, see the order submission result, and observe unknown-outcome reconciliation — completing the first end-to-end trading decision journey.

**RECOMMENDED SCOPE:**

IN_SCOPE:
1. Resolve plan-state mismatch (RISK_VALIDATED → ACCEPTED for execution gate)
2. Post-risk "Execute" / "Cancel" action in plan-page Angular component
3. Versioned `POST /api/v1/executions/validate` Gateway route
4. `POST /api/v1/executions/{id}/execute` Gateway route
5. Execution result/status Angular component
6. Broker Service ownership enforcement on execution endpoints
7. One controlled Kraken sandbox order proof (success, failure, unknown)
8. Reconciliation exercise for unknown outcome
9. Test coverage for execution validation and lifecycle

OUT_OF_SCOPE:
- Passive scanner
- AI interpretation
- News service
- Risk configuration UI
- Backtesting
- Position monitoring
- Trade history page
- Market scope selection in scanner
- Strategy validation
- Analytics/performance dashboard

**V1 / V2:** V1

**ADR:** ADR-029 (execution domain) is Accepted and governs this work. ADR-032 (human validation) should be accepted before or during this story to resolve the plan-state semantics.

**ROADMAP:** The V1 Definition of Done and previous roadmap should be updated to reflect that scan/opportunity/plan/risk UI are implemented, and that execution integration is the current V1 frontier.

## Product Risks

**Biggest Product Risk:** The entire value chain is proven end-to-end in code but cannot be exercised by a user. If execution integration is not completed soon, Trading OS risks being perceived as a demo of microservice architecture rather than a useful trading tool.

**Secondary Risks:**
- Strategy legitimacy: zero validated strategies; only legacy fixture runs under controlled exception
- Gateway discovery-locator may expose internal endpoints beyond intended route table
- Broker Service ownership enforcement gap is a security issue independent of execution
- All opportunity scores = 100; no ranking differentiation

## Relevant Technical Debt

1. **Account-ID mismatch in ValidateAndCreateService** — execution validation incorrectly compares Trading Account ID with Broker Account ID. Must be fixed before execution works.
2. **Broker Service ownership enforcement** — account/order/execution endpoints lack owner checks. Security prerequisite for execution exposure.
3. **Legacy parallel stacks** — old broker controller/DTOs, old trade opening, old synchronization still exist and can mislead developers.
4. **ADR lifecycle gaps** — ADR-032 and ADR-033 implemented as "Proposed" rather than "Accepted".
5. **DevLog knowledge baseline** — 6 pending reviews; should be refreshed after this story.

## Open Questions

1. Can Kraken sandbox credentials with trading permissions be provisioned?
2. Is the Gateway discovery-locator exposure intentional or should it be disabled?
3. Should the legacy trade opening workflow be explicitly deprecated?
4. What is the minimum Kraken permission set for sandbox order placement?
5. Should plan-state semantics be: plan accepted → risk evaluated → execution authorized? Or is explicit post-risk human action sufficient?

## Recommendation

**GO_FOR_NEXT_STORY** — The evidence strongly supports creating the next Engineering Story to connect approved risk decisions to human-controlled execution. The foundation exists, the gap is well-defined, and closing it would produce the first complete user journey from market intelligence to broker order.

---

*Investigation artifact created: 2026-08-27*
*Repository: main @ 8e5edc7*
*DevLog: partially fresh, context 8e5edc7, baseline 21b1d52*
