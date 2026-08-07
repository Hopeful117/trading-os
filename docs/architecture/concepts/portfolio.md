# Portfolio

## Purpose

This document defines the Portfolio business concept used throughout Trading
OS.

A Portfolio represents the complete trading state associated with an Account.

It provides the consolidated view required for deterministic risk evaluation
and portfolio management.

---

# Definition

A Portfolio is a business object representing the collection of positions,
balances and portfolio-level metrics for a trading account.

It reflects the trader's current market exposure.

A Portfolio is not an Account.

A Portfolio is not a broker.

---

# Owner

**Primary Domain**

- Trading Core

**Consumers**

- Risk Domain
- Market Intelligence
- Broker Service

---

# Lifecycle

```text
Account
        │
        ▼
Portfolio Synchronization
        │
        ▼
Portfolio
        │
        ▼
Risk Evaluation
        │
        ▼
Portfolio Update
```

The Portfolio evolves as positions are opened, modified and closed.

Each state represents the authoritative portfolio view at a given moment.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Account | Owns the Portfolio |
| Position | Portfolio contains Positions |
| Risk Evaluation | Uses Portfolio exposure |
| Trade Plan | Evaluated against Portfolio constraints |

---

# Invariants

A valid Portfolio must:

- belong to exactly one Account;
- accurately represent current exposure;
- remain synchronized with broker information;
- provide deterministic input for risk evaluation;
- preserve consistency across all positions.

A Portfolio must never contain broker-specific business logic.

---

# Responsibilities

A Portfolio is responsible for:

- representing current market exposure;
- aggregating open positions;
- exposing portfolio-level metrics;
- supporting deterministic risk evaluation.

A Portfolio is not responsible for:

- market analysis;
- trade planning;
- broker communication;
- trade execution.

---

# Related ADRs

- ADR-028
- ADR-029
- ADR-030
- ADR-031

---

# Related Stories

- Story 0001
- Story 0003

---

# References

- Trading Core
- Account
- Position
- Risk Evaluation
- Trade Plan