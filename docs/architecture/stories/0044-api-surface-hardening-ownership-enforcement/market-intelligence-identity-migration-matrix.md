# Story 0044 - Market Intelligence Identity Migration Matrix

Legacy identity fields remain transport compatibility concerns only. None is
an independent authentication or authorization authority.

| Legacy input | Current producer | Current consumers | Target source | Migration treatment | Conflict policy |
|---|---|---|---|---|---|
| Client `X-Actor-Id` | External caller; Gateway currently overwrites it for `/api/v1/intelligence/**` | MI scan controller | MI-local authenticated user principal | `REMOVE_FROM_TRANSPORT` as authority; compatibility header may exist temporarily | Strip client value; reject mismatch rather than silently selecting one |
| Gateway-derived `X-Actor-Id` | `AuthenticatedActorHeaderFilter` from validated Gateway principal | MI scan controller | MI-local authenticated user principal | `KEEP_TEMPORARILY_BUT_NON_AUTHORITATIVE` | Compare during migration; mismatch is rejected |
| Body `actorId` on legacy `/trade-plans` | Legacy caller | `TradePlanController` | Signed delegated actor or local principal | `REMOVE_FROM_TRANSPORT` as authority | Body value never overrides trusted context |
| Body `actorId` on internal generation | Trading Core Feign | Internal analysis/opportunity generation | Signed delegated actor claim in Core service JWT | `COMPARE_WITH_AUTHENTICATED_ACTOR` during migration, then remove from DTO | Conflict is rejected |
| Body `actorId` on internal decision | Trading Core Feign | Internal decision controller | Signed delegated actor claim | `COMPARE_WITH_AUTHENTICATED_ACTOR` during migration, then remove | Conflict is rejected |
| Query `actorId` on internal plan load | Trading Core Feign | Internal decision controller | Signed delegated actor claim | `REMOVE_FROM_TRANSPORT` as authority | Conflict or absent delegation is rejected |
| `accountId` in scan/generation requests | Angular or Trading Core | Scope/generation services | Authenticated user target plus owner lookup in authoritative service | `KEEP_AS_BUSINESS_DATA` | Account must be owned by trusted actor; mismatch is forbidden |
| `ownerId` in internal planning context | Trading Core Feign | Generation services | Server-validated context/resource data | `KEEP_AS_BUSINESS_DATA` | Must match delegated actor and account relationship |
| Request-context `Authorization` forwarded by MI Feign | `FeignAuthorizationConfiguration` | MI→Core and MI→Market Data | Receiver-specific service JWT | `REMOVE_FROM_TRANSPORT` as service identity | Never treat copied client bearer as MI service identity |
| Scheduled/background execution | MI drivers/pipeline | Expiration and background work | Service principal or explicit `SYSTEM` | `NOT_APPLICABLE` to user actor | No fake user UUID; user-owned work without delegation is rejected |
| Path user identifiers | No confirmed MI path currently uses one | None confirmed | Authenticated principal/delegation | `NOT_APPLICABLE` | Any future path identifier is a resource target, never caller identity |

## Rules

- `X-Actor-Id`, body `actorId`, and query `actorId` have no standalone target
  authority.
- `TradingOpportunity` does not need a user actor for ownership because it is
  shared intelligence; authenticated access is still required by the selected
  `AUTHENTICATED_SHARED` exposure policy.
- `ActiveScan`, `TradePlan`, and plan decisions require trusted user identity
  and resource ownership checks.
- Core→MI user-driven planning calls require `service:trading-core` identity,
  audience `market-intelligence`, and a signed delegated actor.
- Core→MI risk handoff calls currently require service identity and exact
  plan/version authorization, but no delegated actor.
- MI background work may use a service principal or `SYSTEM`; it must never
  manufacture a user actor to satisfy a user-owned API.
