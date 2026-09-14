# Implementation Report - Story 0039

## Result

Implemented in the working tree and ready for human review. No commit or push was performed.

## Production Changes

- `Account` now stores nullable canonical `brokerAccountId`.
- `AccountRepository` exposes canonical broker-account lookup.
- `CreateBrokerAccountRequest` accepts an optional exact `RiskProfileReference`.
- `RiskProfileValidator` validates the existing profile, version, required categories, and rule structure.
- `RiskPersistence` supports exact profile lookup, account risk configuration, and explicit assignment persistence.
- `BrokerAccountService` performs explicit PAPER profile validation and atomic graph provisioning.
- `PaperSettlementService` resolves the financial account through the canonical relation.
- V12 adds the canonical FK/unique constraint, conservative backfill, and removes the legacy user/provider uniqueness assumption.
- T0/T1 risk services now reuse the shared structural validator; Story B identity semantics remain unchanged.

## Test Changes

- Updated provisioning and execution fixtures to use independent Account and BrokerAccount UUIDs.
- Added focused coverage for explicit profile validation, ownership, persistence, and PAPER execution.
- Added a Spring-context integration test proving canonical relation, configuration, and exact profile assignment after reload.

## Validation Evidence

| Command | Result |
|---|---|
| `./mvnw -DskipTests compile` | Passed |
| Focused Story 0039 tests | 44 passed, 0 failures |
| `./mvnw -Dtest=RiskPersistenceTest test` | 4 passed, 0 failures |
| `./mvnw test` | 520 passed, 0 failures |
| `git diff --check` | Passed |

Known non-blocking test warnings concern H2/Flyway version verification, explicit H2 dialect configuration, Mockito self-attaching, and existing deprecated/unchecked test APIs.

## Scope Review

No Broker Service or Angular production change was introduced by Story 0039. Existing unrelated worktree changes were preserved.
