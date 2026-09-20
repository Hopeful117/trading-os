# Implementation Plan - Story 0049

## Approved Scope

Implement the trader-facing execution history in the Angular application using
the existing authenticated execution-list, execution-detail, Trade Plan, and
positions contracts. Do not add execution commands, analytics calculations, or
backend lifecycle changes.

## Steps

1. Extend the frontend execution model and `ExecutionService` with the owned
   execution-list contract.
2. Add a standalone authenticated history page that renders explicit loading,
   empty, error, terminal, pending, and uncertain states.
3. Enrich rows lazily with execution details and Trade Plan context so links use
   authoritative Trade Plan version and trading-account identifiers.
4. Link available rows to the existing Trade Plan and account positions routes;
   do not guess identifiers when relationships are absent.
5. Replace the dead `/analytics` sidebar target with the history route and add
   the route behind `authGuard`.
6. Add focused service, component, and route tests for ownership-scoped data,
   state rendering, uncertainty semantics, and conditional navigation.
7. Run the Angular test suite, production build, and `git diff --check`.

## Design Constraints

* The list endpoint is authoritative for ownership and visible execution
  records.
* The frontend must not expose retry or reconciliation actions from history.
* `SUBMISSION_OUTCOME_UNKNOWN`, `RECONCILIATION_IN_PROGRESS`,
  `RECOVERY_BLOCKED`, and `RISK_REVALIDATION_UNAVAILABLE` remain uncertain or
  pending, never successful or failed by inference.
* Detail and Trade Plan enrichment is bounded to the displayed list and must
  degrade per row without hiding the owned execution record.
* Existing uncommitted workspace changes remain outside the Story scope.

## Validation

```text
npm run test:ci
npm run build
git diff --check
```
