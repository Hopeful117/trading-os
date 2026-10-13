# Story 0089 - Kraken Prop Advanced 50k Benchmark Configuration

## Metadata

- **ID:** `0089`
- **Status:** `IMPLEMENTED - AWAITING HUMAN REVIEW`
- **Related Stories:** `0077`, `0078`, `0079`, `0084`, `0085`, `0086`, `0087`, `0088`
- **Related ADRs:** `ADR-028`, `ADR-051`, `ADR-052`
- **Repository Analysis:** `repository-analysis.md`

## Goal

Configure and validate the first reproducible external benchmark using the
existing provider-neutral Challenge, Risk, settlement, valuation, and execution
compatibility domains:

```text
Kraken Prop / Evaluation / Advanced / 50,000 USD
```

The benchmark must be represented by immutable, versioned configuration and
must not introduce Kraken-specific runtime branches.

## Benchmark Configuration

Expected configuration, subject to the blocking boundary review:

```text
provider                  = KRAKEN_PROP
product                   = EVALUATION
planCode                  = ADVANCED
capitalCurrency           = USD
startingCapital           = 50000
profitTargetRatio         = 0.09
progressionValueSource    = EQUITY
requiredSettlementModel   = CASH
requiredInstrumentProductType = LINEAR_PERPETUAL
riskTimeZone              = UTC
riskDayResetTime          = 00:30
```

Required Risk Policy rules:

```text
DAILY_DRAWDOWN       maximumRatio = 0.03
MAX_TOTAL_DRAWDOWN   maximumRatio = 0.03
```

Derived values must remain runtime calculations:

```text
profit target equity        = 54,500 USD
static total drawdown floor = 48,500 USD
initial daily allowance     = 1,500 USD
```

## Official Sources

English official Kraken sources retrieved on 2026-10-13:

- https://www.kraken.com/pro/prop
- https://support.kraken.com/articles/kraken-prop-faq
- https://blog.kraken.com/product/prop/introducing-kraken-prop

The sources establish the Advanced 50k target and loss limits, the `$180`
evaluation fee, no evaluation deadline, no consistency rule, and the `00:30
UTC` daily reset. The FAQ also states `0.04%` commission per side and
`0.033%` daily margin funding charged every four hours.

## Scope

- Add immutable/versioned benchmark Risk Policy reference data.
- Add immutable/versioned Advanced 50k ChallengeDefinition reference data.
- Preserve exact source URL, retrieval time, effective time, and provenance.
- Make bootstrap/migration idempotent without mutating historical versions.
- Validate generic Challenge start for a USD 50,000 PAPER CASH account.
- Validate EQUITY progression at 54,500 USD.
- Validate CASH plus LINEAR_PERPETUAL compatibility.
- Reject SPOT under the active benchmark Challenge.
- Validate static MDD and daily baseline rollover semantics.
- Preserve Risk-breach-before-PASS ordering.
- Document unsupported funding, leverage/margin, and provider terminal actions.

## Architectural Constraints

- No provider-specific branch in Risk, Challenge, settlement, valuation, or
  execution engines.
- No Kraken-specific Risk rule or evaluator.
- No provider name or plan code may infer instrument identity or runtime rules.
- No purchase fee may reduce trading starting capital.
- No funding approximation as entry/exit commission.
- No leverage, margin, liquidation, scheduler, or terminal-action engine.
- Existing Accounts without Challenges and other ChallengeDefinitions remain
  unaffected.

## Acceptance Criteria

### Configuration

- The exact Advanced 50k ChallengeDefinition is persisted immutably.
- The exact 3%/3% Risk Policy is persisted immutably.
- Repeated bootstrap is idempotent.
- Existing immutable versions are never overwritten.
- Provenance is persisted with the official English source URLs.

### Challenge Start

- A compatible PAPER USD account with 50,000 starting capital and CASH
  settlement starts an ACTIVE Challenge through the generic service.
- Risk configuration is supplied through the generic `UTC` / `00:30` account
  configuration path.
- The Challenge references the exact immutable definition and Risk Policy
  versions.

### Progression and Risk

- Fresh equity below 54,500 keeps the Challenge ACTIVE.
- Fresh equity equal to 54,500 passes the Challenge.
- Stale or unavailable equity cannot pass.
- A blocking Risk breach wins over a simultaneous target.
- MDD remains static at the 50,000 starting-capital reference.
- Daily baseline captures authoritative balance per Risk Day and rolls over at
  the configured reset.

### Instrument Compatibility

- CASH plus LINEAR_PERPETUAL is accepted.
- CASH Challenge plus SPOT is rejected before execution authorization.
- No provider-specific execution branch is introduced.

### Fidelity Documentation

- Commission support is reported according to the existing generic fee model,
  or explicitly marked unsupported if no such model exists.
- Periodic margin funding is explicitly marked unsupported.
- Leverage/margin semantics are explicitly marked unsupported.
- Provider-side automatic terminal actions are explicitly marked unsupported.

## Blocking Stop Condition

The official English Kraken FAQ states that an evaluation breaches when equity
falls **to or below** the daily-loss or maximum-drawdown limit.

The current generic implementation is inconsistent for daily drawdown:

- `DailyDrawdownRule` uses `ThresholdRuleSupport.maximum`.
- `maximum` treats equality as `PASS`.
- `MaximumTotalDrawdownRule` uses `maximumInclusiveBreach` and treats equality
  as a breach.

At the 3% daily-loss boundary, configuration alone therefore cannot produce
the official provider semantics. Implementation is blocked until the generic
daily drawdown equality behavior is reviewed and approved. This Story must not
silently change generic Risk behavior to bypass that decision.

## Out of Scope

- Kraken-specific Risk rules or Challenge evaluators.
- Runtime Kraken website access or rule synchronization.
- Funding engine, leverage, margin, liquidation, or hedging.
- Partial-close redesign, multi-collateral, inverse products, options, or
  futures expiry.
- Kraken Prop Lite, Starter, Intermediate, or other account sizes.
- Payouts, funded-account lifecycle, purchase workflow, or API connectivity.
- Automatic position closure, order cancellation, or account disabling.
- Frontend, Quant, ML, or AI work.

## Validation

When unblocked, run the affected module verification and focused tests for:

```text
benchmark bootstrap and idempotency
Challenge and Risk Policy persistence
Challenge start
Challenge progression
daily Risk baseline
daily and total drawdown boundaries
instrument compatibility
PAPER CASH settlement
PAPER derivative valuation
```

Also run:

```bash
mvn -q verify
git diff --check
```

## Current Decision

The original stop condition was resolved by the separate provider-neutral Risk
fix merged in `6a9715df` (`DailyDrawdownRule` now uses inclusive breach
semantics). Story 0089 is implemented in the working tree and awaits human
review. The PostgreSQL upgrade-path validation passed; a full PostgreSQL fresh
install remains blocked by the pre-existing ordering defect where vendor V3
references `risk_evaluation_t1` before common V9 creates it.
