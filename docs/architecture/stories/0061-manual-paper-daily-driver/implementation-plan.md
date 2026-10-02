# Implementation Plan - Story 0061

## Plan Status

Implementation and runtime validation completed. Human review remains pending.

## Step 1 - Review the Manual Workspace Baseline

Confirm the Story 0060 ticket, account-scoped market context and existing
TradePlan lifecycle before changing backend or frontend behavior.

## Step 2 - Exercise the Official UI

Use an authenticated PAPER account to select an eligible market, inspect market
context, create a MANUAL TradePlan and accept it. Do not use direct HTTP,
database mutation, fabricated identifiers or hidden endpoints.

## Step 3 - Validate Deterministic Risk

Request Risk through the normal lifecycle. Record approved, rejected or
unavailable results. Preserve fail-closed behavior and do not weaken thresholds.

## Step 4 - Validate Execution Recovery

For an approved plan, authorize execution explicitly. If an existing Intent is
recoverable, resume that exact Intent by TradePlan identity and version rather
than creating another Risk evaluation or Intent.

## Step 5 - Validate PAPER Continuity

Verify the PAPER fill, persisted Position, reload behavior, full close, second
reload, execution history and TradePlan continuity.

## Step 6 - Report Negative Evidence

Record unavailable/stale valuation or duplicate submission behavior as evidence,
not as reasons to bypass deterministic controls.

## Explicit Non-Goals

Do not alter Risk rules, introduce LIVE behavior, add broker-specific frontend
logic or redesign the Opportunity journey.
