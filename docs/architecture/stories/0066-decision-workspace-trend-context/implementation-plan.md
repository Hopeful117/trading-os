# Implementation Plan: Story 0066

## Implementation Boundary

Expose the existing authenticated Trend Context read projection in the Angular
Decision Workspace for the selected eligible market.

Reuse the existing Market Intelligence endpoint and Gateway route. Do not add a
backend calculation, persistence model, API route, Gateway route, Strategy,
Risk, TradePlan, execution, or Market Structure capability.

## Planned Changes

### 1. Typed Frontend Projection

Add a frontend model matching the backend `TrendContextReadModel` and its typed
assessment values.

- Preserve nullable assessment and historical assessment fields.
- Preserve operational status separately from analytical attention.
- Model timestamps, validity, observation identity/version, provenance, role
  assessments, EMA/ATR evidence, structural references, findings,
  contradictions, exclusions, and invalidation.
- Do not calculate Trend Context values in Angular.

### 2. HTTP Service

Add `TrendContextService.findTrendContext(marketId)` using the existing
`environment.gatewayUrl` and authenticated `HttpClient` convention.

```text
GET /api/v1/intelligence/trend-context/{marketId}
```

Do not create another endpoint or HTTP abstraction.

### 3. Reactive Workspace Stream

Derive the Trend Context view from the existing account, selected-market, and
market-eligibility streams.

- No account or market produces no request.
- Ineligible markets produce no request.
- Market loading produces an explicit loading state.
- Eligible selected markets request the projection.
- `switchMap` replaces/cancels the prior market request.
- Account and market changes clear the previous assessment.
- Transport failures produce an unavailable state, never `NO_SETUP`, `WATCH`, or
  `UNKNOWN`.

### 4. Assessment Panel

Add one focused panel inside the selected-market context without redesigning the
workspace.

Display:

- operational status, validity, timestamp, and observation version;
- direction, regime, phase, and attention;
- BIAS, SETUP, and optional TRIGGER role states;
- EMA/ATR values when available;
- alignment, contradictions, exclusions, invalidation, and findings;
- profile/rule/cut-off provenance;
- explicit historical-only rendering for `lastSuccessfulAssessment`.

Label the result as analytical evidence and distinguish it from Risk approval
and execution authorization.

### 5. Regression Protection

Keep the existing market header, tradability state, ticker, chart, order book,
recent trades, and manual Trade Ticket behavior unchanged.

The Trend Context panel must not create or invoke StrategyMatch,
TradingOpportunity, TradePlan, Risk, ExecutionIntent, or broker commands.

## Focused Tests

### Service

- endpoint and market ID;
- typed response;
- backend error propagation.

### Workspace

- no request before eligible selection;
- eligible-market request;
- loading and request failure states;
- market-change clearing;
- stale/historical rendering;
- all analytical attention outcomes remain unchanged;
- optional TRIGGER absence;
- authority labels and manual workflow separation.

## Validation

Run:

```text
npm run test:ci -- --include='src/app/core/services/trend-context.service.spec.ts' --include='src/app/features/decision-workspace/decision-workspace.spec.ts'
npm run test:ci
npm run build -- --configuration production
```

The targeted Story 0066 tests must pass. The full suite result must be reported
without weakening unrelated tests. Build budget warnings may be reported if the
production build exits successfully.

## Stop Conditions

Stop for architectural review if implementation requires:

- a new backend endpoint or Gateway route;
- account-specific Trend Context semantics not present in the accepted read
  contract;
- frontend recalculation of deterministic evidence;
- changes to Strategy, Opportunity, Risk, TradePlan, or execution boundaries;
- a broad Decision Workspace redesign;
- Market Structure or new Trend Context algorithms.

## Readiness Gate

```text
CANONICAL STORY 0066: RESOLVED
EXISTING READ CONTRACT: RESOLVED
FRONTEND MODEL BOUNDARY: RESOLVED
REACTIVE MARKET SELECTION BOUNDARY: RESOLVED
AUTHORITY BOUNDARY: RESOLVED
MANUAL TRADE WORKFLOW: PRESERVED
MARKET STRUCTURE SCOPE: DEFERRED
NEW ADR REQUIRED: NO
```
