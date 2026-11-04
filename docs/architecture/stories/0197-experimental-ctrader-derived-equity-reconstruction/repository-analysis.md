# Repository Analysis - Story 0197

## Baseline

```text
CURRENT_BRANCH: main
HEAD: c2e2c1f32cd8319ff95e3d491e529e750a387a00
REMOTE_STATUS: main...origin/main
NEXT_AVAILABLE_STORY: 0197
WORKTREE_STATE: pre-existing cTrader, Story 0193 documentation and one
                untracked Trading Core test modification preserved
```

The working tree must not be reset, stashed, checked out destructively or
cleaned as part of this Story. Existing changes are outside the experimental
prototype scope and must remain reviewable.

## Relevant Decisions

- ADR-082 keeps LIVE provider authority and `CTRADER_SAFETY_EXECUTION_ENABLED`
  disabled by default. It does not authorize local LIVE equity derivation.
- ADR-084 assigns provider transport and financial fact interpretation to
  Broker Service and requires fail-closed handling for missing safety facts.
- ADR-085 keeps cTrader current equity unavailable until derivation semantics,
  source quality and controlled validation are demonstrated. It explicitly
  warns that `balance + sum(netUnrealizedPnL)` is not yet proven equivalent to
  FTMO equity.
- ADR-086 permits diagnostic partial risk evaluation but requires aggregate
  fail-closed execution. An incomplete or inadmissible equity cannot authorize
  T0 or T1.
- Story 0196 explicitly excludes cTrader equity derivation and preserves
  `BROKER_DERIVED_FTMO_EQUITY = INADMISSIBLE`.

## Current Data Path

The cTrader client collects the following sequential responses in
`JsonWebSocketCTraderOpenApiClient.readOnlyObservation(...)`:

1. `ProtoOATraderRes` for balance and account metadata.
2. Asset and symbol lists.
3. `ProtoOAReconcileRes` for open positions and pending orders.
4. `ProtoOAGetPositionUnrealizedPnLRes` for position PnL.
5. `ProtoOADealListRes` for a bounded deal history.

`mapObservation(...)` converts provider money values using `moneyDigits`, maps
position and PnL identities, detects PnL coverage mismatches and records local
observation timestamps. The current mapper also reads an optional JSON
`trader.equity` field, but official `ProtoOATrader` documentation does not
define a general current equity field.

`CTraderAuthoritativeSnapshotCapability.map(...)` currently copies the numeric
observation value into the normalized account facts but deliberately creates
`EquityFact.unavailable("EQUITY_PROVIDER_SOURCE_UNVALIDATED")`. That behavior
must remain unchanged for this Story.

## Prototype Boundary

The smallest safe implementation boundary is Broker Service, adjacent to the
cTrader observation model and mapper, but separate from the normalized
`BrokerAccountSnapshotV2` used by Trading Core.

The prototype should consume a provider-neutral experimental input assembled
from existing observation facts. It should not expose cTrader DTOs to Trading
Core or Risk Domain and should not alter the production risk snapshot.

The experimental result needs to preserve:

- bound account identity;
- valuation currency;
- balance and its source metadata;
- one-to-one position/PnL identifiers;
- total net unrealized PnL;
- source timestamps and collection window;
- hypothesis and calculation version;
- consistency and validation status;
- explicit diagnostics for unknown costs and temporal gaps.

## Accounting Risks

The initial calculation hypothesis is only:

```text
candidateEquity = balance + sum(netUnrealizedPnL)
```

It must not add position swap, commission or mirroring commission unless their
relationship to `netUnrealizedPnL` is explicitly established. Adding them may
double-count costs. Omitting them may understate FTMO equity. Potential closing
commission is not supplied as a current account cost by the reviewed cTrader
contract.

The prototype must therefore expose accounting uncertainty rather than hide it
inside a numeric result.

## Temporal Risks

The current observation is not atomic. Balance, reconcile and PnL are returned
by separate requests. Position timestamps do not timestamp the PnL mark, and
the current observation does not preserve a provider account `balanceVersion`
as a coherence anchor.

Experimental controls may compare repeated observations, identifiers, values,
timestamps and balance versions. They must classify instability or missing
anchors as `INCOHERENT`, `STALE` or `UNVERIFIED`; repeated equal reads must not
be described as proof of atomicity.

## Test Boundary

Tests should exercise pure deterministic calculation and consistency checks with
fixtures only. They must not use credentials, authenticated cTrader sessions,
LIVE flags, broker calls or execution services.

The existing cTrader mapper tests prove numeric conversion and PnL coverage but
do not prove derived-equity financial validity. New tests must make that gap
explicit rather than promote fixture values to provider authority.

## Documentation and DevLog

The canonical Story directory is the required documentation location. No ADR
change is authorized at this stage.

`TOOLS.md` was not found in the repository, so DevLog lifecycle registration
configuration could not be resolved. The DevLog Story Agent applicability check
was attempted but timed out. Repository ADRs, Stories and current source remain
the authoritative basis for this analysis.

## Approval Gate

This Repository Analysis is complete and requires explicit human approval before
an Implementation Plan is prepared. No prototype code, mapper change, DTO
change or test implementation may begin before that approval.
