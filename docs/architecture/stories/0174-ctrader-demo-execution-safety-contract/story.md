# Story 0174 - cTrader DEMO Execution Safety Contract

## Metadata

**ID:** `0174`
**Title:** cTrader DEMO Execution Safety Contract
**Status:** Implemented Locally - Awaiting Review
**Related ADRs:** `ADR-045`, `ADR-071`, `ADR-073`, `ADR-078`
**Related Stories:** `0170`, `0173`

## Goal

Make cTrader order construction deterministic and fail closed before a future,
explicitly authorized DEMO execution test. Trading Core quantities remain
broker-neutral absolute units; cTrader-specific volume and instrument facts stay
inside Broker Service.

## Contract

cTrader Open API represents order volume as an integer in hundredths of a unit.
The adapter converts a positive Trading OS quantity using exact decimal
arithmetic, then validates the provider's current `minVolume`, `maxVolume`, and
`stepVolume` constraints. The adapter rejects non-representable quantities
instead of rounding them.

The instrument contract also carries the provider symbol ID, price precision,
pip position, lot size when supplied, and stop/take-profit distance metadata
when supplied by cTrader.

Reference: https://help.ctrader.com/open-api/messages

## Scope

- Resolve one unambiguous cTrader symbol and its provider metadata.
- Validate exact quantity conversion and volume constraints.
- Validate positive protection and limit prices at provider precision.
- Reject unsupported order types and multiple take-profit prices.
- Preserve account, environment, human authorization, idempotency and unknown
  outcome boundaries.
- Add deterministic multi-contract unit tests.

## Out of Scope

- Any live or demo broker order.
- Position close or partial close.
- Market Data, Level II, storage, backfill or retention.
- Risk, strategy, AI, News or frontend redesign.
- Provider E2E validation.

## Acceptance Criteria

- [x] Trading OS quantity remains broker-neutral and is not silently rounded.
- [x] cTrader volume conversion uses exact decimal arithmetic and explicit
      provider volume semantics.
- [x] Minimum, maximum and step constraints fail closed.
- [x] Multiple materially different provider instrument contracts are tested.
- [x] Price precision is validated without binary rounding.
- [x] Ambiguous or unknown symbols fail closed.
- [x] Unsupported order types fail closed.
- [x] Multiple take-profit targets are rejected explicitly.
- [x] MARKET orders with unsupported absolute SL/TP protections fail before
      provider access.
- [x] Existing account, environment, authorization, idempotency and unknown
      outcome controls remain unchanged.
- [x] No broker mutation is performed.
- [ ] Provider-authenticated metadata and DEMO E2E execution remain a separate
      follow-up validation; this Story does not claim that proof.

## Validation

- `mvn -f broker-service/pom.xml -Dtest=CTraderExecutionInstrumentContractTest,CTraderExecutionCapabilityTest,CTraderBrokerProviderTest,JsonWebSocketCTraderOpenApiClientTest test`
- No cTrader order, position mutation or account mutation is authorized.

## Documentation Outcome

No ADR is required. ADR-045 already assigns provider-specific metadata and
mapping to Broker Service, while Trading Core remains responsible for business
risk and execution authorization.
