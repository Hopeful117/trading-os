# Repository Analysis - Story 0187

## Repository Baseline

```text
REMOTE_MAIN: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_BASE: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_HEAD: f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
CURRENT_BRANCH: story/0187-paper-margin-authority-adr
WORKTREE_STATE: pre-existing untracked Story 0185 artifacts preserved
NEXT_AVAILABLE_ADR: ADR-083
NEXT_AVAILABLE_STORY: 0187
```

The mandatory latest-main verification was executed before branch creation. The
only pre-existing worktree content is the untracked local Story 0185 artifact
directory; it is unrelated to this Story and must remain excluded.

DevLog applicability was checked for prior ADR-082 and margin decisions, but the
DevLog request failed with `Authentication is required`. This analysis therefore
uses the current repository, accepted ADR-082, Stories 0183-0186, and source /
test evidence. The repository is authoritative for current physical behavior.

## Governing Architecture

### ADR-082

ADR-082 defines the CFD financial contract and mark-to-market model. It keeps the
Risk Domain provider-neutral, requires Trading Core to assemble complete facts,
and states:

- the CFD contract contains instrument/product metadata, currencies, quantity
  unit, multiplier, precision, constraints, and contract provenance;
- notional is exposure information and is not required margin;
- required and used margin are factual inputs through `RequiredMarginPort` or an
  equivalent mode-correct boundary;
- `notional / leverage` is forbidden unless a separate approved decision makes
  the rule complete and authoritative;
- missing or stale margin fails closed;
- LIVE provider facts remain authoritative;
- PAPER Trading Core state and Market Data marks/conversions are authoritative;
- PAPER must not call Broker Service for financial or position facts;
- `CASH + CFD` remains incompatible until a separately approved change.

ADR-082 does not define the missing PAPER margin policy. Its historical decision
must remain unchanged; this Story complements it with a separate authority and
simulation policy decision.

### Stories 0183-0186

Story 0183 preserves LIVE provider-authoritative account and position facts and
explicitly excludes proposed-order margin changes. Story 0184 integrates
`RequiredMarginPort` into T0/T1 risk but requires authoritative facts and keeps
PAPER local authority. Story 0185 blocks `CASH + CFD` because the PAPER margin
source is unproven. Story 0186 confirms:

```text
PAPER_MARGIN_PREVIEW_RUNTIME = AVAILABLE
PAPER_MARGIN_PREVIEW_AUTHORITY = UNPROVEN
PAPER_REQUIRED_MARGIN_AUTHORITY = UNAVAILABLE
```

These conclusions remain valid until ADR-083 is accepted and later implemented.

## Current Required-Margin Boundary

`RequiredMarginPort.Request` carries broker account, instrument, direction,
quantity, price, and requested time. `Fact` carries amount, currency, source ID,
source version, and observed time. `RequiredMarginCurrencyNormalizer` validates
positive amount, provenance, freshness, reporting currency, and explicit FX
valuation evidence before Risk receives a normalized monetary value.

`TradePlanRiskEvaluationService` resolves required margin during T0. It supplies
the proposed instrument price and fails closed when the fact is unavailable or
cannot be normalized. `ExecutionTimeRiskRevalidationService` repeats the same
boundary at T1 with fresh execution-time market facts. The application boundary
is therefore suitable for preserving the normalized monetary fact, but its PAPER
authority source is not yet mode-correct.

## Existing PAPER Inconsistency

`BrokerRequiredMarginClient` currently detects `ExecutionMode.PAPER`, loads
provider technical capabilities, and calls:

```text
Trading Core
  -> BrokerRequiredMarginClient
  -> Broker Service provider margin preview
  -> RequiredMarginPort.Fact
```

The provider preview is runtime-available, but it is not a Trading Core local
simulation policy. This conflicts with ADR-082's PAPER financial authority
boundary. ADR-083 must make the runtime authorization path local and deterministic
for PAPER; the provider preview may only remain a non-authoritative configuration
or diagnostic input during migration.

## Existing Provider Evidence

The Kraken implementation uses:

```text
amount = quantity * price / leverage
leverage = requested leverage or 1
```

Its source identity is an infrastructure capability identifier. The implementation
does not represent contract multiplier, account exposure, netting/hedging, policy
version, account reporting currency, or explicit FX semantics. It cannot be the
PAPER authority without a separate normalized policy contract.

cTrader metadata exposes account balance, equity, margin, free margin,
`leverageInCents`, maximum leverage, and a margin calculation type. cTrader
observations expose current account/position margin. The repository does not
expose a provider-neutral proposed-order margin operation with complete semantics.
These facts remain LIVE/provider evidence and do not define PAPER economics.

## Domain Boundaries

`InstrumentFinancialContract` is a provider-neutral instrument contract owned by
the Market Data / Trading Core boundary. It defines what the instrument is:
product type, currencies, quantity unit, contract multiplier, precision,
constraints, and provenance. It must not contain account-specific leverage,
margin rate, exposure interaction, or hedging rules.

A separate provider-neutral `MarginPolicy` or equivalent concept is required to
define how a particular PAPER account supplies collateral for a proposed order.
The policy belongs to account/simulation configuration, not to the instrument
contract and not to FTMO challenge rules.

Current `Account` stores settlement, balances, equity, valuation timestamps,
rules, and trades, but no margin-policy identity or version. `BrokerAccount`
stores provider registration, execution mode, and connection identity, but no
PAPER simulation policy. `TradePlanningProfile` contains risk budget and planning
preferences, not account financial simulation semantics. These facts support a
separate policy assignment boundary rather than silently adding margin policy to
planning preferences or the instrument contract.

## Candidate Models

### Provider-reference simulation

A PAPER account would directly simulate a selected provider/account contract.
This preserves economic resemblance but makes runtime behavior provider-coupled,
risks importing incomplete provider semantics, and conflicts with the PAPER local
authority requirement unless normalized and persisted first.

### Trading OS policy

A PAPER account would use an explicit Trading OS-defined margin policy. This is
provider-neutral and reproducible, but a policy must be configured with complete
economic inputs and provenance rather than appearing as an implicit global
default.

### Hybrid/reference-derived policy

A provider/reference or user configuration can seed a normalized policy, but the
assigned PAPER account uses an immutable versioned local policy snapshot. This
preserves provider-neutral runtime authority and provenance while allowing future
templates without making their runtime calculation provider-specific.

### Analysis recommendation

The hybrid model is the smallest architecture that preserves the current
authority boundaries and still permits provider/reference templates. Runtime
PAPER authority should be a local immutable `MarginPolicy` snapshot assigned to
the account or account-simulation profile. Provider data may establish policy
provenance or configuration inputs, but it must not be queried for every T0/T1
margin decision.

## Required Semantic Decisions for ADR-083

The proposed ADR must make the following explicit rather than leaving them to
implementation inference:

- required margin means incremental collateral required to accept the proposed
  order at the decision boundary, not used, free, position, or post-execution
  total margin;
- V1 should use a canonical positive margin rate, represented as an exact
  decimal percentage ratio, with positive finite validation and explicit
  monetary rounding at the policy boundary;
- V1 should support a static linear policy and a netting account model only;
  tiered/dynamic rates, hedging, portfolio offsets, cross-instrument collateral
  optimization, and ambiguous exposure are unavailable until separately modeled;
- existing exposure must be considered for same-direction increases, partial
  reductions, full closes, and reversals; V1 should fail closed for operations
  whose exposure transition cannot be represented deterministically;
- calculation occurs in instrument quote currency, then converts to account
  reporting currency through explicit directed Market Data FX evidence; identity
  conversion is allowed only when currencies are identical;
- policy identity, immutable version, source/provenance, effective timestamp,
  instrument/product scope, and FX evidence must survive the risk evidence;
- T0 and T1 use the same applicable policy version plus fresh market/account
  facts; a policy change between boundaries causes T1 to fail closed unless the
  original version remains explicitly applicable;
- absent or invalid policy, contract, exposure, price, or FX makes required
  margin unavailable;
- human authorization remains separate from valid margin evaluation.

These are candidate constraints for ADR drafting and require human approval of
this Repository Analysis before becoming an architectural decision.

## Compatibility and Sequencing Constraints

This Story must not change `SettlementCompatibilityPolicy`. The following remain
true:

```text
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
ADR_082_PHASE_8_RETRIED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
PRODUCTION_CODE_CHANGED = NO
```

After ADR-083 approval, a later implementation Story should introduce the
provider-neutral immutable policy model and assignment, persist or snapshot its
identity, implement deterministic PAPER calculation, route PAPER locally and
LIVE to provider authority, remove the PAPER provider preview from authorization,
validate T0/T1 and fail-closed cases, then separately reconsider ADR-082 Phase 8.

## Missing Context and Risks

- DevLog historical context could not be retrieved because authentication was
  unavailable.
- The repository has no existing accepted `MarginPolicy` concept or policy
  assignment persistence boundary.
- Current account models do not demonstrate hedging semantics; assuming hedging
  would invent provider behavior.
- The exact monetary rounding convention for future implementation remains an
  ADR decision, not current repository behavior.
- The current `RequiredMarginPort.Fact` does not carry policy identity; whether
  the later implementation evolves it or preserves policy provenance in the
  enclosing evidence must be decided without leaking policy mechanics into Risk
  Domain.

## Approval Gate

Repository Analysis is complete. Per the Engineering Story workflow, stop here
and request explicit human approval before producing `implementation-plan.md` or
the ADR-083 proposal.
