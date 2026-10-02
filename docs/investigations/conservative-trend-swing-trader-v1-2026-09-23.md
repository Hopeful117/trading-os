# Investigation - Conservative Trend-Following Swing Trader V1

**Date:** 2026-09-23
**Repository baseline:** Story 0061 merged; current repository HEAD inspected locally
**Status:** Investigation complete; no implementation, Story, or ADR created

## 1. Executive Conclusion

Trading OS should become a **conservative decision filter for trend-following
swing trades**, not a signal generator and not an autonomous trading agent.

The product should reduce the trader's decision burden in this order:

1. Decide whether taking risk is justified at all.
2. Remove markets that are not tradable, fresh, liquid enough, or structurally
   compatible with the strategy.
3. Describe the remaining market context objectively: regime, direction,
   structure, phase, strength, volatility, timeframe agreement, important
   levels, and invalidation.
4. Let the trader decide whether the location and evidence justify a plan.
5. Let the existing deterministic Risk Engine decide whether the proposed plan
   is authorized for the account.
6. Preserve the thesis and evidence so the trader can later learn what worked.

The minimum useful next intelligence is therefore a **deterministic trend
context assessment** built from normalized multi-timeframe OHLC and current
tradability/freshness data. It should produce evidence and a conservative
classification such as:

```text
NO_SETUP
WATCH
CONTEXTUALLY_ATTRACTIVE
CONTEXTUALLY_DANGEROUS
```

Those labels are analytical guidance, not Risk decisions and not orders. The
output must also expose disagreement, missing data, and invalidation conditions
instead of compressing them into an opaque score.

The next engineering direction should be to define and validate this small
market-context intelligence slice before expanding scanning, adding a News
Service, introducing AI, or adding more indicators. The system already has the
PAPER Daily Driver, market data, active scans, opportunities, Trade Plans, and
deterministic Risk. It now needs better selectivity and explanation at the
front of that pipeline.

## 2. Trader Decision Loop

The target trader's real loop is a sequence of risk-filtering decisions, not a
sequence of screens or services.

```text
Account and environment check
    -> Is taking risk justified today?
Market universe filter
    -> Which markets deserve attention?
Trend context assessment
    -> What is the trend and where are we inside it?
Multi-timeframe and level review
    -> Is the location attractive or dangerous?
Thesis and invalidation
    -> What would prove this idea wrong?
Human plan decision
    -> Is this good enough to propose a trade?
Deterministic Risk evaluation
    -> Is this plan allowed for this account?
Human execution authorization
    -> Do I explicitly want to enter?
Holding-period review
    -> Is the original thesis still valid?
Post-trade learning
    -> What evidence and behavior should be retained?
```

The loop must be allowed to stop at every stage. In particular:

- no acceptable account or market context means `NO_TRADE`;
- no sufficiently fresh or complete evidence means `NO_TRADE` or `UNKNOWN`,
  never an optimistic interpretation;
- a Risk rejection is authoritative and cannot be overridden by intelligence;
- a favorable analytical context does not authorize execution;
- a profitable outcome does not prove that the original process was correct.

## 3. Information Requirements

### 3.1 Should I trade at all?

The trader needs a concise environment gate before inspecting individual
markets:

- account execution mode and current account availability;
- current drawdown, daily risk consumption, exposure, and open positions;
- recent trading activity and concentration, where it affects discretionary
  selectivity;
- market-wide regime and abnormal-volatility context when available;
- freshness and completeness of the underlying market evidence;
- scheduled high-impact events when a trustworthy event source exists.

The output should distinguish:

- `CLEAR_TO_REVIEW`: no known global blocker, not a trade recommendation;
- `REVIEW_WITH_CAUTION`: evidence is usable but conditions are abnormal or
  contradictory;
- `NO_TRADE_CONTEXT`: a blocking condition or insufficient evidence exists.

Account and risk constraints remain Trading Core and Risk responsibilities.
Market Intelligence may summarize them as context, but must not reimplement
the authorization rules.

### 3.2 Where should I look?

The trader needs a short, ranked attention list rather than a raw list of
hundreds of markets. A market should enter that list only after passing hard
filters:

- market is open/tradable under the normalized market-state contract;
- required OHLC timeframes are available and fresh;
- spread and basic liquidity are acceptable for the intended holding style;
- market is not obviously ranging when the trend strategy requires direction;
- price structure and directional evidence are not internally contradictory;
- current movement is not so abnormal that the setup is unsafe to interpret.

Ranking should prioritize evidence completeness, structural quality, and
selectivity. It must not optimize for the number of candidates. A zero-result
attention list is a valid and useful result.

### 3.3 What is the trend?

The trader needs an interpretation of price behavior, supported by the
underlying measurements:

- regime: trending, ranging, transitional, or unknown;
- direction: bullish, bearish, neutral, or conflicting;
- strength: weak, moderate, strong, or unavailable, with evidence;
- structure: sequence of relevant swing points and structural breaks;
- phase: emerging, expanding, pulling back, continuing, extended, weakening,
  or transitional;
- volatility state: normal, compressed, expanding, or abnormal.

Raw indicator values may be available in the evidence panel, but the primary
trader output should answer these domain questions rather than display an
indicator dashboard.

### 3.4 What is the market structure?

The minimum useful structure model is deterministic and based on confirmed
candles, not subjective chart annotations:

- confirmed swing highs and lows;
- higher-high/higher-low or lower-high/lower-low sequences;
- range boundaries when a directional sequence is absent;
- structural break only after a defined close/confirmation rule;
- continuation or failure of the prior structure;
- confidence/quality of the structure based on data completeness and the
  confirmation rule.

The platform should preserve the actual price/time references behind each
statement. It should avoid claiming that an approximate zone is an exact fact.

### 3.5 Where are we inside the trend?

Trend direction alone is insufficient. The trader needs to know whether current
price is a reasonable location for a new swing position:

- early/emerging: evidence is developing but may be immature;
- expansion/impulse: direction is strong but entry may already be extended;
- pullback: price is retracing within a still-valid structure;
- continuation: pullback evidence and resumption agree;
- extended: price is far from a reference structure or volatility baseline;
- weakening/transition: momentum or structure is deteriorating;
- unknown: evidence is insufficient.

The important output is not the label alone. It is the combination of phase,
distance from structure, volatility-adjusted extension, and invalidation.

### 3.6 Are timeframes aligned?

The trader needs a hierarchy appropriate to the holding period, not a fixed
universal set of timeframes. A valid configuration should identify:

- a context timeframe for the broader trend;
- a setup timeframe for the swing structure;
- an optional entry/refinement timeframe.

The result must show both:

- aligned direction and structure;
- disagreement, stale evidence, or an unresolved transition.

An alignment score alone is unsafe because it can hide a bearish higher-timeframe
context behind a bullish lower-timeframe move. Contradictions must be visible
and should reduce or remove the candidate from the conservative attention list.

### 3.7 Where are the important price areas?

The trader needs reference areas, each with source and uncertainty:

- recent confirmed swing highs and lows;
- range boundaries;
- prior breakout and retest areas;
- dynamic trend references such as a selected moving average;
- structural invalidation level or area;
- volatility-adjusted distance to those references;
- liquidity or participation context only when the available data supports it.

These should be represented as approximate areas or bounded levels, not as
false-precision predictions. A level is useful because it changes the thesis,
not because it is visually prominent.

### 3.8 Is momentum supporting the trend?

Momentum evidence should answer whether movement confirms, weakens, or
contradicts the structural trend. Useful evidence includes:

- directional price change and slope;
- persistence of closes in the trend direction;
- acceleration/deceleration relative to recent history;
- divergence or loss of momentum only when defined and explainable.

One or two complementary measures are preferable to an indicator collection.
Momentum should never turn a structurally poor or extended market into a
conservative setup.

### 3.9 Is volatility appropriate?

Volatility must be used as context, not as a standalone opportunity signal. The
trader needs:

- current ATR or equivalent range measure;
- volatility relative to its recent baseline;
- abnormal expansion or compression;
- distance from entry area to invalidation in volatility units;
- whether the current move is a normal pullback or an unusually large event.

Risk sizing and account authorization remain the Risk and planning boundary.
Market Intelligence may expose the distance and context that help the trader
understand a proposed plan, but it must not calculate a replacement risk limit.

### 3.10 What invalidates the trade thesis?

The system should help produce an explicit thesis record:

- directional thesis;
- structural evidence;
- phase and location evidence;
- contradictory evidence;
- invalidation condition tied to a confirmed price/structure event;
- expiry or freshness window for the observation.

Deterministic logic can derive candidate invalidation conditions from structure
and levels. The trader should remain able to add or refine a discretionary
thesis note. An AI explanation is not required to define the condition.

### 3.11 Is the potential trade worth risking capital?

Before Risk, the trader needs to understand:

- proposed entry area and its relation to current price;
- structural invalidation;
- plausible target area and relevant resistance/support;
- reward/risk under the proposed plan;
- contradictory timeframe, momentum, and volatility evidence;
- whether the market is already extended;
- evidence freshness.

Then the existing deterministic Risk Engine evaluates account-specific rules,
current facts, exposure, drawdown, margin, and the authoritative plan. Market
Intelligence must not duplicate or soften that evaluation.

### 3.12 Once in the trade, is the thesis still valid?

The trader needs a monitoring view that compares the original thesis with new
evidence:

- current regime, direction, and structure;
- phase transition or structural break;
- momentum deterioration;
- volatility regime change;
- distance to invalidation and target;
- significant new market/event evidence when available;
- current P&L and account/risk state;
- original entry rationale and current contradiction list.

This investigation does not recommend an autonomous position manager. The first
requirement is durable, explainable information and explicit human review.

### 3.13 What should the trader learn afterward?

At minimum, the platform must preserve:

- the strategy/context model version;
- all source candle ranges and selected timeframes;
- derived trend, structure, phase, momentum, volatility, and level evidence;
- the thesis and invalidation as known before execution;
- the Risk result and its authoritative snapshot references;
- human decisions, including rejection and no-trade outcomes;
- whether the trade followed or violated the plan;
- subsequent thesis changes and exit reason.

This enables later questions about setup quality without confusing outcome with
process quality.

## 4. Existing Trading OS Coverage

The current repository provides a meaningful execution and data foundation.
The following assessment is from the trader's perspective, not from the
presence of an endpoint alone.

### Market Data

**Present:**

- normalized market catalogue and market constraints;
- normalized market state and tradability;
- ticker, bid/ask, spread-related inputs, volume, and freshness information;
- historical OHLC endpoint with interval and limit;
- OHLC normalization and missing-interval handling;
- order book and recent trades streams;
- price snapshots and valuation snapshots for deterministic downstream use;
- WebSocket market updates used by the PAPER decision workspace.

**Trader value:** strong raw context and tradability filtering. The service does
not currently turn OHLC into trend structure, phase, multi-timeframe context,
or conservative setup quality.

### Market Intelligence

**Present:**

- shared intelligence context and provenance model;
- active user-triggered scan orchestration;
- deterministic capabilities and observation consolidation;
- persisted strategy definitions, versions, matches, and opportunities;
- opportunity setup snapshots and validity windows;
- active capability for OHLC high/low/range/close/price change;
- active capability for normalized bid/ask spread;
- strategy governance and traceable opportunity origin;
- disabled AI adapter, not a functioning AI engine.

**Trader value:** a durable pipeline exists from analysis to opportunity, but the
current trend semantics are weak. `Legacy OHLC Trend` matches any nonzero first-
to-last price change, uses a 15-minute horizon, and is explicitly a bootstrap,
non-validated strategy. The range-expansion strategy is a more selective
foundation, but it is still not a complete swing trend-context model. The active
analysis plan currently centers on spread and OHLC range, with optional order
flow/news sections, not implemented trend structure.

### Active scanning and opportunities

**Present:**

- account-aware active scan scope resolution;
- eligible-market filtering;
- asynchronous scan lifecycle and result projection;
- opportunity list/detail and setup provenance;
- truthful no-result behavior when the current pipeline produces no match.

**Trader value:** useful for requesting a search, but it is not yet an
aggressive conservative filter for high-quality trend swing setups. A broad scan
can still produce too many weak candidates because the underlying strategy
semantics are permissive. Passive recurring market awareness is still deferred
by ADR-033.

### Trade Planning and Risk

**Present:**

- manual and opportunity-origin Trade Plans;
- derived planning context and account-specific planning profiles;
- entry, stop, target, quantity, notional, and monetary-risk fields;
- plan acceptance/rejection and human validation;
- deterministic Risk Engine with authoritative account/risk context;
- fail-closed behavior for unavailable or contradictory risk facts;
- execution-time revalidation and explicit human execution boundary in the
  accepted PAPER baseline;
- durable execution, PAPER settlement, positions, close, and history.

**Trader value:** the risk and execution pipeline is the strongest part of the
current product. It can protect the account once a concrete plan exists. It
does not decide whether the market is a good trend setup and must not be made to
do so.

### Positions and history

**Present:**

- account-scoped PAPER positions and valuation;
- position close flow;
- execution history linked to Trade Plans where available;
- trade analytics/statistics endpoint.

**Trader value:** sufficient for the current PAPER lifecycle and basic review.
There is no dedicated thesis-validity monitoring model and no post-trade dataset
that systematically compares original analytical evidence with later evidence.

### Frontend

**Present:**

- account-scoped Decision Workspace;
- market catalogue and market detail;
- chart, ticker, order book, recent trades, constraints, and freshness states;
- active scan panel and opportunities;
- integrated manual PAPER Daily Driver;
- Trade Plan, Risk, execution, position, and history flows.

**Trader value:** the trader can now complete the accepted PAPER workflow, but
must still interpret raw charts and opportunity evidence manually. The workspace
does not yet provide a compact conservative trend-context assessment.

## 5. Trader-Centric Gap Analysis

| Decision | Need | Current Support | Gap | V1 Importance |
| --- | --- | --- | --- | --- |
| Should I trade at all? | Account, drawdown, exposure, activity, regime, abnormal conditions, events, freshness | Account/risk context and market freshness exist; Risk is authoritative | No trader-facing environment gate combining these facts; scheduled events unavailable | HIGH |
| Where should I look? | Small attention list after hard tradability and trend filters | Active scan, market eligibility, opportunities | Current trend filter is too permissive; no conservative trend-universe filter | CRITICAL |
| What is the trend? | Regime, direction, strength, structure, phase | OHLC and a legacy directional price-change rule | No robust trend state model; 15m nonzero change is not swing intelligence | CRITICAL |
| What is the structure? | Confirmed swings, HH/HL, LH/LL, breaks, ranges | Raw OHLC and chart | No deterministic structure observations or provenance | CRITICAL |
| Where are we in the trend? | Pullback, continuation, extension, weakening | Raw OHLC/range and plan inputs | No phase/location model; extension is not separated from quality | CRITICAL |
| Are timeframes aligned? | Context/setup/entry hierarchy with visible contradiction | OHLC interval support and opportunity timeframe fields | No multi-timeframe context assembly or disagreement output | CRITICAL |
| Where are important areas? | Swing levels, breakout areas, dynamic references, invalidation | Chart and raw OHLC; plan has stop/target | No explainable derived levels/areas tied to structure | HIGH |
| Is momentum supportive? | Confirmation or deterioration, not indicator soup | Price change and range metrics | No momentum evidence model or contradiction handling | HIGH |
| Is volatility appropriate? | Normal/abnormal state, ATR context, pullback vs shock | Range percentage, market snapshot, Risk facts | No ATR/baseline volatility state or trader-facing interpretation | HIGH |
| What invalidates the thesis? | Explicit structural and contextual invalidation | Plan stop/target and manual plan fields | No analytical thesis/invalidation artifact before execution | CRITICAL |
| Is it worth risking capital? | Entry, invalidation, target, R/R, contradiction before Risk | Trade Plans and deterministic Risk | Market context is too thin before plan/Risk; Risk itself is correctly separate | HIGH |
| Is the thesis still valid? | Current vs original context during holding | Positions, valuation, close, history | No trend/structure thesis monitoring or change comparison | HIGH, later V1 |
| What should I learn? | Evidence, decision, plan adherence, outcome | Trade/execution history and statistics | No durable analytical snapshot and no explicit no-trade/plan-adherence dataset | HIGH, later V1 |

## 6. Trend Intelligence Model

The following is a domain proposal for the minimum useful model. It is not an
implementation authorization.

### Regime

`TRENDING_UP`, `TRENDING_DOWN`, `RANGE`, `TRANSITION`, and `UNKNOWN` are more
useful than a binary trend flag. The classification must cite the evidence used
and carry an `UNKNOWN` path for insufficient history or contradictory measures.

### Direction

Direction is the current structural bias, not the sign of one candle or one
price-change window. It should be derived from confirmed structure and supported
by trend-reference behavior. `CONFLICTING` should be possible.

### Structure

Structure is a sequence of confirmed swing points and relations:

```text
UP: higher high + higher low
DOWN: lower high + lower low
RANGE: repeated rejection between bounded areas
BREAK: confirmed close beyond a relevant structure boundary
FAILURE: attempted continuation does not hold
```

The model must preserve candle/time/price provenance and the confirmation rule.

### Phase

Phase describes location and condition inside a trend, not a forecast:

- emerging;
- expansion;
- pullback;
- continuation;
- extended;
- weakening;
- transition;
- unknown.

Phase should be derived from structure, distance, volatility, and momentum
evidence together. No phase should be inferred from a single indicator.

### Strength

Strength should represent persistence and quality of directional evidence, not a
probability of profit. It should include:

- directional structure quality;
- slope/persistence evidence;
- volatility-normalized movement;
- participation evidence if available;
- data completeness.

An ordinal interpretation with evidence is preferable to a naked 0-100 score.

### Momentum

Momentum is supporting evidence for acceleration, persistence, or deterioration.
It must be distinguishable from direction: price can remain structurally bullish
while momentum weakens.

### Volatility

Volatility should describe a baseline-relative state:

- compressed;
- normal;
- expanding;
- abnormal;
- unknown.

It should expose ATR or equivalent values and the comparison window, but the
trader-facing interpretation should emphasize whether the current movement is
compatible with a controlled swing thesis.

### Timeframe context

The model should use role-based timeframes:

- `CONTEXT`: broad swing direction and regime;
- `SETUP`: structure and phase;
- `REFINEMENT`: optional entry context.

The selected intervals must be stored in the observation. The result must keep
each timeframe's independent state and an explicit relation:

```text
ALIGNED
PARTIALLY_ALIGNED
CONFLICTING
INSUFFICIENT
```

### Key levels

Levels are derived areas with source, time, method, and uncertainty. Candidates
include confirmed swing levels, breakout/retest areas, range boundaries, and one
selected dynamic trend reference. The product must avoid presenting every local
high/low as equally important.

### Invalidation

Invalidation is a falsifiable condition, not a stop-size calculation. Examples:

- confirmed close beyond the structural swing that supports the thesis;
- failure of a breakout/retest condition;
- higher-timeframe regime transition;
- data/event condition that makes the thesis unavailable rather than false.

The last case must be represented as `EVIDENCE_INVALIDATED` or `UNKNOWN`, not as
a market loss. Risk remains responsible for the authoritative financial stop and
authorization constraints.

### Conservative assessment output

The combined output should be an evidence package rather than a signal:

```text
MarketContextAssessment
  market and observation timestamp
  freshness/completeness
  regime
  direction
  structure summary
  phase and location
  strength evidence
  momentum evidence
  volatility state
  timeframe states and contradictions
  important areas
  candidate invalidation
  hard exclusions
  conservative assessment: NO_SETUP / WATCH / ATTRACTIVE / DANGEROUS
  provenance and model version
```

`ATTRACTIVE` means contextually worth human review only. It does not mean
approved, profitable, or executable.

## 7. Indicator Assessment

Indicators are evidence tools. The domain output should interpret them only in
combination with structure and data quality.

| Indicator | Trader question | Added information | Overlap / risk | V1 recommendation | Exposure |
| --- | --- | --- | --- | --- | --- |
| EMA / moving averages | Is price and the trend reference moving directionally? Is price extended from it? | Simple trend reference, slope, pullback location | Overlaps with slope/structure; lagging; multiple EMAs create noise | YES, one limited configuration per timeframe | Interpretation plus raw values in evidence |
| ADX or equivalent | Is directional movement strong enough to distinguish trend from range? | Trend-strength evidence independent of direction | Can rise during a late/extended move; does not define structure or direction | CONDITIONAL, only if its behavior is validated against the structure model | Interpretation and period, not an isolated signal |
| ATR | Is current movement normal, compressed, or abnormal? How far is a level in volatility units? | Volatility baseline and distance context | Not direction; must not become a sizing authority | YES, high value for capital preservation | Raw ATR, baseline relation, and interpretation |
| RSI | Is momentum supportive, weakening, or potentially stretched? | Bounded momentum and possible divergence evidence | Overbought is not a sell signal; duplicates some momentum information | DEFER initially; add only for a defined hypothesis | Interpretation only if later justified |
| MACD | Is momentum crossing or accelerating with trend? | Composite moving-average momentum | Redundant with EMA slope and momentum; parameter-sensitive | DEFER for V1 | Not exposed initially |
| Volume | Is the move supported by participation and liquidity? | Participation/confirmation, especially where volume is meaningful | Crypto venue volume may be fragmented or provider-specific; spot/derivative semantics differ | CONDITIONAL; use only when provenance and market meaning are clear | Evidence with source, not a universal score |
| VWAP | Is price accepted above/below a session or anchored reference? | Intraday/session value context | Less natural for multi-day swing context; anchor choice is subjective | DEFER for conservative swing V1; revisit for anchored use cases | Not exposed initially |

Recommended initial indicator set:

- one EMA configuration for trend reference and extension;
- ATR for baseline-relative volatility and distance context;
- a deterministic slope/persistence measure;
- optional ADX only after its role is explicitly defined and tested against
  range/trend classification.

Structure, phase, and timeframe context should be the product output. EMA, ATR,
and any ADX value should remain inspectable evidence, not standalone labels such
as `BUY` or `SELL`.

## 8. Capital Preservation Analysis

The proposed intelligence contributes to capital preservation by removing bad
reasons to trade, not by promising better returns.

### Avoiding range trades

A regime and structure assessment can exclude markets whose movement does not
support a trend strategy. This is materially safer than treating every nonzero
OHLC change as an opportunity, which the current legacy strategy effectively
does.

### Avoiding late entries

Phase, EMA distance, ATR distance, and nearby levels can identify an extended
move. The product can say `DANGEROUS` or `WATCH` even when direction and momentum
look strong.

### Exposing contradiction

Timeframe disagreement, weakening momentum, and failed structure should be
shown as contradiction. They should not be averaged away by an alignment score.

### Treating uncertainty conservatively

Stale OHLC, missing intervals, unavailable event data, or incomplete market
state should reduce confidence or produce `UNKNOWN/NO_SETUP`. The product must
not convert unavailable facts into neutral-looking evidence.

### Preserving account separation

Market Intelligence may present current exposure or risk context to help the
trader decide whether to review a market. It must not calculate a second daily
drawdown rule, risk budget, margin authorization, or position-size limit. The
Risk Engine remains the final authority.

### Reducing screen time

A small, freshness-aware attention list and explicit no-setup result are more
valuable than a constantly changing stream of signals. The trader should be
able to review fewer markets with better evidence and wait without feeling that
the system is missing an opportunity.

### Avoiding false learning

Persisting the context and thesis before execution allows later analysis to
separate a good process with a losing outcome from a bad process with a winning
outcome. This is essential for a conservative discretionary trader.

## 9. Deterministic / Statistical / AI / Human Boundaries

### Deterministic

The following should primarily be deterministic:

- OHLC normalization and freshness;
- swing-point confirmation and structure relations;
- regime classification rules;
- moving-average, slope, ATR, and selected ADX calculations;
- timeframe state and contradiction detection;
- key-level derivation and volatility-adjusted distance;
- candidate phase classification;
- evidence completeness and hard exclusions;
- provenance, versioning, and no-setup outcomes.

These outputs must be reproducible, testable, and explainable.

### Statistical

Statistical analysis may later help calibrate:

- which conditions historically produce excessive false positives;
- which phases or volatility environments deserve stricter filters;
- ranking among already eligible context assessments;
- trader-specific setup quality after enough PAPER history exists.

It should not silently become a financial authorization rule and should not be
introduced before the relevant observations are durably captured.

### AI-assisted

AI is not required for the minimum V1 intelligence. Later, AI may help:

- summarize structured evidence in trader language;
- compare the current thesis with news or macro context;
- surface questions or alternative interpretations;
- explain why evidence conflicts.

AI must not define deterministic structure, override no-trade exclusions,
authorize Risk, create an order, or hide missing evidence.

### Human-provided

The trader remains responsible for:

- selecting or confirming the strategy context and timeframe roles;
- accepting or rejecting the market assessment;
- writing or confirming discretionary thesis details;
- deciding whether a contextually attractive market deserves a Trade Plan;
- accepting Risk and explicitly authorizing execution;
- interpreting unusual events that deterministic data cannot classify;
- reviewing thesis validity while holding a position.

## 10. Minimum Useful V1 Intelligence

The smallest coherent capability that would materially improve the accepted
PAPER Daily Driver is:

### Deterministic Trend Context Assessment

For a selected market and configured context/setup timeframes, produce:

1. normalized/fresh OHLC evidence;
2. regime and directional structure;
3. confirmed swing highs/lows and structural sequence;
4. one phase/location assessment, including extension risk;
5. ATR-based volatility state and distance context;
6. timeframe alignment plus explicit disagreement;
7. a small set of important areas and candidate invalidation;
8. hard exclusion reasons and `NO_SETUP` support;
9. versioned, traceable evidence suitable for an opportunity or manual plan;
10. a compact workspace presentation before the trader creates a plan.

This does **not** require:

- an autonomous scanner that trades;
- a new AI engine;
- all listed indicators;
- a News Service;
- a new Risk Engine;
- automatic entry/exit management;
- profitability claims or a predictive probability.

The first validation target should be PAPER decision support: can the trader
review fewer markets, understand why a market is excluded or watched, and form
a thesis with an explicit invalidation before using the existing Trade Plan and
Risk flow?

## 11. Deferred Intelligence

The following should not be built before the deterministic context slice is
validated:

- indicator soup or a dashboard exposing every common indicator;
- a universal alignment score that hides timeframe contradiction;
- a broad passive scanner optimized for opportunity count;
- autonomous position-management or alerting agents;
- AI-generated trade recommendations as the primary product output;
- news/macro interpretation without a trustworthy source and event model;
- predictive win probabilities or profitability claims;
- machine-learned ranking before analytical snapshots and decisions are stored;
- automatic stop, target, or position-size changes;
- duplicating Risk calculations inside Market Intelligence;
- advanced backtesting/research infrastructure as a prerequisite for daily
  discretionary use;
- LIVE-specific behavior before the PAPER information loop is useful.

Position monitoring is important, but the first version should begin with an
information contract that compares original thesis to current evidence. It
should not begin as an autonomous management service.

## 12. Recommended Next Engineering Direction

The next coherent direction should be:

> Define and validate a deterministic, multi-timeframe Trend Context
> Assessment for PAPER discretionary decision support, integrated ahead of the
> existing Trade Plan -> Risk -> human execution flow.

The direction should proceed in this order:

1. Establish the trader-facing domain vocabulary and conservative outcome
   semantics: regime, structure, phase, location, contradiction, invalidation,
   `WATCH`, and `NO_SETUP`.
2. Define the minimum OHLC history requirements, timeframe roles, freshness
   rules, and deterministic confirmation rules.
3. Implement only the evidence required for structure, phase, ATR context,
   timeframe disagreement, levels, and invalidation.
4. Preserve the assessment as versioned, traceable intelligence evidence.
5. Expose it in the account-scoped Decision Workspace and use it to filter or
   explain attention candidates without bypassing human choice.
6. Validate the result through PAPER decisions, including deliberate no-trade
   outcomes, rejected contexts, and approved plans that continue through the
   existing Risk and execution path.
7. Only then decide whether additional indicators, passive awareness, event
   data, statistical ranking, or AI explanations materially improve the loop.

This direction comes before a larger scanner because the current scanner can
already orchestrate market analysis; its main weakness is the quality and
selectivity of the analytical semantics. It comes before AI because the missing
facts and deterministic context are not yet modeled. It comes before advanced
position monitoring because the pre-trade decision quality is the largest
current gap for the newly accepted PAPER Daily Driver.

This recommendation is a product/domain direction only. It does not authorize
implementation or define the next Engineering Story.

## Final Report

**INVESTIGATION**
COMPLETE

**TARGET TRADER**
Manual, conservative, trend-following discretionary swing trader who values
capital preservation, selectivity, reduced screen time, and accepts `NO_TRADE`.

**CURRENT TRADER SUPPORT**
Trading OS now provides a working PAPER Daily Driver from market/account context
through manual or opportunity Trade Plan, deterministic Risk, human execution,
PAPER position lifecycle, close, and history. Market Data provides rich raw
market context. Market Intelligence provides traceable active scans,
observations, strategies, opportunities, OHLC range analysis, and spread
analysis. It does not yet provide a trustworthy conservative trend-context
assessment.

**LARGEST TRADER GAP**
No deterministic, multi-timeframe model answers whether a market is structurally
trending, where it is inside that trend, whether the location is attractive or
extended, what contradicts the thesis, and what would invalidate it. The current
legacy OHLC trend semantics are too permissive for capital preservation.

**MINIMUM V1 INTELLIGENCE**
A versioned deterministic Trend Context Assessment covering regime, direction,
confirmed structure, phase/location, ATR-relative volatility, timeframe
alignment and disagreement, important areas, candidate invalidation, freshness,
and explicit `NO_SETUP`/`WATCH` outcomes.

**CAPITAL PRESERVATION CONTRIBUTION**
It filters out ranging, stale, contradictory, abnormal, illiquid, and extended
contexts before the trader spends attention or creates a plan. It exposes
uncertainty and invalidation instead of manufacturing signals, while leaving all
account authorization to the deterministic Risk Engine.

**RECOMMENDED NEXT ENGINEERING DIRECTION**
Define and validate the deterministic multi-timeframe Trend Context Assessment
in PAPER decision support before expanding scanners, indicators, events,
statistics, AI, or autonomous monitoring.

**AI REQUIRED NEXT**
NO

**NEXT STORY READY TO DEFINE**
NO. The domain direction is clear enough to guide a future Story, but the next
Story should be defined only after human review of this investigation and any
resulting acceptance criteria.

**FILES CREATED**

- `docs/investigations/conservative-trend-swing-trader-v1-2026-09-23.md`

**FILES MODIFIED**

- None
