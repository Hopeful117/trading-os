# Business Concepts

This directory documents the core business concepts of Trading OS.

Business concepts represent the shared language of the platform.

They describe what the system manipulates, not how it is implemented.

Implementation details belong in the source code.

Architectural decisions belong in ADRs.

---

# Available Concepts

This directory contains documentation for the business objects that drive the
trading workflow.

Typical concepts include:

- Opportunity
- Observation
- Trade Plan
- Risk Evaluation
- Execution Intent
- Position
- Portfolio
- Account
- Market Snapshot
- Rule Set

Each concept is documented independently.

---

# Choosing a Concept

| I want to understand... | Read... |
|--------------------------|----------|
| Market opportunities | Opportunity |
| Market observations | Observation |
| Trading proposals | Trade Plan |
| Risk decisions | Risk Evaluation |
| Approved executions | Execution Intent |
| Open trades | Position |
| Trading portfolio | Portfolio |
| Trading account | Account |
| Market state | Market Snapshot |
| Risk rules | Rule Set |

---

# Concept Documentation Structure

Every concept document should answer the same questions.

- What is the concept?
- Why does it exist?
- Which domain owns it?
- Is it mutable or immutable?
- Where is it created?
- Who consumes it?
- How does it evolve during its lifecycle?
- Which ADRs define its behavior?

Using a common structure makes concepts easier to compare and maintain.

---

# Relationship with Other Documentation

Concept documentation complements the rest of the architecture documentation.

| Information | Authoritative Source |
|-------------|----------------------|
| Domain responsibilities | `domains/` |
| Architecture decisions | ADRs |
| Feature implementation | Stories |
| Implementation details | Source code |
| Engineering history | Engineering Reports |

Concept documents should describe business meaning only.

They should avoid implementation details whenever possible.