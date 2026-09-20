# Implementation Plan - Story 0054

## Plan Status

`IMPLEMENTED - HISTORICAL PLAN`

## Phase 1 - Trace the valuation path

Trace the selected opportunity instrument from the web flow through Gateway,
Market Intelligence, Market Data, and Trading Core risk evaluation. Identify
mapping, freshness, and runtime configuration failures without weakening the
risk boundary.

## Phase 2 - Correct authoritative valuation propagation

Ensure the configured supported PAPER market produces a recent compatible
valuation, with explicit quote timestamp, currency, and source information.
Keep missing or stale valuation fail-closed.

## Phase 3 - Regression coverage

Cover provider mapping, snapshot refresh, valuation client propagation, and
negative unavailable/stale cases. Do not introduce synthetic or manually
injected prices.

## Phase 4 - Runtime evidence

Run the authenticated web journey through risk evaluation and record the market,
valuation freshness, and resulting deterministic decision. If a later risk
rule blocks the journey, record that blocker separately rather than treating it
as a valuation failure.

## Implementation Reference

```text
commit = 317f8ee
```
