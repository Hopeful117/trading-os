# Code Review Report - Story 0147

## Scope Reviewed

Reviewed the cTrader authentication, authorization-attempt persistence, secret
handling, Open API transport boundary, binding workflow, migration and tests
against Story 0147, ADR-073 and the approved Implementation Plan.

## Findings

### Resolved - OAuth callback was protected by the default JWT boundary

The initial implementation did not explicitly permit the cTrader redirect
callback, which would have rejected the provider redirect before state
validation. The callback path is now explicitly permitted and an integration
test verifies that an unauthenticated callback reaches the application and is
rejected only for its invalid state.

Disposition: fixed.

### Medium - Real provider protocol validation remains open

The implementation uses the documented JSON WebSocket payload types for
application authorization, account discovery and account authorization, but no
real cTrader environment was available. The numeric payload constants and
provider response shapes require controlled demo validation before production
use.

Disposition: retained as an explicit residual gap. No execution capability is
enabled until this validation succeeds.

### Medium - Refresh automation is deferred

The implementation fails closed when the stored access token expires and requires
a new owner-correlated authorization flow. It does not silently refresh or
change a binding. This is safe for the current Story but leaves refresh-token
rotation as a future improvement.

Disposition: accepted within Story scope; the final gate reports
`REAUTHENTICATION = PARTIAL`.

### Low - Historical Story 0146 metadata is stale

The merged 0146 Story and plan retain pre-merge workflow statuses and do not
contain a post-merge implementation report.

Disposition: not rewritten in this implementation to preserve temporal truth;
documented for a separate reconciliation action.

## Positive Findings

- OAuth state is server-generated, hashed, owner-scoped and one-time consumed.
- Callback failures are persisted rather than rolled back.
- Tokens remain behind encrypted Broker Service secret ports.
- Trading Core receives no cTrader-specific credentials or DTOs.
- Account selection is explicit and first-account inference is absent.
- External cTrader identity remains distinct from local identities.
- cTrader authentication does not set the existing technical connection to
  `CONNECTED`, so execution remains unavailable.
- No cTrader execution or synchronization messages were added.
- Existing Broker Service verification and migration tests pass.

## Independent Review Corrections

The follow-up independent review identified and corrected the following issues:

- ADR-073 is now marked `Accepted`.
- External account eligibility uses exact element matching rather than substring
  matching.
- Authorization state consumption is committed in a separate transaction before
  token exchange and remains non-replayable after internal failures.
- Callback token persistence and account binding use an owner-scoped pessimistic
  lock and revalidate the cTrader provider boundary.
- Regression tests cover exact account matching, consumed-state replay after
  secret persistence failure and the corrected binding lookup.

## Review Result

`PARTIAL - locally acceptable, real-provider validation required`

The implementation is consistent with the approved scope and fail-closed
boundary. It must not be treated as real cTrader production validation.
