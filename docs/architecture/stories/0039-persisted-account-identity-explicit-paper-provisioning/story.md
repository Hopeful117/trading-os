# Story 0039 - Persisted Account Identity and Explicit PAPER Provisioning

## Metadata

**ID:** `0039`

**Title:** Persisted Account Identity and Explicit PAPER Provisioning

**Status:** Implemented - Ready for Review

---

## Goal

Establish an unambiguous, durable relationship between the financial `Account`
and its execution/routing `BrokerAccount`, then make new PAPER provisioning a
deterministic and fail-closed operation.

A successful PAPER provisioning operation must produce this coherent graph:

```text
BrokerAccount
    + financial Account
    + explicit canonical Account -> BrokerAccount relation
    + explicit AccountRiskConfiguration
    + explicit assignment to an existing valid versioned RiskProfile
```

The resulting account is structurally valid for later risk evaluation without
introducing mode-aware Risk Facts in this Story.

---

## Context

ADR-043 defines two independent identities:

```text
Account.accountId
    = financial identity

BrokerAccount.id
    = execution/routing identity
```

The canonical mapping is an explicit durable `Account -> BrokerAccount`
relation. UUID value equality or inequality has no semantic meaning.

ADR-042 preserves the execution-mode boundary: `BrokerAccount` owns the
execution mode, LIVE authority remains external-provider based, and PAPER
financial state remains Trading Core owned.

The current repository has independent `Account` and `BrokerAccount` tables but
no direct durable relation. PAPER provisioning creates a financial account and
legacy `Rules`, but does not create the canonical relation,
`AccountRiskConfiguration`, or an explicit versioned profile assignment.

The current `UNIQUE(user_id, broker)` constraint also prevents multiple
accounts for the same user and provider and therefore cannot remain the account
identity mechanism.

Relevant repository evidence includes:

- `trading-core/src/main/java/com/hope/trading/trading_core/model/Account.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/BrokerAccount.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistence.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/infrastructure/BrokerSynchronizationServiceImpl.java`
- `trading-core/src/main/resources/db/migration/common/V7__accounts_unique_broker_per_user.sql`

---

## Problem

Runtime code can currently resolve a PAPER financial account using owner and
provider metadata rather than a canonical relationship. Risk configuration
contains a broker-account identifier but is not itself an independent identity
authority. This makes account resolution ambiguous as soon as a user has more
than one account for the same provider.

PAPER provisioning also creates legacy risk rules without requiring an explicit
current risk profile. The resulting account may exist without the persisted
configuration required by the current risk path.

Historical data may not contain enough evidence to reconstruct every relation.
Ambiguous legacy mappings must remain visible and unresolved rather than being
guessed.

---

## Scope

- Add an explicit canonical reference from `Account` to `BrokerAccount`.
- Enforce first-milestone cardinality: one `Account` has exactly one canonical `BrokerAccount`, and one `BrokerAccount` has at most one canonical `Account`.
- Enforce the ownership invariant that linked `Account.userId` and `BrokerAccount.ownerId` are equal.
- Treat `BrokerAccount.provider` as provider authority; retain `Account.broker` only as legacy financial metadata and prevent contradictory new links.
- Keep execution mode authoritative on `BrokerAccount`; do not duplicate it onto `Account`.
- Replace, relax, or remove the current `(user_id, broker)` uniqueness assumption so multiple same-provider accounts are supported.
- Update Trading Core provisioning and account-resolution paths to use the canonical relation where it is available.
- Keep LIVE synchronization consistent while allowing provable LIVE relations to be established; do not change external-provider authority.
- Require a reference to an already existing, structurally valid, explicitly versioned `RiskProfile` for new PAPER provisioning.
- Persist `AccountRiskConfiguration` and its explicit profile assignment for successful PAPER provisioning.
- Ensure any retained `AccountRiskConfiguration.brokerAccountId` matches the canonical relation and cannot drift from it.
- Make all required local PAPER provisioning writes one atomic operation.
- Add the schema migration required to introduce the canonical foreign key and uniqueness constraint.
- Backfill only provable historical mappings, using the simplest repository-supported diagnostic or unresolved representation for ambiguous rows.
- Preserve unresolved legacy rows for manual repair; do not silently delete, reassign, or reinterpret them.

---

## Out of Scope

- Mode-aware or neutral Risk Facts.
- A PAPER Risk Facts adapter.
- LIVE/PAPER Risk Facts routing.
- T0 risk logic changes.
- T1 identity correction or `ExecutionTimeRiskRevalidationService` changes.
- Required-margin semantics, position queries, PAPER exits, close semantics, or realized PnL.
- A universal Position aggregate.
- Fees, slippage, pending orders, or simulation enhancements.
- Broker Service redesign, Kraken API changes, cTrader, or FTMO integration.
- Angular or other frontend changes.
- Risk-profile authoring, administration UI, or default risk-policy bootstrap.
- Automatic repair of ambiguous historical data.
- Promotion of legacy `Rules` into a versioned `RiskProfile`.
- Story B implementation: Mode-Aware Risk Facts and T0/T1.

---

## Acceptance Criteria

- [ ] Given a financial `Account` and its `BrokerAccount`, when the account is provisioned, then an explicit persisted canonical reference links the `Account` to that `BrokerAccount`.
- [ ] Given any two account identifiers, then `Account.accountId` and `BrokerAccount.id` are treated as independent domain identities; neither UUID equality nor UUID inequality establishes a relationship.
- [ ] Given a `BrokerAccount` already linked canonically, when another `Account` attempts to use it, then persistence or domain validation rejects the second canonical link.
- [ ] Given an `Account` and `BrokerAccount` owned by different users, when a canonical link is attempted, then the operation is rejected.
- [ ] Given provider metadata on an `Account`, when it contradicts the canonical `BrokerAccount.provider`, then the link or provisioning operation is rejected or the state is reported invalid according to the repository error conventions.
- [ ] Given one user and one provider, when multiple distinct accounts are provisioned, then all valid accounts can coexist without `(user_id, broker)` uniqueness being used as their identity.
- [ ] Given a PAPER provisioning request without an explicit profile reference, when provisioning runs, then it fails closed.
- [ ] Given an unknown exact profile/version, malformed reference, structurally invalid profile, incomplete required normalized rule set, or unsupported existing rule vocabulary/version, when PAPER provisioning runs, then it fails closed.
- [ ] Given an explicit valid versioned `RiskProfile` reference, when PAPER provisioning succeeds, then the required `AccountRiskConfiguration` and profile assignment are persisted for the financial `Account`.
- [ ] Given successful PAPER provisioning, then the canonical account relation, initial financial state, risk configuration, and profile assignment are present after database reload.
- [ ] Given a provisioning failure at any required local step, then no partial operational PAPER account graph is committed.
- [ ] Given retained `AccountRiskConfiguration.brokerAccountId` data, then it matches the canonical `Account` relation and cannot be independently changed to another `BrokerAccount`.
- [ ] Given a legacy mapping that is provable from persisted ownership, provider, and configuration evidence, when backfill runs, then the canonical relation is populated.
- [ ] Given an ambiguous or unprovable legacy mapping, when backfill runs, then no relation is guessed and the row remains visible for manual repair.
- [ ] Given legacy `Rules` without the current explicit configuration and profile assignment, then the account is not treated as risk-eligible solely because those rules exist.
- [ ] Given existing LIVE account behavior, when this Story is deployed, then external provider authority and existing Broker Service interactions remain unchanged.
- [ ] Given an `Account` after persistence-context reload, then canonical lookup resolves the same `BrokerAccount` without owner/provider identity inference.
- [ ] Given existing authorization flows, when account relations are created or resolved, then ownership and authentication guarantees remain preserved.

---

## Constraints

- Respect ADR-042 and ADR-043 as architectural authority.
- Preserve the boundary between financial account state and execution/provider state.
- Prefer an explicit identifier reference and database constraints over mandatory JPA object association or cascade semantics; the exact Java representation remains an implementation decision.
- Keep the canonical relation independent of risk configuration.
- Do not make the relationship depend on UUID equality, owner/provider lookup, display name, external provider ID, legacy broker metadata, or the existence of a risk configuration row.
- Do not make execution mode a second source of truth on `Account`.
- Do not create or infer a default `RiskProfile`.
- Keep provisioning deterministic and fail-closed.
- Do not make historical `broker_account_id` non-null until unresolved legacy data has been safely handled.
- Keep provider-specific behavior inside existing Broker Service infrastructure boundaries.
- Do not modify Broker Service, Angular, or tests as part of Story authoring.
- Do not introduce unrelated dependencies or change unrelated execution semantics.
- Do not commit, push, merge, or create an implementation branch automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-042.md` - Position Authority and Close Semantics by Execution Mode
- `docs/architecture/adr/ADR-043.md` - Account Identity and Mode-Specific Risk Facts

---

## Relevant Stories

- `docs/architecture/stories/0037-paper-account-web-wiring/story.md` - existing PAPER account creation and mode wiring
- `docs/architecture/stories/0038-persisted-paper-execution-regression/story.md` - persisted PAPER execution evidence and identity fixture limitation

---

## Relevant Modules

- `trading-core`

Broker Service and Angular are explicitly excluded from implementation scope.

---

## Test Intent

Tests are implementation work and must not be added during Story authoring. The
implementation must eventually cover:

- new PAPER provisioning with independently generated Account and BrokerAccount IDs;
- canonical relation persistence and reload;
- database one-to-one enforcement;
- cross-user relation rejection;
- provider mismatch rejection;
- multiple same-provider accounts;
- missing explicit profile reference, unknown exact profile/version, malformed reference, structurally invalid profile, incomplete required normalized rule set, and unsupported existing rule vocabulary/version rejection;
- successful explicit profile assignment;
- legacy `Rules` being insufficient for risk eligibility;
- AccountRiskConfiguration relation consistency;
- rollback after failures at each required provisioning step;
- provable and ambiguous legacy backfill behavior;
- LIVE synchronization regression coverage.

Story B tests for mode-aware Risk Facts and T0/T1 correction are excluded.

---

## Story B Dependency Boundary

This Story prepares the identity graph required by the future Story
`Mode-Aware Risk Facts and T0/T1`:

```text
TradePlan.tradingAccountId
    -> financial Account
    -> canonical BrokerAccount relation
    -> provider and execution mode
```

Story B may consume this relation but must not redesign account identity,
cardinality, ownership, or profile assignment established here.

---

## LEARN / PAIR / DELEGATE

### LEARN

- `Account` versus `BrokerAccount` identity semantics.
- Identifier references versus JPA entity associations.
- Foreign-key, unique-constraint, and nullability reasoning.
- Safe schema migration and conservative legacy backfill.
- Transaction boundaries and rollback semantics.
- `AccountRiskConfiguration` versus current mode-aware Risk Facts.

The human engineer should implement or heavily participate in these areas.

### PAIR

- Canonical relation integration.
- PAPER provisioning orchestration.
- Migration and backfill policy.
- Ownership and provider invariants.
- Explicit `RiskProfile` assignment integration.
- Transaction design.

These areas require collaborative design and implementation.

### DELEGATE

After architecture and learning objectives are validated:

- Mechanical repository methods.
- Repetitive mapping and persistence plumbing.
- Boilerplate DTO adjustments.
- Repetitive fixtures.
- Routine regression wiring.
- Mechanical SQL syntax after migration design is understood.

The complete migration and the identity design must not be delegated without
human review.

---

## Validation

The eventual implementation should validate:

- focused Trading Core account-provisioning and risk-configuration tests;
- complete Trading Core Maven test suite;
- migration execution against a clean schema;
- migration/backfill behavior against representative legacy data, including ambiguous records;
- `git diff --check`.

No Broker Service, Angular, or frontend validation is required for this Story.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved if required by repository analysis.
- [x] Implementation completed within the Story scope.
- [x] Relevant Trading Core and migration validation executed.
- [ ] Diff reviewed in IntelliJ by the human engineer.
- [ ] Code Review approved.
- [x] Engineering Report completed.
- [ ] Human commit created.
