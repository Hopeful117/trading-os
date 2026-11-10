# Provider-Scoped Historical Evidence Retirement Runbook

This runbook is a reviewed procedure for a separately authorized DBA operation.
It is not authorization to execute retirement.

## Preconditions

1. Confirm the exact scope: provider, provider symbol, market ID and depth.
2. Confirm a non-revoked, unexpired approval evidence row exists for the exact
   scope and approved actor.
3. Confirm the Market Data operation is `PREPARED` with a passing preflight.
4. Confirm the Market Intelligence acknowledgement is `CONFIRMED` with the
   same operation ID and dependency fingerprint.
5. Confirm the external backup reference and isolated restore validation.
6. Confirm reader and writer quiescence for the exact scope.
7. Confirm no active holds and no mixed-provider sequences.

Stop if any reference is missing, stale, contradictory or unverifiable.

## Physical Operation Safeguards

1. Re-read the operation and inventory fingerprints immediately before the
   physical operation.
2. Re-assert the exact provider, provider symbol, market ID and depth in the
   database session.
3. Use only the explicitly approved relation and `TRUNCATE TABLE ONLY`; never
   use `CASCADE`, wildcard table lists or an inferred relation.
4. Record the database transaction ID, operator, timestamp and external
   change reference.
5. Do not execute the operation if the preflight fingerprint changed.

The physical command is intentionally omitted from application code and must
be supplied by the approved DBA procedure for the deployed schema.

## Post-Operation Validation

1. Confirm the operation is marked `PHYSICAL_RETIRED` only after the database
   operation has a durable external reference.
2. Confirm the exact retired scope has no remaining raw evidence rows.
3. Confirm unrelated provider scopes still contain their expected rows.
4. Confirm replay returns `SOURCE_RETIRED` for the retired provider scope and
   remains available or unchanged for unrelated scopes.
5. Confirm capture rejects the retired scope and accepts unrelated live data.
6. Confirm artifacts, dependencies and audit rows remain queryable.

## Ambiguous Outcome

If the database connection, transaction or post-operation validation is
ambiguous, transition the operation to `AMBIGUOUS` or
`RECONCILIATION_REQUIRED`. Do not retry the physical operation until the
database state and backup are independently reconciled.

## Rollback

Rollback is possible only through the externally validated backup/restore
procedure. No application endpoint restores or recreates retired raw evidence.
