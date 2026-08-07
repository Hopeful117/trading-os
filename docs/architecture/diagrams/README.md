# Architecture Diagrams

This directory contains the architectural views of Trading OS.

Each diagram focuses on a single architectural concern.

No diagram attempts to describe the complete system.

Readers should select the diagram that best answers their current question.

---

# Available Diagrams

| Diagram | Purpose |
|----------|---------|
| 01 - System Context | Understand the complete system and its external interactions |
| 02 - Trading Decision Pipeline | Understand the business workflow from opportunity to execution |
| 03 - Runtime Sequence | Understand runtime interactions between services |
| 04 - Domain Ownership | Understand domain responsibilities and ownership boundaries |
| 05 - Deployment View | Understand runtime deployment and infrastructure |
| 06 - Data Lifecycle | Understand how business information evolves |
| 07 - Event Flow | Understand asynchronous communication |
| 08 - Story Progression | Understand architectural evolution through Stories |

---

# Choosing a Diagram

| I want to understand... | Open... |
|--------------------------|----------|
| The complete platform | System Context |
| How a trade moves through the system | Trading Decision Pipeline |
| Service communication | Runtime Sequence |
| Which domain owns what | Domain Ownership |
| Infrastructure | Deployment View |
| Data ownership | Data Lifecycle |
| Event propagation | Event Flow |
| Project evolution | Story Progression |

---

# Diagram Principles

Every architecture diagram should:

- answer one specific question;
- use consistent terminology;
- avoid implementation details;
- distinguish implemented and planned capabilities when relevant;
- remain synchronized with accepted ADRs.

---

# Maintenance

Architecture diagrams are living documentation.

Whenever the architecture changes significantly, review the affected diagrams
to ensure they remain accurate.