# Story 0092 - Challenge Dashboard and Angular Workflow

## Metadata

- **ID:** `0092`
- **Status:** `IMPLEMENTED - AWAITING HUMAN REVIEW`
- **Follows:** `0089`, `0090`, `0091`
- **Repository Analysis:** `repository-analysis.md`

## Goal

Provide an authenticated, provider-neutral Angular Challenge workflow for
selecting an existing account, discovering a Challenge definition, starting a
Challenge, and observing backend-owned progress and terminal state.

## Scope

- Add typed Angular Challenge models and HTTP service.
- Add a dedicated authenticated `/challenges` route and page.
- Select existing accounts through `AccountService`; never create accounts.
- Display the generic Challenge catalog and use the exact definition ID/version
  returned by the backend.
- Start Challenges through the Story 0091 endpoint.
- Poll the existing Challenge progress projection reactively while ACTIVE.
- Display ACTIVE, PASSED, and BREACHED states and backend-supplied metrics.
- Add normal authenticated navigation to the feature.
- Add focused service and page tests.

## Constraints

- Angular does not calculate Risk, Challenge qualification, drawdown, or
  provider rules.
- No Kraken-specific branches or hardcoded benchmark values.
- No Broker Account creation, lifecycle mutation, reset/restart, WebSockets,
  SSE, or major Dashboard/navigation redesign.
- A backend error must not be silently rendered as `no Challenge`.
- The known Risk Day reset-boundary limitation remains backend-owned and is not
  corrected in Angular.

## Scope Adjustment Identified During Analysis

The existing progress endpoint uses the same generic `404` ProblemDetail for
`Account has no Challenge`, missing accounts, and ownership failures. A small,
provider-neutral backend error-contract adjustment is required before the
frontend can safely distinguish absence from failure: the no-Challenge case
must expose a stable ProblemDetail code, while missing/inaccessible resources
must remain generic `404` responses.

This adjustment does not change Challenge or Risk semantics; it only makes the
existing read projection consumable without unsafe error inference.
