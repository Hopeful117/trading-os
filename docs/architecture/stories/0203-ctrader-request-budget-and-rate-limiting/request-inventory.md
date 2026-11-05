# cTrader Request Inventory

## Inventory

| Path | Trigger | Provider emissions | Retry/reconnect | Reuse | Owner | Necessity |
| --- | --- | ---: | --- | --- | --- | --- |
| Application/account discovery | Authentication | Session plus protocol setup | No automatic retry | None | Broker Service cTrader client | Required only during explicit binding |
| Account environment binding | Authentication | Session plus protocol setup | No automatic retry | None | Broker Service cTrader client | Required during binding |
| Authoritative account snapshot | Synchronization or account read | One session plus bounded setup/read requests | Persistent lease; no retry after refusal | Complete fresh snapshot reused for 30 seconds | Broker Service snapshot capability | Required for provider facts |
| Current quote | Margin or execution preparation | Session plus metadata, subscription and spot requests | No automatic retry | Not cached | Broker Service cTrader client | Required when caller needs current quote |
| Expected margin | LIVE preflight | Session plus metadata and margin requests | Margin lease; no retry after refusal | No incompatible value reuse | Broker Service margin capability | Required before eligible preflight |
| Order/position mutation | Explicit execution or close | Session plus mutation requests | No automatic replay | Never coalesced | Broker Service execution capability | Human-authorized only |

All protocol emissions pass through `Session.request(...)`. Session creation is
also reserved against the shared persistent budget. Angular and Trading Core
HTTP requests are not cTrader protocol requests.
