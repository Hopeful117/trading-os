# Story 0189 - ADR-082 Phase 8 Retry - CASH + CFD Compatibility Authorization

## Metadata

**ID:** `0189`
**Title:** ADR-082 Phase 8 Retry - CASH + CFD Compatibility Authorization
**Status:** Blocked - CASH + CFD remains rejected
**Related ADRs:** `ADR-082`, `ADR-083`
**Predecessor Story:** `0188`

## Decision

The Phase 8 retry is blocked. Story 0188 introduced a local PAPER margin policy
path, but the repository does not yet prove the complete ADR-083 T0/T1 contract
or the durable policy/exposure authority required to authorize `CASH + CFD`.

```text
READY_FOR_CASH_PLUS_CFD = NO
SETTLEMENT_COMPATIBILITY_CHANGED = NO
ADR_082_PHASE_8 = BLOCKED
```

## Scope Completed

- Verified the current `origin/main` and local repository state.
- Read ADR-082, ADR-083, Stories 0183-0188 and all available Story 0188 artifacts.
- Reconstructed the Story 0188 production diff and tests.
- Built the Phase 8 readiness matrix in `repository-analysis.md`.
- Ran focused and applicable regression validation.
- Preserved fail-closed compatibility behavior.

## Blockers

1. `PaperRequiredMarginClient` invokes Market Data with T0 freshness even when
   resolving T1 and calculates from `Request.price`; no proof binds the
   calculation to the fresh T1 valuation price.
2. No application-level test proves that an authoritative input change between
   T0 and T1 changes the independently recalculated required margin or causes
   `T0 = PASS` and `T1 = REJECT`.
3. V49 persists policy/version rows but does not enforce database immutability or
   the ADR-083 maximum eight-decimal rate precision. `@Immutable` is not a
   database invariant.
4. No direct persistence/assignment/restart test proves policy identity,
   assignment provenance, and historical version lookup through PostgreSQL.
5. The exposure resolver is unit-tested but Story 0188 does not provide a full
   application-level PAPER margin and settlement E2E proving all required facts
   together.
6. Story 0188 has no tracked `code-review.md` artifact, and its report states
   that the full Trading Core suite and PostgreSQL assignment integration were
   not run.

## Safety Invariants

```text
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
UNKNOWN_PLUS_CFD = REJECTED
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
```
