# Repository Analysis — Story 0023

Date: 2026-08-25 · Branch base: `origin/main` @ `d7adb09` (Story 0022 merged, PR #18)
Re-verified against HEAD after the boundary investigation; the code is the
final arbiter.

## Mandatory pre-coding answers

**§72 — Smallest public API to turn an ACTIVE opportunity into a PROPOSED
plan without exposing MI internals?**

`POST /api/v1/trade-plans/opportunities/{opportunityId}/trade-plans`
(Trading Core, JWT actor + `Idempotency-Key`, body `{accountId}`) orchestrating
a new internal MI endpoint
`POST /internal/v1/intelligence/opportunities/{opportunityId}/trade-plans`.
Trading Core resolves the account (ownership + base-currency check) and the
effective `TradePlanningProfile`, builds the planning-context payload exactly
like the existing analysis-based path, and delegates. MI validates the
opportunity is ACTIVE, selects a fresh market price server-side (≤30s,
side-selected bid/ask) and runs the existing `TradePlanApplicationService.create`.

**§73 — Which operation carries PROPOSED → ACCEPTED / REJECTED and how are
owner/version/lifecycle/idempotency guaranteed?**

A new decision capability in MI:
`POST /internal/v1/trade-plans/{planId}/versions/{version}/decisions`
body `{actorId, decision: ACCEPT|REJECT}` wrapped by a small application
service that (1) loads the latest plan, (2) rejects stale versions
(requested version ≠ latest → explicit conflict), (3) verifies the planning
context ownerId equals the actor, (4) returns idempotent success when the
latest version is already in the requested target status, (5) otherwise
delegates to the existing guarded transition (`TradePlanApplicationService.
transition` → `TradePlanLifecyclePolicy`), so illegal transitions fail with
the domain's own semantics. Trading Core exposes it publicly under
`/api/v1/trade-plans/{planId}/versions/{version}/decisions` with the JWT
actor substituted server-side (never trusted from the body).

**§74 — Can TradePlanRiskEvaluationService be used unchanged after ACCEPT?**

Yes. Verified on HEAD: ownership checks, exact-version ACCEPTED snapshot load,
live broker state fetch, context build, `DeterministicRiskEngine.evaluate`,
persistence — all as investigated. Zero risk-code changes required. The
frontend calls the existing public endpoint
`POST /api/v1/trade-plans/{planId}/versions/{version}/risk-evaluations`
(Idempotency-Key + `{accountId}`).

**§75 — Exact plan state after a successful evaluation in the current
workflow?**

`RISK_VALIDATED`. On APPROVED/APPROVED_WITH_WARNINGS the service persists an
acknowledgment which transitions the plan via
`recordRiskValidated` (verified at `TradePlanRiskEvaluationService` line ~255).
This natural semantics is kept (mission §27/§28); the divergence with the
execution gate's ACCEPTED-snapshot precondition remains Gap #3 for the
execution story.

**§76 — What must the trader actually provide?**

Only the **trading account**. Verified on HEAD: deterministic policies derive
entry (from fresh market price side-selection), stop (percentage strategy from
preferences), targets (risk-multiple strategy), position sizing and risk/reward
— all inputs come from the opportunity, the effective profile
(`profiles.effective(actorId, accountId)`: RiskBudget + PlanningPreferences)
and live market data. The form is therefore: account selector + one create
action. Nothing else is asked.

## Additional verified facts driving the design

* Analysis-based generation receives its context FROM Trading Core
  (`InternalAnalysisTradePlanRequest.Context{ownerId, tradingAccountId,
  accountCurrency, riskBudget, preferences}`) and saves the snapshot in MI —
  the opportunity path mirrors this payload shape.
* Price freshness: `intelligence.planning.price-max-age` (default 30s),
  side-selected bid/ask by direction; unavailable price → 503.
* No persistent idempotency exists on MI direct creation; adding it would
  require schema changes. Decision: creation stays non-persistently-idempotent
  — a retried creation may yield an additional inert PROPOSED plan that
  expires naturally. Documented limitation; double-click protection lives in
  the UI single-flight + button state.
* Plan reads: MI holds `TradePlanResponse`; a new owner-checked internal GET
  is exposed for Trading Core to proxy (public read under
  `/api/v1/trade-plans/{planId}/versions/{version}`).
* Gateway already routes `/api/v1/trade-plans/**` → trading-core (Story 0019);
  **no Gateway change needed**; new MI internals stay unrouted by convention.

## Frontend conventions available

Opportunities feature patterns (0021) and trade-planning needs map cleanly:
discriminated view-model streams, `async` pipe only, account selector reuse,
`data-testid` hooks, Vitest service mocks. New routes behind `authGuard`;
entry CTA from Opportunity detail when status is ACTIVE.
