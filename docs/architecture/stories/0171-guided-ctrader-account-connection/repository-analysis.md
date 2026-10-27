# Repository Analysis - Story 0171

## Story Understanding

Story 0171 is a UX and orchestration improvement for the existing cTrader
connection capabilities. It must reduce manual operations without collapsing the
two accepted cTrader authorization boundaries or weakening explicit account
selection and binding.

## Repository Baseline

| Item | Current state |
|---|---|
| Working tree | Dirty with pre-existing cTrader, FTMO, Market Intelligence and frontend changes |
| DevLog | Story Agent unavailable because the provider returned an LLM error |
| Broker Service cTrader auth | Implemented: initiate, callback, discovery, explicit binding |
| Market Data cTrader auth | Implemented as a separate service-owned flow |
| Web cTrader connection UI | Not implemented; Accounts page currently exposes Kraken credentials only |
| Trading Core provisioning | Exposed as a separate frontend action after broker connection |
| Execution | Must remain outside this Story |

## Current Flow

### Broker Service

`CTraderAuthenticationService` currently:

1. validates the owner-owned local cTrader broker account;
2. creates a server-correlated authorization attempt;
3. returns an OAuth URL;
4. consumes the callback and exchanges the code;
5. discovers authorized external account IDs;
6. stores token material behind Broker Service secret management;
7. requires explicit external account binding;
8. validates a read-only observation before marking the connection connected.

The controller exposes separate initiate, callback, status and binding endpoints.
The backend state is sufficient for a guided workflow, but the frontend does not
consume it.

### Market Data

Market Data exposes an independent authorization start/callback flow. It stores
its own token material and uses configured environment/account values. It must
not consume Broker Service tokens or sessions.

### Frontend

`Accounts` and `BrokerAccountService` currently implement Kraken API-key
creation, recovery, disconnect and Trading Core provisioning. No cTrader
provider option, OAuth launch, callback return state, discovered-account
selection or Market Data authorization step exists.

## Architectural Authority

`ADR-073` requires the lifecycle to remain staged:

```text
provider recognized
 -> authorization initiated
 -> callback exchanged
 -> authorized accounts discovered
 -> one account explicitly selected
 -> selected account bound
```

`ADR-077` permits Market Data to own a separate cTrader authorization and
explicitly prohibits shared token material, shared sessions and shared runtime
state. The Story must therefore orchestrate two service-owned stages rather than
implement one shared credential flow.

## Main Risks

- Callback responses currently return a browser-visible authorization result;
  the guided flow must keep it free of token material and avoid leaking provider
  payloads.
- A browser refresh must not duplicate callback consumption, binding or account
  provisioning.
- The frontend must distinguish `ACCOUNT_SELECTION_REQUIRED`, `BOUND`, expired,
  failed and revoked states without reconstructing provider-specific semantics.
- Market Data authorization currently depends on configured account/environment
  values, so the guided UI may require a backend-safe status/start contract
  before it can be associated with a selected local account.
- The user-facing workflow must not imply that Broker Service authentication
  automatically makes Market Data or execution ready.

## Required Clarifications for Planning

The implementation plan must decide, using current repository contracts:

- whether the local cTrader broker account is created before OAuth or as part of
  the first guided command;
- whether the Market Data stage is mandatory for a cTrader broker connection or
  optional until market exploration is requested;
- how the callback returns the user to the Accounts UI without trusting browser
  input for owner/account correlation;
- whether Trading Core provisioning is automatic after binding or remains an
  explicit confirmation step;
- how a selected local account is associated with the service-owned Market Data
  account without sharing credentials.

## Scope Boundary

This analysis supports a guided connection workflow only. It does not authorize
implementation, OAuth scope changes, token sharing, cTrader execution, Risk
changes or automatic account selection.

## Validation Boundary

The first validation target is a deterministic and browser-tested connection
workflow through authorization, explicit selection, binding and safe state
display. No order submission or live trading action is part of validation.

## Approval Status

Repository Analysis is complete and awaiting explicit human approval before an
Implementation Plan is produced.
