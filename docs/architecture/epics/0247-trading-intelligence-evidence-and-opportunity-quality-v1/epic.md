# Epic 0247 — Trading Intelligence Evidence and Opportunity Quality V1

**Status:** `DRAFT — PENDING HUMAN APPROVAL`
**Date:** 2026-11-19
**Priority:** Trading Intelligence product usefulness
**Predecessor:** Epic 0238 — Passive Market Scanning V1
**Authorization:** Planning and architectural discussion only; implementation is not authorized

## 1. Problem

Trading OS can discover deterministic technical candidates, preserve their evidence, expose passive
market awareness, and route a trader into explicit active investigation. It does not yet demonstrate
that the highest-ranked opportunities are the most useful opportunities to investigate.

Current opportunity scores represent business priority, not probability of success. Several strategy
definitions and tradeability policies remain explicitly unvalidated, unavailable, or incomplete.
The next product increment must therefore improve evidence quality and evaluation before introducing
stronger ranking or AI claims.

## 2. Product Goal

Help the trader spend less time scanning raw markets by making each opportunity explainable,
quality-aware, comparable, and prioritizable without promising profitability or automatic execution.

The trader should be able to answer:

- Why was this opportunity detected?
- Which evidence supports it?
- What evidence is missing or contradictory?
- How fresh and reliable is the evidence?
- Why is it ranked above or below another opportunity?
- What happened to similar opportunities under a defined observation protocol?

## 3. Scope

### In scope

- Deterministic evidence-quality assessment.
- Explicit evidence completeness, freshness, provenance, contradiction, and applicability dimensions.
- Opportunity outcome and invalidation capture.
- Bounded, replayable evaluation of deterministic signals.
- Evidence-based opportunity ranking with stable explanations.
- User-facing evidence and ranking rationale in existing Angular surfaces.
- Validation metrics for reproducibility and investigation efficiency.

### Out of scope

- Automatic trading or position management.
- Automatic ActiveScan creation.
- AI-controlled signal generation or risk decisions.
- Probability, win-rate, expectancy, or profitability claims without a separate approved protocol.
- New scanner microservice or generic workflow engine.
- Global historical backfill.
- Broker synchronization or live-account validation.
- Multi-agent orchestration.

## 4. Architecture Boundaries

- Market Data remains authoritative for provider-neutral market identity and observed facts.
- Market Intelligence owns deterministic evidence composition and opportunity interpretation.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization, and execution state.
- Passive monitoring remains account-independent and cannot create trading authority.
- AI remains optional, advisory, and downstream of deterministic evidence.
- Existing `Observation`, `MarketThesis`, `StrategyMatch`, and `TradingOpportunity` contracts are reused unless a concrete gap is demonstrated.
- No new microservice is justified by this Epic.

## 5. Dependency-Ordered Stories

### 0247-01 — Deterministic Evidence Quality Contract

Define a versioned deterministic assessment over existing evidence.

Acceptance criteria:

- Identical inputs produce identical assessments.
- Completeness, freshness, provenance, contradiction, applicability, and missing-policy states are explicit.
- Missing or not-implemented evidence cannot be represented as positive certainty.
- The result is versioned, explainable, persistable, and fingerprinted.
- Existing `MarketThesis` and tradeability semantics are not duplicated without an approved boundary.

Validation:

- Domain and persistence tests.
- Stable fingerprint tests.
- Fresh, stale, missing, contradictory, unavailable, and not-implemented fixtures.

Main risk: duplicating rules already owned by `MarketThesisComposer` or `TradeabilityCalculator`.

### 0247-02 — Opportunity Outcome Capture

Record deterministic observations about what happens after opportunity detection.

Acceptance criteria:

- Expiration, invalidation, supersession, subsequent movement, and unknown outcomes are distinguishable.
- Evaluation horizon and source cutoff are persisted.
- Market outcomes remain separate from executed-trade outcomes.
- Duplicate evaluation is idempotent.
- Incomplete evidence yields an explicit non-evaluable result.

Validation:

- Fixed temporal fixtures.
- Cutoff and horizon tests.
- Duplicate evaluation tests.
- Review for look-ahead and execution-attribution errors.

Main risk: treating subsequent market movement as actual trade performance.

### 0247-03 — Replayable Deterministic Signal Evaluation

Provide a bounded replay mechanism for admissible deterministic evidence.

Acceptance criteria:

- Replays are reproducible and versioned.
- Known-at and evaluation-cutoff semantics are enforced.
- Provider, product, timeframe, and data-quality provenance are retained.
- Missing or excluded samples are visible.
- Spread and transaction-cost assumptions are explicit.
- Results are not presented as calibrated probabilities without separate approval.

Validation:

- Golden datasets.
- Look-ahead-bias fixtures.
- Missing-candle and incomplete-history fixtures.
- Result fingerprint comparisons.

Main risk: expanding into an unbounded research framework before a concrete product question is validated.

### 0247-04 — Evidence-Based Opportunity Ranking

Rank opportunities with measurable, versioned, deterministic inputs.

Acceptance criteria:

- Ranking is deterministic and stable.
- Every ranking dimension is traceable to evidence.
- Policy versions and tie-breaking are explicit.
- Stale or contradicted opportunities cannot silently outrank fresher valid evidence.
- Business-priority semantics remain distinct from probability and profitability.
- Weight changes require a documented validation strategy.

Validation:

- Ranking golden tests.
- Policy-version tests.
- Contradiction and freshness fixtures.
- Replay comparison across ranking versions.

Main risk: arbitrary weights creating false precision.

### 0247-05 — Trader-Facing Evidence and Ranking Rationale

Expose the new quality and prioritization information in existing Angular workflows.

Acceptance criteria:

- Supporting, contradictory, missing, and stale evidence are distinguishable.
- Ranking rationale is visible and understandable.
- Passive candidates, active opportunities, TradePlans, and execution remain distinct.
- No automatic scan, risk, or broker operation is introduced.
- Existing reactive Angular conventions and responsive dashboard patterns are preserved.

Validation:

- Angular service and component tests.
- Loading, empty, stale, unavailable, and positive states.
- Accessibility and responsive review.
- Production build.
- Human review of the trader workflow.

Main risk: presenting deterministic quality dimensions as a recommendation guarantee.

## 6. Future AI Boundary

AI is intentionally deferred from the initial implementation scope.

After deterministic evidence and outcome data are measurable, a bounded advisory layer may:

- summarize deterministic evidence;
- explain contradictions;
- compare historical contexts;
- assist investigation prioritization.

It must not:

- invent evidence;
- override freshness, eligibility, or risk rules;
- create trading authority;
- create ActiveScans automatically;
- execute trades;
- present uncalibrated scores as probabilities.

## 7. Product Validation

Initial measurable indicators:

- Evidence completeness per opportunity.
- Evidence freshness at presentation.
- Contradiction and invalidation rates.
- Duplicate candidate rate.
- Expiration and supersession rates.
- Ranking stability across repeated evaluations.
- Replay reproducibility.
- Historical outcome coverage by strategy and timeframe.
- Trader time from candidate discovery to investigation decision.

The Epic does not claim that these metrics establish profitability. Win rate, expectancy, calibrated
probability, and risk-adjusted return require a separately approved statistical protocol, admissible
data, transaction-cost assumptions, and sufficient sample sizes.

## 8. Human Approval Required

Before creating official Story artifacts or implementing code, the human owner must approve:

- Epic scope and non-goals.
- The evidence-quality contract boundary.
- The distinction between observed market outcomes and executed-trade outcomes.
- The replay population and admissibility protocol.
- The ranking validation approach.
- The decision to defer AI implementation until deterministic prerequisites are measurable.
