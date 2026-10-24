# Trading OS Prop-Challenge Readiness

**Investigation date:** 2026-10-22
**Scope:** readiness to begin a real prop-firm challenge using Trading OS as a
primary workstation
**Repository baseline:** `main` at `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7`
**DevLog:** attempted once; unavailable because the configured provider returned
`429 insufficient_quota`.

## 1. Readiness Definition

This report does not ask whether prop-firm-related code exists. It asks whether a
trader could responsibly pay for and begin a real challenge without relying on
unknown provider rules, unvalidated integrations, stale account state, missing
loss controls or an untested recovery process.

Engineering V1 and prop-challenge readiness are independent:

```text
ENGINEERING_V1 = ACCEPTED_WITH_RESIDUALS
PROP_CHALLENGE_READINESS = NOT_READY
```

No profitability claim is made. System operation, challenge compliance and
trading edge are evaluated separately.

## 2. Target Challenge Status

The repository contains a provider-neutral Challenge domain and a persisted
benchmark catalog entry:

```text
provider: KRAKEN_PROP
product: EVALUATION
plan: ADVANCED
capital: USD 50,000
progression: EQUITY
settlement: CASH
instrument product: LINEAR_PERPETUAL
risk policy: 3% daily drawdown / 3% static maximum drawdown
```

This is an immutable benchmark configuration sourced from official Kraken pages
retrieved on 2026-10-13. Story 0089 is implemented but its metadata still says
`AWAITING HUMAN REVIEW`; the benchmark is not evidence that the user selected,
purchased or validated a real Kraken Prop challenge.

Historical documents mention FTMO and cTrader as future possibilities, not as a
current selected target. No current repository decision establishes a formally
selected prop firm, funded account, challenge fee, execution platform or live
operating contract.

Therefore:

```text
TARGET_CHALLENGE = NOT_SELECTED_OR_NOT_IMPLEMENTED
```

The Kraken benchmark is useful as a candidate configuration, but it must not be
treated as current operational authorization.

## 3. External Verification Required

No provider-specific rule set is current enough in the repository to authorize a
real challenge without a fresh human-approved verification. The following must
be checked against the selected provider's current official terms and platform
contracts:

- challenge phases and account lifecycle;
- profit target and progression basis;
- maximum daily loss and reset timezone;
- maximum total loss and whether it is static or trailing;
- balance versus equity semantics;
- minimum trading days and consistency rules;
- news restrictions and restricted event windows;
- overnight, weekend and rollover restrictions;
- allowed instruments and product types;
- platform availability and API execution capabilities;
- spreads, commissions, swaps/funding and fee treatment;
- leverage, margin, liquidation and stop-out semantics;
- manual broker/platform actions and reconciliation expectations;
- payout, breach, reset and challenge termination behavior;
- challenge fee, account size and personal financial risk budget.

All of these are `EXTERNAL_VERIFICATION_REQUIRED` for a real target. The
repository's 2026-10-13 Kraken benchmark provenance is historical configuration,
not a substitute for current acceptance.

## 4. Challenge Rule-Model Matrix

| Rule capability | Modelled | Enforced | Tested | Current challenge verified |
|---|---:|---:|---:|---:|
| Max daily loss/drawdown | YES | HARD_ENFORCED through Risk policy | YES | PARTIAL; benchmark is historical and unapproved |
| Max total loss/drawdown | YES | HARD_ENFORCED through Risk policy | YES | PARTIAL; static model only |
| Profit target | YES | Challenge lifecycle progression | YES | PARTIAL; benchmark only |
| Position sizing | YES | HARD_ENFORCED through generic Risk/profile rules | YES | NO provider-specific confirmation |
| Per-trade risk | YES | HARD_ENFORCED through `MAX_POSITION_RISK` | YES | NO provider-specific confirmation |
| Concurrent exposure | YES | HARD_ENFORCED through `MAX_EXPOSURE` | YES | NO provider-specific confirmation |
| Trading-day rules | NO | NOT_MODELLED | NO | EXTERNAL_VERIFICATION_REQUIRED |
| Restricted periods | NO | NOT_MODELLED | NO | EXTERNAL_VERIFICATION_REQUIRED |
| News restrictions | NO | WARNING/availability only, no execution block | Partial source tests | EXTERNAL_VERIFICATION_REQUIRED |
| Overnight/weekend rules | NO | NOT_MODELLED | NO | EXTERNAL_VERIFICATION_REQUIRED |
| Fees/commissions/funding | PARTIAL | Not complete for Challenge admission | Partial generic fee tests | EXTERNAL_VERIFICATION_REQUIRED |
| Leverage/margin/liquidation | PARTIAL | Margin may block Risk when unavailable; provider semantics incomplete | Partial | EXTERNAL_VERIFICATION_REQUIRED |
| Provider terminal actions | NO | NOT_MODELLED | NO | EXTERNAL_VERIFICATION_REQUIRED |

The current challenge Risk policy is deterministic, persisted and version-bound,
but it covers only the rules represented by the generic Risk domain. It cannot
claim compliance with rules that are absent from the policy or external platform.

## 5. Rule Authority

The important modeled loss rules are backend-owned and fail-closed. They are not
frontend-only warnings. However, the complete provider rule set is not translated
into a reviewed, current, immutable challenge definition. Important constraints
still exist only in provenance or are absent entirely, including fees/funding,
trading-day rules, restricted periods, news windows, overnight/weekend behavior
and provider terminal actions.

This is a `CHALLENGE_BLOCKER`: starting a real challenge would require assuming
that the generic benchmark is sufficient when the selected provider and current
rules have not been confirmed.

## 6. Daily-Loss Semantics

Current code provides explicit daily semantics:

- each account has a configured `riskTimeZone`;
- each account has a configured `riskDayResetTime`;
- `RiskDay` computes the reset interval using the configured timezone and reset;
- daily baselines are persisted by account and risk day;
- broker facts are required to be observed within the risk day;
- realized closed P&L, fees where available, open-position valuation and proposed
  trade effects feed the deterministic Risk context;
- execution-time Risk revalidation repeats the authoritative snapshot process.

The benchmark story specifies UTC and a 00:30 reset for the Kraken candidate.
The repository does not prove that this remains the selected provider's current
rule or that all provider-specific equity/balance treatment matches it.

The remaining-loss projection returned by `ChallengeProgress` is currently null:
`dailyReferenceBalance`, `dailyLossRemaining` and `totalDrawdownRemaining` are
not computed by the progress read model. The UI correctly renders them as
`Indisponible` rather than recreating Risk formulas.

Verdict: the generic daily-loss engine is `HARD_ENFORCED` for its configured
policy, but challenge compatibility is `INCONCLUSIVE` until the external target
and current semantics are verified.

## 7. Total-Drawdown Semantics

The generic Risk domain models static total drawdown against the configured
starting-balance baseline. The Kraken benchmark explicitly records a static 3%
model and has boundary tests.

There is no generic trailing-drawdown model, intraday high-water mark model or
provider-specific liquidation/termination semantics. If the selected target uses
one of those models, the current implementation cannot enforce it.

Verdict: `HARD_ENFORCED` for the configured static model; `NOT_MODELLED` for
trailing or other provider-specific models.

## 8. Challenge Progress and UI

The Challenge dashboard can show, from backend projections:

- starting capital;
- current value when available;
- target value and progress;
- current profit;
- challenge status and terminal reason;
- evaluation timestamp;
- daily and total remaining-loss fields as unavailable when not supplied.

It does not currently provide authoritative usable headroom values for daily loss
or total drawdown. A trader would still need to interpret the underlying Risk
evaluation or perform critical mental calculations. This is not sufficient as a
primary real-challenge control surface.

Classification: `PRE_CHALLENGE_REQUIRED` for a selected real provider, although
it does not reopen engineering V1 because the Challenge feature is outside the
closed PAPER V1 scope.

## 9. Account Synchronization

Broker Service has Kraken account, balance, open-position, order, close and
reconciliation capabilities. Trading Core also has a broker synchronization path
that imports account balances and open trades.

The Challenge monitoring implementation is explicitly PAPER-oriented: scheduled
monitoring skips accounts whose execution mode is not PAPER. The repository does
not provide accepted deployed evidence that a LIVE challenge account continuously
syncs authoritative balance, equity, fills, fees, open positions and realized or
unrealized P&L into the Risk path.

Local account state, market valuation and broker snapshots have different
authority boundaries. A real challenge cannot rely on stale local state for
critical loss decisions.

Verdict: `ACCOUNT SYNCHRONIZATION = INSUFFICIENT` for real challenge operation.
This is a `CHALLENGE_BLOCKER` until a selected platform's authoritative sync is
implemented and validated.

## 10. Broker and Platform Integration

| Capability | Current state |
|---|---|
| Current provider | Kraken adapter and broker-neutral contracts |
| LIVE execution | Code path exists, but no accepted deployed/live/sandbox proof for a challenge |
| Account sync | Kraken account/balance/open-position capability exists; challenge-grade cadence/authority not proven |
| Position sync | Broker position capability exists; divergence workflow not proven for a selected challenge |
| Close capability | Broker close and reconciliation capabilities exist; challenge platform not selected/validated |
| Reconciliation | Unknown outcome and close reconciliation paths exist and are tested locally |
| cTrader | No implementation found |
| FTMO | No direct integration; historical target only |

Kraken may support personal crypto activity and the candidate benchmark, but it
does not establish forex/CFD prop readiness. cTrader classification:

```text
CTRADER = NOT_IMPLEMENTED
```

This is a `CHALLENGE_BLOCKER` for any target requiring cTrader.

## 11. Execution Reconciliation and Uncertainty

The generic execution domain has strong local safety properties:

- idempotency keys are persisted;
- timeouts/provider failures become unknown outcomes;
- unknown outcomes block blind retry;
- reconciliation searches authoritative broker order state;
- ambiguous reconciliation remains blocked rather than guessed;
- close has a separate execute/reconcile lifecycle.

However, the evidence is local/test-based and Kraken-oriented. It does not prove
that the selected prop platform returns compatible order identity, partial-fill,
fee, manual-action and position semantics.

Current classification:

```text
EXECUTION_RECONCILIATION = PARTIAL
UNCERTAIN_EXECUTION_SAFETY = SAFE locally, PARTIAL for a real selected target
```

This becomes a `DRY_RUN_REQUIRED` and `CHALLENGE_BLOCKER` until a provider/platform
is selected and its uncertain paths are validated.

## 12. Position Management and Manual Broker Actions

Trading OS can manage the PAPER position lifecycle and has broker-provider close
contracts. It has not demonstrated a selected real challenge platform's full
position reconciliation after:

- direct platform-side close or modification;
- partial fill;
- broker-side stop-out/liquidation;
- restart during an open position;
- network interruption during close;
- manual action outside Trading OS.

Until that proof exists, the operational rule cannot safely be "all challenge
position management must occur through Trading OS". The official provider
platform must remain the emergency path, and any manual action must trigger a
reconciliation procedure before Trading OS authorizes another action.

Verdict: `POSITION MANAGEMENT = INSUFFICIENT` for a real challenge.

## 13. Market Data and Instrument Coverage

Market Data currently provides normalized Kraken catalogue, ticker, OHLC, order
book, recent trades, freshness and tradability facts. This is sufficient for the
closed PAPER workstation and potentially relevant to Kraken linear perpetuals.

It does not prove suitability for an unselected prop platform because:

- venue price and execution price consistency is not established;
- forex/CFD instrument coverage is not established;
- provider sessions, holidays, rollover and maintenance calendars are not
  modeled for an unselected target;
- WebSocket state is local and reconnect/multi-instance behavior remains a
  documented operational residual;
- a stale or unavailable market state correctly blocks Risk, but the selected
  challenge venue's exact data contract is unknown.

Verdict: `MARKET DATA = PARTIAL`; challenge-grade readiness is not established.

## 14. Economic Calendar and News Restrictions

The News Service contains provider-neutral economic-calendar contracts and a
XOOMAR integration path when configured. Financial news is disabled/unavailable
by default, and current evidence does not establish a permanently authoritative
calendar for a real challenge.

No deterministic Risk/execution gate currently enforces provider-specific news
blackout windows. A calendar may inform the trader, but informational display is
not compliance enforcement.

Verdict:

```text
ECONOMIC EVENT SAFETY = PARTIAL
```

If the selected provider has news restrictions, this is a `CHALLENGE_BLOCKER`.

## 15. Trading Intelligence and Active Scanner

Current deterministic intelligence supports:

- market context and freshness;
- deterministic trend/context capabilities;
- bounded active scan;
- opportunity identity and expiry;
- provenance-aware TradePlans;
- Decision Workspace continuation into manual planning.

The active scanner can identify candidates and provide structured evidence, but
there is no accepted evidence that it supplies a provider-specific complete
challenge decision process or a validated edge. Financial news is not reliably
available by default. The user may still need external charting and calendar
tools.

Classification:

```text
TRADING_INTELLIGENCE = LIMITED
```

The passive scanner is not required for a manual challenge workflow. AI is also
not required:

```text
AI_REQUIRED_FOR_CHALLENGE = NO
```

## 16. Decision-Support Completeness

The workstation can support this bounded flow:

```text
select account
  -> inspect market facts
  -> inspect deterministic context/opportunity
  -> create MANUAL TradePlan
  -> review and accept
  -> inspect Risk
  -> explicitly execute PAPER
  -> monitor/close/review
```

For a real challenge, the missing pieces are authoritative challenge headroom,
current platform-specific rules, venue-aligned data/execution, news restrictions
and operational recovery proof. The trader would still need companion tools.

## 17. External Tool Dependencies

| Tool/dependency | Classification | Reason |
|---|---|---|
| Official prop-firm/platform dashboard | CRITICAL_DEPENDENCY | Provider account state, emergency management and current rules are not owned by Trading OS |
| Selected execution platform, likely cTrader or provider platform | CRITICAL_DEPENDENCY | No cTrader integration; real target not selected or validated |
| Official economic calendar | CRITICAL_DEPENDENCY if news rules apply | Trading OS does not deterministically enforce provider news windows |
| TradingView or equivalent charting | EXPECTED_COMPANION | Useful for richer discretionary analysis; not required for PAPER V1 |
| Manual risk/challenge worksheet | TEMPORARY_GAP | Needed while authoritative daily/total headroom is unavailable |
| Broker/provider statements and reconciliation view | CRITICAL_DEPENDENCY | Needed to resolve divergence/manual actions until sync is proven |

## 18. Operating Procedure

No accepted real-challenge operating runbook exists. The minimum safe procedure
must be established before payment:

```text
pre-session provider/account/rule check
  -> verify platform and account synchronization freshness
  -> verify official economic calendar and restricted windows
  -> verify daily and total loss headroom
  -> inspect market freshness/tradability and venue alignment
  -> investigate setup
  -> create TradePlan
  -> accept plan
  -> obtain deterministic Risk approval
  -> explicitly authorize execution
  -> monitor provider and Trading OS state
  -> close through the validated path
  -> reconcile provider state
  -> review Decision History
  -> end-of-day challenge state and storage/health check
```

The current product demonstrates the PAPER subset, not the real-provider
procedure. Therefore a realistic dry-run is required.

## 19. Pre-Trade Checklist and Kill Switch

Current V1 verifies account ownership, plan acceptance, Risk and execution mode.
It does not provide a complete challenge checklist that deterministically verifies
all of the following for a selected provider:

- correct challenge account and platform;
- current challenge headroom;
- aggregate provider exposure;
- news/blackout eligibility;
- overnight/weekend permission;
- provider-specific session status;
- authoritative account synchronization freshness;
- external position divergence;
- stop-trading condition across all provider rules.

Risk fail-closed behavior blocks missing critical facts in its own configured
domain, but it is not a complete provider kill switch. This is a
`CHALLENGE_BLOCKER` until the selected provider contract is translated into
deterministic admission and tested.

## 20. Failure-Mode Matrix

| Failure | Detection | Execution blocked? | Recovery | Challenge risk |
|---|---|---:|---|---|
| Stale account | Broker/Risk snapshot freshness and unavailable facts | YES when Risk facts are incomplete; not a proven continuous LIVE gate | Refresh/reconcile provider account before action | HIGH until LIVE sync validated |
| Stale market data | Market freshness/tradability states | YES for Risk paths requiring unavailable/stale facts | Wait for fresh venue-aligned data | HIGH for execution-sensitive decisions |
| Broker unavailable | Broker errors and unknown execution status | YES for retry after unknown; not a full provider runbook | Reconcile with provider before retry | HIGH |
| Uncertain execution | `SUBMISSION_OUTCOME_UNKNOWN` | YES, blind retry blocked | Broker order reconciliation | Medium locally; HIGH until target validated |
| Account sync failure | Snapshot failure/unavailable state | Partial; no demonstrated continuous challenge admission gate | Provider dashboard/manual reconciliation | HIGH |
| Risk unavailable | `CONTEXT_UNAVAILABLE`/fail-closed result | YES for the Risk-governed path | Restore Risk facts/services | HIGH |
| Database unavailable | Service/test/startup failure; no complete challenge observability proof | Not proven as a global execution lock | Restore DB, inspect persisted/provider state | HIGH |
| Service restart | Persistence/reload tested for PAPER | PAPER yes; LIVE/provider reconciliation not proven | Reconcile provider before new action | HIGH |
| Storage pressure | S1 health and storage diagnostics | S1 protection is not a complete challenge execution kill switch | Stop/recover research and verify product services | Medium to high if shared storage degrades |
| Economic calendar unavailable | News availability states | No deterministic provider news block | Use official external calendar; do not trade restricted windows | HIGH where news rules apply |
| Manual provider-side action | Not fully detected/reconciled | Not reliably blocked | Stop Trading OS actions, reconcile provider state | HIGH |

## 21. Observability and Recovery

The repository contains service health, freshness, Risk unavailable, execution
uncertain and S1 storage diagnostics. It does not provide a single challenge
operator view proving that all critical challenge controls are healthy.

Required recovery assumptions before a real attempt:

- if Trading OS is down with an open position, use the official provider platform
  as emergency execution/management path;
- do not assume local position state is authoritative after that action;
- reconcile broker positions, orders, fills, fees and account state before another
  Trading OS execution;
- if execution is uncertain, do not retry until provider reconciliation resolves
  the outcome;
- if Market Data or Risk is unavailable, stop new entries;
- if the database is unavailable, do not infer local state from UI remnants;
- preserve provider screenshots/statements or official order references for the
  reconciliation record.

This procedure is not yet validated in a real selected platform. It is therefore
`PRE_CHALLENGE_REQUIRED` and `DRY_RUN_REQUIRED`.

## 22. S1 Relevance and Safety

S1 is not required for a discretionary manual challenge unless the user's chosen
process explicitly depends on S1-generated signals. Current evidence does not
establish that dependency:

```text
S1_REQUIRED_FOR_CHALLENGE = NO
```

S1 acquisition is healthy at observed checkpoints, but the unattended T+2/T+4/T+8
window, real retirement, replay-after-retirement and provider recovery remain
unproven. The correct independent classification is:

```text
S1_BACKGROUND = INCONCLUSIVE
```

It should not be used to claim a challenge edge or to block manual PAPER V1, but
it should not run unattended during a real challenge without storage monitoring.

## 23. Strategy and Trading-Edge Evidence

The repository documents deterministic analysis, a legacy/benchmark strategy and
manual planning support. It does not provide validated evidence of:

- a stable entry methodology used by the human trader;
- market-selection discipline under the selected provider;
- strategy expectancy or drawdown distribution;
- execution slippage/fee/funding behavior on the target platform;
- challenge-specific trade frequency or session behavior.

No PAPER profitability claim is used. The correct classification is:

```text
TRADING_EDGE_EVIDENCE = INSUFFICIENT
```

This is separate from engineering operation and is a human trading decision, not
an excuse to add speculative AI or strategy scope.

## 24. Dry-Run Decision and Acceptance Criteria

The next prudent step is not `REAL_CHALLENGE`. It is:

```text
PROP_SIMULATION / DEMO / PAPER_DRY_RUN = REQUIRED
```

The dry-run must use the selected provider's current rules, instruments, timezone,
fee/funding assumptions and intended operating procedure. Minimum acceptance
criteria derived from the current gaps:

1. A specific provider, challenge type, platform and account configuration are
   selected and documented from current official sources.
2. The configured Risk policy exactly matches the selected current loss and
   exposure rules, with deterministic boundary tests.
3. Daily reset timezone/boundary and balance/equity semantics are demonstrated
   with controlled scenarios.
4. Account balance, equity, positions, fills, fees and realized/unrealized P&L
   are synchronized from the intended execution venue with freshness evidence.
5. At least one complete non-production entry/monitor/close/reconcile journey is
   completed through the intended platform boundary.
6. Timeout, unknown response, duplicate request, partial fill, restart and
   provider-side manual action scenarios are reconciled without blind retry or
   duplicate exposure.
7. A stale account, stale market, unavailable Risk, unavailable calendar and
   storage/service degradation each produce an explicit stop-trading response.
8. The user executes the daily operating procedure for a bounded observation
   period and records no unresolved account/position divergence.
9. Emergency use of the official provider platform and subsequent reconciliation
   are tested once without real challenge financial exposure.
10. Decision History remains understandable and preserves unavailable facts rather
    than fabricating challenge outcomes.

No arbitrary profitability threshold is required by this engineering readiness
assessment. Edge validation remains a separate human decision.

## 25. Financial and Human Decisions

Before paying for a challenge, the human must accept and document:

- provider and challenge type;
- challenge fee and maximum attempt budget;
- account size and permitted instruments;
- personal financial risk budget for fees and losses;
- platform and emergency execution path;
- whether the chosen trading process depends on S1, news, external charts or
  manual calculations;
- the maximum acceptable operational uncertainty.

These are not decisions the coding agent or Trading OS should silently make.

## 26. Gap Classification

| Gap | Classification | Reason |
|---|---|---|
| Current target provider/platform not selected and externally re-verified | CHALLENGE_BLOCKER | Real operation would rely on unknown rules and contracts |
| Complete provider-specific loss/rule policy | PRE_CHALLENGE_REQUIRED | Generic Risk is not a complete prop rule model |
| LIVE account synchronization and venue authority | CHALLENGE_BLOCKER | Critical Risk facts cannot rely on unproven local state |
| cTrader or selected platform integration | CHALLENGE_BLOCKER | Required if target platform is not Kraken; cTrader is absent |
| Position divergence/manual provider-action reconciliation | CHALLENGE_BLOCKER | Real exposure could be misrepresented |
| Venue-aligned market data and session semantics | PRE_CHALLENGE_REQUIRED | Current Kraken data does not prove another venue |
| News/restricted-period deterministic enforcement | PRE_CHALLENGE_REQUIRED | Informational calendar is not compliance enforcement |
| Operational failure/recovery dry-run | DRY_RUN_REQUIRED | Local tests do not prove selected real platform behavior |
| Challenge headroom projection in progress UI | PRE_CHALLENGE_REQUIRED | Current critical headroom fields are unavailable |
| S1 long-window evidence | ACCEPTABLE_OPERATIONAL_RESIDUAL | Not needed unless the challenge process depends on S1 |
| AI Engine | OPTIONAL_IMPROVEMENT | AI is not required for manual challenge operation |
| Passive scanner | OPTIONAL_IMPROVEMENT | Not required for a bounded discretionary workflow |
| Trading edge evidence | PRE_CHALLENGE_REQUIRED / human decision | No validated edge evidence supports a controlled attempt |

## 27. Final Challenge Verdict

The current system is a coherent PAPER workstation, but it is not a sufficiently
validated real prop-challenge operating system. Starting now would require
unknown provider rules, unvalidated target-platform behavior, incomplete
challenge-specific controls and an untested recovery procedure.

```text
PROP_CHALLENGE_READINESS = NOT_READY
```

The minimum critical path is deliberately bounded; it does not require adding
AI, passive scanning, a broad analytics platform or unrelated V1.1 work.
