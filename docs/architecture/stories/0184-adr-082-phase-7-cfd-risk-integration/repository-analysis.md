# Repository Analysis - Story 0184

## Baseline

- `REMOTE_MAIN`: `4c059eca4a71c8e3cc8f335c9ad7f53ee545db2e`
- `LOCAL_BASE`: `4c059eca4a71c8e3cc8f335c9ad7f53ee545db2e`
- `BRANCH`: `story/0184-adr-082-phase-7-cfd-risk-integration`
- The worktree was clean before analysis and no implementation files have been
  modified for this Story.
- Story 0183 / ADR-082 Phase 6 is merged. Its implementation explicitly leaves
  `RISK_INTEGRATION = NOT_IMPLEMENTED` and states that Phase 7 must consume
  provider facts without routing current used margin into proposed-order margin.
- DevLog context was attempted twice with the same request and idempotency key,
  but both calls timed out. Repository artifacts and current source remain the
  authority. Obsidian was not consulted because no relevant vault context was
  required for this repository-local analysis.

## Authoritative Context

ADR-082 requires:

- CFD position quantity to be signed canonical underlying-equivalent quantity.
- Notional to be `abs(quantity) * referencePrice * contractMultiplier`.
- PnL to be `quantity * (markPrice - entryPrice) * contractMultiplier`.
- Stop risk to use the adverse signed distance, absolute quantity, and contract
  multiplier.
- Quote-currency results to be converted to account reporting currency with an
  explicit timestamped directed conversion fact.
- CFD equity to be unavailable when required mark/conversion evidence is
  incomplete.
- Required and used margin to remain factual inputs; required margin must come
  from `RequiredMarginPort` or an equivalent authoritative boundary and must not
  be inferred from leverage or replaced with current used margin.
- Trading Core to assemble complete facts while the Risk Domain remains
  provider- and product-agnostic.
- PAPER to remain Trading Core-authoritative and LIVE to remain provider-
  authoritative.

Stories 0180 and 0183 establish the relevant read-side boundaries. Story 0180
provides read-only PAPER CFD valuation through `CfdValuationService` and
`MarketValuationPort`, while refusing LIVE valuation before provider authority.
Story 0183 provides complete LIVE account facts, signed provider positions,
freshness/completeness checks, and reconciliation, while explicitly leaving Risk
integration out of scope.

## Current Implementation

### Proposed trade assembly

`TradePlanRiskEvaluationService` and
`ExecutionTimeRiskRevalidationService` already share the same broad pipeline:

1. Load the plan or execution intent.
2. Load mode-aware risk facts.
3. Acquire T0 or T1 market valuation under the corresponding freshness policy.
4. Convert account and portfolio facts into reporting currency.
5. Resolve proposed `notional`, expected loss, and required margin.
6. Build the provider-neutral Risk Domain snapshots.
7. Invoke `DeterministicRiskEngine`.

Both services currently derive proposed notional and expected loss from persisted
plan fields and multiply them by a sizing-currency rate. They do not validate or
recalculate those values from a CFD financial contract before constructing
`ProposedTrade`. Both services already resolve proposed-order margin through
`RequiredMarginPort` and `RequiredMarginCurrencyNormalizer`, which must be
preserved and extended only where CFD-specific fact assembly requires it.

### Current position assembly

The shared `positions(...)` method in T0 and T1 currently calculates:

- market value as `abs(signedQuantity) * valuedPrice`;
- stop loss risk as adverse distance times stop quantity and FX rate;
- margin as the provider position margin converted by an account rate.

This is valid only for a generic quantity/price interpretation. It does not
apply a CFD contract multiplier or resolve the persisted CFD contract. CFD
positions therefore require an application-layer financial projection before
the generic `PositionSnapshot` is constructed.

### PAPER facts

`ModeAwareRiskFactsProvider` keeps PAPER local and does not call Broker Service.
Its `paperPosition(...)` currently exposes generic market value and margin facts
based on `Trade` prices and quantity. `CfdValuationService` is already injected
for PAPER account valuation refresh, but it currently provides a per-trade
valuation result and requires the reporting currency to match the account base
currency. The existing PAPER path therefore needs a CFD-specific risk projection
that reuses the validated `Trade` contract and read-only valuation facts without
mutating the account or calling Broker Service.

### LIVE facts

`ModeAwareRiskFactsProvider` routes LIVE through the authoritative V2 snapshot
when available and validates account completeness, freshness, and position
margin. Phase 6 maps provider equity, used/free margin, signed quantities, and
provider position identity. V2 positions do not carry cTrader payloads or the
full financial contract; Trading Core must resolve the provider-neutral contract
through the existing Market Data contract boundary and use Phase 6 reconciliation
authority rather than treating local persisted exposure as current LIVE truth.

### Financial contracts and valuation

`InstrumentFinancialContractResolver` resolves provider-neutral contracts from
Market Data. `Trade` persists a complete contract snapshot for synchronized CFD
positions. `CfdFinancialCalculator` already implements deterministic CFD
notional, gross PnL, stop-loss risk, and directed conversion operations. It
rejects invalid quantity, price, product type, quantity unit, precision, and
contract constraints.

`CfdValuationService` is a reusable read-only PAPER valuation boundary. It uses
conservative sell/buy marks based on signed quantity, validates freshness and
provenance, and rejects LIVE usage. Its current result exposes gross PnL but not
the full notional/stop-risk projection needed by Risk, so the plan must identify
the smallest reusable application-layer composition rather than duplicate CFD
formulas.

### Risk Domain

`RiskEvaluationContextBuilder`, `PositionSnapshot`, `ProposedTrade`, and
`DeterministicRiskEngine` contain no product or provider branching. They accept
currency-normalized monetary values and remain suitable as the target boundary.
No Risk Domain modification is justified by the current evidence.

### Compatibility and margin

`SettlementCompatibilityPolicy` still rejects CFD for both `CASH` and
`INVENTORY`, as required by ADR-082. This Story must not change that policy.
`RequiredMarginPort` provides amount, currency, source ID, source version, and
observation time. `RequiredMarginCurrencyNormalizer` already requires complete,
fresh valuation evidence and explicit currency handling. Current used margin
from LIVE account/position snapshots must not be wired into this proposed-order
margin port.

## Gaps

1. Proposed CFD notional and stop risk are not currently guaranteed to be
   derived from the validated contract at T0/T1.
2. Current position projections ignore CFD contract multipliers.
3. PAPER position facts do not yet expose a complete CFD-aware risk projection
   with explicit mark/conversion provenance and fail-closed behavior.
4. LIVE risk position facts need contract-aware projection while preserving Phase
   6 provider authority and reconciliation semantics.
5. The existing shared T0/T1 implementations duplicate assembly code, so the
   plan must avoid semantic drift while preserving their distinct freshness
   policies.
6. Existing risk fact records do not carry an explicit product type or complete
   financial contract, so any extension must remain provider-neutral and avoid
   leaking provider payloads into the Risk Domain.

## Constraints and Risks

- Do not add CFD/provider branches to `risk-domain`.
- Do not use symbol heuristics or infer a multiplier.
- Do not use `usedMargin` as proposed-order required margin.
- Do not silently assume identity FX conversion when currencies differ.
- Do not call Broker Service in the PAPER path.
- Do not promote local persisted positions to LIVE authority without the Phase 6
  authoritative snapshot/reconciliation boundary.
- Preserve current generic SPOT and `LINEAR_PERPETUAL` behavior.
- Preserve protection validation and fail-closed semantics for automated plans.
- Avoid database migrations, settlement changes, and provider writes.
- Potential implementation complexity: LIVE provider positions may require a
  provider-neutral contract lookup by instrument and a validated reconciliation
  relationship before their CFD financial projection can be accepted.

## Proposed Implementation Shape

The evidence supports an application-layer change in Trading Core, likely a
small shared CFD risk-facts/projection component reused by T0 and T1. It should:

1. identify CFD plans/positions from provider-neutral product metadata or the
   persisted contract;
2. resolve and validate `InstrumentFinancialContract`;
3. obtain the correct mode-authoritative mark and directed FX facts;
4. invoke `CfdFinancialCalculator` for notional and stop-risk calculations;
5. normalize all monetary results to the Risk reporting currency;
6. return unavailable outcomes for incomplete, stale, contradictory, or
   unreconciled facts;
7. leave existing non-CFD assembly and the Risk Domain unchanged.

The exact class/port decomposition belongs in the Implementation Plan after
human approval of this analysis. No implementation has been performed yet.

## Validation Strategy

- Add focused calculator-backed tests for long/short CFD notional and stop risk,
  including multiplier and cross-currency conversion.
- Add PAPER tests proving no Broker Service financial-fact calls and fail-closed
  behavior for incomplete valuation/contract evidence.
- Add LIVE tests proving provider-authoritative equity/position facts, contract
  resolution, reconciliation/freshness requirements, and separation of current
  used margin from proposed required margin.
- Add T0/T1 tests proving independent fact acquisition and identical CFD
  financial semantics under their respective freshness policies.
- Preserve and run existing SPOT/`LINEAR_PERPETUAL`, settlement compatibility,
  Risk Domain, Trading Core, and Broker Service regressions as applicable.
- Run `git diff --check` and review the complete diff for provider leakage,
  mutation, and authority inversion.

## Decision Status

Repository Analysis is complete and awaits explicit human approval. The next
permitted artifact is the Implementation Plan; implementation files must not be
modified until both the Repository Analysis and the subsequent Implementation
Plan have been explicitly approved.
