# Story 0089 - Implementation Plan

## Plan Status

`IMPLEMENTED - AWAITING HUMAN REVIEW`

This plan follows the resumed and human-approved Repository Analysis. It
configures one immutable benchmark through existing generic Risk and Challenge
domains. It does not add provider-specific runtime behavior.

## Overview

Add one additive Trading Core Flyway reference-data migration containing:

1. a stable, immutable Risk Profile version for the Kraken Prop Advanced 50k
   benchmark; and
2. a stable, immutable ChallengeDefinition version referencing that Risk
   Profile.

The existing generic application paths will provision a PAPER CASH USD account,
apply `UTC` / `00:30` Risk configuration, start the Challenge, evaluate EQUITY
progression, enforce LINEAR_PERPETUAL compatibility, and preserve generic Risk
breach ordering.

No public API, domain engine, settlement engine, valuation engine, or execution
branch will be added.

## Planned Changes

### 1. Establish Stable Reference Identities

Use deterministic UUIDs and semantic versions in the migration, following the
existing `(id, semantic_version)` and `(id, definition_version)` conventions.

Proposed identities:

```text
Risk Profile ID:        0a10c7e2-9d1e-4f5a-b6c8-123456789089
Risk Profile version:   1.0.0
Policy ID:              KRAKEN_PROP_ADVANCED_50K_RISK
Policy version:         1.0.0

Challenge Definition ID: 00890000-0000-4000-8000-000000000050
Definition version:     1.0.0
```

The migration must use plain immutable inserts so an existing identity/version
collision fails rather than silently accepting changed semantics. Flyway's
version tracking makes repeated application idempotent.

### 2. Seed the Benchmark Risk Profile

Create the versioned profile with `PLATFORM` authority and provenance containing
the official English source URLs, retrieval timestamp, effective timestamp, and
the fact that this is a validated repository benchmark.

The profile will contain the required benchmark constraints:

```text
DAILY_DRAWDOWN       maximumRatio = 0.03
MAX_TOTAL_DRAWDOWN   maximumRatio = 0.03
```

The existing `RiskProfileValidator` requires the generic PAPER baseline rules
`MAX_POSITION_RISK`, `MAX_EXPOSURE`, and `DAILY_DRAWDOWN` for account
provisioning. Therefore the profile will also include the existing platform
baseline values for `MAX_POSITION_RISK` and `MAX_EXPOSURE`, without changing
their rule implementations or representing them as Kraken-specific rules.
`MAX_TOTAL_DRAWDOWN` remains optional in the generic validator but is included
for this benchmark.

No Risk formula, rule class, policy resolver, or persisted policy value outside
the benchmark seed will be changed.

### 3. Seed the Immutable ChallengeDefinition

Create the definition using the existing generic fields:

```text
provider                    = KRAKEN_PROP
product                     = EVALUATION
planCode                    = ADVANCED
capitalCurrency             = USD
startingCapital             = 50000
profitTargetRatio           = 0.09
progressionValueSource      = EQUITY
requiredSettlementModel     = CASH
requiredInstrumentProductType = LINEAR_PERPETUAL
riskPolicyId                = benchmark Risk Profile ID
riskPolicyVersion           = 1.0.0
```

Persist the current official source URL set, retrieval timestamp, effective
timestamp, and provenance. Do not persist target equity, drawdown floors,
purchase fee, leverage, funding rate, or derived daily allowances.

### 4. Additive Migration and Reproducibility

Create the next common Trading Core migration, expected as:

```text
trading-core/src/main/resources/db/migration/common/V30__seed_kraken_prop_advanced_50k_benchmark.sql
```

The migration will insert Risk Profile metadata/rules before the Challenge
Definition because the latter has foreign keys to the exact profile identity.
It will use the same SQL style as `V14__seed_paper_standard_risk_profile.sql`.

No startup HTTP call, dynamic provider synchronization, or application runner
will be introduced.

### 5. Add Focused Persistence and Acceptance Coverage

Add a Trading Core integration test for the seeded catalog that verifies:

- exact profile and definition IDs/versions;
- exact benchmark primitive values and provenance;
- required `CASH` settlement and `LINEAR_PERPETUAL` product type;
- exact 3% daily and total drawdown rules;
- repeated test context/migration initialization does not duplicate rows;
- a PAPER USD 50,000 CASH account can be provisioned with `UTC` / `00:30`;
- generic `StartChallengeService` creates an ACTIVE instance with exact
  definition and Risk Policy references.

Extend or add focused Challenge tests for:

- `54,499.99` EQUITY remaining ACTIVE;
- `54,500.00` EQUITY passing;
- stale/unavailable equity remaining ACTIVE;
- blocking Risk breach winning over target;
- active `LINEAR_PERPETUAL` compatibility;
- active `SPOT` rejection.

Use existing `ChallengeEvaluator`, `ChallengeInstrumentCompatibilityService`,
and Risk monitoring/evaluation test fixtures. Do not create a Kraken-specific
test service or endpoint.

Add daily Risk boundary and rollover coverage using the existing account
monitoring path:

```text
baseline 50,000: equity 48,500.01 / 48,500.00 / 48,499.99
baseline 51,000: floor 49,470.00 and equality breach
MDD reference remains 50,000 after profit
```

The prerequisite Risk tests already prove the normalized ratio boundary; the
benchmark integration tests will prove that the seeded policy selects the same
generic rules.

### 6. Document Fidelity Boundaries

Update Story 0089 implementation artifacts with a fidelity matrix documenting:

- supported capital, target, EQUITY progression, MDL, MDD, reset, settlement,
  and product constraints;
- the `$180` purchase fee as provenance-only metadata, not trading capital;
- commission `0.04%` per side as unsupported unless an existing generic fee
  mechanism is found during implementation;
- periodic funding `0.033%` per day charged every four hours as unsupported;
- leverage/margin as unsupported;
- provider terminal close/disable behavior as unsupported.

No new ADR is planned.

## Files to Modify

- `docs/architecture/stories/0089-kraken-prop-advanced-50k-benchmark/story.md`
  - update status and implementation outcome during implementation;
- `docs/architecture/stories/0089-kraken-prop-advanced-50k-benchmark/repository-analysis.md`
  - preserve the original STOP diagnosis and record resume verification;
- `trading-core/src/test/java/com/hope/trading/trading_core/challenge/...`
  - add or extend focused seeded benchmark, start, progression, and product
    compatibility tests;
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/...`
  - add only the benchmark-specific persistence/monitoring coverage needed to
    prove the seeded profile and Risk-day configuration.

## Files to Create

- `trading-core/src/main/resources/db/migration/common/V30__seed_kraken_prop_advanced_50k_benchmark.sql`
  - immutable Risk Profile and ChallengeDefinition reference data;
- `trading-core/src/test/java/com/hope/trading/trading_core/challenge/benchmark/KrakenPropAdvanced50kBenchmarkTest.java`
  - focused integration/acceptance coverage, subject to final package
    conventions discovered during implementation;
- `docs/architecture/stories/0089-kraken-prop-advanced-50k-benchmark/implementation-report.md`
  - implementation and documentation reconciliation result;
- `docs/architecture/stories/0089-kraken-prop-advanced-50k-benchmark/code-review.md`
  - independent review result;
- `docs/architecture/stories/0089-kraken-prop-advanced-50k-benchmark/engineering-report.md`
  - final engineering record.

The exact test path may be adjusted to the existing package convention without
changing scope.

## Dependencies

- Trading Core Flyway migrations V14, V20, V23, V28, and V29.
- Existing Risk Profile persistence and validation.
- Existing ChallengeDefinition persistence and `StartChallengeService`.
- Existing Account/PAPER broker-account provisioning with settlement and Risk
  day configuration.
- Existing Challenge evaluator and instrument compatibility service.
- Existing Risk account monitoring and PAPER CASH valuation authority.

No external library or runtime service dependency is required. Kraken's website
is provenance only and must not be queried during application startup or tests.

## API and Architecture

No public or internal API changes are planned.

Responsibilities remain unchanged:

- Risk Domain evaluates generic configured rules.
- Trading Core owns Challenge definitions, instances, and orchestration.
- Account Risk configuration owns timezone/reset values.
- Market Data owns product identity.
- Settlement remains the final financial compatibility guard.
- ChallengeEvaluator remains provider-neutral and does not execute trades.

## Test Plan

### Focused Tests

- Risk Domain inclusive MDL and MDD boundary tests.
- Risk Profile persistence and exact rule-value tests.
- ChallengeDefinition persistence and immutable identity tests.
- Generic Challenge start with seeded profile/definition.
- Account configuration assertion for `UTC` / `00:30`.
- EQUITY target below/equal boundary tests.
- Stale equity fail-closed test.
- Breach-before-PASS test.
- LINEAR_PERPETUAL acceptance and SPOT rejection tests.
- Daily baseline rollover and static MDD tests.
- Existing accounts without Challenges and existing INVENTORY/SPOT behavior.

### Validation Commands

```bash
mvn -q -Dtest=KrakenPropAdvanced50kBenchmarkTest,ChallengePersistenceTest,ChallengeEvaluatorTest,ChallengeInstrumentCompatibilityServiceTest,AccountRiskMonitoringServiceTest,RiskPersistenceTest test
mvn -q verify
git diff --check
```

Run `mvn -q verify` independently in `risk-domain` and `trading-core`.
Run any additional module verification only if implementation changes that
module. Report warnings separately from failures.

## Risks

- **Seed identity collision:** a reused immutable ID/version could hide changed
  semantics. Mitigation: deterministic plain inserts and exact identity tests;
  duplicate/conflicting rows fail loudly.
- **Required generic baseline rules:** the account provisioning validator
  requires position/exposure rules in addition to benchmark drawdown rules.
  Mitigation: reuse existing platform baseline values and document them as
  generic safety constraints, without adding provider behavior.
- **Fee interpretation:** no generic configurable commission/funding catalog
  is currently present. Mitigation: do not add fee architecture; report
  commission as a fidelity gap and funding as explicitly unsupported.
- **Product semantics:** official pages describe Prop crypto contracts while
  the repository's provider-neutral representation is
  `LINEAR_PERPETUAL`. Mitigation: use the already approved Story 0088 generic
  product identity and document the representation; do not infer product type
  from provider metadata at runtime.
- **Documentation drift:** source pages may change after retrieval. Mitigation:
  record exact URLs/timestamp/provenance and create a new definition version for
  future rule changes.

## Validation Checklist

- [ ] Original STOP diagnosis remains intact.
- [ ] Resume analysis records merged Risk fix and source revalidation.
- [ ] V30 inserts the exact versioned profile before the definition.
- [ ] Risk profile contains daily and total drawdown at `0.03`.
- [ ] ChallengeDefinition stores only primitive benchmark configuration.
- [ ] No derived floor/target or purchase fee reduces starting capital.
- [ ] Account configuration uses `UTC` / `00:30` through existing path.
- [ ] Challenge starts with USD 50,000 CASH account.
- [ ] EQUITY target uses generic `>=` semantics.
- [ ] MDL and MDD equality boundaries breach.
- [ ] Daily baseline rolls from 50,000 to 51,000.
- [ ] MDD remains static after profit.
- [ ] LINEAR_PERPETUAL is accepted and SPOT rejected.
- [ ] Breach wins over simultaneous target.
- [ ] Accounts without this Challenge are unaffected.
- [ ] No provider-specific runtime branch exists.
- [ ] Funding, leverage/margin, fees, and terminal actions are documented with
      actual fidelity status.
- [ ] Affected Maven verification and `git diff --check` pass.

## Recommendation

Ready for implementation.

This is a technical recommendation only and does not authorize implementation.

## Implementation State

The plan was approved and implemented in the working tree. Human review and
integration approval remain required.
