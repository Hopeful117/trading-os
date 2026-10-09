# Story 0078 - Implementation Plan

## Design

Keep Challenge inside Trading Core and separate it from both Account financial
state and BrokerAccount technical connectivity.

- `ChallengeDefinition` is an immutable JPA domain entity keyed by a stable UUID
  and a definition version.
- `ChallengeInstance` is a lifecycle entity with a dedicated UUID, Account
  reference, exact definition/Risk Policy references, starting-capital snapshot,
  terminal metadata and optimistic locking.
- `BALANCE` is the only progression source stored in this Story.
- Risk Policy parameters remain in existing Risk persistence.
- The database uses a composite definition key and a nullable active marker to
  enforce one ACTIVE instance per Account while allowing multiple terminal rows.
- A transactional start service validates ownership, immutable references and
  Account starting state but does not mutate Account or evaluate Risk.
- No public API is added; Story 0079 owns the runtime read model and progression
  surface. The application service remains directly testable and available for a
  later controller.

## Persistence

Add `V23__challenge_definition_and_instance.sql` with:

- `challenge_definition` keyed by `(id, definition_version)`;
- `challenge_instance` keyed by `id`;
- foreign keys to Account, ChallengeDefinition and Risk Policy where compatible;
- immutable definition columns;
- optimistic version on ChallengeInstance;
- status and active-marker checks;
- unique `(account_id, active_marker)` for the single ACTIVE invariant.

## Validation

Add tests for:

- definition and lifecycle invariants;
- exact version/reference preservation;
- terminal transition behavior;
- application start validation and non-mutation;
- JPA persistence, version coexistence and active uniqueness.
