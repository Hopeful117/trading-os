# Story 0088 - Challenge Instrument Product Constraints

## Metadata

- **ID:** `0088`
- **Status:** `IMPLEMENTATION PLAN - AWAITING APPROVAL`
- **Related Stories:** `0078`, `0079`, `0084`, `0085`, `0086`, `0087`
- **Related ADRs:** `ADR-028`, `ADR-042`, `ADR-043`, `ADR-047`, `ADR-048`, `ADR-051`, `ADR-052`
- **Deferred:** Story `0089` - Kraken Prop Advanced 50k configuration

## Goal

Introduce provider-neutral Challenge instrument-product constraints and enforce
them before execution is authorized, while preserving the existing settlement,
Risk, execution, PAPER valuation, and Challenge lifecycle boundaries.

## Context

Story 0084 introduced the provider-neutral `InstrumentProductType` model:

```text
SPOT
LINEAR_PERPETUAL
```

and the settlement compatibility policy:

```text
INVENTORY + SPOT
CASH      + LINEAR_PERPETUAL
```

Stories 0085 and 0086 added CASH-settled derivative PAPER settlement and
authoritative mark-to-market equity. Story 0087 added generic Challenge
progression, settlement requirements, and daily Risk baseline semantics.

Current settlement compatibility is enforced too late, after execution and
fill processing. A product incompatible with an ACTIVE Challenge must be
rejected before an `ExecutionIntent` becomes executable or reaches a broker or
PAPER fill path.

## Scope

- Add nullable `requiredInstrumentProductType` to immutable,
  versioned `ChallengeDefinition`.
- Reuse the existing provider-neutral `InstrumentProductType` enum.
- Preserve `Account` ownership of `SettlementModel`; do not add product type to
  Account.
- Make authoritative product identity available on the immutable TradePlan
  version used by Risk and execution.
- Propagate canonical product identity through Market Intelligence, the Risk
  handoff, execution validation, and execution context.
- Resolve manual TradePlan product identity from authoritative Market Data
  context.
- Preserve canonical identity from automated Market Intelligence paths when
  already available.
- Add a provider-neutral Challenge product compatibility policy/service in
  Trading Core application/domain orchestration.
- Enforce the policy before `ExecutionIntent` creation, with any necessary
  defense-in-depth check at the pre-broker T1 boundary.
- Fail closed when required identity is missing, ambiguous, stale, or when the
  ACTIVE Challenge definition cannot be loaded.
- Preserve final `SettlementCompatibilityPolicy` validation in
  `PaperSettlementService`.
- Analyze and preserve safe ENTRY and EXIT behavior.
- Add additive migrations and focused regression coverage.

## Product Identity

The authoritative identity is:

```text
provider + symbol + InstrumentProductType
```

The product type must originate from authoritative Market Data or an immutable
TradePlan snapshot. It must not be accepted as arbitrary caller-provided
execution data, inferred from `ChallengeDefinition.product`, provider name,
plan code, or settlement model, or defaulted to `SPOT` or
`LINEAR_PERPETUAL`.

## Challenge Rule

For an ACTIVE Challenge:

```text
required == null       -> allowed
required == actual      -> allowed
required != actual      -> rejected
```

With no ACTIVE Challenge, existing execution behavior remains unchanged.
Terminal PASSED and BREACHED Challenges do not introduce new restrictions in
this Story.

## Enforcement and Boundaries

The primary enforcement point must be the earliest authoritative Trading Core
boundary where the Trading Account, exact TradePlan version, canonical product
identity, ACTIVE Challenge, and exact ChallengeDefinition version are known.
`ValidateAndCreateService` is the expected boundary, subject to repository
verification.

Challenge compatibility must not become a Risk rule. The deterministic Risk
Domain must not query Challenge repositories, Market Data, or external services.
Risk may carry product identity as immutable context or provenance only.

The existing settlement compatibility check remains a final financial invariant:

```text
TradePlan identity
    -> Challenge product authorization
    -> execution
    -> settlement compatibility
    -> financial mutation
```

## ENTRY and EXIT

ENTRY execution must use the product identity of the exact authoritative
TradePlan version validated by Risk and execution.

EXIT behavior must be investigated explicitly. A valid EXIT must remain
possible, and product identity must not be reinterpreted from a bare symbol.
If the current Trade model lacks sufficient canonical identity for deterministic
EXIT validation, the implementation must use the smallest additive propagation
required and must not silently classify historical records.

## Migration

Use additive Flyway migrations only.

- Add `challenge_definition.required_instrument_product_type` using enum/string
  semantics consistent with the repository.
- Keep historical Challenge definitions nullable unless deterministic evidence
  supports a migration value.
- Preserve unknown historical TradePlan or Trade identity as unknown; never
  silently label it `SPOT` or `LINEAR_PERPETUAL`.
- Add immutable TradePlan identity persistence safely if required by the current
  serialization or relational model.
- Do not modify existing migrations.

## Acceptance Criteria

### ChallengeDefinition

- `ChallengeDefinition` stores an optional required product type.
- Existing immutable/versioned behavior remains intact.
- Historical/null requirements remain supported.
- `ChallengeDefinition.product` is never parsed as technical product identity.

### Pre-execution Challenge enforcement

- ACTIVE Challenge requiring `LINEAR_PERPETUAL` accepts a linear perpetual.
- ACTIVE Challenge requiring `LINEAR_PERPETUAL` rejects SPOT before
  `ExecutionIntent` or broker interaction.
- ACTIVE Challenge requiring SPOT accepts SPOT when supported by the generic
  model.
- Missing or ambiguous required identity fails closed.
- Missing ACTIVE Challenge definition fails closed.
- Accounts without an ACTIVE Challenge retain existing behavior.

### Identity propagation

- The exact authoritative TradePlan version carries canonical product identity.
- Risk and execution use the same product identity.
- Caller-supplied execution data cannot substitute product type.
- Manual and automated TradePlan paths follow the same identity rules.
- Historical unknown identity is never silently defaulted.

### EXIT and settlement

- Valid EXIT behavior remains possible.
- EXIT identity is deterministic or the limitation is explicitly documented.
- Existing `SettlementCompatibilityPolicy` enforcement remains active.
- Story 0085 CASH/LINEAR_PERPETUAL settlement remains unchanged.
- Story 0086 PAPER valuation remains unchanged.
- Existing INVENTORY/SPOT behavior remains unchanged.

### Provider independence

- No provider-specific branch is introduced.
- No Kraken Prop configuration or Story 0089 behavior is included.
- No Challenge-specific Risk rule is introduced.

### Validation

- Focused Challenge, TradePlan, Risk handoff, execution validation, T1,
  settlement, valuation, migration, ENTRY, EXIT, manual, and automated tests
  pass.
- Affected Maven module `mvn -q verify` passes.
- `git diff --check` passes.
- SonarQube/JaCoCo results are reported accurately when configured.

## Out of Scope

- Kraken Prop configuration or Advanced 50k fixtures.
- MDL/MDD values, Kraken reset time, or Kraken-specific symbols.
- Provider branches, leverage, margin, liquidation, funding, partial close,
  hedging, multi-collateral, inverse perpetuals, futures expiry, or options.
- Automatic Challenge terminal position close, order cancellation, Account
  disabling, settlement mutation, or Risk Policy mutation.
- Frontend, Quant, ML, AI, or real Kraken Prop connectivity.
- Universal Position aggregate or broad Market Data redesign.

## Stop Conditions

Stop before implementation if:

- canonical product identity cannot be resolved before execution without
  inventing identity;
- authoritative TradePlan persistence cannot safely carry product identity;
- historical TradePlans would require silent reclassification;
- EXIT safety cannot be preserved;
- ACTIVE Challenge lookup cannot be deterministic from the Trading Account;
- the execution architecture has a materially contradictory authoritative
  pre-broker boundary;
- Challenge compatibility would need to move into Risk;
- the change requires a larger instrument-identity redesign.

## Required Artifacts

- `story.md`
- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`

Implementation remains blocked until the Repository Analysis and subsequent
Implementation Plan have passed their respective human approval gates.
