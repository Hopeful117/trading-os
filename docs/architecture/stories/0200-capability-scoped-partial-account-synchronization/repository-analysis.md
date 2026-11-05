# Repository Analysis - Story 0200

## Current State

The repository is on `main` aligned with `origin/main`. The working tree
contains the uncommitted Story 0198 and Story 0199 artifacts and their related
implementation/test changes. Story 0200 is now present as the approved Story
scope, but no Story 0200 implementation has been started.

DevLog was queried with a stable idempotency key and resumed once after the
initial timeout. Both requests timed out. This analysis therefore uses the
current repository and the accepted ADRs as authoritative for current physical
state and records the DevLog context limitation.

## Existing Synchronization Flow

```text
POST /api/v2/accounts/{brokerAccountId}/synchronize
  -> Gateway route to Trading Core
  -> AccountSynchronizationV2Controller
  -> BrokerSynchronizationServiceImpl.synchronize(ownerId, brokerAccountId)
  -> BrokerAccountSnapshotV2Client Feign GET
     /internal/v2/broker-accounts/{brokerAccountId}/snapshot
  -> Broker Service AccountSnapshotV2Controller
  -> GetAccountSnapshotV2Service
  -> AuthoritativeSnapshotCapability
  -> CTraderAuthoritativeSnapshotCapability
  -> CTraderOpenApiClient.readOnlyObservation
  -> cTrader WebSocket READ-ONLY observation
  -> Trading Core snapshot validation
  -> Account/trade mutation and save
```

The account is owner-bound in both services. Trading Core also requires the
mapped account to be `LIVE`, validates the provider registration and external
account identity, and performs the mutation inside a transaction.

## Broker Service Contract

`BrokerModels.BrokerAccountSnapshotV2` already carries:

- account identity and provider registration reference;
- external account ID;
- balances and account facts;
- positions, orders, deals and position PnL;
- independent collection availability for positions, orders, PnL and deals;
- `COMPLETE`/`INCOMPLETE` completeness;
- `FRESH`/`STALE` freshness;
- observation start/completion timestamps;
- bounded unavailability reasons;
- equity provenance and quality metadata.

`CTraderAuthoritativeSnapshotCapability` preserves provider collection
availability and does not convert missing equity, margin, PnL or deals into
numeric or empty substitutes. The current reference observation is fresh but
incomplete because cTrader does not provide the required equity, account margin
and deal facts.

The broker-domain snapshot constructor normalizes collection values to lists
and validates the explicit availability enum. This is safe only because the
availability enum remains authoritative; an empty list with
`UNAVAILABLE_OR_ABSENT` must never be interpreted as an authoritative empty
collection by consumers.

## Trading Core Contract and Current Defect

`BrokerAccountSnapshotV2Dto` mirrors the broker snapshot and preserves the
availability enum, provenance, freshness and reasons. The current
`BrokerSynchronizationServiceImpl` nevertheless applies global gates:

1. snapshot must be `COMPLETE` and `FRESH`;
2. account facts must contain valuation asset and balance;
3. positions, orders and deals must not be unavailable or truncated;
4. all collection references must be valid and unique;
5. balances and open positions are then replaced;
6. deals are applied for close reconciliation;
7. provider equity is copied only when present;
8. the account is saved.

The current all-or-nothing collection gate is the confirmed Story 0200 defect:
it rejects an observation before saving a valid balance and an independently
confirmed empty positions/orders state when unrelated equity, margin or deal
history is unavailable.

The existing tests explicitly protect the old behavior in
`BrokerSynchronizationServiceImplTest`:

- incomplete/stale observations are rejected;
- unavailable positions are rejected before mutation;
- truncated deals are rejected before mutation;
- missing account facts are rejected before mutation.

Those tests must be revised only where Story 0200 changes the contract. The
identity, duplicate-reference, invalid-position, paper-account and repeated
close/idempotence protections must remain.

## Persistence Gap

The persisted `Account` entity currently contains balances, equity, peak equity,
valuation status, timestamps and trades, but no durable per-capability
availability, provenance or unavailability-reason fields. `Trade` persists
provider position/deal references and financial lifecycle facts, but no
collection observation status.

The current synchronization endpoint returns only:

```text
Broker account synchronized successfully
```

Therefore the repository can currently persist available balances and positions
but cannot durably expose whether a later synchronization omitted positions,
orders, PnL or deals because the provider capability was unavailable or because
the collection was authoritative and empty.

This is the principal design point for the Implementation Plan. The plan must
choose the smallest coherent representation that satisfies the Story:

- preserve availability and observation metadata in a durable account snapshot
  projection; or
- explicitly define the API result as the authoritative partial-observation
  projection while preserving prior local state for unavailable collections.

The choice must not make stale local facts appear current or silently authorize
risk. If an unavailable collection cannot be represented durably, it must not
be used to clear or replace existing synchronized state.

## Risk Boundary

`ModeAwareRiskFactsProvider` already consumes collection availability and
evidence requirements independently. It treats missing equity, required
positions and required deal history as blocking for the corresponding risk
requirements, while allowing unrelated diagnostics to remain available.

The implementation must preserve these invariants:

- `INCOMPLETE` is not `NOT_APPLICABLE`;
- unavailable data is not zero or empty data;
- incomplete evidence cannot produce aggregate execution approval;
- equity provenance and quality remain explicit;
- FTMO equity-dependent risk remains fail-closed;
- partial synchronization remains read-only diagnostic state.

No cTrader-specific type or FTMO policy rule may be added to Trading Core or
the Risk Domain.

## Mutation Safety Analysis

The current service mutates the managed `Account` aggregate before calling
`accountRepository.save`, but the method is transactional. The implementation
must preserve atomic rejection for malformed or incoherent snapshots while
allowing capability-scoped application for valid partial snapshots.

Required mutation rules:

- identity, provider binding, freshness and mandatory account identity facts are
  validated before any mutation;
- available balances may be replaced only when their availability and account
  facts are valid;
- available positions may be reconciled only when the position collection is
  authoritative and complete for the requested capability;
- unavailable or truncated positions must not clear existing open trades;
- unavailable or truncated deals must not apply closes or baseline changes;
- unavailable orders must not be represented as an authoritative empty order
  set;
- unavailable equity, margin and free margin must remain unavailable;
- any invalid fact in a collection that is being applied must abort the
  transaction without persistence.

The plan must also decide how a previously persisted open position is handled
when a later snapshot reports positions unavailable. The safe default is to
retain the prior local position with explicit stale/unavailable observation
metadata and block any risk capability requiring current positions.

## Relevant Existing Tests

Trading Core:

- `BrokerSynchronizationServiceImplTest` covers owner binding, provider and
  external identity, complete synchronization, position identity, duplicate
  references, partial/final close reconciliation and idempotence.
- `AccountSynchronizationV2ControllerTest` covers the HTTP synchronization
  boundary and must be extended if the response becomes structured.
- `ModeAwareRiskFactsProvider` tests and risk integration tests cover scoped
  evidence admission and fail-closed behavior.

Broker Service:

- `CTraderAuthoritativeSnapshotCapabilityTest` covers fresh/complete mapping,
  authoritative empty collections, incomplete PnL, stale observations and
  account identity mismatch.
- Broker snapshot contract tests cover collection availability normalization.

The minimum new or revised tests should cover:

- balance plus authoritative empty positions/orders with unavailable equity,
  margin and deals persists safely;
- unavailable positions do not clear an existing local position;
- unavailable deals do not close or baseline an existing position;
- truncated collections do not replace complete local state;
- available empty collections do clear the corresponding current state;
- invalid identity and duplicate position references remain atomic failures;
- repeated partial snapshots are idempotent;
- response metadata exposes partial status and bounded reasons;
- risk remains incomplete for mandatory missing evidence and never authorizes
  execution.

## Runtime Validation Boundary

The controlled runtime target remains the existing FTMO Free cTrader account:

```text
external account: 49411967
Trading Core account: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
execution mode: LIVE in Trading OS, read-only provider observation only
CTRADER_SAFETY_EXECUTION_ENABLED: false
```

The expected current observation is usable balance and confirmed empty
positions/orders with unavailable equity, account margin and deals. After
implementation, the result should demonstrate persisted safe facts and an
explicit partial/incomplete status. It must not claim FTMO risk readiness or
execute any provider mutation.

## Affected Components

- `trading-core` synchronization service, DTO/result boundary and tests;
- `trading-core` account persistence only if durable capability metadata is
  required by the approved implementation plan;
- `trading-core` risk fact projection only if the synchronization result changes
  the existing evidence payload;
- `broker-service` snapshot contract/tests only if the provider-neutral
  availability contract needs correction;
- relevant API/controller and error mapping tests;
- Story 0200 documentation artifacts and implementation report.

No change is currently justified in cTrader transport, execution adapters,
FTMO policy thresholds or the Risk Domain algorithms.

## Risks and Open Decisions

- The existing `Account` schema cannot express per-capability availability
  without a persistence change. Adding schema state is a material design choice
  and must remain minimal and provider-neutral.
- Returning partial success while keeping old positions can expose stale local
  positions unless freshness/unavailability is visible to every consumer.
- Returning partial success while clearing unavailable collections would create
  false authoritative emptiness and is prohibited.
- The current synchronization endpoint is synchronous and string-valued; a
  structured result is likely required to expose partial status and reasons.
- The cTrader observation itself already reports incomplete account facts, so
  the implementation must not weaken the Broker Service completeness semantics
  merely to make Trading Core persist data.

## Required Next Stage

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is written. The plan must first settle the minimal
provider-neutral representation of persisted capability availability and the
structured synchronization result, then implement the smallest change covered
by the acceptance criteria.
