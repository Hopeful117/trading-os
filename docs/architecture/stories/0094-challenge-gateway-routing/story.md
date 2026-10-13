# Story 0094 - Challenge Gateway Routing

## Metadata

- **ID:** `0094`
- **Status:** `ANALYSIS COMPLETE - AWAITING HUMAN APPROVAL`
- **Follows:** `0093`
- **Repository Analysis:** `repository-analysis.md`
- **Validation Evidence:** `../0093-postgresql-fresh-install-migration-integrity/e2e-validation-report.md`

## Goal

Expose the existing authenticated Trading Core Challenge API through the public
Gateway so Angular and other clients can use the supported boundary:

```text
Client / Angular -> API Gateway -> Trading Core
```

## Scope

- Route `GET /api/v1/challenges` to Trading Core.
- Route `POST /api/v1/accounts/{accountId}/challenge` to Trading Core.
- Route `GET /api/v1/accounts/{accountId}/challenge` to Trading Core.
- Add Gateway regression coverage for the Challenge route families.
- Validate the three requests through the public Gateway with authentication.
- Document route precedence and security behavior.

## Out Of Scope

- Challenge domain or lifecycle behavior.
- Risk rules or Risk Day reset-boundary behavior.
- Monitoring scheduler fixes.
- Funding, leverage, margin, liquidation, or settlement changes.
- Market Data, Angular redesign, WebSockets, or trading strategies.
- Provider-specific business logic.

## Constraints

- Trading Core remains the owner of Challenge behavior and account ownership
  authorization.
- Gateway must preserve JWT authentication and bearer propagation.
- No direct Angular-to-Trading-Core calls.
- No path rewrite or duplicate Challenge business logic in Gateway.
- Do not expose `/internal/**` or internal Challenge endpoints.
- Follow existing `GatewayRouteConfig` service-discovery conventions.

## Acceptance Criteria

- `GET /api/v1/challenges` returns the Trading Core catalog through Gateway.
- Challenge start succeeds through Gateway for a compatible PAPER account.
- Challenge progress is returned through Gateway.
- Unauthenticated Challenge requests remain rejected.
- Authenticated owner identity remains enforced by Trading Core.
- No internal Challenge endpoint is routed publicly.
- Gateway regression tests cover catalogue, start, progress, and route scope.
- The previously observed Gateway `404` is reproduced by Story 0093 evidence
  and is resolved by the implementation.
- No unrelated Challenge, Risk, or trading behavior changes.

## Validation Expectations

- Run Gateway route and downstream routing tests.
- Run the strongest applicable Gateway module validation.
- Run `git diff --check`.
- Validate login, catalog, Challenge start, and progress through Gateway.
- Preserve the scheduler SQL typing error as a separate observation.

## Required Documentation

- Repository analysis.
- Implementation plan after Repository Analysis approval.
- Implementation and validation report.
- Code review and engineering report according to the Engineering Story
  workflow.
