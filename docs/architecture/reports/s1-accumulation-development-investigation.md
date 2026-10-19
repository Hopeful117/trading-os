# Trading OS Development Priorities During S1 Accumulation

Investigation date: 2026-10-19

## 1. Executive Decision

**A — DEVELOPMENT_BACKLOG_IDENTIFIED_S1_ACCUMULATION_HEALTHY**

Safe, high-value work exists. The best next Story is to make research
accumulation operationally observable and fail-closed without changing any S1
semantics. The next product arc should then complete the PAPER decision-to-
position lifecycle and expose the existing deterministic intelligence path more
coherently in the frontend.

## 2. Repository State

- `REPOSITORY_READY_FOR_INVESTIGATION = YES`
- Branch: `main`
- `HEAD = d4f1958a8b91b48b2e00a4981efce998b11603d2`
- `origin/main = 5cfa75896c9b38d0bbba48467abba58b94df4383`
- S1 pre-registration commit is present and is the current local commit.
- The only pre-existing unrelated worktree item is the untracked
  `docs/architecture/reports/v1-functional-audit.md`.
- That file was not modified, staged, or committed.
- DevLog was checked and unavailable because its provider returned HTTP 429
  quota exhaustion. Current repository and runtime evidence are authoritative.
- `IMPLEMENTATION_ALLOWED = NO` for this investigation.
- No S1 outcome, return, win rate, P&L, or profitability result was inspected.

## 3. Current Architecture Map

```text
Trading OS Web (Angular/Nginx)
              |
              v
Gateway (JWT, public routes, WebSocket routing)
       |       |          |          |          |
       v       v          v          v          v
Trading Core Broker   Market Data Market Intelligence News Service
  |          |            |            |             |
  v          v            v            v             v
Trading DB Broker DB   Market DB     MI DB       News DB
  |          |            |            |             |
  |          +---- Kraken REST/private APIs ------+
  +---------------- Market Data / MI contracts

Eureka provides local service discovery.
Research lifecycle runs in Market Intelligence and consumes strict market
evidence, while economic replay uses Trading Core and historical fee data.
```

Current responsibilities are substantially aligned with the repository
mission. The important boundary is preserved: Market Intelligence proposes and
explains; Risk and Trading Core authorize deterministically; Broker Service
transports broker-specific operations; the human authorizes execution.

## 4. Service Inventory

| Service/module | Responsibility | Maturity | Persistence | Main gap |
|---|---|---|---|---|
| Trading Core | Users, accounts, trades, risk, TradePlans, PAPER/LIVE execution lifecycle, dashboard, outcomes | FUNCTIONAL to OPERATIONAL | PostgreSQL/Flyway | Complete PAPER lifecycle, deployed execution proof, analytics |
| Risk Domain | Deterministic risk rules and evaluation | FUNCTIONAL/VALIDATED | Domain plus Trading Core snapshots | Profile management UX and broader runtime evidence |
| Broker Service | Credentials, Kraken adapter, accounts, positions, orders, reconciliation, fee schedules | FUNCTIONAL | PostgreSQL/Flyway | Sandbox/deployed write proof, fresh pre-submit validation |
| Market Data | Catalogue, ticker, OHLC, order book, trades, subscriptions, historical evidence | FUNCTIONAL/OPERATIONAL | PostgreSQL/Flyway | Feed capability matrix, runtime observability, unsupported paths |
| Market Intelligence | Context, deterministic analyses, observations, opportunities, plans, scans, research | FUNCTIONAL | PostgreSQL plus selected in-memory components | Durable artifact/projection state, richer operational health |
| News Service | Economic events and financial news normalization and persistence | PARTIAL/FUNCTIONAL | PostgreSQL/Flyway | Empty news provider state, stale-cache semantics, MI relevance |
| Gateway | JWT boundary, routing, service discovery, WebSocket routing | FUNCTIONAL | None | Deployed downstream contract proof, internal-port hardening |
| Eureka | Local service discovery | SKELETON/FUNCTIONAL | None | HA/authentication/production policy |
| Trading OS Web | Dashboard, accounts, markets, intelligence, plans, positions, history, news | FUNCTIONAL surface | Browser state plus APIs | Navigation coherence, analysis/provenance UI, real analytics |
| `research/` Python | Dataset validation, experiment helpers, notebook exploration | SKELETON/PARTIAL | Files/notebooks | No S1 execution allowed yet; later economic research contract |

## 5. Capability Heatmap

| Capability | Maturity | User-visible | Runtime validated | Main gap |
|---|---|---|---|---|
| Authentication/JWT | OPERATIONAL | Yes | Partially | Full deployed-path matrix |
| Account and broker connection | FUNCTIONAL | Yes | Partially | Diagnostics and provider capability display |
| Kraken market catalogue | OPERATIONAL | Yes | Yes locally | Capability/status presentation |
| Live market streams | FUNCTIONAL | Yes | Yes locally | Multi-instance state and health visibility |
| Historical research lifecycle | FUNCTIONAL | No | Yes locally | Run-level health and failure accounting |
| FeatureSnapshot V1 | VALIDATED | No | Yes | Preserve semantic freeze |
| PT1H objective outcomes | FUNCTIONAL | No | Yes | Readiness and backlog visibility |
| Order Book evidence/replay | FUNCTIONAL/VALIDATED | Indirectly | Q1 validated | Coverage/epoch diagnostics |
| Deterministic risk | VALIDATED | Partially | Focused tests | Better explanation/profile UX |
| PAPER entry/position view | FUNCTIONAL | Yes | Yes locally | Close, settlement, lifecycle history |
| TradePlan and human validation | FUNCTIONAL | Yes | Partially | Navigation and end-to-end proof |
| Broker LIVE mutation | FOUNDATIONAL | Yes in APIs | Not fully | Sandbox, freshness, recovery, approval proof |
| Opportunities | FUNCTIONAL | Yes | Partially | Pagination, provenance, expiry, filters |
| News/calendar | PARTIAL/FUNCTIONAL | Yes | Events locally | Financial news empty, stale semantics |
| Position monitoring | NOT_STARTED/PARTIAL | No | No | Recommendation-only monitoring |
| AI Engine | NOT_STARTED | Misleading status exists | No | No real adapter; must remain explicit |
| Analytics | SKELETON/PARTIAL | Labelled analytics | No | Trade history only, no performance analytics |

## 6. Current User Journey

| Journey step | Status | Evidence |
|---|---|---|
| Register/login | AVAILABLE | Angular auth and JWT endpoints |
| Connect Kraken account | AVAILABLE | Broker credential and account surfaces |
| Provision PAPER account | AVAILABLE | Trading Core and frontend account flow |
| Inspect portfolio/positions | AVAILABLE for PAPER | Persisted local trades and valuation |
| Inspect markets | AVAILABLE | Catalogue, ticker, OHLC, order book, trades |
| Request/inspect deterministic context | PARTIAL | Decision Workspace and active scans exist |
| Inspect news/calendar | AVAILABLE but limited | Events available; financial news currently empty |
| Discover opportunities | PARTIAL | Active list exists; query/provenance UX limited |
| Prepare TradePlan | AVAILABLE | Manual and opportunity paths |
| Evaluate risk | AVAILABLE | Deterministic risk handoff exists |
| Human-confirm execution | PARTIAL | APIs/UI exist; full deployed proof remains |
| Execute PAPER | AVAILABLE for entry | Persisted simulated execution exists |
| Close/settle PAPER | PARTIAL/MISSING | README identifies closure and settlement gap |
| Monitor open position | MISSING | No recommendation-only monitoring workflow |
| Review trade performance | MISSING/PARTIAL | Execution history exists, analytics do not |

The smallest coherent V1 gap is not another data source. It is completing the
feedback loop from a human decision to a durable PAPER result and then making
that result explainable in the product.

## 7. Top Product Gaps

1. PAPER trading does not yet provide a complete open-to-close learning loop.
2. Decision Workspace does not consistently expose source evidence, news,
   freshness, and provenance in one user workflow.
3. Opportunities are difficult to filter, navigate, and trace to their source
   analysis.
4. The dashboard leaves watched markets and performance information unused.
5. `/analytics` is execution history, not trading analytics.

## 8. Trading Core Audit

Trading Core owns accounts, broker-account references, trades, positions,
TradePlans, risk validation, execution intents/attempts, PAPER settlement,
reconciliation, challenges, dashboard aggregation, and trade outcomes.

Strengths:

- explicit execution lifecycle and idempotency;
- distinct financial-account and broker-account identities;
- persisted execution attempts, broker orders, fills, and recovery state;
- deterministic risk authority remains outside Market Intelligence;
- PAPER state is persisted and reloadable;
- ownership and authenticated actor checks have substantial tests.

Gaps:

- the end-to-end post-risk user path still needs deployed proof;
- legacy and modern trade/execution APIs coexist, increasing contract drift;
- PAPER close/settlement and trade-outcome presentation are incomplete;
- challenge-specific scheduled behavior adds operational surface;
- fresh risk and broker-state validation immediately before LIVE mutation remains
  a future concern.

Trading Core is not currently LIVE-ready despite having LIVE-oriented APIs.

## 9. Risk Engine Audit

The deterministic risk engine is a strong architectural asset. It includes
TradePlan inputs, risk contexts, effective rules, immutable snapshots, atomic
rules, hierarchical authority, fail-closed behavior, and execution-time
validation concepts.

Maturity: `FUNCTIONAL/VALIDATED`.

Safe work:

- read-only rule explanations and profile/version display;
- ownership and stale-snapshot contract tests;
- PAPER-specific visibility and operator diagnostics.

Protected work:

- changing rule authority;
- changing rule evaluation order;
- weakening fail-closed behavior;
- changing execution-time revalidation;
- allowing AI or frontend input to override risk.

## 10. Broker Service Audit

The broker boundary is provider-neutral at its public contract and Kraken-
specific inside infrastructure. Credentials are encrypted and versioned;
accounts, balances, positions, orders, cancellations, errors, fee schedules,
and reconciliation are represented.

Maturity: `FUNCTIONAL`, with incomplete LIVE operational validation.

Research capabilities and read-only/live mutation capabilities are separated
conceptually, but a complete Kraken sandbox/deployed proof is still missing.

High-value safe work includes sandbox contract fixtures, unknown-response
reconciliation tests, ownership matrices, and operational metrics. No real
order should be sent.

## 11. Kraken Integration Audit

Current coverage includes:

- account and balance reads;
- positions and market access;
- Spot and Linear Perpetual provider symbols;
- REST/private broker calls;
- WebSocket market data through Market Data;
- order placement/cancellation abstractions;
- historical fee capture;
- credential validation and revocation.

Known gaps:

- no completed sandbox proof for the entire write/reconcile path;
- funding acquisition remains absent;
- provider rate-limit and schema monitoring is limited;
- process-local credential rate limiting is not distributed;
- stale risk/account state before LIVE submission remains unresolved.

## 12. Market Data Audit

Market Data has the strongest evidence boundary outside research itself:

- canonical market/product identity;
- provider symbol isolation;
- OHLC, ticker, order book, recent trades;
- persisted historical evidence with `occurredAt` and `knownAt`;
- active/sealed/invalidated Order Book epochs;
- trusted replay and provenance fingerprints;
- reconnection, subscription replay, and termination diagnostics.

Maturity: `FUNCTIONAL/OPERATIONAL`, with meaningful runtime and provider gaps.

Further development should focus on health visibility and explicit capability
status, not on changing normalization or replay semantics during S1.

## 13. S1 Data Accumulation Protected Zone

### DO NOT MODIFY DURING ACCUMULATION

1. `FeatureProjectionV1` and its feature semantics.
2. Trend-context and market-structure analyzer semantics used by V1.
3. Research lifecycle cutoff alignment and idempotency.
4. `FeatureSnapshot` identity, missingness, and provenance rules.
5. `ObservedMarketOutcome` horizon, maturity, and status semantics.
6. Exact `ResearchSample` snapshot/outcome binding.
7. Order Book normalization, epoch selection, trusted replay, and `knownAt`.
8. Historical fee applicability and cost-completeness semantics.
9. S1 pre-registration and its required feature/signal/metric rules.
10. Passive acquisition market scope, cadence, and configured horizons.

### SAFE TO MODIFY WITH GUARDRAILS

1. Read-only health and accumulation dashboards.
2. Lifecycle run accounting and failure reporting, without changing scheduling
   or selection semantics.
3. Replay-coverage diagnostics that do not alter replay.
4. Frontend presentation, navigation, provenance, and explicit unavailable
   states.
5. PAPER-only lifecycle work isolated from research services.
6. Contract tests, CI checks, migration verification, and operational metrics.
7. News cached/stale presentation, provided objective research inputs remain
   unchanged.

## 14. FeatureProjection Semantic Freeze

`feature-projection-v1` remains frozen and the pre-registration is intact.

Indirect semantic risks include:

- changing trend artifact rule/profile versions;
- changing OHLC temporal availability;
- changing structure swing retention or relation calculation;
- changing strict observed evidence selection;
- changing context-fingerprint assembly;
- changing analyzer reuse or artifact linkage;
- adding a fallback when a feature is unavailable.

The schema can remain textually unchanged while any of these changes alter
feature meaning. Such work is `DEFER_UNTIL_S1_FROZEN` unless explicitly
approved as a separate versioned experiment.

## 15. Market Intelligence Audit

Market Intelligence contains a substantial deterministic pipeline:

```text
context contributors
→ analysis execution
→ deterministic capabilities
→ observations
→ opportunities/ranking
→ trade plans
→ Trading Core risk handoff
```

It also contains passive research, objective outcomes, exact population
binding, economic orchestration, active scans, artifact provenance, and News
adapters.

Main gaps:

- selected artifact/projection/metrics components remain process-local;
- lifecycle failures are logged rather than durably summarized;
- AI adapters are intentionally disabled;
- user-facing provenance is incomplete;
- analysis execution APIs are not fully represented in the Angular UI.

## 16. Passive Scanner Audit

The current passive research lifecycle is a research scheduler, not a complete
passive market-awareness scanner. It submits strict observations, projects
features, and evaluates matured objective outcomes. It does not yet provide a
user-facing stream of “what deserves attention,” opportunity ranking, or
recommendation delivery.

The two concerns should remain separate. Research acquisition must preserve
stable evidence; a future passive scanner can consume durable observations and
produce user-facing opportunities without changing acquisition semantics.

## 17. Active Scanner Audit

An active scan path exists: scope resolution, bounded analysis execution,
concurrency control, reconciliation, opportunities, and frontend polling are
present.

It is `FUNCTIONAL` but not a complete decision assistant because:

- filtering and provenance are limited in the UI;
- News/macro context is not consistently embedded in the workflow;
- analysis execution/result lifecycle APIs are not directly exposed in the UI;
- user-visible failure and stale states are uneven.

This is a strong product-development area after accumulation observability and
PAPER lifecycle work.

## 18. Opportunity Domain Audit

The repository has a distinct opportunity domain and persistence/projection
surface. Opportunities are not merely TradePlans, which preserves the correct
boundary between “interesting market state” and “human-approved executable
decision.”

Current gaps are query and presentation capabilities: pagination, filtering,
expiry, source scan, analysis execution, observation, and provenance should be
visible without changing the domain authority.

## 19. TradePlan Lifecycle Audit

The current lifecycle is:

```text
market/context
→ opportunity or manual input
→ TradePlan version
→ human accept/reject
→ deterministic risk evaluation
→ explicit execution authorization
→ execution attempt/recovery/reconciliation
→ position/trade outcome
```

The core contracts exist. The largest gap is not another state; it is the
durable, user-visible completion loop for PAPER close/settlement and later
analytics. Ownership boundaries are mostly correct and must not be collapsed.

## 20. Live Execution Readiness

```text
LIVE_EXECUTION_READINESS = FOUNDATIONAL
```

| Requirement | State |
|---|---|
| Account connectivity | Functional |
| Market data | Functional with explicit gaps |
| Trade planning | Functional |
| Risk authorization | Validated deterministic foundation |
| Execution transport | Functional, not fully sandbox/deployed validated |
| Position tracking | Functional but incomplete monitoring |
| Failure recovery | Foundational and tested, not fully operationally proven |
| Auditability | Strong backend foundation |
| User confirmation | Present in flow, end-to-end proof incomplete |
| Overall | Not LIVE-ready |

## 21. Paper / Shadow Trading Audit

PAPER account provisioning, simulated entry, persisted OPEN trade, position
reload, and market-data valuation are available. PAPER close and realized
settlement remain incomplete according to the repository README.

Shadow recommendations and decision logging are conceptually possible through
opportunities, TradePlans, and observations, but there is no complete dedicated
shadow ledger separating recommendation, user decision, simulated execution,
and realized result.

PAPER completion is a high-value product opportunity, but it must be isolated
from research acquisition and risk authority changes.

## 22. Decision Journal Audit

The repository can persist parts of:

- market context and provenance;
- opportunity and TradePlan versions;
- risk decisions;
- execution attempts and outcomes.

It does not yet provide a coherent user decision journal answering:

```text
what was observed
what was recommended
what the human accepted/rejected
why
what happened afterward
```

This is a P1/P2 product gap. It should be built from existing durable
identities rather than a parallel unstructured notes system.

## 23. Trading Analytics Audit

The `/analytics` frontend route currently renders execution history. It does
not provide portfolio/trading analytics such as equity curves, realized P&L,
drawdown history, expectancy, exposure, or risk-adjusted measures.

Trading statistics and challenge tracking exist in Trading Core, but they are
not yet a coherent personal trading journal analytics surface. Implementing a
small read-only history/summary surface is worthwhile after PAPER settlement is
complete.

## 24. Position Monitoring Audit

Position monitoring is not a complete capability. Existing polling and
valuation can display positions, but there is no recommendation-only monitor
for thesis invalidation, stop adjustment, partial reduction, or rising risk.

This should follow a durable decision journal and completed PAPER lifecycle. It
must recommend rather than execute automatically.

## 25. News Service Audit

News Service has persisted economic events, provider-neutral contracts, Xoomar
calendar integration, and Alpha Vantage adapter code. The current runtime
contains 27 economic events, with latest fetch at `2026-10-19T21:11:14Z`.

The financial-news table currently contains zero items. Provider absence is not
equivalent to “no news” and should be represented as unavailable or stale.

The service is useful for deterministic event context, but it is not yet a
complete market-relevant news product.

## 26. Macro Intelligence Audit

Economic events are normalized and user-visible. Market Intelligence does not
yet demonstrate a durable deterministic event-proximity/risk-window context
that is consistently attached to a selected market or TradePlan.

The next safe macro step is deterministic context integration:

- event proximity;
- impact;
- affected currencies/markets;
- freshness and source;
- explicit absence/unavailable state.

No speculative AI synthesis is required.

## 27. News → Intelligence Integration

The service-to-service boundary exists through internal News APIs and Market
Intelligence clients. The main gaps are relevance and presentation:

- market-specific filtering is not consistently visible;
- freshness/provenance is not surfaced through the complete decision flow;
- provider outage versus empty result needs stronger semantics;
- News is not yet a stable Decision Workspace section.

This is safe if it only enriches context and never changes risk or S1 feature
semantics.

## 28. Frontend Audit

Current authenticated routes include dashboard, accounts, challenges, markets,
Decision Workspace, opportunities, manual trade planning, TradePlan detail,
positions, execution history, economic context, and financial news.

The frontend uses Angular standalone components, RxJS Observables, signals,
`async` pipe, explicit loading/error states, and `switchMap` in many places.

Weaknesses include inconsistent primary navigation, remaining manual
subscriptions, duplicated market-stream lifecycle code, and no wildcard route.

## 29. Dashboard Usefulness

The dashboard already shows account equity, risk, alerts, positions,
opportunities, and scan status. It does not render all backend data:

- `watchedMarkets` is present in the model but not shown;
- the performance area is a placeholder;
- links into Market Detail, Decision Workspace, and plan history are limited;
- hard-coded `AI Engine Online` status is misleading because the AI adapter is
  intentionally disabled.

The dashboard should first present truthful status and actionable navigation,
not cosmetic expansion.

## 30. Frontend / Backend Mismatch

Concrete mismatches:

1. Backend analysis execution/result/cancel endpoints have no complete frontend
   surface.
2. Backend paginated opportunity APIs are not used by the current active list.
3. Trading Core trade-outcome APIs are not consumed by frontend analytics.
4. Dashboard watched markets are not rendered.
5. `/analytics` is named analytics but displays execution history.
6. Main navigation omits several existing routes.
7. WebSocket access token is sent in the query string, requiring a security
   review before production exposure.

## 31. Active Market Analysis UX

The existing infrastructure supports:

```text
select market
→ request bounded analysis/scan
→ poll execution or scan state
→ inspect deterministic context/opportunity
→ open TradePlan
```

The missing work is a coherent user path with explicit evidence, loading,
failure, stale, unavailable, and provenance states. This is a strong safe
product candidate after operational research status is visible.

## 32. Explainability

Backend provenance exists for feature snapshots, artifacts, analyzers,
observations, opportunities, TradePlans, risk rules, executions, fees, and
market evidence. User-facing exposure is inconsistent.

The frontend should eventually show:

- source evidence and cutoff;
- analyzer/projection versions;
- freshness and missingness;
- opportunity origin;
- risk rule and reason;
- execution/reconciliation state.

This is more valuable than adding an AI label to a recommendation.

## 33. AI Engine State

No real AI/Analytics service is present. Market Intelligence uses disabled AI
adapters that fail closed rather than returning fabricated results.

The frontend displays an “AI Engine Online” indicator despite this repository
state. That is a product-truth defect and should be corrected before any AI
milestone.

## 34. AI Readiness

```text
Deterministic context mature = PARTIAL
News/macro context mature = PARTIAL
Risk context mature = YES for authority, PARTIAL for presentation
Historical research mature = PARTIAL
Structured AI contract exists = YES
First AI milestone recommended now = NO
```

The deterministic inputs and structured contract exist, but durability,
provenance presentation, news relevance, and real historical case coverage are
not mature enough to justify an AI layer now.

## 35. LLM / Deterministic Boundary

| Capability | Boundary |
|---|---|
| Trend calculation | DETERMINISTIC |
| Liquidity/order-book replay | DETERMINISTIC |
| Event proximity and market relevance | DETERMINISTIC |
| Risk authorization | DETERMINISTIC |
| Position accounting | DETERMINISTIC |
| News normalization | DETERMINISTIC |
| News summarization | LLM REASONING later |
| Macro synthesis | LLM REASONING later, based on structured facts |
| Opportunity explanation | LLM REASONING later |
| Historical similarity | STATISTICAL/ML later |
| Order placement | DETERMINISTIC and human-authorized |

## 36. First AI Milestone

`NOT YET`.

The first future AI milestone should be structured market/news/macro synthesis
or opportunity explanation, but only after deterministic context durability,
user-facing provenance, and News freshness semantics are stronger.

## 37. RAG Readiness

```text
TRADING_OS_RAG_PRIORITY = LATER
```

The repository has notes, research artifacts, events, observations, and
strategy documentation, but a durable curated knowledge contract and a mature
AI consumer do not yet exist. RAG would currently obscure missing deterministic
context rather than solve it.

## 38. ML Readiness

```text
ML = NOT YET
```

Reasons:

- S1 has not been evaluated;
- the current chronological population is small;
- regime diversity is insufficient;
- funding remains unavailable for full-cost claims;
- economic and objective dataset contracts are not yet a complete research
  product;
- baseline and OOS evidence are not established.

## 39. Quant Research V2 Backlog

### Needed now

- research lifecycle run health;
- readiness with replay/fee/funding coverage;
- exact population/export diagnostics;
- storage-growth measurement;
- temporal-integrity regression tests.

### Needed after S1

- independent economic dataset/export contract;
- walk-forward/OOS partitions;
- baseline library;
- regime segmentation;
- robustness and cost sensitivity;
- strategy comparison and parameter sensitivity.

### Later

- multiple horizons and broader markets;
- strategy registry;
- ML dataset lifecycle;
- retrieval of historical similar cases.

## 40. Funding Blocker Reassessment

| Area | Funding impact |
|---|---|
| S1 signal research | Not a blocker |
| Objective PT1H outcomes | Not a blocker |
| Historical commission research | Not a blocker |
| Partially-costed economic replay | Allowed if explicit |
| Fully-costed perpetual profitability | Hard blocker |
| Paper trading | Not necessarily a blocker if marked simulated |
| Live perpetual accounting | Material blocker for complete realized economics |
| Portfolio performance claims | Blocker when funding applies |

Funding is `NOT_YET` the immediate next task, but it must be acquired before
fully-costed profitability conclusions.

## 41. Provider Neutrality

Provider-specific code is mostly isolated in Broker and Market Data adapters.
Kraken symbols and payloads are correctly kept at infrastructure boundaries.

Observed coupling:

- canonical runtime scope is currently Kraken/`PF_XBTUSD`;
- environment defaults assume Kraken;
- some frontend account/provider choices are effectively Kraken-shaped;
- funding and fee acquisition are provider/product-specific.

Classification: acceptable for crypto-first V1, minor debt for future brokers,
not an immediate blocker. Do not expand to another broker before the current
contracts and sandbox evidence are stronger.

## 42. Multi-Broker Readiness

The broker-neutral ports and provider registry provide a useful foundation.
Future cTrader/prop support would still require:

- product/session semantics;
- authentication and credential policy;
- funding/fee semantics;
- order/reconciliation behavior;
- market/tradability normalization;
- provider contract tests.

Current status: `FOUNDATIONAL`, not a current implementation priority.

## 43. Security

Positive controls include JWT validation, service JWTs, encrypted broker
credentials, protected internal routes, actor propagation, and deny-by-default
security configurations.

Practical debt:

1. Compose publishes internal services and Eureka directly for development.
2. Local database credentials are plaintext Compose defaults.
3. WebSocket JWT query-string transport may expose tokens to infrastructure
   telemetry.
4. Actor-header trust requires continued cross-user authorization tests.
5. Provider query parameters require strict log redaction.

These are deployment/security Stories, not reasons to weaken local development
or research semantics.

## 44. Configuration

Configuration is environment-driven and includes explicit research, fee,
Order Book, News, JWT, and provider flags. The main risks are:

- standalone defaults differ from Compose runtime overrides;
- research lifecycle is enabled in the current runtime but disabled by default;
- Order Book capture and desired subscriptions depend on multiple variables;
- historical fee capture is independently enabled/configured;
- direct service port exposure is development-oriented;
- production-like secret-backed database configuration is not represented by
  Compose defaults.

A configuration/status inventory is a safe operational opportunity.

## 45. Observability

Current support includes structured logs, lifecycle logs, Order Book diagnostic
logs, health-style readiness, and local metrics in selected areas.

Highest-value missing signals:

- lifecycle last successful run and output counts;
- projection failure counts;
- outcome backlog and maturity lag;
- S1 readiness counts without performance;
- replay coverage and reason categories;
- active/sealed/invalidated epoch counts;
- WebSocket reconnect/gap/checksum metrics;
- fee/funding coverage;
- News freshness/provider availability;
- execution unknown/reconciliation age.

## 46. Operational Dashboard

An internal read-only operations view is worthwhile. It should show:

- service/container health;
- latest market-data evidence and connection state;
- latest FeatureSnapshot and objective outcome;
- lifecycle run status;
- Order Book epoch state and replay coverage;
- historical fee freshness;
- funding availability;
- News/calendar freshness;
- S1 accumulation readiness counts.

It must not show S1 returns or profitability during accumulation.

## 47. S1 Accumulation Observability

Current runtime evidence:

- latest FeatureSnapshot: `2026-10-19T21:00:00Z`;
- latest available PT1H outcome: `2026-10-19T20:00:00Z`;
- total FeatureSnapshots: 75;
- feature-only S1 counts: 13 LONG, 13 SHORT, 49 NO_TRADE;
- pre-registration span: approximately three days of persisted feature history;
- trusted Order Book evidence: 29,926,635 rows, 86 sequences, latest known-at
  `2026-10-19T21:20:54Z`;
- Q1 trusted replay coverage: 5/5 paired samples;
- current full S1 signal replay coverage: not measured in this investigation;
- historical fee schedules: 1 applicable schedule;
- funding rows: 0;
- acquisition containers: running.

The absence of a dedicated S1 readiness status is itself a development gap.
Adding it must remain feature-only/readiness-only and must not calculate S1
returns.

## 48. Data Quality Observability

The system can detect many conditions internally but does not present them as a
single operational status. Missing or stale states should be categorized as:

- stale market evidence;
- unavailable evidence;
- incomplete outcome;
- invalidated Order Book epoch;
- competing eligible epoch;
- projection failure;
- fee unavailable;
- funding unavailable;
- provider unavailable;
- persistence failure.

The highest-value improvement is durable run/reason accounting, not generic
logging volume.

## 49. Testing

Testing is broad: unit, JPA/integration, security, architecture, provider
contract, Market Data replay, Market Intelligence concurrency, News adapters,
frontend tests, Python validators, and runtime scripts.

Weak protection remains around:

- lifecycle runs that produce zero snapshots while appearing successful;
- current runtime/deployment schema mismatches;
- multi-instance in-memory state;
- complete Gateway-to-downstream paths;
- sandbox/deployed Kraken writes;
- replay-coverage readiness;
- stale provider cache semantics.

No tests were run because this task changed no source or configuration.

## 50. Contract Testing

Important boundaries:

- Gateway ↔ Trading Core;
- Gateway ↔ Broker Service;
- Gateway ↔ Market Data;
- Gateway ↔ Market Intelligence;
- Gateway ↔ News;
- Trading Core ↔ Broker Service;
- Trading Core ↔ Market Intelligence;
- Market Intelligence ↔ Market Data;
- Market Intelligence ↔ News;
- frontend ↔ Gateway.

Focused contract tests exist, but the highest-value missing evidence is a
small authenticated deployed-path matrix for the PAPER decision journey and
the analysis/news context journey.

## 51. Database / Migrations

Each major service has PostgreSQL and Flyway migrations with `ddl-auto=validate`.
Immutable research and evidence tables are appropriately constrained.

Risks:

- migration/runtime mismatch can make a healthy-looking service operate against
  incomplete schema;
- no common backup/restore or retention policy;
- large evidence payloads reside in PostgreSQL text columns;
- process-local Market Intelligence artifacts are not represented by durable
  migrations.

Do not change schemas in this investigation. Any new durable lifecycle or
economic contract requires an ADR and migration plan.

## 52. Data Growth

The current Market Data database contains approximately 29.9 million Order Book
evidence rows and 86 sequences. FeatureSnapshots are 75; PT1H outcomes have
130 available, 59 incomplete, and 1 unavailable rows. News contains 27 events
and zero financial-news items.

Order Book payloads and indexes will grow first. FeatureSnapshots and outcomes
are currently small but can multiply with markets, contexts, and projection
versions.

Measure rows/day, bytes, replay latency, index growth, checkpoint benefit,
vacuum/backup duration, and lifecycle output rate before adding markets or
depth.

## 53. Retention

No complete retention or archival policy is present. Do not delete or compact
trusted research evidence casually.

First define measurements and reproducibility requirements. Then decide whether
raw provider evidence, normalized evidence, derived snapshots, research
exports, logs, and News have different retention classes. This is later work,
not a current blocking problem.

## 54. Performance

Meaningful risks:

- repeated lifecycle scans over all historical executions;
- replay queries over growing event-level Order Book tables;
- serialized JSON payloads and indexes;
- process-local reconstruction/cache behavior under restart;
- frontend repeated execution-list queries for plan state;
- unpaginated active opportunity retrieval.

Measure before optimizing. Do not micro-optimize stable research code during
S1 accumulation.

## 55. Concurrency

Idempotency and concurrency protections exist around research scheduling, active
scans, executions, and persistence. Remaining risks are:

- process-local Market Data subscription state;
- process-local Market Intelligence artifacts/projections/metrics;
- scheduled challenge/reconciliation jobs;
- repeated full-range lifecycle processing;
- multiple service instances without shared leases.

The next operational Story should record these states rather than introduce a
message broker prematurely.

## 56. Failure Semantics

The system generally models open/closed/degraded/unavailable states correctly
for market evidence and economic costs. The main weakness is observability:

- lifecycle projection/outcome exceptions are logged and continued;
- readiness hides some Market Data errors to remain available;
- News provider absence can look like empty data;
- no durable run status distinguishes zero output from success.

Preserving uncertainty is correct; making it visible is the missing work.

## 57. Provenance

Backend provenance is strong across market evidence, Features, outcomes, fees,
execution replay, artifacts, and risk snapshots. It answers source, known-at,
version, and fingerprint questions in many domains.

The product does not expose this chain consistently. User-facing provenance is
a high-value differentiator for a discretionary trading assistant.

## 58. Domain Model Debt

Concrete debt is concentrated at boundaries, not in a single broken aggregate:

- Trade, TradePlan, execution, and outcome concepts are spread across mature
  but overlapping APIs;
- Opportunity is correctly separate but its projection/query surface is narrow;
- Market Intelligence artifact durability differs from domain persistence;
- dashboard “analytics” terminology is ahead of its actual model;
- research economic results are not yet a durable dataset aggregate.

Avoid broad model consolidation. Address one user journey at a time.

## 59. Legacy Challenge Debt

Challenge tracking and monitoring remain useful for the product’s prop-firm
mode. They are not inherently harmful to the generic trading model.

Classification:

- still useful: challenge rules, monitoring, daily risk semantics;
- isolated/configurable: challenge-specific profiles and scheduled monitoring;
- worthwhile later: generic portfolio abstractions where challenge assumptions
  leak into them;
- current blocker: none identified.

Do not remove challenge support during S1 accumulation.

## 60. Crypto-First Coherence

Trading OS can currently support a useful but incomplete discretionary crypto
workflow: account/market inspection, deterministic context, planning, risk,
PAPER entry, and positions.

The smallest missing chain is:

```text
human decision
→ PAPER entry
→ PAPER close/settlement
→ durable trade outcome
→ readable history/analytics
```

That is a stronger immediate product direction than multi-broker or AI work.

## 61. Personal Portfolio Use Case

Available: account visibility, balances/equity, positions, market valuation,
broker synchronization, and basic risk.

Partial: entry context, realized performance, exposure aggregation, and
historical trade review.

Missing: complete decision journal, realized analytics, active monitoring, and
portfolio-level alerting.

## 62. Watchlist / Market Scope

Market catalogue and bounded research scope exist. The dashboard model already
contains watched markets, but the frontend does not render them. A true user-
managed watchlist is not yet a mature capability.

A read-only dashboard presentation is safe. Persistent watchlist ownership and
scanner scheduling should be separate Stories.

## 63. MarketState

Market eligibility/readiness and provider/product identity exist. The repository
correctly distinguishes data availability from tradability in several places,
but the user-facing market state still needs a clear status contract:

- executable now;
- data stale;
- feed unavailable;
- product unsupported;
- insufficient history;
- broker unavailable.

Do not simplify “market open” to exchange calendar status alone.

## 64. Alerts

Dashboard alerts and error states exist, but there is no coherent alert/event
capability for data failures, macro windows, position risk, or opportunity
expiry.

A generic notification platform is not justified yet. First define read-only
operational and recommendation events in existing bounded contexts.

## 65. Event Architecture

REST plus local domain events are sufficient for the current workflows. The
repository already uses durable outbox-like patterns in selected Trading Core
flows, but there is no evidence that Kafka/RabbitMQ is required now.

Recommendation: `REST sufficient` for read/query paths; `local domain event`
or existing outbox patterns where durable cross-service publication is already
required; broker infrastructure later only after measured need.

## 66. Transactional Outbox Assessment

`NO` new cross-service outbox requirement was identified for the immediate
backlog. Existing execution/reconciliation/outbox behavior should be tested and
observed rather than replaced.

An outbox becomes justified for a durable decision journal or operational event
stream only if the event must be delivered reliably across service boundaries.

## 67. API Quality

The APIs generally use versioned paths, `ResponseEntity`, internal/public route
separation, validation, and explicit domain statuses.

High-value cleanup:

- consistent pagination for opportunities/history;
- consistent error/status DTOs;
- expose freshness/provenance consistently;
- add downstream contract tests;
- avoid adding routes that duplicate existing TradePlan/execution semantics.

## 68. DTO / Domain Boundaries

Provider DTO leakage is mostly controlled by Broker and Market Data adapters.
The main current risks are cross-service DTO growth, frontend duplication, and
legacy API overlap rather than a confirmed systemic JPA leak.

Address only concrete contracts touched by a selected Story.

## 69. Frontend Reactive Architecture

Angular uses good Observable/async-pipe patterns in Dashboard, Positions, News,
and Decision Workspace. Remaining manual subscriptions exist in Accounts,
Login/Register, execution history, opportunities, and manual trade query state.

This is worthwhile P2 maintenance, but not the best isolated next Story unless
it is bundled with a user journey that needs the cleanup.

## 70. Frontend Design Coherence

The frontend generally follows the intended dark dashboard style and has
explicit loading/error states. The main coherence problem is information
architecture, not visual styling:

- inconsistent navigation surfaces;
- misleading AI status;
- hidden decision/research context;
- execution history labelled analytics;
- missing clear provenance hierarchy.

Prioritize truthful state and workflow clarity over cosmetic redesign.

## 71. Developer Experience

Local Docker Compose, bootstrap environment scripts, per-module Maven builds,
frontend npm commands, Python research tooling, and quality scripts exist.

Friction:

- no root Maven build;
- many service-specific environment variables;
- runtime database state is necessary for some investigations;
- Compose exposes development ports directly;
- reports can become stale relative to current code.

A concise runtime/validation guide and automated health snapshot would reduce
future investigation cost.

## 72. Build / CI

`.github/workflows/quality.yml` exists and validates eight backend modules,
frontend format/tests/build, coverage summaries, and Sonar on applicable runs.

Local scripts also provide `test-all.sh` and `quality-verify.sh`.

Remaining gaps:

- no research-specific CI health gate;
- no deployed stack E2E matrix;
- no data-quality/replay-coverage gate;
- external Sonar runner is environment-dependent.

Historical reports claiming no CI are stale relative to current HEAD.

## 73. Documentation Consistency

README and current code agree on many important facts: deterministic risk,
human authority, PAPER entry, incomplete AI, and incomplete end-to-end/live
validation.

Some older reports are stale, especially reports predating merged Gateway,
identity, News, Order Book, and research work. Future reports must include a
commit/runtime timestamp and distinguish historical evidence from current code.

## 74. ADR Backlog

Maximum five decisions are warranted:

1. **Research lifecycle run health and failure semantics**: when a successful
   scheduler invocation with zero output is degraded or failed.
2. **User-facing provenance contract**: the minimum evidence/version/freshness
   chain exposed by opportunities, plans, and decisions.
3. **PAPER/shadow decision journal boundary**: what is durable and who owns the
   user decision, simulation, and outcome records.
4. **Economic research dataset separation**: independent objective/economic
   manifests and stable joins.
5. **Evidence retention policy**: only after storage-growth measurements exist.

No ADR is needed for ordinary frontend navigation or read-only presentation.

## 75. Technical Debt Triage

### P0 — correctness/data integrity

- Do not allow research run health to appear successful when required outputs
  silently fail.
- Preserve exact S1 feature/outcome semantics and risk/broker authority.

### P1 — near-term blocker

- Research accumulation health and replay/fee coverage visibility.
- Complete PAPER close/settlement loop.
- Authenticated deployed-path proof for the decision/execution journey.
- Durable Market Intelligence user-visible state where restart loses truth.

### P2 — worthwhile

- News stale-cache semantics;
- provenance UI;
- opportunity pagination/filtering;
- execution-history analytics;
- data-growth measurement;
- frontend subscription consolidation;
- service capability/health matrix.

### P3 — later/cosmetic

- broad visual redesign;
- multi-broker expansion;
- generic event broker;
- RAG and ML;
- retention compaction before measurements.

## 76. Current S1 Accumulation Health

```text
S1 ACCUMULATION HEALTH

Latest FeatureSnapshot = 2026-10-19T21:00:00Z
Latest PT1H outcome = 2026-10-19T20:00:00Z (AVAILABLE)
Current total snapshots = 75
Current eligible S1 snapshots = 75
Current LONG signals = 13
Current SHORT signals = 13
Current NO_TRADE = 49
Elapsed pre-registration accumulation = less than one day at investigation time
Trusted replay coverage = 5/5 Q1 paired samples validated; current S1 signal coverage not measured here
Fee coverage = one applicable historical fee schedule; Q1 fee resolution validated
Funding coverage = zero historical funding rows
Acquisition status = PASS, with run-level observability gaps
```

The health conclusion is about acquisition/readiness only. No S1 performance
was evaluated.

## 77. Development Opportunity Matrix

Scores use 1–5, with higher risk meaning more risk. Priority is a transparent
heuristic:

```text
Product + Architecture + Research + Operations - Implementation Risk - S1 Risk
```

| Story | Problem | Dependencies | Affected modules | S1 classification | Risk | Effort | ADR |
|---|---|---|---|---|---:|---|---|
| Research accumulation health and readiness | Silent/degraded runs are hard to distinguish from healthy accumulation | Existing lifecycle/readiness | MI research, Market Data read-only diagnostics, Broker fee read-only | SAFE_WITH_GUARDRAILS | 2 / S1:1 | M | Yes |
| Complete PAPER position lifecycle | Entry exists but close/settlement/feedback loop is incomplete | Trading Core execution, existing frontend | Trading Core, Web, Market Data reads | SAFE_WITH_GUARDRAILS | 4 / S1:2 | L | Maybe |
| Deterministic decision workspace completion | Existing context/news/provenance is fragmented | Existing MI/News APIs | MI, News, Web, Gateway | SAFE_DURING_S1_ACCUMULATION | 3 / S1:1 | M | No |
| Durable MI user-visible artifacts | Restart loses selected artifacts/projections/metrics | Existing repositories and migrations | MI persistence/configuration | SAFE_WITH_GUARDRAILS | 4 / S1:2 | L | Yes |
| Gateway/PAPER deployed contract proof | Isolated tests do not prove user journey | Docker runtime, test fixtures | Gateway, Trading Core, Broker, Web | SAFE_WITH_GUARDRAILS | 4 / S1:2 | M | No |
| Opportunity query/provenance UX | Active list lacks filtering, pagination, origin | Existing paginated API | MI Web/Gateway | SAFE_DURING_S1_ACCUMULATION | 2 / S1:1 | M | No |
| Execution history and trade analytics | `/analytics` is history only | Trade outcomes, PAPER settlement | Trading Core, Web | SAFE_WITH_GUARDRAILS | 3 / S1:2 | M | Maybe |
| News stale-cache and relevance | Provider absence can look like no data | News contracts, MI context | News, MI, Web | SAFE_DURING_S1_ACCUMULATION | 3 / S1:1 | M | No |
| Research-specific CI/data-quality gates | Accidental evaluation or broken evidence is not gated | Existing CI scripts | CI, MI/Market Data tests | SAFE_WITH_GUARDRAILS | 2 / S1:1 | S | No |
| Order Book storage-growth measurement | Event data can grow without a policy | Runtime metrics, read-only SQL | Market Data operations | SAFE_WITH_GUARDRAILS | 2 / S1:1 | S | No |
| Position monitoring recommendations | No active thesis/risk monitoring | Durable journal, position state | MI, Trading Core, Web | SAFE_WITH_GUARDRAILS | 4 / S1:2 | L | Yes |
| Production network/secret hardening | Internal services are directly exposed in Compose | Deployment policy | Compose, Gateway, service security | SAFE_WITH_GUARDRAILS | 4 / S1:1 | M | Maybe |
| Historical funding acquisition | Fully-costed perpetual economics unavailable | Provider semantics and capture | Market Data, Broker, Trading Core | DEFER_UNTIL_S1_FROZEN | 4 / S1:4 | L | Yes |
| AI Engine integration | No real AI adapter exists | Mature deterministic context/news | MI, future service, Web | DEFER_UNTIL_S1_FROZEN | 5 / S1:5 | XL | Yes |
| Multi-broker expansion | Current product is Kraken-shaped | Stable generic contracts and sandbox proof | Broker, Market Data, Core, Web | DEFER_UNTIL_S1_FROZEN | 5 / S1:4 | XL | Yes |

## 78. Priority Ranking

| Rank | Story | Product | Architecture | Research | Ops | Risk | S1 Risk | Effort | Classification | Priority |
|---:|---|---:|---:|---:|---:|---:|---:|---|---|---:|
| 1 | Research accumulation health and readiness | 3 | 5 | 5 | 5 | 2 | 1 | M | SAFE_WITH_GUARDRAILS | 15 |
| 2 | Research-specific CI/data-quality gates | 2 | 4 | 5 | 4 | 2 | 1 | S | SAFE_WITH_GUARDRAILS | 12 |
| 3 | Order Book storage-growth measurement | 2 | 3 | 4 | 5 | 2 | 1 | S | SAFE_WITH_GUARDRAILS | 11 |
| 4 | Deterministic decision workspace completion | 5 | 4 | 3 | 3 | 3 | 1 | M | SAFE_DURING_S1_ACCUMULATION | 11 |
| 5 | Durable MI user-visible artifacts | 4 | 5 | 4 | 4 | 4 | 2 | L | SAFE_WITH_GUARDRAILS | 11 |
| 6 | Complete PAPER position lifecycle | 5 | 5 | 2 | 4 | 4 | 2 | L | SAFE_WITH_GUARDRAILS | 10 |
| 7 | Gateway/PAPER deployed contract proof | 4 | 4 | 2 | 5 | 4 | 2 | M | SAFE_WITH_GUARDRAILS | 9 |
| 8 | Opportunity query/provenance UX | 4 | 3 | 2 | 2 | 2 | 1 | M | SAFE_DURING_S1_ACCUMULATION | 8 |
| 9 | Execution history and trade analytics | 4 | 4 | 2 | 3 | 3 | 2 | M | SAFE_WITH_GUARDRAILS | 8 |
| 10 | News stale-cache and relevance | 3 | 3 | 2 | 4 | 3 | 1 | M | SAFE_DURING_S1_ACCUMULATION | 8 |

The numerical ranking is not a substitute for sequencing. CI/data-quality and
storage measurement can be folded into the first Story without becoming
separate product workstreams.

Top five rationale:

1. Accumulation health protects the validity of the current scarce evidence.
2. CI/data-quality gates prevent accidental evaluation and catch evidence drift.
3. Storage measurement protects the growing Order Book evidence base.
4. Decision Workspace uses existing capabilities with low semantic risk.
5. Durable MI state prevents restart loss and supports user trust.

### Best-next Story definition

**Title:** Make S1 accumulation health and research readiness operationally
observable.

**Problem statement:** The passive lifecycle can catch projection and outcome
errors while continuing, and the current readiness view does not expose run
status, replay coverage, fee coverage, funding availability, or failure reason
counts. A healthy-looking service can therefore accumulate incomplete evidence
without a durable operational explanation.

**Scope:** Add read-only run accounting and readiness projection for lifecycle
submissions, completed/failed projections, matured outcomes, Order Book replay
coverage, historical fee coverage, funding availability, and degraded reasons.
Expose an internal status response and logs/metrics sufficient to answer
whether accumulation is healthy.

**Out of scope:** Changing scheduler cadence, cutoff alignment, feature values,
FeatureProjection V1, outcome semantics, replay rules, fee rules, funding
meaning, S1 signal rules, S1 evaluation, live execution, or introducing a
message broker.

**Likely modules:**

- `market-intelligence` research lifecycle/readiness application services;
- read-only Market Data replay/readiness client;
- read-only Broker fee coverage client;
- focused tests and internal response DTOs.

**Key invariants:**

- no replacement or latest-row selection;
- no mutation of immutable evidence;
- unavailable funding remains unavailable, never zero;
- no S1 P&L/return/win-rate computation;
- degraded or zero-output runs are explicit;
- readiness remains separate from strategy performance.

**Acceptance criteria:**

1. Each lifecycle run has an observable start/end/status and counts for
   submissions, projection successes/failures, and outcome evaluations.
2. Projection or outcome exceptions are represented in run health rather than
   only logged.
3. Readiness reports latest successful run, stale age, snapshot counts,
   outcome maturity, replay coverage, fee coverage, and funding availability.
4. The response distinguishes unavailable, incomplete, invalidated, stale, and
   persistence-failure reasons.
5. Existing FeatureSnapshot, ObservedMarketOutcome, replay, fee, and S1
   fingerprints remain unchanged.
6. Tests prove the status is truthful for zero-output, degraded, and healthy
   runs.

**Runtime validation:** Run the existing passive lifecycle against the current
   local stack, verify the status endpoint, compare counts with PostgreSQL,
   and confirm no new S1 economic result is produced.

**S1 protection requirements:** The implementation must not read or calculate
   S1 outcome performance. It may count feature-only signal readiness and
   evidence coverage only. Changes to protected research semantics require a
   separate Story and, if material, a new versioned experiment.

**ADR requirement:** Yes. Record the lifecycle run-health and degraded-state
   semantics before introducing durable run state or changing operational
   interpretation.

## 79. Recommended Parallel Workstreams

Maximum three:

1. **Research Operations:** accumulation health, replay/fee readiness, CI/data
   quality, storage measurement.
2. **Product Workflow:** PAPER lifecycle, decision workspace, trade history,
   provenance.
3. **Context Quality:** News stale semantics, macro relevance, capability
   status, only if the first two streams remain isolated from research truth.

One developer should execute these sequentially or with narrow AI assistance;
they should not become three simultaneous architectural rewrites.

## 80. Best Next Story

**Make S1 accumulation health and research readiness operationally observable.**

Why it beats the alternatives:

- it protects the only currently active experiment without evaluating it;
- current lifecycle exceptions can be logged while output silently fails;
- current readiness omits replay, fee, funding, and run-health coverage;
- it is smaller and safer than PAPER or persistence work;
- it creates an objective interruption signal for when S1 may be frozen;
- it does not alter FeatureProjection, outcomes, replay, fees, or S1 rules.

## 81. Second Next Story

**Complete the PAPER position lifecycle and post-trade feedback loop.**

This is the strongest product-value Story after accumulation health: persisted
PAPER close/settlement, outcome visibility, and user-facing history, without
touching LIVE broker authority or research semantics.

## 82. Third Next Story

**Complete the deterministic Decision Workspace with provenance and context
status.**

Use existing Market Intelligence, Market Data, News, risk, and TradePlan
contracts to make one coherent human decision surface.

## 83. Development Arc During Accumulation

```text
Story A: accumulation health/readiness observability
    → Story B: complete isolated PAPER lifecycle and feedback
    → Story C: deterministic Decision Workspace/provenance completion
    → evaluate S1 when its pre-registered gates are satisfied
```

This arc remains valid if S1 reaches readiness earlier: finish only the current
safe atomic Story, freeze the population, evaluate S1, then resume the next
Story after preserving the research semantics.

## 84. S1 Readiness Interruption Rule

Pause development for S1 when all pre-registered gates are satisfied:

- at least 60 eligible signals;
- at least 30 LONG and 30 SHORT;
- at least 14 chronological days;
- at least two regimes;
- at least 90% trusted T0/T1 replay coverage;
- complete historical fee coverage.

At that point:

1. finish or safely stop the current atomic Story;
2. do not change protected research semantics;
3. freeze exact selected snapshot/outcome identities;
4. execute S1 exactly as pre-registered;
5. record funding as unavailable if still absent;
6. resume development only after the S1 result is preserved.

## 85. Things Not to Build Now

1. S1 optimizer, threshold search, or profitability dashboard.
2. FeatureProjection V2 or Order Book features in V1.
3. ML model training.
4. LLM agent or RAG platform.
5. Full live automation.
6. Multi-broker expansion.
7. Kafka/RabbitMQ or Kubernetes without measured need.
8. Retention/compaction before growth measurements.
9. Generic alert/notification infrastructure before event ownership is clear.
10. Broad cosmetic frontend redesign.

## 86. Do During S1 Accumulation

1. Research lifecycle/run health and readiness coverage.
2. CI/data-quality safeguards that cannot inspect or optimize S1 performance.
3. PAPER close/settlement and decision feedback, isolated from research.
4. Deterministic Decision Workspace/provenance UX.
5. Opportunity filtering/pagination and truthful status rendering.
6. News stale/unavailable semantics and deterministic event context.
7. Read-only runtime capability and operational views.

## 87. Do After S1

1. S1 canonical objective/economic evaluation.
2. Out-of-sample and walk-forward planning based on observed readiness.
3. Independent economic dataset/export contract.
4. Funding acquisition and fully-costed profitability work.
5. Strategy comparison, robustness, and cost sensitivity.

## 88. Later

1. Position monitoring recommendations.
2. AI synthesis/explanation.
3. RAG and historical similarity.
4. Multi-broker/prop support.
5. Retention/archival and scale architecture.
6. Limited semi-automatic live workflows after sandbox and deployed proof.

## 89. Investigation Artifact

Created:

`docs/architecture/reports/s1-accumulation-development-investigation.md`

This report is the only intended repository change.

## 90. Repository Protection

- No source, configuration, migration, or test file was changed.
- No S1 performance was evaluated.
- No S1 rules, features, horizon, quantity, baseline, or failure criterion were
  changed.
- No live order, account mutation, or broker mutation occurred.
- `docs/architecture/reports/v1-functional-audit.md` remains untouched,
  untracked, and unstaged.
- No secrets or runtime temporary files were added.

## 91. Commit

The investigation artifact is intended for the focused commit:

```text
docs: investigate development priorities during S1 accumulation
```

No push is performed.

## 92. Final Decision

```text
A — DEVELOPMENT_BACKLOG_IDENTIFIED_S1_ACCUMULATION_HEALTHY
```

## 93. Exact Next Action

Create and approve a Story for **research accumulation health and readiness
observability**. Its implementation must be read-only with respect to S1
semantics: add run/status/coverage accounting and truthful degraded states,
without changing acquisition, FeatureProjection, outcome, replay, fee, or
pre-registration behavior. After that Story, implement the isolated PAPER
position lifecycle and then the deterministic Decision Workspace arc.

## Required Executive Summary

```text
S1 ACCUMULATION DEVELOPMENT STRATEGY

S1 accumulation healthy = YES
Safe high-value development exists = YES
Additional research architecture needed now = NO
Live trading should be implemented now = NO
ML should be implemented now = NO
LLM should be implemented now = NO
Historical funding is immediate priority = NO

BEST NEXT STORY = Research accumulation health and readiness observability
SECOND NEXT STORY = Complete PAPER position lifecycle and post-trade feedback
THIRD NEXT STORY = Complete deterministic Decision Workspace and provenance

PRIMARY WORKSTREAM = Research Operations
SECONDARY WORKSTREAM = Product Workflow
OPTIONAL THIRD WORKSTREAM = Deterministic Context Quality

FINAL DECISION = A — DEVELOPMENT_BACKLOG_IDENTIFIED_S1_ACCUMULATION_HEALTHY

EXACT NEXT ACTION = Prepare the first Story with the S1 guardrails above;
                    do not implement it as part of this investigation.
```
