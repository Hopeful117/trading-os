# Code Review Checklist - Story 0080

## Reviewed Scope

* Shared Trading Core Market Data valuation port.
* Multi-asset Dashboard account balance valuation.
* Dashboard equity source/status propagation.
* Explicit broker position-PnL treatment.
* Incomplete/unavailable valuation handling.
* Focused Dashboard and valuation tests.

## Findings

The initial independent review identified five major defects. They were corrected
in the follow-up implementation:

* PAPER Dashboard reads persisted Trading Core balances and positions without
  calling the external broker.
* Broker equity fallback now requires an explicit total-equity flag.
* Position PnL is included only when its valuation is compatible with the account
  currency; unsupported or unavailable PnL fails closed.
* Missing balance payloads and null balance amounts produce degraded valuation.
* Broker PnL and equity semantics are explicit in the normalized contract.

## Review Notes

* Trading Core remains the owner of Dashboard aggregation.
* Market Data remains the source of valuation observations and freshness policy.
* The frontend performs no cross-asset calculation.
* Missing or incomplete valuation does not fabricate equity or risk percentages.
* Broker equity preference and divergence handling remain in `AccountEquityService`.
* The normalized PnL treatment prevents implicit double counting at the
  Dashboard aggregation boundary.
* The typed frontend model now accepts valuation status and provenance metadata.
* Existing Risk Domain callers retain the compatibility valuation port.
* No commit, push, or integration action was performed.

## Residual Risks

* No authenticated runtime proof was performed with a real multi-asset account.
* The current broker service does not populate `brokerEquityTotal`; broker equity
  is therefore not selected unless a provider explicitly establishes that fact.
* Existing test logs contain unrelated expected warnings from negative test
  scenarios.

## Human Review Decision

Pending human review and acceptance. The independent review findings are
addressed, but no authenticated runtime proof was performed with a real
multi-asset account.
