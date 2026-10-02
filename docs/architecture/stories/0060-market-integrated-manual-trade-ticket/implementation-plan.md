# Implementation Plan - Story 0060

## Plan Status

Implementation and runtime validation completed. Human review remains pending.

## Step 1 - Extract the Reusable Ticket

Move manual form fields, validation and submission behavior into
`ManualTradeTicket`. Keep account and market inputs injectable as contextual
read-only state rather than selectors.

## Step 2 - Compose the Decision Workspace

Render the ticket only after account and eligible market context are resolved.
Keep ticker, chart, order book, recent trades, constraints and freshness states
visible around the ticket.

## Step 3 - Preserve the Standalone Route

Reuse the extracted ticket from the existing standalone manual route where
practical. Keep the route available as a fallback and preserve its backend
contract.

## Step 4 - Verify Lifecycle Boundaries

Cover missing context, inherited account/market, successful submission,
navigation to `PlanPage`, and the absence of ExecutionIntent or broker side
effects.

## Step 5 - Validate Runtime

Use only the official authenticated PAPER UI. Record market-data degradation and
Risk rejection honestly; do not convert unavailable data into a successful
execution claim.

## Explicit Non-Goals

Do not change Risk rules, create a direct execution path, alter broker APIs,
redesign the Opportunity flow or execute LIVE orders.
