# Repository Analysis - Story 0190

## Baseline

```text
REMOTE_MAIN: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_BASE: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_HEAD: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
CURRENT_BRANCH: story/0190-paper-cfd-t1-margin-revalidation
WORKTREE_STATE: pre-existing untracked Story 0185, 0187, and 0189 directories preserved
NEXT_AVAILABLE_STORY: 0190
```

Story 0189 is present locally but is uncommitted. Story 0188 has no tracked
`code-review.md`; that documentation gap is preserved and not fabricated here.
DevLog returned low-confidence, truncated context without exact T1 evidence. The
repository is authoritative. Obsidian was not consulted.

## Current T0 Margin Authority Flow

```text
TradePlanRiskEvaluationService
  -> MarketValuationPort.value(..., FreshnessRequirement.t0())
  -> T0 current valuation snapshot
  -> RequiredMarginPort.Request
  -> local PAPER MarginPolicy resolution
  -> PaperMarginCalculator
  -> normalized monetary Risk fact
```

T0 used the plan entry price when present for `Request.price`, while the same T0
valuation snapshot supplied market/FX evidence. Story 0190 preserves the request
field for boundary compatibility but now carries the authoritative valuation
snapshot and T0 freshness requirement explicitly.

```text
T0_PRICE_SOURCE = existing TradePlan entry price when present; otherwise T0 market fact
T0_PRICE_PROVENANCE = TradePlan or Market Data, respectively
T0_FRESHNESS = FreshnessRequirement.t0()
T0_MARGIN_REQUEST_PRICE_SOURCE = existing T0 request price
T0_EXPOSURE_SOURCE = current local PAPER open Trade read-side snapshot
T0_POLICY = assigned/versioned local MarginPolicy
```

## Current T1 Margin Authority Flow Before

`ExecutionTimeRiskRevalidationService` already acquired the current valuation with
`FreshnessRequirement.t1()`. However, it then selected `plan.entryIntent().price()`
when present for the margin request. `PaperRequiredMarginClient` performed a
second Market Data call using `FreshnessRequirement.t0()` and calculated from
`Request.price`. This made the T1 margin amount independent of the fresh T1
valuation price and introduced a duplicate acquisition.

```text
T1_PRICE_SOURCE_BEFORE = TradePlan entry price when present
T1_PRICE_PROVENANCE_BEFORE = carried TradePlan value
T1_FRESHNESS_BEFORE = T1 for risk valuation, but T0 inside PAPER margin client
T1_MARGIN_REQUEST_PRICE_SOURCE_BEFORE = TradePlan entry price
T1_EXPOSURE_SOURCE_BEFORE = local Trade read-side snapshot reacquired by margin client
T1_POLICY_BEFORE = exact T0 policy version from persisted margin context
USES_T0_FRESHNESS = YES
USES_T0_OR_STALE_PRICE = YES
```

## Current T1 Margin Authority Flow After

```text
ExecutionTimeRiskRevalidationService
  -> MarketValuationPort.value(..., FreshnessRequirement.t1())
  -> T1 current valuation snapshot and sourcePrice
  -> RequiredMarginPort.Request(valuation, FreshnessRequirement.t1())
  -> local PAPER MarginPolicy and fresh local exposure snapshot
  -> PaperMarginCalculator(sourcePrice)
  -> normalized monetary Risk fact
```

```text
T1_PRICE_SOURCE_AFTER = T1 valuation Fact.sourcePrice
T1_PRICE_PROVENANCE = T1 Market Data Fact source/provenance and timestamps
T1_FRESHNESS_AFTER = FreshnessRequirement.t1(), validated against snapshot metadata
T1_MARGIN_REQUEST_PRICE_SOURCE_AFTER = T1 valuation Fact.sourcePrice for PAPER
T1_EXPOSURE_SOURCE_AFTER = local PAPER trades reacquired at T1 requestedAt
T1_POLICY_AFTER = exact immutable T0 policy identity/version
INDEPENDENT_T1_PRICE = YES
INDEPENDENT_T1_MARGIN_RECALCULATION = YES
```

The PAPER adapter no longer calls Market Data. It fails closed if the orchestration
layer does not provide valuation context, preventing silent fallback to T0
freshness or a provider preview.

## Freshness Boundary Analysis

`FreshnessRequirement.t0()` permits a five-minute observation age and reuse-or-
acquire behavior. `FreshnessRequirement.t1()` permits thirty seconds and
confirm-or-acquire behavior. The request now carries the requirement used to
acquire its valuation snapshot, and the PAPER resolver rejects a snapshot whose
declared maximum age does not match that requirement. Existing timestamp checks
remain in place and reject future or stale facts.

## Price Authority Analysis

`RequiredMarginPort.Request.price` remains because LIVE provider requests and
application compatibility still need a request price. For PAPER, it is no longer
the financial authority. `PaperRequiredMarginClient` uses the single resolved
valuation Fact's `sourcePrice`, and T1 constructs that request price from the same
fresh fact. This prevents a TradePlan entry price from silently becoming current
T1 market truth.

## T0/T1 Independence Proof

`PaperRequiredMarginClientTest` supplies a T0 snapshot at `1.10` and a T1 snapshot
at `1.20` under the corresponding freshness contracts. With the same explicit 1%
policy and 1000-unit EUR/USD order, required margin changes from `11.00` to
`12.00`. The same test proves stale/mismatched T1 snapshots and a missing T1
source price fail closed. `ExecutionTimeRiskRevalidationServiceTest` captures
the actual T1 request and verifies its price, valuation fact, and `t1()` freshness
requirement.

## Remaining Boundaries

This Story does not resolve Story 0189's separate ADR-083 persistence blockers:
database immutability, database rate precision, PostgreSQL policy assignment and
restart evidence, or a full PAPER T0/T1/settlement E2E. `CASH + CFD` remains
rejected.
