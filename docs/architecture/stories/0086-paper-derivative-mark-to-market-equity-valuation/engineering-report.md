# Story 0086 - Engineering Report

## Outcome

Story 0086 implementation is complete and ready for human review.

## Final Decisions

- ADR created: `ADR-052`.
- Realized authority: persisted `Account.realizedBalance` for PAPER CASH.
- Equity meaning: latest successful mark-to-market snapshot.
- Market source: canonical Market Data fresh bid/ask snapshots.
- Price policy: provider-neutral bid/ask midpoint.
- Freshness: explicit `FRESH`, `STALE`, and `UNAVAILABLE` account status.
- Risk integration: valuation refresh precedes PAPER CASH Risk fact assembly.
- Progression: generic BALANCE progression remains realized-only.
- Peak equity: advances only on successful fresh valuation.
- Concurrency: Account optimistic version protects valuation persistence.
- LIVE: broker synchronization remains authoritative.
- INVENTORY/SPOT: existing behavior remains unchanged.

## Validation

`mvn -q verify` passed in `trading-core`; focused Story tests and
`git diff --check` passed. SonarQube was not run.

## Files and Artifacts

Implementation files are under `trading-core`, including Account persistence,
V27 migration, settlement, Risk fact integration, valuation service, and focused
tests. Canonical Story 0086 artifacts and ADR-052 are present under `docs`.

## Vault Outcome

The vault was not consulted. No proposal-only vault action is required.

## Delivery Boundary

No commit, push, merge, pull request, or Story 0087 work was performed.
