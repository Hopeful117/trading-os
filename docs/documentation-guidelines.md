# Documentation Guidelines

## Purpose

This document defines the documentation standards for Trading OS.

Its objective is to ensure that every document is:

- easy to read;
- easy to navigate;
- easy to maintain;
- easy to consume by both human engineers and AI agents.

These guidelines apply to all project documentation unless explicitly stated
otherwise.

---

# Scope

These guidelines apply to:

- Architecture documentation
- ADRs
- Stories
- Engineering Reports
- Domain documentation
- Concept documentation
- README files
- Markdown documentation
- AI-generated documentation

---

# Documentation Philosophy

Documentation is part of the architecture.

It is not an afterthought.

Every document should help readers answer a specific question as quickly as
possible.

Documentation should be organized as a network of focused documents rather than
a collection of large monolithic files.

Readers should navigate between documents instead of reading the documentation
from beginning to end.

---

# Guiding Principles

## Human and AI Readability

Trading OS documentation is intended to be read by both human engineers and AI
agents.

Documents should therefore be:

- easy to scan;
- logically structured;
- self-contained within their defined responsibility;
- free from unnecessary duplication;
- consistent in terminology and formatting.

Optimizing documentation for AI must never reduce human readability.

Optimizing documentation for humans must never introduce ambiguity for AI.

---

## One Question Per Document

Every document should answer one primary question.

Examples:

README

→ Where can I find information?

Concept

→ What is this business concept?

Domain

→ Which responsibilities belong to this domain?

ADR

→ Why was this architectural decision made?

Story

→ What should be implemented?

Engineering Report

→ What was implemented?

If a document starts answering multiple unrelated questions, consider splitting
it into multiple focused documents.

---

## Single Source of Truth

Every type of information has one authoritative owner.

Examples:

| Information | Authoritative Source |
|-------------|----------------------|
| Architecture decisions | ADRs |
| Feature scope | Stories |
| Domain responsibilities | Domain documentation |
| Business concepts | Concept documentation |
| Engineering history | Engineering Reports |
| Implementation details | Source code |

Documentation should reference authoritative sources instead of duplicating
their contents.

---

## Navigation Over Duplication

Documentation exists to guide readers toward the correct information.

Prefer:

- links;
- references;
- summaries.

Avoid copying information that already exists elsewhere.

---

## Optimize for Retrieval

Documentation should minimize the time required to locate authoritative
information.

Prefer:

- descriptive headings;
- predictable structure;
- focused documents;
- explicit ownership;
- cross references.

Avoid:

- oversized documents;
- unrelated topics;
- hidden assumptions;
- duplicated explanations.

---

## Incremental Evolution

Documentation evolves together with the architecture.

Documents should remain maintainable as the project grows.

Large documents should be divided into smaller documents whenever doing so
improves readability.

---

# Documentation Hierarchy

Documentation should follow the hierarchy below.

Architecture Principles

↓

Documentation Standards

↓

Architecture Decision Records (ADRs)

↓

Stories

↓

Engineering Reports

↓

Source Code

Each layer builds upon the previous one.

Lower layers should never redefine higher-level decisions.

---

# Document Responsibilities

Every document type has a clearly defined responsibility.

| Document | Responsibility |
|----------|----------------|
| README | Navigation |
| Diagram | Visual explanation |
| Domain | Responsibilities and ownership |
| Concept | Business meaning |
| ADR | Architectural decision |
| Story | Implementation scope |
| Engineering Report | Engineering outcome |

---

# Document Size Guidelines

The following sizes are targets rather than strict limits.

| Document Type | Target Size |
|---------------|------------:|
| README | 50–120 lines |
| Concept | 150–250 lines |
| Domain | 200–350 lines |
| ADR | 400–450 lines |
| Story | Official Story Template |
| Engineering Report | As required |

If a document grows significantly beyond its target size, review whether it
should be divided into multiple documents.

---

# Writing Style

Documentation should use:

- clear language;
- short paragraphs;
- descriptive headings;
- active voice;
- consistent terminology.

Avoid:

- unnecessary repetition;
- marketing language;
- implementation details where they do not belong;
- vague wording.

---

# Markdown Guidelines

Use standard Markdown.

Prefer:

- headings;
- tables;
- bullet lists;
- fenced code blocks.

Avoid excessive nesting.

Code examples should remain concise and focused.

---

# Naming Conventions

Documentation files should use:

- lowercase;
- kebab-case;
- descriptive names.

Examples:

```text
trade-plan.md
risk-evaluation.md
market-intelligence.md
documentation-guidelines.md
```

README files should be used only as entry points for directories.

---

# Cross References

Whenever information already exists elsewhere:

- reference it;
- do not duplicate it.

Cross references should always point toward the authoritative document.

---

# README Guidelines

README files should:

- introduce the directory;
- explain its purpose;
- help readers choose the correct document.

README files should not duplicate the contents of child documents.

---

# Diagram Guidelines

Architecture diagrams should:

- answer one question;
- remain visually consistent;
- avoid implementation details;
- use project terminology;
- distinguish implemented and planned capabilities when relevant.

---

# AI Agent Guidelines

AI agents must follow the same documentation standards as human contributors.

Agents should:

- preserve existing terminology;
- follow established templates;
- respect document ownership;
- avoid introducing duplication;
- prefer updating existing documents;
- preserve navigation structure;
- respect document size targets.

Agents must never invent architectural decisions.

When uncertain, they should request clarification instead of making
assumptions.

---

# Documentation Maintenance

Documentation should be reviewed whenever:

- architecture changes;
- a Story is completed;
- an ADR is accepted;
- terminology evolves;
- new domains or concepts are introduced.

Keeping documentation synchronized with the project is a continuous engineering
activity.

---

# Success Criteria

Good documentation should allow a contributor to:

- locate the correct document quickly;
- understand its purpose immediately;
- identify the authoritative source;
- navigate naturally to related information.

Documentation should minimize the effort required to answer engineering
questions.

If readers cannot quickly determine where information belongs, the
documentation structure should be improved.