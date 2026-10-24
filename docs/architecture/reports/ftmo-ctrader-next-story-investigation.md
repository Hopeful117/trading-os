# FTMO + cTrader Next Critical-Path Story Investigation

**Investigation date:** 2026-10-23
**Scope:** post-V1 critical-path selection for FTMO 2-Step through cTrader Open API
**Implementation:** none; documentation only

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `26f8832306c41938897c1fdd19a1c028e1d33882` |
| `origin/main` | `26f8832306c41938897c1fdd19a1c028e1d33882` |
| Working tree | pre-existing untracked ADR-071 and reports; no tracked modifications before this report |
| Latest merge | PR #163, `26f8832`, merge of Story 0145 |
| Latest merged ADR | ADR-070, commit `77eaa7f`, post-trade Decision History association |
| Latest merged Engineering Story | Story 0145, `dc2bde1`, Generalize Provider Registration and Account Routing |
| DevLog | attempted once; unavailable with `429 insufficient_quota`; repository and Git evidence are authoritative |

Important discrepancy: `docs/architecture/adr/ADR-071.md` exists as an untracked
local file, but it is absent from `HEAD`, `origin/main`, and the merge commit for
Story 0145. The FTMO/cTrader target-lock reports are also untracked. They are
useful local context, but they are not merged repository authority.

## Latest FTMO ADR

There is no merged FTMO/cTrader ADR in current `main`.

The local untracked `ADR-071 - FTMO Challenge Target and cTrader Broker Boundary`
is marked `Accepted` and contains the expected target and boundary decision, but
that status cannot be treated as a merged repository decision until the artifact
is committed through the repository workflow.

The latest actually merged ADR is `ADR-070 - Trade Outcome Association with PAPER
Trade Lifecycle`. It is unrelated to FTMO/cTrader target selection and preserves
the V1 PAPER Decision History boundary.

Consequently, the user-supplied statement that the first FTMO/cTrader ADR was
merged conflicts with the current repository. This investigation does not silently
resolve that contradiction.

## Latest Merged Story

### Story 0145

Story 0145, `Generalize Provider Registration and Account Routing`, was merged in
PR #163 at `26f8832`. Its implementation was verified against the merged code.

It established:

- a broker-neutral versioned v2 account snapshot contract;
- explicit local `BrokerAccount` routing identity;
- separate provider `externalAccountId`;
- owner-scoped Broker Service access;
- Trading Core resolution through `Account.brokerAccountId`;
- fail-closed missing, cross-owner, identity, provider, metadata and external
  account mismatch checks;
- preservation of the v1 synchronization path;
- provider registry duplicate and unsupported-provider coverage.

It did not establish:

- a second production provider identity;
- a cTrader adapter;
- cTrader OAuth or token lifecycle;
- cTrader account discovery or account selection;
- cTrader read-only synchronization;
- FTMO Challenge phase or rule semantics;
- instrument mapping, execution, event ingestion or reconciliation.

The physical repository confirms the limitation: both
`broker-service.connection.domain.BrokerProviderId` and
`trading-core.brokeraccount.domain.BrokerProvider` still contain only `KRAKEN`.
The Broker Service registry is capability-oriented, but its production composition
still contains only `KrakenBrokerProvider`.

## Current Architecture

The current intended boundary remains:

```text
FTMO
    = Challenge/business provider

cTrader
    = Broker/execution platform

Trading Core
    = Challenge lifecycle, deterministic Risk, authorization, execution state

Broker Service
    = credentials, authentication, provider capabilities and external facts
```

No cTrader or FTMO-specific code was added by Story 0145. No provider leakage was
found in the merged implementation.

The current identity graph is partially explicit:

```text
ChallengeInstance
    -> Account.accountId
        -> Account.brokerAccountId
            -> BrokerAccount.id
                -> BrokerConnection.brokerAccountId
                    -> externalAccountId
```

The relation is now used for v2 synchronization, but the physical provider remains
Kraken-only and no cTrader external account can yet be bound or validated.

## Updated Gap Matrix

| Gap | Current classification | Evidence after Story 0145 |
|---|---|---|
| FTMO immutable contract | `BLOCKED` | No merged FTMO ADR or validated contract artifact in `main` |
| Multi-phase Challenge | `UNCHANGED` | `ChallengeDefinition` and `ChallengeInstance` remain single-definition/single-terminal lifecycle |
| Minimum trading days | `UNCHANGED` | No deterministic qualifying-day model |
| Maximum Daily Loss semantics | `UNCHANGED` | Generic Risk day/baseline exists; FTMO reset, DST, cost and equality semantics are not locked |
| Maximum Loss semantics | `REDUCED` | Generic static drawdown rule exists; FTMO-specific semantics remain unverified |
| Fees and swaps | `UNCHANGED` | Generic ledger/fact shapes exist; cTrader deal-level authority is absent |
| Challenge headroom | `UNCHANGED` | Projection fields exist but remain null; no duplicate Risk formula is allowed |
| Second-provider foundation | `REDUCED` | Routing and contract boundary are provider-neutral, but production provider IDs and adapter composition are still Kraken-only |
| Account/BrokerAccount identity | `REDUCED` | Explicit relation and v2 fail-closed checks now exist; cTrader binding and cross-service lifecycle proof do not |
| cTrader authentication | `UNCHANGED` | Credential references and generic authentication capability exist; OAuth, token refresh, account selection and cTrader provider identity do not |
| Connection lifecycle | `UNCHANGED` | Basic status transitions exist; no cTrader session, event freshness, reconnect or degraded-state evidence |
| READ-ONLY synchronization | `REDUCED` | Generic v2 account snapshot path exists; no cTrader adapter or complete account/order/deal/position authority |
| Provider events | `UNCHANGED` | No neutral durable cTrader event ingestion or restart cursor |
| Execution protection/modify | `UNCHANGED` | Generic execution/cancel/close foundations exist; modify and protection contract gaps remain |
| Instrument mapping | `UNCHANGED` | Canonical/provider identity separation exists; no FTMO account/cTrader catalogue or equivalence evidence |
| Position sizing | `UNCHANGED` | Generic Risk inputs exist; cTrader volume, tick value, contract size, margin and stop constraints are unverified |
| Reconciliation | `REDUCED` | Generic unknown-safe reconciliation exists; cTrader order/deal/position mapping is absent |
| Market Data alignment | `UNCHANGED` | No cTrader/FTMO instrument coverage; full analytical Market Data is not required before account-only synchronization |

## Dependency Graph

The repository-derived hard path is currently blocked before implementation:

```text
FTMO + cTrader target decision
        |
        +--> ADR-071 must be merged into main
                    |
                    v
        cTrader provider identity and BrokerConnection foundation
                    |
                    v
        cTrader authentication and exact-account binding
                    |
                    v
        cTrader READ-ONLY account/order/deal/position synchronization
                    |
                    v
        complete, fresh provider facts -> RiskSnapshot / Risk Facts
                    |
                    v
        FTMO phase/rule contract and Challenge progress/headroom
                    |
                    v
        instrument/account execution constraints
                    |
                    v
        human-authorized execution
                    |
                    v
        provider-authoritative reconciliation and controlled dry-run
                    |
                    v
        PRE_CHALLENGE_READINESS
```

The first implementation dependency after the ADR gate is provider identity and
connection/account foundation. Authentication cannot safely be selected before
that foundation because the current production provider identity set is Kraken-only.

## FTMO Challenge Readiness

```text
FTMO_CHALLENGE_MODEL = BLOCKED
```

Current Challenge domain supports one immutable definition per instance and one
terminal transition to `PASSED` or `BREACHED`. It cannot express Phase 1 followed
by Verification, phase-specific immutable definitions, phase transitions, or
minimum trading-day eligibility. No validated FTMO rules are merged into `main`.

S1 and AI remain outside this critical path:

```text
S1_REQUIRED_FOR_FTMO_CHALLENGE = NO
AI_REQUIRED_FOR_FTMO_CHALLENGE = NO
```

## Second Provider Readiness

```text
SECOND_PROVIDER_FOUNDATION = PARTIAL
```

Story 0145 reduced the architectural and routing risk, but did not add a second
production provider. `BrokerProviderRegistry` can compose multiple implementations
and resolve by provider ID, while `BrokerProviderId`, Trading Core provider identity,
connection persistence and production composition still expose only Kraken.

## Account Identity Readiness

```text
ACCOUNT_IDENTITY = PARTIAL
```

Trading Core can now resolve the financial Account from the explicit
`brokerAccountId` relation and reject cross-owner or inconsistent v2 snapshots.
However, no exact external cTrader account can be selected, bound, authenticated,
or reconciled. ChallengeInstance does not itself carry a separate external provider
binding; it relies on the Account-to-BrokerAccount path.

## cTrader Authentication Readiness

```text
CTRADER_AUTH_STORY_READY = NO
```

The generic credential-reference and BrokerConnection lifecycle are reusable, but
the current production provider identity is Kraken-only and no cTrader OAuth,
application identity, token refresh/revocation, account discovery or selected-account
binding contract exists. A cTrader authentication Story would otherwise introduce
provider behavior before the provider/connection foundation is established.

## cTrader READ-ONLY Readiness

```text
CTRADER_READ_ONLY_STORY_READY = NO
```

The v2 snapshot endpoint and Trading Core synchronization path are reusable neutral
boundaries. The required cTrader adapter is not present, and the repository has no
proof for complete authoritative balance, equity, margin, positions, pending orders,
orders, deals, fees, swaps, freshness, completeness, reconnect or restart facts.

## Market and Instrument Readiness

Account-only synchronization requires an authoritative external account identity,
account metrics, positions, orders, deals and their provider references. It does not
require completing the entire analytical Market Data catalogue first.

Execution requires more:

- canonical-to-cTrader instrument mapping;
- account-available symbol equivalence;
- contract size, tick value, precision and volume units;
- minimum volume and step;
- margin/leverage and stop-distance constraints;
- execution-compatible price freshness.

Therefore:

```text
ACCOUNT_SYNC_INSTRUMENT_REQUIREMENTS = provider instrument identity and references only
EXECUTION_INSTRUMENT_REQUIREMENTS = full account/provider constraint mapping
MARKET_INSTRUMENT_FOUNDATION = PARTIAL
```

## Risk Readiness

```text
RISK_PROVIDER_FACT_BOUNDARY = READY
```

The generic Risk boundary can consume immutable account snapshots containing
balance, equity, margin, positions, closed trades, ledger facts, observation time,
completeness and freshness-related metadata. The domain enforces deterministic and
fail-closed behavior.

The provider fact source is not ready for cTrader:

```text
NO AUTHORITATIVE FACTS
    -> NO VALID RISK CONTEXT
        -> NO NEW EXECUTION
```

Risk formulas must not be changed or duplicated in Challenge or frontend code.

## Execution Readiness

```text
EXECUTION_FOUNDATION = PARTIAL
```

Current generic foundations can represent human-authorized market/limit execution,
cancel, close, idempotency and unknown outcomes. They do not prove cTrader support
for stop orders, protective SL/TP, modify, volume/precision, hedged/netted position
semantics or provider acknowledgement/fill mapping.

| Operation | Current readiness |
|---|---|
| Market order | Generic contract reusable; cTrader mapping absent |
| Limit order | Generic contract reusable; cTrader mapping absent |
| Stop order | Contract gap |
| Cancel | Generic capability exists; provider mapping absent |
| Modify | Generic capability gap |
| Stop loss / take profit | Protection contract gap |
| Close | Generic position-management boundary exists; cTrader target mapping absent |
| Partial fill | Generic status exists; cTrader deal aggregation absent |
| Unknown result | Safety semantics reusable |

Execution must remain after READ-ONLY synchronization.

## Reconciliation Readiness

```text
RECONCILIATION_FOUNDATION = PARTIAL
```

Trading Core already preserves `UNKNOWN` outcomes, no blind retry, idempotency and
provider-authoritative LIVE state. cTrader-specific order/deal/position identity,
event loss, restart convergence and manual platform action evidence remain absent.
Events may accelerate synchronization but cannot replace authoritative snapshots.

## Candidate Story Ranking

| Candidate | Hard dependency removed | Enables | Risk | Size | Critical-path rank |
|---|---|---|---|---|---|
| Merge/accept ADR-071 | Establishes target and FTMO/cTrader boundary in repository authority | Any target-specific Story without governance contradiction | Low | Small | Gate 0 |
| cTrader provider identity and connection/account foundation | Removes the remaining physical Kraken-only prerequisite | cTrader auth, selected-account binding and READ-ONLY adapter | Medium | Medium | 1 after ADR gate |
| cTrader authentication and exact-account binding | Provides credentials and external account selection | READ-ONLY synchronization | High | Medium/Large | 2; blocked by provider foundation |
| cTrader READ-ONLY synchronization | Establishes authoritative account facts | Risk facts, monitoring and later dry-run | High | Large | 3; blocked by auth and provider foundation |
| FTMO multi-phase Challenge model | Expresses Phase 1/Verification and phase transition | Challenge monitoring and completion rules | Medium | Medium/Large | Parallel domain work, not the immediate broker hard prerequisite |
| Challenge headroom projection | Exposes remaining limits from persisted Risk facts | Operator readiness | Low | Small | Downstream of authoritative facts |
| cTrader instrument identity foundation | Makes execution constraints authoritative | Safe sizing and execution | High | Medium | After READ-ONLY account/provider facts |

Because ADR-071 is absent from `main`, the first row is a required governance gate,
not an implementation Story. The second row is the first implementation Story that
would become eligible after that ADR is merged.

## Selected Next Story

```text
NEXT_ENGINEERING_STORY = BLOCKED_BY_ADR
```

The exact blocker is the missing merged FTMO/cTrader architecture ADR. Creating a
new Story now would either reference an unmerged decision or silently authorize a
cTrader provider foundation without a canonical target/boundary decision in `main`.

After ADR-071 is merged and accepted through the normal workflow, the single next
implementation Story should be:

```text
Story 0146 - Establish cTrader Provider Identity and Connection Account Foundation
```

That Story is not created by this investigation because its authorization depends
on resolving the current ADR contradiction. It should remain provider-neutral at
the Trading Core boundary and explicitly exclude OAuth token implementation,
read-only synchronization, execution, FTMO rules and Market Data integration.

## Story Created

```text
STORY_CREATED = NO
REASON = BLOCKED_BY_ADR
```

No production code, migrations, dependencies, SDK, credentials, Risk formula,
Challenge rule, execution path, reconciliation path or S1 behavior was changed.

## Files Changed

- `docs/architecture/reports/ftmo-ctrader-next-story-investigation.md`

The pre-existing untracked `ADR-071.md` and historical reports were not modified.

## Validation

- `git status --short --branch` inspected.
- `git log --oneline --decorate --graph -20` inspected.
- `git show` inspected for merge commit `26f8832` and Story 0145.
- `git ls-tree` verified that `ADR-071.md` is absent from `HEAD` and `ADR-070.md` is present.
- Story 0145 implementation report and merged production code inspected.
- Provider IDs, registry, resolver, BrokerAccount, BrokerConnection, Challenge,
  Risk and cTrader-relevant capability boundaries inspected.
- DevLog attempted once and failed with `429 insufficient_quota`.
- No production test suite was run because this task was documentation/architecture
  investigation only.

## Final Gate

========================================
FTMO + CTRADER NEXT STORY GATE
========================================

REPOSITORY BASELINE:
26f8832306c41938897c1fdd19a1c028e1d33882

LATEST FTMO ADR:
NONE MERGED; local untracked ADR-071 - FTMO Challenge Target and cTrader Broker Boundary

ADR STATUS:
BLOCKED - ADR-071 is accepted only in an untracked local artifact and is absent from main

LATEST MERGED STORY:
0145 - Generalize Provider Registration and Account Routing

MERGED STORY VERIFIED:
YES

FTMO TARGET:
LOCKED BY USER EVIDENCE, NOT REPRESENTED BY A MERGED ADR

ENGINEERING V1:
CLOSED

PRE_CHALLENGE:
ACTIVE

FTMO CHALLENGE MODEL:
BLOCKED

SECOND PROVIDER FOUNDATION:
PARTIAL

ACCOUNT IDENTITY:
PARTIAL

CTRADER AUTH STORY READY:
NO

CTRADER READ-ONLY STORY READY:
NO

RISK PROVIDER FACT BOUNDARY:
READY

MARKET/INSTRUMENT FOUNDATION:
PARTIAL

EXECUTION FOUNDATION:
PARTIAL

RECONCILIATION FOUNDATION:
PARTIAL

READ_ONLY_BEFORE_EXECUTION:
REQUIRED

S1 REQUIRED:
NO

AI REQUIRED:
NO

HIGHEST-PRIORITY HARD DEPENDENCY:
Merge and accept the FTMO+cTrader target/boundary ADR in main

NEXT ENGINEERING STORY:
BLOCKED_BY_ADR

STORY CREATED:
NO

NEXT ADR REQUIRED:
Merge/accept ADR-071 - FTMO Challenge Target and cTrader Broker Boundary

IMPLEMENTATION AUTHORIZED:
NO

NEXT HUMAN ACTION:
Merge ADR-071 into main through the normal ADR approval workflow
