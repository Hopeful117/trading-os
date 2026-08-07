# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for Trading OS.

ADRs are the authoritative source for architectural decisions.

Every significant architectural decision should be documented as an ADR before
implementation begins.

---

# Purpose

The purpose of ADRs is to preserve architectural knowledge.

Each ADR documents:

- the architectural problem;
- the decision;
- the rationale;
- the consequences;
- related decisions.

ADRs describe *why* the architecture is the way it is.

They do not describe implementation details.

---

# Finding an ADR

Use ADRs when you need to understand:

| I want to understand... | Read... |
|--------------------------|----------|
| Why a decision was made | ADR |
| Why an alternative was rejected | ADR |
| Long-term architectural direction | ADR |
| Design rationale | ADR |

If you need to understand implementation details, read the corresponding Story
or the source code instead.

---

# ADR Lifecycle

Every ADR follows the same lifecycle.

Draft

↓

Review

↓

Accepted

↓

Implemented

↓

Superseded *(optional)*

Only Accepted ADRs define the current architecture.

Superseded ADRs remain part of the project's history.

---

# Relationship with Other Documentation

| Information | Authoritative Source |
|-------------|----------------------|
| Architecture decisions | ADRs |
| Feature implementation | Stories |
| Engineering history | Engineering Reports |
| Source code | Repository |

Stories implement ADRs.

Engineering Reports document the implementation of Stories.

Neither should redefine architectural decisions.

---

# Writing ADRs

A new ADR should be created when:

- introducing a significant architectural decision;
- changing domain responsibilities;
- introducing new architectural patterns;
- modifying long-term technical direction.

Minor implementation details do not require an ADR.

---

# Maintaining ADRs

Accepted ADRs should remain stable.

If an architectural decision changes, create a new ADR.

Avoid modifying historical ADRs unless correcting factual errors.

Architecture evolves by creating new ADRs, not by rewriting history.