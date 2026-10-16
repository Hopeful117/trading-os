# Story 0107C-1 - Explicit Outcome Version Selection

## Goal

Provide a deterministic selection boundary for immutable objective outcome
versions before research dataset construction.

## Scope

- Define the complete immutable outcome selection identity.
- Add exact repository lookup for a selected outcome fingerprint.
- Preserve append-only corrected outcomes.
- Document that automatic latest/superseded selection is not yet defined.

## Out Of Scope

- Research datasets or manifests.
- Outcome recalculation or correction mutation.
- ML, Python, labels, strategy evaluation, Risk, Broker, Execution, or UI.

## Validation

- Domain identity tests.
- PostgreSQL/H2 persistence selection test.
- `cd market-intelligence && mvn -q test`.
- `cd market-intelligence && mvn -q verify`.
- `git diff --check`.
