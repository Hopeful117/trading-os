# Story 0102 - Runtime Validation Report

## Compose

The existing Compose runtime was healthy before validation. The affected
`news-service`, `gateway`, and `trading-web` services were rebuilt and restarted
without changing unrelated service data.

Verified running:

- `news-postgres` / `news-db`: running.
- `news-service`: running on port `17086`.
- `gateway`: running on port `17080`.
- `trading-web`: running on port `17085`.

`docker compose config -q` passed. News Service automated startup tests passed,
but an independent live PostgreSQL/Flyway migration assertion was not captured
from container logs in this run.

## Runtime API

Request attempted through Gateway:

```text
GET http://localhost:17080/api/v1/news/events?from=2026-10-15T00:00:00Z&to=2026-10-22T23:59:59Z&limit=100
```

Unauthenticated request response:

```text
HTTP/1.1 401 Unauthorized
```

This confirmed the route is protected by the existing authentication boundary.

After authenticating in the browser, the frontend issued these requests through
the Gateway and received `HTTP 200 OK` responses:

```text
GET /api/v1/news/events?...&limit=100
GET /api/v1/news/events?...&currency=USD&limit=100
GET /api/v1/news/events?...&currency=USD&impact=HIGH&limit=100
```

The current normalized response was provider-unavailable with the message
`No production news provider is configured`. The UI displayed that degraded
state rather than presenting an indistinguishable empty calendar.

## Runtime Frontend

Request attempted:

```text
GET http://localhost:17085/economic-context
```

The rebuilt frontend served successfully with `HTTP 200`. The authenticated
browser flow opened the Economic Context page, confirmed the sidebar link,
displayed the calendar controls and the provider-unavailable state, then
reloaded the API when currency and importance filters were changed.

The provider-unavailable state was validated in the real UI. A live event-card
rendering could not be validated because the current News Service catalog has no
configured production provider and returned no available events. Event-card
rendering remains covered by frontend tests using representative normalized
responses.

## Provider Validation

Live XOOMAR validation: **NOT TESTED**. No provider credentials or live request
were introduced.
