# Repository Analysis

## Repository State

- Current branch: `main`
- Working tree: clean
- `HEAD`: `408b3a3`
- Story 0091 is merged into `main` and its Challenge API is the current
  backend contract.
- DevLog context retrieval was attempted and failed because the API has no
  remaining credits. Analysis therefore continues from current repository
  evidence and canonical Story 0091 artifacts.
- Obsidian was not consulted; the repository and Story 0091 contracts provide
  the relevant current project context.

## Exact Story 0091 Backend Contracts

### Catalog

`GET /api/v1/challenges`

Returns `List<ChallengeDefinitionResponse>`:

- `id: UUID`
- `definitionVersion: string`
- `provider: string`
- `product: string`
- `planCode: string`
- `capitalCurrency: string`
- `startingCapital: BigDecimal`
- `profitTargetRatio: BigDecimal`
- `progressionValueSource: BALANCE | EQUITY`
- `requiredSettlementModel: SettlementModel | null`
- `requiredInstrumentProductType: InstrumentProductType | null`
- `riskPolicyId: UUID`
- `riskPolicyVersion: string`
- `sourceUrl: string`
- `retrievedAt: ISO instant`
- `effectiveFrom: ISO instant`
- `provenance: string`

The catalog returns one current definition per logical ID. The frontend must
retain both `id` and `definitionVersion` for start requests.

### Start

`POST /api/v1/accounts/{accountId}/challenge`

Request body:

```json
{
  "definitionId": "uuid",
  "definitionVersion": "1.0.0"
}
```

The authenticated owner is taken from the JWT principal by Trading Core. The
response is `ChallengeInstanceResponse`:

- `id`
- `accountId`
- `definitionId`
- `definitionVersion`
- `riskPolicyId`
- `riskPolicyVersion`
- `startingCapital`
- `capitalCurrency`
- `startedAt`
- `status: ACTIVE | PASSED | BREACHED`
- `passedAt`
- `breachedAt`
- `terminalReason`
- `terminalRiskEvaluationId`

### Progress

`GET /api/v1/accounts/{accountId}/challenge`

Returns the derived `ChallengeProgress` projection:

- `challengeId`
- `accountId`
- `status: ACTIVE | PASSED | BREACHED`
- `startingCapital`
- `currentValue`
- `targetValue`
- `currentProfit`
- `targetProgress`
- `dailyReferenceBalance`
- `dailyLossRemaining`
- `totalDrawdownRemaining`
- `evaluatedAt`

The current `ChallengeProgressService` sets the daily reference/loss/drawdown
fields to `null`; Angular must render them as unavailable and must not recreate
Risk calculations.

## Backend Error Semantics

Current Challenge advice maps:

- `ChallengeNotFoundException` and `ChallengeOwnershipException` to `404`
  with detail `Challenge resource was not found`;
- `ChallengeConflictException` to `409`;
- `ChallengeValidationException` to `400`;
- malformed/invalid request bodies and path conversions to `400`;
- `ChallengeConfigurationException` to `500` with detail
  `Challenge configuration is unavailable`.

The progress service throws `ChallengeNotFoundException("Account has no
Challenge")` when no instance exists, but the advice intentionally replaces
the detail with the same generic `404` detail used for missing accounts and
ownership failures. Therefore the frontend cannot safely classify every `404`
as `no Challenge` under the Story's explicit error rule.

Required provider-neutral adjustment before frontend implementation:

- introduce a stable ProblemDetail property such as
  `code: CHALLENGE_NOT_STARTED` only for the no-Challenge projection case;
- preserve the generic `404` response for missing/inaccessible accounts and
  definitions;
- keep `401/403`, `409`, `500`, timeout, and network failures as errors.

This is a public error-contract clarification, not a Challenge/Risk behavior
change. It should be implemented and tested only after the Repository Analysis
and Implementation Plan gates are approved.

## Angular Architecture

The application is a standalone Angular application using:

- Angular 21;
- strict TypeScript and strict Angular templates;
- standalone components;
- `provideRouter(routes)`;
- `provideHttpClient(withInterceptors([authInterceptor]))`;
- RxJS Observables with `AsyncPipe` in existing Dashboard flows;
- Vitest through Angular's unit-test builder;
- SCSS component styles with dark slate trading-dashboard conventions.

The existing Dashboard establishes the relevant reactive pattern:

- `BehaviorSubject` for selected account;
- account loading state with `startWith`, `catchError`, and `shareReplay`;
- `combineLatest` for view-model composition;
- `timer(0, 5_000)` plus `switchMap` for polling;
- `switchMap` cancels the previous request and avoids overlap;
- `AsyncPipe` renders the observable view model.

The Challenge feature should use the same shape without embedding its state in
the general Dashboard.

## Existing Account and Authentication Infrastructure

`AccountService.getAccounts()` calls `GET /api/v1/accounts` through the
gateway. `Account` exposes `accountId`, `name`, `baseCurrency`, balances,
equity, and ownership-related display fields. The Challenge page must use
these existing accounts and never call Broker Account creation APIs.

The authenticated route convention is `canActivate: [authGuard]`. The HTTP
interceptor adds the bearer token and redirects `401/403` to `/error`; the
Challenge page should preserve this behavior and only handle local
start/progress errors that reach the component.

## Existing Navigation and Route Conventions

Authenticated routes are declared directly in `app.routes.ts` and import
standalone page components. The sidebar is a simple `RouterLink` list using
the established labels and styling. A single `Challenges` link and a
`/challenges` route fit the existing conventions without a navigation redesign.

## Existing Progress Sufficiency

The existing projection is sufficient for the first dashboard without backend
Risk calculation changes:

- identity and status are available from progress and selected catalog data;
- current value, target, profit, and backend progress ratio are available;
- daily and total drawdown safety fields are modeled but currently unavailable,
  so the UI must show an explicit unavailable state;
- start response supplies start and terminal metadata for the immediate
  post-start view;
- after a reload, progress does not supply provider/name or terminal reason,
  so the page should join progress with the selected catalog where possible and
  avoid inventing missing terminal metadata.

No Risk projection enhancement is required for the initial implementation.
The error-contract clarification above is required for safe absence handling.

## Proposed Feature Shape

```text
core/models/challenge.model.ts
core/services/challenge.service.ts
core/services/challenge.service.spec.ts
features/challenges/pages/challenge-dashboard/
  challenge-dashboard.ts
  challenge-dashboard.html
  challenge-dashboard.scss
  challenge-dashboard.spec.ts
```

The page view model should explicitly represent:

- accounts loading/error/empty;
- catalog loading/error/empty;
- selected account;
- progress loading/error/noChallenge/available;
- start in-flight/error;
- active or terminal Challenge projection.

The service should expose typed `findCatalog`, `startChallenge`, and
`findProgress` methods and propagate `HttpErrorResponse` values so the page can
distinguish `CHALLENGE_NOT_STARTED` from other failures.

## Relevant Validation

Frontend validation is configured through:

- `npm run test:ci` for non-watch tests;
- `npm run build` for the production build;
- `npm run check` for both.

Angular unit coverage has an 80% line threshold. Existing service tests use
`HttpClientTestingModule` and `HttpTestingController`; existing component tests
use standalone component imports, mocked services, Vitest spies, and Observable
fixtures.

## Risks and Open Decisions

- The backend `404` ambiguity is a real contract gap and must be resolved before
  safely implementing `no Challenge` UI state.
- The current projection's risk margin fields are null; displaying them as
  unavailable is correct and preserves backend authority.
- The 5-second polling interval is an existing convention, not yet a Challenge
  requirement; the plan should centralize it as a page/service constant and
  stop or reduce polling after terminal states.
- The Story does not require changing the general Dashboard; navigation is the
  only optional integration point.
