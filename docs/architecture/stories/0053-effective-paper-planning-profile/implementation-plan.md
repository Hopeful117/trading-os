# Implementation Plan - Story 0053

## Plan Status

`IMPLEMENTED - HISTORICAL PLAN`

## Phase 1 - Define the default profile

Use a validated, versioned platform-managed Trade Planning Profile for new
PAPER accounts. Keep it distinct from the account's Risk Profile.

## Phase 2 - Provision transactionally

During PAPER account creation:

1. resolve the compatible platform planning profile;
2. assign the profile in the same provisioning boundary as the account;
3. preserve profile version immutability;
4. reject currency incompatibility explicitly;
5. preserve ownership checks for reads and updates.

## Phase 3 - Expose effective state

Return effective risk and planning profile references from the account view and
render their versions in the Angular account card.

## Phase 4 - Regression validation

Cover account provisioning, ownership, currency compatibility, account mapping,
and account-card rendering. Re-run the authenticated PAPER path through Trade
Plan creation without bypassing risk or human authorization.

## Non-Goals

* no user-selected planning profile;
* no LIVE default assignment;
* no Risk Domain rule change;
* no autonomous execution;
* no new E2E framework.

## Implementation Reference

```text
commit = 698fe03
ADR = docs/architecture/adr/ADR-046.md
```
