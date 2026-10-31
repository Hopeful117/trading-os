# cTrader EURUSD Economic Product Classification Investigation

**Investigation date:** 2026-10-31
**Scope:** provider/account evidence and domain mapping for `CTRADER/EURUSD`
**Implementation:** explicit EURUSD product classification only
**Runtime mutation:** no database or Market Data synchronization

## Question

What economic instrument is the specific cTrader market represented by:

```text
provider=CTRADER
providerSymbol=EURUSD
canonicalSymbol=EUR/USD
```

The permitted conclusions are `SPOT`, `CFD`, `OTHER`, or `UNRESOLVED`. The
existing runtime `SPOT` row is explicitly excluded as evidence because it was
created before Story 0177's fail-closed classification boundary.

## Repository Baseline

| Item | Value |
|---|---|
| Authoritative remote HEAD | `20f2419985c3c6fb7233933062e6070983a5f04a` |
| Local HEAD | `b74e41594566e7b928187834636c12ad5591b324` |
| Branch | `feat/ctrader-read-only-quote` |
| Worktree | Pre-existing broker-service modifications and diagnostic artifacts preserved |
| DevLog | Unavailable: authentication required |
| `git diff --check` before investigation | Passed |

The local branch predates the merged remote `main` commit but contains the
Story 0177 commit. No pre-existing local work was reverted or modified.

## Governing Repository Semantics

### Product identity

ADR-051 assigns canonical product identity to Market Data and keeps it separate
from account settlement:

```text
provider + canonical symbol + productType
```

The current vocabulary is:

```text
SPOT
LINEAR_PERPETUAL
CFD
```

ADR-081 requires an explicit, instrument-specific classification. Provider
identity, account environment, symbol spelling, and missing configuration are
not classification evidence. Missing, blank, invalid, or ambiguous cTrader
classification must fail closed.

### Trading OS SPOT

The only implemented compatibility meaning of `SPOT` is the inventory model:

```text
INVENTORY + SPOT -> compatible
```

The repository does not define a separate cTrader spot settlement implementation
or prove that a cTrader FX trade changes owned base/quote inventory. Therefore
mapping cTrader EURUSD to `SPOT` would require positive evidence of the
inventory/deliverable semantics expected by Trading OS, not merely an FX label.

### Trading OS CFD

`CFD` is a provider-neutral canonical identity for a leveraged or derivative
instrument. The identity was added without implementing the associated
financial model. ADR-051 and ADR-081 deliberately keep all current settlement
combinations incompatible with CFD:

```text
INVENTORY + CFD -> incompatible
CASH + CFD      -> incompatible
```

Deferred areas include cash-equity accounting, mark-to-market valuation,
margin, leverage, financing, liquidation, stop-out, and challenge semantics.

## Provider Runtime Evidence

The existing Broker Service cTrader read-only path is available. The runtime
uses the LIVE cTrader transport endpoint while the provider account is handled
by the existing `FTMO_FREE` simulated-account rule. DNS and TCP reachability
passed, and the authenticated application/account session completed the account
observation and symbol-detail request. No synchronization or write operation
was initiated.

```text
PROVIDER_RUNTIME_EVIDENCE=AVAILABLE
TRANSPORT_ENVIRONMENT=LIVE
WEBSOCKET_ENDPOINT=wss://live.ctraderapi.com:5036
ACCOUNT_AUTH=PASS
ACCOUNT_SNAPSHOT=PASS
SYMBOL_LIST=PASS
SYMBOL_DETAIL=PASS
```

The local BrokerAccount was already connected and the existing validation path
accepted the provider account as DEMO/simulated. The raw provider `isLive` flag
was `true`; this remains a provider transport fact and does not reopen the
already-resolved FTMO_FREE account interpretation. The runtime account
observation was complete with USD valuation, balance and equity of 50000, and
no open positions. These facts were read-only and were not written back as
account or market mutations.

The exact provider symbol is `EURUSD`; requesting `EUR/USD` correctly returned
`CTRADER_UNKNOWN_INSTRUMENT`. The successful read-only symbol-detail response
returned:

```text
CTRADER_SYMBOL_ID=1
CTRADER_SYMBOL_NAME=EURUSD
DIGITS=5
PIP_POSITION=4
MIN_VOLUME=100000
MAX_VOLUME=10000000000
STEP_VOLUME=100000
LOT_SIZE=10000000
STOP_LOSS_DISTANCE=0
TAKE_PROFIT_DISTANCE=0
DISTANCE_SET_IN=POINTS
```

The existing quote endpoint was also called read-only for `EURUSD` and returned
`cTrader quote facts are incomplete`. This is a quote/freshness limitation, not
a symbol-list or symbol-detail failure.

The current normalized symbol-detail contract does not expose description,
category, base/quote asset IDs, commission, swap, dynamic leverage, or account
margin rules. Those fields remain `NOT_EXPOSED`, not absent at the provider.
The adapter receives the account-specific symbol-detail response in
`resolveSymbol`, but intentionally maps only execution constraints into
`CTraderExecutionInstrumentContract`. The existing token client stores a
refresh token but implements authorization-code exchange only; access-token
refresh is not currently supported.

This investigation now includes a separate read-only metadata capability at
`GET /internal/v1/broker-accounts/{brokerAccountId}/ctrader/instrument/metadata?instrument=EURUSD`.
It returns a Broker Service value object with separate account and symbol facts;
it does not persist data, alter Market Data, or infer `SPOT`/`CFD`.

```text
DESCRIPTION=AVAILABLE
CATEGORY_ID=AVAILABLE_NAME_NOT_RETURNED
BASE_ASSET=EUR
QUOTE_ASSET=USD
MARGIN_FACTS=ACCOUNT_MARGIN_FIELDS_UNAVAILABLE
LEVERAGE_FACTS=AVAILABLE
SWAP_FINANCING_FACTS=ZERO_RUNTIME_VALUES
OTHER_PRODUCT_CLASSIFICATION_FACTS=EXPLICIT_PROVIDER_TYPE_NOT_EXPOSED
```

The new metadata capability was then called once through the running Broker
Service using the existing authenticated user session. The raw/sanitized
account-specific result was:

```text
ACCOUNT_ID=49289512
PROVIDER_IS_LIVE=true
PROVIDER_ACCOUNT_TYPE=0
ACCOUNT_CURRENCY=USD
BALANCE=50000.00
MONEY_DIGITS=2
LEVERAGE_IN_CENTS=10000
SYMBOL_ID=1
SYMBOL_NAME=EURUSD
DESCRIPTION=Euro vs United States Dollar
CATEGORY_ID=11
BASE_ASSET=EUR
QUOTE_ASSET=USD
DIGITS=5
PIP_POSITION=4
LOT_SIZE=10000000
MIN_VOLUME=100000
MAX_VOLUME=10000000000
STEP_VOLUME=100000
COMMISSION=250
COMMISSION_TYPE=2
MIN_COMMISSION=0
MIN_COMMISSION_TYPE=1
SWAP_LONG=0
SWAP_SHORT=0
TRADING_MODE=0
MEASUREMENT_UNITS=""
STOP_LOSS_DISTANCE=0
TAKE_PROFIT_DISTANCE=0
DISTANCE_SET_IN=1
```

Fields not present in the runtime response remain unavailable: equity, margin,
free margin, maximum leverage, margin calculation type, swap calculation type,
triple-swap day, and an explicit provider product type. Numeric enum and unit
codes above remain raw cTrader values; the metadata contract does not reinterpret
them.

The official cTrader enum documentation gives the following meanings for the
raw symbol values returned above:

- `commissionType=2` means `USD_PER_LOT`; cTrader describes this as usually
  used for CFDs and futures for commodities and indices.
- `minCommissionType=1` means `CURRENCY`.
- `tradingMode=0` means `ENABLED`.
- `distanceSetIn=1` means distances are expressed in points.
- `categoryId=11` remains only an identifier because the category-list response
  containing its account-specific name was not returned by this observation.

The `USD_PER_LOT` convention is relevant supporting evidence, but it is not a
settlement declaration. cTrader documents the commission convention as a
typical use, not as a universal product-type rule; the exact account/broker
contract is still required before mapping EURUSD to a Trading OS economic
product type.

The existing adapter is capable of requesting only a subset of the relevant
metadata through `CTraderExecutionInstrumentContract`: symbol ID, symbol name,
digits, pip position, volume limits, lot size, and protection distances. The
read-only observation path additionally models balance, equity, used margin,
positions, deals, swap, realized P&L, and position margin, but does not surface
all raw account/symbol fields through the diagnostic endpoint.

## Provider Contract Evidence

Official cTrader Open API documentation was consulted:

- [Model messages](https://help.ctrader.com/open-api/model-messages/)
- [Messages](https://help.ctrader.com/open-api/messages/)
- [Account authentication](https://help.ctrader.com/open-api/account-authentication)

The documentation establishes the following facts about the provider contract:

- `ProtoOALightSymbol` can expose symbol ID, symbol name, base/quote asset IDs,
  symbol category ID, and description.
- `ProtoOASymbol` can expose digits, pip position, lot size, min/max/step volume,
  schedule, commissions, stop distances, swap-long, swap-short, and the day of
  triple swap.
- cTrader account metadata can expose account leverage, maximum leverage,
  account type, margin calculation type, limited-risk settings, and stop-out
  settings.
- `ProtoOAPosition` exposes used margin, margin rate, swap, commission and
  unrealized position state.
- `ProtoOAExpectedMarginReq` explicitly estimates buy/sell margin for a symbol
  and volume.
- `ProtoOATradeData.volume` and order/deal volume are represented in cents of a
  provider unit. The official example states that protocol volume `1000` means
  `10.00` units.
- cTrader models positions, deals, closing details, gross profit, swap,
  commission, and account balance changes. This supports a provider-managed
  position/P&L lifecycle, but the generic protocol model does not by itself
  prove whether a particular broker symbol is deliverable spot or a CFD.
- The protocol includes asset classes and symbol categories, but an asset class
  such as Forex is not equivalent to a Trading OS economic product type.

The official cTrader model documentation does not provide a universal rule
that every symbol named EURUSD is `SPOT` or `CFD`. Symbol IDs and metadata are
server/account-specific, and broker-provided contract semantics remain relevant.

## FTMO Evidence

Sources were accessed on `2026-10-31` and are official FTMO sources. The linked
terms PDF is owned by FTMO Evaluation.

Official FTMO sources consulted:

- [FTMO Symbols](https://ftmo.com/en/symbols/)
- [Which instruments can I trade?](https://ftmo.com/en/faq/which-instruments-can-i-trade-and-what-strategies-am-i-allowed-to-use/)
- [Trading according to a real market](https://ftmo.com/en/faq/what-is-trading-according-to-a-real-market/)
- [Holding positions overnight](https://ftmo.com/en/faq/can-i-hold-positions-overnight-or-over-the-weekend/)

Additional contractual and account-semantics sources:

- [Terms and conditions](https://ftmo.com/en/terms-and-conditions/)
- [FTMO Challenge Terms and Conditions](https://cdn.ftmo.com/docs%2Fterms-and-conditions%2Fb1d80f4da76c4bd4bcd42a5807727e34)
- [What are the account specifications?](https://ftmo.com/en/faq/what-are-the-account-specifications/)
- [What is the difference between Balance and Equity?](https://ftmo.com/en/faq/what-is-the-difference-between-balance-and-equity/)

| Source title | Owner | Accessed | Relevant fact | Evidence scope |
|---|---|---|---|---|
| FTMO Challenge Terms and Conditions | FTMO Evaluation | 2026-10-31 | Simulated Trading uses real-market data but no actual trading or market execution takes place | Contractual account context |
| What are the account specifications? | FTMO | 2026-10-31 | Standard leverage is up to 1:100 and applies to simulated capital | Account/leverage semantics |
| What is the difference between Balance and Equity? | FTMO | 2026-10-31 | Balance changes on close; equity includes unrealized P&L | Position/P&L lifecycle |
| What is trading according to a real market? | FTMO | 2026-10-31 | FTMO platform trading is simulated with fictitious capital | Simulated environment |
| Symbols | FTMO | 2026-10-31 | Symbol specifications are published under the CFD offering | Product taxonomy |
| cTrader Open API model messages | Spotware/cTrader | 2026-10-31 | Provider positions, deals, volume, commission, leverage and P&L fields have defined protocol meanings | Provider corroboration |

FTMO currently presents the relevant account offering and Symbols page under
its CFD section, lists Forex among the available asset classes, and states that
its FTMO platform accounts use fictitious funds in a simulated environment.
The linked FTMO Challenge Terms define Simulated Trading as reproducing
financial-market conditions from liquidity-provider data and explicitly state
that no actual trading takes place and no trades are executed in financial
markets. The account-specification and balance/equity documentation describes
up-to-1:100 leverage on simulated capital, balance changes when positions are
closed, and equity including unrealized P&L from open positions.

The FTMO Free Trial documentation states that it uses the same simulated
environment and product conditions. Therefore the exact connected account
cannot create, receive, withdraw, or transfer actual EUR inventory. FTMO does
not publish the individual FTMO Account Agreement for this account in the
public sources, and the dynamic Symbols page does not expose a machine-readable
EURUSD row in its public HTML. That limits confidence to MEDIUM, but does not
support the Trading OS `SPOT` inventory interpretation.

```text
FTMO_PRODUCT_TERMINOLOGY=CFD_OFFERING
FTMO_SIMULATED_TRADING=YES
FTMO_ACTUAL_DELIVERY=NO
FTMO_ACCOUNT_AGREEMENT_PUBLIC_SAMPLE=NO
```

```text
FTMO_EVIDENCE=CFD-OFFERING / SIMULATED-TRADING / NO-ACTUAL-DELIVERY
FTMO_SYMBOL_SPECIFIC_EURUSD_PROOF=ACCOUNT-FAMILY-AND-RUNTIME-MATCH
```

## Economic Tests

| Test | Result | Reason |
|---|---|---|
| Underlying inventory ownership | `NO` | FTMO terms state no actual market trade occurs and the account uses fictitious funds; no EUR inventory can be acquired or delivered |
| Physical or deliverable settlement | `NO` | The account lifecycle is simulated and FTMO provides no EUR withdrawal/transfer/settlement right for a simulated position |
| Price-difference P&L model | `YES` | FTMO defines balance as realized P&L and equity as balance plus unrealized P&L from open positions |
| Leveraged exposure | `YES` | FTMO documents up to 1:100 leverage applied to simulated capital; runtime reports leverage `10000` centi-units |
| Margined exposure | `MEDIUM` | Leverage and account-risk semantics establish leveraged exposure, but actual EURUSD margin fields were unavailable in the runtime response |
| Position-based lifecycle | `YES` | FTMO documents open-position equity, close-based balance changes, and position-holding rules |
| Swap/financing | `UNKNOWN` | Runtime swap values were zero; that does not establish whether financing is absent or configured at zero |
| Quantity `1000` | `1000` Trading OS units -> `100000` provider centi-units | The actual symbol contract reports volume in centi-units and lot size, but economic inventory/notional meaning remains unknown |

The decisive facts are the absence of actual ownership/delivery and the
official CFD product terminology combined with a leveraged, position/P&L-based
account model. The missing public individual Account Agreement prevents HIGH
confidence, but it does not leave the SPOT inventory interpretation viable.

## Evidence Matrix

| Evidence | Source | Scope | Supports SPOT | Supports CFD/Derivative | Neutral | Ambiguous |
|---|---|---|---:|---:|---:|---:|
| FTMO product terminology | FTMO pages and navigation | Account family/product offering |  | X |  |  |
| FTMO ownership semantics | FTMO Challenge Terms | Exact simulated account model |  | X |  |  |
| FTMO settlement semantics | FTMO Challenge Terms | Simulated trading; no actual market execution |  | X |  |  |
| FTMO position/P&L semantics | FTMO Balance and Equity FAQ | Account-wide lifecycle |  | X |  |  |
| FTMO leverage/margin semantics | FTMO Account Specifications FAQ | Standard account; simulated capital |  | X |  | X |
| FTMO overnight/market-break rules | FTMO FAQ | Position holding |  | X |  |  |
| cTrader position model | Official cTrader model messages | Provider protocol |  |  |  | X |
| cTrader volume model | Official cTrader model messages | Provider protocol |  |  | X |  |
| Actual EURUSD provider metadata | Runtime account/symbol response | Exact account symbol |  |  |  | X |
| Actual leverage `10000` | Runtime plus cTrader semantics | Exact account |  | X |  |  |
| Base/quote `EUR/USD` | Runtime plus cTrader symbol model | Asset references only |  |  | X |  |
| Commission `USD_PER_LOT` | Runtime plus cTrader enum | Exact symbol convention |  | X |  | X |
| Swap long/short `0/0` | Runtime | Exact symbol configuration |  |  |  | X |

## Provider Boundary

`InstrumentProductType` is not present in the cTrader order request. The
existing execution payload contains provider account ID, instrument, side,
order type, quantity, prices, protection and client order ID. Product identity
remains a Trading OS Market Data/risk concern and is not sent to cTrader.

```text
PRODUCT_TYPE_SENT_TO_PROVIDER=NO
PRODUCT_TYPE_PROVIDER_BLOCKER=NO
```

The provider boundary still requires account/instrument-specific constraints
for safe execution. A symbol name alone is not sufficient because cTrader
symbol IDs are server/environment-specific and broker account availability can
vary.

```text
CLASSIFICATION_SCOPE=PROVIDER_ACCOUNT_SYMBOL
```

## Other Symbols

No positive provider evidence was obtained for GBPUSD or XAUUSD either. Their
classification must remain independent:

```text
GBPUSD_CLASSIFICATION=UNRESOLVED
GBPUSD_CONFIDENCE=LOW
XAUUSD_CLASSIFICATION=UNRESOLVED
XAUUSD_CONFIDENCE=LOW
```

The stale runtime `SPOT` rows are not evidence for any of these conclusions.

## Classification Decision

```text
EURUSD_CLASSIFICATION=CFD
CLASSIFICATION_CONFIDENCE=MEDIUM
UNDERLYING_EUR_OWNERSHIP=NO
PHYSICAL_OR_DELIVERABLE_SETTLEMENT=NO
PRICE_DIFFERENCE_PNL_MODEL=YES
LEVERAGED_EXPOSURE=YES
POSITION_BASED_LIFECYCLE=YES
```

Rationale:

1. Runtime/account-specific symbol identity, assets, technical constraints,
   commission, swap, and account leverage are now available.
2. Official cTrader documentation describes the technical facts needed to
   classify the account symbol but does not classify all EURUSD symbols
   universally.
3. Official FTMO material places the exact account family and symbol catalogue
   in the CFD offering, while the linked terms define the account as simulated
   with fictitious funds and no actual market execution.
4. FTMO's official balance/equity and account-specification material establishes
   a leveraged position/P&L lifecycle rather than owned or deliverable EUR.
5. The actual cTrader account reports the matching 1:100 leverage, enabled
   EURUSD symbol, USD-per-lot commission, and position-oriented provider facts.
6. The missing public individual FTMO Account Agreement limits confidence to
   MEDIUM; it does not create positive evidence for inventory ownership.
7. The repository requires positive instrument-specific evidence and explicitly
   rejects inference from provider, symbol spelling, account environment, or
   stale persisted state. The FTMO account-family/product evidence satisfies
   that requirement for this exact bound account.

`OTHER` is not selected because the official FTMO product terminology and
economic lifecycle fit the existing provider-neutral `CFD` identity. This is a
product classification only and does not authorize settlement.

## Conditional CFD Consequences

The classification is now resolved, but the next problem is not a mapping
change. The minimum deterministic model for one controlled DEMO E2E would need,
at minimum:

- authoritative account equity/balance and used-margin facts;
- symbol-specific quantity, contract-size, price and protection constraints;
- deterministic notional and stop-based monetary-risk interpretation;
- fresh provider quote and fill authority;
- open-position quantity and entry-price reconciliation;
- unrealized and realized P&L treatment;
- protected order admission and immediate controlled close;
- zero residual exposure verification.

The following can remain deferred for the first bounded E2E unless the account
contract makes them mandatory: portfolio margin optimization, long-duration
financing history, liquidation optimization, advanced stop-out modeling,
multi-position netting complexity, and production FTMO challenge rules.

No current settlement model may be changed to enable this path. The explicit
CFD classification remains incompatible with both existing `INVENTORY` and
`CASH` settlement models under ADR-051.

## Decision Gates

```text
CLASSIFICATION_DECISION_READY=YES
RUNTIME_CONFIGURATION_READY=YES_FOR_EXPLICIT_PRODUCT_TYPE_ONLY
PRODUCT_CLASSIFICATION_GATE=RESOLVED
SETTLEMENT_COMPATIBILITY_GATE=BLOCKED
ARCHITECTURAL_DECISION_REQUIRED=YES_FOR_CFD_SETTLEMENT_MODEL
```

## Safety and Validation

```text
CTRADER_SAFETY_EXECUTION_ENABLED=false
BROKER_ORDERS=0
CLOSE_REQUESTS=0
POSITION_MUTATIONS=0
TRADEPLAN_MUTATIONS=0
ACCOUNT_MUTATIONS=0
SETTLEMENT_POLICY_MUTATIONS=0
```

## Decision Record

```text
REMOTE_MAIN=20f2419985c3c6fb7233933062e6070983a5f04a
LOCAL_HEAD=b74e41594566e7b928187834636c12ad5591b324
BRANCH=feat/ctrader-read-only-quote
WORKTREE_PRESERVED=YES
IDENTITY_GAP_REOPENED=NO
NEW_PROVIDER_INFRASTRUCTURE_ADDED=NO
BROKER_EXECUTION_PERFORMED=NO

ACCOUNT_CONTEXT=FTMO_FREE simulated/test
ASSET_CLASS=Forex
PRODUCT_TERMINOLOGY=FTMO CFD offering
SETTLEMENT_MODEL=not changed; current account remains incompatible with CFD
PROVIDER_SYMBOL=EURUSD
PROVIDER_SYMBOL_ID=1
RAW_LEVERAGE_IN_CENTS=10000
NORMALIZED_LEVERAGE=1:100
VOLUME_SEMANTICS=provider centi-units; 1000 Trading OS units -> 100000 provider units
POSITION_MODEL=position-based simulated lifecycle with unrealized/realized P&L
PNL_MODEL=price-difference P&L
COMMISSION_MODEL=USD_PER_LOT
SWAP_FACTS=long=0, short=0
PROVIDER_EXPLICIT_PRODUCT_TYPE=NOT_EXPOSED

SPOT_DEFINITION_MATCH=NO
CFD_DEFINITION_MATCH=YES
OTHER_DEFINITION_REQUIRED=NO
EURUSD_CLASSIFICATION_BEFORE=UNRESOLVED
EURUSD_CLASSIFICATION_AFTER=CFD
CLASSIFICATION_CONFIDENCE=MEDIUM
DECISIVE_EVIDENCE=official FTMO CFD taxonomy, no-actual-trading terms, no delivery,
  leveraged simulated capital, and realized/unrealized position P&L semantics
CONFLICTING_EVIDENCE=NONE
EXACT_MISSING_FACT=public individual FTMO Account Agreement and dynamic EURUSD
  row are unavailable; this limits confidence but does not support SPOT inventory

EURUSD_CONFIG_CHANGED=YES
EURUSD_CONFIG_VALUE=CFD
GBPUSD_CHANGED=NO
XAUUSD_CHANGED=NO
DIRECT_DATABASE_MUTATION=NO
MARKET_DATA_SYNC=NO

PRODUCT_CLASSIFICATION_GATE=RESOLVED
SETTLEMENT_COMPATIBILITY_GATE=BLOCKED
RISK_GATE=NOT_RUN
EXECUTION_GATE=NOT_AUTHORIZED

METADATA_SECURITY_REGRESSION=PASS
METADATA_CLIENT_TESTS=PASS
STORY_0177_FAIL_CLOSED_TESTS=PASS
CLASSIFICATION_TESTS=PASS
GIT_DIFF_CHECK=PASS

CTRADER_SAFETY_EXECUTION_ENABLED=false
BROKER_ORDERS=0
CLOSE_REQUESTS=0
POSITION_MUTATIONS=0
TRADEPLAN_MUTATIONS=0
ACCOUNT_MUTATIONS=0
SETTLEMENT_POLICY_MUTATIONS=0

AUTHORITATIVE_CONTRACT_ESTABLISHED=YES_FOR_PRODUCT_CLASSIFICATION
FINAL_PRODUCT_CLASSIFICATION=CFD
NEXT_ENGINEERING_UNIT=minimum truthful CFD financial, settlement, valuation,
  margin, financing, risk, and challenge model under a separate approved Story/ADR
```

Focused validation already executed against the inspected contracts:

- Market Data cTrader classification tests: passed, including missing/blank
  product-type fail-closed tests.
- Trading Core settlement compatibility and market response tests: passed.
- `git diff --check`: passed.

No source production behavior, database market row, account settlement model,
TradePlan, historical provenance, mapping, settlement policy, or broker state
was modified by this investigation.
