# Story 0088 - Repository Analysis

## Metadata

- Story: `0088 - Challenge Instrument Product Constraints`
- Analysis base: `62cb940af2d1ba005d4ae396b12cd2cdea488663`
- Branch at analysis: `main`
- Worktree: clean
- `main`: `62cb940af2d1ba005d4ae396b12cd2cdea488663`
- `origin/main`: `62cb940af2d1ba005d4ae396b12cd2cdea488663`
- Main/origin relationship: aligned

## Context Sources

The following canonical artifacts were inspected:

- Story 0078: Challenge definition and instance domain.
- Story 0079: PAPER Challenge progression and Risk monitoring.
- Story 0084: Account settlement semantics and instrument identity foundation.
- Story 0085: CASH-settled derivative PAPER settlement.
- Story 0086: PAPER derivative mark-to-market and equity valuation.
- Story 0087: Challenge progression and daily Risk baseline semantics.
- ADR-028: deterministic Risk Engine architecture.
- ADR-042: position authority and close semantics by execution mode.
- ADR-043: Account identity and mode-specific Risk facts.
- ADR-047: manual TradePlans as a first-class TradePlan origin.
- ADR-048: intelligence evidence and authority.
- ADR-051: Account settlement semantics and instrument product identity.
- ADR-052: PAPER CASH realized state and equity valuation authority.

DevLog applicability was checked for Story 0088. The Story Agent was unavailable
because the configured provider returned an exhausted-quota error. The analysis
therefore relies on the current repository and canonical repository artifacts.

## Existing Identity Model

The canonical product identity is already provider-neutral:

```text
provider + symbol + InstrumentProductType
```

`InstrumentProductType` is shared by Market Data and Trading Core and currently
contains `SPOT` and `LINEAR_PERPETUAL`. `MarketResponse` already exposes
`provider`, `symbol`, and `productType`. `SettlementCompatibilityPolicy` is a
local deterministic policy implementing:

```text
INVENTORY + SPOT
CASH      + LINEAR_PERPETUAL
```

`Account` owns `SettlementModel`; it does not own product type. No Account
product-type field is required or permitted by ADR-051.

## Challenge State

`ChallengeDefinition` is immutable and versioned by `(id, definitionVersion)`.
It currently stores nullable `requiredSettlementModel`, but has no product-type
requirement. `ChallengeInstance` snapshots definition identity and Risk Policy
identity, but does not need to duplicate a product requirement because the exact
definition version can be loaded while the instance is ACTIVE.

`StartChallengeService` validates Account ownership, one ACTIVE instance,
definition existence, settlement compatibility, Risk Policy existence, capital,
and currency before persistence. Story 0088 should add the nullable product
requirement to the immutable definition and leave Challenge start focused on the
existing Account/settlement compatibility. Runtime product authorization belongs
to the pre-execution boundary.

The ACTIVE lookup capability already exists as
`ChallengeInstanceRepository.findByAccountIdAndStatus(...)` with a pessimistic
lock. No production caller currently enforces instrument product constraints.

## TradePlan Identity Findings

The current authoritative TradePlan is owned and persisted by Market
Intelligence. Its immutable execution payload contains the instrument symbol,
but no `InstrumentProductType` or explicit Market Data identity.

Current propagation:

```text
Market Intelligence TradePlan execution.instrument
    -> TradePlanRiskPort.Snapshot.instrument
    -> Risk ProposedTrade.instrument
    -> ExecutionParameters.instrument
    -> PAPER/Broker execution
```

The following boundaries currently drop product identity:

- Manual TradePlan creation resolves a Market Data market by `marketId`, then
  forwards only `market.symbol` to Market Intelligence.
- Automated opportunity TradePlan creation forwards the existing opportunity
  flow, but its authoritative TradePlan snapshot exposes only the symbol to
  Trading Core.
- `TradePlanRiskPort.Snapshot` carries no product type.
- `MarketIntelligenceRiskClient.TradePlanTransport` carries no product type.
- `ExecutionIntent` preserves the TradePlan reference and execution parameters,
  but no product identity.
- `Trade` persists only `symbol`; this is insufficient to classify historical
  trades safely and must not be silently backfilled as SPOT.

The exact TradePlan version is already the authoritative execution reference:
Risk loads it by `(tradePlanId, version)`, `ValidateAndCreateService` verifies
the Risk Evaluation version, and `ExecutionTimeRiskRevalidationService` loads
the READY version again. Adding product identity to that immutable TradePlan
version and carrying it through the Risk port provides the required TOCTOU-safe
source. Caller-supplied execution parameters cannot override it because
`ValidateAndCreateService` derives parameters from the authoritative snapshot.

## Market Data Findings

Trading Core's `MarketDataClient` currently exposes `findAll()` and `findById()`.
`MarketService` exposes the same operations. The current PAPER settlement path
resolves product type using `findAll()` filtered by provider and symbol, then
fails closed when the result is missing or ambiguous. This is correct as a
defense-in-depth invariant but is not an adequate primary pre-execution lookup
for the new Challenge rule.

The smallest preferred capability is a precise provider/symbol/product-aware
lookup or a precise provider/symbol lookup that fails when multiple products
match. It must remain a Market Data capability and must not accept Challenge
concepts. Manual creation can use the already resolved `marketId`; automated
creation must propagate the canonical identity from its existing opportunity or
market context rather than reinterpreting a bare symbol.

## Execution Enforcement Findings

The earliest authoritative Trading Core boundary with the required data is
`ValidateAndCreateService`:

- it loads the completed authoritative Risk Evaluation;
- it loads and verifies the exact accepted TradePlan version;
- it resolves the Trading Account and BrokerAccount ownership;
- it prepares the exact TradePlan for execution;
- it creates the `ExecutionIntent` only after validation.

A dedicated provider-neutral application policy/service should validate:

```text
accountId + authoritative product type
```

against the ACTIVE Challenge and its exact definition version before
`CreateExecutionIntentService.create(...)` is called. Expected behavior:

- no ACTIVE Challenge: allow existing behavior;
- ACTIVE definition with null requirement: allow;
- matching requirement: allow;
- mismatch: reject with stable application error semantics;
- missing/ambiguous required identity or missing ACTIVE definition: fail closed.

`ExecutionTimeRiskRevalidationService` is a second pre-broker boundary and
should repeat the authorization from the same immutable READY TradePlan context
if needed for defense against lifecycle races. It must not independently resolve
the product from a bare symbol.

`ExecutionIntent` creation is therefore the primary invariant boundary, while
the existing `PaperSettlementService` compatibility validation remains a final
financial invariant. The settlement lookup may later consume the preserved
identity, but its current check must not be deleted.

## ENTRY and EXIT

ENTRY TradePlans have an authoritative product identity once the TradePlan
propagation is added. They must be rejected before an `ExecutionIntent` exists
when incompatible with an ACTIVE Challenge.

EXIT currently uses `PaperExitService` and `CreateExecutionIntentService.createExit`
without a TradePlan reference. The target Trade is the current local PAPER
position authority, but `Trade` persists only symbol and cannot safely carry a
canonical product type. LIVE close follows a separate broker-authoritative path.

The implementation must not bypass Challenge checks blindly for EXIT. It must
either propagate a safe canonical identity from the target Trade/position
context or explicitly establish that the existing EXIT contract is outside the
ACTIVE Challenge ENTRY restriction and preserve the financial settlement guard.
If the current Trade model cannot support deterministic EXIT identity without
require a small additive identity field plus an explicit unknown state rather
than a default.

## Risk Boundary

ADR-028 requires immutable Risk inputs and forbids repository or external
service access from the deterministic Risk Domain. Challenge product
compatibility must therefore remain in Trading Core application/domain
orchestration. Risk may carry product identity as contextual provenance, but it
must not query Challenge or Market Data and must not introduce a
`CHALLENGE_PRODUCT_TYPE` Risk rule.

## Persistence and Migration Impact

Required additive persistence changes are expected in:

- Trading Core `challenge_definition.required_instrument_product_type`.
- Market Intelligence TradePlan version persistence for immutable product
  identity, if the current JSON execution payload is not extended safely.
- Potentially an explicit canonical identity field on PAPER `Trade` only if EXIT
  analysis proves it necessary.

Historical Challenge definitions may remain null. Historical TradePlans and
Trades must not be guessed as SPOT or LINEAR_PERPETUAL. Any nullable historical
identity must cause the new product authorization to fail closed when the
identity is required.

No provider-specific configuration, Kraken branch, plan-code interpretation,
leverage, margin, or Story 0089 behavior is present in the analysis scope.

## Production Call-Site Inventory

Relevant production calls found:

- `SettlementCompatibilityPolicy`: `PaperSettlementService` only.
- Market Data product lookup: `PaperSettlementService.resolveProductType(...)`
  and Market Data consumers using `findAll()`/`findById()`.
- ACTIVE Challenge lookup: `ChallengeReevaluationService` and Challenge
  application services use `ChallengeInstanceRepository`; no execution policy
  currently uses it.
- TradePlan to Risk: `MarketIntelligenceRiskClient` and
  `TradePlanRiskEvaluationService`.
- TradePlan to Execution: `ValidateAndCreateService` and
  `ExecutionTimeRiskRevalidationService`.
- Execution to settlement/broker: `ExecutionFinalizationStep`,
  `PaperSettlementService`, and `RoutingBrokerExecutionAdapter`.

## Baseline Validation

Executed from `trading-core` before implementation:

```bash
mvn -q -Dtest=ChallengeDefinitionTest,ChallengeInstanceTest,StartChallengeServiceTest,ChallengePersistenceTest,ChallengeProgressServiceTest,AccountRiskMonitoringServiceTest,TradePlanRiskEvaluationServiceTest,ExecutionTimeRiskRevalidationServiceTest,PaperCashDerivativeSettlementTest,PaperSettlementExitTest,SettlementCompatibilityPolicyTest,PaperExecutionVerticalRegressionTest test
```

Result: passed. The output contained expected test logging, H2/Flyway version
warnings, and Mockito dynamic-agent warnings; no test failure was reported.

## Analysis Conclusion

The requested Story is implementable without a larger instrument-identity
redesign, provided the implementation makes the immutable TradePlan version the
identity carrier and keeps Challenge compatibility outside Risk. The main
implementation risks are:

1. preserving compatibility for existing Market Intelligence and Trading Core
   transport constructors/tests while making new authoritative identity
   mandatory for supported execution paths;
2. migrating nullable historical TradePlan identity without defaulting it;
3. ensuring manual and automated paths both populate the same canonical field;
4. preserving deterministic EXIT behavior without trapping existing PAPER
   positions;
5. preventing duplicate or stale symbol-only resolution at T1 and settlement.

No implementation code has been changed at this stage. The next permitted stage
is an Implementation Plan after explicit human approval of this Repository
Analysis.
