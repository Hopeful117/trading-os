# Story 0051 - Expose broker capabilities and margin facts

## Metadata

**ID:** `0051`
**Title:** Expose broker capabilities and margin facts through broker-neutral contracts
**Status:** Draft

---

## Goal

Expose the technical capabilities and margin facts required by Trading Core
through Broker Service without moving business authorization or risk decisions
out of Trading Core and the Risk Domain.

## Architectural Boundary

The implementation must preserve:

* ADR-006: Market Data owns instrument metadata and market constraints.
* ADR-028: Risk Domain owns deterministic authorization.
* ADR-029: Trading Core owns execution lifecycle.
* ADR-030: Broker Service reports technical broker facts and isolates providers.
* ADR-042: Trading Core remains PAPER position authority.
* ADR-044: internal calls use service authentication and ownership remains in
  Trading Core.

Broker Service may report provider capabilities and technical margin facts. It
must not authorize a trade or own PAPER position state.

## Scope

* Define immutable broker-neutral capability contracts.
* Expose supported leverage levels by provider and instrument.
* Expose technical order capability facts and versions.
* Expose a broker-neutral margin preview contract.
* Use the same provider capability model for PAPER and LIVE.
* Keep provider-specific implementation inside Broker Service adapters.
* Authenticate Trading Core internal calls with a service JWT.
* Read capabilities on demand for critical operations.
* Return version, source, and observation timestamp.
* Fail closed when capabilities or margin facts are unavailable or stale.
* Add contract and integration tests.

## Out of Scope

* Risk authorization or rule evaluation.
* Ownership decisions for user accounts.
* Market instrument metadata owned by Market Data.
* PAPER position mutation or settlement.
* Moving PAPER execution to Broker Service.
* Automatic leverage selection.
* Static unlimited fallback capability catalogs.

## Acceptance Criteria

* [ ] Broker Service exposes broker-neutral capability contracts.
* [ ] Provider-specific payloads do not cross the Broker Service boundary.
* [ ] Capabilities are resolved from provider and instrument identity.
* [ ] Supported leverage levels are returned explicitly and versioned.
* [ ] Market Data remains authoritative for instrument metadata and constraints.
* [ ] Margin preview returns amount, currency, source ID, version, and timestamp.
* [ ] PAPER and LIVE use the same capability contract.
* [ ] Capability and margin reads use Trading Core service authentication.
* [ ] Missing or stale data produces a technical unavailable result.
* [ ] Broker Service does not make a business risk decision.
* [ ] Contract and integration tests pass.
* [ ] `git diff --check` passes.

## Related ADRs

* `ADR-006.md`
* `ADR-028.md`
* `ADR-029.md`
* `ADR-030.md`
* `ADR-044.md`
* `ADR-045.md`

## Related Stories

* `0050-paper-risk-context`
* `0052-paper-execution-via-broker-contract`
* `0048-paper-trading-journey-runtime-acceptance`

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved.
* [ ] Contracts and provider adapters implemented.
* [ ] Security and ownership boundaries tested.
* [ ] Affected tests pass.
* [ ] Human code review completed.
* [ ] Engineering Report completed.
* [ ] Human commit created.
