# Opportunity

## Purpose

This document defines the Opportunity business concept used throughout Trading
OS.

An Opportunity represents a potential trading situation identified during
market analysis.

It is the starting point of the deterministic trading decision pipeline.

---

# Definition

An Opportunity is an immutable business object describing a market situation
that may justify the creation of a Trade Plan.

An Opportunity represents analysis.

It is not a trading proposal.

It is not an authorization.

It is not an execution request.

---

# Owner

**Primary Domain**

- Market Intelligence

**Consumers**

- Market Intelligence
- Trading Core (indirectly through Trade Plans)

---

# Lifecycle

```text
Market Observation
        │
        ▼
Opportunity
        │
        ▼
Trade Plan
        │
        ▼
Risk Evaluation
```

An Opportunity is created when market analysis identifies a potentially
interesting trading situation.

It remains immutable and may result in zero, one or multiple Trade Plans,
depending on future architectural decisions.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Observation | Source information used to identify the opportunity |
| Trade Plan | Generated from an Opportunity |
| Market Snapshot | Represents the market state when the Opportunity was identified |

---

# Invariants

A valid Opportunity must:

- be immutable;
- describe a single market opportunity;
- be traceable throughout the decision pipeline;
- remain independent of broker implementations;
- reference the market context that produced it.

An Opportunity must never contain execution or authorization decisions.

---

# Responsibilities

An Opportunity is responsible for:

- representing a potential market opportunity;
- preserving the context of its discovery;
- serving as the input for Trade Plan generation.

An Opportunity is not responsible for:

- risk evaluation;
- trade authorization;
- trade execution;
- broker communication.

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
- Observation
- Trade Plan
- Market Snapshot