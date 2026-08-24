# Trading OS Resumption Investigation — Project State Recovery

- **Date:** 2026-08-24
- **Scope:** Full-state investigation of Trading OS after a development pause dedicated to DevLog AI. Sources: DevLog MCP (`get_engineering_context`, `search_project_history`, commit resources), repository `main` @ `010ed05`, local validation runs.
- **Constraint:** Investigation only. No functional code was modified, no Story artifacts were created, no branch was created.

---

# Executive Summary

Trading OS is an **engineering prototype with a nearly complete deterministic backend decision chain**: Kraken market data → active scan → deterministic capabilities → observation → strategy evaluation → StrategyMatch → TradingOpportunity → TradePlan → Risk Domain → execution intent → broker adapter.

Development did **not** stop where DevLog suggests (Story 0004, Aug 7). Between Aug 21 and Aug 24, twelve additional stories landed (Stories 0007–0020), covering the strategy domain chain (ADR-034..038), public Gateway routing for the trader journey (Story 0019), and a full quality baseline with blocking coverage and SonarQube gates (Story 0020, ADR-039).

The authoritative frontier document is now `docs/TRADING_OS_V1_TRADER_DEFINITION_OF_DONE.md` (2026-08-23): **V1 closes when the trader journey works through the product, not when the backend is complete.** Its closure blocker #1 (Gateway routing) is resolved. The next blocker in journey order is **opportunity surfacing in the UI** — the backend opportunity API is complete, versioned, expirable and publicly routed, but the Angular app has zero Intelligence surface.

The quality baseline is fully green on `main` (all backend modules, 159 frontend tests, production build).

---

# Current Architecture

Verified against repository HEAD (`010ed05`, branch `main`, clean tree).

| Component | State | Evidence |
|---|---|---|
| **Gateway** | implemented | Public route table per domain prefix, no rewrite (Story 0019); JWT security package; integration tests prove real request → downstream routing for `/api/v1/opportunities/**`, `/api/v1/intelligence/**`, `/api/v1/trade-plans/**` |
| **Eureka Server** | implemented | Basic discovery service; covered by tests |
| **Trading Core** | implemented | Auth/users (JWT), accounts, broker-account references, trades/stats, dashboard aggregation; full execution domain per ADR-029 (pipeline + recovery pipeline); risk orchestration per ADR-028/031/032; trade-plan orchestration |
| **Broker Service** | implemented (Kraken only) | Broker-neutral domain (provider registry, capabilities), Kraken infrastructure adapter (signing, resilient client, mappers), credential/connection bounded context with secret rotation; legacy packages coexist |
| **Market Data** | implemented | Catalogue, live ticker/OHLC/order book/recent trades via Kraken WS+REST, dynamic subscriptions, valuation snapshots, history, on-demand snapshot acquisition (Story 0008); older package style |
| **Market Intelligence** | implemented (deterministic) | Analysis executions, artifact reuse, capabilities, observations, opportunities + expiration driver, trade plans, active scans (lifecycle/dispatch/reconciliation/projection), strategy domain with persisted definitions as runtime source of truth (ADR-037), governance split (ADR-036), deterministic evaluators, StrategyMatch provenance (ADR-034/035); `adapter/ai` package exists but no real AI engine behind it |
| **Risk Domain** | implemented | Pure deterministic library: engine, rules (daily drawdown, max exposure, max position risk), metric projection, policy resolution, decision explainer, audit/replay; consumed by Trading Core as a jar dependency |
| **trading-os-web (Angular)** | partial | Standalone components, reactive services, auth/accounts/dashboard/markets with live WebSocket stream; **no Market Intelligence surface at all** |
| News Service, passive scanner, real AI Engine | planned only | ADR-008 vision, DoD §9 non-blocking gaps |

Planned-only items from old roadmaps (News, AI Engine, passive scanning) must not be assumed present — they are not.

---

# Current Development Frontier

**Where development stopped:** Story 0020 — Quality Baseline (merged 2026-08-24, PR #16).

The last coherent completed sequence:

1. **Aug 21** — Stories 0007–0011: active scan lifecycle/result projection, on-demand market snapshot acquisition, strategy domain foundation, deterministic strategy evaluator, StrategyMatch persistence (ADRs 034).
2. **Aug 22** — Stories 0012–0016: opportunity derived from required StrategyMatch truth, first CI quality gates, strategy-agnostic evaluation pipeline (ADR-035), second strategy + governance split (ADR-036), persisted strategies as runtime source of truth (ADR-037).
3. **Aug 23** — Stories 0017–0019: empirical strategy validation boundary (ADR-038), opportunity expiration driver, **public Gateway routing of trader-journey APIs**, and the **V1 Trader Definition of Done** reference document.
4. **Aug 24** — Story 0020: quality baseline — JaCoCo LINE ≥ 80% blocking gates (backend + gateway), self-hosted SonarQube blocking gate (ADR-039), comprehensive backend tests across all modules, 159-test frontend baseline, KrakenStreamConnector injection fix.

The project then pivoted from "backend capability chains" to "product surfacing of those chains", formalized by the Definition of Done document.

---

# Trading Core

Implemented and tested:

- **Identity & ownership:** users/auth (JWT), accounts, broker-account references.
- **Execution domain (ADR-029):** aggregate-rooted intents/attempts/broker orders; application pipeline (validation → idempotency verification → attempt creation → broker submission → response processing → finalization); recovery pipeline (discovery → inspection → reconciliation strategy → finalization); explicit unknown-outcome handling; idempotency keys; observability metrics; persistent events.
- **Human-validation gate (Story 0004 / ADR-032):** `ValidateAndCreateService` validates that a trade plan is risk-approved before creating the execution intent; entry intent modeled explicitly.
- **Risk orchestration (ADR-028/031):** `TradePlanRiskEvaluationService` (+559 lines over 3 commits), `TradePlanRiskPort`, `MarketIntelligenceRiskClient`, persistent risk records; refusal blocks execution (verified by tests).
- **Trade planning context:** `AnalysisTradePlanController` (public `/api/v1/trade-plans/**`), planning profiles with persistence, MI client.
- Legacy flatter packages (`controller/`, `dto/`, `service/`, `market_data/` client) coexist with the new hexagonal packages — see Open Challenges.

Validation: `mvnw test` green.

# Broker Service

- **Neutral core:** provider registry/resolver, capability model, domain exceptions/models, operations + query controllers, metrics and health indicators.
- **Kraken adapter (isolated):** credential sessions and request signing, resilient REST client, order/asset mappers, capabilities incl. risk-snapshot.
- **Credentials & connection:** bounded context for credential submission/validation with rate limiting, ownership checks vs Trading Core, stored-secret source with rotation (`broker_secret`, optimistic `row_version`), environment fallback source, safe provider diagnostics.
- Known limits (accepted, per DoD §E): broker execution has never been proven E2E against a Kraken-compatible sandbox; automatic reconciliation scheduling absent (recovery pipeline exists in Trading Core but trigger path is manual).
- Legacy packages (`controller/BrokerController`, `brokerClient/`, `dto/`) coexist with the new structure.

Validation: `mvnw test` green (incl. new Story-0020 test classes for signing, credentials, exception handling).

# Market Data

- Responsibilities match AGENTS.md: catalogue, market state, ticker, OHLC, order book reconstruction, recent trades, normalized price snapshots, valuation snapshots, history, dynamic subscriptions, freshness handling.
- Providers: Kraken WS + REST, isolated under `kraken/`; malformed-message and non-subscribed-market cases handled deterministically (negative tests prove no silent data).
- On-demand current snapshot acquisition added (Story 0008) — used by MI context assembly.
- Internal endpoints (`InternalMarketController`, `InternalValuationController`) serve Trading Core / MI; public API under `/api/v1/markets/**`; frontend WebSocket routed at `/ws/market-data`.
- Package layout remains the older flat style (no DDD restructuring) — acceptable, not part of any current story.

Validation: `mvnw test` green.

# Frontend (trading-os-web)

- Angular standalone, dark dashboard style, reactive flows with Observables/async pipe.
- Routes: home, login/register (guards), dashboard, accounts, markets + market detail (live data through `market-data-stream.service` WebSocket).
- Core services: auth, token, account, broker-account, dashboard, market, market-data-stream — all typed and tested.
- **Zero Market Intelligence surface** (verified: no reference to opportunities/intelligence/trade-plans/scans anywhere in `src/app`). This is the DoD's biggest open gap.
- 159 unit tests passing; production build succeeds with one pre-existing budget warning (initial bundle 553.46 kB > 500 kB budget).

# AI / Analytics

- **Implemented:** deterministic intelligence only — capability orchestration, observations, strategy evaluation (deterministic evaluators: e.g., OHLC range expansion), StrategyMatch provenance, opportunity derivation, trade-plan generation, shadow parity monitor (`ShadowStrategyParityMonitor`) preparing future strategy comparison.
- **Foundation without implementation:** `adapter/ai` package exists in MI but no AI engine sits behind it; ADR-003 (AI-first) remains architectural vision.
- **Explicit honesty boundary (ADR-038):** 0 empirically VALIDATED strategies; only `LEGACY_OHLC_TREND` runs under `BOOTSTRAP_CONTROLLED_RUN` (unvalidated fixture). V1 closure requires an explicit human decision on this exception (DoD §C).
- Do not treat AI-first plans as existing capabilities.

# Infrastructure

- Docker Compose: PostgreSQL ×4 (one per stateful service), trading-core, broker-service, eureka, gateway, web (nginx), market-data, market-intelligence. Default host ports moved to the 1708x range (2026-08-21).
- CI: GitHub Actions `quality.yml` — build/tests + JaCoCo LINE ≥80% gates + self-hosted SonarQube blocking gate (ADR-039); `docker-compose.sonar.yml` provided.
- Local tooling: `scripts/test-all.sh` (note: does not run risk-domain tests — it has no Maven wrapper), `quality-verify.sh`, `quality-scan.sh`, `check-backend-coverage.py`.
- Migrations: Flyway everywhere relevant (MI currently at v7 — strategy match persistence & governance split).
- No deployed environment; E2E against Kraken sandbox still pending (DoD §E).

---

# Recent Engineering History

Reconstructed from local Git (DevLog index stops earlier — see Gaps):

| Period | Work | References |
|---|---|---|
| Jul 29 – Aug 1 | MI foundation wave: ADRs 020–030 implemented (context assembly, artifacts, capability execution, observations, opportunities, trade planning, risk roadmap, broker architecture) + AI-assisted engineering workflow (ADR-030) | commits `5fda25d`, `278eca5`, `d3bc6bf`, `3009ae6`, `e897e90`, `624b681`, `2e2fc06`, `08e3a67`, `aebe5ba` |
| Aug 4 | Story 0002 — production trade-planning pipeline | commit `f830257` |
| Aug 6–7 | Architecture docs restructure (concepts/domains/diagrams/stories), ADR-032, Story 0004 — approved risk evaluation → human validation → execution | commits `f774ad0`, `55fac35`, `eb9b460` |
| Aug 21–22 | Stories 0007–0012 + ADR-034/035: scan lifecycle, snapshot acquisition, strategy domain chain up to opportunity-from-StrategyMatch; first quality-gate CI; ports migration | PRs #3–#8 |
| Aug 22–23 | Stories 0013–0018 + ADR-036/037/038: strategy-agnostic pipeline, second strategy, governance split, persisted source of truth, empirical validation boundary, expiration driver | PRs #9–#13 |
| Aug 23 | Story 0019 + ADR-039 groundwork: public gateway routing of trader-journey APIs; V1 Trader Definition of Done adopted | PR #14, commit `a1df44a` |
| Aug 24 | Story 0020: quality baseline (JaCoCo ≥80% gates, SonarQube blocking CI, cross-module tests, frontend baseline) | PR #16 |

Key decisions to honor going forward: ADR-034..038 (strategy boundaries and honesty rules), the V1 Definition of Done (journey-order prioritization), ADR-029 (execution safety), ADR-031/032 (risk/planning responsibility split).

---

# Open Challenges

Re-classified after code verification (historical labels deliberately ignored):

**BLOCKING (for V1 closure, in DoD journey order)**
1. **No Market Intelligence product surface** — verified: nothing in the Angular app consumes opportunities/scans/trade-plans even though the APIs are now publicly routed. Journey steps 4–7 are invisible to the trader. *(Blocker #1 — Gateway routing — was resolved by Story 0019.)*
2. **Kraken execution never proven E2E** — mocks/unit tests only; DoD §E requires sandbox proof incl. idempotency and unknown-outcome drill.
3. **Strategy legitimacy human decision pending** — DoD §C requires an explicit, recorded choice between time-boxed legacy exception or blocked closure.

**IMPORTANT**
4. Reconciliation trigger is manual (recovery pipeline exists; no scheduler/procedure documented as acceptable for V1).
5. External analysis failures not yet surfaced to the trader (DoD §G).
6. Post-restart catch-up semantics undocumented (expiration driver is restart-safe, but behavior contract should be written down).

**TECHNICAL_DEBT**
7. Legacy flat packages coexist with hexagonal ones in broker-service (`controller/`, `brokerClient/`, `dto/`) and trading-core (`controller/`, `dto/`, `market_data/` client) — confusing for newcomers, no active harm.
8. Stories 0013–0020 have no workflow artifacts in `docs/architecture/stories/` (only ADRs + PRs record them); breaks the traceability convention the repo itself defines.
9. `scripts/test-all.sh` omits risk-domain tests (module has no wrapper; script never runs them).
10. Frontend initial bundle exceeds its 500 kB budget (553 kB) — warning only today.
11. Minor doc defects: duplicated "Engineering Reports" section appended to `stories/README.md`; audit reports cited by the DoD (`TRADING_OS_CAPABILITY_CONTEXT_REPORT`, `TRADING_OS_RUNTIME_CAPABILITY_AUDIT`) are not committed in the repo.
12. Cosmetic log noise: H2-vs-Flyway version warnings, explicit Hibernate dialect warnings.

**INFORMATIONAL**
13. Mono-instance scheduling assumptions; observability platform absent — accepted post-V1.

None of these contradict the architecture; items 1–3 define the remaining V1 work.

---

# Quality Baseline

Executed locally on `main` @ `010ed05`, clean tree, 2026-08-24:

| Module | Command | Result |
|---|---|---|
| eureka-server | `mvnw test` | PASS |
| gateway | `mvnw test` (incl. routing integration tests) | PASS |
| broker-service | `mvnw test` | PASS |
| market-data | `mvnw test` | PASS |
| trading-core | `mvnw test` | PASS |
| market-intelligence | `mvnw test` | PASS |
| risk-domain | `mvn test` (system Maven; no wrapper) | PASS |
| trading-os-web | `npm run test:ci` | PASS — 26 files, 159 tests |
| trading-os-web | `npm run build` | PASS (budget warning, pre-existing) |
| working tree | `git diff --check` | CLEAN |

CI status: `.github/workflows/quality.yml` adds JaCoCo ≥80% LINE blocking gates and a self-hosted SonarQube blocking gate (Story 0020). The pipeline is green; no pre-existing failure to investigate. The only warning is the pre-existing frontend bundle-budget overrun.

---

# DevLog MCP Usage

Actual usage during this investigation:

- **`get_engineering_context`** — 1 call with a resumption intent. Returned 60 evidence items (from 137 candidates) dominated by Story 0004 artifacts, ADR-032, MI pipeline commits (ADR-020..030 era) and one project-overview insight. Selection reasons (`SELECTED_BY_RANK/DIVERSITY/STRONG_RELEVANCE`) and resolved revision metadata were present and coherent. **Useful for the July–early-August arc; stale relative to HEAD.**
- **Resources (`resources/read`)** — 1 commit resource opened (`f830257…`, Story 0002). Payload ≈100 KB JSON, truncated by the client; the commit subject plus local `git show --stat` answered the question faster. Resources work but raw commit dumps are heavy; summaries were sufficient.
- **`search_project_history`** — 4 calls:
  - `"approved risk evaluation human validation execution ValidateAndCreate"` → 1 precise hit (commit `eb9b460`, Story 0004). Good precision.
  - `"quality baseline JaCoCo SonarQube coverage"` → 0 hits.
  - `"strategy evaluator StrategyMatch opportunity"` → 0 hits.
  - `"opportunities gateway routing"` → 0 hits.
- **Git fallbacks (required)** — for every question touching Aug 8–24 work:
  - *Question:* "What is the actual development frontier?" → DevLog queries returned nothing past Story 0004 → insufficient because recent commits appear unindexed → `git log --pretty=... -40` + `ls docs/architecture/stories/` + PR merge inspection → discovered Stories 0007–0020, DoD adoption, quality baseline.

**Behavioral observation:** the choice between `get_engineering_context` (orientation), `search_project_history` (targeted historical questions) and direct repository reading (current truth) happened naturally along the mission's intended workflow; no tool misuse occurred. The single decisive limitation is **index freshness/coverage of recent commits**, which forced Git for the entire last week of work — exactly the period that matters most for a resumption.

---

# Gaps Between DevLog and Repository

Recorded only — intentionally not corrected during this investigation.

- **Stale:** index effectively stops at commit `eb9b460` (2026-08-07, Story 0004). All Stories 0005–0020, ADRs 033–039, the Gateway routing fix, the strategy domain and the quality baseline are invisible to DevLog. The only newer item is an auto-generated insight (2026-08-18) of generic content.
- **Missing:** no evidence for the V1 Definition of Done adoption — arguably the single most important project decision of the pause period.
- **Incorrect/incomplete metadata:** project description mentions "Python" and "FTMO challenges"; the repository contains no Python service and FTMO-specific logic is not visible in code. Description reflects early vision more than the current system.
- **Incomplete:** engineering-context evidence is documentation-heavy (story/ADR files dominate) relative to code-change evidence for the same commits; fine for orientation, weaker for technical archaeology.

---

# Recommended Next Engineering Story

One small story, proposed for human review (artifacts deliberately NOT generated here).

```text
proposed title:
    Surface active Market Intelligence opportunities in the Web app

problem:
    The backend produces persisted, versioned, expirable opportunities with
    strategy provenance and evidence, publicly reachable since Story 0019
    (/api/v1/opportunities/**). The Angular app has no Intelligence surface,
    so the trader cannot see any of it. In the V1 Definition of Done this is
    closure blocker #2 (journey steps 5–6).

objective:
    An authenticated trader can list ACTIVE opportunities and open one to
    understand why it exists — entirely through the product.

scope:
    - New Angular feature (e.g. features/intelligence/opportunities):
      typed opportunity service against /api/v1/opportunities,
      list view (status + validity window), detail view
      (evidence/explanation, strategy provenance).
    - Guarded routes consistent with existing conventions;
      reactive flow with Observables + async pipe;
      explicit loading / error / empty states.
    - Frontend unit tests maintaining the ≥80% LINE baseline.

out of scope:
    - Scan triggering UI, trade-plan creation UI, risk-decision display,
      execution surface (later journey steps).
    - Any backend change beyond reading the existing public contract.
    - AI-generated explanations; pagination beyond the existing API shape.

high-level acceptance criteria:
    - Trader sees real persisted opportunities in the product; expired ones
      are never presented as active.
    - Detail view renders the explanation/evidence fields served by MI.
    - No internal port access or developer tooling required (DoD rule).
    - Frontend tests pass and quality gates stay green.

reason why this should be next:
    It directly extends what Story 0019 just unblocked, it is the highest
    journey-ordered blocker still open, it is read-only (zero risk-surface
    impact, no deterministic-core change), and it delivers the first visible
    product value of the entire Market Intelligence chain — the smallest
    step that turns the ENGINEERING PROTOTYPE toward a usable trader runtime.
```

Alternative considered and rejected for now: scan-trigger UI (next in journey order after this one, but larger scope: scope selection + long-running operation feedback).

---

# Success Criterion Answer

**Where is Trading OS really?** Backend decision chain complete and quality-gated (ENGINEERING PROTOTYPE, all modules green); **development stopped** at Story 0020 (quality baseline, 2026-08-24) right after adopting a journey-driven V1 Definition of Done; **the next small story** is surfacing active opportunities in the Angular app — the first unresolved blocker in trader-journey order, directly enabled by the just-completed Gateway routing work.
