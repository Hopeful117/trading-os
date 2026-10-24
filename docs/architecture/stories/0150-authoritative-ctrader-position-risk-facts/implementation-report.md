# Story 0150 - Implementation Report

## Result

`IMPLEMENTED LOCALLY - REAL CTRADER POSITION VALIDATION REQUIRED`

The smallest provider-neutral extension is implemented. cTrader position
`usedMargin` and current position `stopLoss` now reach the existing deterministic
Risk facts boundary.

## Changed Surfaces

- Broker Service provider-owned position observation.
- Broker-neutral `PositionSnapshot`.
- Trading Core `BrokerPositionSnapshotV2Dto`.
- cTrader JSON normalization.
- `ModeAwareRiskFactsProvider` mapping.
- Broker Service and Trading Core focused tests.
- Story 0150 closure artifacts.

## Fact Semantics

- Position margin is a direct cTrader fact, normalized from provider money
  precision; no derivation or account-margin allocation is performed.
- Stop-loss is read from the current cTrader position observation and is
  normalized as provider-authoritative protection evidence.
- A missing stop-loss is not converted to protected; it becomes
  `UNPROTECTED`, preserving existing Risk behavior.
- A missing or invalid position margin marks the Risk snapshot incomplete.
- Current market value remains supplied by the existing market valuation port.
- Account equity remains the authority for aggregate unrealized P&L; the mapping
  does not double-count position P&L.

## Completeness and Freshness

Story 0148 completeness/freshness semantics are reused. A position with missing
margin is incomplete. A position with no stop-loss is complete but unprotected,
which allows the existing deterministic Risk rules to decide according to the
trade-plan mode.

## Documentation Outcome

Story 0150 artifacts record the implementation and provider evidence. ADR-074
already covers the architecture; no new ADR was required.

## Vault Outcome

The vault was not consulted. No curated-vault action is proposed.

## Validation

| Check | Result |
|---|---|
| Broker Service compilation | PASS |
| Trading Core compilation | PASS |
| cTrader JSON mapper and authoritative capability tests | PASS |
| Trading Core provider and targeted Risk tests | PASS |
| Broker synchronization regression | PASS |
| `git diff --check` | PASS |
| Real cTrader position Risk validation | NOT RUN - controlled provider access unavailable |

Mockito/JDK dynamic-agent warnings were emitted during tests and did not fail
the test runs.

## Runtime Validation Attempt

- Local LIVE cTrader BrokerAccount created successfully:
  `4075e131-45ee-4ff5-85b7-052ac5ede330`.
- Gateway cTrader authorization route was added, rebuilt and reached
  Broker Service successfully.
- OAuth initiation returned `200` with scope `accounts`.
- cTrader rejected authorization because the OA application status is
  `Submitted`, not active: `OA client is not in active state`.
- No cTrader external account was bound.
- No order, cancel, amend, close, SL/TP or other mutation was attempted.

**Runtime status:** `BLOCKED_PENDING_SPOTWARE_APPROVAL`

Resume criteria: the cTrader Open API application becomes active/approved;
then initiate a fresh OAuth attempt and complete the bounded READ-ONLY
position-risk validation.
