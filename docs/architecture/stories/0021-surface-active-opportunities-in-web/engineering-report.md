# Engineering Report — Story 0021

Date: 2026-08-24 · Branch: `feature/story-0021-opportunities-surfacing`

## Outcome

The trader journey now includes its first Market Intelligence surface:
authenticated users can list ACTIVE opportunities served by the public Gateway
and open any of them to understand why it exists, using only deterministic
backend data.

```text
TradingOpportunity (persisted) → /api/v1/opportunities/** (Gateway, JWT)
    → OpportunityService → Angular views → Trader
```

## What shipped

* Typed opportunity contract + API service (`findActive`, `findById`).
* Opportunities list page with loading / empty / error states and refresh.
* Opportunity detail page with facts, deterministic explanation, provenance
  (strategy match id, observation references), truthful not-found state.
* Guarded routes `/opportunities`, `/opportunities/:opportunityId`; one
  sidebar entry.
* 25 new unit tests (159 → 184), including route-guard configuration tests.

## V1 Definition of Done impact

* Closure blocker #2 ("Opportunity surfacing") — **implemented at product
  level**; final confirmation still requires the runtime check described in
  §Manual validation once trading-core runs locally.
* DoD checklist items advanced: "UI: list active opportunities" and "UI: open
  one opportunity and see its evidence/explanation" — code complete, awaiting
  live demonstration.

## Validation summary

| Check | Result |
|---|---|
| `npm run test:ci` | PASS — 30 files, 184 tests |
| `npm run build` | PASS (+14 kB initial bundle; pre-existing warning unchanged) |
| New-code coverage | 90–100% LINE (gate ≥80%) |
| Backend diff | None (frontend-only story confirmed) |
| Live gateway probe `GET /api/v1/opportunities/active` | 401 unauthenticated — routing + security verified |
| Full authenticated UI journey | Not executed: local trading-core instance down; browser automation unavailable (no sudo). Covered by DOM-level unit tests. |

## DevLog MCP usage

* `get_engineering_context` called first with an implementation-focused intent;
  after the manual DevLog refresh it returned evidence up to HEAD
  (`010ed05`, resolved revision metadata): Story 0019 gateway-routing commits,
  full Story 0020 chain (frontend baseline, JaCoCo gates, SonarQube CI,
  ADR-039), expiration-driver commit. **Recent history now visible: YES.**
* Residual gaps observed (informational): no evidence items for ADR-033..038
  documents or literal `StrategyMatch`/`TradingOpportunity` strings — commit
  subjects only. No STORY/DECISION/CHALLENGE evidence kinds returned.
* Resources opened: none this time; commit summaries were sufficient.
* `search_project_history`: not needed — the repository answered all current-
  contract questions directly.
* Git fallback: used only for conventional details (branch naming history).

## Remaining limitations

* Pagination/filtering of opportunities not exposed yet (endpoint supports it).
* Version-history endpoint unused.
* Empty-state screen is the expected outcome until a scan produces data (scan
  triggering is deliberately out of scope — next journey step).
* Bundle budget warning persists (pre-existing technical debt).
* Sidebar footer still advertises a static "AI Engine ● Online" placeholder —
  dishonest per product honesty rules but untouched (pre-existing, out of
  scope).

## Suggested next story

Choose between (not created):

1. **Trigger a market scan from the product** (DoD blocker #3): scan form +
   progress/result feedback on real backend endpoints. Natural continuation —
   without it the new screen stays empty in fresh environments.
2. **Trade-plan surfacing from an opportunity** (DoD blocker #4): requires
   reading `/api/v1/trade-plans/analyses/**` orchestration; larger because it
   crosses into Trading Core risk acknowledgment flow.

Recommendation: option 1 first — smaller, unblocks visible data for the
screen this story delivered.
