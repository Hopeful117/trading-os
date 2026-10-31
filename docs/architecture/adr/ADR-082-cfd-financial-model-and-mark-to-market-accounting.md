# ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting

## Status

Accepted - human approval recorded

## Date

2026-10-31

## Decision Type

CFD financial semantics, valuation, risk inputs, and implementation boundary

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-042 - Position Authority and Close Semantics by Execution Mode
- ADR-043 - Account Identity and Mode-Specific Risk Facts
- ADR-051 - Account Settlement Semantics and Instrument Product Identity
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-081 - cTrader Instrument Product Classification

## Related Stories

- Story 0084 - Account Settlement Semantics and Instrument Identity
- Story 0085 - Cash-Settled Derivative PAPER Settlement
- Story 0086 - PAPER Derivative Mark-to-Market and Equity Valuation
- Story 0167 - cTrader Market Data for FTMO Dry-Run

---

## 1. Context

ADR-081 establishes the EURUSD cTrader product identity as `CFD` with medium
classification confidence. ADR-051 deliberately keeps every `CFD` settlement
combination incompatible until a financial model is defined.

The current implementation is not sufficient for CFD accounting:

- `SettlementModel.CASH` is currently coupled to `LINEAR_PERPETUAL` in
  `PaperSettlementService` and is not a general CFD capability.
- `SettlementModel.INVENTORY` represents owned base/quote balances and is not
  truthful for a leveraged FX CFD.
- `Trade` stores price, quantity, and PnL fields but does not state the unit of
  quantity, contract multiplier, quote currency, or valuation provenance.
- calculator logic uses price distance times quantity without validating the
  financial contract or converting quote-currency results.
- the risk pipeline already has provider-neutral market valuation and required
  margin ports, but required margin is authoritative only when a complete fact
  is available.

FTMO and cTrader are integration context, not financial semantics. The model
must remain usable for any provider-neutral CFD instrument and must not encode
FTMO rules, cTrader payloads, or provider-specific volume terminology.

## 2. Decision

Trading OS SHALL represent a CFD as a cash-collateralized, mark-to-market
contract. CFD positions do not create owned base-asset inventory.

The account settlement model remains an account-level accounting choice and the
instrument product type remains `InstrumentProductType.CFD`. A future
implementation may make `CASH + CFD` compatible only after the CFD capability
defined here is implemented and validated. This ADR does not change the current
fail-closed compatibility matrix.

The first CFD capability SHALL provide:

```text
CFD_POSITION = signed underlying-equivalent quantity
CFD_NOTIONAL = abs(quantity) * reference price * contract multiplier
CFD_PNL = signed price movement * quantity * contract multiplier
CFD_EQUITY = cash balance + realized PnL + unrealized PnL - known costs
CFD_MARGIN = authoritative required/used margin fact
```

The Risk Domain remains provider- and product-agnostic. Trading Core assembles
complete provider-neutral facts before invoking deterministic risk evaluation.

## 3. Canonical Contract Semantics

### 3.1 Instrument financial metadata

Every supported CFD instrument must expose an explicit provider-neutral financial
contract containing at least:

- canonical instrument identity and product type;
- base/underlying currency and quote currency;
- quantity unit;
- positive contract multiplier, expressed in underlying units per quantity unit;
- price precision and quantity precision;
- minimum quantity and quantity increment;
- valuation and conversion provenance.

Missing, conflicting, or provider-only metadata fails closed. The model must not
infer a contract multiplier from a provider symbol, account environment, or
textual symbol name.

For EURUSD, the quantity supplied to the financial model must be the canonical
underlying-equivalent quantity produced by the market/broker normalization
boundary. cTrader volume conversion remains in the cTrader adapter.

### 3.2 Position direction

The position sign is authoritative:

```text
positive quantity -> long
negative quantity -> short
```

Existing entry/exit APIs may continue to expose side, but the financial model
must normalize side and quantity into one signed position representation before
valuation or PnL calculation. A CFD exit must reduce the existing exposure and
must not create an inventory balance or an unintended reversal.

### 3.3 Notional exposure

Notional exposure is calculated in quote currency:

```text
notionalQuote = abs(quantity) * price * contractMultiplier
```

Risk reporting converts notional to the account reporting currency using a
timestamped, complete `MarketValuationPort` fact. A missing conversion is a
context-unavailable failure, never a one-to-one assumption.

Notional is exposure information. It is not the amount debited from a base-asset
inventory balance and it is not a substitute for required margin.

### 3.4 Unrealized and realized PnL

For a position with signed quantity `q`, entry price `p0`, and mark/exit price
`p1`:

```text
grossPnlQuote = q * (p1 - p0) * contractMultiplier
```

This formula is deterministic for both long and short positions. It is then
converted from quote currency to account reporting currency using the valuation
fact captured for the same decision boundary.

Realized PnL is recorded only when quantity is reduced or closed. Unrealized PnL
is a valuation projection over the open position and must carry:

- mark price and price-use policy;
- valuation timestamp;
- conversion rate and conversion timestamp;
- source provenance and source version.

Dynamic marks must not mutate persisted position identity on every market tick.

### 3.5 Balance and equity

CFD entry and exit do not debit or credit base-asset inventory. Cash accounting
is separate from exposure accounting:

```text
cash balance = opening cash + realized PnL - settled costs
equity       = cash balance + unrealized PnL - unsettled known costs
```

The implementation must not use the current inventory balance path for CFD
entry, shorting, or exit. Account equity and valuation status must identify the
mark snapshot used. An incomplete mark makes CFD equity unavailable for a
decision that requires current equity.

### 3.6 Stop-loss monetary risk

For a protected position, gross adverse stop distance is:

```text
long  distance = max(entryPrice - stopPrice, 0)
short distance = max(stopPrice - entryPrice, 0)
stopRiskQuote = distance * abs(quantity) * contractMultiplier
```

The risk context converts this value to the reporting currency and adds only
explicitly supported, known costs. A missing stop, contract multiplier, or FX
conversion fails closed for automated/protected plans. Manual-plan behavior
continues to follow existing policy and must not be silently broadened here.

### 3.7 Margin and leverage

Required and used margin are factual account/broker inputs, not risk-engine
inferences. Trading Core SHALL use an authoritative `RequiredMarginPort` or
mode-correct equivalent containing amount, currency, timestamp, source identity,
and source version.

The system must not derive margin as `notional / leverage` unless a separate
approved decision makes the leverage and margin rule authoritative and complete.
Missing or stale margin fails closed. Leverage may be reported as an observed
ratio, but it does not authorize an order or replace margin facts.

### 3.8 Costs and financing

Commission, spread, slippage, swap, and financing are economically distinct
facts. They must not be silently represented as zero when the source indicates
that they are unknown or applicable.

The first CFD implementation may support only the cost categories for which a
complete deterministic source and persistence contract exist. Unsupported or
ambiguous non-zero costs make the relevant settlement or risk context
unavailable. FTMO daily loss, maximum loss, and challenge rules remain policy
configuration, not CFD accounting semantics.

## 4. Authority and Boundaries

The authority chain remains:

```text
cTrader/provider facts
    -> Broker Service provider-neutral contract facts
Market Data
    -> canonical prices, contract metadata, and FX valuation facts
Trading Core
    -> account linkage, settlement, cash/equity projection, orchestration
Risk Domain
    -> deterministic interpretation and decision
```

Market Intelligence may propose a plan but cannot define CFD financial facts or
override margin, valuation, or risk outcomes.

For `LIVE`, the provider remains authoritative for current positions, balances,
margin, costs, and reconciliation facts as exposed through Broker Service. For
`PAPER`, Trading Core is authoritative for local cash, positions, fills, and
realized state; Market Data supplies current marks and conversions.

No provider payload or cTrader field name may cross the CFD domain boundary.

## 5. Implementation Plan

Implementation must proceed in this order and remain fail-closed until the
complete path exists:

1. Add provider-neutral contract metadata and validation at the Market Data /
   Trading Core boundary. Preserve cTrader-specific quantity conversion inside
   the cTrader adapter.
2. Introduce a small pure financial calculator for CFD notional, PnL, stop risk,
   and currency conversion. Add explicit rounding/precision policies and reject
   invalid metadata.
3. Extend the Trade/account financial projection additively with product type,
   quote currency, contract multiplier, and valuation/cost provenance where the
   existing persistence model cannot represent the facts truthfully.
4. Add a mode-correct CFD valuation/facts adapter. Reuse the existing
   `MarketValuationPort`, `RiskFactsProvider`, and `RequiredMarginPort` concepts;
   do not add provider branching to the Risk Domain.
5. Implement cash-settled CFD PAPER entry, mark-to-market equity, and full exit
   settlement as one atomic local financial transaction. Do not reuse inventory
   balance mutation.
6. Add LIVE fact mapping and reconciliation without making Trading Core the
   authority for LIVE positions or balances.
7. Add risk integration for notional, stop risk, equity, margin, and currency
   conversion. Keep missing facts as `CONTEXT_UNAVAILABLE`.
8. Only after the above passes, propose the deliberate compatibility change for
   `CASH + CFD` and update ADR-051/implementation tests. No compatibility change
   is authorized by this proposed ADR.

## 6. Validation Requirements

The implementation must prove:

- EURUSD CFD identity is preserved without cTrader types in domain contracts.
- Long and short PnL are equal and opposite for the same price movement.
- Notional uses canonical quantity and contract multiplier.
- Quote-to-account conversion is required when currencies differ.
- Missing metadata, mark, conversion, margin, or cost facts fail closed.
- CFD entry/exit never creates or consumes base-asset inventory.
- Mark-to-market equity survives restart from persisted cash and exposure state.
- Realized PnL is recorded once on a reducing fill and is idempotent.
- Stop-loss risk is deterministic and uses conservative, timestamped marks.
- Required margin is authoritative and never defaulted to zero.
- `INVENTORY + CFD` remains rejected during the entire implementation.
- `CASH + CFD` remains rejected until a separately approved compatibility change.
- LIVE provider authority and reconciliation are unchanged.
- PAPER execution does not call Broker Service for financial or position facts.
- `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default.
- Existing SPOT and LINEAR_PERPETUAL settlement tests remain green.

## 7. Scope Boundary

This ADR does not implement or decide:

- cTrader order transport or volume conversion;
- FTMO challenge rules, daily loss, or maximum loss policy;
- liquidation, stop-out, or automatic protection triggering;
- partial fills, pending orders, or latency simulation;
- a universal Position aggregate;
- provider-specific financing schedules;
- historical backfill of existing CFD rows;
- enabling `CASH + CFD` compatibility;
- broker mutations or live trading authorization.

## 8. Consequences

### Positive

- CFD economics are explicit without coupling Trading OS to cTrader or FTMO.
- Inventory settlement cannot accidentally represent leveraged FX exposure.
- Risk receives deterministic monetary facts in one reporting currency.
- Margin remains authoritative rather than inferred from an untrusted leverage
  assumption.
- Unsupported costs and valuation gaps remain visible through fail-closed paths.
- Existing LIVE/PAPER authority boundaries remain intact.

### Accepted Costs

- Contract metadata and valuation provenance require additive domain/persistence
  work.
- Existing generic calculator methods need a product-aware boundary.
- CFD execution remains unavailable until the full capability and compatibility
  decision are approved.
- Existing `PaperSettlementService` must be split or delegated rather than
  growing another product-specific branch.

## 9. Final Decision Summary

```text
CFD_ACCOUNTING = CASH_COLLATERALIZED_MARK_TO_MARKET
CFD_INVENTORY_BALANCES = NO
CFD_QUANTITY = SIGNED_UNDERLYING_EQUIVALENT_QUANTITY
CFD_NOTIONAL = ABS(quantity) * price * contract_multiplier
CFD_PNL = quantity * price_delta * contract_multiplier
CFD_CURRENCY_CONVERSION = EXPLICIT_TIMESTAMPED_VALUATION_REQUIRED
CFD_MARGIN = AUTHORITATIVE_FACT_REQUIRED
CFD_UNKNOWN_COSTS = FAIL_CLOSED
CFD_RISK_DOMAIN_CHANGES = NO
INVENTORY_PLUS_CFD = INCOMPATIBLE
CASH_PLUS_CFD = INCOMPATIBLE_UNTIL_SEPARATE_APPROVAL
FTMO_RULES_IN_CORE_MODEL = NO
CTRADER_TYPES_IN_DOMAIN_MODEL = NO
ADR_APPROVED_BY_HUMAN = YES
ADR_APPROVED_ON = 2026-10-31
PRODUCTION_CODE_CHANGED = NO
DATABASE_MIGRATION_CREATED = NO
ENGINEERING_STORY_CREATED = NO
```
