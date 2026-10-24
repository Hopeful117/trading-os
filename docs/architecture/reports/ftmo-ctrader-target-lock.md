# FTMO + cTrader Target Lock

**Decision date:** 2026-10-23
**Repository baseline:** `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7`
**Implementation:** documentation only; no production code, migrations, dependencies, credentials, or runtime mutations

## 1. Evidence

The project owner supplied a direct FTMO support response dated 2026-10-23:

```text
SOURCE = DIRECT_FTMO_SUPPORT_REPLY
EVIDENCE = USER_SUPPLIED_SUPPORT_RESPONSE
FTMO_CTRADER_OPEN_API_PERMISSION = CONFIRMED_BY_SUPPORT
AUTOMATED_STRATEGIES = ALLOWED
```

Official cTrader Open API documentation was also checked on 2026-10-23. It
documents real-time market data, trading operations, historical/current/pending
operations, demo/live accounts, and broker-affiliated account support. It does
not prove access to the exact FTMO Challenge account or its account-specific
rules and operation permissions.

Kraken support evidence remains:

```text
KRAKEN_PROP_API_ACCESS = NOT_AVAILABLE
```

Therefore FTMO Challenge 2-Step + cTrader is selected as the first prop target,
while execution readiness remains unproven.

## 2. Decision

```text
BUSINESS_PROVIDER = FTMO
CHALLENGE = FTMO_CHALLENGE_2_STEP
EXECUTION_PLATFORM = CTRADER
INTEGRATION_TARGET = CTRADER_OPEN_API
TARGET_SELECTION = ACCEPTED
EXECUTION_READINESS = NOT_READY
```

The durable architectural decision is `ADR-071`.

FTMO remains a Challenge/business provider. cTrader remains a Broker/execution
platform. Trading Core remains provider-neutral. Broker Service owns the cTrader
adapter, authentication, transport, mapping, provider errors, and technical
connectivity. Trading Core owns Challenge state, deterministic Risk, execution
authorization, lifecycle state, and reconciliation decisions.

## 3. Preserved History

The prior report `ftmo-2step-ctrader-pre-challenge-gap.md` remains historically
accurate: it recorded FTMO cTrader compatibility as pending external confirmation
at the time it was written. This report does not rewrite that report. The new
support response changes the current target-selection status, not the prior
report's historical state.

The following V1 facts remain unchanged:

```text
ENGINEERING_V1 = ACCEPTED_WITH_RESIDUALS
V1_ENGINEERING_MILESTONE_CLOSED = YES
S1_REQUIRED_FOR_FTMO_CHALLENGE = NO
AI_REQUIRED_FOR_FTMO_CHALLENGE = NO
```

## 4. Reusable Architecture

The following boundaries are reusable in principle:

- provider-neutral Broker Service capabilities;
- encrypted credential isolation inside Broker Service;
- distinct Account and BrokerAccount identities;
- deterministic Risk and immutable evaluation context;
- explicit human authorization;
- stable idempotency and unknown-outcome reconciliation;
- provider-authoritative LIVE position state;
- canonical versus provider instrument identity;
- Decision History provenance.

The repository is not yet physically multi-provider. Provider registration and
some account-routing assumptions are Kraken-only. Story 0145 is the next focused
implementation prerequisite.

## 5. Read-Only-First Admission

The target path is intentionally staged:

```text
authenticate
    -> identify and bind the exact cTrader account
        -> read-only account/order/deal/position synchronization
            -> prove freshness, completeness and restart recovery
                -> assemble authoritative Risk Facts
                    -> human authorization
                        -> execution only after controlled evidence
```

The following facts are mandatory for execution admission:

```text
account identity
balance and equity
used/free margin
open positions
pending orders and orders
deals/fills
realized and unrealized P&L
commissions and swaps/carrying costs
instrument constraints
observation time, freshness and completeness
Challenge phase and current local state
```

Missing, stale, contradictory, or incomplete facts must block authorization.
Events may accelerate updates but cannot replace restart-safe authoritative
snapshots.

## 6. Unverified Items

The following remain outside the evidence currently available:

- OAuth/application registration and token lifecycle for the exact FTMO account;
- account discovery and selected-account binding;
- read/write operation coverage for the exact account;
- cTrader event delivery, reconnect, rate limits, and restart behavior;
- FTMO account-specific instruments and contract constraints;
- commissions, swaps, carrying costs, and margin semantics;
- FTMO daily reset and DST semantics;
- equality/breach semantics for loss rules;
- minimum trading days and phase transitions;
- provider-side pass, breach, liquidation, or forced-close observability.

No FTMO percentages, phase definitions, or provider facts are persisted by this
target-lock work.

## 7. Risk And Safety

The existing safety model remains authoritative:

```text
analysis != authorization
Risk approval != execution
human authorization -> execution
timeout != rejection
unknown result -> no blind retry
manual provider divergence -> stop local mutation and reconcile
```

No cTrader SDK decision, endpoint implementation, credential creation, financial
mutation, or purchase decision is authorized here.

## 8. Dependency Graph

```text
ADR-071 target/boundary decision
    -> Story 0145 provider registration and account routing
        -> exact-account cTrader read-only adapter validation
            -> authoritative synchronization and Risk Facts proof
                -> FTMO phase/rule contract lock
                    -> controlled dry-run
                        -> pre-challenge readiness gate
                            -> human purchase decision
```

Story 0145 is the only next implementation Story selected by this report. It is
provider-neutral and must not be expanded into cTrader implementation.

## 9. Validation

Validated:

- repository baseline and current Git state;
- accepted ADR and Story conventions;
- existing provider-neutral architecture and current Kraken-only gaps;
- direct FTMO support evidence supplied for this decision;
- official cTrader Open API capability documentation;
- target separation between FTMO business semantics and cTrader execution.

Not validated:

- exact FTMO account API access;
- live or sandbox cTrader integration;
- FTMO rules and Challenge lifecycle;
- execution, reconciliation, event, restart, or instrument behavior;
- production code or runtime behavior.

DevLog was attempted but unavailable because the configured API account had no
remaining credits. Repository and external documentation evidence were used.

## 10. Changed Files

- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/stories/0145-generalize-provider-registration-and-account-routing/story.md`
- `docs/architecture/reports/ftmo-ctrader-target-lock.md`

No existing report was modified.

## 11. Final Gate

```text
========================================
FTMO + CTRADER TARGET LOCK
========================================

TARGET LOCK:
ACCEPTED

FTMO SUPPORT CONFIRMED:
YES

CTRADER OFFICIAL OPEN API:
DOCUMENTED

ACCOUNT-SPECIFIC API ACCESS:
UNVERIFIED

FIRST PROP TARGET:
FTMO + CTRADER

EXECUTION READY:
NO

READ-ONLY SYNCHRONIZATION:
NOT_VALIDATED

RISK FACTS:
PARTIAL

MULTI-PHASE CHALLENGE:
GAP

INSTRUMENT ALIGNMENT:
NOT_VALIDATED

FEES/SWAPS:
NOT_VALIDATED

EVENTS/RECONNECT:
NOT_VALIDATED

DRY-RUN:
NOT_RUN

HUMAN AUTHORIZATION:
REQUIRED

FAIL-CLOSED:
REQUIRED

IMPLEMENTATION AUTHORIZED:
NO

NEXT STORY:
0145 - Generalize Provider Registration and Account Routing

NEXT ACTION:
Human review of ADR-071 and Story 0145, then execute Story 0145 only after approval.
```
