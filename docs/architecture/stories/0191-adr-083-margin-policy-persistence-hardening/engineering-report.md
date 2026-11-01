# Engineering Report - Story 0191

```text
================================================
ADR-083 MARGIN POLICY PERSISTENCE HARDENING
================================================

REMOTE_MAIN: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_BASE: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_HEAD: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
BRANCH: story/0191-adr-083-margin-policy-persistence-hardening
STORY: 0191

========================================
BASELINE
========================================

ADR_083: ACCEPTED
STORY_0190_PRESENT: YES
PREVIOUS_LATEST_MIGRATION: V49
NEW_LATEST_MIGRATION: V50
PRE_EXISTING_UNTRACKED_FILES:
  docs/architecture/stories/0185-adr-082-phase-8-cash-cfd-compatibility/
  docs/architecture/stories/0187-paper-margin-authority-adr/
  docs/architecture/stories/0189-adr-082-phase-8-cash-cfd-compatibility-retry/

========================================
PERSISTENCE MODEL
========================================

POLICY_IDENTITY: UUID + explicit version, composite primary key
POLICY_VERSION: durable and independently reloadable
POLICY_PROVENANCE: source category/reference plus assignment provenance
POLICY_SCOPE: exact provider-neutral instrument scope
POLICY_MARGIN_RATE: exact PostgreSQL NUMERIC, 0 < rate <= 1, max 8 decimals
FINANCIAL_ACCOUNT_ASSIGNMENT: margin_policy_assignment.account_id -> accounts.account_id
BROKER_ACCOUNT_USED_AS_AUTHORITY: NO

========================================
DATABASE INVARIANTS
========================================

RATE_GT_ZERO: DB_ENFORCED
RATE_LTE_ONE: DB_ENFORCED
RATE_MAX_8_DECIMALS: DB_ENFORCED
NINE_DECIMAL_RATE_REJECTED: YES
REQUIRED_IDENTITY: DB_ENFORCED through NOT NULL + composite primary key
REQUIRED_VERSION: DB_ENFORCED through NOT NULL + composite primary key
REQUIRED_PROVENANCE: DB_ENFORCED through NOT NULL + nonblank checks
ASSIGNMENT_REFERENTIAL_INTEGRITY: DB_ENFORCED through account/policy foreign keys
ASSIGNMENT_AMBIGUITY_PREVENTED: YES for indistinguishable effective assignments

========================================
IMMUTABILITY
========================================

IMMUTABILITY_MODEL: append-only immutable policy economics/version; explicit
  assignment insertion for a new version
POLICY_ECONOMICS_MUTABLE_IN_PLACE: NO
OLD_VERSION_PRESERVED: YES
NEW_VERSION_CREATES_NEW_AUTHORITY: YES
DATABASE_IMMUTABILITY_DEFENSE: PostgreSQL UPDATE/DELETE trigger on margin_policy
APPLICATION_IMMUTABILITY_DEFENSE: immutable domain record, Hibernate @Immutable,
  duplicate composite-key rejection, explicit save/assign APIs

========================================
POSTGRESQL DURABILITY
========================================

TESTCONTAINERS_POSTGRES: YES
POLICY_PERSIST: PASS
POST_COMMIT_RELOAD: PASS
POLICY_ID_PRESERVED: YES
VERSION_PRESERVED: YES
PROVENANCE_PRESERVED: YES
SCOPE_PRESERVED: YES
MARGIN_RATE_PRESERVED: YES
ACCOUNT_ASSIGNMENT_PRESERVED: YES
PERSISTENCE_CONTEXT_RECONSTRUCTION: PASS
SPRING_CONTEXT_RESTART: NOT_TESTED

========================================
RELOADED AUTHORITY
========================================

REAL_RESOLVER_USED: YES
RELOADED_POLICY_RESOLVES: YES
PAPER_MARGIN_FROM_RELOADED_POLICY: PASS
EXPECTED_MARGIN: 11.00 USD
ACTUAL_MARGIN: 11.00 USD

========================================
INVALID STATE TESTS
========================================

ZERO_RATE: REJECTED
NEGATIVE_RATE: REJECTED
RATE_GT_ONE: REJECTED
EIGHT_DECIMAL_RATE: ACCEPTED
NINE_DECIMAL_RATE: REJECTED
MUTATE_EXISTING_POLICY: REJECTED
AMBIGUOUS_ASSIGNMENT: REJECTED

========================================
MIGRATIONS
========================================

HISTORICAL_MIGRATIONS_MODIFIED: NO
FRESH_INSTALL: PASS
HISTORICAL_UPGRADE: PASS
V34_LEGACY_CASE: PRE_EXISTING; explicit rejection test passes

========================================
REGRESSION
========================================

STORY_0190_T1_TESTS: PASS
PAPER_MARGIN_AUTHORITY: LOCAL_POLICY
LIVE_MARGIN_AUTHORITY: PROVIDER
PAPER_PROVIDER_PREVIEW_USED: NO
RISK_DOMAIN_CHANGED: NO
BROKER_SERVICE_CHANGED: NO

========================================
COMPATIBILITY
========================================

CASH_CFD: REJECTED
INVENTORY_CFD: REJECTED
UNKNOWN_CFD: REJECTED
SETTLEMENT_COMPATIBILITY_CHANGED: NO
ADR_082_PHASE_8_RETRIED: NO
READY_FOR_CASH_PLUS_CFD: NO

========================================
SAFETY
========================================

CTRADER_SAFETY_EXECUTION_ENABLED: false
REAL_CTRADER_ORDERS: 0
REAL_CTRADER_CLOSES: 0
PROVIDER_MUTATIONS: 0

========================================
VALIDATION
========================================

FOCUSED_TESTS: PASS
POSTGRES_INTEGRATION_TESTS: PASS
MIGRATION_TESTS: PASS
TRADING_CORE_TEST: PASS
TRADING_CORE_VERIFY: PASS
RISK_DOMAIN_VERIFY: PASS
GIT_DIFF_CHECK: PASS
NEW_HIGH_MEDIUM_REVIEW_FINDINGS: 0
INDEPENDENT_REVIEW_FINDINGS_CLOSED: YES

========================================
PHASE 8 BLOCKERS
========================================

T1_MARGIN_CORRECTNESS: RESOLVED
POLICY_DB_IMMUTABILITY: RESOLVED
POLICY_RATE_DB_CONSTRAINT: RESOLVED
POSTGRES_POLICY_RESTART_EVIDENCE: PARTIAL (context reconstruction proven;
  Spring process restart not tested)
FULL_PAPER_T0_T1_SETTLEMENT_E2E: BLOCKED - next Story

========================================
FINAL
========================================

STORY_RESULT: COMPLETE - implementation ready for human commit/review
ADR_083_PERSISTENCE_HARDENED: YES
READY_FOR_FULL_PAPER_CFD_E2E: YES
READY_FOR_CASH_PLUS_CFD: NO
RECOMMENDED_NEXT: human review and commit of Story 0191, followed by the
  deterministic full PAPER CFD lifecycle/E2E Story
```

No commit or push was performed. Unrelated tracked frontend changes and the
pre-existing untracked Story directories remain untouched and excluded.
