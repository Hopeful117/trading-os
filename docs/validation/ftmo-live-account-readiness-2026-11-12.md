# FTMO Free cTrader LIVE Account Readiness Diagnosis

## 1. Executive Summary

Final verdict: `READINESS_BLOCKED_BY_PROVIDER`.

The existing FTMO Free cTrader account is connected and its identity, USD
balance, empty position/order state, leverage metadata and recent deal data are
available through the read-only provider observation. The account remains
`CONNECTED_BUT_FACTS_INCOMPLETE` because the cTrader `ProtoOATrader` response
for external account `49411967` omits current `usedMargin` and `freeMargin`.

The adapter maps those optional fields correctly to `null` and Trading Core
preserves the explicit unavailable facts. The account is therefore not eligible
for deterministic Risk evaluation or execution. No synchronization, broker
order, position mutation or permission change was performed.

## 2. Existing Account State

| Fact | Observation |
|---|---|
| Trading OS broker account | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| Provider | cTrader (`3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012`) |
| External account | `49411967` |
| Display name | `FTMO Free cTrader` |
| Execution mode | `LIVE` |
| Connection status | `CONNECTED` |
| Valuation asset | `USD` |
| Balance | `50179.47` USD |
| Equity fact | `50179.47` USD, derived for a validated empty account |
| Positions | Empty and available |
| Pending orders | Empty and available |
| Recent deals | Available and non-empty |
| Snapshot freshness | `FRESH` |
| Snapshot completeness | `INCOMPLETE` |
| Used margin | Unavailable |
| Free margin | Unavailable |

The account-status response returned:

```text
status=CONNECTED_BUT_FACTS_INCOMPLETE
executionAllowed=false
riskEvaluationAllowed=false
unavailabilityReasons=USED_MARGIN_UNAVAILABLE, FREE_MARGIN_UNAVAILABLE
```

## 3. Readiness Decision Path

The current path is:

```text
cTrader WebSocket ProtoOA responses
  -> Broker Service JsonWebSocketCTraderOpenApiClient
  -> CTraderReadOnlyObservation
  -> CTraderAuthoritativeSnapshotCapability
  -> BrokerAccountSnapshotV2
  -> Trading Core latest-snapshot read-only status
  -> CONNECTED_BUT_FACTS_INCOMPLETE
```

The authoritative boundaries are:

- Broker Service owns cTrader transport, authentication, response validation,
  provider mapping and provider-neutral observation construction.
- `CTraderAuthoritativeSnapshotCapability` determines provider snapshot
  completeness and freshness.
- Trading Core `BrokerAccountReadOnlyStatusService` reports whether the latest
  snapshot is both `COMPLETE` and `FRESH`. It does not trigger a new provider
  snapshot.
- Trading Core and Risk Domain retain deterministic eligibility checks. The
  incomplete snapshot cannot be used as current authoritative Risk input.

Relevant implementation:

- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImpl.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountReadOnlyStatusService.java`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`

## 4. Missing-Facts Inventory

| Fact | Required use | Current value | Source/path | Classification | Safe remediation |
|---|---|---|---|---|---|
| Account identity | Risk and execution identity binding | `49411967`, validated | cTrader account response; durable `BrokerConnection` binding | Available | None |
| Provider identity | Cross-provider/account isolation | cTrader registration reference, validated | `BrokerAccount` and provider registration | Available | None |
| Valuation currency | Monetary interpretation | `USD` | `depositAssetId` resolved through asset list | Available | None |
| Balance | Capital baseline | `50179.47` USD | `ProtoOATrader.balance`, scaled by `moneyDigits=2` | Available | None |
| Equity | Current account valuation | `50179.47` USD derived only under empty-account rule | Provider equity absent; validated empty-account derivation | Provider limitation for direct fact | Do not promote beyond existing validated diagnostic rule |
| Used margin | Margin/risk context | `null` | `ProtoOATrader.usedMargin` absent | `NOT_PROVIDED_BY_BROKER` / `UNSUPPORTED_PROVIDER_CAPABILITY` | Confirm with broker/provider whether an account-level source exists; otherwise remain blocked |
| Free margin | Margin/risk context | `null` | No account-level field in current `ProtoOATrader` response | `NOT_PROVIDED_BY_BROKER` / `UNSUPPORTED_PROVIDER_CAPABILITY` | Confirm with broker/provider whether another authorized read-only source exists; otherwise remain blocked |
| Leverage | Risk context and provider metadata | `100.00` | `leverageInCents=10000`, mapped with decimal scaling | Available | None |
| Position exposure | Current exposure | Empty, available | `ProtoOAReconcileRes` position collection | Available empty | None |
| Pending orders | Execution/exposure context | Empty, available | `ProtoOAReconcileRes` order collection | Available empty | None |
| Position PnL | Current valuation support | Empty, available for empty account | `ProtoOAPositionUnrealizedPnlRes` | Available empty | None |
| Deals/fills | Closed-trade and reconciliation context | Available, non-empty | `ProtoOADealListRes`, bounded seven-day window | Available | None |
| Account risk profile | Risk policy input | Not established by the read-only status contract | Trading Core risk-profile assignment | `UNKNOWN`, not the observed blocker | Inspect through the authorized Trading Core account contract before any risk evaluation |
| Market capabilities | Instrument-specific execution validation | EUR/USD catalogue available | Market Data contract and cTrader symbol metadata | Separate execution prerequisite | Validate only for a future approved plan |

The current response does not support the conclusion that currency, balance,
leverage, positions, orders or deals are missing. The remaining account-level
readiness blocker is margin availability.

## 5. cTrader Mapping Findings

The adapter performs the following safe conversions:

- `moneyDigits=2` is used to convert provider monetary integers to display
  amounts. The observed balance is therefore `50179.47`, not the raw provider
  integer.
- `depositAssetId` is resolved through the asset list to `USD`.
- `leverageInCents=10000` is mapped to `100.00`.
- Position and order collections are normalized as available empty collections
  when cTrader omits zero-entry repeated fields after response validation.
- Deal quantities are converted from cTrader volume units by dividing by 100.

The official cTrader `ProtoOATrader` model documents `balance` and
`depositAssetId` as required, while `leverageInCents`, `totalMarginCalculationType`
and `moneyDigits` are optional. It does not define current account-level
`equity`, `usedMargin` or `freeMargin` fields in that model. Used margin is
available on individual positions and margin-change events, but this account has
no open positions and the current account observation has no aggregate margin
source.

The Broker Service runtime log for the authenticated observation explicitly
reported:

```text
trader=balance=PRESENT,equity=ABSENT,usedMargin=ABSENT,freeMargin=ABSENT,
depositAssetId=PRESENT,moneyDigits=PRESENT,leverageInCents=PRESENT
```

This proves that the current blocker is not a monetary-unit conversion bug. No
mapping correction is justified by the evidence.

## 6. Synchronization and Persistence

The explicit V2 synchronization path is a POST operation and was not invoked
for this diagnosis. It would:

- read a fresh provider-authoritative snapshot;
- validate local broker-account and provider identity;
- reject non-fresh or invalid account facts;
- persist account balances, positions and snapshot metadata transactionally;
- update local account snapshot completeness and freshness metadata.

Because the current provider observation is incomplete, synchronization must
remain fail-closed for authoritative financial persistence. The prior Story 0200
controlled validation confirms that partial state is diagnostic-only and does
not authorize Risk or execution.

The read-only status operation instead reads the latest persisted provider
observation and deliberately does not contend with or trigger synchronization.
The separate read-only reconciliation check returned `UNAVAILABLE` with
`LIVE_PROVIDER_SNAPSHOT_UNAVAILABLE`; this is consistent with the provider
safety guard/cooldown boundary and is not evidence that the account identity is
invalid.

## 7. Readiness Semantics

The readiness result is justified:

- Connected: yes.
- Identity bound: yes.
- Fresh observation: yes.
- Complete account facts: no, because used and free margin are absent.
- Risk evaluation eligible: no.
- Execution eligible: no.

The system correctly keeps read-only account observation available while keeping
financial readiness fail-closed. Changing the flags or treating balance as free
margin would weaken the deterministic safety boundary and is not an acceptable
correction.

## 8. Corrections Implemented

No new code correction was implemented in this diagnosis. The existing recent
corrections remain in place:

- latest-snapshot status reads do not trigger a provider synchronization;
- deferred provider snapshots have a bounded positions-read fallback;
- incomplete snapshot reasons remain visible;
- account identity, LIVE mode and fail-closed eligibility are preserved.

The evidence does not demonstrate a mapping, persistence or freshness defect
that can be safely corrected locally.

## 9. Validation Executed

Previously executed targeted validation remains passing:

- MCP adapter client, bridge and server tests: `41 passed`.
- Trading Core `AgentBusinessControllerTest`: passed.
- Trading Core `BrokerAccountReadOnlyStatusServiceTest`: passed.
- Market Data `MarketControllerTest`: passed.
- Gateway application and downstream routing tests: passed.
- Angular `npm run check`: 589 tests passed and build completed.
- `git diff --check`: passed.

Runtime read-only validation after rebuilding `trading-app`:

- `get_authorized_account`: succeeded.
- `get_authorized_positions`: succeeded with `[]`.
- `get_authorized_account_status`: succeeded with the incomplete status and
  explicit margin reasons.
- `list_markets(EUR/USD)`: succeeded with four distinct market records.
- `get_authorized_account_reconciliation`: returned `UNAVAILABLE` with
  `LIVE_PROVIDER_SNAPSHOT_UNAVAILABLE`; no local synchronization was requested.

No broker order, position mutation, account synchronization, credential change,
risk-limit change or permission change was performed.

## 10. Remaining Blockers

1. A broker/provider-authoritative source for aggregate used margin is absent.
2. A broker/provider-authoritative source for free margin is absent.
3. The cTrader provider or FTMO configuration must clarify whether these facts
   are exposed through another authorized read-only API or are unavailable for
   this account type.
4. Risk-profile assignment was not changed or evaluated as a substitute for the
   missing provider facts.

## 11. Required Human-Authorized Operations

No operation is required to preserve the current safe state.

If the owner wants to pursue readiness, the next safe step is to authorize a
specific read-only investigation or synchronization procedure after confirming
that it only reads provider state and writes bounded local projections. The
procedure must identify the provider endpoint for aggregate margin facts before
any readiness rule is reconsidered.

No order placement or position management is required or appropriate for this
diagnosis.

## 12. Final Verdict

`READINESS_BLOCKED_BY_PROVIDER`

The account is connected and partially observable, but it is not ready for
deterministic Risk evaluation or execution. The missing facts are provider-side
aggregate margin facts, not fabricated local values or an identified adapter
conversion defect. The safe behavior is to retain read-only access and keep
`executionAllowed=false` and `riskEvaluationAllowed=false` until authoritative
margin facts are available and validated.
