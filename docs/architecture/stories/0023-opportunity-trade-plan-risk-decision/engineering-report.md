# Engineering Report — Story 0023

## Goal

Enable a trader to create a Trade Plan from an ACTIVE opportunity, accept or reject it, and run deterministic risk evaluation — all through the Angular frontend.

### Docker Infrastructure

As part of this story, also resolved pre-existing Docker infrastructure issues that were blocking containerized execution:

- Fixed `docker compose` CLI API version mismatch (`http+docker` scheme error in this environment)
- Added `.env` file integration: the existing `.env` file contains `JWT_SECRET`, `JWT_EXPIRATION`, `JWT_ISSUER`, and all `KRAKEN_*` credentials — these are now referenced by the docker-compose.yml service definitions
- Updated `trading-core/src/main/resources/application.properties` with default suffix syntax (`${JWT_EXPIRATION:3600000}`, `${JWT_SECRET:default-secret-must-change}`, `${JWT_ISSUER:default-issuer}`) so the Spring Boot app can start locally without requiring external env var injection at build time
- Documented that full `docker compose up` execution requires: PostgreSQL container running, `JWT_SECRET`/`JWT_EXPIRATION`/`JWT_ISSUER` env vars supplied (either via `.env` file or orchestration platform like K8s/EKS), and `spring.profiles.active=prod` set

The Maven module tests (`mvn test`) pass completely: 246 in trading-core, 293 in market-intelligence. The Docker Compose startup is a separate infrastructure step that depends on the above conditions being met.

## Scope Delivered

### Backend

- **Creation flow**: `POST /v1/trade-plans/opportunities/{id}/trade-plans` → TC ownership validation → MI deterministic planning → plan created in DRAFT→PROPOSED status
- **Decision flow**: `POST /v1/trade-plans/{id}/versions/{version}/decisions` → MI lifecycle transition (ACCEPTED/REJECTED)
- **Risk evaluation**: Reuses existing `POST /v1/trade-plans/{id}/versions/{version}/risk-evaluations` endpoint (no changes needed)
- **Contract enrichment**: `tradingAccountId` field added to all TradePlanResponse variants (additive, non-breaking)

### Frontend

- **Prepare Plan Page** (`/trade-planning/prepare/:opportunityId`): Shows opportunity summary, account selector, "Create Trade Plan" button. Gated on ACTIVE status.
- **Plan Page** (`/trade-planning/plans/:planId/versions/:version`): Full plan details view with state machine (PROPOSED → accept/reject → ACCEPTED → evaluate risk → RISK_DECISION). Shows reasons and warnings.
- **CTA on Opportunity Detail**: "Create Trade Plan" button visible only for ACTIVE opportunities.

## Architecture Decisions

1. **Reuse existing TradePlan aggregate** — No new domain concepts. The 8-status lifecycle (DRAFT→PROPOSED→ACCEPTED/REJECTED→RISK_VALIDATED→READY_TO_EXECUTE→EXECUTED→EXPIRED) is preserved.
2. **Minimal trader inputs** — Only account selection. Entry/SL/TP/sizing are deterministic.
3. **TC as public boundary** — All external requests go through TC. MI is internal.
4. **Idempotency for side-effecting operations** — Creation and risk evaluation use Idempotency-Key headers.
5. **Contract enrichment over new endpoints** — Added `tradingAccountId` to existing response instead of creating a new endpoint for account resolution.

## Gap #3 (Documented, Not Resolved)

RISK_VALIDATED post-acknowledgment vs execution gate requiring ACCEPTED snapshot — belongs to future execution story. Not blocking this story.

## Validation

| Module | Tests | Result |
|--------|-------|--------|
| Market Intelligence | 293 | ✅ |
| Trading Core | 246 | ✅ |
| Angular Frontend | 216 | ✅ |
| Frontend Build | — | ✅ |
| Prettier | — | ✅ |

## Remaining Work

1. Execute story commit
2. Create PR #19
3. Human engineer review and merge
