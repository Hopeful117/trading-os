# FTMO 2-Step + cTrader Pre-Challenge Gap Investigation

**Investigation date:** 2026-10-23
**Scope:** repository-grounded architecture and pre-challenge readiness investigation
**Implementation:** none; production code, migrations, dependencies, enums and existing reports were not modified
**External confirmation:** pending

## 1. Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7` |
| `origin/main` | `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7` |
| Latest relevant merge | PR #162, `feat/v1-paper-closure` |
| Latest merged product Story | Post-trade Decision History V1 |
| Latest Broker capability Story | Story 0051, closed, human reviewed, committed and PAPER validated |
| Latest Risk Story | Story 0077, closed and human accepted |
| Latest Challenge Story | Story 0089, implemented and awaiting human review |
| Latest Broker connection Story | Story 0126, implementation completed; human commit pending |
| Latest execution/reconciliation Story | Story 0127, draft; not implemented |
| Latest relevant Market Data Story | Story 0096, implementation plan complete; awaiting human approval |
| Relevant ADRs | ADR-002, ADR-028, ADR-029, ADR-030, ADR-040, ADR-042, ADR-043, ADR-045, ADR-051, ADR-053, ADR-055, ADR-070 |
| Working tree | Pre-existing untracked reports are present; no tracked modifications |
| DevLog | Attempted once for this investigation; request timed out. Repository evidence was used instead. |

The existing untracked reports, including the three required prior investigations,
were treated as user-owned work and were not modified. The current repository and
accepted ADRs take precedence over historical report wording.

The closed V1 decision remains unchanged:

```text
ENGINEERING_V1 = ACCEPTED_WITH_RESIDUALS
V1_ENGINEERING_MILESTONE_CLOSED = YES
PRE_CHALLENGE_READINESS = CURRENT_PHASE
```

The repository does not contain a cTrader adapter, an FTMO definition, an FTMO
account classification, or a current FTMO contract artifact.

## 2. Target And Objective

```text
PROP_PROVIDER = FTMO
CHALLENGE = FTMO CHALLENGE
MODEL = 2-STEP
TRADING_PLATFORM = CTRADER
INTEGRATION_TARGET = CTRADER OPEN API
FTMO_CTRADER_OPEN_API_COMPATIBILITY = PENDING_EXTERNAL_CONFIRMATION
```

The objective is to determine whether the existing human-controlled Trading OS
loop can safely exercise authoritative synchronization, deterministic Risk,
explicit human authorization, broker execution, monitoring, close,
reconciliation and Decision History on an FTMO 2-Step cTrader account.

This report does not purchase an account, implement cTrader support, reopen V1,
or treat generic FTMO/cTrader compatibility as proven.

The required authority chain remains:

```text
analysis != authorization
TradePlan acceptance != execution
Risk approval != execution
human authorization -> execution
```

The FTMO inputs supplied for this investigation are external hypotheses requiring
current official verification:

```text
Phase 1 target       = 10%
Verification target  = 5%
Maximum Daily Loss   = 5%
Maximum Loss         = 10%, static
Minimum Trading Days = 4
Trading period       = unlimited
reset boundary       = associated with 00:00 CE(S)T, pending verification
```

No input above is persisted by this investigation.

## 3. V1 Capabilities Reusable Unchanged

The following capabilities can be reused in principle without making Trading Core
FTMO-aware:

| Capability | Current evidence | Reuse verdict |
|---|---|---|
| Decision Workspace | V1 closure and TradePlan orchestration | Reuse unchanged in principle |
| TradePlan lifecycle | Provider-neutral manual and opportunity plans | Reuse unchanged |
| Human plan acceptance | Separate from execution | Reuse unchanged |
| Deterministic Risk | Immutable context, versioned policies, explainable rules | Reuse engine and policy architecture |
| Explicit execution authorization | `ExecutionIntent` and authenticated execution action | Reuse unchanged in principle |
| Execution idempotency | Stable intent key, attempt lifecycle and terminal guards | Reuse with cTrader identity mapping |
| Unknown outcome safety | Unknown submission blocks blind retry | Reuse unchanged semantically |
| LIVE provider authority | ADR-042 assigns LIVE position truth to provider | Reuse unchanged |
| LIVE close boundary | Provider-neutral close command and reconciliation path | Reuse with cTrader position addressing |
| Decision History | ADR-070 association and truthful unavailable states | Reuse; add provider evidence only where proven |
| Ownership/security | Trading Core ownership, Broker Service secret isolation | Reuse unchanged |
| Market identity | `provider + canonical symbol + productType` | Reuse with cTrader mapping |
| Risk Snapshot shape | Balance/equity/margin/positions/closed trades/ledger | Reuse if cTrader supplies authoritative facts |
| S1 and AI | Neither is required for a human discretionary challenge loop | Explicitly out of critical path |

The current V1 proof is PAPER. It is not evidence that these capabilities are
already validated against a live FTMO account or cTrader execution semantics.

## 4. Broker Abstraction Map

### 4.1 Dependency direction

The intended and documented direction is:

```text
Trading Core
    -> broker-neutral Broker Service contracts
        -> BrokerProviderRegistry
            -> provider composition root
                -> provider capabilities, clients, mappers and error translation
                    -> external broker/platform
```

ADR-002 assigns broker abstraction, synchronization, execution and adapters to
Broker Service. ADR-030 prohibits provider DTOs, SDKs, authentication details and
provider errors from entering Trading Core. ADR-029 keeps execution lifecycle,
recovery, idempotency and reconciliation decisions in Trading Core.

### 4.2 Actual contracts

| Concern | Current contract/evidence | cTrader implication |
|---|---|---|
| Provider identification | `broker-service/.../connection/domain/BrokerProviderId.java`; only `KRAKEN` | Additions would be provider registration work, not FTMO coupling; no enum was added here |
| Trading Core provider | `trading-core/.../brokeraccount/domain/BrokerProvider.java`; only `KRAKEN` | Trading Core currently has a physical second-provider gap |
| Credentials | `BrokerConnection.activeCredentialReference`, `BrokerAccount.credentialReference`, encrypted Broker Service secrets | Can remain credential-blind in Trading Core; cTrader token material belongs in Broker Service |
| Authentication | `AuthenticationCapability`, Kraken credential session and validator | OAuth/application identity/token refresh require adapter-specific implementation |
| Account retrieval | `AccountCapability.account()` and `RiskSnapshotCapability.snapshot()` | Must map cTrader account metrics and preserve observation/completeness |
| Balance/equity | `AccountSnapshot` and `AccountRiskFacts` | Existing shape can carry balance/equity; provider semantics need validation |
| Positions | `PositionCapability`, `RiskPosition`, `PositionSnapshot` | Position ID, net/hedged semantics and protective orders need mapping proof |
| Orders | `OrderCapability`, `OrderSnapshot`, `OrderStatus` | Pending orders, provider IDs and event state need richer mapping validation |
| Deals/fills | `FillSnapshot`, `ClosedTrade` | cTrader deals/fills must map without losing partial executions or fees |
| Execution | `ExecutionCapability.execute(ExecutionRequest)` | Existing request supports MARKET/LIMIT only and lacks explicit SL/TP fields |
| Cancel/modify | Cancel exists in `OrderCapability`; no generic modify capability is present | Modify, SL/TP and pending-order modification are a contract gap |
| Close | `PositionManagementCapability` with resolve/execute/reconcile | Existing LIVE close shape is reusable if cTrader position IDs can be addressed safely |
| Risk snapshot | `RiskSnapshotCapability` includes account, positions, closed trades, ledger | Strongest reusable boundary; cTrader completeness and freshness are unproven |
| Technical constraints | `TechnicalCapability` and `MarginCapability` | Existing fields cover leverage and margin partially; Market Data remains metadata owner |
| Reconciliation | `ReconciliationCapability`, close reconciliation, Recovery Pipeline | Can preserve unknown-before-retry semantics; event and partial-fill coverage is incomplete |
| Connection identity | `BrokerConnection` stores provider, broker account ID, external account ID and status | Can represent one cTrader account reference, but session identity is not modeled |
| Connection status | `CREATED`, `PENDING_VALIDATION`, `CONNECTED`, `DISCONNECTED`, `REAUTHENTICATION_REQUIRED`, etc. | Basic lifecycle exists; `RECONNECTING` and `DEGRADED` are not explicit |

The abstraction is capability-oriented, which is the correct architectural shape
for a second provider. The physical repository is not yet multi-provider because
both provider identifiers and the Trading Core broker-provider enum are Kraken-only.

## 5. Kraken Coupling Audit

| Area | Classification | Evidence and consequence |
|---|---|---|
| Broker Service capability records | `GENERIC_CONTRACT` | `BrokerCapabilities` and `BrokerModels` use neutral names and provider-neutral statuses |
| Risk Snapshot account facts | `GENERIC_CONTRACT` | Balance, equity, margin, positions, closed trades and ledger entries are generic |
| Execution lifecycle | `GENERIC_CONTRACT` | `ExecutionRequest`, idempotency, unknown result and reconciliation are not Kraken-named |
| Position close lifecycle | `GENERIC_CONTRACT` | Opaque broker position reference and provider mutation scope are intentionally abstract |
| Technical capability metadata | `GENERIC_CONTRACT` | Instrument and leverage are represented as neutral technical facts |
| Historical fee schedule | `GENERIC_CONTRACT` | Provider/account/product/instrument scoping is generic, though current data is Kraken-derived |
| Provider registry | `GENERIC_CONTRACT` with Kraken-only registration | Registry resolves `BrokerProviderId`; adding another implementation is structurally normal |
| `BrokerProviderId.KRAKEN` | `KRAKEN_ADAPTER_DETAIL` at current implementation | Correct for current provider, but enum expansion is required for a second provider |
| Trading Core `BrokerProvider.KRAKEN` | `ACCIDENTAL_KRAKEN_COUPLING` | Core account creation and tests physically accept only Kraken; this must be generalized before a second provider |
| Kraken request signing | `KRAKEN_ADAPTER_DETAIL` | HMAC/session/signature code is isolated under Kraken infrastructure |
| Kraken endpoint clients and DTOs | `KRAKEN_ADAPTER_DETAIL` | `/0/private/*`, Kraken mappers and provider payloads remain below the Broker boundary |
| Kraken symbols such as `PF_XBTUSD` | `KRAKEN_ADAPTER_DETAIL` | Market Data preserves provider symbols below canonical identity per ADR-053 |
| Kraken asset normalization | `KRAKEN_ADAPTER_DETAIL` | Bitcoin aliases and Kraken asset prefixes are isolated in mapper/normalizer code |
| Spot/perpetual product mapping | `KRAKEN_ADAPTER_DETAIL` over a generic product contract | cTrader needs a separate adapter mapping to existing product types |
| Provider error semantics | `GENERIC_CONTRACT` plus Kraken translator | Error translation boundary is reusable; exact cTrader categories need adapter tests |
| Kraken FIFO/reduce-only close scope | `KRAKEN_ADAPTER_DETAIL` | Must not be copied into cTrader; cTrader position addressing remains adapter-owned |
| Kraken balance double-read and ledger acquisition | `KRAKEN_ADAPTER_DETAIL` | The coherent snapshot pattern is reusable; endpoint mechanics are not |
| Kraken-specific fee/funding acquisition | `KRAKEN_ADAPTER_DETAIL` | Must not define FTMO economics without authoritative cTrader account evidence |

No direct FTMO coupling is required. The key precondition is to remove or
generalize the current physical Kraken-only provider registration and account
creation assumptions in an approved multi-provider Story. This investigation does
not perform that refactor.

## 6. cTrader Protocol Fit

The future adapter must satisfy the following protocol requirements without
exposing cTrader types outside Broker Service:

| Protocol requirement | Current fit | Gap |
|---|---|---|
| OAuth/application authorization | Credential reference and authentication capability exist | Token grant, refresh, expiry and application identity are not implemented |
| Trading account selection | External account ID exists on connection/account | Account discovery and selected-account binding are not implemented |
| Persistent session/connection | Provider client abstraction exists | Current operational implementation is Kraken REST/session oriented; persistent cTrader session is not represented |
| Request/response operations | Capability contracts are synchronous and request-driven | Suitable for bounded commands, but stateful acknowledgement and events need adapter/runtime work |
| Real-time event stream | ADRs acknowledge asynchronous broker events; current Broker contracts expose no event stream | Provider event ingestion and durable/restart behavior are a bounded-to-major gap |
| Orders/deals/positions | Generic snapshots exist | cTrader-specific order, deal, position and pending-order semantics need mapping tests |
| Account metrics | Generic account/risk facts exist | Equity, margin, balance and fee semantics require authoritative validation |
| Reconnect/auth expiry | Basic connection statuses exist | No explicit reconnecting/degraded runtime state or durable provider event cursor |
| Rate limits | Error translation and resilience foundations exist | cTrader-specific quotas and safe read/write handling need adapter policy |
| Provider errors | Neutral error boundary exists | cTrader error taxonomy and unknown-outcome evidence need mapping |

The current Broker Service does not require Trading Core to become protocol-aware.
However, a cTrader adapter cannot be only a thin REST client if the intended
account synchronization depends on persistent account/order/position events.

Classification:

```text
CTRADER_ADAPTER_FIT = SMALL_ABSTRACTION_GAP
CTRADER_RUNTIME_FIT = BOUNDED_GAP
```

The final classification remains `INCONCLUSIVE` until FTMO confirms that the
selected Challenge account is accessible through the required Open API operations.

## 7. Authentication Fit

The current security boundary is suitable:

```text
Trading Core
    -> credential reference only
Broker Service
    -> encrypted secret/token storage and provider authentication
cTrader
```

Story 0126 confirms that Trading Core does not store broker secrets, credential
fields are write-only, and Broker Service owns validation, credential references
and secret persistence. ADR-030 explicitly includes OAuth and temporary-token
lifecycles inside the Broker Service authentication capability.

The cTrader implementation would need to represent, inside Broker Service only:

- application identity and redirect/authorization configuration;
- user authorization result;
- access-token and, if required, refresh-token storage;
- token expiry and refresh locking;
- selected cTrader trading account identity;
- authorization revocation and reauthentication;
- safe diagnostics without token or authorization-code leakage.

The current `BrokerConnection` can hold a credential reference, external account
ID and coarse technical status. It does not prove support for token metadata,
refresh state, application identity or multi-account authorization selection.

Classification:

```text
AUTHENTICATION = BOUNDED_GAP
TRADING_CORE_CREDENTIAL_VISIBILITY = READY
```

No OAuth implementation or secret was created.

## 8. Connection Lifecycle

Current status models cover `CREATED`, validation pending, connected, invalid
credentials, insufficient permissions, temporarily unavailable,
reauthentication required, disconnected and revoked. This is enough for basic
account connection workflows and fail-closed routing.

The stateful cTrader target additionally needs operationally observable states or
equivalents for:

```text
CONNECTED
DISCONNECTED
RECONNECTING
DEGRADED
AUTH_EXPIRED
```

The repository can express some of these as existing states, but it cannot
distinguish a healthy authenticated connection from a connected record with a
stale event stream. There is no provider-neutral last-event cursor, subscription
health, event freshness, or restart-safe session recovery contract.

Classification:

```text
CONNECTION_LIFECYCLE = BOUNDED_GAP
```

The gap is architectural only if cTrader requires persistent state to be shared
with Trading Core. It does not: session management can remain inside Broker
Service, while Trading Core receives neutral freshness and completeness facts.

## 9. Account Identity

The current accepted identity decision is:

```text
Account.accountId = financial Trading Core account identity
BrokerAccount.id  = execution/routing identity
Account -> BrokerAccount = required explicit durable relation
```

The current physical repository still has incomplete relation support documented by
ADR-043. `BrokerConnection` has `brokerAccountId` and an `externalAccountId`; the
Trading Core `BrokerAccount` also has an external account ID, but the canonical
financial Account-to-BrokerAccount relation is not fully implemented as the
identity authority.

The desired future graph is expressible conceptually without FTMO in Broker
Service:

```text
ChallengeInstance
    -> Trading Core Account
        -> BrokerAccount
            -> BrokerConnection
                -> cTrader external trading account ID
```

`ChallengeDefinition.provider` may contain a challenge business provider such as
FTMO, while Broker Service resolves a platform provider such as cTrader. The
repository does not currently have a separate `ChallengeProvider`/`BrokerProvider`
model, so this separation is a design direction, not an existing contract.

Classification:

```text
ACCOUNT_IDENTITY = BOUNDED_GAP
FTMO_IN_BROKER_SERVICE = NOT_REQUIRED
```

The identity gap is important for safe synchronization and must be addressed
before a real challenge, but no FTMO-specific identity should be introduced into
Broker Service.

## 10. Account Synchronization

The strongest existing boundary is `RiskSnapshotCapability.snapshot()`. It can
represent:

- balance and equity;
- margin;
- starting balance;
- positions and provider references;
- protective stops;
- closed trades and realized P&L;
- ledger entries and fees;
- completeness and unavailability reasons;
- observation time and snapshot version.

This is substantially reusable for cTrader. The current repository does not prove
that the full cTrader account supplies all facts in one coherent authority window,
nor that synchronization is continuously active for LIVE/Challenge accounts. The
existing Challenge monitoring path is explicitly PAPER-oriented in the previous
investigation.

Required cTrader facts for Challenge-grade synchronization are:

```text
balance
equity
used/free margin
open positions
pending orders
orders and status
deals/fills
realized P&L
unrealized P&L
commissions
swaps/other carrying costs
instrument constraints
observation time and freshness
```

Missing or incoherent facts must produce an incomplete snapshot and block new
execution. They must not become zero, empty exposure or a locally inferred
success.

Classification:

```text
GENERIC_ACCOUNT_SYNC = REUSABLE_WITH_ADAPTER
FTMO_CTRADER_ACCOUNT_SYNC = BOUNDED_GAP / BLOCKED_BY_FTMO_CONFIRMATION
```

## 11. Real-Time Event Implications

Current Trading Core execution and recovery already account for asynchronous
broker events conceptually, and ADR-029 lists order/fill/recovery events. The
current Broker Service public capability contracts are principally request-driven:

```text
account()
positions()
orders()
execute()
reconcile()
snapshot()
```

There is no current neutral event contract or durable event ingestion path for:

- order accepted or rejected;
- order modified or cancelled;
- deal/partial fill;
- position opened, modified or closed;
- equity/account changes;
- provider-side stop loss or take profit;
- manual platform action.

The smallest safe design is not a general event platform. It is a Broker Service
adapter/runtime that can consume cTrader events when available, update durable
provider-neutral synchronization evidence, and fall back to authoritative
request-driven snapshots after reconnect or restart. Trading Core should consume
neutral facts and retain its existing reconciliation decisions.

Classification:

```text
EVENT_CONSUMPTION = BOUNDED_GAP
EVENTS_AS_SOLE_AUTHORITY = NOT_ACCEPTABLE
```

Snapshots remain required for restart recovery and divergence proof even if an
event stream is available.

## 12. Execution

The current execution lifecycle is:

```text
TradePlan
    -> Risk
        -> human authorization
            -> ExecutionIntent
                -> ExecutionAttempt
                    -> Broker Service
                        -> provider submission
                            -> ExecutionResult
                                -> reconciliation / Trading Core state
```

This is provider-neutral and preserves the required human boundary. The current
Broker `ExecutionRequest` supports `MARKET` and `LIMIT`, side, quantity, optional
limit price, account ID and idempotency identity. `OrderStatus` supports
acknowledged, open, partially filled, filled, cancelled, rejected and unknown.

The cTrader adapter must additionally prove mapping for:

| Execution concern | Current state | cTrader gap |
|---|---|---|
| Market order | Supported generically | Map cTrader execution response and fill evidence |
| Limit order | Supported generically | Map price/precision and pending state |
| Stop order | Not represented by `OrderType` | Contract gap if required by intended TradePlans |
| Stop loss/take profit | Protective stop appears in Risk snapshot, not execution request | Modify/placement contract gap |
| Volume | Generic decimal quantity | Map cTrader volume units, minimum and step safely |
| Price precision | Market Data has price precision | Verify execution adapter consumes authoritative metadata |
| Provider order ID | `externalOrderId` | Adapter mapping required |
| Position ID | `brokerPositionReference` | Adapter mapping and close addressing required |
| Deal/fill ID | `FillSnapshot.fillId` | Adapter mapping required |
| Partial fill | `PARTIALLY_FILLED` exists | End-to-end persistence and reconciliation proof is missing |
| Cancel | Generic cancel capability exists | Verify state races and event/snapshot convergence |
| Modify | No generic modify capability | Bounded abstraction gap |
| Close | Generic position-management capability exists | cTrader position scope must be adapter-owned |

Classification:

```text
EXECUTION = REUSABLE_WITH_ADAPTER for current market/limit shape
EXECUTION_WITH_PROTECTION_AND_MODIFY = BOUNDED_GAP
```

No cTrader execution contract is authorized by this investigation.

## 13. Execution Uncertainty

The current safety semantics are appropriate:

```text
timeout != rejection
unknown result -> no blind retry
stable idempotency key
reconciliation before retry
```

These semantics are implemented in Trading Core and represented in Broker
neutral `Unknown`, `Inconsistent`, and close reconciliation results. They can map
to cTrader if the adapter retains sufficient provider evidence, including request
correlation, order ID, position ID and deal history.

The cTrader adapter must not convert a lost synchronous response into a rejection
when a provider event may arrive later. It must also avoid treating an event as a
complete fill unless the event or follow-up snapshot establishes quantity,
price, fees and resulting position state.

Classification:

```text
UNCERTAIN_EXECUTION_SAFETY = REUSABLE_WITH_ADAPTER
CTRADER_PROOF = DRY_RUN_REQUIRED
```

## 14. Reconciliation

The existing recovery architecture can reconcile an unknown submission by looking
up broker orders and deciding whether the outcome is reconciled, absent or
inconsistent. Close has a dedicated resolve/execute/reconcile path.

A cTrader adapter must reconcile at least:

```text
unknown order submission
open/pending order
accepted and filled order
partial fill
position opened or changed
manual platform close
manual SL/TP change
manual cancellation
stop-loss/take-profit execution
disconnect/reconnect
application restart
provider-side forced action
```

Generic Trading Core decisions are reusable. Provider-specific work is mapping
the cTrader order/deal/position graph into stable snapshots and ensuring that
reconciliation can still resolve an outcome after event loss or restart.

Classification:

```text
RECONCILIATION = REUSABLE_WITH_ADAPTER
CTRADER_RECONCILIATION_VALIDATION = DRY_RUN_REQUIRED
```

## 15. Manual Platform Actions

ADR-042 explicitly treats LIVE provider state as authoritative and allows the
provider to change position state outside Trading OS. This is the correct basis
for cTrader.

The current architecture can notice divergence when a later authoritative snapshot
differs from local projection, but it does not provide a complete provider-neutral
manual-action event model or Challenge-specific stop-trading state.

Required behavior after detecting divergence:

```text
stop new Trading OS mutation
    -> obtain fresh cTrader account/order/position snapshot
        -> classify external change
            -> update provider projection and Decision History
                -> require fresh Risk facts before new authorization
```

Manual cTrader actions must not be overwritten by local assumptions. Provider
liquidation, forced close, terminal Challenge action and direct platform changes
are all external facts requiring reconciliation.

Classification:

```text
MANUAL_ACTION_AUTHORITY = READY in principle
MANUAL_ACTION_DETECTION = BOUNDED_GAP
```

## 16. FTMO Challenge Domain Fit

The current domain has:

```text
immutable ChallengeDefinition
    -> provider/product/plan/capital/target/progression/settlement/instrument
        -> immutable Risk policy reference

ChallengeInstance
    -> one Account
    -> one definition ID/version
    -> one Risk policy ID/version
    -> ACTIVE / PASSED / BREACHED
```

This fits one immutable benchmark with one profit target. It does not model:

- a phase collection or phase sequence;
- Phase 1 and Verification as separate immutable definitions;
- phase transition evidence;
- minimum trading days;
- funded-state progression;
- provider-side Challenge terminal state;
- account-to-Challenge external identity;
- provider lifecycle actions after pass or breach.

The smallest clean direction is likely one immutable definition per phase plus a
local progression/instance relation that records ordered phases and transitions.
That is an architectural direction only. A generic workflow engine is not
justified, and this report does not choose a model or create a definition.

Classification:

```text
CURRENT_SINGLE_PHASE_FIT = PARTIAL
FTMO_2STEP_MODEL = GAP
```

## 17. Static Maximum Loss

The current generic static total drawdown rule consumes total drawdown relative to
the immutable account starting balance and uses inclusive breach semantics:

```text
starting balance = challenge initial capital
static floor = starting balance * (1 - 0.10)
equality at floor = breach
profits do not move the floor
```

This matches the supplied static Maximum Loss model if official FTMO verification
confirms the percentage and equality boundary. The implementation is provider
neutral and already tested for the generic static rule.

Classification:

```text
STATIC_MAXIMUM_LOSS = READY for the generic static model
FTMO_SEMANTIC_COMPATIBILITY = INCONCLUSIVE pending official verification
```

## 18. Maximum Daily Loss

The current architecture already separates:

- `RiskDay`;
- account-configured `riskTimeZone`;
- account-configured `riskDayResetTime`;
- persisted `DailyRiskBaseline`;
- current equity;
- realized daily P&L;
- open-position valuation;
- immutable Risk context.

`DailyRiskBaseline` contains reference balance, effective timestamp, source and
provenance. `ObservedMetricsCalculator` derives floating P&L as equity minus
balance and uses daily closed P&L, margin and free margin in observed metrics.

The supplied FTMO reset statement must not be implemented as a simple fixed
offset or as an unverified `Europe/Prague` configuration. The correct future
architecture is:

```text
verified provider reset semantics
    -> explicit provider-neutral RiskDay boundary policy
        -> DST-aware instant interval calculation
            -> authoritative baseline at boundary
                -> immutable RiskEvaluationContext
```

The provider rule must establish whether “CE(S)T” means a named civil timezone,
which account/server clock is authoritative, what happens at DST transitions,
and whether balance/equity, commissions and swaps are included at the boundary.

Classification:

```text
MAXIMUM_DAILY_LOSS = GAP
```

The generic model is reusable, but FTMO compatibility is not proven until reset,
equity, open P&L, commissions, swaps and equality semantics are independently
verified.

## 19. Fees And Swaps

The current Risk account snapshot can consume balance, equity, realized P&L,
ledger entries and fees where the Broker adapter provides them. The generic
economic path can also represent projected trade costs when supplied.

This is not yet a complete challenge-grade cost model. The following distinction
must remain explicit:

```text
projected cost before execution
    != observed provider commission/swap after execution
```

For cTrader, the adapter must provide authoritative deal-level commissions,
swaps/carrying costs and ledger effects. The Risk/Challenge projection must use
those facts without duplicating accounting formulas or silently substituting a
generic fee schedule.

Classification:

```text
FEES = BOUNDED_GAP
SWAPS = BOUNDED_GAP
```

Official FTMO fee treatment and cTrader account evidence remain external
verification requirements.

## 20. Minimum Trading Days

The current Challenge domain has no deterministic concept for:

- a qualifying trading day;
- a phase trading-day counter;
- minimum trading-day eligibility;
- a day definition tied to a verified provider reset;
- phase completion eligibility separate from profit target.

This rule belongs in the Challenge domain, not the Broker adapter and not the
generic Risk engine. The Broker supplies authoritative deals/trades and timestamps;
Challenge application logic determines whether they qualify under the immutable
phase definition.

Classification:

```text
MINIMUM_TRADING_DAYS = GAP
```

The supplied value of four remains an external input pending official verification.

## 21. Multi-Phase Progression

`ChallengeDefinition` has one `profitTargetRatio`, one progression source and one
immutable version. `ChallengeInstance` references one definition and transitions
only from ACTIVE to PASSED or BREACHED. `ChallengeProgressService` calculates one
target from that definition.

Therefore it cannot represent both:

```text
Phase 1       = 10%
Verification  = 5%
```

without incorrectly mutating an immutable definition or treating Verification as
an unrelated challenge. The cleanest bounded direction is phase-specific immutable
definitions with an ordered Challenge progression model, retaining immutable
per-phase Risk policy references and transition evidence. Whether funded state is
inside the same product lifecycle requires official FTMO contract verification.

Classification:

```text
MULTI_PHASE_CHALLENGE = GAP
```

## 22. Challenge Headroom

`ChallengeProgress` already contains:

```text
dailyReferenceBalance
dailyLossRemaining
totalDrawdownRemaining
```

but `ChallengeProgressService` currently returns all three as `null`. This is an
intentional authority-preserving gap: the UI does not recreate Risk formulas.

The previous recommendation remains valid and is more valuable for FTMO+cTrader:

> Project Challenge headroom from the latest complete persisted Risk evaluation
> and its authoritative facts; do not duplicate Risk formulas in Challenge or
> Angular.

For cTrader, the authoritative Risk Snapshot could make this projection useful,
provided freshness, completeness, reset baseline and fee/swap evidence are all
available. This should be an early provider-neutral FTMO readiness Story, but it
must not be implemented before the relevant Story and authority contract are
approved.

Classification:

```text
CHALLENGE_HEADROOM = GAP
EARLY_FTMO_STORY = RECOMMENDED
```

## 23. Challenge State Versus Provider State

The architecture should distinguish:

```text
LOCAL_CHALLENGE_STATE
    = Trading Core definition, phase, progress, local evaluation evidence

PROVIDER_ACCOUNT_STATE
    = cTrader account metrics, orders, deals, positions and platform status
```

The current repository has the first partially and the second through generic
Broker facts, but no durable external Challenge lifecycle identity or provider
terminal state.

FTMO should remain a Challenge/business provider concern. cTrader should remain a
Broker/execution provider concern. Trading Core can associate a ChallengeInstance
with a BrokerAccount without putting FTMO lifecycle in Broker Service.

Classification:

```text
STATE_SEPARATION = SUPPORTED_BY_ARCHITECTURE
STATE_IMPLEMENTATION = BOUNDED_GAP
```

## 24. Market Data And Instrument Alignment

Market Data currently has a generic `Market` model with:

```text
provider
canonical symbol
providerSymbol
base/quote assets
productType
MarketConstraints
MarketState
```

The persistence identity is `provider + symbol + productType`, consistent with
ADR-053. `MarketConstraints` currently includes minimum order size, minimum cost,
tick size, quantity precision and price precision.

The current concrete provider coverage is Kraken Spot and the in-progress Kraken
linear-perpetual path. There is no cTrader or FTMO instrument catalogue and no
repository proof of the FTMO account's available symbols.

The required FTMO instrument categories are external inputs, not current facts:

```text
FOREX
INDEX/CFD
METAL/COMMODITY
CRYPTO CFD
```

The generic product vocabulary must be checked against actual repository enum
values and cTrader/FTMO instrument semantics before any definition is locked. No
new enum is added here.

Classification:

```text
MARKET_DATA_ALIGNMENT = INSUFFICIENT for FTMO+cTrader
```

## 25. Market Data Provider Strategy And Identity Mapping

The architecture permits market-data provider and execution provider to differ.
ADR-053 specifically separates canonical symbol from provider request symbol and
requires product-aware routing.

The future mapping must preserve:

```text
Trading OS canonical instrument
    -> market-data provider symbol and product identity
    -> cTrader provider symbol for execution
    -> FTMO account-available instrument identity
```

This requires an explicit equivalence/mapping fact. A display-symbol match is not
sufficient because aliases, contract sizes, quote currencies, CFD specifications,
sessions and account-specific availability can differ.

For pre-trade Risk and execution validation:

```text
analysis price       = analytical observation, not execution authority
planning price       = TradePlan assumption, not fill truth
Risk price           = timestamped valuation input used by deterministic Risk
execution price      = provider/venue quote used to validate immediate order
fill price           = authoritative cTrader deal/fill fact
position valuation   = current authoritative/provider-compatible valuation input
```

The cTrader account must be authoritative for order acknowledgement and fill
price. Market Data may remain primary for analysis, but execution-sensitive
validation needs venue/account alignment or a fail-closed freshness/mismatch
policy.

Classification:

```text
PROVIDER_SEPARATION = SUPPORTED_BY_ARCHITECTURE
INSTRUMENT_MAPPING = BOUNDED_GAP
EXECUTION_PRICE_AUTHORITY = BOUNDED_GAP
```

## 26. Instrument Constraints And Position Sizing

The current Market Data constraints cover minimum quantity/cost, tick size and
precision. Broker Service can provide technical capabilities and margin preview.
Risk sizing consumes risk amount, entry, stop, instrument information and margin
facts through provider-neutral inputs.

The following cTrader requirements are not yet proven:

- contract size;
- tick value and currency conversion;
- volume units versus lots;
- minimum volume and volume step;
- price precision and tick size per symbol;
- margin/leverage by side and account;
- minimum stop distance;
- hedged versus netted position behavior;
- CFD overnight swap and market-session constraints.

There is no evidence that current sizing contains a mandatory Kraken crypto
assumption in the generic Risk domain. Kraken-specific normalization is isolated
in adapters. Nevertheless, cTrader sizing cannot be declared ready until the
provider-neutral input is populated from verified instrument metadata and the
conversion formulas are tested.

Classification:

```text
POSITION_SIZING = PARTIAL
INSTRUMENT_METADATA = PARTIAL
CTRADER_ORDER_CONSTRAINTS = BOUNDED_GAP
```

## 27. Risk Fail-Closed Behavior

ADR-028 and ADR-043 establish the required behavior:

```text
NO AUTHORITATIVE FACTS
    -> incomplete Risk context
        -> no authorization
```

The current architecture already blocks or marks incomplete cases for unavailable
Risk snapshots, missing margin, stale market valuation, invalid configuration and
inconsistent account facts. The intended FTMO+cTrader admission must extend this
to:

- disconnected or degraded cTrader session;
- expired authorization;
- stale account snapshot;
- stale or missing positions/orders/deals;
- missing balance/equity/margin;
- incomplete commission/swap evidence where required by the rule;
- stale or mismatched execution market data;
- unknown Challenge phase or provider account mapping;
- unavailable Challenge headroom;
- unavailable Risk or persistence.

The current implementation does not prove a single global LIVE Challenge kill
switch across every service failure. It provides the right local fail-closed
Risk and unknown-execution primitives.

Classification:

```text
FAIL_CLOSED_SAFETY = PARTIAL
```

## 28. Security

The existing security boundary is compatible with the target:

- Trading Core remains credential-blind;
- Broker Service owns provider credentials and token access;
- secrets are encrypted and not returned to the frontend;
- service-to-service calls use service authentication;
- user ownership remains in Trading Core;
- provider payloads and tokens stay inside Broker Service.

The future cTrader implementation must additionally protect authorization codes,
access tokens, refresh tokens and account-selection data. Token refresh and
revocation must be serialized safely and must not be logged or exposed through
diagnostics. A provider token must never become a Trading Core credential field or
Decision History payload.

Classification:

```text
SECURITY_BOUNDARY = READY
CTRADER_TOKEN_LIFECYCLE = BOUNDED_GAP
```

## 29. Observability

Existing Broker Service monitoring and operational metrics can be reused as a
foundation. A stateful cTrader integration additionally requires neutral telemetry
for:

```text
connection status
last successful authorization/refresh
last provider event
last account sync
last position/order sync
snapshot completeness
execution request and acknowledgement correlation
reconciliation status
disconnect/reconnect
authorization expiry
rate limiting
provider availability
```

Telemetry must identify account and correlation identities without exposing
credentials, tokens or raw sensitive provider payloads. The current repository has
provider health and operation metrics but no single Challenge operator view proving
all controls are healthy.

Classification:

```text
OBSERVABILITY = BOUNDED_GAP
```

## 30. Full Capability Matrix

Classification vocabulary is used as requested. `BLOCKED_BY_FTMO_CONFIRMATION`
means the capability cannot be safely valued as an implementation target until
the selected FTMO account contract is confirmed.

| Capability | Current Trading OS | FTMO+cTrader requirement | Gap | Classification |
|---|---|---|---|---|
| Challenge definition | Immutable single-phase definition | FTMO 2-Step phase-specific immutable contract | No phase sequence or current FTMO definition | BOUNDED_GAP |
| Multi-phase challenge | One definition per instance | Phase 1 -> Verification, potentially funded | Domain does not model ordered phases | MAJOR_GAP |
| Static Maximum Loss | Generic static starting-balance drawdown, inclusive boundary | 10% static floor if verified | Provider semantics and value unverified | READY / BLOCKED_BY_FTMO_CONFIRMATION |
| Maximum Daily Loss | RiskDay, baseline and daily drawdown rule | 5% with verified DST-aware reset and equity semantics | Provider reset/cost semantics unverified | BOUNDED_GAP |
| Minimum trading days | Not modeled | Four qualifying days if verified | No deterministic Challenge counter | BOUNDED_GAP |
| Fees/swaps | Partial fee/ledger/economic facts | Authoritative cTrader commissions and swaps in monitoring | No complete Challenge cost authority | BOUNDED_GAP |
| Challenge headroom | Projection fields exist but return null | Authoritative daily/total remaining values | Must project from persisted Risk facts | BOUNDED_GAP |
| Broker authentication | Credential references, encrypted storage, auth capability | OAuth/token/account authorization | Token lifecycle and cTrader adapter absent | BOUNDED_GAP |
| Connection lifecycle | Basic connected/disconnected/reauth states | Persistent session, reconnect, degraded and auth expiry | No event/session freshness model | BOUNDED_GAP |
| Account sync | Generic Risk Snapshot boundary | Complete cTrader authoritative snapshots | No cTrader adapter or Challenge LIVE proof | BLOCKED_BY_FTMO_CONFIRMATION |
| Balance/equity | Generic snapshot fields | Current authoritative cTrader values | Provider semantics/freshness unproven | REUSABLE_WITH_ADAPTER |
| Positions | Generic positions and provider references | cTrader positions including external changes | Mapping and live validation absent | REUSABLE_WITH_ADAPTER |
| Orders | Generic order snapshots and statuses | Market/pending orders and lifecycle | Stop/modify richness and event ingestion gap | BOUNDED_GAP |
| Deals/fills | Generic fills and closed trades | cTrader deals, partial fills, fees | Adapter mapping and persistence proof absent | REUSABLE_WITH_ADAPTER |
| Execution | Human-authorized Broker capability path | cTrader order submission | Provider adapter and operation proof absent | BLOCKED_BY_FTMO_CONFIRMATION |
| Modify/cancel | Cancel exists; modify not generic | Cancel and SL/TP/order modification | Generic modify/protection contract gap | BOUNDED_GAP |
| Close | LIVE provider close and reconciliation boundary | cTrader position close | Adapter position addressing and dry-run absent | REUSABLE_WITH_ADAPTER |
| Reconciliation | Unknown-safe recovery and close reconciliation | Event/snapshot reconciliation after all divergence | cTrader identity/event proof absent | DRY_RUN_REQUIRED |
| Manual action detection | Divergence can be observed on later snapshots | Detect and stop on platform-side actions | No complete event/state model | BOUNDED_GAP |
| Market Data | Kraken Spot and in-progress derivative coverage | FTMO-available cTrader instrument coverage | No cTrader/FTMO catalogue | INSUFFICIENT |
| Instrument mapping | Canonical/provider-symbol separation | Canonical to cTrader/FTMO account symbol mapping | Account-specific equivalence unproven | BOUNDED_GAP |
| Position sizing | Generic risk and partial metadata/margin | Contract/tick/volume/margin-correct sizing | cTrader metadata and formulas unvalidated | PARTIAL |
| Decision History | V1 account-scoped history and provenance | Full provider order/deal/position evidence | cTrader event and external-action evidence absent | REUSABLE_WITH_ADAPTER |

## 31. Failure Matrix

| Failure | Required detection | Expected fail-closed behavior | Recovery evidence |
|---|---|---|---|
| Authentication failure | cTrader authorization/token validation failure | No account sync or execution; mark connection unusable | Corrected authorization and fresh account validation |
| Authorization expiry | Token expiry or provider auth rejection | Stop new execution; require refresh/reauthorization | Refreshed token, selected account revalidated |
| Disconnect | Session/socket health and missed event/snapshot freshness | No new execution or close unless emergency provider path is used | Reconnect plus complete authoritative snapshot |
| Reconnect | Session restoration and account identity check | Do not trust event continuity until snapshot convergence | Snapshot/order/position reconciliation and freshness |
| Provider unavailable | Transport/availability and health signals | No new execution; preserve unknown outcomes | Provider availability and reconciliation evidence |
| Rate limit | Provider quota response or local limiter | Do not blindly retry writes; defer safe reads and execution | Bounded retry policy and fresh state |
| Stale account | Snapshot observation age/completeness | Risk context unavailable; no new execution | Complete current balance/equity/margin snapshot |
| Stale market | Market freshness/tradability and venue mismatch | No new Risk approval or execution | Fresh execution-compatible price and market state |
| Submission timeout | No definitive provider acknowledgement | Mark UNKNOWN; no blind retry | Order/deal/position reconciliation |
| Unknown execution | Ambiguous response/event loss | Block retry and additional exposure | Provider order and position evidence |
| Duplicate request | Stable intent/idempotency key | Reject duplicate logical execution | Existing provider order or terminal intent state |
| Partial fill | Filled quantity differs from requested quantity | Persist/represent partial state; do not assume full fill | Deal aggregation and resulting position snapshot |
| Order rejection | Provider response/event with translated reason | Terminal rejected outcome; no local success | No residual order/exposure or explicit reconciliation |
| Manual provider action | Event or snapshot divergence | Stop Trading OS mutation and re-evaluate | Fresh provider state and Decision History evidence |
| Restart during exposure | Startup scan and account reconciliation | No new execution until account/positions/orders converge | Complete post-restart snapshot and Risk facts |
| Close uncertainty | Timeout/event loss during close | UNKNOWN close; do not repeat close blindly | Position/order/deal reconciliation |
| Risk unavailable | Missing facts, incomplete context or service failure | Reject/withhold authorization | New complete immutable Risk evaluation |
| Database unavailable | Persistence failure/health signal | Do not report completed local state; stop new execution | Durable intent/attempt state plus provider reconciliation |

## 32. External-Confirmation Boundary

The following must be confirmed in writing by FTMO before cTrader implementation is
treated as a real target:

1. A purchased or evaluation FTMO Challenge 2-Step account can use cTrader Open
   API, not merely the cTrader platform generally.
2. The API can access the exact Challenge account and selected trading account.
3. Read access exists for balance, equity, margin, positions, pending orders,
   orders, deals/fills, commissions, swaps and relevant ledger/account state.
4. Write access exists for the required market/pending order, cancel, modify and
   close operations.
5. Real-time events or an equivalent authoritative polling contract exists for
   order, deal, position and account changes.
6. OAuth/application authorization, account selection, token lifetime and refresh
   behavior are permitted for this account type.
7. FTMO Challenge loss rules, reset boundary, equity/balance semantics, fees,
   swaps, minimum trading days and phase transitions match the intended model.
8. FTMO provider-side actions, stop-out/liquidation and pass/breach closure can be
   observed or reconciled.

Until then:

```text
FTMO_CTRADER_OPEN_API_COMPATIBILITY = PENDING_EXTERNAL_CONFIRMATION
```

Neither FTMO support for cTrader nor generic cTrader Open API capability proves
the account-specific contract.

## 33. Work Possible Before FTMO Confirmation

The following work can be investigated or implemented independently, subject to
separate approved Stories and any required ADRs:

- generalize the physical Kraken-only provider registration into the existing
  provider-neutral capability boundary, without creating FTMO-specific classes;
- complete the canonical Account-to-BrokerAccount identity relation required by
  ADR-043;
- project Challenge headroom from authoritative persisted Risk evaluation facts;
- define and test a generic phase-capable Challenge model only after agreeing the
  smallest domain extension;
- define a provider-neutral modify/protective-order contract if the product scope
  requires it;
- improve neutral connection health/freshness evidence without choosing an SDK;
- prepare cTrader protocol acceptance fixtures using synthetic/provider-safe data;
- prove generic Risk boundary semantics for static drawdown, inclusive equality,
  DST-aware reset calculations and fee/swap inputs using provider-neutral tests;
- prepare instrument identity and execution-price authority contracts;
- prepare the dry-run runbook, failure matrix and operator evidence template;
- define neutral observability fields for account sync, events and reconciliation.

These are candidate bounded Stories, not authorization to implement all of them in
one change.

## 34. Work Blocked Pending FTMO Confirmation

The following depends specifically on FTMO Challenge account access and must wait:

- selecting cTrader Open API as the real execution integration target;
- implementing OAuth/account authorization for the FTMO Challenge account;
- binding a real FTMO Challenge account to a cTrader external account;
- implementing cTrader account/position/order/deal synchronization for Challenge;
- implementing cTrader order placement, cancel, modify or close for Challenge;
- binding provider fees/swaps and account metrics into Challenge Risk;
- proving event subscriptions and reconnect behavior for the selected account;
- validating manual platform-action detection against FTMO account state;
- locking FTMO 2-Step definitions, percentages, reset semantics and minimum days;
- executing any FTMO Challenge dry-run that relies on account-specific access;
- purchasing the FTMO Challenge.

No cTrader SDK/library decision is made here.

## 35. Minimum Critical Path If API Is Confirmed

If FTMO confirms full required compatibility, the minimum evidence-driven path is:

1. Preserve the external confirmation as an approved current FTMO/cTrader account
   contract, including permissions, facts, writes, events and lifecycle rules.
2. Lock immutable FTMO 2-Step phase definitions and Risk policy versions after
   independently verifying official rules, including daily reset/DST and
   equality semantics.
3. Approve any required ADR for multi-phase Challenge progression, account
   identity, connection lifecycle or Broker contract changes.
4. Generalize provider registration and account routing without leaking FTMO into
   Trading Core or Broker Service provider contracts.
5. Implement the cTrader authentication and connection foundation inside Broker
   Service, including token lifecycle, account selection, health and reconnect.
6. Implement read-only cTrader account, order, deal and position synchronization
   with completeness, freshness and restart evidence.
7. Route authoritative cTrader facts through the neutral Risk Facts boundary and
   validate static loss, daily loss, fees/swaps, margin, minimum days and headroom.
8. Implement cTrader execution, cancel/modify/close only for the confirmed
   capability set, preserving explicit human authorization and unknown-result
   semantics.
9. Implement event/snapshot reconciliation for partial fills, manual actions,
   restarts, uncertain submissions and uncertain closes.
10. Expose phase progress/headroom from authoritative persisted facts and preserve
    Decision History provenance.
11. Complete a controlled demo or dry-run with no Challenge financial exposure,
    including failure/recovery and official-platform emergency procedures.
12. Pass the pre-challenge readiness gate and only then make the human purchase
    decision.

## 36. If FTMO Says No

If FTMO confirms that Challenge accounts cannot use cTrader Open API, the following
assumption becomes invalid:

```text
Trading OS -> Broker Service -> cTrader Open API -> FTMO Challenge account
```

The provider-neutral Trading OS loop, Risk, Decision Workspace and human
authorization remain valid. The full authoritative execution/synchronization loop
would not be available through this target. A manual cTrader/FTMO decision-support
workflow could be considered only with explicit limitations and manual provider
reconciliation; it would not satisfy the full capability objective established by
this investigation. The human must review whether FTMO remains a useful first
challenge candidate rather than the coding agent selecting another provider.

## 37. Architecture Conclusion

**Required conclusion:**

```text
YES_WITH_BOUNDED_ABSTRACTION_CHANGES
```

cTrader can fit as a normal Broker Service provider adapter while Trading Core and
the Challenge domain remain independent of FTMO. The evidence is:

- ADR-030 explicitly defines provider isolation, capability composition,
  authentication encapsulation, mapping and error translation;
- Broker-neutral contracts already represent account, position, order, fill,
  execution, risk snapshot, margin and close facts;
- ADR-029 and ADR-042 keep lifecycle authority, human authorization, recovery and
  LIVE provider position truth outside provider implementations;
- ADR-043 separates financial Account identity from BrokerAccount routing identity;
- ADR-053 already establishes canonical versus provider symbol separation.

The bounded changes are real: current provider enums and account creation are
Kraken-only; event/session freshness is not modeled; modify/protective-order
contracts are incomplete; Challenge is single-phase; and account identity is not
fully implemented. These are bounded abstraction/domain gaps, not evidence that a
new FTMO-specific service or Trading Core adapter is required.

## 38. Product Conclusion Versus Kraken Prop

```text
PRODUCT CONCLUSION VS KRAKEN PROP = YES
```

This is an engineering/product integration conclusion only and makes no
profitability claim. If FTMO confirms the required cTrader Open API access,
FTMO+cTrader is a stronger first real challenge candidate than Kraken Prop for
testing the complete intended loop because:

- Kraken Support has explicitly confirmed `KRAKEN_PROP_API_ACCESS = NOT_AVAILABLE`;
- cTrader Open API is at least a plausible official execution and synchronization
  boundary, subject to FTMO account confirmation;
- the existing Broker, Risk, Challenge, execution and reconciliation architecture
  can be exercised against a second execution provider;
- cTrader/FTMO would test provider-neutral account, instrument, event and
  reconciliation assumptions rather than only the existing Kraken adapter.

The conclusion is conditional. If FTMO denies Challenge Open API access, the
comparison becomes `INCONCLUSIVE` for the full-loop objective and requires human
review.

## 39. Recommendation

Do not implement cTrader or FTMO support while compatibility is pending. Preserve
the closed V1 milestone. The highest-value next engineering work is a bounded,
provider-neutral Story for authoritative Challenge headroom and the account
identity/facts boundary, followed by a separate cTrader provider ADR only after
FTMO's written response.

```text
S1_REQUIRED_FOR_FTMO_CHALLENGE = NO
AI_REQUIRED_FOR_FTMO_CHALLENGE = NO
```

## 40. Final Gate

```text
========================================
FTMO 2-STEP + CTRADER PRE-CHALLENGE GATE
========================================

REPOSITORY BASELINE:
a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7

TARGET:
FTMO CHALLENGE 2-STEP + CTRADER

TARGET STATUS:
CANDIDATE

FTMO CTRADER OPEN API:
PENDING_EXTERNAL_CONFIRMATION

V1 CORE LOOP REUSE:
PARTIAL

BROKER ABSTRACTION:
BOUNDED_CHANGES

CTRADER ADAPTER FIT:
SMALL_ABSTRACTION_GAP

AUTHENTICATION:
BOUNDED_GAP

CONNECTION LIFECYCLE:
BOUNDED_GAP

ACCOUNT SYNCHRONIZATION:
BOUNDED_GAP

EXECUTION:
REUSABLE_WITH_ADAPTER

RECONCILIATION:
REUSABLE_WITH_ADAPTER

STATIC MAXIMUM LOSS:
INCONCLUSIVE

MAXIMUM DAILY LOSS:
INCONCLUSIVE

MINIMUM TRADING DAYS:
INCONCLUSIVE

MULTI-PHASE CHALLENGE:
GAP

FEES/SWAPS:
GAP

CHALLENGE HEADROOM:
GAP

MARKET DATA ALIGNMENT:
INSUFFICIENT

INSTRUMENT/SIZING ALIGNMENT:
PARTIAL

DECISION HISTORY:
PARTIAL

FAIL-CLOSED SAFETY:
PARTIAL

S1 REQUIRED:
NO

AI REQUIRED:
NO

FULL TRADING OS LOOP POSSIBLE IF API CONFIRMED:
YES

ARCHITECTURE CONCLUSION:
YES_WITH_BOUNDED_ABSTRACTION_CHANGES

PRODUCT CONCLUSION VS KRAKEN PROP:
YES

CAN WORK NOW:
Provider-neutral provider registration corrections; Account-to-BrokerAccount identity and Risk Facts boundary; authoritative Challenge headroom projection; generic phase and reset semantics analysis; neutral cTrader protocol fixtures; dry-run and observability preparation.

BLOCKED PENDING FTMO CONFIRMATION:
FTMO account authorization; cTrader account sync; cTrader execution/cancel/modify/close; provider event/reconnect validation; FTMO rule locking; FTMO-specific fee/swap and instrument binding; Challenge dry-run and purchase.

MINIMUM CRITICAL PATH IF CONFIRMED:
Lock verified FTMO contract -> approve phase/Risk model -> approve provider ADR -> generalize provider boundary -> implement authentication/connection -> read-only synchronization -> Risk/headroom -> execution -> reconciliation -> controlled dry-run -> readiness gate -> human purchase decision.

PRE-CHALLENGE ENGINEERING STATUS:
BOUNDED_GAPS

NEXT ENGINEERING STORY:
Define and implement the smallest provider-neutral authoritative Challenge headroom and LIVE Risk Facts projection from persisted Broker snapshots, preserving Account/BrokerAccount identity and fail-closed freshness semantics.

NEXT EXTERNAL ACTION:
Wait for explicit FTMO confirmation regarding cTrader Open API access for FTMO Challenge 2-Step accounts.
```
