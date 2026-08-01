# Trading OS Stories

This directory contains the engineering work items used to evolve Trading OS.

Each meaningful change should begin with a focused Story.

## Directory Structure

```text
stories/
└── NNNN-short-title/
    ├── story.md
    ├── repository-analysis.md
    ├── implementation-plan.md
    ├── implementation-report.md
    ├── code-review.md
    └── engineering-report.md
```

Only `story.md` is created initially.

The remaining artifacts are produced as the workflow progresses.

An Implementation Plan is optional when the approved Story, Repository Analysis, ADRs, and repository conventions provide sufficient implementation guidance.

## Naming

Use:

```text
NNNN-short-kebab-case-title
```

Example:

```text
0001-connect-trade-plan-to-risk
```

## Workflow

```text
Story
→ Repository Analysis
→ Human Approval
→ Implementation Plan when required
→ Human Approval when required
→ Implementation
→ Implementation Report
→ Code Review
→ Human Approval
→ Engineering Report
→ Human Commit
```

## Principles

* Keep each Story focused on one product outcome.
* Use accepted ADRs for architectural decisions.
* Do not silently expand scope.
* Do not infer approval from artifact existence.
* Keep implementation changes visible for review in IntelliJ.
* Agents must not commit, push, or merge.
