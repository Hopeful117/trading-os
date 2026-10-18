# Story 0123 - Forward Historical Fee Schedule Runtime Activation Investigation

## Metadata

**ID:** `0123`
**Title:** Forward Historical Fee Schedule Runtime Activation Investigation
**Status:** Investigation complete; runtime activation remains blocked
**Related Stories:** `0120`, `0121`, `0122`
**Related ADRs:** `ADR-064`

## Objective

Identify the first runtime transition at which Story 0120 fee capture diverges
from its intended configuration, trigger, Kraken request, normalization, domain,
persistence, and strict-reader lifecycle.

## Scope

- Inspect the merged Story 0120 implementation and tests.
- Inspect the effective Broker Service container configuration and logs.
- Verify the existing fee database schema and row count without modification.
- Verify provider source selection and authenticated request construction from code
  and current Kraken documentation.
- Preserve the operational Story 0122 Order Book lifecycle and do not restart
  Market Data.
- Implement no production fix unless a bounded defect is proven and approved.

## Out Of Scope

- Historical fee reconstruction or backdating.
- Synthetic or default fee evidence.
- Changes to fee domain semantics, schema, or reader behavior.
- Changes to Market Data, Order Book, checksum, funding, or economic formulas.
- Credential inspection, secret output, database reset, commit, push, or merge.

## Expected Outcome

Classify the first failing transition and determine whether a narrow activation
fix is justified. A runtime success claim requires a real authenticated Kraken
Futures-applicable observation persisted as a canonical `HistoricalFeeSchedule`.
