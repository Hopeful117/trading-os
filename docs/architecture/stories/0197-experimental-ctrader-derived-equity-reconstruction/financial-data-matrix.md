# Financial Data Matrix

| Fact | Source | Unit | Currency | Temporal evidence | Guarantee |
|---|---|---|---|---|---|
| Balance | cTrader `ProtoOATrader.balance` | account money scale | deposit asset mapped to valuation asset | trader observation timestamp | provider balance fact |
| Net unrealized PnL | cTrader `ProtoOAPositionUnrealizedPnl.netUnrealizedPnl` | account money scale | valuation asset | no provider mark timestamp preserved | position-level provider fact |
| Position identity | cTrader position id | identifier | n/a | position update timestamp when supplied | required for matching |
| Balance version | cTrader `ProtoOATrader.balanceVersion` when supplied | integer version | n/a | version comparison only | optional coherence signal |
| Candidate equity | experiment output | `BigDecimal` | valuation asset | observation collection window | derived and unverified |

## Known Gaps

- cTrader does not provide a documented atomic snapshot guarantee for the
  balance, positions and PnL responses used by this experiment.
- The PnL response does not provide a preserved mark timestamp in the current
  observation model.
- The experiment does not establish whether balance already includes any of
  the position-level cost terms.
- The candidate is not an FTMO equity fact and is not admitted to the risk
  engine.
