# Implementation Report - Story 0051

## Status

`CLOSED - HUMAN REVIEWED, COMMITTED AND PAPER RUNTIME VALIDATED`

## Scope Delivered

The implementation adds a provider-backed Kraken capability and required-margin
query path through the Story implementation and documentation commits.

* Broker Service exposes capability facts through its query controller and
  operation service.
* Provider capability data is represented through neutral domain models.
* Kraken capability mapping remains inside the Kraken adapter.
* Trading Core consumes capability and required-margin facts through dedicated
  clients.
* Kraken `AssetPairs?info=info&assetVersion=1` supplies the instrument quote
  currency and side-specific leverage levels used by the margin preview.
* Required margin is calculated from the requested notional and a provider-supported
  leverage level; missing or invalid provider facts still fail closed.
* Trading Core rejects capability and margin responses with mismatched account
  or instrument identity, missing provenance, invalid versions, invalid lists,
  or stale timestamps.
* Configuration and focused tests were added for the provider and margin
  client paths.
* BUY and SELL leverage levels are exposed separately in the neutral contract;
  invalid trade directions are rejected before broker calls.
* Positive authenticated endpoint tests, contract serialization tests, and
  provider-unavailable fail-closed tests were added.

The implementation does not move risk authorization or PAPER position
authority into Broker Service.

## Validation Evidence

The implementation includes `KrakenCapabilitiesTest` and
`KrakenRestProviderClientTest`. Fresh Maven validation covered the planned
Broker Service, Trading Core, and Risk Domain boundary after the client
hardening.

```text
implementation commit: 3dcbf283070be9df0f0d0e9966e8a20bfd0de95
documentation commit: f2eee8cdc729d74ccd2326bc07f71e376ab6af49
Broker Service: tests passed (`./mvnw -q test` and `./mvnw -q verify`)
Trading Core: full suite passed (`./mvnw -q test`)
Risk Domain: 26 tests passed
mvn verify: passed; JaCoCo checks met
git diff --check: passed
runtime E2E: authenticated provider-backed Kraken proof passed
fresh PAPER proof: artifacts/story-0051-runtime-proof.json
fresh PAPER scan: COMPLETED
fresh PAPER Risk: APPROVED
fresh PAPER Risk evaluation: `ec3e9f4b-4c67-4762-a715-f5827e729283`
fresh PAPER TradePlan: `cb7a97dc-8ce4-4901-936a-dee6ce1d9f69`, version `2`
fresh PAPER negative validation: REJECTED by blocking risk rules
fresh PAPER execution: not attempted
```

## Remaining Evidence

* the authenticated local runtime proof exercised `AssetPairs` and returned
  side-specific leverage levels plus a provider-backed margin preview;
* provider-specific payload isolation is covered by the neutral contracts and
  adapter tests;
* Story 0051 is closed after independent review, human approval, commit
  creation, and fresh PAPER validation.
