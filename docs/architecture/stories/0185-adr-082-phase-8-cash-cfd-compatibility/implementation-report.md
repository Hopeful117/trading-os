# Implementation Report - Story 0185

## Result

**BLOCKED.** `CASH + CFD` remains rejected. No production source, settlement
policy, migration, ledger, or broker adapter was changed.

## Evidence

- `origin/main` was fetched and verified at
  `885fb3be7eb26e63404eea39889be4260dcd8d82`.
- Story 0184 Phase 7 is already merged.
- The dedicated PAPER CFD settlement service exists and is selected by the
  execution finalization boundary.
- T0 and T1 CFD projection boundaries exist and fail closed for missing facts.
- Required margin remains a hard risk prerequisite.
- The current PAPER margin implementation delegates to Broker Service provider
  margin preview; the Kraken implementation derives preview amount from
  quantity, price, and leverage, with leverage defaulting to one.
- This does not prove the authoritative, mode-correct PAPER CFD required-margin
  contract demanded by Phase 8.

## Decision

```text
READY_FOR_CASH_PLUS_CFD = NO
COMPATIBILITY_CHANGED = NO
NEW_ARCHITECTURAL_DECISION_REQUIRED = YES
```

The next decision is a dedicated authoritative PAPER CFD proposed-order margin
boundary. Until it exists and is tested, changing compatibility would violate
the Phase 8 hard gate.

## Safety

```text
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_PROVIDER_MUTATION = 0
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
```

## Validation

No production tests were run because the readiness blocker was identified by
static repository inspection before any production change. `git diff --check`
passed against the Story-only artifact diff. The focused existing
`SettlementCompatibilityPolicyTest` and `BrokerRequiredMarginClientTest` also
passed, confirming the current rejection policy and existing margin-client
behavior remain unchanged.
