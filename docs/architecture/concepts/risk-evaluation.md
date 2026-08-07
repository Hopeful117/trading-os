# Risk Evaluation

## Purpose

This document defines the Risk Evaluation business concept used throughout
Trading OS.

A Risk Evaluation is the authoritative result produced by the Risk Domain after
evaluating a Trade Plan against the deterministic risk rules.

It determines whether a Trade Plan may proceed to the execution phase.

---

# Definition

A Risk Evaluation is an immutable business object representing the outcome of a
deterministic risk assessment.

It records the decision made by the Risk Domain together with the context used
to produce that decision.

A Risk Evaluation is an authorization decision.

It is not a Trade Plan.

It is not an execution request.

---

# Owner

**Primary Domain**

- Risk Domain

**Consumers**

- Trading Core
- Broker Service (indirectly through Execution Intent)

---

# Lifecycle

```text
Trade Plan
        │
        ▼
Risk Evaluation
        │
        ├────────► Rejected
        │
        ├────────► Approved with warnings
        │
        ▼
Approved
        │
        ▼
Execution Intent
```

A Risk Evaluation is produced once for a specific evaluation context.

It remains immutable for audit and traceability purposes.

---

# Relationships

| Concept | Relationship |
|----------|--------------|
| Trade Plan | Input evaluated by the Risk Domain |
| Risk Evaluation Context | Snapshot used during evaluation |
| Rule Set | Defines the deterministic rules applied |
| Execution Intent | Created after a successful authorization |

---

# Invariants

A valid Risk Evaluation must:

- be immutable;
- reference exactly one Trade Plan;
- be produced by deterministic rules;
- be fully traceable;
- record the Rule Set used;
- preserve the evaluation context.

A Risk Evaluation must never be modified after creation.

Any re-evaluation produces a new Risk Evaluation.

---

# Decision Outcomes

A Risk Evaluation may produce one of the following outcomes.

| Decision | Meaning |
|----------|---------|
| APPROVED | The Trade Plan satisfies every applicable rule. |
| APPROVED_WITH_WARNINGS | The Trade Plan satisfies applicable rules but produced warnings. |
| REJECTED | One or more deterministic rules failed. |

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
- Trading Core
- Trade Plan
- Rule Set
- Risk Evaluation Context