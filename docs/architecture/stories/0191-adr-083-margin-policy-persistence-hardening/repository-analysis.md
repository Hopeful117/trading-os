# Repository Analysis - Story 0191

## Baseline

```text
REMOTE_MAIN: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_BASE: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_HEAD: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
CURRENT_BRANCH: story/0191-adr-083-margin-policy-persistence-hardening
WORKTREE_STATE: pre-existing untracked Story 0185, 0187, and 0189 directories preserved
NEXT_AVAILABLE_STORY: 0191
STORY_0190_PRESENT: YES
PREVIOUS_LATEST_MIGRATION: V49
```

DevLog applicability was checked for `trading-os`. The request completed with
truncated, low-confidence context that did not establish substantive ADR or
MarginPolicy evidence. Current repository artifacts and source are therefore the
authoritative basis for this analysis. Obsidian was not consulted because the
repository ADRs, Stories, migrations, and tests contain the relevant project
context.

## Current MarginPolicy Persistence Model

### Domain

`MarginPolicy` is a provider-neutral Java record keyed by `(UUID id, String
version)`. It stores instrument scope, exact `BigDecimal marginRate`, source
category/reference, `effectiveAt`, optional `expiresAt`, `configuredAt`,
reporting-currency scale, and `revoked`. Construction enforces positive rate,
`rate <= 1`, maximum eight fractional digits, nonblank provenance, required
timestamps, valid expiry ordering, reporting scale `0..18`, and rejection of
tiered/dynamic/hedged/portfolio-offset semantics.

### JPA and application persistence

`MarginPolicyPersistence` maps `margin_policy` to an `@Immutable` Hibernate
entity with composite `(id, version)` identity. `save` rejects an existing key;
the entity is then persisted. `version` reloads one exact policy version.

Assignments are separate rows keyed by generated `id`, with `accountId`,
`policyId`, `policyVersion`, `assignedAt`, `effectiveAt`, text provenance, and a
JPA optimistic `version`. `assign` verifies the policy key, locks the latest
assignment for the account, optionally checks an expected assignment version,
and inserts a new assignment. `assigned` joins assignments to policies by exact
policy identity/version, filters instrument and effective time, then uses
`setMaxResults(1)` ordered by effective time and id.

The canonical financial `Account` owns `brokerAccountId` through the existing
V12 relation. Margin policy assignment uses `accounts.account_id`; the broker
account is only checked by the PAPER resolver for execution routing and owner
binding. There is no `BrokerAccount`-owned policy table.

### Migration V49

V49 creates `margin_policy` with `NUMERIC(38,18)` `margin_rate`, composite
primary key `(id, version)`, required text/timestamp fields, and checks only for
`0 < margin_rate <= 1` and reporting scale `0..18`. It creates
`margin_policy_assignment` with an account foreign key, composite policy foreign
key, required assignment fields, optimistic version, an account/effective-time
lookup index, and uniqueness only on `(account_id, effective_at)`.

V49 is merged and must remain unchanged. The current latest migration is V49;
schema changes therefore require the next actual migration, expected to be V50
after current-state verification.

## ADR-083 Persistence Invariant Matrix

| Invariant | Java enforced | Database enforced | Durable test evidence | Gap |
|---|---|---|---|---|
| Stable policy identity | Composite record/JPA key | Composite primary key | None for policy reload | PostgreSQL lifecycle proof missing |
| Explicit version | Nonblank record field; composite key | `NOT NULL`, FK component | None | No direct durable version test |
| Provenance | Nonblank policy source and assignment argument | `NOT NULL` only; arbitrary text | Calculator/unit evidence only | Category/relationship durability not proven |
| `0 < marginRate <= 1` | Constructor | `CHECK` in V49 | No PostgreSQL invalid-write test | DB boundary evidence missing |
| Maximum 8 rate decimals | Constructor | Missing; `NUMERIC(38,18)` may round | Domain test only | Nine-decimal database write must be rejected |
| Effective/expiry ordering | Constructor | Missing | No persistence test | Invalid direct writes can enter |
| Required timestamps | Constructor | `NOT NULL` columns | None | Reload/timezone precision evidence missing |
| Policy economics immutable | Record and Hibernate `@Immutable` | No update/delete defense | None | Direct SQL/future persistence path can mutate/delete |
| Assignment referential integrity | `assign` checks policy | Composite policy and account FKs | No PostgreSQL assignment test | Lifecycle and violation evidence missing |
| Assignment uniqueness | Unique `(account_id,effective_at)` | Exact-time uniqueness only | No ambiguity test | Overlapping distinct effective times remain selectable-first |
| Account authority | Resolver loads financial `Account` and validates broker owner binding | `account_id` FK | No PostgreSQL resolver lifecycle test | Durable authority reconstruction unproven |
| Scope | Policy `instrument` and resolver equality | Required text only | Unit resolver coverage | Scope persistence/reload not proven |
| Fail-closed resolution | Resolver rejects invalid/missing/stale data | Partial constraints | Unit tests only | Ambiguity and restart path missing |

## Immutability Model

ADR-083 defines policy identity/version and economics as immutable. Assignment may
move to a new policy/version through an explicit new assignment; it must not
rewrite the meaning of an old version. Historical versions remain available for
T0/T1 evidence. The current repository expresses this intent in the domain
record, duplicate-key rejection, and Hibernate annotation, but not at the
PostgreSQL boundary.

The implementation must preserve this distinction:

```text
policy economics/version = append-only immutable row
account assignment       = explicit versioned append-only history
current authority        = deterministic applicable assignment
```

The current `setMaxResults(1)` resolver is a material risk if database state can
contain overlapping applicable assignments. The follow-up must reject or prevent
ambiguous authority; it must not rely on undefined or merely incidental ordering.

## Database Constraint Analysis

Database defense in depth is required for authoritative financial economics where
direct SQL, future JPA paths, migrations, or concurrent writes could corrupt
state. The smallest likely boundary is a forward migration that adds:

- exact rate precision rejection rather than relying on `NUMERIC(38,18)`;
- effective/expiry and required-field checks not already covered;
- immutable policy mutation protection consistent with existing repository
  append-only trigger conventions;
- assignment constraints sufficient to prevent indistinguishable overlapping
  authority, while preserving intentional historical reassignment;
- existing foreign-key integrity and explicit schema assertions.

The exact SQL mechanism requires implementation-plan approval after confirming
PostgreSQL behavior for numeric coercion and the repository's trigger
conventions. No historical migration should be edited.

## PostgreSQL Durability Evidence

The repository already uses PostgreSQL 16 Testcontainers and Flyway migration
tests. `PostgresFreshInstallMigrationTest` proves the current history reaches V49
and also exercises historical upgrades, but contains no MarginPolicy schema or
assignment assertions. Existing PostgreSQL integration tests provide patterns for
container setup and direct JDBC verification.

Required evidence is a focused integration boundary that:

1. Migrates a fresh PostgreSQL schema and verifies the new policy constraints.
2. Persists a valid policy and financial-account assignment.
3. Commits, clears/reconstructs the persistence context, and reloads through the
   real policy resolver.
4. Verifies identity, version, provenance, instrument scope, timestamps, and
   exact rate.
5. Calculates the explicit EUR/USD `0.01` fixture through reloaded PAPER
   authority and expects `11.00 USD`.
6. Creates a second version without mutating the first, then proves explicit
   reassignment is deterministic.
7. Attempts invalid direct PostgreSQL writes for rate and immutable-policy cases.
8. Exercises fresh and historical migration paths through the new migration.

The migration suite contains a known V34 legacy-provider failure path. That case
must be classified during validation and must not be repaired by editing V34 or
any other historical migration.

## Scope and Regression Boundaries

Story 0190 is present on `main` and its T1 semantics must remain unchanged:
valuation freshness is explicit and PAPER margin uses the authoritative valuation
fact price. `SettlementCompatibilityPolicy` remains fail-closed for `CASH + CFD`,
`INVENTORY + CFD`, and `UNKNOWN + CFD`. Risk Domain and Broker Service are not
implementation targets. The pre-existing untracked directories are:

```text
docs/architecture/stories/0185-adr-082-phase-8-cash-cfd-compatibility/
docs/architecture/stories/0187-paper-margin-authority-adr/
docs/architecture/stories/0189-adr-082-phase-8-cash-cfd-compatibility-retry/
```

They must not be staged, rewritten, deleted, or committed by this Story.

## Repository Analysis Conclusion

The current implementation has the intended local PAPER authority shape, but the
durable proof is incomplete in exactly the areas identified by Story 0189:
PostgreSQL precision enforcement, policy immutability, assignment ambiguity,
provenance/timestamp/scope reconstruction, and real post-commit resolver evidence.
The scope is implementable without changing ADR-082 compatibility, T1 semantics,
Risk Domain, Broker Service, or provider authority.

**Human approval required:** approve this Repository Analysis before an
Implementation Plan is produced. No production code or migration has been
changed by this analysis.
