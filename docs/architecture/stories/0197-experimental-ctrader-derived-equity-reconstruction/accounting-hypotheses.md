# Accounting Hypotheses

## Implemented Candidate

```text
candidate equity = balance + sum(net unrealized PnL)
```

This is `BALANCE_PLUS_NET_UNREALIZED_PNL_V1`. It is a reproducible numerical
candidate, not a validated account-equity definition.

## Explicitly Excluded

The calculation does not add swap, commission or mirroring commission. Their
presence in the provider observation is recorded as an uncertainty because
adding them could double count costs already reflected in net PnL or balance.

No alternative formula is claimed until provider semantics and an independent
reference value establish the treatment of each term.

## Safety Boundary

The result remains `UNVERIFIED`, is not exposed as `EquityFact`, and cannot
feed FTMO policy evaluation, T0, T1 or order execution.
