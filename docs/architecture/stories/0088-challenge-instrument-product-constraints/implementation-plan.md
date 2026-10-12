# Story 0088 - Implementation Plan

## Plan Status

`PROPOSED - AWAITING IMPLEMENTATION PLAN APPROVAL`

This plan follows the approved Repository Analysis. It does not include Story
0089 or provider-specific Challenge configuration.

## Design Summary

Use the immutable TradePlan version as the single identity carrier for the
product used by Risk and execution.

The persisted identity will contain:

```text
instrument provider
instrument symbol
InstrumentProductType product type
```

Market Intelligence will preserve the provider-neutral product type as a
transport/domain value without introducing a second Challenge taxonomy. The
Trading Core boundary will validate the value against its existing
`InstrumentProductType` enum. Product identity will be copied into the
`TradePlanRiskPort.Snapshot` and execution context; execution callers will not
be allowed to supply or replace it.

Challenge compatibility will remain an application/domain policy in Trading
Core. Risk will not query Challenge or Market Data and will not receive a new
Challenge-specific rule.

## Work Packages

### 1. Challenge definition persistence

Update Trading Core:

- Add nullable `requiredInstrumentProductType` to
  `challenge.domain.ChallengeDefinition`.
- Preserve immutable/versioned construction and accessors.
- Add `V29__challenge_definition_instrument_product_type.sql` with a nullable
  enum/string column.
- Keep historical definitions null; do not infer from `product`, provider,
  plan code, settlement model, or historical accounts.
- Add domain and persistence tests for SPOT, LINEAR_PERPETUAL, null, and
  immutable definitions.

`ChallengeInstance` will continue to snapshot the exact definition identity,
not duplicate the requirement. `StartChallengeService` will retain the
existing settlement compatibility check and will not add provider or product
inference at Challenge start.

### 2. Canonical Market Data lookup

Update the smallest existing Market Data boundary required for precise lookup:

- Add a provider/symbol lookup that returns the complete set of matching
  product identities, or an equivalent precise lookup by canonical `marketId`.
- Keep the endpoint provider-neutral and free of Challenge concepts.
- Keep ambiguity explicit; zero or multiple incompatible matches must not be
  converted into a default.
- Update Trading Core `MarketDataClient`/`MarketService` adapters and tests.

The existing `PaperSettlementService` lookup will remain a final compatibility
guard. It may be switched to the precise capability where appropriate, but its
fail-closed behavior and `SettlementCompatibilityPolicy` call will remain.

### 3. Market Intelligence TradePlan identity

Extend the Market Intelligence authoritative TradePlan version:

- Add immutable product identity fields to the TradePlan execution snapshot or
  its dedicated identity value object.
- Preserve the symbol as the canonical symbol and add provider/product type
  without parsing business `product` metadata.
- Resolve automated opportunity identity from the existing opportunity
  `marketId` through the existing Market Data client before TradePlan creation.
- Extend manual TradePlan transport so Trading Core forwards the `Market Data`
  identity already resolved from the requested `marketId`.
- Reject missing or ambiguous identity while creating a supported TradePlan;
  do not silently create a symbol-only plan.
- Preserve identity across TradePlan version transitions, persistence mapping,
  risk snapshots, execution-readiness snapshots, and response contracts.
- Add `V14__trade_plan_instrument_product_identity.sql` with nullable historical
  identity fields if explicit columns are needed by the current relational
  model. Existing historical TradePlans remain unknown.

The cross-service JSON contract will use stable provider-neutral names for the
existing enum values. Market Intelligence will not add a Kraken branch or a
Challenge-specific enum.

### 4. Risk handoff and execution context

Update the Trading Core/M market-intelligence contract path:

- Add provider/product identity to `TradePlanRiskSnapshot` and
  `TradePlanTransport`.
- Map the identity into `TradePlanRiskPort.Snapshot`.
- Validate identity completeness and enum values in
  `MarketIntelligenceRiskClient` and/or the risk handoff boundary.
- Preserve the identity in Risk provenance/context as contextual data only;
  do not add a Risk rule or repository lookup.
- Ensure `TradePlanRiskEvaluationService` and
  `ExecutionTimeRiskRevalidationService` use the exact snapshot identity.
- Keep version correspondence checks unchanged and extend them to reject
  incomplete identity where the execution path requires it.

### 5. Pre-execution Challenge policy

Add a focused Trading Core application service, following repository naming
conventions, for example `ChallengeInstrumentCompatibilityService`.

Policy inputs:

```text
accountId
InstrumentProductType actualProductType
```

Policy behavior:

- no ACTIVE Challenge: allow;
- active definition with null requirement: allow;
- equal actual/required type: allow;
- mismatch: reject with stable `CHALLENGE_INSTRUMENT_PRODUCT_MISMATCH`;
- missing active definition or required identity: fail closed with stable
  application error semantics such as
  `INSTRUMENT_PRODUCT_IDENTITY_UNAVAILABLE`.

The service will load the ACTIVE Challenge through
`ChallengeInstanceRepository.findByAccountIdAndStatus(...)`, then load the
exact `(definitionId, definitionVersion)` from `ChallengeDefinitionRepository`.
It will not use latest-definition lookup or infer identity from provider data.

Integrate the policy in `ValidateAndCreateService` after the authoritative
READY TradePlan snapshot is available and before `CreateExecutionIntentService`
is called. Add a defense-in-depth invocation at T1 only if needed to cover
execution lifecycle races, using the same immutable TradePlan identity rather
than re-resolving a symbol.

### 6. EXIT safety and settlement defense in depth

Investigate the existing `PaperExitService` and `Trade` representation before
changing EXIT behavior.

- Preserve valid EXIT creation and opposite-side semantics.
- If target Trade identity is available from the authoritative opening
  TradePlan, carry or resolve it deterministically.
- If historical Trade rows lack identity, keep them unknown and fail closed
  only where the active Challenge rule requires product identity; never label
  them SPOT by default.
- Do not bypass Challenge checks merely because an operation is an EXIT.
- Do not remove `PaperSettlementService` product resolution or
  `SettlementCompatibilityPolicy` validation.

If the existing EXIT contract cannot satisfy these constraints without a larger
Position redesign, stop and report the exact gap rather than introducing a
universal Position aggregate.

## Expected File Areas

### Trading Core

- `challenge/domain/ChallengeDefinition.java`
- `challenge/application/ChallengeDefinitionRepository.java`
- `challenge/application/ChallengeInstanceRepository.java`
- new Challenge compatibility policy/service and exception mapping
- `risk/application/port/TradePlanRiskPort.java`
- `risk/infrastructure/client/MarketIntelligenceRiskClient.java`
- `execution/application/service/ValidateAndCreateService.java`
- `execution/application/service/ExecutionTimeRiskRevalidationService.java`
- `execution/domain/aggregate/ExecutionIntent.java` or immutable provenance
  mapping only if required by the exact execution contract
- `positionclose/application/service/PaperExitService.java` if EXIT identity
  requires a minimal change
- `execution/application/service/PaperSettlementService.java` only for lookup
  reuse, never to remove defense-in-depth validation
- `market_data/apiClient/MarketDataClient.java`
- `market_data/service/MarketService.java` and implementation
- additive migration under `trading-core/src/main/resources/db/migration/common/`

### Market Intelligence

- TradePlan execution/domain identity model
- `TradePlanBuilder.java`
- manual and opportunity TradePlan creation boundaries
- `JpaTradePlanEntity.java`, `TradePlanMapper.java`, and repository mapping
- `TradePlanRiskSnapshot.java`
- `TradePlanRiskHandoffService.java`
- `TradePlanResponse.java` and internal risk transport
- existing Market Data adapter/client and market response contract
- additive migration under `market-intelligence/src/main/resources/db/migration/`

## Tests to Add or Update

### Challenge

- `ChallengeDefinitionTest`: nullable and both supported product types.
- `ChallengePersistenceTest`: persisted product requirement and historical null.
- Compatibility service tests for no active Challenge, null requirement,
  matching, mismatch, missing definition, missing identity, and ambiguity.

### Identity propagation

- Market Data precise lookup tests.
- Manual TradePlan test proving `marketId` identity is preserved.
- Automated opportunity TradePlan test proving canonical market identity is
  preserved.
- Market Intelligence persistence round-trip tests.
- Risk handoff snapshot and Trading Core client mapping tests.
- Exact TradePlan version tests proving identity cannot be substituted.

### Execution

- `ValidateAndCreateService` rejects mismatch before intent persistence.
- Matching LINEAR_PERPETUAL and SPOT paths are allowed where compatible.
- Missing/ambiguous identity fails closed.
- No-active-Challenge execution remains unchanged.
- T1 uses the same TradePlan identity and does not perform symbol-only lookup.
- Broker/PAPER adapter is not called after a pre-execution mismatch.

### EXIT and regressions

- Valid PAPER EXIT remains possible.
- EXIT mismatch/missing identity behavior is explicit.
- Story 0085 settlement tests remain green.
- Story 0086 valuation tests remain green.
- Story 0087 Challenge progression and baseline tests remain green.
- Existing Risk, execution lifecycle, INVENTORY/SPOT, and CASH/
  LINEAR_PERPETUAL regressions remain green.

## Validation Plan

Run after implementation:

```bash
mvn -q verify
git diff --check
```

Run `mvn -q verify` independently in each affected Maven module, at minimum:

- `trading-core`
- `market-intelligence`
- `market-data` if its API or implementation changes

Also run focused Challenge, Market Data identity, TradePlan, Risk handoff,
execution validation/T1, PAPER settlement, derivative valuation, migration,
manual, automated, ENTRY, and EXIT tests.

If configured, run the repository-supported JaCoCo/Sonar analysis for each
affected module and report Quality Gate, new coverage, new issues, and any
unrelated baseline failures separately.

## Documentation and ADR Decision

No new ADR is planned. ADR-051 already establishes the canonical identity and
ownership boundary, while ADR-028, ADR-047, ADR-048, and ADR-052 establish the
Risk, TradePlan authority, execution, and PAPER valuation boundaries.

The implementation report will document the final identity origin, immutable
propagation, pre-execution enforcement point, ACTIVE lookup, ENTRY/EXIT
semantics, historical migration behavior, and Story 0089 readiness. A new ADR
will be proposed only if implementation reveals a durable decision not covered
by those accepted ADRs.

## Stop Conditions During Implementation

Stop and report instead of improvising if:

- the exact TradePlan version cannot persist product identity safely;
- automated opportunity identity cannot be resolved from authoritative Market
  Data without guessing;
- manual and automated paths require incompatible identity models;
- EXIT safety requires a universal Position aggregate or silent historical
  reclassification;
- Challenge compatibility would need to enter Risk Domain;
- a provider-specific branch becomes necessary;
- an existing authoritative pre-broker boundary materially contradicts this
  plan.

## Expected Deliverables

- implementation code and additive migrations;
- focused and regression tests;
- updated `implementation-report.md`;
- `code-review.md`;
- `engineering-report.md`;
- no Story 0089 configuration;
- no commit, push, or merge as part of implementation.
