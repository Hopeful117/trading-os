# ADR-078 - PAPER and LIVE Execution Boundary

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-28 |
| **Decision Makers** | Project Owner, Trading OS Architecture |
| **Related ADRs** | ADR-029, ADR-042, ADR-043, ADR-071 |
| **Related Stories** | Story 0171 |

---

## 1. Context

Trading OS uses `ExecutionMode.PAPER` and `ExecutionMode.LIVE` in account,
execution, position, and Risk Facts routing. The values have sometimes been
interpreted as economic classifications instead of execution-boundary
classifications.

The concrete failure was a cTrader dry run. The Trading OS account was `LIVE`,
the provider was cTrader, and the provider account was economically
DEMO/SIMULATED. The dry run was nevertheless directed through the PAPER
journey because provider demo status was treated as Trading OS PAPER status.

This interpretation is unsafe. A connected provider account crosses the
external broker boundary even when the provider account is economically
simulated.

---

## 2. Decision

`ExecutionMode` describes whether trading remains inside Trading OS or crosses
an external broker/provider boundary.

### PAPER

```text
Execution is simulated entirely inside Trading OS.
Trading instructions never leave Trading OS toward a broker or provider.
Orders, fills, settlement, and position lifecycle remain inside the local
Trading OS simulated execution boundary.
```

### LIVE

```text
The account participates in the external broker/provider execution boundary.
Authoritative broker facts may come from the external provider.
Trading instructions may leave Trading OS only after the existing human,
TradePlan, deterministic Risk, Challenge, capability, freshness, and safety
gates pass and execution is explicitly enabled.
```

The canonical classification rule is:

```text
Does the trade/order remain entirely inside Trading OS?

YES -> PAPER
NO, an external broker/provider boundary is involved -> LIVE
```

Therefore:

```text
LIVE != REAL_MONEY
PAPER != ALL_DEMO_ACCOUNTS
```

---

## 3. Separate Dimensions

The following concepts remain independent:

```text
Trading OS ExecutionMode
provider technical transport environment
provider-reported isLive
provider account classification
economic environment
Challenge phase
execution permission
```

For the FTMO/cTrader integration:

```text
FTMO demo/free/challenge/verification/funded account
+ connected cTrader provider boundary
= Trading OS ExecutionMode.LIVE
```

An economically simulated cTrader account must not be converted to PAPER. The
cTrader `isLive` field remains provider-owned data and must not be mapped
directly to Trading OS `ExecutionMode`.

---

## 4. Safety Invariants

- PAPER execution, exit, cancellation, settlement, and position lifecycle must
  remain local to Trading Core.
- PAPER must never call cTrader, Kraken, or a future external broker mutation
  adapter.
- LIVE does not authorize execution by itself.
- LIVE provider-backed facts remain authoritative where the provider owns them.
- PAPER facts remain authoritative in the local Trading OS PAPER domain.
- Risk interpretation remains mode-independent inside the Risk Domain.
- Provider-specific environment interpretation remains inside the provider
  adapter and broker-neutral translation boundary.
- Unknown, stale, incomplete, or contradictory provider facts remain fail-closed
  under the existing execution and Risk rules.

---

## 5. Consequences

Positive consequences:

- A cTrader demo account follows the provider-backed LIVE data/execution path
  without implying real-money trading.
- The local PAPER path remains a strong no-external-mutation boundary.
- Risk Facts authority follows the execution boundary rather than economic
  labels.
- Agents and operators have one deterministic rule for selecting the product
  journey.
- Existing persisted enum values and account records remain compatible.

Trade-offs:

- A provider demo account can be `LIVE` in Trading OS while execution remains
  disabled by safety configuration.
- UI and runbooks must explain execution boundary and economic environment as
  separate fields.
- Challenge monitoring scope for LIVE accounts remains a separate product
  decision and is not changed by this ADR.

---

## 6. Rejected Alternatives

### `PAPER = all economically simulated accounts`

Rejected because a provider-hosted demo account still crosses the external
broker boundary and receives authoritative facts from that provider.

### `LIVE = real-money accounts only`

Rejected because it conflates execution routing and economic environment, and
misclassifies FTMO/cTrader demo or Challenge accounts.

### `provider isLive=false -> ExecutionMode.PAPER`

Rejected because provider `isLive` is a provider fact, not a Trading OS routing
decision. Some provider/business programs can also have provider-specific
semantic exceptions.

### Add `DEMO`, `CHALLENGE`, or `FUNDED` ExecutionMode values

Rejected because those are provider/business classifications, not additional
Trading OS execution boundaries.

---

## 7. Scope Boundary

This ADR does not authorize:

- external order execution;
- cTrader account binding or runtime data changes;
- changes to Risk formulas or Challenge rules;
- changes to provider request budgets or snapshot cooldowns;
- changes to Market Data, Market State, Scanner, Thesis, Opportunity, or
  TradePlan semantics;
- automatic LIVE execution or removal of human authorization.
