# Account

## Purpose

This document defines the Account business concept used throughout Trading OS.

An Account represents the trading account managed by the platform.

It provides the financial context required for deterministic trading decisions
and broker interactions.

---

# Definition

An Account is a business object representing a trading account together with
its configuration, balances and trading constraints.

An Account is the authoritative representation of the trader's financial
environment.

It is not a broker connection.

It is not a portfolio.

---

# Owner

**Primary Domain**

- Trading Core

**Consumers**

- Risk Domain
- Broker Service
- Market Intelligence

---

# Lifecycle

```text
Broker Account
        │
        ▼
Account Synchronization
        │
        ▼
Account
        │
        ▼
Risk Evaluation
        │
        ▼
Trade Execution
```

The Account is synchronized with external brokers while remaining the
authoritative business representation inside Trading OS.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Portfolio | Belongs to an Account |
| Position | Managed within an Account |
| Trade Plan | Evaluated against Account constraints |
| Risk Evaluation | Uses Account information |
| Execution Intent | Executed on behalf of an Account |

---

# Invariants

A valid Account must:

- have a unique identifier;
- represent a single trading account;
- maintain consistent financial information;
- remain synchronized with the external broker;
- preserve auditability.

An Account must never contain broker-specific business logic.

---

# Responsibilities

An Account is responsible for:

- representing the trading account;
- exposing balances and equity;
- exposing trading configuration;
- providing financial context for risk evaluation.

An Account is not responsible for:

- market analysis;
- trade planning;
- risk evaluation;
- broker communication.

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
- Portfolio
- Position
- Trade Plan
- Risk Evaluation
```