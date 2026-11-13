# FTMO LIVE Market Research to TradePlan/T0 E2E

## 1. Executive Summary

Final verdict: `E2E_T0_PARTIALLY_VALIDATED`

The supported on-demand Market Intelligence scanner consumed current cTrader
EUR/USD evidence and produced a traceable active opportunity. The opportunity
was intentionally not actionable: its Market Thesis was `NO_TRADE`, contained a
blocking structural contradiction and lacked required tradeability evidence.
The public TradePlan workflow rejected it with HTTP 422 before creating a
TradePlan. Consequently no T0 Risk evaluation was started, and no execution
authorization or broker operation was possible.

This validates the Research -> Opportunity -> TradePlan admission boundary and
its fail-closed behavior, but does not validate a completed T0 evaluation.

## 2. Preflight and Permissions

The exposed MCP workflow was inspected before invocation:

| Operation | MCP entry point | Effect | Used |
|---|---|---|---|
| Account/market reads | `get_authorized_account`, `get_authorized_account_status`, `get_authorized_positions`, `list_markets` | Read-only | Yes |
| Existing opportunities | `get_opportunities`, `get_opportunity` | Read-only | Yes |
| On-demand analysis | `start_market_scan` | Persists a local scan and analysis executions; no broker action | Yes |
| Scan projection | `get_active_scan` | Read-only | Yes |
| TradePlan creation | `create_trade_plan_from_opportunity` | Persists only through the public Trading Core workflow; no broker action | Attempted, rejected |
| T0 evaluation | `evaluate_authorized_trade_plan_risk` | Persists a deterministic evaluation | Not invoked; no valid plan existed |
| Human approval/execution | approval and execution tools | Human/broker boundary | Not invoked |

The alternative `start_market_research` entry point requires an explicit
research-scope UUID and exactly two markets. No MCP tool exposed a scope-listing
operation, so no scope was invented and that path was not invoked.

The DevLog applicability check completed but returned low-confidence context
because the requested validation artifacts were not present in its retained
projection. Repository Stories, ADRs, source code and runtime evidence were
used instead.

## 3. LIVE Account Observations

Read-only account facts were refreshed before analysis:

| Fact | Result |
|---|---|
| Trading OS broker account | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| Provider | cTrader |
| External account | `49411967` |
| Execution mode | `LIVE` |
| Connection | `CONNECTED` |
| Account status | `CONNECTED_BUT_FACTS_INCOMPLETE` |
| Execution allowed | `false` |
| Risk evaluation allowed | `false` |
| Positions | Empty and available |
| Used/free margin | Unavailable |

The scan resolved the associated Trading OS account as
`b91c63b6-9357-4f0a-9d4d-3f3e2e8c9f11`. This is distinct from the broker-account
UUID and was not substituted across boundaries.

## 4. Market Selection and Data

The selected canonical market was:

- Symbol: `EUR/USD`
- Market ID: `82852969-d309-481f-b6b8-078ff29d81e3`
- Provider: `CTRADER`
- Product: `CFD`
- Provider symbol: `1`
- Quantity unit: `UNDERLYING_UNITS`
- Price precision: `5`
- Market state: `OPEN`, `tradable=true`

Other EUR/USD records were not interchangeable: Kraken spot, Kraken linear
perpetual and a second cTrader spot record were excluded from this cTrader
account analysis.

The scan used `LIVE_CURRENT` analysis evidence. The resulting opportunity
reported a source cutoff of `2026-11-13T03:08:16.308161Z`, a 15-minute reference
period at `2026-11-13T03:00:00Z`, and validity through `2026-11-13T03:30:00Z`.
No raw quote, spread, volume or indicator was fabricated. The exposed MCP tool
set did not provide a direct OHLC endpoint; the supported scan was the source of
the structured market evidence.

## 5. Research and Opportunity

Scan:

- Scan ID: `c5662dfd-10ed-417d-8f25-a36b8d6109f9`
- Status: `COMPLETED`
- Analysis execution: `f8aee3f0-42f5-4795-9e57-477f3344ff76`
- Result quality: `DEGRADED`
- Completed markets: `1`
- Opportunities found: `1`

Opportunity:

- ID: `810ea100-7f81-3b9d-b9eb-d16726771f54`
- Version: `3`
- Status: `ACTIVE`
- Direction: `SHORT`
- Scenario: `OHLC_TREND`
- Timeframe: `m15`
- Score: `100.00`
- Reference price: `1.10606`
- Trigger: directional price change `-0.01051`
- AI analyses: none

The score was not treated as a probability. The attached thesis was
`NO_TRADE`, with:

- `TREND_DIRECTIONAL`: satisfied;
- `MOMENTUM_CONFIRMATION`: pending/contradicted by available evidence;
- `TRADEABILITY_CONTEXT`: pending because `TRADEABILITY_UNAVAILABLE`;
- blocking contradiction: `INVALIDATION_DOWN_V1` structural invalidation active;
- additional missing non-blocking capabilities: volume and advanced liquidity.

## 6. TradePlan Lifecycle

The public MCP operation was called with opportunity version `3`, the authorized
broker-account UUID and a stable idempotency key.

Result:

```text
HTTP 422
code=MARKET_INTELLIGENCE_REJECTED
```

No TradePlan ID or version was returned. No TradePlan was persisted by the
workflow. Source inspection confirms that Market Intelligence rejects a thesis
whose state is not `CANDIDATE`; the observed `NO_TRADE` state therefore provides
the deterministic reason for rejection. Trading Core preserves the stable public
code `MARKET_INTELLIGENCE_REJECTED` and now also exposes the bounded downstream
reason in `details.downstreamReason`.

## 7. T0 Risk Result

T0 was not invoked because there was no valid TradePlan. This is correct:

- no internal Risk endpoint was called;
- the account-level `riskEvaluationAllowed=false` gate was not bypassed;
- no missing margin fact was converted to zero or ignored;
- no Risk result was represented as PASS, FAIL or INDETERMINATE without a plan.

The previously observed account blockers remain relevant for any future plan:
`USED_MARGIN_UNAVAILABLE` and `FREE_MARGIN_UNAVAILABLE`. A future valid plan
must still be evaluated through the supported public Risk workflow, which can
then report rule-level requirements and missing evidence.

## 8. Defects and Corrections

The E2E exposed a traceability defect: the `createFromOpportunity` path mapped
the downstream 422 body to the generic `MARKET_INTELLIGENCE_REJECTED` code and
dropped the downstream reason, while the other orchestration paths already used
a translator.

The minimal correction was applied in:

- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanOrchestrationService.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanOrchestrationServiceTest.java`

The public error code and fail-closed behavior are unchanged. When the
downstream body contains a bounded JSON `message`, it is now exposed as
`details.downstreamReason`. The E2E rejection can therefore report both:

```text
code=MARKET_INTELLIGENCE_REJECTED
details.downstreamReason=MARKET_THESIS_PREPARATION_BLOCKED_NO_TRADE
```

No admission rule, Risk decision, broker boundary or execution authority was
changed.

## 9. Tests Executed

Passed:

- `./mvnw -q -Dtest=OpportunityTradePlanOrchestrationServiceTest test` in
  `trading-core`, including the downstream-reason regression assertion.
- `mvn -q -Dtest=OpportunityEngineTest,OpportunityPoliciesTest,OpportunitySetupSnapshotPropagationTest,AnalysisTradePlanGenerationServiceTest,OpportunityTradePlanGenerationServiceTest test` in `market-intelligence`.
- `mvn -q -Dtest=RiskEngineIntegrationTest,RiskEvaluationContextBuilderTest,RiskPolicyResolverTest test` in `risk-domain`.
- `git diff --check`.

Blocked or failed due unrelated existing test infrastructure:

- `OpportunityTradePlanPreparationIntegrationTest` could not start its Spring
  context because H2 rejects the existing V69 migration's combined
  `ALTER TABLE ... ADD COLUMN ..., ADD COLUMN ...` syntax. The production
  PostgreSQL migration had already validated successfully in the running
  container; this migration issue was not changed during the E2E.

## 10. Operational Side Effects

- One local Active Scan and its analysis execution were created through the
  supported workflow.
- One opportunity and its supporting thesis were produced by that scan.
- The TradePlan attempt was rejected; no plan, Risk evaluation or approval was
  created.
- No broker synchronization, order, position mutation, credential change,
  permission change or execution authorization occurred.

## 11. Remaining Blockers

- The observed market setup is not a valid TradePlan candidate because of its
  `NO_TRADE` thesis and blocking contradiction.
- Tradeability evidence is unavailable in the generated analysis.
- Account aggregate used/free margin remains unavailable.
- The public TradePlan rejection now preserves the specific Market Intelligence
  reason in `details.downstreamReason`; an end-to-end rerun with this response
  shape remains pending.
- A completed T0 E2E still requires a future genuine `CANDIDATE` opportunity
  and a valid TradePlan.

## 12. Final Verdict

`E2E_T0_PARTIALLY_VALIDATED`

Real cTrader market evidence was analyzed through Market Intelligence, a
traceable opportunity was produced, and the public TradePlan boundary correctly
rejected a non-actionable thesis. T0 was not reached because no valid plan
existed. No broker-side mutation occurred.
