# Story 0050 - Establish deterministic PAPER risk facts

## Metadata

**ID:** `0050`
**Title:** Establish deterministic PAPER risk facts
**Status:** Draft

---

## Goal

Provide a complete, deterministic local financial snapshot for PAPER risk
evaluation while preserving the authority boundaries established by ADR-028
and ADR-042.

PAPER position and account truth remains owned by Trading Core. Broker Service
is not called for PAPER position mutation or external reconciliation.

---

## Context

The current PAPER path reads local balances and trades but returns an incomplete
snapshot with:

```text
PAPER_RISK_LEDGER_UNAVAILABLE
PAPER_MARGIN_UNAVAILABLE
PAPER_POSITION_PROTECTION_UNAVAILABLE
```

The local account and trade facts must be made complete before the Risk Domain
can evaluate a PAPER Trade Plan. Broker capability and margin facts are handled
by Story 0051 and are consumed through a broker-neutral input boundary.

---

## Scope

* Add a dedicated PAPER risk-facts implementation in Trading Core.
* Derive balances from persisted local account state.
* Map open PAPER trades to risk positions.
* Map closed PAPER trades to closed-trade facts.
* Represent absent protective orders explicitly rather than treating the whole
  local snapshot as unavailable.
* Produce stable provenance and source versions for local facts.
* Preserve the LIVE Broker Service facts path unchanged.
* Keep the Risk Domain independent from repositories and external services.
* Consume broker-neutral margin/capability facts without owning their provider
  retrieval. Provider capability work belongs to Story 0051.

---

## Out of Scope

* Changing risk-policy rules or thresholds.
* Implementing broker capability discovery.
* Implementing broker margin preview.
* Moving PAPER execution or settlement to Broker Service.
* Creating a universal Position aggregate.
* Creating a full persisted PAPER ledger unless local facts prove insufficient.
* Changing LIVE position authority or reconciliation.
* Completing Story 0048 runtime acceptance.

---

## Domain Rules

### PAPER authority

For PAPER accounts, Trading Core is authoritative for local account balances,
trades, positions, settlement, and reloadable financial state.

### Position mapping

```text
Trade.tradeId       -> positionId
Trade.symbol        -> instrument
Trade.type          -> signedQuantity
Trade.entryPrice    -> entryPrice
Trade.quantity      -> absolute quantity
```

An absent stop is represented as zero protected quantity and an empty stop
collection. The Risk Domain decides the consequence using the effective rules.

### Fail closed

Incomplete local account data, invalid ownership, inconsistent mappings, or
missing required market/capability facts must not produce an approval.

---

## Acceptance Criteria

* [ ] A valid PAPER account produces a complete local risk-facts snapshot.
* [ ] The PAPER provider never calls Broker Service for position or account
      mutation/reconciliation.
* [ ] Balances and equity are represented with correct valuation asset and
      provenance.
* [ ] Open PAPER trades map to positions with correct side, quantity, and entry
      basis.
* [ ] Closed PAPER trades contribute to daily closed-PnL facts.
* [ ] Missing protection is represented explicitly and deterministically.
* [ ] LIVE facts still use Broker Service and remain covered by regression tests.
* [ ] The resulting local snapshot can be assembled into the immutable
      `RiskEvaluationContext`.
* [ ] Missing or inconsistent facts remain fail-closed.
* [ ] Affected Trading Core and Risk Domain tests pass.
* [ ] `git diff --check` passes.

---

## Relevant ADRs

* `ADR-006.md` - Market Data Service Responsibilities
* `ADR-028.md` - Risk Engine Architecture
* `ADR-029.md` - Execution Domain Architecture
* `ADR-042.md` - Position Authority and Close Semantics by Execution Mode
* `ADR-045.md` - Broker Facts and Capability Boundaries (proposed)

## Related Stories

* `0040-mode-aware-neutral-risk-facts`
* `0041-paper-local-position-query-valuation`
* `0043-paper-account-risk-profile-onboarding`
* `0048-paper-trading-journey-runtime-acceptance`
* `0051-broker-capabilities-and-margin-facts`

---

## Validation

* Trading Core unit tests for local PAPER facts.
* Trading Core integration tests for immutable risk-context assembly.
* LIVE regression tests confirming Broker Service delegation.
* Affected Maven module tests.
* `git diff --check`.

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved.
* [ ] PAPER local facts implemented.
* [ ] Affected tests pass.
* [ ] Human code review completed.
* [ ] Engineering Report completed.
* [ ] Human commit created.
