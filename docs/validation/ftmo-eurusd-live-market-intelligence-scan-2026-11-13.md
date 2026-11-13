# FTMO EUR/USD LIVE Market Intelligence Scan

## 1. Executive Summary

Final verdict: `RUNTIME_MCP_SCHEMA_STALE_FOREX_PROFILE_NOT_OPERATIONALLY_VALIDATED`

The read-only active scan reached the live Market Intelligence runtime and
consumed the selected cTrader EUR/USD market. The first post-fix scan completed
with one generated opportunity and real provider-backed OHLC/trend evidence, but
the exposed MCP schema rejected the new `tradeability_evidence_profile` field.
The subsequent scan after the Java service reload completed with no opportunity,
confirming the local direction-consistency guard is running, but it still could
not explicitly select `FOREX_QUOTE_BASIC` through the stale MCP session.

The scan was useful for deterministic OHLC/trend analysis, but it did not
validate the new Forex quote-based LIVE tradeability path operationally. No
TradePlan, Risk evaluation, authorization, synchronization or broker mutation
was performed.

## 2. Runtime and Service Readiness

Observed at `2026-11-13`:

| Check | Result |
|---|---|
| cTrader MCP session | `active`, plant `ftmo`, environment `live` |
| cTrader session login | `17238780` |
| Trading OS account | `FTMO Free cTrader` |
| Trading OS broker account ID | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| External account ID | `49411967` |
| Execution mode | `LIVE` |
| Connection | `CONNECTED` |
| Account readiness | `CONNECTED_BUT_FACTS_INCOMPLETE` |
| Execution allowed | `false` |
| Risk evaluation allowed | `false` |
| Positions | Empty and available |
| Account balance/equity | USD 50,179.47 |
| Used/free margin | Unavailable |

The account snapshot was fresh, but incomplete because of
`USED_MARGIN_UNAVAILABLE` and `FREE_MARGIN_UNAVAILABLE`. The account
reconciliation endpoint returned `PERMISSION_DENIED`; no retry or workaround
was attempted.

The source implementation and runtime behavior were compared. The latest
Forex capability code is present locally, but the operational active-scan
contract does not expose the profile required to activate it. No service reload
was needed or performed.

## 3. Market Identity and Current Data

The selected canonical market was:

- Symbol: `EUR/USD`
- Market ID: `82852969-d309-481f-b6b8-078ff29d81e3`
- Provider: `CTRADER`
- Product: `CFD`
- Provider symbol: `1`
- Quantity unit: `UNDERLYING_UNITS`
- Price precision: `5`
- Trading status: `OPEN`
- Tradable: `true`
- Market-state update: `2026-11-13T06:40:25.521572Z`

Other EUR/USD records were not substituted: Kraken spot, Kraken perpetual and
the second cTrader spot record remain separate market identities.

Direct cTrader quote evidence:

- Bid: `1.10605`
- Ask: `1.10610`
- Spread: `0.00005` (`0.5` pip)
- Quote timestamp: `2026-11-13T06:40:38.552Z`
- Quote high/low: `1.10637` / `1.10474`
- Session close: `1.10537`

The quote was two-sided, positive, identity-resolved and observed about 35
seconds before the scan cutoff. It proves quote/spread observation only; it does
not prove liquidity suitability or executable quantity.

Read-only OHLC calls returned data for `M_15`, `H_1` and `D_1`, including volume
fields from cTrader. The scan itself used its supported OHLC/trend context; no
synthetic values were supplied.

## 4. Scan Request and Lifecycle

The supported MCP request was:

```text
start_market_scan(
  broker_account_id="7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456",
  market_ids=["82852969-d309-481f-b6b8-078ff29d81e3"],
  objective="Operational read-only EUR/USD market intelligence scan using current provider-backed quote and available OHLC evidence. Assess trend, spread, tradeability, Market Thesis and opportunity eligibility without creating a TradePlan."
)
```

The API accepted only account, objective, market IDs and idempotency key. It
does not accept `EvidenceAcquisitionPolicy` or
`TradeabilityEvidenceProfile` as request fields.

Observed result:

- Scan ID: `914cf407-6a3f-4d98-a3c8-9a9238cc956c`
- Trading account ID: `b91c63b6-9357-4f0a-9d4d-3f3e2e8c9f11`
- Scan market ID: `d5dc2af9-a47f-4008-ad96-6ff7c6f75c84`
- Analysis execution ID: `d8b9ccf5-62cd-4d78-b85c-39cfdc8eb18d`
- Created: `2026-11-13T06:41:13.023756265Z`
- Resolved cutoff: `2026-11-13T06:41:13.514943363Z`
- Completed: `2026-11-13T06:41:31.309520163Z`
- Status: `COMPLETED`
- Result quality: `DEGRADED`
- Completed markets: `1/1`
- Opportunities found: `1`

## 5. Policy and Profile Propagation

The active-scan implementation forces `EvidenceAcquisitionPolicy.LIVE_CURRENT`
when registering the child analysis, which was observed in the persisted scan
execution behavior. However, it does not call
`withTradeabilityEvidenceProfile(FOREX_QUOTE_BASIC)`.

The exact boundary is:

```text
MCP start_market_scan
  -> POST /api/v1/intelligence/scans
  -> ActiveScanCreationTransaction.buildMarkets
  -> IntelligenceAnalysisRequest(..., LIVE_CURRENT)
  -> profile defaults to UNSUPPORTED
```

The direct `/api/v1/intelligence/analyses` request DTO can carry a
`tradeabilityEvidenceProfile`, but the active-scan DTO and MCP tool do not.
Therefore `FOREX_QUOTE_BASIC` was **not actually applied**. The scan must not
be described as a successful operational validation of ADR-100/Story 0235.

## 6. Capability and Analysis Results

The scan produced:

- OHLC trend observation: available.
- Trend context: available from cTrader.
- M15 setup: directional short signal.
- H1 and H4 context: available but not aligned.
- Spread/quote tradeability: not produced by this scan because the profile was
  `UNSUPPORTED` at the active-scan boundary.
- Level II/order-book: not requested and not inferred.
- Volume semantics: `NOT_IMPLEMENTED` in the Market Thesis.
- Advanced liquidity: `NOT_IMPLEMENTED` in the Market Thesis.

The scan's Market Thesis evidence distinguished missing evidence from negative
market evidence. In particular, `TRADEABILITY_UNAVAILABLE` was a required
missing item, not a claim that EUR/USD was illiquid.

## 7. Market Thesis and Opportunity

One opportunity was created:

- Opportunity ID: `40738c48-8674-3164-afa6-56d67999c949`
- Version: `3`
- Status: `ACTIVE`
- Direction: `SHORT`
- Scenario: `OHLC_TREND`
- Timeframe: `m15`
- Score: `100.00`
- Score semantics: `BUSINESS_PRIORITY_NOT_PROBABILITY`
- Reference price: `1.10576` at `2026-11-13T06:30:00Z`
- Trigger: directional price change `-0.01068`
- Validity: `2026-11-13T06:30:00Z` to `2026-11-13T07:00:00Z`
- Thesis ID/version: `f4fa646f-6efd-33ea-93f0-adeaa799c1ca` / `21`

The attached Market Thesis was:

- State: `NO_TRADE`
- Scenario direction: `LONG`
- `TREND_DIRECTIONAL`: `SATISFIED`
- `MOMENTUM_CONFIRMATION`: `PENDING`
- `TRADEABILITY_CONTEXT`: `PENDING`, source `TRADEABILITY_UNAVAILABLE`
- Blocking contradiction: `MOMENTUM`, `DOWN`, severity `BLOCKING`
- Weaker contradictions: timeframe direction conflict and non-aligned trends
- Optional missing evidence: `VOLUME_NOT_IMPLEMENTED`,
  `LIQUIDITY_NOT_IMPLEMENTED`

The opportunity direction `SHORT` and the attached thesis scenario direction
`LONG` are inconsistent. This is a functional traceability defect in the
current result, not evidence supporting either trade direction. It should be
investigated before treating OHLC opportunities as actionable research output.

The opportunity is not TradePlan-eligible because its Thesis is `NO_TRADE`.
No TradePlan preparation was attempted.

## 8. Blockers

| Classification | Exact blocker | Effect | Smallest justified next action |
|---|---|---|---|
| Integration/runtime contract | Active scan cannot carry `FOREX_QUOTE_BASIC`; profile defaults to `UNSUPPORTED` | Prevents quote-based Tradeability evidence and Candidate eligibility | Extend the existing active-scan request/propagation contract, then add an operational propagation test |
| Market Thesis contradiction | Required M15 momentum evidence is `BLOCKING` and `DOWN/DECELERATING` | Keeps Thesis at `NO_TRADE` | Preserve fail-closed behavior; review strategy/thesis direction semantics |
| Integration consistency | Opportunity is `SHORT`, attached Thesis scenario is `LONG` | Makes the opportunity interpretation unsafe | Trace direction mapping between trend strategy, thesis composer and opportunity projection |
| Account readiness | Used/free margin unavailable | Blocks Risk evaluation and LIVE execution | Repair authorized account fact completeness through the approved read/sync workflow; do not infer margin |
| Evidence capability | Volume and advanced liquidity are `NOT_IMPLEMENTED` | Optional analysis remains degraded; not itself a liquidity rejection | Implement only if required by an approved strategy policy |
| Authorization | Market Thesis read endpoint returned `ACCOUNT_ACCESS_DENIED` outside the opportunity projection | Prevented separate thesis endpoint retrieval | Preserve scoped access control and use the authorized opportunity projection for this scan |

## 9. Functional Usefulness Assessment

The current assistant is useful for:

- resolving the correct cTrader EUR/USD market identity;
- confirming current market state and provider-backed quotes;
- producing deterministic OHLC/trend observations;
- preserving explicit missingness and blocking contradictions;
- generating a traceable but non-actionable opportunity;
- preventing TradePlan/Risk/execution progression when the Thesis is `NO_TRADE`.

It is not yet useful for the intended ADR-100 operational claim because the
active MCP session cannot select the approved Forex quote profile. The result is
therefore a valid degraded research scan, not a successful Forex quote-based
LIVE Tradeability scan.

## 10. Recommended Next Development Priority

Highest-value next action: reconnect/reload the MCP adapter session so its tool
schema exposes `tradeability_evidence_profile`, then rerun the same scan with
`LIVE_CURRENT + FOREX_QUOTE_BASIC` and inspect the persisted execution
provenance. The Java active-scan API and direction guard are already implemented
and locally tested.

## 11. Safety Confirmation

- Read-only account, market and cTrader data only.
- Active Market Intelligence scan created; no broker operation.
- No account synchronization.
- No TradePlan created.
- No Risk evaluation mutation invoked.
- No approval or execution authorization created.
- No order, position or broker configuration changed.
- No synthetic LIVE evidence used.
- No commits, pushes or pull requests performed.

## 12. Post-Implementation Follow-up

The local Java stack was rebuilt with:

```text
docker compose up --build -d market-intelligence
```

The `market-intelligence` container restarted successfully. A new scan after
the reload produced:

- Scan ID: `fd5a3434-ab16-4241-b991-a3334a2ad22e`
- Analysis execution ID: `fd58c048-5ebe-4180-8a33-8b235a8b53de`
- Resolved cutoff: `2026-11-13T13:28:27.050215Z`
- Status: `COMPLETED`
- Result quality: `DEGRADED`
- Outcome: `COMPLETED_NO_OPPORTUNITY`
- Diagnostic: `NO_SIGNAL`, `No strategy produced a matching setup`

This is consistent with the new direction-consistency guard preventing the
previous contradictory opportunity from being emitted. It does not prove the
Forex profile was active because the MCP request could not carry the new field.

The explicit MCP call with
`tradeability_evidence_profile=FOREX_QUOTE_BASIC` was rejected by the connected
tool schema before reaching the Gateway. The connected MCP process therefore
needs a reload/reconnect before the operational profile propagation can be
validated end to end.

## 13. Post-MCP-Restart Operational Validation

Validation timestamp: `2026-11-13T13:38:02Z`.

### Refreshed MCP schema

The refreshed `trading-os_start_market_scan` tool exposes the required
parameter:

```text
tradeability_evidence_profile: Literal[
  "UNSUPPORTED",
  "FOREX_QUOTE_BASIC",
  "LEVEL_II_REQUIRED"
] = "FOREX_QUOTE_BASIC"
```

The parameter is optional in the MCP schema, with a default of
`FOREX_QUOTE_BASIC`. The validation request would have supplied it explicitly.
The local implementation is present and forwards it as the Gateway JSON field
`tradeabilityEvidenceProfile`; no local correction was required. The refreshed
schema therefore resolves the previous stale-process boundary.

### Runtime readiness and blocking boundary

The cTrader read-only session is active with plant `ftmo`, environment `live`,
profile `trading`, and login `17238780`. Current broker-side evidence for
`EURUSD` (provider symbol ID `1`) was available:

- Bid/ask: `1.10671` / `1.10671`; observed spread: `0`
- Quote timestamp: `2026-11-13T13:38:02Z`
- M15, H1 and D1 OHLC/trendbars: available with provider volume fields
- cTrader symbol: enabled, `EURUSD`, digits `5`, tags `EUR/USD`
- Account balance/equity/free margin: USD `50,179.47` / `50,180.47` /
  `49,073.83` (`depositAssetId=15` resolved to `USD`)
- Current cTrader state: one open BUY EURUSD position, volume `10,000,000`

These observations establish current broker-side quote and OHLC availability,
but they do not verify the Trading OS canonical market record, its `LIVE`
execution mode, provider provenance projection, or market-state freshness
through the Gateway. The Trading OS read-only MCP tools
`list_authorized_accounts` and `list_markets` both failed before reaching the
Gateway with non-retryable `401 AUTHENTICATION_FAILED` during agent credential
exchange. The account and market identities supplied in the previous report
therefore could not be re-verified in the refreshed runtime.

### Scan request and propagation result

No operational `start_market_scan` request was sent. Calling it would have
required the authorized Trading OS account lookup inside the MCP adapter and
would not have established the requested FTMO account or canonical market
identity after the credential-exchange failure. Consequently there are no new
scan ID, analysis execution ID, persisted effective evidence policy, profile,
capability diagnostic, Market Thesis, or opportunity result to report.

The following propagation chain remains unobserved in this runtime:

```text
OpenCode MCP
  -> start_market_scan
  -> Gateway REST API
  -> ActiveScanCreationTransaction
  -> IntelligenceAnalysisRequest
  -> TradeabilityAnalysisCapability
  -> TradeabilityCalculator
  -> Market Thesis
```

In particular, `LIVE_CURRENT` and `FOREX_QUOTE_BASIC` were not persisted or
executed by a new scan, quote-based tradeability evidence was not produced by
Trading OS, missing Level II behavior was not exercised, and no opportunity or
Candidate status was generated. The previous direction-consistency result is
preserved: the Java guard was locally implemented and the prior post-reload
scan emitted `COMPLETED_NO_OPPORTUNITY`, but it was not re-exercised after this
MCP restart.

### Remaining limitations and verdict

| Classification | Observed limitation | Effect |
|---|---|---|
| Runtime integration | Trading OS agent credential exchange fails with `401 AUTHENTICATION_FAILED` | Prevents account/market identity verification and active-scan creation |
| Market evidence | Broker quote is two-sided but currently has zero observed spread; Trading OS provenance is unavailable | Broker evidence cannot be reconciled with the canonical market projection |
| Account state | cTrader currently has an open EURUSD BUY position, unlike the previous empty snapshot | The prior account state must not be reused; no mutation was attempted |
| Validation coverage | No scan was created after restart | Profile propagation, tradeability capability execution, thesis and opportunity behavior remain unobserved |

Final verdict: `FOREX_LIVE_TRADEABILITY_STILL_BLOCKED`

The MCP schema refresh succeeded and broker-side cTrader evidence is current,
but the Trading OS Gateway authentication boundary prevents the required
readiness verification and scan execution. The most useful next functional
action is to repair or rotate the explicitly provisioned MCP agent credential,
then repeat the same read-only identity checks and active scan with
`LIVE_CURRENT` and explicit `FOREX_QUOTE_BASIC`. No Level II requirement or
strategy change is justified by this blocked run.

## 14. Post-ADR-099 Authentication Recovery - Forex Operational Validation

Validation timestamp: `2026-11-13T14:51:31Z`.

This section records the first operational scan after MCP authentication
recovery. The previous results and timestamps above remain unchanged. No new
ADR-099 investigation or credential rotation was performed.

### Authentication and authorization readiness

The persisted agent credential was accepted after restart. The following
authorized read-only calls succeeded:

- authorized account lookup;
- account read-only status;
- authorized positions lookup;
- canonical market lookup;
- active scan creation and scan projection retrieval;
- Market Thesis projection and opportunities projection.

The account remained `CONNECTED` with `executionAllowed=false` and
`riskEvaluationAllowed=false`. The account observation was fresh but partial:
equity, unrealized P&L, used margin and free margin were unavailable. The
known manual EURUSD BUY position remained outside this investigation and was
not modified. The current status snapshot contains that position, while the
dedicated positions projection also returns it; no reconciliation or mutation
was attempted.

### Account and market identity

The verified runtime identities were:

| Field | Observed value |
|---|---|
| Trading OS broker account | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| Execution mode | `LIVE` |
| Connection | `CONNECTED` |
| Canonical market | `82852969-d309-481f-b6b8-078ff29d81e3` |
| Instrument | `EUR/USD` |
| Provider | `CTRADER` |
| Product | `CFD` |
| Provider symbol | `1` |
| Market state | `OPEN`, `tradable=true` |
| Market state updated | `2026-11-13T14:51:09.470234Z` |
| Price precision | `5` |

The canonical CFD identity was selected; no Kraken or alternate cTrader
EUR/USD record was substituted.

### Actual scan request and lifecycle

The MCP request accepted and forwarded the explicit profile:

```text
start_market_scan(
  broker_account_id="7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456",
  market_ids=["82852969-d309-481f-b6b8-078ff29d81e3"],
  objective="Analyze current EUR/USD Forex CFD conditions using genuine cTrader quotes and available OHLC data. Evaluate quote-based tradeability, spread, trend, momentum, multi-timeframe context, Market Thesis and opportunity eligibility. Research only. Do not create a TradePlan or initiate execution.",
  tradeability_evidence_profile="FOREX_QUOTE_BASIC"
)
```

Observed lifecycle:

- Scan ID: `94f39117-5928-41a1-be1c-f2feac8df9f1`
- Analysis execution ID: `e506db66-604d-4f4c-9183-8a90b8450743`
- Created: `2026-11-13T14:51:17.429376Z`
- Resolved cutoff: `2026-11-13T14:51:17.646104Z`
- Completed: `2026-11-13T14:51:31.758591323Z`
- Status: `COMPLETED`
- Market eligibility: `1/1` eligible and completed
- Result quality: `PARTIAL`
- Outcome: `COMPLETED_NO_OPPORTUNITY`
- Diagnostic: `NO_SIGNAL`, `No strategy produced a matching setup`

The request was accepted by the refreshed MCP schema and the active scan
completed through the Gateway and Market Intelligence workflow. The local
implementation reviewed for this validation carries `LIVE_CURRENT` into the
child analysis and propagates `FOREX_QUOTE_BASIC` through
`ActiveScanCreationTransaction` and `AnalysisExecutionService`.

### Effective evidence and capability result

The runtime projection does not expose the persisted analysis provenance or
effective profile directly. The resulting Market Thesis is therefore the
authoritative observable downstream evidence for this scan. It was evaluated
at `2026-11-13T14:51:24.259799Z` for the scan cutoff and reached:

- State: `NO_TRADE`.
- Scenario: `LONG`.
- `TREND_DIRECTIONAL`: `SATISFIED`.
- `MOMENTUM_CONFIRMATION`: `PENDING`.
- `TRADEABILITY_CONTEXT`: `PENDING`, source `TRADEABILITY_UNAVAILABLE`.
- Blocking contradiction: structural `INVALIDATION_UP_V1`.
- Weakening contradiction: upward momentum was decelerating.
- OHLC/trend evidence: available from provider `CTRADER` at the scan cutoff.
- Volume: `NOT_IMPLEMENTED`.
- Advanced liquidity: `NOT_IMPLEMENTED`.

This is a functional runtime limitation: although the MCP request accepted
`FOREX_QUOTE_BASIC` and the scan completed, the downstream Thesis still
records `TRADEABILITY_UNAVAILABLE`. No genuine Trading OS quote/spread
observation was exposed in the scan result, and no Level II evidence was
required or inferred. Consequently, `LIVE_CURRENT` and
`FOREX_QUOTE_BASIC` are confirmed at request/schema acceptance, but are not
confirmed as effective positive tradeability evidence through the complete
capability result.

The result correctly preserves the ADR-100 boundary: missing tradeability is
not treated as negative liquidity evidence, and no spread observation is
interpreted as liquidity suitability.

### Independent provider market evidence

After the scan, separate read-only cTrader calls confirmed current provider
data for symbol ID `1`:

- Bid: `1.10575`.
- Ask: `1.10575`.
- Observed spread: `0.00000`.
- Quote timestamp: `2026-11-13T14:53:18Z`.
- M15 and D1 trendbars: available with provider volume fields.
- H1 trendbar request: failed with `SESSION_REQUIRED` (`No valid session; send initialize first`).

These calls confirm that provider-side current quote and some OHLC data were
available independently, but they do not prove that the Trading OS scan
persisted or consumed the quote. The H1 MCP diagnostic is recorded as a
separate provider-session limitation and was not bypassed or retried through a
different authorization path.

### Market Thesis, opportunities and eligibility

The scan generated no opportunity and no `CANDIDATE` result. The active
opportunity projection for `EUR/USD` was empty. The `NO_TRADE` Thesis was
consistent with the absence of a matching setup and with the blocking
structural invalidation. No score was produced; therefore the
`BUSINESS_PRIORITY_NOT_PROBABILITY` score semantics were not exercised in this
run. No TradePlan eligibility was inferred.

### Functional assessment and remaining limitations

| Classification | Observed limitation | Effect |
|---|---|---|
| Runtime evidence | Thesis persisted `TRADEABILITY_UNAVAILABLE` despite explicit `FOREX_QUOTE_BASIC` request | ADR-100 quote/spread evidence is not operationally proven |
| Observability | Active-scan projection does not expose effective evidence policy/profile or capability findings | End-to-end propagation cannot be independently audited from the authorized read projection |
| Strategy | Structural invalidation and decelerating momentum blocked the Thesis | No signal is a valid fail-closed result, not a reason to weaken safeguards |
| Evidence quality | Result quality was `PARTIAL`; volume and advanced liquidity remain unimplemented | Research remains useful for trend context but incomplete for broader intelligence |
| Account readiness | `executionAllowed=false`, `riskEvaluationAllowed=false`; margin and P&L facts incomplete | Risk and execution remain correctly blocked |
| Position consistency | Manual EURUSD position is visible and intentionally out of scope | No impact on this independent market analysis; no reconciliation attempted |

Highest-value next engineering priority: trace the post-scan capability/context
construction for `FOREX_QUOTE_BASIC` and expose persisted analysis provenance,
tradeability findings and quote/spread observations in the authorized active
scan projection. The fix must preserve fail-closed behavior for missing or
stale quote evidence and must not weaken Market Thesis, Risk or execution
boundaries. Do not require global Level II data for this basic Forex profile.

### Final verdict

`FOREX_LIVE_TRADEABILITY_STILL_BLOCKED`

The recovered credential and refreshed MCP schema enabled a real authorized
active scan for the correct LIVE cTrader EUR/USD CFD, and the scan completed
with real provider-backed trend/OHLC context. However, the downstream runtime
result still reports `TRADEABILITY_UNAVAILABLE`; genuine quote/spread evidence
was not produced in the observable capability result. The ADR-100 operational
claim therefore remains blocked, while the deterministic `NO_TRADE` outcome
and all account safety restrictions were preserved.

### Safety confirmation

- No order was submitted, modified, cancelled or closed.
- No position was modified or reconciled.
- No account synchronization was requested.
- No TradePlan, Risk evaluation, approval or execution authorization was created.
- No credential rotation, grant change or permission change was performed.
- No synthetic LIVE evidence was used.
- No commit, push or pull request was performed.

## 15. Story 0236 Post-Implementation Revalidation

Validation timestamp: `2026-11-13T15:18:09Z`.

Market Intelligence was rebuilt with the Story 0236 implementation using:

```text
docker compose up --build -d market-intelligence
```

A new read-only scan was executed for the authorized LIVE cTrader EUR/USD CFD
with explicit `FOREX_QUOTE_BASIC`. The scan completed with no opportunity and
exposed the new bounded evidence projection:

- Scan ID: `38adc407-330f-44c5-b068-9da37cdf1141`
- Analysis execution ID: `353a9b7d-0949-4930-9828-54b44f1e1c50`
- Effective policy: `LIVE_CURRENT`
- Effective profile: `FOREX_QUOTE_BASIC`
- Status: `COMPLETED`
- Result quality: `DEGRADED`
- Outcome: `COMPLETED_NO_OPPORTUNITY`
- Tradeability capability: `COMPLETED`
- Tradeability finding: `Market tradeability evidence`

The earlier `TRADEABILITY_UNAVAILABLE` boundary is no longer present in this
runtime. The projection showed available market identity and snapshot evidence
at the cutoff, plus explicit warnings for stale historical OHLC, news newer
than the cutoff and unsupported order-book replay. These warnings remain
truthful missing/degraded evidence and do not assert liquidity suitability.

No TradePlan, Risk evaluation, approval, execution authorization,
synchronization, reconciliation or broker mutation was performed.

## 16. Final Story 0236 Validation

Validation completed: `2026-11-13T16:25:37+01:00`.

### Persisted scan evidence

The target scan `38adc407-330f-44c5-b068-9da37cdf1141` persisted:

- `EvidenceAcquisitionPolicy`: `LIVE_CURRENT`.
- `TradeabilityEvidenceProfile`: `FOREX_QUOTE_BASIC`.
- Analysis cutoff: `2026-11-13T15:17:45.177243685Z`.
- Market identity context: `AVAILABLE`, source `market-data`.
- Market snapshot context: `AVAILABLE`, source `market-data`, observed at
  `2026-11-13T15:17:45.143Z`, fetched at `2026-11-13T15:17:45.773202345Z`.
- Tradeability capability: `COMPLETED`, duration `PT0.226008198S`.
- Tradeability finding: `Market tradeability evidence`.
- Tradeability capability warnings were not recorded as an execution failure.

The authorized evidence projection does not expose the numeric bid, ask,
absolute spread, pip spread or quote freshness value. The available snapshot
status and provenance prove that a provider-backed snapshot reached the
capability, but the target scan cannot be used to independently verify the
numeric quote values through this read contract. No later cTrader quote was
substituted for the target scan.

### Tradeability interpretation

The code-level calculator contract confirms that a valid snapshot emits
`SPREAD_OBSERVED` as informational evidence and does not infer liquidity from
the spread. The target scan's persisted `Market tradeability evidence` finding
proves that the capability completed and reached the downstream evidence path.
It does not prove that the full Tradeability requirement was satisfied.

### Market Thesis integration

The persisted Market Thesis for the same analysis execution is:

- State: `NO_TRADE`.
- Scenario: `LONG`.
- `TREND_DIRECTIONAL`: `SATISFIED`.
- `MOMENTUM_CONFIRMATION`: `PENDING`.
- `TRADEABILITY_CONTEXT`: `PENDING`.
- Tradeability missing evidence: `SPREAD_POLICY_UNAVAILABLE` (optional),
  `LIQUIDITY_UNAVAILABLE` (optional), `RANGE_CONTEXT_STALE` (required), and
  `NEWS_CONTEXT_UNAVAILABLE` (required).
- Blocking contradictions: structural `INVALIDATION_UP_V1` and momentum
  `DOWN` contradiction.
- Result: no opportunity and no Candidate.

Therefore the Thesis consumed the capability finding, preserved the
distinction between observed quote/spread evidence and liquidity suitability,
and correctly kept the tradeability condition pending because required range
and news evidence were not valid at the cutoff. The independent structural and
momentum contradictions also correctly prevent trading.

### Complete Maven validation

Command executed from `market-intelligence`:

```text
mvn test
```

Result:

- `BUILD SUCCESS`
- Tests run: `996`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- Total time: `02:54 min`
- `git diff --check`: passed

The test logs contained expected environment-dependent warnings from tests that
simulate unavailable Market Data discovery/JWT configuration; they caused no
test failures and no production changes were made to suppress them.

### Final verdict

`FOREX_TRADEABILITY_VALIDATED_WITH_LIMITATIONS`

The Story is not fully closable as a `FULLY_VALIDATED` Forex tradeability
claim. The execution path and bounded observability are validated, and the
capability finding reaches Market Thesis. Numeric quote/spread values are not
available in the authorized projection, and the Thesis remains pending on
required stale/unavailable context. No additional scan was necessary.

### Next priority

Add a bounded, non-secret quote measurement projection for the authorized
evidence contract, then resolve the profile-specific requiredness policy for
stale historical range and unavailable news. Preserve fail-closed behavior and
do not weaken Level II, Thesis, Risk or execution boundaries.
