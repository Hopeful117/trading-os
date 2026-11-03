# Implementation Report - Story 0193

## Result

Implemented locally and left uncommitted for human review. The implementation
preserves the provider-neutral Trading Core and Risk Domain boundaries while
adding capability-scoped cTrader observation semantics and provider-authoritative
ExpectedMargin resolution.

## Changes

- Added capability-scoped collection availability for positions, orders, PnL and
  deal history, including `AVAILABLE_EMPTY`, `AVAILABLE_NON_EMPTY`,
  `UNAVAILABLE_OR_ABSENT` and `TRUNCATED`.
- Removed the universal requirement for optional account `equity`, `usedMargin`
  and `freeMargin` fields from the cTrader observation/snapshot path without
  deriving replacement values.
- Preserved provider-authoritative position margin and kept it distinct from
  account-level used margin and proposed-order required margin.
- Added the broker-neutral cTrader `MarginCapability` implementation and the
  read-only `ProtoOAExpectedMarginReq/Res` adapter in Broker Service.
- Kept cTrader protocol IDs, volume scaling, `moneyDigits`, account assets and
  provider response validation inside Broker Service.
- Routed LIVE proposed-order margin through the existing `RequiredMarginPort`;
  PAPER continues to use the local deterministic margin policy.
- Added required-margin provenance to T0/T1 risk evidence and fail-closed
  handling for missing, stale, invalid or unsupported LIVE margin facts.
- Added focused tests for ExpectedMargin mapping, identity and precision
  validation, collection availability, and LIVE risk-facts admission.
- Added ADR-084 and the canonical Story analysis/plan artifacts.

## Safety Boundaries

```text
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_MARGIN_POLICY
MISSING_REQUIRED_MARGIN = FAIL_CLOSED
EQUITY_DERIVATION = NOT_ADDED
FREE_MARGIN_DERIVATION = NOT_ADDED
ACCOUNT_MARGIN_AGGREGATION = NOT_ADDED
CTRADER_PROVIDER_MUTATIONS = 0
AUTOMATIC_TRADING_AUTHORIZATION = NOT_ADDED
```

No cTrader credentials, provider connections or order operations were used.
The implementation adds no cTrader mutation capability.

## Validation

- Broker Service focused tests: PASS, 28 tests.
- Trading Core focused tests: PASS, 39 tests.
- Broker Service full suite: PASS, 342 tests.
- Trading Core full suite: PASS, 980 tests.
- Risk Domain suite: PASS, 43 tests.
- Broker Service and Trading Core compilation with tests skipped: PASS.
- `git diff --check`: PASS.

The full suites were executed independently because the repository contains
independent Maven modules rather than one root Maven build.

## Independent Review

An independent review was performed against the complete current diff. It found
two cTrader adapter defects, both corrected:

- ExpectedMargin payload IDs were corrected from `2267/2268` to the documented
  `2139/2140` request/response IDs.
- ExpectedMargin response validation now requires the returned provider volume
  to equal the requested scaled volume.

The review also identified two follow-up gaps that remain visible rather than
being hidden by the validation result:

- The current Risk Domain `AccountSnapshot` requires a non-null `dailyClosedPnl`,
  so fully policy-dependent deal-history admission requires a contract decision
  before missing history can safely be allowed for a policy that does not use it.
- Dedicated LIVE T0/T1 integration tests for missing or changed provider margin
  should be expanded beyond the existing T0 fail-closed and execution-pipeline
  regression coverage.

## Limitations

- Controlled READ-ONLY cTrader validation reached the connected LIVE account and
  confirmed the account's instrument identity (`EURUSD` and `XAUUSD`). The
  ExpectedMargin endpoint was then blocked by the persisted 20-minute snapshot
  cooldown after the first account-scoped read. The safety guard was not
  bypassed and no provider mutation occurred, so ExpectedMargin remains
  unvalidated against a live response.
- The local generated cTrader dependency does not expose dedicated Java types
  for the ExpectedMargin messages; the adapter uses the existing JSON session
  boundary while keeping provider semantics isolated in Broker Service.
- The Story remains pending human code review, human commit and any separately
  authorized read-only provider validation.

## Delivery

No commit, push, pull request, merge or branch cleanup was performed.
