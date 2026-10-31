# Repository Analysis - Story 0188

## Repository Baseline

```text
REMOTE_MAIN: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_BASE: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_HEAD: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
CURRENT_BRANCH: story/0188-paper-margin-policy-implementation
WORKTREE_STATE: uncommitted ADR-083/0187 artifacts and pre-existing 0185 artifacts preserved
```

Latest-main verification was executed before analysis. No production files are
currently modified by this Story. The uncommitted 0187 documentation is carried
on this dedicated branch because it had not yet been committed; it must remain
in the final 0187 delivery scope and must not be mixed into an 0188 implementation
commit without an explicit integration decision.

DevLog applicability was checked for Story 0188, but the request failed with
`Authentication is required`. This analysis therefore relies on the accepted
ADR-083, ADR-082, ADR-043, current source, migrations, tests, and Story 0188.

## Governing Decisions

### ADR-083

ADR-083 is accepted and establishes:

- local immutable versioned `MarginPolicy` as PAPER authority;
- provider/reference information as configuration provenance only;
- LIVE provider authority unchanged;
- incremental required margin rather than used, position, or total margin;
- V1 static positive margin rate, linear contract, netting-only exposure;
- hedging, tiered, dynamic, portfolio-offset, and cross-instrument cases fail
  closed;
- exact account/policy/contract/exposure/valuation resolution context;
- quote-currency calculation followed by exactly one canonical directed FX path;
- exact T0 policy version reuse at T1 with fresh facts;
- deterministic failure diagnostics in Trading Core evidence;
- current PAPER Broker Service preview unavailable for PAPER authorization;
- `CASH + CFD` and `INVENTORY + CFD` remain rejected.

### ADR-082 and Story 0186

ADR-082 remains authoritative for CFD contract, valuation, and accounting
semantics. Story 0186 established that the current PAPER provider preview is
runtime-available but semantically non-authoritative. Story 0188 must implement
ADR-083 without rewriting ADR-082 or retrying Phase 8 compatibility.

## Current Account Identity

The repository now contains a canonical relation field:

- `Account.brokerAccountId` is persisted and has a foreign key through
  `V12__persist_canonical_account_broker_relation.sql`.
- `AccountRepository.findByBrokerAccountId` resolves the financial account.
- PAPER provisioning in `BrokerAccountService` sets `Account.brokerAccountId` to
  the newly created `BrokerAccount.id`.
- `PaperCfdSettlementService` resolves PAPER accounts through that relation and
  validates owner identity.
- `RiskPersistence.AccountConfiguration` also stores and validates the
  account-to-broker mapping.

This is stronger than the earlier ADR-043 baseline, but the risk/execution
boundary still carries both identities inconsistently. `ExecutionIntent` and
`RequiredMarginPort.Request` use `brokerAccountId`, while T0 starts from the
financial account ID and T1 has historically treated the routing ID as an
account ID in parts of the flow. Story 0188 must not add another lookup by
provider or display name.

The implementation must establish one application context before margin
resolution:

```text
financial Account.id
    -> validated Account.brokerAccountId
    -> routing BrokerAccount.id
    -> PAPER MarginPolicy assignment
```

The provider registration UUID remains a provider identity and is not a substitute
for either account ID.

## Existing Versioned Policy Pattern

`RiskPersistence` already implements an immutable versioned policy pattern for
risk profiles:

- `RiskProfileEntity` is keyed by `(id, semanticVersion)` and marked immutable;
- `RiskProfileRuleEntity` is keyed by profile/version/rule;
- `AccountRiskProfileAssignmentEntity` stores account assignment, timestamp, and
  provenance;
- `RiskPersistence.assignedProfile` resolves the assignment to the exact profile
  version;
- `RiskPersistence.profile` resolves an immutable historical version;
- risk evaluation evidence persists component snapshots and payloads.

This pattern is the strongest existing repository convention for `MarginPolicy`.
Story 0188 should reuse its conceptual lifecycle, but must not conflate margin
policy with risk-rule profile semantics. A distinct policy identity, rule table
or immutable policy payload, assignment, provenance, and resolver are required.

The current `AccountRiskConfiguration` already carries reporting currency and a
broker-account reference, but it has no margin-policy reference. The likely
implementation boundary is additive account-scoped policy assignment with an
immutable policy/version store. Exact schema names and migration structure belong
to the approved Implementation Plan.

## Existing Instrument Contract

`InstrumentFinancialContract` and persisted `Trade` contract fields already
provide:

- product type and canonical instrument identity;
- base/quote currencies;
- quantity unit;
- contract multiplier;
- precision and quantity constraints;
- contract source ID/version/observed time.

`InstrumentFinancialContractResolver` resolves the contract from the Market Data
catalogue and rejects missing or ambiguous instrument identity. `Trade` persists
the contract snapshot for restart-safe CFD valuation. No margin rate, leverage,
hedging, or account exposure policy belongs in this contract.

The future margin resolver must consume this contract and reject a missing,
conflicting, or stale contract rather than reconstructing it from provider
symbols.

## Existing PAPER Exposure

`PaperCfdSettlementService` persists each PAPER CFD entry as a separate open
`Trade`. A CFD exit targets one trade and currently requires the fill quantity to
equal the target trade quantity. The service preserves signed open quantities
through `V48__allow_signed_open_cfd_exposure.sql` and calculates valuation by
iterating open trades.

This is not yet a canonical net-position aggregate. The implementation must add a
read-side `PaperMarginExposureSnapshot` or equivalent application boundary that:

1. resolves the canonical financial account;
2. loads every open CFD trade for the canonical instrument;
3. validates status, signed quantity, contract identity/version, account
   ownership, and required timestamps;
4. aggregates signed quantities only for the V1 netting policy;
5. preserves the existing individual target-trade settlement behavior;
6. records source account/trade versions and observation time;
7. returns deterministic `AMBIGUOUS_EXPOSURE` or `EXPOSURE_UNAVAILABLE` instead
   of silently choosing a subset.

Same-direction increases, partial reductions, full closes, and reversals can then
use the ADR-083 incremental formula. Hedged independent positions, conflicting
contract versions, missing trades, unsupported pending/partial-fill states, or
ambiguous account identity must fail closed.

## Required Margin Current Boundary

`RequiredMarginPort` currently has:

```text
Request(brokerAccountId, instrument, direction, quantity, price, requestedAt)
Fact(amount, currency, sourceId, sourceVersion, observedAt)
```

`TradePlanRiskEvaluationService` resolves the fact at T0. `ExecutionTimeRiskRevalidationService`
resolves it again at T1. `RequiredMarginCurrencyNormalizer` validates the fact and
converts it into reporting currency using Market Data valuation facts.

The current request shape is insufficient for ADR-083 because it does not carry
financial account identity, policy identity/version, exposure snapshot, reporting
currency, or FX context. Story 0188 must evolve the application boundary or add a
mode-aware local resolver before the final `Fact` projection. The canonical
context must be resolved before calculation, and `brokerAccountId` must remain a
routing correlation only.

The final Risk-facing output may remain the normalized monetary margin and Risk
Domain must remain provider-neutral. Failure reasons such as missing policy,
ambiguous exposure, missing FX, and invalid rounding must be retained in Trading
Core evidence rather than collapsed beyond auditability.

## T0 and T1 Integration

T0 currently assembles market, broker/account, risk profile, and required-margin
facts in `TradePlanRiskEvaluationService`, then persists component snapshots and
the official result through `RiskPersistence`.

T1 currently assembles fresh facts in `ExecutionTimeRiskRevalidationService`,
persists T1 evidence, and reuses the existing risk pipeline. Story 0188 must:

- capture the exact MarginPolicy identity/version and assignment snapshot at T0;
- persist policy/exposure/margin provenance with the existing evidence payload;
- resolve that exact immutable policy version at T1 rather than the current
  account default;
- acquire fresh T1 price, FX, account, and exposure facts;
- fail closed when the version is missing, revoked, expired, or the account/routing
  binding changed;
- prevent a concurrent policy assignment from mixing versions within one
  evaluation.

The existing `RiskPersistence` component/context snapshot mechanism is reusable,
but policy-specific persistence and assignment locking are not present yet.

## Currency and FX Evidence

`MarketValuationPort` already models:

- reporting currency;
- valuation and capture timestamps;
- `maxObservationAge`;
- quote-to-reporting rate;
- ordered conversion legs with `fromCurrency`, `toCurrency`, rate, provenance,
  effective time, and capture time.

T0 uses a five-minute freshness requirement and T1 uses a thirty-second
execution-time requirement through `FreshnessRequirement`. The client composes
conversion leg rates and preserves source evidence. Existing CFD valuation and
risk projection validate conversion direction and compare the composed rate with
`quoteToReportingRate`.

Story 0188 can reuse this boundary, but must ensure the local margin resolver
requires exactly one canonical path, validates the explicit max age against the
decision boundary, and rejects multiple/ambiguous paths. It must calculate in
quote currency and convert before the final upward currency-scale rounding.

## Provider Preview and LIVE Boundary

`BrokerRequiredMarginClient` currently calls:

```text
PAPER -> Broker Service provider preview
LIVE  -> Broker account preview
```

The Kraken implementation calculates `quantity * price / leverage` and defaults
to cash leverage one. This remains non-authoritative for PAPER. Story 0188 must
route PAPER to the local policy resolver and preserve LIVE provider authority.

The provider preview may remain only as explicitly labelled configuration-import
or diagnostic data. It must not be used as a fallback when local policy facts are
missing, and Broker Service availability must not decide PAPER margin approval.

## Persistence and Migration Surface

The implementation will require additive persistence for at least:

- immutable margin policy identity/version and policy payload/rules;
- policy assignment to the financial PAPER account or owned simulation profile;
- assignment provenance/effective time;
- policy/exposure provenance in risk evidence if the current payload is
  insufficient;
- any diagnostic reason code needed to explain unavailable margin.

Existing migrations demonstrate the project convention for additive versioned
schema changes. No migration has been created yet. Backward compatibility for
existing accounts without a policy must be fail-closed (`UNAVAILABLE`), not an
implicit default. Existing risk profiles must remain separate and unchanged.

## V1 Calculation Scope

The implementation should support only:

```text
static positive margin rate
linear contract notional
single account reporting currency
per-instrument signed net exposure
exactly one directed FX path when needed
eight-decimal maximum margin-rate precision
exact intermediate arithmetic
positive final CEILING rounding to explicit currency scale
```

It must reject:

```text
hedging
tiered or dynamic rate schedules
portfolio offsets
cross-instrument collateral optimization
ambiguous exposure
missing or stale contract/mark/FX/policy facts
unverified provider preview fallback
```

The formula is applied to existing and resulting signed exposure:

```text
requiredMargin = max(margin(existing + proposed) - margin(existing), 0)
```

It must produce zero new required margin for reductions and full closes, and
margin for the residual new exposure on a reversal.

## Compatibility and Safety

This implementation Story must not modify `SettlementCompatibilityPolicy`:

```text
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
ADR_082_PHASE_8_RETRIED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
```

No broker order, close, cancel, provider mutation, or execution safety change is
allowed. Human authorization remains independent of margin validity.

## Required Implementation Plan Decisions

Before implementation, the approved plan must settle:

- exact `MarginPolicy` aggregate and rule representation;
- exact policy assignment owner and database keys;
- canonical relation checks for financial and routing accounts;
- `RequiredMarginPort` evolution or adapter boundary;
- exposure snapshot query and consistency/locking strategy;
- policy assignment optimistic locking and T0/T1 version lookup;
- diagnostic result/reason contract;
- currency-scale authority and rounding implementation;
- migration behavior for existing accounts with no policy;
- test fixtures and quality-validation commands.

No implementation can proceed safely until these are explicit in the approved
Implementation Plan.

## Approval Gate

Repository Analysis is complete. Per the Engineering Story workflow, stop here
and request explicit human approval before creating `implementation-plan.md` or
modifying production code.
