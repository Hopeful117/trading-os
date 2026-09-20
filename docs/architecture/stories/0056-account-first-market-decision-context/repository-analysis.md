# Repository Analysis - Story 0056

## Scope

Story 0056 establishes the account-first foundation for the future Market
Decision Workspace. It must prevent the frontend from treating the global
market catalogue as the set of markets available to every account.

The requested behavior is account context selection and account-aware market
eligibility only. Market presentation, intelligence rendering, Trade Plan
interaction, Risk evaluation, and execution remain later slices.

## Current Repository Evidence

### Account authority

Trading Core exposes authenticated account access through:

* `GET /api/v1/accounts`;
* `GET /api/v1/accounts/{accountId}`.

`AccountController` resolves the authenticated principal and delegates account
ownership to `AccountService`. The current `AccountDto` already exposes:

* `accountId`;
* `brokerAccountId`;
* base currency;
* balances and equity;
* user identity;
* Risk Profile id and semantic version;
* Trade Planning Profile id and version.

Angular already has a typed `AccountService` and `Account` model. Existing
account selection is used by the Active Scan panel and Trade Plan preparation.

### Market authority

Market Data exposes the global catalogue through:

* `GET /api/v1/markets`;
* `GET /api/v1/markets/{marketId}`.

`MarketResponse` contains provider, symbol, assets, `MarketState`, and
`MarketConstraints`. `MarketState` includes trading status, tradability,
closure reason, and last update.

The existing contract is global. It does not, by itself, express account,
broker-account, currency, or profile-specific eligibility.

### Existing account-aware eligibility

Market Intelligence already exposes:

* `POST /api/v1/intelligence/scans/scope`.

The request contains `accountId`, optional objective, and requested market ids.
The response contains candidate markets, per-market eligibility decisions,
reasons, effective market ids, and resolution time.

The existing scope resolver is therefore strong evidence for the deterministic
eligibility boundary. However, its current public naming and request shape are
scan-specific. Implementation must verify whether its application service and
response can be reused by the Decision Workspace without making scan scope
resolution an accidental generic account-context authority.

### Existing frontend market behavior

Angular currently provides:

* `Markets` list and filtering;
* market detail route;
* ticker, OHLC, order-book, and recent-trades streams;
* global `MarketService.findAll()` and `findById()` calls;
* scan-panel market catalogue loading.

The market detail component opens subscriptions after loading a selected
market. Story 0056 must add the account prerequisite before the future
Workspace can select or subscribe to a market; it should not change the
existing standalone `/markets` exploration behavior unless explicitly scoped.

## Current Gaps

1. The global market catalogue is not sufficient as an account-scoped decision
   universe.
2. There is no dedicated account-first Decision Workspace context contract.
3. The Angular frontend has no state machine for account selection followed by
   account-dependent market resolution.
4. The frontend has no standardized representation of eligible versus excluded
   markets for this future flow.
5. The relationship between Active Scan scope resolution and generic Decision
   Workspace eligibility has not yet been formalized.

## Proposed Responsibility Boundary

* Trading Core remains authoritative for authenticated account ownership and
  canonical account context.
* Market Data remains authoritative for market catalogue and market state.
* Market Intelligence remains authoritative for account-aware Active Scan
  eligibility and intelligence-specific scope resolution.
* Angular consumes the resolved decision and never reproduces its rules.
* Risk Domain remains outside this Story and remains the authority for final
  risk evaluation.

The preferred implementation is to reuse the existing account-aware
eligibility application logic, while exposing a contract whose semantics are
explicit enough for account-scoped market availability. A new authority or
service should not be introduced unless repository evidence shows that the
existing boundary cannot support this use case.

## Implementation Boundary

The implementation should be limited to:

* backend contract/application changes required to resolve the selected account
  and eligible markets;
* ownership and deterministic eligibility tests;
* frontend account-first state and contract consumption;
* reset and error semantics for dependent market state;
* focused route/service wiring if required for the future Workspace entry.

It must not alter:

* global Market Data semantics;
* Risk rule evaluation;
* Trade Plan sizing or lifecycle;
* broker execution;
* existing Active Scan lifecycle behavior;
* standalone market exploration unless a regression fix is required.

## Risks and Open Questions

* Current account DTOs contain profile references but not necessarily all
  effective rule details. The Workspace should consume references/status, not
  invent a new frontend risk model.
* Current scope resolution distinguishes requested, candidate, and effective
  markets. The new contract must preserve this diagnostic distinction if it is
  reused.
* Account-specific broker or currency compatibility may not be fully modeled
  for every provider. Unknown compatibility must be reported as unavailable or
  unresolved, not guessed as eligible.
* The standalone `/markets` route currently has no account prerequisite. This
  Story should not silently convert it into an account-scoped route.

## Validation Expectations

* Trading Core ownership tests for selected account access.
* Market Intelligence scope/eligibility tests for eligible and excluded
  markets.
* Cross-account negative tests.
* Angular tests for account-first loading, reset, empty, error, and no-market
  states.
* Angular production build.
* `git diff --check`.
* Authenticated runtime validation with distinct account contexts when the
  environment provides them.

## Architectural Assessment

No new ADR is required if the implementation reuses the established Account,
Market Data, Market Intelligence, and Risk boundaries. An ADR must be raised
before implementation if the selected design introduces a new service as the
authority for account-market eligibility or merges Risk Profile and Trade
Planning Profile semantics.
