# Kraken Prop Advanced 50K Pre-Challenge Gap Investigation

**Investigation date:** 2026-10-22
**Scope:** repository-grounded readiness gap investigation only
**Target:** Kraken Prop / Evaluation / Advanced / USD 50,000
**Implementation:** none; production code was not modified

## 1. Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7` |
| `origin/main` | `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7` |
| Latest relevant merge | PR #162, `feat/v1-paper-closure` |
| Working tree | No tracked modifications; five pre-existing/untracked reports remain |
| Latest Challenge Story | Story 0089, implemented and awaiting human review |
| Latest Risk Story | Story 0077, closed and human accepted |
| Latest Broker capability Story | Story 0051, closed, human reviewed, committed and PAPER validated |
| Latest Broker connection Story | Story 0126, implementation completed; human commit pending |
| Latest Broker reconciliation Story | Story 0127, draft; not implemented |
| Latest Kraken market Story | Story 0096, implementation plan complete; awaiting human approval |
| Relevant ADRs | ADR-028, ADR-042, ADR-043, ADR-051, ADR-052, ADR-070 |
| DevLog | Attempted once; unavailable because the provider returned `429 insufficient_quota` |

The existing untracked reports were not modified. The closed V1 verdict is not
reopened:

```text
ENGINEERING_V1 = ACCEPTED_WITH_RESIDUALS
V1_ENGINEERING_MILESTONE_CLOSED = YES
```

The repository is authoritative for current implementation state. The external
Kraken facts supplied for this investigation are treated as investigation inputs,
not as repository provenance.

## 2. Target Definition

The selected candidate is fixed for this investigation:

```text
PROP_PROVIDER = KRAKEN_PROP
PRODUCT = EVALUATION
PLAN = ADVANCED
ACCOUNT_SIZE = USD 50,000
```

Kraken Prop Lite, FTMO and cTrader are explicitly out of scope. The repository
benchmark is a candidate configuration and is not evidence that an evaluation
account was purchased or that a Prop account is API-accessible.

## 3. Canonical Benchmark

The canonical repository representation is the immutable seed migration:

```text
trading-core/src/main/resources/db/migration/common/V30__seed_kraken_prop_advanced_50k_benchmark.sql
```

It persists:

| Field | Repository value |
|---|---|
| Entity | `ChallengeDefinition` plus immutable `RiskPersistence.Profile` |
| Definition identity | `00890000-0000-4000-8000-000000000050` |
| Definition version | `1.0.0` |
| Provider | `KRAKEN_PROP` |
| Product | `EVALUATION` |
| Plan | `ADVANCED` |
| Currency | `USD` |
| Starting capital | `50,000` |
| Profit target | `0.09`, target equity `54,500` |
| Progression basis | `EQUITY` |
| Settlement | `CASH` |
| Instrument product | `LINEAR_PERPETUAL` |
| Risk policy | `KRAKEN_PROP_ADVANCED_50K_RISK`, version `1.0.0` |
| Daily drawdown | `3%` |
| Total drawdown | `3%`, static starting-balance baseline |
| Risk timezone | Account configuration `UTC` |
| Risk-day reset | Account configuration `00:30` |
| Static MDD floor | `48,500` |
| Initial daily allowance | `1,500` |
| Evaluation fee | USD 180 in provenance only; not trading capital |
| Commission | `0.04%` per side in provenance only; not modeled by benchmark Risk |
| Funding | `0.033%` per day, every four hours, in provenance only; not modeled by benchmark Risk |
| Time limit | No evaluation deadline in provenance |
| Automatic terminal actions | Not modeled |
| Leverage/margin | Not represented as a Prop rule |
| Instrument list | Not persisted as a current official Prop market catalogue |

Provenance points to the official Kraken pages retrieved on 2026-10-13. The
definition has `updatable = false` fields, a `@PreUpdate` rejection, a composite
identity of definition ID and version, immutable policy references and migration
identity checks. The benchmark is therefore versioned and immutable, but its
currentness is only partial because the repository retrieval predates the
external inputs used here and the Story remains awaiting human review.

## 4. External-Input Reconciliation

| Rule | Repository value | Current external input | Match | Engineering consequence |
|---|---|---|---|---|
| Account size | USD 50,000 | USD 50,000 | YES | Existing benchmark can represent the account size |
| Profit target | 9%; equity target 54,500 | Inspect current official value; current repository benchmark says 9% | PARTIAL | Confirm current target before locking a paid evaluation |
| MDL | 3% | 3% | YES | Generic daily drawdown policy is configured |
| MDL reset timezone | UTC account configuration | UTC | YES | Existing account risk configuration can express it |
| MDL reset time | `00:30` account configuration | `00:30` UTC | YES | `RiskDay` boundary is expressible and tested |
| MDD | 3% | 3% | YES | Static MDD policy is configured |
| MDD model | Static starting-balance baseline | STATIC | YES | Generic static total drawdown rule is compatible |
| Realized P&L | Included in Risk context through closed trades/ledger facts | Counts | PARTIAL | Provider facts and fee treatment must be authoritative for Prop |
| Unrealized P&L | Included through equity/position valuation in the generic Risk model | Counts | PARTIAL | Current Prop account valuation/API semantics are unproven |
| Commission | `0.04%` per side in provenance; not benchmark Risk modeled | 4 bps per side | NUMERICALLY YES, SEMANTICALLY GAP | The rate matches, but projected/observed challenge Risk participation is not proven |
| Funding | `0.033%` per day every four hours in provenance; not modeled | `0.033%` per day, collected every 4 hours | DOCUMENTED ONLY | Overnight headroom can be understated unless provider funding enters authoritative facts |
| Overnight | No repository prohibition; no runtime funding engine | Allowed | NO CONTRADICTION FOUND | No new overnight blocker; funding remains a gap |
| Time limit | No deadline in provenance | No time limit | YES | No deadline rule is required for this candidate |

The benchmark's commission value is numerically equivalent to 4 bps per side,
but the repository explicitly says it is not modeled. The current external
funding input is also explicitly not modeled. These are not provenance errors;
they are known runtime Risk/economic gaps.

Lite is not a fallback. Its trailing drawdown semantics are not represented and
remain out of scope.

## 5. Challenge Domain Relationship

The actual repository relationship is:

```text
immutable RiskPersistence.Profile
    -> ChallengeDefinition.riskPolicyId + riskPolicyVersion

immutable ChallengeDefinition
    -> provider/product/plan/capital/target/progression/settlement/instrument
    -> definition ID + definition version

ChallengeInstance
    -> account ID
    -> immutable ChallengeDefinition ID/version
    -> immutable Risk policy ID/version
    -> starting capital
    -> lifecycle status

Account + BrokerAccount + AccountRiskConfiguration
    -> runtime balance/equity/positions
    -> RiskEvaluationContext

RiskEvaluationResult
    -> ChallengeEvaluator
    -> ACTIVE / PASSED / BREACHED
```

The external provider contract is represented as a versioned benchmark and Risk
policy, while runtime account state comes from Account/Broker facts. It is not a
first-class Prop credential or provider-terminal state model. Challenge progress
is a read projection and does not own a second mutable Risk truth.

## 6. Static MDD

The current implementation can express Advanced static MDD:

```text
starting balance = 50,000
maximum ratio = 3%
static floor = 50,000 - (50,000 * 0.03) = 48,500
```

`MaximumTotalDrawdownRule` consumes the immutable account starting balance and
uses `maximumInclusiveBreach`; equality at the 48,500 floor is a breach. Profits
do not move the floor because the rule uses the starting-balance baseline rather
than a peak-equity or high-water mark. Focused benchmark and Risk tests cover the
3% boundary and static semantics.

The rule is provider-neutral and does not implement Lite trailing drawdown.

Classification:

```text
STATIC_MDD = READY for the represented generic rule
PROP_MDD_READINESS = DRY_RUN_REQUIRED until provider account facts and terminal behavior are validated
```

## 7. MDL Semantics

The repository separates the required concepts:

- `RiskDay` computes the interval from an account-configured timezone and reset
  time.
- `DailyRiskBaseline` stores a reference balance, effective time, source and
  provenance.
- The generic daily rule consumes the supplied baseline and current equity.
- The benchmark provisions `UTC` and `00:30` through the account configuration
  path.
- Equality is an inclusive breach after the provider-neutral Risk correction.

For the candidate, the intended semantics are therefore:

```text
daily reference = authoritative balance captured for the risk day at/after 00:30 UTC
monitoring value = authoritative equity during the risk day
daily loss = reference balance - current equity, including provider-reported effects
```

The repository does not yet provide a single challenge-grade implementation of
that contract for a Prop account. The active Challenge monitor explicitly skips
non-`PAPER` accounts. The legacy synchronization path derives equity from local
balances plus locally stored open-trade P&L, while the newer Broker Risk Snapshot
path can obtain broker account/equity/positions/closed trades/ledger facts but has
not been proven against a Kraken Prop account. The projection read model returns
`dailyReferenceBalance`, `dailyLossRemaining` and `totalDrawdownRemaining` as
`null`.

The distinction between balance at reset and equity during monitoring is modeled
in the Risk domain, but current Prop authority, cadence and provider semantics are
not established.

Classification:

```text
MDL_MODEL = READY for generic UTC/00:30 configuration
MDL_CHALLENGE_OPERATION = BOUNDED_PRE_CHALLENGE_GAP / EXTERNAL_CONFIRMATION_REQUIRED
```

## 8. Fee and Funding Semantics

The repository has two separate foundations:

1. `HistoricalFeeSchedule` is an immutable, temporal, provider/account/product/
   instrument-scoped evidence model with maker/taker rates, observation time,
   known time, provenance, fingerprints and strict point-in-time reads.
2. Kraken's adapter can acquire current fee tiers through authenticated
   `TradeVolume`, including derivatives-oriented request parameters, and normalize
   the rates as decimal fractions.

These capabilities do not currently make Kraken Prop fees authoritative for
Challenge admission. The benchmark migration explicitly records commission and
funding as unsupported. Historical fee capture is a separate Broker Service
evidence path and the fee table is not evidence that a Prop evaluation account
uses the same schedule.

The current Risk snapshot contains realized P&L, ledger amounts and ledger fees,
and the Risk projection can consume proposed-trade costs where supplied. It does
not contain a generic provider funding accrual engine or a Kraken Prop-specific
commission/funding schedule bound to ChallengeProgress. Market Data has
historical funding evidence structures, but this is not equivalent to charging
the Prop account's provider funding in Risk.

Consequences:

- entry/exit commission can be represented by generic economic evaluation when
  valid fee evidence is supplied;
- current challenge admission does not prove projected 4 bps-per-side costs;
- accrued 0.033% daily funding every four hours is not automatically reflected in
  Challenge headroom;
- overnight positions are not incorrectly rejected, but their funding impact is
  not challenge-safe;
- no fee configuration is changed in this investigation.

Classification:

```text
COMMISSION = BOUNDED_PRE_CHALLENGE_GAP
FUNDING = BOUNDED_PRE_CHALLENGE_GAP
```

## 9. Profit Target and Lifecycle

`ChallengeEvaluator` resolves the configured progression value, calculates:

```text
50,000 + (50,000 * 0.09) = 54,500
```

and passes when current equity is greater than or equal to the target, provided
Risk evaluation is complete and no blocking daily or total drawdown result exists.
Risk breach is evaluated before the target, so a simultaneous breach and target
reaches `BREACHED`.

Stale or unavailable account valuation cannot pass the benchmark in focused tests.
Challenge progress uses the configured progression basis and exposes current
value, target, profit and progress.

The repository does not model Kraken's provider-side lifecycle action that closes
open positions after PASS or breach. It models local Challenge status only. It
also does not prove that a Prop terminal event can be detected and reconciled
after provider-side closure.

Classification:

```text
PROFIT_TARGET_CALCULATION = READY for the repository's 9% benchmark
CURRENT_TARGET = EXTERNAL_CONFIRMATION_REQUIRED
PROVIDER_TERMINAL_LIFECYCLE = BLOCKED_BY_PROVIDER_CONNECTIVITY / DRY_RUN_REQUIRED
```

## 10. Challenge Headroom

The current `ChallengeProgress` record contains:

```text
dailyReferenceBalance
dailyLossRemaining
totalDrawdownRemaining
```

but `ChallengeProgressService` currently returns all three as `null`. The UI
renders them unavailable rather than recomputing Risk formulas, which preserves
authority but does not provide usable challenge headroom.

The smallest authoritative backend source for a future projection is the latest
complete immutable Risk evaluation/context for the Challenge account, assembled
from the broker Risk Snapshot and the configured Risk policy. It should not be a
second formula implementation in Angular or an independent Challenge calculator.

Classification:

```text
CHALLENGE_HEADROOM = BOUNDED_PRE_CHALLENGE_GAP
```

## 11. Kraken Broker Capabilities

The repository contains a provider-neutral capability boundary and a Kraken
provider implementation. The capability model includes authentication, account,
positions, orders, execution, reconciliation, risk snapshots, technical
capabilities, margin preview and position close management.

| Capability | Repository state | Validation state | Prop implication |
|---|---|---|---|
| Authentication | Implemented through credential session and Kraken signing | Local tests; no Prop validation | Prop credentials/account acceptance unproven |
| Account/balances | Implemented through `/0/private/Balance` and risk snapshot | Local tests; no Prop validation | General Kraken account facts exist, Prop facts unproven |
| Equity | `/0/private/TradeBalance` maps `eb`/`e` in the Risk Snapshot path | Local tests; no Prop validation | Provider equity semantics for Prop unproven |
| Positions | Implemented through `/0/private/OpenPositions` | Local tests; no Prop validation | Endpoint/account product compatibility unproven |
| Open orders | Implemented through `/0/private/OpenOrders` | Local tests; no Prop validation | Prop order visibility unproven |
| Historical/closed orders | Implemented through `/0/private/ClosedOrders` | Local tests; no Prop validation | Prop fill/history semantics unproven |
| Fills | Generic `OrderSnapshot` supports fills, but Kraken order mapping does not establish complete fill records | Local only | Fee/fill completeness requires provider validation |
| Place order | Implemented through `/0/private/AddOrder` with idempotent client ID | Local tests; no sandbox/live proof | Prop execution endpoint/permission unproven |
| Cancel order | Implemented through `/0/private/CancelOrder` | Local tests; no Prop validation | Prop cancellation semantics unproven |
| Close position | Implemented through Kraken `/0/private/AddOrder` close path | Local tests; no Prop validation | It is a general Kraken path, not proven for Prop terminal accounts |
| Execution reconciliation | Implemented with open/closed order lookup and explicit unknown/inconsistent outcomes | Local tests | Provider identifiers/partial fills require dry-run |
| Risk snapshot | Implemented with balance double-read, position/stop correlation, history/ledger capture and completeness reasons | Local tests; no Prop validation | Strong generic boundary, but API/account compatibility remains unproven |
| Fee schedule | Current fee acquisition and historical evidence foundation exists | Local/provider-contract tests; no Prop validation | Not currently bound to Challenge Risk |
| Funding | Market-data funding evidence exists; no Prop account accrual integration | Not challenge-validated | Overnight headroom remains incomplete |
| Margin/leverage | Generic capability and Kraken AssetPairs margin preview exist | Local tests; no Prop validation | Futures/Prop margin contract unproven |
| Instrument identity | Canonical/provider-symbol normalization exists; Market Data has Futures product mapping | Local tests and public market work; no Prop execution proof | Exact Prop instrument catalogue remains external |

The adapter has no explicit account type for:

```text
ordinary Kraken account
Kraken derivatives account
Kraken Prop evaluation account
Kraken Prop funded account
```

Broker connections are resolved by provider ID and broker account ID with
credential references. The repository therefore assumes a generic `KRAKEN`
provider connection, not a distinct Prop account contract.

The older `KrakenApiClient`/`KrakenRestClientImpl` path is even narrower, exposing
balance, ticker and open positions. The newer capability path is the relevant
current architecture, but it still calls general Kraken `/0/private/*` endpoints.

## 12. Kraken Prop API Assumptions

The current repository assumption is:

```text
BrokerProviderId.KRAKEN
    + brokerAccountId
    + encrypted credential reference
    -> Kraken provider adapter
```

There is no repository evidence that `KRAKEN_PROP` is a broker provider ID, no
Prop terminal/account discriminator, no Prop credential binding, no Prop endpoint
family, and no account-discovery rule distinguishing Prop evaluation from an
ordinary Kraken or derivatives account.

This is an architecture assumption, not a confirmed external fact. General
Kraken API availability cannot establish Prop API availability.

The repository contains some Kraken Futures support: Market Data has separate
Futures public market data and `LINEAR_PERPETUAL` identity handling, while Broker
Service has a limited Futures private client for an accounts endpoint. The
provider capability implementation used for account, positions, orders and
execution remains wired to general `/0/private/*` endpoints. This is not evidence
of Kraken Prop account compatibility.

## 13. Connectivity Scenarios

### Case A: Prop uses the existing Kraken API contract

The existing provider-neutral Broker Service boundary could be reused. Minimum
work would still include:

- explicitly binding a verified Prop account to a broker connection;
- proving the existing endpoints expose Prop balance, equity, positions, orders,
  fills, fees and ledger facts;
- binding Challenge identity to the correct account;
- enabling challenge-grade synchronization and monitoring for the intended mode;
- validating execution, close, unknown-result and provider-side terminal events;
- validating Prop-specific fee/funding semantics and instrument scope.

No new microservice is implied. Existing Broker, Trading Core and Risk
boundaries are sufficient in principle.

### Case B: Prop has a distinct account identifier but compatible endpoints

The provider adapter may be reusable, but account identity and credential mapping
must carry the distinct identifier without treating it as an ordinary account.
Challenge association, ownership, synchronization and reconciliation must use the
Prop account identity. This remains blocked until the actual identifier and
endpoint contract are confirmed.

### Case C: Prop exposes read-only API access

Trading OS could support analysis, TradePlan generation, deterministic Risk
pre-checks, Challenge progress projection and Decision History only if the API
provides authoritative balance/equity/positions/fills/fees/ledger facts. Execution
would remain manual in Kraken Prop Terminal. The current repository has no
validated read-only Prop import path or manual result import workflow, so this
would be assisted manual execution with explicit synchronization limitations, not
integrated challenge control.

### Case D: Prop has no external API access

Trading OS could still be used as decision support:

```text
market analysis -> TradePlan -> Risk pre-check -> human decision
```

The user would execute in Kraken Prop Terminal. Provider balance, equity, daily
reference, realized/unrealized P&L, fees, funding, positions, orders and terminal
status would be external/manual facts. The current repository cannot treat those
facts as authoritative without an import or verified synchronization boundary.
Therefore it cannot safely act as the primary challenge compliance control. A
small bounded manual procedure could support planning, but it would not establish
challenge-grade automatic headroom or reconciliation.

No case is selected by this report.

## 14. Account Authority Matrix

| Required fact | Current repository source | Required Prop source | Status |
|---|---|---|---|
| Balance | Broker Risk Snapshot or local Account by mode | Kraken Prop authoritative account/API or verified manual source | External confirmation required |
| Equity | Broker `TradeBalance`/Risk Snapshot or PAPER valuation | Kraken Prop authoritative equity | External confirmation required |
| Daily reference balance | Trading Core/Risk baseline persistence | Provider balance at 00:30 UTC, captured authoritatively | Generic model ready; Prop source unproven |
| Realized P&L | Broker closed trades and ledger facts | Prop account history/ledger | External confirmation required |
| Unrealized P&L | Broker positions/equity and market valuation | Prop account/terminal semantics | External confirmation required |
| Commission | Fill/fee evidence or historical fee schedule | Prop fee schedule/fill facts | Partial |
| Funding | Market Data evidence and economic cost structures | Prop account ledger/funding facts | Not challenge-integrated |
| Open positions | Broker Risk Snapshot | Prop account | External confirmation required |
| Open orders | Broker order capability | Prop account | External confirmation required |
| Executions/fills | Broker orders, closed trades and mapper | Prop account | Partial until provider validated |
| Challenge state | ChallengeInstance local projection | Kraken Prop terminal/account state | Provider-side authority absent |

If Prop API access is unavailable, the facts that become unsafe for autonomous
challenge control are balance, equity, daily reference, P&L, fees/funding,
positions, orders, fills and terminal state. Risk must fail closed rather than
substitute stale local state.

## 15. Synchronization

There are two relevant synchronization boundaries:

1. A legacy user-triggered Trading Core synchronization imports a Broker Account,
   balances and open trades, then derives local equity from available base-currency
   balance plus locally stored open-trade P&L. It has no demonstrated provider
   freshness/authority contract for a Prop evaluation.
2. The newer Broker Risk Snapshot capability obtains a coherent snapshot by
   reading balances before and after positions, protective orders, closed trades
   and ledger entries. It records versions, observation time and explicit
   incompleteness reasons, and rejects inconsistent ledger/balance boundaries.

The current Challenge monitor is scheduled/batched but intentionally skips any
BrokerAccount whose execution mode is not `PAPER`. Therefore current automatic
Challenge monitoring is not a LIVE/Prop synchronization path.

What prevents challenge-grade synchronization today:

- no confirmed Prop account/API contract;
- no Prop account identity binding;
- no accepted LIVE/Prop cadence and freshness evidence;
- no authoritative daily baseline acquisition at the provider reset boundary;
- no Challenge headroom projection from authoritative Risk results;
- no provider-side manual action or automatic terminal event import;
- no proven divergence workflow for Prop account state.

Classification:

```text
GENERIC_BROKER_SYNC = PARTIAL
KRAKEN_PROP_SYNC = BLOCKED_BY_API_CONFIRMATION
```

## 16. Execution and Reconciliation

The generic execution architecture has the required safety shapes:

- explicit human authorization remains separate from Risk approval;
- idempotency keys are persisted;
- timeout/provider failures become `UNKNOWN` rather than rejection;
- blind retry is blocked;
- reconciliation can return reconciled, absent or inconsistent results;
- close has a separate resolve/execute/reconcile lifecycle;
- provider-side position references are preserved with provenance.

The Kraken adapter maps errors into rejected/unknown outcomes and reconciles
orders through open/closed order lookup. It can aggregate a Kraken general-account
position scope for close operations.

The following remain unvalidated for Prop:

```text
timeout after Prop submission
duplicate Prop request
partial Prop fill and fee capture
restart with open Prop exposure
uncertain close
provider-side forced close
Prop terminal PASS/BREACH auto-close
manual terminal action outside Trading OS
```

The current order mapping does not by itself prove complete fill and funding
semantics for a Prop account. These behaviors require provider confirmation and a
non-financial or controlled dry-run before purchase or execution.

## 17. Provider-Side Events and Manual Actions

The repository can detect changes when a subsequent authoritative broker snapshot
contains different positions/orders/balances. It does not currently provide an
explicit Challenge event model for:

- new external positions;
- external position modification;
- manual close;
- provider cancellation;
- provider stop-out/liquidation;
- automatic Kraken Prop close after pass or breach.

The safe current behavior is to stop Trading OS actions and reconcile the provider
state before authorizing another action. The provider terminal must remain the
emergency path. Automatic terminal event detection is not proven.

Classification:

```text
PROVIDER_SIDE_EVENTS = BLOCKED_BY_API_CONFIRMATION / DRY_RUN_REQUIRED
```

## 18. Market and Instrument Alignment

The repository has a provider-neutral identity model:

```text
provider + canonical symbol + productType
```

Market Data supports Kraken Futures public discovery and maps valid non-expired
perpetuals to `LINEAR_PERPETUAL`, retaining the provider symbol such as
`PF_XBTUSD` for adapter requests. Spot and perpetual identities are distinct.

The benchmark requires:

```text
CASH + LINEAR_PERPETUAL
```

and explicitly rejects `SPOT` through the generic compatibility service. This is
architecturally coherent.

However:

- Story 0096 remains awaiting human approval and does not prove full runtime
  readiness for a Prop account;
- Broker execution remains on general Kraken `/0/private/*` endpoints rather than
  a proven Futures/Prop execution contract;
- no current official Kraken Prop instrument list is persisted;
- the repository cannot prove every Kraken Futures perpetual is available to
  Kraken Prop.

Therefore:

```text
CURRENT_PROP_INSTRUMENT_LIST = EXTERNAL_VERIFICATION_REQUIRED
```

Market data alignment is a bounded partial capability, not a Prop execution proof.

## 19. News and Overnight Rules

The supplied current external inputs do not establish a general Advanced news
blackout rule:

```text
KRAKEN_ADVANCED_NEWS_RESTRICTION = NOT_ESTABLISHED_BY_CURRENT_EXTERNAL_INPUT
```

No news-rule blocker is added to the critical path. The existing news/economic
calendar remains decision-support context only unless official provider evidence
later establishes a restriction.

Overnight positions are allowed by the current external input. The repository has
no incorrect benchmark rule rejecting overnight positions. The remaining issue is
funding impact, not an overnight prohibition.

## 20. Human Execution Model

The existing V1 invariants remain reusable:

```text
AI/analysis != authorization
Risk approval != execution
human authorization -> execution boundary
```

Two possible future models remain open:

### Integrated execution

```text
Trading OS -> Risk -> explicit human authorization -> Broker Service -> Kraken Prop
```

This is possible only if Prop API/account access and all provider semantics are
confirmed and validated.

### Assisted manual execution

```text
Trading OS -> Risk -> explicit human decision -> Kraken Prop Terminal
          -> provider/manual synchronization or import
```

The current architecture can reuse decision support and human control for this
model, but it does not currently have a challenge-grade authoritative manual
import or headroom projection. It is therefore feasible as limited decision
support, not safe as a complete compliance control without an explicit bounded
procedure and manual reconciliation.

No model is selected until Prop API capability is known.

## 21. V1 Capability Reuse

The following should be reused unchanged in principle:

- Decision Workspace;
- provider-neutral TradePlan;
- human plan acceptance;
- deterministic Risk engine and immutable evaluations;
- explicit execution authorization;
- account ownership/security boundaries;
- generic position and close lifecycle;
- Decision History and provenance preservation;
- provider-neutral ChallengeDefinition/ChallengeInstance identity;
- `CASH + LINEAR_PERPETUAL` compatibility;
- Kraken Market Data canonical/provider-symbol separation.

No rewrite of the proven V1 loop is justified by this investigation. The gaps
are at the provider-account authority, challenge projection, fee/funding and
external validation boundaries.

S1 is independent:

```text
S1_REQUIRED_FOR_KRAKEN_PROP = NO
```

AI is not required:

```text
AI_REQUIRED_FOR_KRAKEN_PROP = NO
```

The active scanner may provide decision support over the relevant Kraken market
scope; no scanner defect was found that should expand this investigation.

## 22. Exact Gap Classification

| Finding | Classification | Reason |
|---|---|---|
| Immutable Advanced 50K benchmark exists | READY | Repository can represent the candidate and versions |
| 3% static MDD rule | READY | Static starting-balance floor and inclusive breach are implemented/tested |
| UTC/00:30 Risk day | READY | Account configuration and RiskDay support the boundary |
| Current external target/profit/fee terms | EXTERNAL_CONFIRMATION_REQUIRED | Repository provenance is from 2026-10-13 and Story 0089 awaits review |
| MDL provider balance/equity authority | BOUNDED_PRE_CHALLENGE_GAP | Generic model exists; Prop source/cadence not proven |
| Challenge headroom projection | BOUNDED_PRE_CHALLENGE_GAP | Progress fields remain unavailable/null |
| Commission participation in Challenge Risk | BOUNDED_PRE_CHALLENGE_GAP | Rate is documented but not bound to admission/projected headroom |
| Funding participation in Challenge Risk | BOUNDED_PRE_CHALLENGE_GAP | Funding evidence/cost structures are not Prop Challenge authority |
| Kraken general adapter contracts | READY | Generic capabilities, execution and reconciliation exist locally |
| Kraken Prop account/API compatibility | EXTERNAL_CONFIRMATION_REQUIRED | No authoritative Prop API contract is established |
| Prop credential/account binding | BLOCKED_PENDING_PROP_API_CONFIRMATION | No Prop account identity model exists |
| Prop account synchronization | BLOCKED_PENDING_PROP_API_CONFIRMATION | Current Challenge monitor is PAPER-only |
| Prop integrated execution | BLOCKED_PENDING_PROP_API_CONFIRMATION | Existing endpoints are not proven for Prop |
| Prop reconciliation/manual terminal actions | BLOCKED_PENDING_PROP_API_CONFIRMATION | Provider-side event semantics are unknown |
| Prop instrument list | EXTERNAL_CONFIRMATION_REQUIRED | Current official Prop market list is not in repository |
| Market data canonical alignment | BOUNDED_PRE_CHALLENGE_GAP | Kraken Futures data path exists; Prop availability is unproven |
| Provider-side PASS/BREACH close | DRY_RUN_REQUIRED | Local Challenge lifecycle does not model provider terminal mutation |
| News blackout | OUT_OF_SCOPE | Not established by current external inputs |
| Overnight prohibition | READY | No incorrect prohibition found; funding remains separate |
| Lite trailing drawdown | OUT_OF_SCOPE | Explicit non-target |
| S1 and AI | OPTIONAL | Neither is required for the selected manual evaluation workflow |

## 23. Must Resolve Before Purchasing Advanced

Only the following are payment blockers:

1. Obtain authoritative confirmation whether a Kraken Prop evaluation account is
   accessible through a supported API/account contract, and identify the exact
   account/credential/platform boundary.
2. Verify the current Advanced 50K target, fee, MDL/MDD semantics, instrument
   availability, fee/funding treatment and terminal PASS/BREACH behavior against
   current official Kraken Prop terms.
3. Determine whether the existing Kraken adapter can read authoritative Prop
   balance, equity, positions, orders, fills, fees, funding and ledger facts.
4. Establish whether the minimum operating model is integrated execution or
   assisted manual execution, including the emergency terminal and reconciliation
   procedure.

Convenience improvements such as AI, passive scanning, frontend redesign and S1
completion are not purchase blockers.

## 24. Can Be Implemented Now

The following work is independent of the answer to Prop API compatibility and is
supported by repository evidence:

- lock the external Advanced benchmark reconciliation as a reviewed/versioned
  artifact without changing the existing migration during this investigation;
- add focused tests for the already represented static MDD and UTC/00:30 boundary
  if a future approved Story requires broader coverage;
- project Challenge headroom from authoritative persisted Risk evaluation facts,
  reusing Risk metrics rather than duplicating formulas;
- connect valid commission/funding evidence to the Challenge Risk/economic
  boundary only after the responsible Story and semantics are approved;
- prepare the provider-specific operational runbook, failure matrix and dry-run
  evidence template;
- validate the exact Kraken Futures/Prop instrument mapping through the existing
  provider-neutral Market Data boundary once the official Prop catalogue is known.

These are candidate bounded Stories, not authorization to implement during this
investigation.

## 25. Blocked Pending Prop API Confirmation

The following must wait for authoritative Prop API/account confirmation:

- Prop credential binding and account discovery;
- deciding whether `KRAKEN_PROP` is a distinct BrokerProvider/account type or a
  verified account classification under `KRAKEN`;
- Prop balance/equity/position/order/fill/ledger synchronization;
- challenge-grade reset baseline acquisition;
- Prop execution, cancel, close and unknown-result handling;
- Prop reconciliation after manual/provider-side actions;
- detection of provider-side PASS/BREACH automatic closure;
- Prop-specific fee/funding source binding;
- final instrument availability and venue execution alignment;
- selection of integrated versus assisted manual execution as the operational
  model.

No generic Prop Provider Service, new microservice or speculative adapter is
justified before this boundary is resolved.

## 26. Minimum Future Dry-Run Model

Do not execute this sequence in the current investigation. The minimum future
sequence is:

```text
1. Confirm official Kraken Prop API/account/platform contract without assuming
   general Kraken API compatibility.
2. Lock the current Advanced 50K definition, target, fee, rules, reset and
   instrument scope with provenance.
3. Bind a non-production or otherwise safe account identity through the existing
   Broker boundary, if supported.
4. Prove authoritative balance, equity, positions, orders, fills, fees, funding,
   ledger and daily-baseline freshness.
5. Validate static MDD, MDL boundary, target progression and fee/funding headroom
   with deterministic scenarios.
6. Execute a bounded non-financial/demo/PAPER-compatible entry, monitor, close and
   reconciliation journey through the intended provider boundary.
7. Test timeout, unknown submission, duplicate request, partial fill, restart,
   manual provider action and automatic terminal-close recovery.
8. Validate the human runbook and emergency Kraken Prop Terminal procedure.
9. Only then make a human decision about purchasing the evaluation.
```

If no Prop API exists, stop at the decision-support/manual execution boundary and
do not represent manually observed facts as automatically authoritative.

## 27. Smallest Authoritative External Confirmation

The next external action should not be an evaluation purchase solely to discover
connectivity. The smallest safer confirmation is:

```text
Ask Kraken Prop Support for an explicit current answer to:
1. Are evaluation accounts API-accessible?
2. Which API family, account identifier and credential permissions apply?
3. Are balance, equity, positions, orders, fills, fees, funding and ledger facts
   available?
4. Are order placement, cancellation, close and reconciliation supported?
5. What provider event/API reports PASS, BREACH and automatic position closure?
6. Which current instruments and symbols are available to Advanced 50K?
```

If Support cannot answer, the next safest evidence source is the actual Prop
evaluation UI/API settings or an explicitly documented demo/non-financial account.
Do not infer compatibility from ordinary Kraken API success.

## 28. Recommendation

Preserve the closed V1 and do not implement speculative Prop infrastructure yet.
The current repository is close enough to reuse its Challenge/Risk/Market Data and
Broker abstractions, but it is not ready to claim a Kraken Prop Advanced dry-run.

The minimum engineering path is bounded:

```text
external Prop API/account confirmation
    -> current Advanced benchmark lock
    -> provider-specific account authority mapping
    -> challenge headroom and fee/funding admission correctness
    -> synchronization/execution/reconciliation validation
    -> controlled dry-run and human runbook acceptance
    -> purchase decision
```

The critical uncertainty is connectivity and authority, not provider selection and
not the closed V1 loop.

========================================
KRAKEN PROP ADVANCED PRE-CHALLENGE GATE
========================================

REPOSITORY BASELINE:
a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7

TARGET:
KRAKEN PROP ADVANCED USD 50,000

TARGET STATUS:
CONFIRMED_CANDIDATE

BENCHMARK CURRENTNESS:
PARTIAL

STATIC MDD COMPATIBILITY:
READY

MDL COMPATIBILITY:
GAP

FEE/FUNDING COMPATIBILITY:
GAP

CHALLENGE PROGRESS:
GAP

KRAKEN BROKER ADAPTER:
PARTIAL

PROP API ACCESS:
NOT_CONFIRMED

PROP ACCOUNT SYNCHRONIZATION:
BLOCKED_BY_API_CONFIRMATION

PROP EXECUTION:
BLOCKED_BY_API_CONFIRMATION

PROP RECONCILIATION:
BLOCKED_BY_API_CONFIRMATION

MARKET DATA ALIGNMENT:
PARTIAL

S1 REQUIRED:
NO

AI REQUIRED:
NO

MUST RESOLVE BEFORE PURCHASE:
Prop API/account contract; current Advanced rules and instrument scope; authoritative account facts; execution model and emergency reconciliation path

CAN IMPLEMENT NOW:
Headroom projection from Risk facts; approved benchmark reconciliation tests; bounded fee/funding admission work; runbook and dry-run evidence preparation

BLOCKED PENDING PROP API CONFIRMATION:
Prop credential binding; account sync; Prop execution/cancel/close; provider-side event detection; Prop reconciliation; final fee/funding and instrument binding

MINIMUM CRITICAL PATH:
Confirm Prop API/account contract -> lock current Advanced benchmark -> map authoritative account facts -> validate Risk/headroom/fees/funding -> validate sync/execution/reconciliation -> run controlled dry-run -> human purchase decision

PRE-CHALLENGE ENGINEERING STATUS:
BOUNDED_GAPS

NEXT ENGINEERING STORY:
Define and implement the smallest provider-neutral authoritative Challenge headroom projection from persisted Risk evaluation facts, after human approval of the benchmark reconciliation.

NEXT EXTERNAL ACTION:
Obtain written Kraken Support confirmation of Prop evaluation API/account connectivity, permissions, available facts, execution operations and terminal events before purchasing the evaluation.
