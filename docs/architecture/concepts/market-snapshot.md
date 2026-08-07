# Market Snapshot

## Purpose

This document defines the Market Snapshot business concept used throughout
Trading OS.

A Market Snapshot represents the state of the market at a specific point in
time.

It provides the deterministic market context required for analysis, trade
planning and risk evaluation.

---

# Definition

A Market Snapshot is an immutable business object representing a consistent
view of the market captured at a specific instant.

It aggregates the market information required by downstream business domains.

A Market Snapshot is factual.

It does not contain analysis.

It does not contain trading decisions.

> Naming note: In ADR-028/ADR-031, `MarketSnapshot` denotes a financial
> valuation snapshot assembled by Trading Core for Risk evaluation. This concept
> document describes the separate market price state owned by Market Data
> Service (the ADR-019 `MarketPriceSnapshot` view), not that risk snapshot.

---

# Owner

**Primary Domain**

- Market Data Service

**Consumers**

- Market Intelligence
- Risk Domain
- Trading Core
- AI Engine *(planned)*

---

# Lifecycle

```text
Market Data
        │
        ▼
Market Snapshot
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

A Market Snapshot is created from public market information.

It remains immutable and provides a reproducible market context for subsequent
business decisions.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Observation | Created from a Market Snapshot |
| Opportunity | Identified using one or more Market Snapshots |
| Trade Plan | References the originating market context |
| Risk Evaluation | May validate decisions using the captured market state |

---

# Invariants

A valid Market Snapshot must:

- be immutable;
- represent a single point in time;
- contain a consistent market view;
- be fully traceable;
- remain independent of broker implementations.

A Market Snapshot must never contain subjective interpretation or business
decisions.

---

# Responsibilities

A Market Snapshot is responsible for:

- capturing market state;
- providing deterministic market context;
- supporting reproducible analysis;
- preserving historical market information.

A Market Snapshot is not responsible for:

- market analysis;
- opportunity detection;
- risk evaluation;
- trade execution.

---

# Related ADRs

- ADR-006
- ADR-026
- ADR-027

---

# Related Stories

- Story 0001
- Story 0002

---

# References

- Market Data Service
- Observation
- Opportunity
- Trade Plan
- Risk Evaluation