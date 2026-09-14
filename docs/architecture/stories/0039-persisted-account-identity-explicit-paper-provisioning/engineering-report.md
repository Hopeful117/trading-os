# Engineering Report - Story 0039

## Executive Summary

Story 0039 establishes durable financial-to-execution account identity and makes new PAPER provisioning fail closed unless an exact existing versioned risk profile is supplied. The complete Trading Core suite passes after the change.

## Delivered Behavior

The persisted PAPER graph is now:

```text
BrokerAccount
    -> Account via canonical broker_account_id
    -> AccountRiskConfiguration
    -> exact RiskProfile assignment
```

Provisioning writes are transactionally coordinated. Missing, unknown, malformed, or structurally invalid profile references fail before a usable PAPER graph is created. Existing LIVE provider authority and legacy Rules compatibility are preserved.

## Migration Safety

V12 keeps the new relation nullable during rollout. It backfills only rows supported by persisted evidence, adds the canonical FK and uniqueness constraint, and leaves ambiguous historical mappings unresolved instead of guessing.

## Acceptance Evidence

- Canonical relation and reload: covered by `RiskPersistenceTest` and PAPER persistence integration tests.
- Independent identities: covered by PAPER execution fixtures using distinct UUIDs.
- Exact profile assignment: covered by persistence and provisioning integration tests.
- Ownership and missing/invalid profile rejection: covered by `BrokerAccountServiceOwnershipTest`.
- Existing full-suite compatibility: 520 Trading Core tests passed.

## Remaining Review Items

- Human review of the migration/backfill SQL against representative production data.
- Human verification of the full diff and acceptance checklist.
- Human commit and integration approval.

## Status

`STORY_0039_IMPLEMENTED_READY_FOR_REVIEW`
