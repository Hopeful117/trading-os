# Implementation Report - Story 0061

## Status

`IMPLEMENTED - RUNTIME COMPLETE - HUMAN REVIEW PENDING`

## Scope Executed

The official Web journey was exercised at `http://localhost:17085` with an
authenticated PAPER account and the account-scoped Decision Workspace. No direct
database mutation, manual backend bridge, hidden endpoint, or direct broker call
was used for the product journey.

## Runtime Evidence

```text
Account: Story 0061 Retry PAPER
Market: AIXBT/EUR
Plan: 497c62ae-019d-4376-a67d-6400c0ec689a
Origin: MANUAL
Accepted version: 2
Ready version: 4
Risk-approved evaluation: 615099ee-e5dd-4bad-a3bc-4a51f5923c73
Execution intent: 5c051202-c139-479b-814b-d5f15f209f4f
Execution attempt: 9224085e-4b97-412b-a1db-d1a59e35f5a4
Execution-time Risk evaluation: acef2f17-79ad-4774-91d4-4f62d408fa0c
PAPER broker order: SIM-7fb8821f-b39a-4de0-b7c5-96b6a8e6cbed
PAPER trade/position: cc1a68b9-ae9f-4dbb-8db5-66a1e8f5bb0f
Close execution intent: b1483c46-62c6-4e14-8a20-3661855b319c
```

The journey reached account selection, eligible market context, manual TradePlan
creation, human acceptance, deterministic Risk approval, explicit execution
authorization, PAPER execution, fill, persisted position, position reload, full
close, close reload, and trade-history continuity.

## Minimal Fixes

* `MarketValuationClient` now refreshes the quote-to-reporting conversion market
  for each proposed instrument.
* Risk evaluation uses the authoritative current instrument source price for
  MARKET orders when no limit entry price exists.
* Execution-time Risk revalidation uses the same authoritative current price
  fallback for MARKET orders.
* The Web plan page discovers an existing authorized Execution Intent by exact
  TradePlan ID and version and resumes it through the normal execute endpoint.
  It never starts a second Risk evaluation or creates a second Intent.
* Execution summaries expose TradePlan version so recovery cannot select an
  Intent from another plan version.

## Duplicate Risk Root Cause

The duplicate was a second browser submission of the Evaluate Risk action at
`2026-09-23T16:42:58Z`, after the first approved evaluation had already been
acknowledged and had advanced the plan from accepted version 2 to ready version
4. It was not caused by execution-time revalidation, polling, component
lifecycle, or retry handling. Execution-time revalidation is invoked only inside
`ExecuteTradeService.execute()` after an Intent already exists.

The acknowledgment owner is Market Intelligence's
`TradePlanRiskHandoffService`. Its operation is idempotent for the same
evaluation, decision, and timestamp, and correctly rejects a different
evaluation for the same accepted plan version. The duplicate evaluation was
therefore correctly rejected with `RISK_VALIDATION_ACKNOWLEDGMENT_CONFLICT`.

The original Intent was recoverable: it was non-terminal, unexpired, owned by
the authenticated user, linked to the approved evaluation, and had no attempt
or broker order. The missing product behavior was Web recovery from a ready
plan to that existing Intent.

## Validation

```text
MarketValuationClientTest, TradePlanRiskEvaluationServiceTest: passed
MarketValuationClientTest, TradePlanRiskEvaluationServiceTest,
ExecutionTimeRiskRevalidationServiceTest: passed
PlanPage: 10 tests passed
Trading Core compile: passed
Angular production build: passed with existing bundle/style budget warnings
git diff --check: passed
```

## Runtime Acceptance

The existing Intent was resumed through the official UI without creating a new
Intent. Execution-time Risk evaluation `acef2f17-79ad-4774-91d4-4f62d408fa0c`
completed `APPROVED`. Attempt `9224085e-4b97-412b-a1db-d1a59e35f5a4` completed
`SUCCEEDED`; the PAPER broker order was filled for quantity `1000` at `0.02`.

The position appeared in the Positions page, survived a page reload, and showed
the expected PAPER exposure. The current-price display was `Indisponible` during
the check, which is recorded as a known valuation/freshness limitation rather
than treated as an execution or persistence failure.

The complete exposure was closed through the normal UI. The resulting trade
`cc1a68b9-ae9f-4dbb-8db5-66a1e8f5bb0f` is persisted as `CLOSED`; after a second
reload the Positions page showed no open positions. Trade history retained the
entry execution, Trade Plan v4 link, filled broker status, and the separate
completed close execution.

## Human Review

Risk authority was preserved: the duplicate acknowledgment remained rejected,
execution-time Risk ran and approved the actual submission, and no direct broker
or database mutation was used for the official journey. Human review is still
required before Story 0061 is accepted.
