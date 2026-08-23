# Trading OS V1 — Trader Definition of Done

## Status

Reference document — governs when Trading OS V1 is considered closed from the
trader-user perspective.

## Date

2026-08-23

---

## 1. Purpose

This document defines when **Trading OS V1 is finished as a product**, not as
a codebase.

> **Trading OS V1 is not closed when the backend is technically complete.
> V1 is closed when a trader can perform the primary trading journey through
> the product, without developer tooling, and with acceptable operational
> reliability.**

Developer tooling — curl, Postman, direct access to internal service ports,
database inspection, code modification — must NOT be required to use the main
V1 workflow.

This document formalizes a priority shift:

```text
Former question: "What is the next architectural gap?"
New question:    "What is the next obstacle in the real trader journey?"
```

From now on, every V1 story is evaluated by its impact on actual product use.

## 2. V1 product definition

```text
Trading OS V1 = Trader Runtime

Market → Intelligence → Opportunity → Risk → Human Trader
```

The trader opens Trading OS, discovers what the market offers, understands why,
decides, and acts — through the product.

```text
Trading OS V2 = Quant Researcher (ResearchQuestion → Hypothesis → Experiment
→ Finding → Evidence → possible Strategy)

Developer OS   = Developer workflow (external tooling)
```

V2 concepts must NOT leak into the V1 closure scope (see §16).

## 3. Current usability baseline

Established by the cross-audit (`TRADING_OS_CAPABILITY_CONTEXT_REPORT`,
`TRADING_OS_RUNTIME_CAPABILITY_AUDIT`) against repository HEAD:

- Current classification: **ENGINEERING PROTOTYPE**.
- Backend chain largely present end-to-end:
  Kraken market data → active scan → deterministic capabilities →
  Observation/Evidence → strategy evaluation → StrategyMatch →
  TradingOpportunity → TradePlan → Risk → Execution intent → Broker.
- Hard user-facing ruptures:
  - no UI to trigger a scan;
  - no Market Intelligence UI at all (opportunities, strategies, plans);
  - Gateway routing broken for `/opportunities` and MI `/trade-plans`
    (no matching route, no StripPrefix);
  - broker execution never validated E2E against Kraken;
  - automatic reconciliation absent (timeout recovery is manual);
  - zero normal strategies VALIDATED + ENABLED; only LEGACY_OHLC_TREND runs
    under BOOTSTRAP_CONTROLLED_RUN (explicitly UNVALIDATED fixture);
  - passive scanner absent; AI absent.

## 4. Trader journey (target V1 workflow)

Primary scenario:

> The user opens Trading OS and wants to identify — then possibly act on — a
> trading opportunity.

| # | Step | User goal | Backend today | Frontend today | Real state |
|---|------|-----------|---------------|----------------|------------|
| 1 | Authentication | Identify myself | JWT complete | Login/Register pages | DONE |
| 2 | Account / broker context | Connect my trading context | Accounts + encrypted credentials + validation | Accounts/Broker pages | DONE |
| 3 | Market discovery | See what markets exist and how they behave | Catalogue + REST + WebSocket live data | Markets pages + live stream | DONE |
| 4 | Trigger market analysis | Ask "is there something now?" | Active scan API (`POST /api/v1/intelligence/scans`), multi-instrument, full pipeline wired | NOTHING | GAP |
| 5 | View active opportunities | See actionable candidates | Persisted, versioned, expiration driver active | NOTHING — and endpoint unreachable via Gateway | DOUBLE GAP |
| 6 | Understand why an opportunity exists | Trust before acting | Observation/evidence refs, condition results, explanation persisted | NOTHING | GAP |
| 7 | Build/view TradePlan | Prepare an actionable plan | Generation from analysis context, versioned plans | NOTHING (and MI `/trade-plans` unreachable via Gateway) | DOUBLE GAP |
| 8 | View deterministic risk decision | Know if the trade is authorized for me | Risk Domain blocking decision + explainer | Dashboard shows risk usage only; decision view absent | PARTIAL |
| 9 | Human decision | Approve or reject explicitly | Risk acknowledgment + explicit validation flow exists backend-side | NOTHING | GAP |
| 10 | Execute or explicitly cancel | Act on my decision | Execution intent → broker (idempotent client order id) | NOTHING | GAP |
| 11 | Observe resulting state | Know what happened | Execution results, trades (close/partial-close) | NOTHING beyond dashboard positions | GAP |

## 5. Trader journey gap analysis (ordered by journey, not by layer)

1. **Step 4 broken** — analysis cannot be triggered from the product.
2. **Steps 5–7 doubly broken** — features invisible in UI AND unreachable
   through the public Gateway (`/opportunities`, MI `/trade-plans` have no
   route). A complete backend feature invisible to the trader remains
   incomplete for V1.
3. **Steps 8–9 partially broken** — risk decision exists but is not presented
   as part of a plan-decision flow.
4. **Steps 10–11 broken** — execution flow has no product surface and has
   never been proven against Kraken.
5. Cross-cutting: the only live strategy is an unvalidated fixture, so even a
   perfectly surfaced opportunity would carry no demonstrated value (§13).

## 6. V1 closure principles

- Backend completeness does not close V1.
- A story is DONE when the trader can use it through the product.
- Integration/product stories require demonstrated runtime behavior, not unit
  tests alone.
- No silent fallbacks, no hidden automation of human decisions.
- Honest strategy legitimacy: technical correctness is never presented as
  empirical validation (ADR-038).

## 7. V1 Definition of Done

### A — Product accessibility

- [x] Authentication through the product (login/register/guards).
- [x] Account & broker-account management through the product.
- [x] Market discovery with live data through the product.
- [ ] All MI endpoints used by the journey reachable through the public
      Gateway (`/opportunities`, MI trade-plan endpoints) — no internal port
      access required.
- [ ] UI: launch a market scan (select instruments/scope) without Postman.
- [ ] UI: list active opportunities with status and validity window.
- [ ] UI: open one opportunity and see its evidence/explanation.
- [ ] UI: build/view a TradePlan from an opportunity.
- [ ] UI: view the deterministic risk decision (approved/refused + reason).
- [ ] UI: explicit human approve/reject/cancel action on the plan.
- [ ] UI: observe execution result/state after the decision.

### B — Market intelligence usability

- [x] Analysis pipeline works on real Kraken market data without developer
      intervention (scan POST → capabilities → observation → evaluation).
- [x] Multi-instrument scans supported (scope resolution implemented).
- [x] Expired opportunities are transitioned out of ACTIVE by the expiration
      driver (STORY-0018).
- [ ] Active opportunities consultable in the product, expired ones never
      presented as active.
- [ ] Minimum explanation available per opportunity in the product (why it
      exists: observation, conditions, strategy metadata).

### C — Strategy legitimacy

Honest current state:

- [x] Documented: **0 empirically VALIDATED + ENABLED strategies.**
- [x] Only LEGACY_OHLC_TREND runs, under BOOTSTRAP_CONTROLLED_RUN
      (UNVALIDATED fixture) — explicit temporary human-approved exception.
- [ ] **Human decision recorded** choosing ONE of:
      - (a) close V1 with the legacy exception explicitly accepted and
        time-boxed for the trader journey (documented limitation: produced
        opportunities carry no demonstrated empirical value), OR
      - (b) block V1 closure until at least one strategy reaches
        VALIDATED + ENABLED via accepted empirical evidence (deferred to the
        future validation capability — ADR-038 boundary).
- Technical correctness (tests, proofs, parity) must NEVER be presented as
  empirical validation.

### D — Risk

- [x] Every TradePlan passes through the Risk Domain before execution.
- [x] A risk refusal actually blocks execution.
- [ ] The trader sees the refusal/approval reason in the product.
- [x] No silent bypass path (execution requires validated plan state).

### E — Broker execution

- [ ] Controlled E2E external execution PROVEN against a Kraken-compatible
      environment (sandbox/demo), clearly identified in the run report.
- [ ] Success/failure behavior documented from that real run.
- [ ] Idempotency verified under retry (same idempotency key → same outcome).
- [ ] Unknown-outcome handling exercised (timeout → reconciliation path).
- [ ] Reconciliation reachable automatically or via an explicit operational
      procedure documented as acceptable for V1.

Mocks and unit tests do NOT satisfy this section.

### F — Human authority

- [x] No automatic order placement: execution requires an explicit validated
      plan and human trigger.
- [x] AI cannot bypass risk or human approval (AI not implemented; governance
      ADRs forbid it).
- [ ] Confirmed after UI work: every order in the journey traces back to an
      explicit human decision in the product.

### G — Runtime reliability

- [x] Opportunity expiration effective (driver active, restart-safe,
      idempotent).
- [x] No hidden fallback to in-memory strategy catalogue (persistent source
      of truth; bootstrap failures visible).
- [ ] External failures visible to the trader (analysis failure states
      surfaced, not swallowed).
- [ ] Post-restart behavior acceptable and documented (catch-up semantics).
- [ ] Mono-instance limitations documented as known V1 constraints if still
      present.

## 8. Closure blockers (journey order)

1. **Gateway routing fix** — `/opportunities` and MI `/trade-plans` reachable
   through the public Gateway. Without this, nothing else is visible.
2. **Opportunity surfacing** — UI list + detail (evidence/explanation).
3. **Scan UI** — trigger analysis from the product.
4. **TradePlan + Risk decision UI** — opportunity → plan → risk reason →
   explicit decision.
5. **Execution surface + Kraken sandbox E2E proof** (including unknown
   outcome and reconciliation procedure).
6. **Strategy legitimacy human decision** (§C choice (a) or (b)).

## 9. Important but non-blocking gaps

- Passive scanner (automatic periodic analysis).
- Configurable/versioned risk profiles.
- Historical OHLC persistence (also a V2 dependency).
- Accurate market-open determination ("open when executable").
- Background broker reconciliation scheduler.
- News / economic calendar.

## 10. Nice-to-have / post-V1

Multi-instance scheduling, observability platform, position monitoring
automation, domain events for opportunities, advanced passive intelligence.

## 11. Story-level Definition of Done rules

For any V1 story touching the trader journey:

```text
Gateway routing story:
DONE = endpoint reachable through the public gateway (verified request).

Opportunity UI story:
DONE = trader sees real persisted opportunities in Angular.

Scan UI story:
DONE = trader launches a real market scan without Postman.

TradePlan/Risk UI story:
DONE = opportunity → plan → risk decision usable from the UI.

Broker E2E story:
DONE = controlled external execution proven against a Kraken-compatible
environment, including at least one unknown-outcome reconciliation drill.
```

Unit tests alone never close integration/product stories.

## 12. External-validation requirements

Any capability involving an external system (Kraken private API) is DONE only
when exercised against that system in a clearly identified environment
(sandbox/demo), with outcomes recorded. Mock-based verification is engineering
progress, never product closure.

## 13. Strategy-validation constraint

Per ADR-038, `VALIDATED` means accepted deterministic/empirical evidence bound
to the exact StrategyId + version. That capability does not exist yet; normal
strategies therefore legitimately stay UNVALIDATED. V1 closure cannot quietly
redefine VALIDATED as "technically reviewed". The §C human decision is
mandatory before closure, and option (a) must be explicit, documented and
time-boxed — it is an accepted limitation, not a validation.

## 14. Human-authority requirements

Every executed order must trace back to an explicit human decision made
through the product. No future AI capability may bypass Risk Domain or human
approval (ADR-003/021/034).

## 15. V1/V2 boundary

```text
V1 (Trader Runtime):  Market → Intelligence → Opportunity → Risk → Human
V2 (Quant Researcher): ResearchQuestion → Hypothesis → Experiment → Finding
                       → Evidence → possible Strategy
Developer OS:          developer workflow (external)
```

Out of scope for V1 closure unless a direct dependency is proven: Quant
Research Workspace, full backtesting engine, historical dataset platform,
feature engineering, portfolio research, Quant Research Agent, full AI Engine,
news service, advanced passive intelligence, multi-instance scheduling,
large-scale observability.

## 16. Explicit non-goals

Nothing in this document designs V2 aggregates, decides the evidence-producer
ownership, or schedules roadmap items. It only prevents V2 features from
polluting V1 closure and prevents "backend complete" from being mistaken for
"V1 complete".

## 17. Final V1 closure checklist

A journey is usable only when ALL of the following hold:

- [ ] Trader logs in and sees accounts/dashboard (already true).
- [ ] Trader launches a scan from the product.
- [ ] Scan runs on real market data and persists opportunities.
- [ ] Trader sees active opportunities (not expired ones) in the product.
- [ ] Trader can explain why each opportunity exists (evidence view).
- [ ] Trader builds/views a TradePlan from an opportunity in the product.
- [ ] Trader views the deterministic risk decision with its reason.
- [ ] Trader approves, rejects or cancels explicitly.
- [ ] Approved orders execute against a Kraken-compatible environment with
      proven success/failure/idempotency/unknown-outcome behavior.
- [ ] Trader observes the resulting state (positions/trade records).
- [ ] Strategy-legitimacy human decision recorded (§C).
- [ ] Known limitations documented (mono-instance, legacy exception if kept,
      reconciliation procedure if manual).

Until every box is checked, Trading OS V1 remains an ENGINEERING PROTOTYPE —
regardless of backend completeness.
