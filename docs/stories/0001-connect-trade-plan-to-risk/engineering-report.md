# Engineering Report

## Story

Story 0001 — Connect Trade Plan to Risk.

## Final Status

Complete.

The exact accepted Market Intelligence Trade Plan version can now be evaluated through an immutable ADR-028 Risk Evaluation Context assembled by Trading Core. The resulting official evaluation is persisted, traceable, idempotent, and acknowledged durably by Market Intelligence when approved.

The corrective Code Review is approved. No commit, push, merge, reset, or discard operation was performed.

## Delivered Architecture

```text
Exact accepted Trade Plan version
    -> Trading Core authenticated/idempotent command
    -> account-scoped authorized Broker risk snapshot
    -> coherent broker balances, positions, stops, ledger and closed trades
    -> timestamped Market Data prices and account-currency conversions
    -> immutable versioned ADR-028 context snapshots
    -> deterministic Risk Domain evaluation
    -> persisted official result and acknowledgment outbox
    -> durable idempotent Market Intelligence lifecycle acknowledgment
```

Service ownership remains aligned with the accepted ADRs:

- Trading Core owns financial-context assembly and the official evaluation record.
- Broker Service owns account-scoped broker-neutral account and position facts.
- Market Data owns timestamped price and conversion facts.
- Market Intelligence owns the exact accepted Trade Plan and its lifecycle.
- Risk Domain remains deterministic, stateless, and free of repositories or remote calls.

No Execution Intent, broker order submission, frontend behavior, or Gateway route was added.

## Resolved Technical Blockers

### Authoritative daily-start balance

- The risk day is the half-open interval `[00:00, next 00:00)` in an explicitly configured account timezone.
- Broker balance and ledger acquisition is coherently bracketed and reconciled.
- Trading Core reconstructs the opening asset balance from authoritative running ledger balances and values it at the risk-day boundary.
- The resulting baseline is immutable and versioned.

### Daily closed PnL

- Closed trades are selected by closure time within the exact risk-day interval.
- Each realized PnL and fee is converted at its closure timestamp.
- The legacy Trading Core query was corrected from `openedAt` to half-open `closedAt` boundaries.

### Account-scoped Broker snapshots

- Broker risk snapshots are explicitly keyed by broker account.
- Broker Service authorizes the requested account against the authenticated owner before provider or credential access.
- Cross-account access is rejected.

### Stable broker-neutral position identity

- Broker Service persists UUID position mappings keyed by broker account, provider, and provider position reference.
- Provider references are retained as provenance rather than exposed as domain identity.

### Authoritative loss at stop

- Broker Service correlates open positions with active protective stops and protected quantities.
- Trading Core calculates remaining loss from the conservative current price, direction, stop, quantity, and recorded conversion.
- Missing, partial, ambiguous, or non-protective stops fail closed.

### Account-currency valuation and FX

- Market Data persists immutable valuation snapshots.
- Same-currency, direct-bid, and inverse-ask conversion are supported.
- Historical valuation prevents look-ahead and enforces configured staleness.
- Trade Plan account and sizing currencies must match the configured reporting currency.

### Snapshot versioning

- Broker, valuation, assembled component, context, baseline, rule-set, and evaluation artifacts use persistent database-issued identities or versions.
- Source payloads and provenance are preserved for reproduction and audit.

### Versioned effective risk profiles

- Trading Core persists semantic risk profiles, rules, assignments, versions, and provenance.
- Incomplete legacy rules are not promoted or backfilled.
- Accounts without an explicit complete assignment fail closed.

### Maximum-exposure provenance

- `MAX_EXPOSURE` comes only from the selected versioned risk profile.
- Policy version, rule version, assignment, and provenance are retained in the context and result trace.

## Corrective Review Resolution

The initial Code Review requested five corrections. All were implemented and approved:

- CR-001: transactional outbox, after-commit delivery, durable retry, and idempotent acknowledgment;
- CR-002: Broker account ownership authorization;
- CR-003: coherent Broker capture and independent ledger reconciliation;
- CR-004: strict account/reporting-currency agreement;
- CR-005: durable atomic Market Intelligence Trade Plan and acknowledgment persistence.

The original review remains available as historical evidence, with the approved corrective review recorded separately.

## Persistence and Migrations

Added or extended persistence includes:

- Broker position identity and risk snapshot versions;
- Market Data price observations and valuation snapshots;
- Trading Core risk configuration, profiles, assignments, baselines, component/context snapshots, official evaluations, and acknowledgment outbox;
- Market Intelligence immutable Trade Plan versions and risk-validation acknowledgments;
- PostgreSQL-backed Market Intelligence Compose wiring.

Migrations are schema-only. They deliberately seed no balances, identities, profiles, assignments, maximum exposure, timestamps, or financial values.

## Validation Summary

Complete suites:

| Module | Tests | Failures | Errors |
| --- | ---: | ---: | ---: |
| Risk Domain | 21 | 0 | 0 |
| Broker Service | 61 | 0 | 0 |
| Market Data | 30 | 0 | 0 |
| Trading Core | 66 | 0 | 0 |
| Market Intelligence | 117 | 0 | 0 |
| **Total** | **295** | **0** | **0** |

Additional validation:

- `git diff --check` passed;
- `docker compose config --quiet` passed;
- branch remained `main`;
- all implementation and Story artifacts remain uncommitted for engineer control.

Focused tests cover account isolation, coherent acquisition, ledger and fee reconciliation, stop correlation, no-look-ahead valuation, FX and staleness provenance, DST risk-day boundaries, `closedAt` PnL, profile provenance, currency mismatch, context idempotency, transaction rollback, outbox retry, duplicate delivery, restart durability, and concurrent acknowledgment conflicts.

## Accepted Limitations and Deployment Work

The Story is complete, with the following operational validations remaining before deployment:

- rehearse all new and modified migrations against disposable PostgreSQL 16 instances and schema copies of deployed databases;
- verify the PostgreSQL-specific immutable-artifact triggers in Trading Core;
- validate Kraken balance, ledger, trade-history, position, and stop semantics against a sandbox account;
- provision explicit account risk configuration and complete versioned risk-profile assignments;
- preserve fail-closed behavior for existing accounts until that configuration exists.

`TradingContextRepository` remains process-local. Persisting Trading Context is a separate architectural scope; it does not change the completed Story's deterministic risk-context ownership, but it affects recovery of new Trade Plan snapshot reads after Market Intelligence restart.

## Artifact History

- `story.md` — authoritative Story;
- `repository-analysis.md` — repository and architecture analysis;
- `implementation-plan.md` — approved initial plan;
- `implementation-report.md` — blocked attempt, resumed implementation, and corrective implementation record;
- `code-review.md` — initial Changes Requested review;
- `code-review-corrective.md` — approved corrective review;
- `engineering-report.md` — final consolidated report.

## Final Recommendation

Story 0001 is technically complete and approved through the Engineering Story workflow. The worktree should remain uncommitted until the engineer has completed any desired final IDE inspection and chooses the repository's normal commit and delivery process.
