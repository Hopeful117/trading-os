# Story 0087 - Implementation Plan

## Plan Status

Repository Analysis was explicitly approved by the human engineer. This plan
is now awaiting its own approval. No implementation is authorized until this
plan is approved.

## Design Decisions

### Progression source

Extend the existing `ProgressionValueSource` with `EQUITY`.

- `BALANCE` keeps its current semantics and remains the historical default.
- `EQUITY` reads `Account.equity` only when the Account is a CASH account with
  `AccountValuationStatus.FRESH` and a valid valuation timestamp.
- No fallback from EQUITY to realized balance or asset balance is allowed.
- Missing or stale authority leaves the Challenge ACTIVE and the projection
  reports the current value as unavailable.
- Blocking Challenge Risk violations remain evaluated before progression.

The same source-selection helper will be reused by `ChallengeEvaluator` and
`ChallengeProgressService` so lifecycle evaluation and read projection cannot
silently diverge.

### Required settlement model

Add nullable `requiredSettlementModel` to `ChallengeDefinition` using the
existing `SettlementModel` enum. Historical rows remain nullable. New domain
creation calls will require an explicit value, and all current fixtures and
definition creation sites will be updated with their existing intended
semantics.

`StartChallengeService` will reject a non-null requirement when it differs from
`Account.settlementModel`, before `ChallengeInstance` persistence. It will not
mutate Account state. The exact immutable definition version remains the source
of the requirement; no duplicate Challenge-specific enum is introduced.

### Risk-day configuration ownership

Reuse `account_risk_configuration` as the existing effective Risk runtime
configuration. It already owns `riskTimeZone` and `riskDayResetTime`, and all
Risk evaluation paths already read it through `RiskPersistence`.

This Story will not add duplicate timezone/reset fields to ChallengeDefinition
or introduce provider-based selection. Challenge monitoring will continue to
resolve the exact Challenge Risk Policy/version while using the Account's
effective Risk-day configuration. Challenge start will require the existing
Risk configuration to be present; it will not silently mutate configuration
shared with unrelated workflows.

The provisioning path will stop treating a static starting capital as the
daily rollover authority. Its initial baseline remains a compatibility seed for
the newly created first Risk Day, while all later days are created on first
authoritative Risk evaluation. The existing generic configuration API remains
the extension point for future configured timezone/reset values.

### Daily baseline lifecycle

`AccountRiskMonitoringService` will:

1. Resolve the configured `RiskDay`.
2. Load complete authoritative Account facts.
3. Call `RiskPersistence.baseline` with the observed authoritative balance,
   not `Account.startingBalance`.
4. Reuse the stored first baseline when the row already exists.
5. Include capture source, Account version, observed timestamp, Risk Day and
   valuation provenance in the baseline payload.
6. Continue passing `Account.startingBalance` separately to the Risk context
   for static total drawdown.

Trade-plan and execution-time paths will retain their existing stronger start
reconstruction for broker contexts. They will continue to consume the stored
immutable baseline and will not reset total drawdown.

`RiskPersistence.baseline` will be hardened for duplicate first-writer attempts
using the existing unique constraint and transaction behavior. A duplicate
insert will resolve to the committed first row where the database transaction
allows it; tests will cover the repository's actual behavior rather than
inventing a second baseline mechanism.

## Domain Changes

### Challenge

- Add `ProgressionValueSource.EQUITY`.
- Add nullable persisted `requiredSettlementModel` to
  `ChallengeDefinition`.
- Preserve immutable definition behavior and exact Risk Policy identity.
- Keep `ChallengeInstance` tied to the immutable definition version; no
  Account mutation or Challenge-specific settlement enum.

### Risk

- Change only baseline input selection in the application orchestration.
- Do not modify `DailyDrawdownRule`, `MaximumTotalDrawdownRule`, or Risk
  formulas.
- Preserve `RiskDay`, `DailyRiskBaseline`, policy version snapshots, and audit
  provenance.

## Migration Plan

Add the next common Flyway migration after V27:

- add nullable `challenge_definition.required_settlement_model`;
- replace the historical progression source check so both `BALANCE` and
  `EQUITY` are accepted;
- preserve existing ChallengeDefinition rows as BALANCE;
- do not rewrite historical Risk baseline rows.

The migration will be validated against the repository's H2 test profile and
PostgreSQL migration path. If the unnamed historical check constraint cannot be
removed portably, the implementation must stop rather than modify V23 or use a
database-specific provider workaround without approval.

## Implementation Sequence

1. Update domain enum, ChallengeDefinition persistence, factory call sites and
   definition tests.
2. Add the additive Challenge migration and migration coverage.
3. Add shared Challenge progression source resolution with explicit authority
   validation.
4. Update evaluator and progress projection for BALANCE/EQUITY semantics.
5. Add settlement compatibility validation to Challenge start.
6. Change account-monitoring baseline capture from static starting balance to
   authoritative observed balance and add provenance.
7. Verify provisioning and Risk configuration paths preserve configurable
   timezone/reset behavior without provider branches.
8. Add focused tests, then run affected module validation and review the full
   diff for provider leakage and duplicated Risk formulas.

## Tests to Add or Update

### Challenge progression

- BALANCE behavior remains unchanged.
- EQUITY below target remains ACTIVE.
- Fresh EQUITY reaches target and passes.
- Realized balance alone cannot pass EQUITY.
- EQUITY can pass while realized balance remains below target.
- Stale, unavailable, invalid, and non-CASH equity fail closed.
- Blocking drawdown breach wins over simultaneous target.
- Projection reports unavailable value without balance fallback.

### Settlement compatibility

- CASH requirement with CASH Account succeeds.
- CASH requirement with INVENTORY Account is rejected before save.
- Null historical requirement remains compatible.
- Existing Account and Challenge creation paths remain green.

### Daily baseline

- First account-monitoring evaluation captures observed authoritative balance.
- Repeated same-day evaluation returns the original immutable amount.
- Intraday balance/equity changes do not mutate the baseline.
- Next Risk Day captures a new balance.
- Starting balance remains the total-drawdown reference.
- Custom reset time and timezone boundaries remain deterministic.
- Duplicate baseline attempts preserve the first row and provenance.

### Regression

- Story 0079 Challenge monitoring and lifecycle tests.
- Story 0085 PAPER derivative settlement tests.
- Story 0086 valuation and freshness tests.
- PAPER INVENTORY and LIVE account behavior.
- Accounts without a Challenge.

## Validation Commands

```bash
mvn -q test                              # risk-domain
mvn -q verify                            # trading-core
git diff --check
```

Run focused tests separately for Challenge, Risk monitoring/baselines,
settlement and valuation. Review Flyway migration execution and report JaCoCo
and SonarQube results when configured.

## Documentation Reconciliation

Update the Story 0087 implementation report with:

- exact BALANCE versus EQUITY semantics;
- settlement compatibility and historical-null behavior;
- first-authoritative-evaluation baseline limitation;
- Account Risk configuration ownership;
- concurrency behavior;
- validation results and deferred Story 0088/0089 scope.

No new ADR is planned because this implementation reuses ADR-028, ADR-051,
ADR-052 and the existing Risk configuration/baseline architecture. A new ADR
will be required only if implementation reveals a genuinely new durable
ownership decision.
