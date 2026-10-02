# Story 0067 - Repository Analysis

## 1. Scope and Authority

This analysis covers the repository state for Story 0067, **Validate Trend
Context in the PAPER Decision Loop**. The canonical scope is
`story.md`. The analysis is read-only and does not implement fixes, alter
runtime configuration, enable strategies, or execute LIVE actions.

Repository state is authoritative for what currently exists. DevLog was
checked, but the Story Agent was unavailable because the DevLog request
returned HTTP 500. No conclusion below depends on unavailable DevLog context.

## 2. Story Objective

The objective is to validate deterministic Trend Context from authenticated
market inspection through human review, TradePlan, deterministic Risk, and
explicit PAPER execution. This is validation work, not parameter optimization,
profitability validation, or automatic trading.

## 3. Current Baseline

Stories 0062-0066 are represented in the repository:

- Trend Context input contract and deterministic engine.
- Durable Market Intelligence observation and assessment persistence.
- Strategy evaluation and StrategyMatch boundary.
- Decision Workspace projection and frontend read path.
- Existing human-controlled TradePlan, Risk, execution, and PAPER settlement
  paths.

Story 0066 is implemented and merged in commit `a973538` (merge commit), with
implementation commit `b2c533f`. Its targeted tests passed 29/29 and its
production build passed with bundle budget warnings. The prior full frontend
suite had nine jsdom/localStorage failures, which remain a validation concern
until reproduced or resolved.

## 4. Relevant Architecture

The intended responsibility chain is:

```text
Market Data -> Market Intelligence -> Observation -> Trend Context
    -> optional StrategyEvaluation/StrategyMatch -> Opportunity
    -> human TradePlan -> deterministic Risk -> explicit execution
    -> Trading Core PAPER settlement -> persisted position/trade
```

Trend Context is evidence and decision support. It is not Risk approval, a
StrategyMatch, an opportunity, or an execution authorization. This follows
ADR-048, ADR-041, ADR-042, ADR-047, and the Story 0067 constraints.

## 5. Authenticated Entry Points

The web application protects the relevant routes with `authGuard`:

- `/decision-workspace`
- `/opportunities`
- `/opportunities/:opportunityId`
- `/trade-planning/prepare/:opportunityId`
- `/trade-planning/manual`
- `/trade-planning/plans/:planId/versions/:version`
- `/positions`
- `/analytics`

Authentication begins through Trading Core:

- `POST /api/v1/users/register`
- `POST /api/v1/users/login`

The login response contains a JWT. The browser services call the Gateway
using the configured `gatewayUrl`; the Gateway routes the versioned API paths
to the authenticated services.

## 6. PAPER Account Prerequisites

The official journey requires an authenticated user with:

- a Trading Core account;
- a linked broker account whose execution mode is `PAPER`;
- an applicable trade-planning profile for manual TradePlan creation;
- at least one eligible and tradable market;
- current Market Data sufficient to build an observation;
- service/database runtime availability.

The manual-trade page filters accounts to PAPER broker accounts with a
non-null trade-planning profile and filters markets to `marketState.tradable`.
The repository contains no checked-in runtime seed that proves such an account
exists in a deployed environment. This must be established during runtime
validation without direct database mutation.

## 7. Decision Workspace Path

The frontend selection flow is:

1. Load authenticated Trading Core accounts.
2. Resolve `GET /api/v1/intelligence/decision-context/{accountId}`.
3. Select an eligible market.
4. Load market metadata and current Market Data.
5. Fetch `GET /api/v1/intelligence/trend-context/{marketId}`.
6. Display loading, unavailable, no-context, or loaded Trend Context states.

`DecisionWorkspace` calls the Trend Context service only after the selected
market is eligible and loaded. Failure is surfaced as an unavailable state;
there is no fallback that fabricates an assessment.

The backend endpoint authenticates the actor and reads through
`TrendContextReadService`. The read model exposes current assessment data and
its evidence/invalidation references as defined by the accepted contract.

## 8. Analysis and Observation Trigger

Market Intelligence exposes:

- `POST /api/v1/intelligence/analyses`
- `GET /api/v1/intelligence/analyses/{executionId}`
- `GET /api/v1/intelligence/analyses/{executionId}/result`
- `POST /api/v1/intelligence/scans`
- `GET /api/v1/intelligence/scans/{scanId}`

The analysis creation endpoint requires `Idempotency-Key`, accepts market,
mode, and objective, and returns `202 Accepted` with a Location pointing to the
Trading Core analysis-to-TradePlan entry point. The active-scan UI uses the
authenticated account, optional objective, and either specific markets or all
eligible markets, then polls until a terminal scan state.

The production intelligence pipeline persists a run and explicitly covers:

- market-data failure -> `FAILED_OBSERVATION`;
- missing observation -> `COMPLETED_NO_SIGNAL`;
- eligible strategy match -> persisted match and opportunity;
- multiple matches -> no arbitrary singular opportunity selection;
- ineligible strategy -> not evaluated;
- repeat execution -> idempotent by analysis execution and pipeline version.

These are automated tests with mocks at service boundaries. They are not
authenticated runtime evidence.

## 9. Strategy Boundary

The built-in `CONSERVATIVE_TREND_FOLLOWING_V1` definition requires CRYPTO,
M15, and KRAKEN and lists Trend Context semantic inputs. Its initial state is
`UNVALIDATED` and `DISABLED`. `BuiltinStrategyBootstrap` persists missing
definitions but never implicitly validates or enables them.

Therefore a favorable Trend Context alone cannot create a StrategyMatch or
opportunity. Story 0067 must record this negative evidence. The strategy must
not be enabled as part of validation.

The repository also contains a legacy OHLC trend strategy in a controlled
bootstrap state and an OHLC range expansion strategy. Any opportunity produced
by those definitions must retain its own StrategyDefinition,
StrategyEvaluation, StrategyMatch, observation, and pipeline lineage; it must
not be attributed to Trend Context merely because Trend Context is displayed.

## 10. Opportunity Path

The authenticated UI supports:

- `GET /api/v1/opportunities/active` for active opportunities;
- `GET /api/v1/opportunities/{id}` for detail;
- `/trade-planning/prepare/{opportunityId}` for human preparation.

Opportunity detail displays instrument, direction, scenario, score, origin,
version, explanation, setup, StrategyMatch reference, and observation IDs when
provided. Only an active opportunity can proceed to TradePlan creation.

The active scan panel displays per-market outcome, status, and linked
opportunities. It supports explicit no-opportunity, excluded, failed, and
processing outcomes. This is suitable for recording no-trade evidence.

## 11. TradePlan Path

The supported opportunity-to-plan operation is:

```text
POST /api/v1/trade-plans/opportunities/{opportunityId}/trade-plans
```

The frontend requires a selected account and sends an idempotency key. The
result routes the user to:

```text
/trade-planning/plans/{tradePlanId}/versions/{version}
```

Manual planning is also available through:

```text
POST /api/v1/trade-plans/manual
```

Manual planning is a human-originated path and must not be used to imply that
Trend Context generated an opportunity. The opportunity path and manual path
must remain distinguishable in validation evidence.

## 12. Human Review and Risk Path

The plan page supports:

- load plan version;
- human `ACCEPT` or `REJECT` decision;
- deterministic Risk evaluation;
- display of approved or rejected Risk result.

The Risk endpoint is:

```text
POST /api/v1/trade-plans/{planId}/versions/{version}/risk-evaluations
```

The frontend passes the plan account and an idempotency key. A rejected or
unavailable Risk result must terminate the path without an ExecutionIntent,
broker mutation, or PAPER position. This must be demonstrated as negative
evidence, not inferred from a successful case.

## 13. Explicit Execution Path

After Risk approval, the frontend calls:

```text
POST /api/v1/executions/validate
POST /api/v1/executions/{executionId}/execute
GET  /api/v1/executions/{executionId}
```

Validation carries the TradePlan ID/version, Risk evaluation ID, broker-account
reference, expiry, and idempotency key. Execution ownership is checked against
the authenticated principal. The execution page also models acknowledged,
filled, rejected, unknown, reconciliation, and recovery-blocked outcomes.

The implementation therefore preserves explicit human authorization. Trend
Context, StrategyMatch, opportunity, and Risk approval do not themselves
submit an order.

## 14. PAPER Entry Settlement

`PaperExecutionPersistenceIntegrationTest` proves a Spring-backed PAPER
execution can:

- complete without calling the live broker client;
- persist the execution intent and successful attempt;
- persist a filled simulated BrokerOrder and fill;
- debit/credit account balances;
- persist an open Trade;
- retain approved TradePlan stop-loss/take-profit values.

The test uses a test profile, H2/PostgreSQL-style persistence, mocked market
snapshots, and an explicitly constructed approved execution intent. It does
not prove the full authenticated browser journey.

## 15. PAPER Position and Close Path

Positions are queried through:

```text
GET /api/v1/accounts/{accountId}/positions
```

The frontend polls every ten seconds and exposes close confirmation and
reconciliation states. For a Trading Core/PAPER position it sends:

```text
POST /api/v1/accounts/{accountId}/positions/close
```

with `tradeId` and `Idempotency-Key`. `PositionCloseController` dispatches to
`PaperExitService` when `tradeId` is supplied. The service rejects missing,
foreign, already-closed, mode-mismatched, canonically-unlinked, or conflicting
requests.

`PaperExitAcceptanceIntegrationTest` proves entry, reload, close, reload, and
position query behavior, including idempotent replay, concurrent close
protection, and rollback on settlement failure. This is strong persistence
evidence but is still fixture-driven rather than an authenticated deployed
runtime journey.

## 16. Evidence and Lineage Model

The validation record must capture, where present:

- authenticated user and PAPER account identity, without credentials;
- market ID, symbol, provider, timeframe, and data cut-off;
- Trend Context assessment ID/version;
- input evidence and invalidation references;
- observation ID/version and source references;
- analysis execution and pipeline run;
- StrategyDefinition ID/version/status;
- StrategyEvaluation and StrategyMatch references;
- TradingOpportunity ID/version/status;
- TradePlan ID/version and human decision;
- Risk evaluation ID/decision;
- ExecutionIntent, attempt, BrokerOrder, fill, and settlement references;
- position/trade state before and after reload.

No tokens, credentials, private keys, or unnecessary raw provider payloads may
be recorded.

## 17. Required Positive Scenarios

The implementation supports validation of these scenarios, but repository
inspection alone does not mark them executed:

- understandable aligned bullish context;
- understandable aligned bearish context;
- healthy pullback context;
- current data with visible freshness/cut-off;
- applicable StrategyDefinition path with complete lineage;
- human TradePlan review;
- approved Risk and explicitly authorized PAPER execution;
- persisted open position followed by PAPER close and reload.

Each positive scenario must use a reproducible market/fixture and record the
actual IDs and timestamps returned by the official APIs.

## 18. Required Negative Scenarios

The validation must include or record evidence for:

- `NO_SETUP`;
- `WATCH`;
- `UNKNOWN`;
- stale evidence;
- incomplete or unavailable evidence;
- synthetic, gapped, or abnormal-volatility input where safely reproducible;
- favorable Trend Context with no applicable StrategyDefinition;
- Risk rejection;
- unavailable Risk;
- no execution after rejection/unavailability;
- no PAPER position after a blocked execution.

The existing production pipeline tests cover no signal, ineligible strategy,
market-data failure, and multiple-match semantics. They do not cover every
Trend Context assessment classification or the complete authenticated UI.

## 19. Runtime Readiness and Blockers

The repository provides Docker Compose services for Trading Core, Market Data,
Market Intelligence, Gateway, Web, Eureka, and PostgreSQL databases. Compose
requires JWT/service secret environment variables and may require broker
configuration. It does not provide a checked-in authenticated PAPER account
fixture or a completed end-to-end validation script.

Current runtime status is **not executed**. The following must be checked by
the human-controlled validation run:

- all required services start and register;
- Gateway routes the browser paths;
- authentication and JWT propagation work;
- a PAPER account/profile exists through supported APIs;
- market data is fresh and tradable;
- Market Intelligence can persist and reload the assessment;
- the official UI reaches each required state.

No direct database mutation, manual HTTP bridge, fabricated identifier, or
direct broker call is an acceptable substitute.

## 20. Validation Matrix

| Area | Repository evidence | Status before runtime validation |
|---|---|---|
| Authenticated login | User controller and guarded routes | Implemented, not runtime-proven |
| PAPER account selection | Manual trade page and account APIs | Implemented, prerequisites unproven |
| Current Market Data | Market Data services and freshness fields | Implemented, environment-dependent |
| Trend Context read | Backend endpoint and Decision Workspace | Implemented, not runtime-proven |
| Observation persistence | Market Intelligence persistence/read services | Implemented, automated coverage present |
| Strategy boundary | Production pipeline and governance model | Implemented; built-in Trend strategy disabled |
| No-opportunity evidence | Pipeline and scan outcomes | Partially automated; runtime not executed |
| TradePlan | Opportunity/manual API and UI | Implemented, not runtime-proven |
| Risk gate | Risk evaluation API and UI | Implemented, negative runtime case pending |
| Explicit execution | Validate/execute APIs and UI | Implemented, not runtime-proven |
| PAPER entry persistence | Spring integration test | Automated fixture evidence |
| PAPER close/reload | Acceptance integration test | Automated fixture evidence |
| Full browser journey | Angular routes/components | No authenticated E2E evidence found |
| LIVE safety | PAPER-specific tests and boundary | LIVE action excluded |

## 21. Recommendation and Definition-of-Ready

Repository implementation is sufficiently present to proceed to controlled
PAPER validation without a production fix or architectural decision. The next
work should be a human-approved validation run and a durable validation report,
not code expansion.

Before declaring Story 0067 complete, the validation report must identify the
actual environment, successful and blocked paths, negative/no-trade evidence,
lineage IDs, reload results, and the nine previously observed frontend suite
failures if they remain relevant. It must explicitly state that provisional
Trend Context profile values are not optimized and make no profitability claim.

## Q1. Can an authenticated PAPER trader inspect Trend Context before planning?

**Repository answer:** Yes, through `/decision-workspace`, the account and
market selection flow, and `GET /api/v1/intelligence/trend-context/{marketId}`.
**Evidence status:** Runtime execution pending.

## Q2. Is the Trend Context assessment understandable and provenance-bearing?

**Repository answer:** The backend read model and frontend projection expose
assessment state and evidence/invalidation data. Exact runtime content must be
recorded during validation.

## Q3. Are aligned bullish and bearish contexts supported?

**Repository answer:** The deterministic model and Trend Context contract
support directional context. No repository artifact proves both authenticated
runtime examples were executed.

## Q4. Are pullback contexts supported?

**Repository answer:** The model includes phase/alignment semantics needed for
pullback review. Runtime evidence is pending.

## Q5. Are NO_SETUP, WATCH, and UNKNOWN valid visible outcomes?

**Repository answer:** The assessment/read-model path supports non-trade states,
and the pipeline supports truthful no-signal outcomes. Required classification
examples must still be executed and recorded.

## Q6. Do stale, incomplete, synthetic, gapped, and abnormal inputs fail safely?

**Repository answer:** Safe handling is part of the accepted deterministic
contract and test requirements. Complete scenario evidence is not present in
the repository analysis inputs and must be produced by validation fixtures or
market evidence.

## Q7. Can favorable Trend Context alone create a trade?

**Repository answer:** No. The pipeline requires an eligible StrategyDefinition
and persists StrategyEvaluation/StrategyMatch before opportunity creation. The
built-in Trend Context strategy is disabled and unvalidated.

## Q8. Is the optional strategy path available?

**Repository answer:** Yes, the generic strategy boundary and production
pipeline exist. The built-in conservative Trend strategy must remain disabled;
any alternative strategy evidence must retain its own lineage.

## Q9. Does TradePlan creation remain human-controlled?

**Repository answer:** Yes. Opportunity preparation and manual planning are
separate authenticated UI paths, and plan decisions are explicit.

## Q10. Does Risk remain deterministic and authoritative?

**Repository answer:** Yes. Risk evaluation is a separate Trading Core API and
must approve before execution validation. No Trend Context output bypasses it.

## Q11. Does rejected/unavailable Risk prevent execution and position creation?

**Repository answer:** The contracts and execution validation boundary require
this. A negative runtime or integration evidence record is still required for
Story acceptance.

## Q12. Does approved PAPER execution require explicit authorization and reload?

**Repository answer:** Yes. The UI performs execution validation and explicit
execute calls; PAPER integration tests prove persistence, position creation,
close, idempotency, and reload behavior. The authenticated official journey is
not yet executed.

## Q13. What is the implementation recommendation?

Proceed with controlled authenticated PAPER validation and do not modify
production behavior unless the official journey demonstrates a concrete blocker
within an accepted contract. Do not enable `CONSERVATIVE_TREND_FOLLOWING_V1`,
perform LIVE actions, optimize parameters, or infer profitability.
