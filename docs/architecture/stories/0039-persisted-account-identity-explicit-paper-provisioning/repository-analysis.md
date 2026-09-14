# Repository Analysis - Story 0039

## Story

0039 - Persisted Account Identity and Explicit PAPER Provisioning

## Repository State

| Field | Value |
|---|---|
| Branch | `story/0038-persisted-paper-execution-regression` |
| HEAD | `ab78229` |
| Working tree | Dirty; unrelated pre-existing Broker Service, IDE, and report changes preserved |
| Target module | `trading-core` |
| Baseline | 517 Trading Core tests passed before Story 0039 implementation |

## Governing ADRs

| ADR | Impact | Alignment |
|---|---|---|
| ADR-042 | Execution mode remains owned by `BrokerAccount`; PAPER financial state remains Trading Core owned | Aligned |
| ADR-043 | `Account.accountId` and `BrokerAccount.id` are independent identities; canonical relation is explicit | Aligned |

## Current Gaps Found

- `Account` and `BrokerAccount` were persisted independently without a canonical relation.
- PAPER provisioning created legacy `Rules` but did not require an explicit versioned profile assignment.
- PAPER settlement resolved accounts through owner/provider inference.
- `UNIQUE(user_id, broker)` prevented multiple same-provider accounts for one user.
- `AccountRiskConfiguration.brokerAccountId` was not structurally tied to the canonical account relation.

## Implementation Boundary

Included:

- Trading Core entity, repository, persistence, provisioning, settlement, migration, and tests.
- Canonical nullable FK with uniqueness for conservative legacy rollout.
- Exact profile ID/version validation and atomic PAPER provisioning.

Excluded:

- Broker Service, Angular, mode-aware Risk Facts, T0/T1 correction, and Story B.
- Default profile creation, profile administration, and automatic repair of ambiguous legacy rows.

## Risks and Decisions

- Historical relations cannot safely be guessed; V12 backfills only provable matches and leaves ambiguous rows unresolved.
- Legacy `Rules` remain for runtime compatibility but do not substitute for explicit profile assignment.
- The implementation uses identifier references and database constraints rather than mandatory JPA cascades.

## Analysis Result

The Story can be implemented safely in `trading-core` under ADR-042 and ADR-043. An implementation plan is useful because the change crosses schema, persistence, provisioning, and execution resolution boundaries.
