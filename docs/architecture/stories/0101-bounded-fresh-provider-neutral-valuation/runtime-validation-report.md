# Story 0101 - Runtime PAPER Validation Report

## Runtime

- Compose project: `trading-e2e-paper-final`
- Services: all application and PostgreSQL containers running
- Mode: PAPER
- Settlement: CASH
- Product: LINEAR_PERPETUAL
- Provider: KRAKEN simulator

## Identifiers

| Entity | Identifier |
|---|---|
| User | `story0101_1792018873` |
| BrokerAccount | `ec19fa55-f942-4a03-9fcb-017fec2e0296` |
| Account | `ff4aeb2a-2deb-4157-8ca1-1a5476ad76f1` |
| Challenge | `2c48e6af-2213-4294-bf8a-ddb84ba558eb` |
| Market | `09b6a477-dd67-4f92-908b-9e5bfe6495f4` (`BTC/USD`) |
| TradePlan | `6bd7589c-a3cb-3e7c-b5f9-2cff7594ce30` |
| T0 RiskEvaluation | `a7f96817-f3d0-4e34-873f-1ccb41deae94` |
| ExecutionIntent | `e70c4a7e-f171-41b1-8355-0296cfd558d4` |
| T1 RiskEvaluation | `cdda2831-43e3-4972-9330-5c2f44ab759b` |
| PAPER Trade | `339531b9-a000-4159-aa2f-143dd2cb2e9f` |

## Workflow Matrix

| Stage | Result | Evidence |
|---|---|---|
| PAPER account provisioning | PASS | Account created with USD 50,000 |
| Challenge start | PASS | Challenge `ACTIVE` |
| Manual TradePlan | PASS | BTC/USD LONG plan accepted |
| T0 Risk | PASS | `APPROVED`, evaluation `a7f96817...` |
| Expired execution guard | PASS | Expired request rejected before execution |
| T1 Risk | PASS | `APPROVED`, evaluation `cdda2831...` |
| PAPER entry | PASS | Execution `COMPLETED`, broker order `FILLED` |
| Open Trade | PASS | BTC/USD quantity `0.001` |
| Separate open-position MTM capture | NOT EXECUTED | Position was closed before an independent MTM snapshot |
| Close | PASS | Trade closed at 82,000 |
| Realized settlement | PASS | Account value increased to 50,000.44 USD |
| Challenge state | PASS | Challenge remained `ACTIVE`, current value 50,000.44 |
| Risk acknowledgment outbox | PASS | `DELIVERED`, one attempt |
| Challenge reevaluation outbox | PASS | `DELIVERED`, one attempt |

## Execution Evidence

- Entry price: `81,561`
- Exit price: `82,000`
- Quantity: `0.001`
- Realized P&L: `+0.44 USD`
- Execution status: `COMPLETED`
- Broker order status: `FILLED`
- Final account valuation: `FRESH`

## Verdict

```text
T0/T1, PAPER entry, close, settlement, Challenge state, and outbox delivery: PASS
Independent open-position MTM acceptance criterion: NOT EXECUTED
Overall Story runtime acceptance: PARTIAL, pending explicit MTM evidence
```

No live broker order or direct database mutation was performed. PostgreSQL was
queried read-only after the runtime flow to verify outbox delivery and T1
persistence.
