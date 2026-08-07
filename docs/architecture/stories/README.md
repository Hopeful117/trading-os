# Trading OS Stories

This directory contains the engineering Stories used to evolve Trading OS.

A Story defines the scope of a single engineering change. It describes what
should be implemented, why it is needed and how success will be validated.

Stories define implementation work.

They do not define architecture.

Architectural decisions belong to the ADRs.

---

# Story Lifecycle

Every Story progresses through the same engineering workflow.

```text
Story
    ↓
Repository Analysis
    ↓
Human Approval
    ↓
Implementation Plan (when required)
    ↓
Human Approval (when required)
    ↓
Implementation
    ↓
Implementation Report
    ↓
Code Review
    ↓
Human Approval
    ↓
Engineering Report
    ↓
Human Commit
```

Each artifact has a single responsibility and is produced only when required by
the workflow.

---

# Directory Structure

```text
stories/
└── NNNN-short-kebab-case-title/
    ├── story.md
    ├── repository-analysis.md
    ├── implementation-plan.md
    ├── implementation-report.md
    ├── code-review.md
    └── engineering-report.md
```

Only `story.md` is created when a Story begins.

Additional artifacts are created as the engineering workflow progresses.

An Implementation Plan is optional when the approved Story, Repository
Analysis, ADRs and repository conventions already provide sufficient
implementation guidance.

---

# Naming Convention

Story directories follow a numeric prefix and a short kebab-case title.

Example:

```text
0001-connect-trade-plan-to-risk
```

Story identifiers are immutable and should never be reused.

---

# Choosing the Right Document

| I want to... | Read... |
|--------------|----------|
| Understand the feature scope | `story.md` |
| Understand the current repository state | `repository-analysis.md` |
| Understand the implementation approach | `implementation-plan.md` |
| Review the implementation | `implementation-report.md` |
| Review code quality | `code-review.md` |
| Understand the complete engineering outcome | `engineering-report.md` |

Read only the document that answers your current question.

---

# Relationship with Other Documentation

Stories complement the rest of the project documentation.

| Information | Authoritative Source |
|-------------|----------------------|
| Architecture decisions | ADRs |
| Domain responsibilities | Domain documentation |
| Business concepts | Concept documentation |
| Feature implementation | Stories |
| Implementation history | Engineering Reports |
| Implementation details | Source code |

Stories implement accepted ADRs.

Stories should never redefine architectural decisions.

---

# Story Principles

Every Story should:

- remain focused on a single product outcome;
- implement accepted ADRs;
- avoid unnecessary scope expansion;
- define clear acceptance criteria;
- preserve traceability throughout the engineering workflow.

Agents must never infer approval from the existence of an artifact.

Commits, pushes and merges always require explicit human approval.

---

# Maintaining Stories

Stories are engineering records.

Completed Stories should remain stable.

If requirements change significantly after approval, create a new Story or
supersede the existing one rather than rewriting its original intent.

Engineering Reports capture what was implemented.

Stories capture what was intended.



# Engineering Reports

This directory contains the engineering reports produced during the development
of Trading OS.

Reports document the outcome of engineering work.

They record what was implemented, how it was validated and the final outcome of
the engineering workflow.

Reports do not define architecture or implementation scope.

Architecture belongs to ADRs.

Implementation scope belongs to Stories.

---

# Purpose

Engineering Reports provide a permanent engineering history.

They allow contributors to understand:

- what was implemented;
- what was validated;
- which issues were identified;
- the outcome of reviews;
- the final implementation status.

---

# Available Reports

Typical engineering artifacts include:

- Implementation Report
- Code Review
- Engineering Report

Each report documents a different stage of the engineering workflow.

---

# Choosing the Right Document

| I want to... | Read... |
|--------------|----------|
| Understand what was implemented | Implementation Report |
| Review implementation quality | Code Review |
| Understand the final engineering outcome | Engineering Report |

---

# Relationship with Other Documentation

Engineering Reports complement the rest of the documentation.

| Information | Authoritative Source |
|-------------|----------------------|
| Architecture decisions | ADRs |
| Feature scope | Stories |
| Engineering outcome | Engineering Reports |
| Implementation details | Source code |

Reports describe implementation.

They should never redefine architecture or modify Story scope.

---

# Report Principles

Every report should:

- describe facts rather than intentions;
- remain objective and traceable;
- summarize validation activities;
- identify unresolved issues when applicable;
- reference related Stories and ADRs.

Reports should document evidence, not assumptions.

---

# Maintaining Reports

Engineering Reports are historical records.

Once approved, reports should remain stable.

Corrections should be limited to factual errors.

New engineering work should produce new reports rather than rewriting
historical ones.