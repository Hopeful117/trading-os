# Observation

## Purpose

This document defines the Observation business concept used throughout Trading
OS.

An Observation represents a factual piece of market information collected by
Trading OS.

Observations are the foundation of market analysis and the primary input used
to identify trading opportunities.

---

# Definition

An Observation is an immutable business object representing an objective market
fact captured at a specific point in time.

An Observation contains data.

It does not contain interpretation.

It does not represent a trading decision.

---

# Owner

**Primary Domain**

- Market Intelligence

**Consumers**

- Market Intelligence
- AI Engine *(planned)*

---

# Lifecycle

```text
Market Data
        │
        ▼
Observation
        │
        ▼
Opportunity
        │
        ▼
Trade Plan
```

Observations are created from raw market information.

They remain immutable and may contribute to one or more Opportunities.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Market Snapshot | Captured within a specific market context |
| Opportunity | Generated from one or more Observations |
| Trade Plan | Indirectly derived from Observations |

---

# Invariants

A valid Observation must:

- be immutable;
- represent objective market information;
- be timestamped;
- remain traceable;
- remain independent of trading decisions.

Observations must never contain subjective interpretations or execution
decisions.

---

# Responsibilities

An Observation is responsible for:

- representing factual market information;
- preserving the observed market state;
- providing reliable input for market analysis.

An Observation is not responsible for:

- identifying opportunities;
- evaluating risk;
- authorizing trades;
- executing trades.

---

# Related ADRs

- ADR-026
- ADR-027

---

# Related Stories

- Story 0001
- Story 0002

---

# References

- Market Intelligence
- Market Snapshot
- Opportunity
- Trade Plan