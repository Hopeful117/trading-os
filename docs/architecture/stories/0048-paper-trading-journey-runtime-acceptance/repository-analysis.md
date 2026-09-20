# Repository Analysis - Story 0048

## Scope

Story 0048 validates the supported authenticated PAPER journey through the web
application. It is an acceptance Story: the main output is runtime evidence,
not a new autonomous execution capability.

## Current Evidence Before Validation

The application already exposed the required screens and API boundaries, but
the previous walkthrough stopped before execution because the generated plan
was rejected by `MAX_EXPOSURE`. The execution path also needed the approved
risk evaluation to be promoted into an execution-ready Trade Plan before
creating an intent.

## Validation Boundary

The scenario uses only the web application and its existing Gateway routes:

```text
register/login
  -> PAPER account creation
  -> opportunity scan
  -> Trade Plan creation
  -> human acceptance
  -> deterministic risk evaluation
  -> explicit execution
  -> position reload
  -> explicit full close
  -> empty-position reload
```

No direct broker call, database mutation, risk bypass, or manually constructed
execution intent is permitted.

## Deterministic Regression Boundary

The implementation changes required to complete the path preserve:

* Trading Core authority for execution and PAPER settlement;
* Market Intelligence authority for the durable risk acknowledgment handoff;
* explicit `READY_TO_EXECUTE` state before execution;
* Gateway-backed frontend communication;
* fail-closed risk behavior.

## Validation Expectations

* Angular tests and production build;
* affected Trading Core, Market Intelligence, and Gateway tests;
* authenticated runtime walkthrough;
* page-reload persistence checks;
* `git diff --check`.
