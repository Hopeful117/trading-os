# Story 0089 - Repository Analysis

## Status

**STOPPED BEFORE IMPLEMENTATION**

Implementation is blocked by a boundary-semantics mismatch between the
official English Kraken rule wording and the current generic daily drawdown
operator. No benchmark configuration, migration, runtime code, or tests were
added.

## Repository State

- Branch: `main`
- HEAD: `838c9b670c9122071478bea551725de14d8342e5`
- Expected Story 0088 merge commit: verified
- Worktree before this artifact: clean
- `main` / `origin/main`: aligned (`0 0` ahead/behind)

The DevLog applicability check was attempted for Story 0089. It was
unavailable because the configured provider returned an exhausted-quota error.
Repository artifacts and current source were used instead.

## Canonical Context Inspected

The following repository artifacts were inspected:

- Stories 0077, 0078, 0079, 0084, 0085, 0086, and 0087.
- Story 0088 repository analysis and implementation artifacts.
- ADR-028, ADR-051, and ADR-052.
- `ChallengeDefinition`, `ChallengeInstance`, `StartChallengeService`,
  `ChallengeEvaluator`, and `ChallengeInstrumentCompatibilityService`.
- `RiskPersistence`, `AccountRiskMonitoringService`,
  `AccountRiskConfiguration`, `DailyDrawdownRule`, and
  `MaximumTotalDrawdownRule`.
- PAPER CASH account provisioning, settlement, and derivative valuation paths.
- Flyway migrations V14, V23, V28, and V29.

The generic model can represent the requested capital, currency, EQUITY
progression, CASH settlement, LINEAR_PERPETUAL requirement, immutable Risk
Policy identity, and UTC/00:30 account Risk configuration without a provider
runtime branch. PAPER account creation already accepts the Risk timezone and
reset time and persists the linked Risk profile.

The existing convention for immutable reference data is versioned Flyway seed
data. Risk profiles are identified by `(id, semantic_version)` and Challenge
definitions by `(id, definition_version)`. This convention would be used if
implementation resumes.

## Official English Sources

Retrieved at `2026-10-13T14:15:12+02:00`:

- https://www.kraken.com/pro/prop
- https://support.kraken.com/articles/kraken-prop-faq
- https://blog.kraken.com/product/prop/introducing-kraken-prop

The English Kraken Prop page confirms for the `$50,000` `Advanced` plan:

- profit target: `9%`;
- maximum daily loss: `3%`;
- maximum drawdown: `3%`;
- evaluation fee: `$180`;
- no evaluation time limit, consistency rule, or profit cap.

The English FAQ confirms:

- MDL recalculates daily at `00:30 UTC` based on account balance;
- MDD is measured from starting balance and does not reset;
- commission is `0.04%` per side;
- margin funding is `0.033%` per day, charged every four hours;
- an evaluation breaches when equity falls `to or below` the daily-loss or
  maximum-drawdown limit.

## Blocking Discrepancy

Official wording requires equality at the loss limit to breach:

```text
equity <= daily-loss limit -> breach
equity <= maximum-drawdown limit -> breach
```

Current generic code:

- `risk-domain/src/main/java/com/hope/trading/risk/rule/DailyDrawdownRule.java`
  calls `ThresholdRuleSupport.maximum(...)`.
- `ThresholdRuleSupport.maximum(...)` marks `current <= maximum` as `PASS`.
- `MaximumTotalDrawdownRule` correctly calls
  `ThresholdRuleSupport.maximumInclusiveBreach(...)`, which marks equality as
  a failure.

Therefore, at a 3% daily loss boundary, the current generic engine would not
produce the provider-correct breach result. This directly triggers Story 0089
stop condition 21 and cannot be fixed by benchmark configuration alone.

## Expected Benchmark Values If Resumed

```text
startingCapital              = 50000 USD
profitTargetRatio            = 0.09
progressionValueSource       = EQUITY
requiredSettlementModel      = CASH
requiredInstrumentProductType= LINEAR_PERPETUAL
DAILY_DRAWDOWN.maximumRatio  = 0.03
MAX_TOTAL_DRAWDOWN.maximumRatio = 0.03
riskTimeZone                 = UTC
riskDayResetTime             = 00:30
```

Derived values remain non-persisted:

```text
profit target equity          = 54500 USD
static total drawdown floor   = 48500 USD
initial daily allowance       = 1500 USD
```

Periodic funding, leverage/margin behavior, provider terminal actions, and
commission configuration remain unsupported or require separate generic
architecture work. No fee or funding implementation was attempted.

## Required Follow-up

Human architectural review is required before implementation. The smallest
follow-up is to establish generic daily drawdown equality semantics consistent
with the official benchmark, then update the affected generic Risk tests and
Story 0089 analysis before adding the benchmark catalog configuration.

No `story.md`, implementation plan, implementation report, code review, or
engineering report was created because the mandatory stop condition prevents
safe implementation.

## Follow-up Note

The generic `DAILY_DRAWDOWN` equality defect was corrected separately in the
targeted Risk Domain correctness fix. Story 0089 remains unimplemented and
must be re-analyzed independently before benchmark configuration work resumes.

## Resume Analysis

### Resume Timestamp

Resumed at `2026-10-13T14:41:51+02:00`.

Current repository state:

- Branch: `main`
- HEAD: `6a9715df19b9223df384672832681c733147e9f4`
- `main`: `6a9715df19b9223df384672832681c733147e9f4`
- `origin/main`: `6a9715df19b9223df384672832681c733147e9f4`
- Main/origin relationship: aligned (`0 0` ahead/behind)
- Worktree: only the existing untracked Story 0089 documentation directory

The Risk fix is present in the current branch as merged commit `6a9715df`,
which contains `7efb54ad` (`fix(risk): enforce inclusive daily drawdown breach`).

### Blocker Resolution Verification

`DailyDrawdownRule` now delegates to the existing generic
`ThresholdRuleSupport.maximumInclusiveBreach(...)`. Its semantics are:

```text
current < maximum -> PASS
current == maximum -> FAILURE for BLOCKING severity
current > maximum -> FAILURE for BLOCKING severity
```

The implementation contains no provider, Challenge, or plan-code branch.
`MaximumTotalDrawdownRule` continues to use the same inclusive operator.
`ThresholdRuleSupport.maximum(...)` remains unchanged for its other callers.

The existing Risk calculation path was verified with a 50,000 balance and
3% daily baseline:

```text
equity = 48,500.01 -> daily ratio 0.0299998 -> PASS
equity = 48,500.00 -> daily ratio 0.03      -> FAILURE
equity = 48,499.99 -> daily ratio 0.0300002 -> FAILURE
```

The same path retains inclusive total-drawdown behavior at the 50,000 starting
reference. Focused `risk-domain` tests passed:

```bash
mvn -q -Dtest=RiskEngineIntegrationTest,MetricAndProjectionTest test
```

The complete Risk Domain and affected Trading Core verification was previously
executed successfully for the prerequisite fix:

```bash
risk-domain: mvn -q test
risk-domain: mvn -q verify
trading-core: mvn -q -Dtest=AccountRiskMonitoringServiceTest,ChallengeEvaluatorTest,ChallengeReevaluationServiceTest,StartChallengeServiceTest,ChallengeInstrumentCompatibilityServiceTest test
trading-core: mvn -q verify
```

The original blocker is therefore **CLEARED**.

### Official Source Revalidation

The following English official pages were retrieved again at
`2026-10-13T14:41:51+02:00`:

- https://www.kraken.com/pro/prop
- https://support.kraken.com/articles/kraken-prop-faq

The current pages still establish the requested Advanced 50k benchmark:

```text
wallet size       = 50,000 USD
plan              = Advanced
evaluation target = 9% / 4,500 USD
maximum daily loss= 3% / 1,500 USD initially
maximum drawdown  = 3% / 1,500 USD
evaluation fee    = 180 USD
MDL reset         = 00:30 UTC, based on balance
MDD reference     = starting balance, non-resetting
evaluation limit  = none
```

The FAQ states that equity falling `to or below` the daily-loss or drawdown
limit breaches the evaluation. The existing generic Risk fix now matches both
boundaries. Existing `ChallengeEvaluator` compares the authoritative
progression value inclusively (`compareTo(target) >= 0`), matching the official
"reach the profit target" wording and Story 0087 semantics.

The FAQ currently states:

```text
commission      = 0.04% per side
margin funding  = 0.033% per day, charged every 4 hours on open positions
```

The repository has no generic configurable fee-rate catalog or periodic
funding model. These remain benchmark fidelity limitations and are not blockers
for the Challenge/Risk configuration. The purchase fee will not affect the
50,000 trading capital.

### Resume Conclusion

The original `DAILY_DRAWDOWN` stop condition is resolved. The generic domain
can represent the benchmark without provider-specific runtime behavior. The
next workflow stage is an Implementation Plan covering deterministic Flyway
reference-data seeds, stable identities, idempotency/conflict behavior, and
focused benchmark acceptance tests.

Implementation has not started. This resumed Repository Analysis requires
human approval before the Implementation Plan may be produced.
