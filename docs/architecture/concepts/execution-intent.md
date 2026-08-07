# Execution Intent

## Purpose

This document defines the Execution Intent business concept used throughout
Trading OS.

An Execution Intent represents an approved intention to execute a trade after
successful deterministic risk evaluation.

It acts as the contract between the deterministic business domains and the
Broker Service.

---

# Definition

An Execution Intent is an immutable business object representing an authorized
request to execute a trade.

It is created only after a Trade Plan has successfully passed deterministic
risk evaluation.

An Execution Intent authorizes execution.

It does not perform execution.

---

# Owner

**Primary Domain**

- Trading Core

**Consumers**

- Broker Service

---

# Lifecycle

```text
Trade Plan
        │
        ▼
Risk Evaluation
        │
        ▼
Execution Intent
        │
        ▼
Broker Service
        │
        ▼
Position
```

An Execution Intent is created once and remains immutable throughout its
lifecycle.

Execution results are represented by new business objects rather than modifying
the original intent.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Trade Plan | Original trading proposal |
| Risk Evaluation | Authorization required before creation |
| Position | May be created after successful execution |
| Account | Defines the execution context |

---

# Invariants

A valid Execution Intent must:

- be immutable;
- reference exactly one authorized Trade Plan;
- reference the Risk Evaluation that approved it;
- contain sufficient information for broker execution;
- remain independent of broker-specific implementations.

An Execution Intent must never bypass deterministic risk evaluation.

---

# Responsibilities

An Execution Intent is responsible for:

- representing an authorized execution request;
- preserving execution traceability;
- carrying execution parameters to the Broker Service.

An Execution Intent is not responsible for:

- market analysis;
- risk evaluation;
- broker communication;
- execution monitoring.

---

# Related ADRs

- ADR-028
- ADR-029
- ADR-030
- ADR-031

---

# Related Stories

- Story 0003

---

# References

- Trading Core
- Broker Service
- Trade Plan
- Risk Evaluation
- Position