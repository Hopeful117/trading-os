# Story 0095 - Implementation Plan

## Plan Status

`PROPOSED - AWAITING HUMAN APPROVAL`

The Repository Analysis was approved. This plan is limited to repairing the
PostgreSQL first-page cursor typing failure while preserving Story 0090
monitoring behavior.

## Design

### 1. Split first-page and continuation discovery

Replace the nullable-cursor `ChallengeInstanceRepository.findNextByStatus`
query with two repository methods:

- `findFirstPageByStatus(status, pageable)`
- `findNextPageByStatus(status, afterStartedAt, afterId, pageable)`

The first query will contain no nullable cursor parameters. The continuation
query will contain only typed comparisons against `startedAt` and `id`:

```text
startedAt > afterStartedAt
OR (startedAt = afterStartedAt AND id > afterId)
```

Both queries will retain ACTIVE status selection, ascending `startedAt`/ID
ordering, and bounded `Pageable` limits.

### 2. Preserve monitoring orchestration

Update `ActiveChallengeMonitoringService` to call the first-page method when
the cursor is unset and the continuation method thereafter. Preserve:

- bounded keyset pagination;
- strict cursor advancement from the last row;
- deterministic same-timestamp ID ordering;
- PAPER eligibility checks;
- identifier-based reevaluation;
- per-Challenge failure isolation;
- cycle statistics and structured logs.

No scheduler, transaction, lock, Risk, valuation, settlement, or Challenge
state-transition logic will change.

### 3. PostgreSQL regression coverage

Add a focused PostgreSQL-backed test context using the existing Testcontainers
dependency and migration conventions. Cover repository behavior for:

- empty ACTIVE state;
- first-page query execution;
- cursor-present continuation;
- strict exclusion of the cursor row;
- same `startedAt` ordered by ID;
- multiple pages without duplication or omission;
- terminal-state exclusion.

The test must execute the actual Spring Data JPA repository against PostgreSQL,
not only validate an equivalent JDBC query.

### 4. Monitoring integration coverage

Add PostgreSQL-backed coverage for the actual
`ActiveChallengeMonitoringService`. Verify that it:

- evaluates eligible PAPER Challenges;
- processes more than one page;
- excludes LIVE candidates from reevaluation;
- excludes terminal Challenges through ACTIVE discovery;
- continues after a per-Challenge failure;
- completes the first page without SQLState `42P18`.

Retain the existing H2 unit and concurrency tests as regression coverage for
the unchanged application semantics.

### 5. Runtime acceptance

After focused and module validation, rebuild and restart a fresh PostgreSQL
environment using the supported service configuration. Through public Gateway
APIs:

1. create the required PAPER account and Challenge state;
2. allow a real scheduler cycle to run;
3. confirm Trading Core logs contain a completed monitoring cycle without
   `42P18`;
4. retrieve Challenge progress through Gateway;
5. confirm monitoring fields are populated according to existing domain
   semantics.

Do not continue the broader PAPER E2E into TradePlan or execution.

## Expected Files

Production:

- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeInstanceRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ActiveChallengeMonitoringService.java`

Tests:

- existing or new PostgreSQL-backed Challenge repository test under
  `trading-core/src/test/java/.../challenge/infrastructure/`;
- existing or new PostgreSQL-backed monitoring integration test under the same
  infrastructure package;
- focused updates to existing H2 tests only if repository method names or
  stubbing contracts require them.

Documentation:

- Story 0095 implementation report after validation;
- Story 0095 engineering report after review and human acceptance.

No Flyway migration or Risk Domain production change is planned.

## Validation Commands

Run focused tests first from `trading-core`:

```bash
mvn -q -Dtest=ChallengePersistenceTest,ActiveChallengeMonitoringServiceTest,ChallengeMonitoringConcurrencyIntegrationTest test
```

Run PostgreSQL-backed Story 0095 tests using the repository's supported
Testcontainers execution. If the test class requires a separate profile or
invocation, document the exact command and result.

Then run:

```bash
mvn -q verify
```

in `trading-core`, followed by the relevant Risk Domain validation if the
dependency graph or test results show an affected boundary. Also run:

```bash
git diff --check
```

Review the complete diff and verify that no unrelated modules, migrations, or
generated artifacts were modified.

## Explicit Exclusions

- no PostgreSQL-specific casts in the domain or provider layers;
- no offset pagination or unbounded `findAll()` replacement;
- no schema/index migration without new evidence;
- no changes to Risk, valuation, settlement, Challenge progression, or
  execution semantics;
- no scheduler architecture change;
- no broader E2E continuation;
- no commit, push, merge, or branch integration.

## Approval Required

Human approval is required before implementation begins.
