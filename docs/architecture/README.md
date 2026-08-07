# Trading OS Architecture

Welcome to the Trading OS architecture documentation.

This documentation is the primary entry point for understanding the system
architecture, navigating the repository and locating the authoritative source
for architectural information.

The goal is not to explain every aspect of Trading OS in a single document.

Instead, this README helps readers quickly find the document that answers their
current question.

---

# Documentation Structure

The architecture documentation is organized into focused sections.

| Looking for... | Read... |
|----------------|---------|
| System overview | `diagrams/` |
| Domain responsibilities | `domains/` |
| Business concepts | `concepts/` |
| Architecture decisions | `adr/` |
| Feature implementation | `stories/` |
| Engineering history | `reports/` |

Every document has a single responsibility.

---

# Reading Guide

Start with the document that best matches your objective.

| I want to... | Read... |
|--------------|----------|
| Understand the overall architecture | Architecture diagrams |
| Understand a business domain | Domain documentation |
| Understand a business concept | Concept documentation |
| Understand an architectural decision | ADR |
| Understand a feature | Story |
| Understand how a feature was implemented | Engineering Report |

There is no required reading order.

Navigate directly to the information you need.

---

# Documentation Principles

The documentation follows a small set of principles.

- One source of truth for every type of information.
- Link instead of duplicate.
- Keep documents focused.
- Keep documentation synchronized with the architecture.
- Prefer multiple small documents over one large document.

---

# Repository Structure

```text
docs/
└── architecture/
    ├── README.md
    ├── diagrams/
    ├── domains/
    ├── concepts/
    ├── adr/
    ├── stories/
    └── reports/
```

---

# Contributing

Before adding new documentation:

1. Check whether an authoritative document already exists.
2. Update the authoritative document whenever possible.
3. Add references instead of duplicating content.
4. Keep documents concise and focused.

If a document starts answering multiple unrelated questions, consider splitting
it into smaller documents.