# Code Review Checklist - Story 0051

## Reviewed Scope

* Broker-neutral capability and required-margin contracts.
* Broker Service query controller and operation service.
* Kraken capability adapter and configuration.
* Trading Core capability and margin clients.
* Focused provider and client tests from commit `40e5258`.

## Findings

No confirmed defect was established from the implementation commit and the
available Story scope.

## Review Notes

* Broker Service reports technical facts; it does not make a risk decision.
* Provider-specific mapping remains inside the Kraken infrastructure adapter.
* Trading Core remains responsible for account ownership and risk-context
  assembly.
* Capability and margin unavailability must remain distinguishable from a
  valid technical fact.

## Human Review Required

* Confirm route authentication and actor propagation with the current Gateway
  and service-security configuration.
* Confirm stale-data behavior in the integrated Trading Core path.
* Run the affected Maven suites and inspect contract serialization and
  integration coverage.
