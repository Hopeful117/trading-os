# Broker Internal Endpoint Authorization Matrix

This matrix describes the current Broker Service `/internal/v1/**` controller mappings for Story0044.

| Method | Path | Trust class | Authorized callers | Actor required | Resource ownership | Ownership authority | System call allowed | Current consumer | Test coverage |
|---|---|---|---|---|---|---|---|---|---|
| POST | `/internal/v1/executions` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via execution service | No | Trading Core execution | `BrokerApiSecurityIntegrationTest`, `BrokerOwnershipTest` |
| POST | `/internal/v1/executions/reconcile` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via reconciliation service | No | Trading Core execution recovery | `BrokerOwnershipTest` |
| POST | `/internal/v1/executions/{externalOrderId}/cancel` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via cancellation service | No | Trading Core execution recovery | `BrokerOwnershipTest` |
| POST | `/internal/v1/positions/resolve-target` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via position service | No | Trading Core position close | `BrokerApiSecurityIntegrationTest`, `BrokerOwnershipTest` |
| POST | `/internal/v1/positions/execute-close` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via position service | No | Trading Core position close | `BrokerOwnershipTest` |
| POST | `/internal/v1/positions/reconcile-close` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via position service | No | Trading Core position close recovery | `BrokerOwnershipTest` |
| GET | `/internal/v1/broker-accounts/{id}/risk-snapshot` | SERVICE_WITH_DELEGATED_ACTOR | `trading-core` | Yes | Yes | `BrokerAccount.ownerId` via risk snapshot service | No | Trading Core risk facts | `BrokerApiSecurityIntegrationTest` |
| GET | `/internal/v1/broker-accounts/{id}` | SERVICE_ONLY | `trading-core` | No | No additional actor check in controller/service | Broker provider account authority | Yes | No current Core Feign client in this path | Security chain coverage |
| GET | `/internal/v1/broker-accounts/{id}/positions` | SERVICE_ONLY | `trading-core` | No | No additional actor check in controller/service | Broker provider position authority | Yes | No current Core Feign client in this path | Security chain coverage |
| GET | `/internal/v1/broker-accounts/{id}/orders` | SERVICE_ONLY | `trading-core` | No | No additional actor check in controller/service | Broker provider order authority | Yes | No current Core Feign client in this path | Security chain coverage |
| GET | `/internal/v1/broker-operations/metrics` | SERVICE_ONLY | `trading-core` | No | No | Broker operational authority | Yes | No current Core Feign client in this path | Security chain coverage |

## Enforcement

`ServiceJwtAuthenticationFilter` validates the service JWT before endpoint dispatch. Broker accepts only callers listed in `security.service-jwt.authorized-callers`; production currently lists `trading-core` only. Actor-required controllers reject a missing delegated actor, then application services perform resource ownership checks. `X-Actor-Id` is not an authority source.

User-facing `/api/**` endpoints are outside this internal service matrix and retain their existing user authentication path.
