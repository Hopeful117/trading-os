# Story 0175 - cTrader Market Order Protection Contract

## Metadata

**ID:** `0175`
**Title:** cTrader Market Order Protection Contract
**Status:** Implemented Locally - Awaiting Review
**Related ADRs:** `ADR-045`, `ADR-071`, `ADR-073`, `ADR-078`
**Related Stories:** `0170`, `0173`, `0174`

---

## Goal

Close the cTrader execution safety gap identified by Story 0174: a Trading OS
`MARKET` execution request may require an attached protective stop, while cTrader
Open API may not accept absolute stop-loss and take-profit prices on a market
order.

The Broker Service must construct a provider-compatible protected order or reject
the request before provider mutation. Trading Core quantities, protection intent,
risk decisions and human authorization remain provider-neutral and unchanged.

---

## Context

Story 0174 introduced deterministic cTrader instrument contracts for volume,
price precision and provider constraints. It also made the adapter fail closed
for `MARKET` orders carrying absolute SL/TP prices because the official cTrader
Open API documentation does not establish that these fields are supported for
market orders.

The current safety behavior prevents an unsafe order but leaves the intended
protected market-order path incomplete. A future cTrader DEMO execution proof
cannot proceed until the adapter has a documented and tested representation for
the required protection semantics.

cTrader provider metadata includes price precision, pip position and optional
stop/take-profit distance constraints. The provider adapter owns the protocol
mapping and provider error semantics; Trading Core owns execution lifecycle,
authorization and deterministic orchestration.

---

## Problem

The generic execution contract carries absolute `stopLossPrice` and
`takeProfitPrices`, but cTrader market-order protection may require a different
provider representation, such as a provider-supported relative distance or a
subsequent protected amendment. The repository currently has no validated
contract for that translation.

Sending unsupported protection fields could result in a rejected order, an order
without effective protection, or provider behavior that cannot be proven from
the response. Treating an acknowledged order as protected without authoritative
evidence would violate the deterministic risk and fail-closed boundaries.

---

## Scope

- Define the provider-compatible cTrader representation of required protection
  for a `MARKET` order, based on the documented Open API contract and current
  instrument metadata.
- Preserve the broker-neutral absolute protection intent at the Trading Core and
  Broker Service capability boundaries.
- Preserve the existing provider-neutral MARKET reference price from the
  Market Intelligence Trade Plan snapshot through Trading Core execution.
- Convert and validate protection values using exact decimal arithmetic and the
  provider instrument's precision, pip position and distance constraints.
- Reject protection configurations that cannot be represented or cannot be
  proven effective before any provider order mutation.
- Preserve the existing volume, symbol identity, idempotency, authorization,
  environment, freshness and unknown-outcome safeguards from Story 0174.
- Add deterministic adapter and capability tests covering valid conversion,
  invalid precision, minimum distances, direction-specific validity and
  fail-closed behavior.
- Document the provider contract and residual DEMO validation boundary.

---

## Out of Scope

- Any live or DEMO broker order, position mutation, cancellation or close.
- Provider-authenticated E2E validation; that remains a separately authorized
  runtime activity under Story 0170.
- Changes to Trading Core execution lifecycle, Risk formulas, Challenge rules,
  human authorization or execution mode semantics.
- Automatic retry, compensation or protection mutation after an ambiguous order
  outcome unless a separate approved Story defines it.
- General multi-broker protection redesign.
- Market Data ownership changes or creation of a new instrument catalogue.
- UI, frontend, AI, strategy or trade-plan redesign.

---

## Acceptance Criteria

- [x] The provider-compatible cTrader protection representation for a MARKET
      order is documented and traceable to the official Open API contract.
- [x] A broker-neutral absolute stop-loss intent is translated only inside
      Broker Service; no cTrader-specific protection fields leak to Trading Core
      or Risk Domain.
- [x] Protection conversion uses exact decimal arithmetic and the current
      instrument precision without binary rounding.
- [x] Provider minimum, maximum and step/distance constraints are validated
      before any provider order mutation.
- [x] Direction-specific stop-loss and take-profit validity is enforced
      deterministically.
- [x] An unrepresentable, ambiguous, stale, incomplete or contradictory
      protection contract fails closed before provider submission.
- [x] A provider acknowledgement is not treated as proof of effective
      protection unless the supported request representation establishes it.
- [x] Existing volume, symbol identity, idempotency, authorization, environment,
      freshness and unknown-outcome controls remain unchanged.
- [x] Existing behavior remains fail-closed when no provider-compatible
      protected MARKET order representation is available.
- [x] Deterministic tests cover at least two materially different instrument
      contracts and both protected and rejected MARKET order paths.
- [x] No broker mutation is performed by automated tests, builds or startup.
- [x] The Story 0174 regression suite and the affected Broker Service module
      tests pass.

---

## Constraints

- Preserve `ADR-045` responsibility boundaries: provider technical facts and
  mappings remain in Broker Service; Risk and business authorization remain
  outside the adapter.
- Preserve `ADR-071` execution safety, idempotency and uncertain-outcome rules.
- Preserve `ADR-078`: a cTrader DEMO account remains Trading OS `LIVE` because
  execution crosses an external provider boundary.
- Never silently remove a required protective stop to make an order acceptable.
- Never round a quantity, price or protection distance into a provider-valid
  value without explicit contract semantics.
- Do not infer protection effectiveness from a local request object or an
  acknowledgement that does not establish the provider state.
- Do not create, log, print or commit credentials, tokens or private provider
  payloads.
- Do not commit, push, merge or execute a runtime provider test automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-045.md`
- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`

---

## Relevant Modules

- `broker-service`
- `market-intelligence` for preserving the existing Trade Plan reference price
  in the internal risk snapshot contract
- `trading-core` for provider-neutral contract regression verification only
- `risk-domain` for deterministic boundary regression verification only

---

## Validation

- Verify the official cTrader Open API representation for protected market
  orders and record the relevant contract evidence.
- Run focused cTrader adapter, instrument-contract and execution-capability
  tests.
- Run the complete `broker-service` Maven test suite.
- Run provider-neutral Trading Core and Risk Domain regression tests when the
  shared contract surface is affected.
- Run the affected Market Intelligence Trade Plan snapshot tests when its
  internal execution snapshot contract is affected.
- Run `git diff --check` and review the complete Story implementation diff.
- Do not perform authenticated cTrader execution as part of automated
  validation.

---

## Definition of Done

- [x] Repository Analysis approved
- [x] Implementation Plan approved when required
- [x] Implementation completed
- [x] Documentation reconciliation completed
- [x] Relevant validation executed
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created

## Documentation Outcome

For cTrader `MARKET` orders, the adapter sends the provider-supported relative
protection fields `relativeStopLoss` and `relativeTakeProfit`, expressed in the
instrument's provider distance unit. Trading OS continues to carry absolute
protection prices and a provider-neutral `referencePrice`; only the Broker
Service converts these values using the resolved symbol precision, pip position,
distance unit and minimum distance metadata.

The adapter rejects missing reference prices, invalid direction, invalid
precision, unsupported distance constraints, ambiguous instruments and
unsupported protection configurations before sending `NEW_ORDER`. A successful
provider acknowledgement remains an acknowledgement of the submitted request,
not independent proof of the resulting position state.

The official contract reference is the cTrader Open API message documentation:
https://help.ctrader.com/open-api/messages

No authenticated cTrader DEMO execution was performed. Provider metadata
discovery and end-to-end proof of effective protection remain separately
authorized runtime validation under Story `0170`.
