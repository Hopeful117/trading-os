# Code Review - Story 0181

## Findings

The initial independent-review findings were addressed:

- Exit instrument is now required to match the target trade symbol.
- Entry fills are now required to match the execution intent quantity.
- Normal PAPER finalization routes identified CFD instruments to
  `PaperCfdSettlementService` before the legacy settlement service.

## Residual Risks

- Restart reconstruction and durable concurrent replay require integration tests;
  focused in-memory tests are not sufficient evidence for those acceptance
  criteria.

## Scope Check

- `SettlementCompatibilityPolicy` is unchanged.
- No LIVE broker/provider mutation path was added.
- No Risk Domain or broker-neutral contract boundary was changed.
- Existing SPOT and `LINEAR_PERPETUAL` settlement code was not modified.
