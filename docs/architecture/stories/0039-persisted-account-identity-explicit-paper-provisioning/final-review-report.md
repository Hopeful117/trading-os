# Final Review Report - Story 0039

## Migration Audit

V12 path:

`trading-core/src/main/resources/db/migration/common/V12__persist_canonical_account_broker_relation.sql`

Execution order:

1. Adds nullable `accounts.broker_account_id UUID`.
2. Adds `fk_accounts_broker_account` from `accounts.broker_account_id` to `broker_account.id`.
3. Backfills only relations supported by `account_risk_configuration`, matching owner and provider evidence and requiring exactly one valid candidate for the Account.
4. Excludes a candidate already referenced by configuration for another Account.
5. Adds `accounts_broker_account_key UNIQUE (broker_account_id)`.
6. Drops `accounts_user_broker_key` if present.
7. Drops `accounts_broker_key` if present.

```text
V12_PATH = trading-core/src/main/resources/db/migration/common/V12__persist_canonical_account_broker_relation.sql
PRODUCTION_DATABASE = PostgreSQL
BACKFILL_SOURCE = account_risk_configuration.account_id/broker_account_id joined to broker_account.id, corroborated by accounts.user_id/broker_account.owner_id and accounts.broker/broker_account.provider
BACKFILL_PREDICATE = configuration account_id equals the current Account; referenced BrokerAccount exists; Account.user_id equals BrokerAccount.owner_id; Account.broker is non-null and equals BrokerAccount.provider; the total valid candidate count for the Account is exactly 1; the candidate is not referenced by configuration for another Account
OWNERSHIP_CORROBORATION = YES, explicit equality predicate
PROVIDER_CORROBORATION = YES, explicit equality predicate; contradictory provider metadata is not backfilled
UNIQUE_CANDIDATE_PROOF = YES, global candidate count equals 1 and cross-Account candidate reuse is excluded
ARBITRARY_SELECTION_POSSIBLE = NO
UUID_EQUALITY_USED_AS_IDENTITY = NO
AMBIGUOUS_MAPPING_BEHAVIOR = broker_account_id remains NULL when an Account has zero or multiple valid candidates
UNPROVABLE_MAPPING_BEHAVIOR = broker_account_id remains NULL; no failure or automatic repair
DUPLICATE_BROKER_MAPPING_BEHAVIOR = all conflicting historical candidates remain unresolved, so the later unique constraint does not select or silently accept one mapping
FK = accounts.broker_account_id -> broker_account.id
BROKER_ACCOUNT_UNIQUE = UNIQUE(accounts.broker_account_id), allowing at most one Account per BrokerAccount
MULTIPLE_NULL_RELATIONS_ALLOWED = YES under PostgreSQL single-column UNIQUE semantics; H2 tests also pass
USER_PROVIDER_UNIQUE = REMOVED
H2_POSTGRESQL_SEMANTIC_RISK = LOW for the reviewed constructs: UUID, correlated subqueries, UPDATE scalar subquery, ADD/DROP CONSTRAINT IF EXISTS, FK, and UNIQUE are supported by both; production PostgreSQL validation was not run against a live database
MIGRATION_REVIEW = PASS
```

## Documentation

```text
STORY_DOCUMENTATION_CORRECTED = YES
REMOVED_UNSUPPORTED_SEMANTICS = active, assignable, compatible, inactive, non-assignable, incompatible
ADR_042_CHANGED = NO
ADR_043_CHANGED = NO
NEW_ADR_REQUIRED = NO
```

The words removed were profile requirements, not the unrelated legacy `Rules.active` field or generic prose. The Story now describes existing, structurally valid, explicitly versioned profiles and repository-supported failure conditions.

## Implementation Verification

```text
CANONICAL_RELATION = PASS
PAPER_SETTLEMENT_CANONICAL_LOOKUP = PASS
PROFILE_REFERENCE = PASS
EXACT_PROFILE_VERSION = PASS
PROFILE_VALIDATOR = PASS
T0_T1_SEMANTICS_CHANGED = NO
PROFILE_ASSIGNMENT_ACCOUNT_IDENTITY = PASS
ACCOUNT_RISK_CONFIGURATION_CONSISTENCY = PASS
ATOMIC_PROVISIONING = PASS
LEGACY_RULES_CONTAINMENT = PASS
LIVE_BOUNDARY = PASS
STORY_B_SCOPE = PRESERVED
```

`Account.accountId` remains the financial identity, `BrokerAccount.id` remains the execution identity, and `Account.brokerAccountId` is the canonical relation. PAPER settlement uses `findByBrokerAccountId`; it no longer resolves by owner/provider. The assignment and configuration writes use `Account.accountId`, while the configuration retains the matching canonical BrokerAccount ID. No `REQUIRES_NEW` boundary was found; the required provisioning writes join the outer `BrokerAccountService @Transactional` transaction.

The shared validator is used by PAPER provisioning, T0 trade-plan evaluation, and T1 execution-time revalidation. It validates structural/version/rule-vocabulary requirements only and introduces no mode, provider, active, assignable, or compatibility policy.

## Rollback Evidence

```text
ROLLBACK_TEST_PROFILE_VALIDATION = YES (validation occurs before BrokerAccount persistence; test verifies no BrokerAccount save)
ROLLBACK_TEST_CONFIGURATION_WRITE = NO
ROLLBACK_TEST_ASSIGNMENT_WRITE = NO
ROLLBACK_TEST_OTHER = NO dedicated failure-stage rollback test
ROLLBACK_COVERAGE_ASSESSMENT = PARTIAL
```

The transaction boundary is implemented, but rollback after configuration or assignment failure is not independently exercised by tests.

## Diff Classification

```text
STORY_0039 = canonical Account relation, V12, explicit RiskProfile request/validation/persistence, PAPER provisioning/settlement changes, related Trading Core fixtures/tests, and Story 0039 documentation
PRE_EXISTING_UNRELATED = Broker Service refactoring, .idea/compiler.xml, delegate regression tests, and unrelated investigation/report files already present in the working tree
GENERATED_REPORT = existing root-level delegate/audit/implementation reports; preserved
UNEXPECTED = none identified
```

`PositionCloseLifecycleServiceTest` contains a large pre-existing unrelated change and was not modified for Story 0039. The complete diff was inspected without reverting any unrelated work.

## Validation

```text
FOCUSED_TESTS =
    tests: 44
    failures: 0
    errors: 0
    skipped: 0

RISK_PERSISTENCE_TESTS =
    tests: 4
    failures: 0
    errors: 0
    skipped: 0

FULL_TRADING_CORE_TESTS =
    tests: 520
    failures: 0
    errors: 0
    skipped: 0

GIT_DIFF_CHECK = PASS
```

The tests execute V12 on a clean H2 test schema. No live PostgreSQL migration run was performed. Non-blocking warnings include H2/Flyway version verification, explicit H2 dialect configuration, and Mockito dynamic-agent loading.

## Residual Non-Blocking Risks

- V12 should be dry-run or applied against representative production PostgreSQL data before deployment.
- No dedicated test currently seeds ambiguous, unprovable, provable, or duplicate historical mappings through a fresh migration.
- Rollback coverage after configuration/assignment failure remains partial.
- Human review of the complete diff and migration data assumptions remains required.

```text
STORY_0039 = READY_FOR_HUMAN_COMMIT
COMMIT_PERFORMED = NO
FINAL_REVIEW_RECOMMENDATION = READY_FOR_HUMAN_COMMIT
```
