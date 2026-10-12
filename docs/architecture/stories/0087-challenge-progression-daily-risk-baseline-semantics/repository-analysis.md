# Story 0087 - Repository Analysis

## Baseline

- Repository: `/home/ludo/workspace/trading-os`
- Branch: `main`
- HEAD: `f7a612c9b69f6b9a31a00a224e6febc8b213f2d2`
- `main` matches `origin/main` at analysis time.
- Worktree: clean.
- DevLog: unavailable for this analysis because the Story Agent provider
  returned an insufficient-quota error. Repository evidence is authoritative.
- Obsidian vault: not consulted; the repository Story, ADRs, and current code
  provide the required project context.

## Canonical Context Reviewed

- Stories 0078, 0079, 0084, 0085, and 0086.
- ADR-028, ADR-031, ADR-051, and ADR-052.
- Existing Challenge, Risk, Account, settlement, valuation, and migration code.

## Current Challenge Model

`ChallengeDefinition` is immutable and versioned by `(id, definitionVersion)`.
It currently stores an exact Risk Policy reference and only supports
`ProgressionValueSource.BALANCE`. `V23__challenge_definition_and_instance.sql`
also enforces `progression_value_source = 'BALANCE'`.

`ChallengeInstance` snapshots the definition and Risk Policy references and the
starting capital. It does not currently snapshot settlement semantics. The
one-ACTIVE invariant is enforced by the unique `(account_id, active_marker)`
constraint.

`StartChallengeService` validates ownership, active-instance uniqueness, exact
Risk Policy existence, currency, and starting capital before persisting the
instance. It does not validate Account settlement semantics.

`ChallengeEvaluator` evaluates blocking daily/total Risk failures before
progression. BALANCE progression uses realized balance for CASH accounts and
the settlement-currency Account balance otherwise. The ordering already
ensures a simultaneous Risk breach wins over a profit target.

`ChallengeProgressService` currently exposes only the BALANCE-derived current
value and target. It must select the same authoritative source as
`ChallengeEvaluator` once EQUITY exists, and must fail closed rather than
substituting balance for unavailable equity.

## Post-0086 Financial Authority

ADR-052 and the current Account model establish these semantics:

```text
PAPER CASH realizedBalance = authoritative realized settlement balance
PAPER CASH equity = latest successfully computed realizedBalance
                       + aggregate OPEN-trade unrealized P&L
```

`Account.valuationStatus`, `valuedAt`, and `marketDataTimestamp` describe the
latest valuation. `PaperDerivativeAccountValuationService` only produces a
fresh result after validating all required market snapshots. Risk facts are
assembled only after a successful CASH valuation refresh. Stale or unavailable
valuation therefore cannot be treated as authoritative equity.

PAPER INVENTORY retains its existing balance/equity behavior. LIVE accounts
remain broker-authoritative. No Challenge code may infer settlement from
provider or execution mode.

## Current Risk-Day and Baseline Model

`RiskDay.containing(instant, zone, resetTime)` already provides deterministic,
half-open Risk Days with timezone and reset-time support, including DST
behavior. The reset configuration is stored in
`account_risk_configuration.risk_time_zone` and
`account_risk_configuration.risk_day_reset_time`.

`risk_day_baseline` already provides:

- one row per `(account_id, risk_day)`;
- persisted start/end instants;
- immutable first-writer behavior in the application path;
- a database uniqueness constraint;
- PostgreSQL update/delete protection.

However, the current `AccountRiskMonitoringService` calls
`RiskPersistence.baseline(...)` with `account.getStartingBalance()`. This makes
the daily baseline static across Risk Days and violates the requested rollover
semantics. The authoritative observed `snapshot.account().balance()` is
already available before baseline creation and is the correct V1 capture value.

`BrokerAccountService` also creates an initial baseline during PAPER account
provisioning using `initialCapital` and hardcodes `UTC`. This is valid only as
an initial first-day compatibility path and must not be used as the rollover
authority. The generic Risk configuration API already accepts a timezone and a
reset time, but account provisioning currently does not expose configurable
values.

The trade-plan and execution-time Risk paths use Risk Day configuration and
their existing broker start reconstruction. They must retain the static total
drawdown reference while consuming the corrected daily baseline lifecycle.

## Risk Authority Boundaries

`AccountRiskMonitoringService` assembles immutable snapshots and delegates all
drawdown calculations to the Risk Domain. `DailyDrawdownRule` reads the daily
metric from the Risk context, and `MaximumTotalDrawdownRule` reads the static
starting-balance metric. Story 0087 must change the input baseline, not copy or
move either formula into Challenge.

The exact Challenge Risk Policy ID/version is already resolved in account
monitoring and included in the Risk trace. This behavior must remain unchanged.

## Writers and Readers

### Daily Risk baseline

Production writers/readers are:

- `BrokerAccountService`: initial provisioning baseline writer.
- `AccountRiskMonitoringService`: account-monitoring baseline writer/reader.
- `TradePlanRiskEvaluationService`: pre-trade baseline writer/reader.
- `ExecutionTimeRiskRevalidationService`: execution-time baseline writer/reader.
- `RiskPersistence.baseline`: persistence authority and first-writer lookup.
- `DailyRiskBaseline` construction in Risk contexts: deterministic reader.

### Risk timezone and reset time

- `RiskPersistence.configuration`: writer.
- `BrokerAccountService`: current provisioning writer, currently `UTC` and
  default `00:00`.
- `RiskPersistence.configuration(accountId)`: reader.
- `AccountRiskMonitoringService`, `TradePlanRiskEvaluationService`, and
  `ExecutionTimeRiskRevalidationService`: readers used to construct `RiskDay`.

### Account equity and realized balance

- Account creation/migration: initial values.
- `PaperSettlementService`: realized settlement mutation and local equity
  recomputation after CASH settlement.
- `PaperDerivativeAccountValuationService`: authoritative fresh CASH equity
  refresh and valuation metadata.
- `AccountServiceImpl` and `TradingServiceImpl`: legacy mutation paths guarded
  by settlement/mode semantics.
- `ModeAwareRiskFactsProvider`: PAPER Risk snapshot reader and valuation gate.
- `ChallengeEvaluator` and `ChallengeProgressService`: Challenge progression
  readers.
- `AccountMapper`/`AccountDto`: API read projection.

### Progression value source

- `ChallengeDefinition`: persistent writer and domain reader.
- `ChallengeEvaluator`: lifecycle evaluation reader.
- `ChallengeProgressService`: read-projection reader.
- Challenge definition tests and PAPER settlement integration fixtures:
  existing BALANCE construction sites.

## Proposed Scope for Implementation Planning

The repository supports the Story without redesigning the Risk engine.
Implementation planning should cover:

1. Add `EQUITY` to `ProgressionValueSource` and add an additive migration that
   allows both BALANCE and EQUITY without reinterpreting historical definitions.
2. Add provider-neutral optional `requiredSettlementModel` to
   `ChallengeDefinition`, preserving nullable historical rows and requiring an
   explicit value for newly created definitions where the domain contract is
   used. Validate it in `StartChallengeService` without mutating Account.
3. Make `ChallengeEvaluator` select BALANCE or authoritative fresh EQUITY.
   For EQUITY, unavailable, stale, invalid, or non-authoritative valuation must
   return ACTIVE/non-completed behavior and never PASS. No realized-balance
   fallback is allowed.
4. Apply the same source semantics to `ChallengeProgressService`, exposing an
   unavailable value rather than silently reporting balance for EQUITY.
5. Change account-monitoring baseline creation to use the authoritative
   observed balance at the first evaluation of the Risk Day. Reuse an existing
   baseline unchanged. Preserve `Account.startingBalance` for total drawdown.
6. Preserve the existing stronger start reconstruction in trade-plan and
   execution-time paths unless tests show it conflicts with the PAPER
   authoritative-balance rule. Do not rewrite historical baselines.
7. Make risk-day configuration ownership explicit. The current effective
   configuration is account-scoped; the plan must decide whether Challenge
   start only validates/reuses it or whether the existing Risk Policy model must
   carry immutable timezone/reset semantics. Provider-based defaults are not
   acceptable.
8. Harden or explicitly test duplicate baseline creation under the existing
   unique constraint and transaction behavior. A duplicate attempt must return
   the stored first baseline rather than replace it.

## Migration and Compatibility Constraints

- Historical Challenge definitions currently require BALANCE at the database
  level. The migration must remove only that restrictive check and preserve
  existing rows as BALANCE.
- Historical `requiredSettlementModel` must remain nullable unless repository
  evidence establishes a deterministic value. A null historical requirement
  means no settlement compatibility check for that definition.
- Existing PAPER INVENTORY accounts and LIVE accounts must not be reinterpreted
  as CASH or EQUITY-authoritative.
- Existing Risk baselines must not be rewritten without deterministic
  historical evidence.

## Stop-Condition Assessment

No mandatory Story stop condition is currently proven:

- Story 0086 establishes authoritative equity and explicit freshness.
- Realized balance and equity are distinct persisted fields.
- Risk baseline persistence has the required uniqueness and immutability
  structure.
- Risk Day already supports configurable timezone/reset semantics.
- No provider-specific branch is required.

The ownership decision for making Challenge-specific Risk-day configuration
effective remains an implementation-plan decision. It must be resolved before
code changes and must not be hidden in provider provisioning.

## Baseline Validation

Executed before implementation:

```text
mvn -q -Dtest=ChallengeDefinitionTest,ChallengeInstanceTest,ChallengeEvaluatorTest,ChallengeReevaluationServiceTest,StartChallengeServiceTest,ChallengeProgressServiceTest,AccountRiskMonitoringServiceTest,RiskDayAndReconstructionTest,RiskPersistenceTest,PaperDerivativeAccountValuationServiceTest,PaperCashDerivativeSettlementTest test
mvn -q test                         # risk-domain
```

Both commands completed successfully. The Trading Core command emitted only
existing Spring/H2/Mockito warnings. `ChallengeProgressServiceTest` and
`AccountRiskMonitoringServiceTest` were included in the selector; absent test
classes were ignored by the configured Surefire behavior.

## Required Human Decision

Approve this Repository Analysis before an Implementation Plan is produced.
The plan must explicitly settle the ownership of Challenge-effective timezone
and reset-time configuration and define the unavailable EQUITY projection
behavior.
