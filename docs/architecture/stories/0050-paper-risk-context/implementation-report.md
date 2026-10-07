# Implementation Report - Story 0050

## Status

`IMPLEMENTED - LOCAL VALIDATION COMPLETE; ACCEPTANCE BLOCKED`

## Scope Delivered

Trading Core now keeps the PAPER risk-facts path mode-aware. The implementation
preserves local Trading Core authority for PAPER account and trade facts while
retaining the Broker Service path for LIVE facts.

The implementation represented by commit `fdcb6ac`:

* updates `ModeAwareRiskFactsProvider` for the PAPER risk context;
* keeps PAPER facts local to Trading Core;
* preserves the broker-backed LIVE path;
* retains fail-closed behavior when the required facts are not available;
* updates the focused provider regression test.
* rejects malformed local account/trade data without synthesizing a source
  version or throwing during snapshot construction.
* carries explicit `PROTECTED`, `PARTIALLY_PROTECTED`, `UNPROTECTED`, and
  `UNKNOWN` position states through the risk snapshot;
* represents unavailable stop-loss risk as absent rather than as a synthetic
  zero value for unprotected positions;
* permits optional stop-loss and take-profit values for manual Trade Plans;
* keeps opportunity/automated Trade Plans fail-closed when protection is absent;
* keeps manual unprotected positions available for management without treating
  them as protected positions; exposure and margin controls remain active.

No Risk Domain repository or provider dependency was introduced.

## Validation Evidence

The implementation includes focused regression tests for the PAPER protection
state, manual unprotected Trade Plans, automated protection enforcement, and
Risk Domain projection state. The affected module suites were also executed.

```text
Risk Domain targeted tests: 10 passed
Market Intelligence targeted tests: 8 passed
Trading Core targeted tests: 29 passed
Risk Domain full suite: 26 passed
Market Intelligence full suite: 489 passed
Trading Core full suite: 577 passed
git diff --check: passed
```

## Remaining Evidence

* confirm complete local balance, open-trade, closed-trade, and protection
  facts in the supported runtime;
* PAPER margin remains unavailable until an authoritative margin source is
  defined and implemented;
* confirm LIVE delegation remains covered by the current Trading Core tests;
* authenticated runtime validation now covers service startup, registration,
  login, PAPER provisioning, account listing, and empty-position retrieval;
  the complete decision path reaches risk evaluation and remains blocked by the
  unavailable margin fact; no execution is attempted without approval.

## Worktree and Git

```text
IMPLEMENTATION_COMMIT = pending
DOCUMENTATION_BRANCH = docs/story-artifact-remediation
PUSH = NO
```

The working tree contains unrelated pre-existing changes and no commit was
created by this implementation.
