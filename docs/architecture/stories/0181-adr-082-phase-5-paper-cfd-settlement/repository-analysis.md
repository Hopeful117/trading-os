# Repository Analysis - Story 0181

## Repository State

- `REMOTE_MAIN`: `926d051c7ba57a726257c8ec0e2bd2ed18362736`
- `LOCAL_BASE`: `926d051c7ba57a726257c8ec0e2bd2ed18362736`
- Current branch: `main`
- Worktree: clean before Story artifacts
- Latest merged CFD commits:
  - Story 0178: `92c6ebd7` canonical CFD financial contract
  - ADR-082 Phase 2 calculator: `3c4996eb`
  - Story 0179: `5cbf3669` persisted CFD contract projection
  - Story 0180: `c2559fad` read-only CFD valuation boundary

The repository contains Stories 0178, 0179, and 0180. Their frontmatter/status
metadata still reflects earlier workflow states in places, but Git history and
the current source confirm that the corresponding implementation commits are
merged into `main`. No material semantic contradiction with ADR-082 was found.

DevLog applicability was checked for ADR-082 history and prior implementation
constraints, but the service returned `Authentication is required`. This
analysis therefore relies on the current repository, ADR-082, and merged Story
artifacts.

## Authoritative Context

ADR-082 requires CFD to remain a cash-collateralized mark-to-market contract:
signed underlying-equivalent quantity, no underlying inventory, explicit
contract multiplier, deterministic PnL, explicit FX conversion, authoritative
margin, and fail-closed missing facts. ADR-082 Phase 5 is specifically PAPER
entry, MTM equity, and full exit as one atomic local transaction. It explicitly
requires `CASH + CFD` to remain incompatible until a later approval.

Story 0178 provides `InstrumentFinancialContract` and validation. Story 0179
persisted contract fields on `Trade` and added migration `V47`. Story 0180 added
`CfdValuationService`, `MarketValuationPort`, and
`ReadOnlyMarketValuationPort`; the service reconstructs a CFD contract from
persisted `Trade` fields and uses conservative marks plus explicit conversion
legs without mutating state.

## Existing Settlement Boundary

`PaperSettlementService` currently owns PAPER settlement for both inventory
SPOT and cash-settled `LINEAR_PERPETUAL`. It calls
`SettlementCompatibilityPolicy` before settlement, and its cash derivative
branch currently tests the equivalent of `SettlementModel.CASH` plus
`InstrumentProductType.LINEAR_PERPETUAL`. It mutates `Account.realizedBalance`
and `Account.equity`, but it creates the position through the shared
`updatePosition` path. CFD must not be added as another condition to this
branch. ADR-082 requires delegation or separation.

The current compatibility matrix is correct and must remain unchanged:

- `INVENTORY + SPOT`: compatible
- `CASH + LINEAR_PERPETUAL`: compatible
- `INVENTORY + CFD`: rejected
- `CASH + CFD`: rejected

## Existing Persistence and Idempotency

`Trade` persists product type, currencies, quantity unit, multiplier,
precision/constraints, contract provenance, signed-compatible quantity, entry
price, status, and account linkage. Migration `V47` enforces all-or-none CFD
contract projection semantics while preserving legacy null rows.

`Account` persists balances, equity, realized balance, valuation status, and
valuation timestamps. `AccountBalance` is the inventory/balance collection;
CFD settlement must not add or subtract a base-asset balance.

`TradeExecutionProvenanceStore` and migration `V39` provide a durable unique
constraint on `(broker_order_id, fill_id)`. The current settlement service uses
this lookup for duplicate entry settlement, but does not record exit settlement
provenance before returning. Phase 5 must verify whether the existing boundary
can safely protect exit replay and extend it minimally if needed. The unique
constraint and transaction boundary are the likely basis for exactly-once
settlement; concurrent/replay behavior must be tested rather than assumed.

## Existing Valuation

`CfdValuationService` is PAPER-only and rejects LIVE context. It reconstructs
the persisted financial contract, selects conservative sell for LONG and
conservative buy for SHORT, calls the read-only valuation port, validates
freshness/completeness/provenance, calculates quote PnL through
`CfdFinancialCalculator`, and performs explicit directed FX conversion.

`PaperDerivativeAccountValuationService` is explicitly for
`LINEAR_PERPETUAL`. It persists current marks and account valuation state and
must not be reused as the CFD valuation implementation without a product-safe
boundary. Existing `AccountEquityService` is a dashboard selector rather than
the CFD accounting projection.

## Entry and Exit Integration Risk

The normal `ExecutionFinalizationStep` invokes `PaperSettlementService` for
PAPER acknowledgements. Since global compatibility must remain closed, the new
CFD accounting capability cannot be enabled by routing normal execution through
the compatibility matrix. The Story therefore needs a dedicated internal
accounting boundary whose invocation is explicit and whose LIVE use is rejected;
the implementation plan must define how it associates an actual PAPER fill and
execution provenance without globally authorizing CFD execution.

`ExecutionParameters` carries instrument, side, quantity, and order data but
not a contract snapshot. `MarketResponse` exposes the provider-neutral contract
metadata needed to construct one, so entry must resolve exactly one complete CFD
market contract and persist it on the new exposure. The implementation must not
infer missing metadata from the symbol or provider.

## Required Design Decisions for Planning

1. Choose the smallest dedicated CFD PAPER accounting component and its narrow
   command/result contract, preserving the existing PaperSettlementService
   responsibility for SPOT and LINEAR_PERPETUAL.
2. Define how the component is called internally without changing global
   compatibility or normal external execution authorization.
3. Reuse `CfdValuationService` for MTM while keeping projection read-only with
   respect to trade identity/current financial contract.
4. Extend execution provenance minimally so both entry and full-exit fills are
   durable and replay-safe, including database uniqueness behavior.
5. Define the atomic transaction boundary for account/trade/provenance state and
   prove rollback behavior.
6. Define how account cash/equity projection represents unsupported costs and
   unavailable marks without retaining a stale value as current.

## Stop Conditions Checked

- Story 0179 is present and its implementation commit is merged.
- Story 0180 is present and its implementation commit is merged.
- Persisted CFD fields are sufficient to reconstruct the canonical contract.
- CFD exposure is distinguishable from inventory through `productType` and
  `quantityUnit`.
- No requirement currently forces `CASH + CFD` compatibility, LIVE authority,
  margin inference, or Risk Domain modification.
- Exactly-once exit settlement is not yet proven by the current implementation;
  this is an implementation risk to resolve in the approved plan, not a reason
  to invent a parallel idempotency system.

## Recommendation

Proceed to Implementation Planning only after human approval of this analysis.
The plan must remain narrow, must preserve the closed compatibility matrix, and
must stop if the existing provenance/transaction model cannot safely guarantee
exactly-once full-exit accounting.
