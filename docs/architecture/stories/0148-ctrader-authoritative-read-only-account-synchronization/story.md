# Story 0148 - cTrader Authoritative READ-ONLY Account Synchronization

## Metadata

**ID:** `0148`
**Title:** cTrader Authoritative READ-ONLY Account Synchronization
**Status:** Implemented locally - real provider validation required
**Related Stories:** `0145`, `0146`, `0147`
**Related ADRs:** `ADR-040`, `ADR-071`, `ADR-072`, `ADR-073`, `ADR-074`

## Goal

Establish the smallest safe Broker Service capability that obtains a coherent,
fresh, owner-scoped and provider-authoritative READ-ONLY observation of an
authenticated and explicitly bound cTrader account.

The Story must preserve the distinction between:

```text
provider authentication != provider facts
provider facts != Risk approval
Risk approval != execution
```

## Scope

- Read authoritative cTrader account facts for the explicitly bound external
  account.
- Normalize cTrader account, position, pending-order and bounded deal/fill facts
  inside Broker Service.
- Preserve provider identifiers, monetary precision, volume semantics and
  provider timestamps without exposing cTrader DTOs outside Broker Service.
- Define explicit snapshot completeness, observation coherence and freshness.
- Reject cross-owner requests, wrong-account responses, expired authentication,
  transport failures and partial observations that cannot form a usable snapshot.
- Reuse or safely extend the existing provider-neutral Broker/Risk boundary.
- Preserve Kraken behavior and keep cTrader mutation capabilities unavailable.

## Out Of Scope

- cTrader order, cancel, amend, close, SL/TP or reconciliation mutation.
- FTMO Challenge phases, targets, drawdown, minimum days or headroom formulas.
- Market Data provider replacement or global cTrader instrument ingestion.
- AI, S1, scanners or Kafka/event-bus infrastructure.
- Automatic rebinding, credential switching or stale-fact reuse.
- Real credentials in tests or uncontrolled live-provider calls.

## Architecture Constraints

- cTrader remains Broker Service infrastructure; Trading Core receives only
  provider-neutral facts.
- `Account.brokerAccountId -> BrokerAccount.id -> BrokerConnection` remains the
  canonical owner-scoped route.
- Every provider response must match the durable bound cTrader external account.
- Missing facts remain missing; they are never converted to zero or empty values
  unless cTrader explicitly reports zero or empty.
- No `COMPLETE + FRESH` authoritative observation means no valid cTrader Risk
  context and no future cTrader execution admission.
- Existing Kraken capabilities and semantics must remain unchanged.

## Acceptance Criteria

- [ ] An authenticated, bound cTrader account can be synchronized through an
      owner-scoped Broker Service operation.
- [ ] Provider response identity is checked against the explicit binding and a
      mismatch fails closed without rebinding.
- [ ] Account currency, balance, equity, used/free margin where authoritative,
      positions, pending orders and bounded deals/fills preserve provider facts
      and precise monetary/volume semantics.
- [ ] Account, position, order and deal normalization contains no cTrader DTO,
      transport type, token or provider error outside Broker Service.
- [ ] Snapshot completeness, provider observation timestamps, synchronization
      completion and freshness are explicit and deterministic.
- [ ] Partial or incoherent reads are not published as complete observations.
- [ ] Restart does not make previous facts fresh; expired/revoked authentication
      blocks synchronization.
- [ ] Owner isolation, account identity mismatch, stale response, provider
      timeout, transport disconnect and persistence failure fail closed.
- [ ] Repeated synchronization does not create inconsistent duplicates and uses
      authoritative external identifiers where available.
- [ ] No cTrader mutation capability is registered or exposed.
- [ ] Kraken synchronization, RiskSnapshot and execution regression tests remain
      green.
- [ ] Real cTrader validation remains explicitly separate from deterministic
      local validation and uses READ-ONLY scope with no orders.

## Required Focused Tests

- complete synchronization;
- not authenticated, not bound, expired and revoked authentication;
- cross-owner request and wrong external-account response;
- account, position, order and deal partial failures;
- provider timeout, disconnect, malformed payload and persistence failure;
- coherence window and stale-observation rejection;
- money precision and cTrader volume normalization;
- restart and duplicate synchronization semantics;
- credential/payload redaction and Trading Core boundary inspection;
- cTrader mutation capability absence;
- Kraken regression.

## Validation Boundary

```text
LOCAL_DETERMINISTIC_VALIDATION = REQUIRED
REAL_CTRADER_READ_VALIDATION = REQUIRED
CTRADER_EXECUTION = NO
FTMO_CHALLENGE_INTERPRETATION = NO
S1_REQUIRED = NO
AI_REQUIRED = NO
```

## Next Workflow Gate

Human approval of the Repository Analysis is complete. ADR-074 approval is
required before producing the Implementation Plan or authorizing changes to the
authoritative provider-observation boundary.
