# Repository Analysis - Story 0040

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `2f2dbdc` |
| Story 0039 | Merged and part of baseline |
| Target module | `trading-core` |
| Implementation performed | Yes - completion pass |
| New ADR required | No |

## Governing Decisions

ADR-042 requires Broker Service/provider authority for LIVE positions and
Trading Core authority for PAPER financial and position state.

ADR-043 requires distinct financial and routing identities, an explicit
canonical relation, neutral Risk Facts, mode-aware fact routing, exact profile
assignment, and fail-closed missing facts.

## Current Evidence

### T0

`TradePlanRiskEvaluationController.evaluate()` accepts a financial Account ID
in `RiskEvaluationModels.Command`. `TradePlanRiskEvaluationService` loads that
Account and validates ownership, then loads configuration and an assigned
profile. It currently calls `BrokerRiskFactsPort`, `MarketValuationPort`, and
`RequiredMarginPort` directly and does not read `ExecutionMode`.

`RiskPersistence.configuration()` checks a non-null canonical relation against
the configuration, but T0 still uses the configuration BrokerAccount ID as its
primary routing mapping. A legacy configuration can therefore remain usable
when the canonical relation is absent.

### T1

`ExecuteTradeService` invokes T1 before execution validation, attempt creation,
broker submission, and finalization. `ExecutionTimeRiskRevalidationService`
currently sets `accountId = intent.brokerAccountId()`, then loads Account,
configuration, profile, and facts using that value. This is incorrect because
the intent field is a routing identity. T1 also persists that value in the T1
financial Account field on unavailable paths.

`ValidateAndCreateService` already has the required financial Account ID from
`TradePlan.tradingAccountId()` and resolves the BrokerAccount for the intent.

### Current Facts Sources

| Facts | Current source | Required LIVE source | Required PAPER source |
|---|---|---|---|
| Balances | BrokerRiskFactsPort | Broker/provider | AccountBalance |
| Equity | BrokerRiskFactsPort plus valuation | Broker/provider plus Market Data | Local Account plus Market Data where required |
| Used margin | BrokerRiskFactsPort | Broker/provider | Explicit supported source or fail closed |
| Daily start | Broker ledger/baseline | Broker/provider | Local baseline/ledger |
| Daily closed PnL | Broker trades/ledger | Broker/provider | Local closed Trade/execution state |
| Open positions | BrokerRiskFactsPort | Broker/provider | Local OPEN Trade state |
| Entry basis | BrokerRiskFactsPort | Provider position | Local Trade/fill |
| Position value | BrokerRiskFactsPort plus Market Data | Provider plus Market Data | Local position plus Market Data |
| Protection | BrokerRiskFactsPort | Provider | Explicit local representation or fail closed |
| Current prices | MarketValuationPort | Market Data | Market Data |
| Required margin | UnavailableRequiredMarginClient | Explicit authoritative source | Explicit supported source or fail closed |

### Neutral Risk Domain

`risk-domain` already consumes neutral `AccountSnapshot`, `PortfolioSnapshot`,
`MarketSnapshot`, `RuleSetSnapshot`, `TradingContext`, and `ProposedTrade`.
It does not inspect BrokerAccount, ExecutionMode, repositories, or providers.
The missing neutrality is in Trading Core fact acquisition, not in RiskEngine.

### Market Data

`MarketValuationClient` uses the Market Data catalogue and batch valuation API.
It returns prices, conversion rates, timestamps, completeness, observation-age
metadata, and provenance. It is suitable as a valuation input boundary and must
not become account or position authority.

## Architectural Gaps

- No neutral Trading Core Risk Facts provider/router exists.
- No local PAPER facts adapter exists.
- T0 does not use the canonical relation as the primary resolver.
- T1 uses BrokerAccount ID as Account ID.
- Neither risk path selects facts from ExecutionMode.
- PAPER still depends on Broker Service financial/position facts.

## Implementation Gaps

- Local PAPER used-margin and required-margin sources are not currently available.
- Local PAPER daily baseline, closed-PnL, and protective-stop facts are incomplete.
- `PaperSettlementService` silently returns when its canonical lookup is missing.
- T0/T1 currently perform external calls inside their database transaction templates.

These gaps do not require a new architectural decision. Unsupported required
facts must remain fail-closed and any new PAPER margin/protection semantics must
remain separate decisions.

## Recommended Boundary

Add a mode-aware application fact provider/router above the existing LIVE
`BrokerRiskFactsPort` adapter. Its input should include the financial Account,
canonical BrokerAccount, ExecutionMode, evaluation time window, and proposed
trade context. Its output should be neutral Risk Domain snapshots plus source
metadata and completeness information.

The Risk Domain must remain unchanged.
