# Story 0148 - Repository Analysis

## Status

`WAITING_FOR_ANALYSIS_APPROVAL`

This artifact records the current repository analysis. It does not authorize
implementation and does not grant approval for a new ADR or implementation plan.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `dc1d14f278555091c45bb41c5bc9d6f456445127` |
| origin/main | `dc1d14f278555091c45bb41c5bc9d6f456445127` |
| Working tree | Nine pre-existing untracked files under `docs/architecture/reports/`; no tracked modifications at analysis start |
| Latest merged Story | Story 0147, PR #165, merge commit `dc1d14f278555091c45bb41c5bc9d6f456445127` |
| Latest relevant ADRs | ADR-071, ADR-072 and ADR-073 are present on `main` and accepted; ADR-073 governs 0147 authentication/binding |
| Next Story number | `0148`; no canonical 0148 artifact existed before this analysis |
| DevLog | Applicability check attempted once; unavailable because the configured provider quota is exhausted |
| External documentation | Official cTrader Open API documentation consulted on 2026-10-23 |

## Previous Story Verification

### Story 0145

Story 0145 established the explicit
`Account.brokerAccountId -> BrokerAccount.id` routing relation, provider-neutral
v2 snapshot plumbing, owner/provider identity checks and Kraken regression
coverage. It does not provide cTrader facts.

### Story 0146

The merged implementation provides the Broker Service-owned cTrader provider
registration and opaque provider reference. Trading Core remains free of a
concrete cTrader provider authority. A recognized cTrader provider without a
capability fails unavailable rather than falling back to Kraken.

The historical 0146 Story artifact still contains pre-merge workflow status, but
the physical code on current `main` is the authoritative state.

### Story 0147 prerequisite

The merged implementation provides:

- owner-correlated, expiring and one-time authorization attempts;
- encrypted cTrader token storage behind `CredentialReference`;
- explicit discovery and binding of a cTrader external account;
- exact external-account eligibility matching;
- owner/provider revalidation and locked connection access;
- durable callback consumption before provider exchange;
- no cTrader execution capability.

The prerequisite is sufficient for a read-only adapter to identify the bound
account, subject to the documented residual limitation that real cTrader
authentication and protocol compatibility remain unvalidated. This Story must
not compensate by rebinding accounts, falling back to Kraken, or treating stale
tokens/facts as current.

## Current Provider-Neutral Contracts

The current Broker Service contracts are:

- `BrokerModels.AccountSnapshot`: balances and one observation timestamp;
- `BrokerModels.BrokerAccountSnapshotV2`: broker account ID, opaque provider
  registration reference, external account ID, balances, positions and
  `observedAt`;
- `BrokerModels.PositionSnapshot`: instrument, signed quantity, entry price,
  observation timestamp and opaque broker position reference;
- `BrokerModels.OrderSnapshot`: external/client IDs, status, quantity, filled
  quantity, fills and observation timestamp;
- `BrokerModels.RiskSnapshot`: account facts, risk positions, closed trades,
  ledger entries, completeness and unavailability reasons;
- `RiskSnapshotCapability`: a provider-neutral complete risk-oriented read path
  with an observation interval.

The v2 account snapshot path currently invokes only `AccountCapability` and
`PositionCapability` in `GetAccountSnapshotV2Service`. It does not invoke order,
deal/fill or risk-snapshot capabilities, and its DTO has no completeness,
freshness, equity, margin, provider timestamp or observation-window fields.

Trading Core currently replaces local balances and open trades from the v2 DTO
and sets `marketDataTimestamp` from `observedAt`. It does not receive an explicit
freshness or completeness decision and therefore cannot safely consume a new
partial cTrader observation through the current DTO unchanged.

## Risk Boundary

ADR-028/ADR-031 assign financial snapshot assembly and deterministic Risk context
to Trading Core. Broker Service owns provider facts and provider capabilities.
The existing `RiskSnapshot` model is the strongest reusable neutral contract for
account balance, equity, margin, positions, closed trades and ledger facts, but
it is currently implemented only by Kraken and uses a provider-specific bounded
capture strategy.

This Story must not add FTMO logic or Risk formulas to Broker Service. The
Implementation Plan must choose, using the current architecture, whether cTrader
produces:

1. an extended provider-neutral authoritative Broker observation consumed by
   Trading Core; or
2. the existing neutral `RiskSnapshot` contract through a cTrader
   `RiskSnapshotCapability`.

The choice must preserve missing/incomplete facts and must not make a partial
observation valid Risk input.

## Authoritative cTrader Contract Evidence

Sources consulted:

- `https://help.ctrader.com/open-api/account-authentication`
- `https://help.ctrader.com/open-api/messages`
- `https://help.ctrader.com/open-api/model-messages`
- `https://help.ctrader.com/open-api/proxies-endpoints`

Documented cTrader facts:

- `ProtoOAGetAccountListByAccessTokenRes` returns authorized account IDs and
  permission scope;
- `ProtoOATrader` is the account-level trader model and exposes account facts
  such as balance/equity-related values, deposit asset and account settings;
- `ProtoOAPosition` and `ProtoOAGetPositionUnrealizedPnLRes` provide position
  identity, symbol identity, volume and unrealized P&L-related data;
- `ProtoOAOrderListRes` and `ProtoOAOrderListByPositionIdRes` provide pending
  and related order observations;
- `ProtoOADealListRes` and `ProtoOADealListByPositionIdRes` provide bounded deal
  history with deal/order/position IDs, execution timestamps, volume, price,
  commission and optional close-position details;
- cTrader monetary integer fields require `moneyDigits` scaling, while price
  and volume fields use provider-specific semantics;
- cTrader external symbol IDs are environment/server-specific and cannot be
  treated as canonical Trading OS instruments without a safe mapping;
- demo and live endpoints are separate, and the configured environment must
  match the bound account;
- `SCOPE_VIEW` is read-only and prohibits trading commands;
- token expiry/revocation and account disconnect events invalidate current
  account usability.

Not established by documentation alone:

- that every target FTMO account exposes every requested fact with the same
  completeness or timestamp semantics;
- that a single atomic timestamp covers account, positions, orders and deals;
- that free margin, margin level, swap and all realized P&L fields are available
  in the exact required response for the target account;
- that the existing JSON transport implementation is compatible with the real
  provider response shapes.

## Coherence, Completeness and Freshness

The current repository has `observedAt` and `SnapshotCompleteness` but no
provider-neutral freshness/staleness contract. Kraken's risk implementation
uses bounded reads, a capture timestamp, missing-fact reasons and repeated
capture when balances change, but this behavior is not generalized to the v2
snapshot boundary.

For cTrader, account, positions, orders and deals may be obtained from separate
messages. The implementation must therefore preserve the observation window,
provider update timestamps where available, completion time, missing-fact
reasons and stale threshold. A partial response cannot be represented as
complete, and a previous persisted observation cannot be promoted to fresh after
restart.

## Ownership and Identity

The safe route is:

```text
authenticated owner
  -> local BrokerAccount
  -> owner/provider BrokerConnection
  -> durable bound externalAccountId
  -> cTrader response ctidTraderAccountId
```

The Broker Service v2 endpoint already validates the delegated owner and local
connection. The cTrader synchronization service must additionally validate the
stored bound external ID and reject any provider response for another account.
It must never overwrite the binding from provider data.

## Persistence and Restart

`RiskSnapshotPersistence` currently stores only a generated snapshot version,
account ID, observation timestamp and completeness, plus stable provider position
identity mappings. It does not persist the full neutral account/order/deal
observation or a freshness state.

The Story must decide whether the authoritative observation is transient or
persisted. If persisted, identity, owner/account relation, versioning,
completeness, freshness and retention must be explicit. In either case, restart
must invalidate current freshness and require a new authenticated provider read.

## Execution Boundary

No cTrader provider is currently registered with execution, order, cancel or
position-management capabilities. The new adapter must remain read-only and must
not add any mutation message or expose a cTrader mutation capability.

## Architecture Decision Assessment

**ADR required: YES.**

ADR-071 establishes that Broker Service owns cTrader synchronization and that
read-only synchronization precedes execution. ADR-073 establishes authentication
and binding but explicitly excludes synchronization. The current contracts do
not define the durable authority for a multi-resource provider observation, its
coherence window, freshness/staleness semantics, or the boundary between a
provider-neutral Broker observation and the RiskSnapshot contract.

A new ADR is required before implementation planning can authorize changes to
that boundary. The ADR should decide only the durable architecture: observation
ownership, coherence/completeness/freshness semantics, persistence policy and
the Broker Service to Trading Core contract. cTrader field mapping remains Story
implementation detail after the ADR is accepted.

## Risks and Stop Conditions

- The existing JSON WebSocket cTrader client has not passed real-provider
  validation; local fixtures cannot prove protocol compatibility.
- cTrader monetary scaling and volume units must be normalized with BigDecimal
  and explicit provider precision.
- cTrader symbol IDs are server/environment-specific; ambiguous canonical
  mapping must make the observation incomplete, not guess.
- No provider response identity may replace the durable binding.
- A missing or stale observation must never be accepted as Risk input.
- No execution, FTMO rule, Market Data, S1 or AI scope may enter this Story.

## Validation Performed

- Current branch, HEAD, `origin/main`, worktree and recent history inspected.
- Stories 0145, 0146 and 0147 inspected.
- ADR-071, ADR-072 and ADR-073 inspected.
- Current cTrader authentication/binding code, Broker contracts, v2 snapshot
  endpoint, Trading Core synchronization path, RiskSnapshot boundary,
  persistence and Kraken risk implementation inspected.
- Official cTrader authentication, messages, model messages and endpoint
  documentation consulted on 2026-10-23.
- DevLog applicability check attempted once and failed due provider quota.
- No production code or tests were changed during Repository Analysis.

## Current Gate

Repository Analysis is complete. Human approval is required before producing the
Implementation Plan. Because the analysis identifies a required new ADR, the
implementation remains blocked until that ADR is accepted through the repository
workflow.
