# DRAFT — Trading Intelligence Evidence and Opportunity Quality V1

**Status:** `DRAFT — PENDING HUMAN APPROVAL`
**Date:** 2026-11-19
**Purpose:** Product-oriented proposal for the next Trading Intelligence milestone
**Authorization:** Planning direction only; no Epic or Story implementation is authorized
**Canonical draft:** `docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md`

## 1. Problem Statement

Trading OS can discover deterministic technical candidates, preserve their evidence, expose passive
awareness, and route a trader into explicit active investigation. It does not yet demonstrate that
the highest-ranked opportunities are the most useful opportunities to investigate.

The main gap is not the absence of another indicator. It is the absence of a measurable quality loop
between evidence, opportunity ranking, subsequent market behavior, and trader investigation outcomes.

Current scores are explicitly business-priority scores, not probabilities of success. Existing
strategies include permissive or explicitly unvalidated definitions. Tradeability records observed
spread, liquidity, range, and news facts, but several suitability policies remain unavailable or
not implemented. The product therefore needs stronger evidence quality and evaluation contracts
before it should make stronger prioritization or AI claims.

## 2. Product Goal

Help the trader spend less time scanning raw markets by showing which opportunities deserve attention,
why they rank where they do, what evidence supports them, what contradicts them, and how prior
opportunities behaved under a clearly defined evaluation protocol.

The milestone must improve decision quality and investigation efficiency without promising
profitability, calibrated probability, or automatic execution.

## 3. Existing Contracts to Reuse

The proposal should extend existing boundaries rather than introduce a new scanner service or generic
workflow framework:

- `MarketDataClient` and provider-neutral market facts for catalogue, state, snapshots, OHLC, and evidence freshness.
- `PassiveScanDispatchCoordinator` and `ActiveScanApplicationService` for bounded orchestration.
- `MomentumCalculator`, `TrendContextEngine`, and `MarketStructureEngine` for deterministic analysis.
- `TradeabilityCalculator` for observed spread, liquidity, range, and news evidence.
- `MarketThesisComposer` and `MarketThesis` for evidence-bearing interpretation and contradiction state.
- `StrategyMatch` and `StrategyEvaluationService` for versioned strategy applicability.
- `TradingOpportunity`, `OpportunityEngine`, and `OpportunityRankingEngine` for lifecycle, fusion, and stable ordering.
- `DeterministicRiskEngine` and `TradePlanningEngine` as downstream authorities, not ranking overrides.
- Existing Angular opportunities, passive-awareness, and decision-workspace surfaces.

## 4. Scope Increments

### Increment 1 — Deterministic Evidence Quality

Define a reproducible evidence-quality assessment over existing evidence, without converting quality
into probability.

Inputs should include:

- Required evidence completeness
- Evidence freshness relative to timeframe and cutoff
- Source/provider identity and provenance
- Data availability and quality state
- Contradictions and invalidations
- Market operational eligibility
- Strategy applicability and evaluation status
- Missing or not-implemented policies

Output should include a versioned assessment, dimensions, deterministic reasons, and a stable
fingerprint suitable for persistence and replay.

### Increment 2 — Opportunity Outcomes and Evaluation

Record what happens after an opportunity is detected, without confusing market outcome observation
with executed-trade performance.

The evaluation contract should distinguish:

- Expiration
- Invalidation
- Supersession
- Subsequent price movement
- Evaluation horizon and timeframe
- Strategy-specific outcome interpretation
- Actual executed-trade results, when present

Historical or replay evaluation must account for look-ahead bias, survivorship bias, spread,
transaction costs, incomplete data, provider identity, and known-at cutoff semantics.

### Increment 3 — Evidence-Based Opportunity Prioritization

Improve deterministic ranking using measurable dimensions such as:

- Evidence quality
- Freshness
- Contradictions
- Strategy applicability
- Market state and tradeability evidence
- Data reliability
- Opportunity validity window
- Account/risk context when legitimately available

Weights must not be arbitrary. Each ranking input requires a validation strategy and an explanation
that can be shown to the trader. Business priority must remain distinct from probability and
profitability claims.

### Increment 4 — Trader-Facing Explanation

Extend the existing Angular opportunities and passive-awareness surfaces to show:

- Why the opportunity was detected
- Supporting evidence and source timestamps
- Contradictory or missing evidence
- Evidence quality dimensions
- Ranking rationale
- Expiration, invalidation, and supersession reasons
- Clear separation between observation, passive candidate, active investigation, and trade plan

Use existing reactive services, Observables, and async-pipe patterns. Avoid a dashboard redesign.

### Future Increment — Advisory AI Interpretation

Only after the deterministic evidence and outcome foundation is measurable, add an advisory AI layer
that can summarize evidence, explain contradictions, compare historical contexts, and help prioritize
investigation.

AI must not override deterministic risk or freshness rules, invent evidence, create trading authority,
automatically create ActiveScans, execute trades, or present uncalibrated scores as probabilities.

## 5. Non-Goals

- Automatic order placement or position management
- Automatic ActiveScan escalation
- AI-controlled signal generation or risk decisions
- Win-rate, expectancy, or profitability claims without validated evidence
- New scanner microservice
- Generic workflow infrastructure
- User watchlist administration unless a concrete product need emerges
- Historical backfill of all available markets
- Broker synchronization or live-account validation
- Multi-agent orchestration

## 6. Proposed Engineering Stories

### Story A — Define the Evidence Quality Contract

**Goal:** Establish a versioned deterministic assessment over existing evidence.

**User value:** The trader can distinguish a technically interesting candidate from one supported by
complete, fresh, and non-contradictory evidence.

**Scope:** Domain contract, deterministic dimensions, reasons, fingerprint, persistence/projection
integration, and unit tests.

**Non-goals:** Probability, profitability, AI interpretation, and new provider acquisition.

**Dependencies:** Existing `MarketThesis`, tradeability, trend, structure, and opportunity contracts.

**Acceptance criteria:** Identical inputs produce identical assessments; missing/stale/contradictory
evidence is explicit; not-implemented policies cannot be represented as positive certainty; the
assessment is versioned and explainable.

**Validation:** Domain tests, persistence round-trip tests, deterministic fingerprint tests, and
negative evidence fixtures.

**Complexity:** Medium.

**Main risk:** Duplicating logic already owned by `MarketThesisComposer` or tradeability contracts.

### Story B — Capture Opportunity Lifecycle Outcomes

**Goal:** Record deterministic observation outcomes after opportunity detection.

**User value:** The platform can learn which candidates expired, invalidated, persisted, or moved
without pretending they were executed trades.

**Scope:** Outcome vocabulary, evaluation horizon, source references, invalidation reasons, and
replay-safe persistence.

**Non-goals:** Strategy promotion, automatic feedback into risk, or live trading attribution.

**Dependencies:** Story A and existing opportunity validity/version semantics.

**Acceptance criteria:** Outcomes preserve market/provider/product identity and known-at timestamps;
execution outcomes remain separate; duplicate evaluation is idempotent; incomplete data produces an
explicit unknown/non-evaluable result.

**Validation:** Fixed-fixture replay tests, cutoff tests, duplicate evaluation tests, and bias-risk
review.

**Complexity:** Medium.

**Main risk:** Accidentally treating subsequent market movement as executed-trade performance.

### Story C — Add Replayable Signal Evaluation

**Goal:** Measure deterministic signals over bounded, admissible evidence.

**User value:** Strategy and ranking changes can be compared using reproducible evidence rather than
anecdotes.

**Scope:** Bounded replay harness, evaluation populations, data-quality exclusions, spread/cost
assumptions, and result provenance.

**Non-goals:** Production strategy promotion, automatic optimization, or claims of profitability.

**Dependencies:** Stories A and B; existing historical evidence admissibility policies.

**Acceptance criteria:** Replays are reproducible; cutoff and known-at semantics are enforced;
provider/product identity is retained; invalid or incomplete samples are visible; results cannot be
presented as calibrated probabilities without a separate validation decision.

**Validation:** Golden datasets, look-ahead-bias fixtures, missing-candle fixtures, and reproducible
result fingerprints.

**Complexity:** Medium to high.

**Main risk:** Building a research framework broader than the concrete product question.

### Story D — Evidence-Based Opportunity Ranking

**Goal:** Rank opportunities using validated deterministic inputs and explain the result.

**User value:** The trader sees a stable, inspectable shortlist rather than an opaque score order.

**Scope:** Ranking dimensions, explicit configuration/versioning, stable tie-breaking, and API
projection of rationale.

**Non-goals:** Probability labels, AI ranking, or risk-rule replacement.

**Dependencies:** Stories A–C and a concrete validation dataset.

**Acceptance criteria:** Ranking is deterministic and stable; every dimension is traceable to evidence;
changing a dimension changes the ranking only through the documented policy; score semantics remain
business priority, not probability; stale and contradicted opportunities cannot silently outrank
fresh valid evidence.

**Validation:** Ranking golden tests, policy version tests, contradiction/freshness fixtures, and
replay comparison.

**Complexity:** Medium.

**Main risk:** Arbitrary weights creating an appearance of precision without validation.

### Story E — Surface Evidence and Ranking Rationale

**Goal:** Make evidence quality and prioritization useful in the existing Angular workflow.

**User value:** The trader can understand and challenge the platform's recommendation before
starting an active investigation.

**Scope:** Existing opportunities/passive-awareness cards, filters, loading/error states, and
explanation panels.

**Non-goals:** New frontend state-management infrastructure or automatic actions.

**Dependencies:** Stories A and D.

**Acceptance criteria:** Supporting, contradictory, missing, and stale evidence are distinguishable;
ranking rationale is visible; passive candidates remain distinct from active opportunities and plans;
no automatic scan or broker operation is introduced; responsive existing dashboard conventions are
preserved.

**Validation:** Angular component/service tests, accessibility checks, production build, and manual
human workflow review.

**Complexity:** Low to medium.

**Main risk:** Presenting deterministic quality dimensions as a recommendation guarantee.

## 7. Product Validation Strategy

Initially measurable metrics:

- Evidence completeness by opportunity
- Evidence freshness at presentation time
- Contradiction and invalidation rates
- Duplicate candidate rate
- Opportunity expiration and supersession rates
- Ranking stability across repeated evaluations
- Replay reproducibility
- Historical outcome coverage by strategy and timeframe
- Trader time from candidate discovery to investigation decision

Metrics not currently justified:

- Calibrated probability of success
- Win rate
- Expectancy
- Profitability
- Risk-adjusted return

Those measures require a separately approved evaluation protocol, admissible data, costs/spread
assumptions, sufficient samples, and explicit statistical interpretation. They must not be inferred
from opportunity scores or small replay results.

## 8. Architecture and Safety Boundaries

- Market Data remains provider-neutral and authoritative for market identity and observed facts.
- Market Intelligence owns deterministic evidence composition and opportunity interpretation.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization, and execution state.
- AI remains advisory and downstream of deterministic evidence.
- Passive monitoring remains account-independent and cannot create trading authority.
- No new microservice is justified by this proposal.
- Existing `Observation`, `MarketThesis`, `StrategyMatch`, and `TradingOpportunity` models should be extended only where a demonstrated contract gap exists.

## 9. Recommendation

Approve the direction for architectural planning, beginning with the Evidence Quality Contract and
Opportunity Outcome definitions. Defer advisory AI until the deterministic quality and outcome loop
is measurable. The canonical Epic draft is now available for review, but official Story artifacts and
implementation remain unauthorized until the human owner approves this scope, the validation protocol,
and the boundary between observed market outcomes and executed-trade results.
