# Story 0101 - Implementation Plan

## Architectural Basis

Implement ADR-055 without changing Risk rules, provider-neutral responsibility
boundaries, or execution side-effect semantics.

## Changes

1. Extend the Market Data snapshot request with provider-neutral freshness,
   decision-boundary, acquisition mode, and bounded deadline information.
2. Reuse a valid cached observation before invoking a provider.
3. Implement bounded REST-first acquisition and persist normalized observations.
4. Use the existing ticker subscription boundary as a bounded alternate when a
   provider response is stale and the alternate is supported.
5. Return distinct `FRESH`, `STALE_PROVIDER`, `UNAVAILABLE`, and `TIMEOUT`
   outcomes and ensure non-fresh outcomes fail closed through existing Risk
   reasons.
6. Propagate explicit T0 and T1 requirements through the Trading Core port and
   client without provider-specific terminology.
7. Bound coalescing, future cancellation, temporary subscription cleanup, and
   diagnostic retention.
8. Add focused Market Data and Trading Core regression coverage.
9. Validate a manual PAPER `CASH + LINEAR_PERPETUAL` lifecycle and inspect
   durable risk and Challenge reevaluation outbox state.

## Expected Affected Areas

- Market Data internal valuation API, DTOs, services, and subscription boundary.
- Trading Core market valuation port/client and T0/T1 orchestration.
- Focused Market Data and Trading Core tests.
- Story-local runtime and engineering artifacts.

No changes are planned to Risk policies, broker adapters, frontend behavior,
scanner strategy, or permanent catalogue-wide subscriptions.

## Validation

- Focused Market Data acquisition and subscription tests.
- Focused Trading Core valuation-client and T0/T1 orchestration tests.
- Module-level Maven compilation and tests.
- `git diff --check`.
- Isolated Compose PAPER lifecycle with PostgreSQL outbox inspection.

## Constraints

- Provider `effectiveAt` remains the freshness authority.
- `capturedAt` cannot make an old source observation fresh.
- T0 retains the existing maximum observation-age policy.
- T1 uses provider-neutral `CONFIRM_OR_ACQUIRE` with `PT30S` source-age policy.
- Acquisition stops at the caller deadline or Market Data safety maximum.
- No unsafe write operation is retried or duplicated.
- No commit or push is performed automatically.

## Workflow Note

This plan is a reconstructed traceability artifact created after the
implementation. It does not represent inferred human approval of a plan.
