# Repository Analysis - Story 0058

## Scope

Story 0058 adds a truthful manual origin to the existing Trade Plan model. The
manual path must share deterministic risk, human validation, Execution Intent,
idempotency and reconciliation behavior with opportunity-driven plans.

The Story does not implement a complete direct-trading UI or broker execution
journey.

## Current Repository Evidence

### Trade Plan domain

`market-intelligence` owns the Trade Plan aggregate and lifecycle. The current
aggregate contains:

* immutable id and version lineage;
* proposal status;
* planning-context reference;
* execution parameters;
* rationale with Opportunity and Observation provenance;
* creation timestamp.

The current `TradingRationale` constructor requires at least one Opportunity and
one Observation. That invariant is valid for opportunity-origin plans but blocks
manual provenance.

### Planning and application flow

`TradePlanningEngine` currently:

* loads and validates active Opportunities;
* loads and authorizes the Trade Planning Context;
* applies deterministic planning policies and optional AI contributions;
* builds and persists a proposed Trade Plan.

The existing planning request is Opportunity-oriented and rejects an empty
Opportunity set. A manual request therefore requires a separate application
input and builder path rather than weakening the Opportunity pipeline.

### Persistence

Trade Plans are persisted in `trade_plan_versions` through JPA. Existing rows do
not contain an explicit origin or author. A new Flyway migration must:

* backfill existing rows to `OPPORTUNITY`;
* add an optional legacy author column;
* constrain origin values to the accepted enum.

Manual plans must persist an authenticated author. Historical opportunity plans
may retain a null author until a separate backfill policy is approved.

### API and security

Existing Trade Plan generation is used by internal Trading Core orchestration.
The existing public Trade Plan DTO accepts a client actor id and is not suitable
as the authority for manual authorship.

The manual creation endpoint must derive the actor from `MiUserPrincipal`,
validate the planning-context ownership server-side, and create only a proposed
Trade Plan.

### Downstream risk and execution

Trading Core risk evaluation consumes the normalized Trade Plan execution and
planning context. It does not require Opportunity provenance. Execution Intent
creation is downstream of approved Risk Evaluation and must remain unchanged.

## Responsibility Boundary

* Market Intelligence owns Trade Plan origin, identity, version and lifecycle.
* Trading Core owns account authorization, deterministic risk evaluation and
  Execution Intent creation.
* Risk Domain remains the authority for deterministic risk decisions.
* Trading OS Web expresses input and approval intent but is not authoritative.
* Broker Service remains outside manual Trade Plan creation.

## Implementation Boundary

Included:

* `TradePlanOrigin` domain value;
* manual application request and creation path;
* origin/author persistence and migration;
* authenticated manual creation contract;
* response provenance;
* domain, application, API and persistence regression tests.

Excluded:

* direct-trading frontend form;
* Risk rule changes;
* Execution Intent changes;
* broker calls;
* position management;
* partial close, SL/TP and pending-order capabilities.

## Risks and Open Questions

* Manual plans require explicit execution and sizing inputs; this Story must not
  silently introduce a new sizing algorithm.
* Existing opportunity-origin plans must remain valid after the migration.
* The manual endpoint must not accept a client actor id as authorization.
* Runtime Gateway authentication and production database migration require
  separate validation.

## Architectural Assessment

ADR-047 already establishes the required architecture. No additional ADR is
required unless implementation introduces a new Trade Plan owner, a second
execution pipeline or a different risk authority.
