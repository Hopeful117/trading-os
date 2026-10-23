# Engineering Report - Story 0147

## Final Result

Story 0147 establishes a provider-isolated cTrader authentication and explicit
external-account binding foundation. The implementation is locally validated,
but the real cTrader provider gate remains open because no human-supplied
application/account credentials were used.

## Workflow

- Repository Analysis: approved by the human engineer.
- Implementation Plan: approved by the human engineer.
- Implementation: completed within approved scope.
- Documentation reconciliation: completed for Story 0147 and ADR-073.
- Code review: completed with the documented residual provider-validation gaps.
- Commit/PR delivery: not performed by this report.

## Architecture

Broker Service owns cTrader authorization initiation, callback correlation, token
exchange, encrypted token references, Open API application/account
authentication and external account binding. Trading Core remains unaware of
cTrader transport, OAuth payloads and token material. FTMO remains outside the
Broker Service provider boundary.

The current implementation requests view-only `accounts` scope. Authentication
and binding do not make account facts authoritative and do not enable Risk or
execution.

## Validation Summary

| Check | Result |
|---|---|
| cTrader focused unit tests | PASS |
| Broker Service full test suite | PASS |
| Broker Service `clean verify` | PASS |
| Flyway V7 PostgreSQL validation | PASS |
| H2 JPA schema validation | PASS |
| `git diff --check` | PASS |
| Kraken regression | PASS |
| Real cTrader authorization | NOT RUN - credentials intentionally unavailable |
| cTrader account synchronization | NOT RUN - next Story |
| cTrader execution | NOT IMPLEMENTED |

## Security and Ownership

The callback cannot select a local account from browser input. State is bound to
the owner, local BrokerAccount, provider registration, environment and expiry.
Tokens and client secrets remain inside Broker Service. Account discovery and
binding require authenticated owner access, and the selected external identifier
is persisted separately from local account identities.

## Residual Risks

- Confirm JSON WebSocket payload constants and response mapping against the real
  cTrader demo endpoint.
- Run the controlled human validation procedure with `accounts` scope.
- Decide in a future Story whether refresh-token automation is needed.
- Do not begin synchronization until the real authentication/binding evidence is
  recorded.

## Vault Outcome

Vault was not consulted. No curated-vault action is proposed; all relevant
architecture and validation evidence is repository-owned.

## Final Gate

========================================
CTRADER AUTH + ACCOUNT BINDING GATE
========================================

REPOSITORY BASELINE:
009df93080bc11507e53dc3ee05d9a45fc074b06

STORY:
0147 - cTrader Authentication and Authenticated Account Binding

ADR REQUIRED:
YES

ADR:
ADR-073 - cTrader Authentication and Explicit External Account Binding

CTRADER PROVIDER FOUNDATION:
READY

AUTHENTICATION CONTRACT:
IMPLEMENTED

AUTH STATE CORRELATION:
READY

CREDENTIAL ISOLATION:
READY

ACCOUNT DISCOVERY:
READY

EXPLICIT ACCOUNT SELECTION:
READY

EXTERNAL ACCOUNT BINDING:
READY

OWNER ISOLATION:
READY

REAUTHENTICATION:
PARTIAL

RESTART SEMANTICS:
PARTIAL

TRADING CORE CTRADER COUPLING:
NO

BROKER SERVICE FTMO COUPLING:
NO

KRAKEN REGRESSION:
PASS

REAL CTRADER AUTH VALIDATED:
NO

CTRADER ACCOUNT SYNCHRONIZED:
NO

RISK READY FROM CTRADER:
NO

CTRADER EXECUTION ENABLED:
NO

S1 REQUIRED:
NO

AI REQUIRED:
NO

IMPLEMENTATION STATUS:
PARTIAL

NEXT ENGINEERING STORY:
0148 - cTrader READ-ONLY Account Synchronization

NEXT HUMAN ACTION:
Perform controlled demo cTrader authorization and explicit account-binding validation with `accounts` scope, without placing orders.
