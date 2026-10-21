# V1 Functional Audit - Trading OS

- **Date:** 2026-10-18
- **Scope:** Broker account lifecycle, Trading Core/Broker Service callbacks, Angular Accounts workflows, News/XOOMAR, Market Intelligence pipeline, Gateway routing, fees, and security boundaries.
- **Constraint:** Read-only audit. No application source, configuration, runtime process, database, credential, or Market Data acquisition state was modified.
- **Repository state:** `main` at `c4081ac9`; worktree clean before the report was created. `git fetch origin` was unavailable because SSH authentication was refused.

## Executive Summary

The current V1 code is coherent in the audited decision path. Broker ownership and credential boundaries are preserved, the revoke flow is explicit and tested, News and XOOMAR contracts are provider-neutral, and the Market Intelligence execution-to-opportunity pipeline is present and covered by tests.

Two functional gaps or risks remain relevant:

1. Financial News is user-facing but operationally unavailable by default because every real financial-news provider is disabled. This is an intentional configuration state, not an implementation defect.
2. Broker revocation remains a distributed partial-failure risk when credential revocation succeeds but the Trading Core callback does not complete. This risk is explicitly documented by Story 0125 and is not solved by the current implementation.

The single recommended next Story is **0103B - Marketaux Financial News Provider**, but only after its written provider-authorization and data-use gate is resolved. It is the clearest remaining V1 capability gap that can add user-visible market context without changing deterministic risk or execution authority.

## Findings

### GAP-001 - Financial News Has No Active Production Provider

- **Category:** Functional capability gap
- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `news-service/src/main/resources/application.properties:17` defaults `NEWS_PROVIDER_ENABLED` to `false`.
  - `news-service/src/main/resources/application.properties:30` defaults Alpha Vantage to disabled.
  - `news-service/src/main/resources/application.properties:34` defaults Alpha Vantage polling to disabled.
  - `docs/architecture/stories/0104-user-facing-financial-news-feed/implementation-report.md:33-35` records that Alpha Vantage remains disabled and Marketaux was not implemented.
  - Story 0103 reports automated implementation complete but live validation blocked by provider quota/API-key availability.
- **Impact:** The Angular Financial News page exists, but a normal environment returns an unavailable/empty provider state rather than provider-backed financial-news facts. Economic Context through XOOMAR is separate and remains available when configured.
- **Recommendation:** Resolve the provider authorization decision and implement the bounded provider path. Do not enable a provider, add credentials, or poll continuously without the documented provider gate.

### GAP-002 - Broker Revocation Has No Durable Reconciliation State

- **Category:** Distributed reliability risk
- **Severity:** High
- **Confidence:** High
- **Evidence:**
  - `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionService.java:148-157` revokes the secret, changes the local Broker Service connection, then performs the Trading Core callback in one command path.
  - `docs/architecture/stories/0125-revoke-broker-account/story.md:77-81` explicitly defines the callback failure as a partial-failure case.
  - `docs/architecture/stories/0125-revoke-broker-account/code-review-report.md:34-36` records the remaining reconciliation risk.
  - `broker-service/src/test/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionServiceTest.java:160-177` verifies callback failure in an in-memory service test, but not a durable cross-service reconciliation workflow.
- **Impact:** A lost or failed callback can leave Trading Core stale relative to Broker Service credential state. The UI reports that revocation could not be confirmed, but there is no durable reconciliation marker or operator-facing recovery workflow.
- **Classification:** Known residual risk, not a regression introduced by the current audit scope. Story 0125 deliberately did not authorize a new reconciliation subsystem.
- **Recommendation:** Track as a future reliability Story before treating revocation as strongly recoverable in production. Do not solve it by physical deletion or by silently treating the operation as successful.

### GAP-003 - Broker Recovery Actions Are Not Exposed in Accounts UI

- **Category:** User workflow gap
- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `trading-os-web/src/app/core/services/broker-account.service.ts:67-79` exposes `rotate` and `disconnect`.
  - `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html:123-141` exposes only status and revoke for listed broker accounts.
  - `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts:73-140` creates new LIVE connections but has no rotate, reconnect, or disconnect action.
- **Impact:** A user whose existing account is disconnected, requires reauthentication, or has invalid/expired credentials has no corrective action in the Accounts page. The fallback is to create another broker account, which can leave stale local account records.
- **Classification:** Follow-up product gap, not a defect in Story 0125: that Story explicitly excluded reconnecting or un-revoking an account.
- **Recommendation:** Create a separate approved workflow Story for credential rotation, reconnect, and disconnect/recovery. Keep it separate from logical revoke semantics.

## Verified Areas

- **Broker ownership:** Trading Core validates the authenticated owner before delegating credential commands; Broker Service validates its connection owner.
- **Revoke behavior:** Explicit confirmation, duplicate-request protection, safe UI feedback, local fallback for an unconnected `CREATED` account, and refresh after success are implemented and tested.
- **Gateway:** Broker account, trade-plan, opportunity, market, and News routes are explicitly separated and routed to their owning services.
- **News/XOOMAR:** Provider DTOs remain inside News Service infrastructure; source identity is deterministic; UTC parsing, bounded windows, attribution, and unavailable states are covered.
- **Market Intelligence:** Current code contains durable analysis execution, production pipeline processing, opportunity projection, and account-scoped trade-plan continuation. The historical Story 0002 blocker is not sufficient evidence of a current defect because later repository code implements the missing pipeline pieces.
- **Security boundary:** No evidence was found in the audited files of Angular receiving broker credentials, provider payload leakage, or Trading Core owning credential persistence.
- **Fees/funding:** The current forward-only fee evidence and provider-blocked funding behavior are documented constraints, not reclassified as defects by this audit.

## Recommended Next Story

### Story 0103B - Marketaux Financial News Provider

Proceed only after the existing decision gate is satisfied:

- written authorization for automated polling and normalized persistence;
- approved single-active-provider selection;
- bounded rate-limit and retention policy;
- no live token or raw provider payload in repository artifacts.

This Story is recommended over enabling an undocumented provider because it already defines the provider-neutral boundary, safe activation constraints, deterministic identity, and failure semantics required for the existing Financial News UI and Market Intelligence context.

## Validation

- `cd broker-service && mvn -B test`: **238 tests passed**.
- `cd news-service && mvn -B test`: **56 tests passed**.
- `cd market-intelligence && mvn -B test`: **681 tests passed**.
- `git diff --check`: passed before this report was added.
- No production runtime restart, provider request, database mutation, credential operation, or Market Data acquisition restart was performed.

## Limitations

- A fresh remote comparison was not possible because `git fetch origin` was rejected by SSH authentication.
- DevLog applicability was attempted, but the Story Agent was unavailable because the configured LLM provider returned HTTP 429 for exhausted quota.
- No live provider validation was performed for Alpha Vantage, Marketaux, or XOOMAR during this audit.
- Sonar Quality Gate evidence was not re-run; existing Story reports record environment/configuration limitations.
