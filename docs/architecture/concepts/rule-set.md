# Rule Set

## Purpose

This document defines the Rule Set business concept used throughout Trading
OS.

A Rule Set represents the deterministic collection of business and risk rules
used to evaluate Trade Plans.

It guarantees that every Risk Evaluation is reproducible, traceable and
auditable.

---

# Definition

A Rule Set is an immutable business object representing a versioned collection
of deterministic validation rules.

It defines the exact rules applied during a Risk Evaluation.

A Rule Set contains business logic configuration.

It does not perform the evaluation itself.

---

# Owner

**Primary Domain**

- Risk Domain

**Consumers**

- Risk Domain
- Trading Core

---

# Lifecycle

```text
Rule Definition
        │
        ▼
Rule Set Version
        │
        ▼
Risk Evaluation
        │
        ▼
Audit & Traceability
```

A Rule Set is versioned.

Once published, a Rule Set remains immutable.

New business rules require a new Rule Set version.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Risk Evaluation | Executed using a specific Rule Set |
| Trade Plan | Evaluated against the Rule Set |
| Account | Provides contextual information for rule evaluation |
| Portfolio | Used to evaluate portfolio-level constraints |

---

# Invariants

A valid Rule Set must:

- be immutable;
- have a unique version;
- contain deterministic business rules;
- produce reproducible evaluation results;
- remain fully traceable.

A Rule Set must never change after publication.

Any modification requires a new version.

---

# Responsibilities

A Rule Set is responsible for:

- defining deterministic validation rules;
- preserving rule versioning;
- supporting reproducible evaluations;
- enabling complete auditability.

A Rule Set is not responsible for:

- executing broker operations;
- market analysis;
- trade planning;
- managing trading accounts.

---

# Related ADRs

- ADR-028
- ADR-029
- ADR-031

---

# Related Stories

- Story 0001
- Story 0003

---

# References

- Risk Domain
- Risk Evaluation
- Trade Plan
- Account
- Portfolio