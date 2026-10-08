# Implementation Report - Story 0051

## Status

`COMPLETED - HUMAN REVIEWED AND COMMITTED`

## Scope Delivered

The implementation now adds a provider-backed Kraken capability and required-margin
query path without creating a commit.

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
implementation commit: not created; human review pending
Broker Service: 209 tests passed
Trading Core: 580 tests passed
Risk Domain: 26 tests passed
mvn verify: passed; JaCoCo checks met
git diff --check: passed
runtime E2E: authenticated provider-backed Kraken proof passed; PAPER preview
  revalidation is pending after follow-up Trading Core wiring
```

## Remaining Evidence

* the authenticated local runtime proof exercised `AssetPairs` and returned
  side-specific leverage levels plus a `1000.00 USD` margin preview;
* provider-specific payload isolation is covered by the neutral contracts and
  adapter tests;
* Story 0051 is closed after independent review, human approval, and commit creation.
