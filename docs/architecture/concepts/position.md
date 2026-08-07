# Position

## Purpose

This document defines the Position business concept used throughout Trading OS.

A Position represents an active or historical market exposure resulting from
the execution of a trade.

It is the authoritative representation of an executed trade within the
platform.

---

# Definition

A Position is a business object representing an executed market position held
within a trading account.

It reflects the current lifecycle and state of an executed trade.

The Broker Service provides broker position facts and snapshots; the business
representation of a Position belongs to Trading Core.

A Position is not a Trade Plan.

A Position is not an Execution Intent.

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
Execution Intent
        │
        ▼
Broker Execution
        │
        ▼
Open Position
        │
        ▼
Position Updates
        │
        ▼
Closed Position
```

A Position is created after successful broker execution.

Its state evolves until the position is fully closed.

Historical Positions remain available for analysis and auditing.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Execution Intent | Originates the Position |
| Portfolio | Contains one or more Positions |
| Account | Owns the Position |
| Risk Evaluation | Uses Position exposure during evaluation |

---

# Invariants

A valid Position must:

- belong to exactly one Account;
- belong to exactly one Portfolio;
- reference its originating Execution Intent;
- accurately represent the broker execution state;
- preserve complete execution history.

A Position must never be created without a successful execution.

---

# Responsibilities

A Position is responsible for:

- representing an executed trade;
- tracking the position lifecycle;
- exposing current exposure;
- supporting portfolio management;
- providing historical execution information.

A Position is not responsible for:

- market analysis;
- trade planning;
- risk authorization;
- broker connectivity.

---

# Related ADRs

- ADR-029
- ADR-030
- ADR-031

---

# Related Stories

- Story 0003

---

# References

- Broker Service
- Trading Core
- Account
- Portfolio
- Execution Intent
- Risk Evaluation