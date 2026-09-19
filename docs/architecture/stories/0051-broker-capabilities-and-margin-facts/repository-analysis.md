# Repository Analysis - Story 0051

## Baseline

```text
STORY = 0051-broker-capabilities-and-margin-facts
BRANCH = story/0050-paper-risk-context (planning artifact; implementation branch to be created later)
WORKTREE = pre-existing local modifications plus Story 0050 artifacts
```

## Existing Authority

ADR-030 defines Broker Service as the technical provider boundary. It exposes
broker-neutral contracts, translates provider payloads, and reports technical
facts. It does not own execution authorization, risk decisions, lifecycle, or
reconciliation decisions.

ADR-006 assigns market metadata and instrument constraints to Market Data. A
new Broker Service capability contract must not duplicate that authority.

## Required Contract Boundary

The intended flow is:

```text
Trading Core
    -> internal broker-neutral request
        -> Broker Service
            -> provider capability adapter
                -> technical broker fact
```

Trading Core owns account ownership and maps the user account to a broker
account. Broker Service validates the internal caller and resolves the provider
technical operation.

## Capability Categories

The first contract should distinguish:

* provider/account capabilities, such as supported leverage levels;
* technical order capabilities, such as supported order types;
* margin facts for a concrete proposed order;
* source version and freshness metadata.

Instrument metadata such as precision and minimum order size remains a Market
Data concern. If a provider-specific execution constraint is not available from
Market Data, the contract must identify its authority explicitly rather than
silently duplicating it.

## Security Boundary

Internal Trading Core calls require a service JWT with an explicit Broker
Service audience. User ownership remains checked in Trading Core. Broker Service
must not trust client-provided actor identifiers as ownership proof.

## Risks

* Existing Broker Service capabilities may not yet expose a generic catalog.
* Kraken may not provide all leverage facts through the current adapter.
* Margin semantics may differ by instrument and account type.
* A technical capability fact must not be interpreted as risk approval.
* The public Trading Core endpoint must avoid leaking credentials or provider
  payloads.

## Validation Plan

* Contract tests for records and serialization.
* Provider adapter tests for capability mapping.
* Margin preview tests for valid, stale, unsupported, and unavailable cases.
* JWT trust-boundary tests.
* Trading Core to Broker Service integration tests.
* Regression tests proving Market Data ownership is not duplicated.

## Workflow Gate

```text
ADR_CONFLICT_CHECK = completed against ADR-006, ADR-028, ADR-029, ADR-030, ADR-042, ADR-044
REPOSITORY_ANALYSIS = completed
IMPLEMENTATION_PLAN = required
HUMAN_APPROVAL = required before implementation
```
