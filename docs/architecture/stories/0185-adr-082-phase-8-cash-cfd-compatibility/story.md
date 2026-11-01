# Story 0185 - ADR-082 Phase 8 CASH + CFD Compatibility Authorization

## Metadata

**ID:** `0185`
**Title:** ADR-082 Phase 8 CASH + CFD Compatibility Authorization
**Status:** Blocked - CASH + CFD remains rejected
**Related ADR:** `ADR-082`
**Related Stories:** `0178`, `0179`, `0180`, `0181`, `0182`, `0183`, `0184`

## Decision

Phase 8 does not authorize `CASH + CFD`. The compatibility policy remains
unchanged because the current proposed-order margin path does not prove a
mode-correct, authoritative CFD margin fact for the PAPER capability gate.

## Readiness Matrix

| Capability | Implemented | Tested | Authoritative source | Fail-closed behavior | Blocker |
|---|---|---|---|---|---|
| Canonical CFD classification | YES | YES | Market Data product type | Missing/ambiguous identity unavailable | NO |
| Canonical financial contract | YES | YES | Market Data and persisted Trade projection | Missing contract unavailable | NO |
| Signed quantity and multiplier | YES | YES | Normalized contract boundary | Invalid facts unavailable | NO |
| Persisted restart-safe contract | YES | YES | Trade contract projection | Incomplete projection unavailable | NO |
| PAPER entry and MTM | YES | YES | PaperCfdSettlementService and CfdValuationService | Missing valuation unavailable | NO |
| PAPER full exit and exactly-once evidence | YES | YES | PaperCfdSettlementService and provenance store | Replay/rollback guarded | NO |
| LIVE account/position authority | YES | YES | Story 0183 authoritative snapshot | Incomplete/stale snapshot unavailable | NO |
| LIVE reconciliation | YES | YES | LiveCfdReconciliationService | Unsafe state unavailable | NO |
| CFD notional and stop risk | YES | YES | CfdFinancialCalculator via CfdRiskProjectionService | Missing facts unavailable | NO |
| T0 CFD risk | YES | PARTIAL | TradePlanRiskEvaluationService | Missing risk facts unavailable | NO |
| T1 CFD risk | YES | PARTIAL | ExecutionTimeRiskRevalidationService | Missing risk facts unavailable | NO |
| Human authorization and execution gates | YES | YES | ValidateAndCreateService and execution pipeline | Missing approval blocks execution | NO |
| Dedicated PAPER CFD settlement routing | YES | PARTIAL | ExecutionFinalizationStep | Dedicated service selected by product | NO |
| PAPER proposed-order required margin | NO | NO | Current BrokerRequiredMarginClient calls provider preview | Optional empty fails risk closed | YES |
| LIVE proposed-order required margin | PROVIDER-DEPENDENT | PARTIAL | Broker Service margin preview | Optional empty fails risk closed | NEEDS CONTRACT PROOF |
| Provider quantity normalization for Phase 8 | PARTIAL | PARTIAL | Broker/Market Data adapters | Unsupported facts unavailable | NEEDS PROOF |

## Hard Gate

`BrokerRequiredMarginClient` routes PAPER requests to the provider margin
preview endpoint. The current Kraken capability implementation uses
`quantity * price / leverage`, with a default cash leverage of `1`. This is an
infrastructure calculation, not a proven canonical CFD proposed-order margin
contract for the local PAPER accounting boundary. Phase 8 explicitly forbids
introducing or relying on margin inference to unlock compatibility.

Until an approved authoritative PAPER required-margin boundary exists, the
following must remain true:

```text
READY_FOR_CASH_PLUS_CFD = NO
SettlementCompatibilityPolicy(CASH, CFD) = REJECTED
```

## Scope Exclusions

- Do not change `SettlementCompatibilityPolicy`.
- Do not add a margin formula, leverage inference, or used-margin substitution.
- Do not weaken `CONTEXT_UNAVAILABLE` behavior.
- Do not enable broker safety or perform provider mutations.
- Do not create a migration or a new risk ledger.

## Next Required Decision

Define and approve a mode-correct authoritative proposed-order margin contract
for PAPER CFD, and separately verify the LIVE provider preview semantics against
that contract. This is the smallest prerequisite for a future Phase 8 attempt.
