# Repository Analysis - Story 0050

## Baseline

```text
STORY = 0050-paper-risk-context
BRANCH = story/0050-paper-risk-context
BASE = 0300499
WORKTREE = pre-existing local modifications plus runtime artifacts
```

The worktree changes listed before this Story remain outside its scope and
must not be reverted or included accidentally.

## Architectural Boundary

ADR-042 is authoritative for execution-mode position authority:

```text
LIVE  -> Broker/provider authority
PAPER -> Trading Core local authority
```

ADR-028 requires Trading Core to assemble complete immutable financial snapshots
before invoking the Risk Domain. The Risk Domain itself must not access
repositories or external services.

ADR-006 remains authoritative for instrument metadata and market constraints.
Broker capability work must not duplicate Market Data ownership.

## Current Gap

`ModeAwareRiskFactsProvider.paperSnapshot()` already reads local balances and
trades, but returns an incomplete snapshot with PAPER unavailability reasons.
This prevents the Risk Domain from evaluating the local PAPER context.

`Trade` already contains the minimal local projection needed for the first
PAPER milestone, including quantity, side, entry price, lifecycle, stop loss,
and take profit.

## Candidate Design

Introduce a dedicated local PAPER facts path selected by the mode-aware
boundary:

```text
LIVE  -> BrokerRiskFactsPort -> Broker Service
PAPER -> PaperRiskFactsProvider -> local Trading Core state
```

The PAPER provider must expose complete local facts and provenance. It must not
call Broker Service to determine whether a local PAPER position exists.

Broker margin and provider capability facts are an input dependency owned by
Story 0051. Story 0050 must not implement a second broker capability model.

## Risks

* Existing local account state may not contain every ledger field needed for a
  future drawdown model.
* Fees and protection records may be incomplete.
* Existing tests currently assert that PAPER facts are incomplete and must be
  updated to the new supported contract.
* A local snapshot can be complete only for the data actually persisted; it
  must not invent external broker facts.

## Validation Plan

* Update `ModeAwareRiskFactsProviderTest` for complete local PAPER facts.
* Add tests for long, short, open, closed, and unprotected trades.
* Add integration coverage for risk-context assembly.
* Retain LIVE delegation tests.
* Run affected Maven tests and `git diff --check`.

## Workflow Gate

```text
REPOSITORY_ANALYSIS = completed
IMPLEMENTATION_PLAN = completed - approval required
ADR_CONFLICT_CHECK = completed against ADR-006, ADR-028, ADR-029, ADR-042
HUMAN_APPROVAL = required before implementation
```
