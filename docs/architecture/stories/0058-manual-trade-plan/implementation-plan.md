# Implementation Plan - Story 0058

## Plan Status

Implementation completed. Human code review remains pending.

## Step 1 - Extend the Trade Plan Domain

Add an explicit `TradePlanOrigin` and authenticated author reference.

Preserve these invariants:

* `OPPORTUNITY` plans require Opportunity and Observation provenance;
* `MANUAL` plans do not reference an Opportunity;
* manual plans require an authenticated author;
* both origins use the same immutable identity, version and status lifecycle.

## Step 2 - Add the Manual Planning Path

Introduce a manual application request carrying explicit:

* planning context;
* instrument and direction;
* supported entry parameters;
* stop and target parameters;
* quantity, notional and expected monetary risk;
* expiration and rationale fields.

Build the existing execution value objects directly. Do not reuse Opportunity
policies with an empty Opportunity list and do not add an automatic sizing
algorithm in this Story.

## Step 3 - Add the Authenticated API Boundary

Expose an authenticated manual creation endpoint under the existing intelligence
API namespace.

Rules:

* actor identity comes from the authenticated principal;
* planning-context ownership is validated server-side;
* the endpoint returns a proposed Trade Plan;
* the endpoint creates no Execution Intent and calls no broker.

## Step 4 - Extend Persistence Compatibly

Add a new Flyway migration for origin and author.

Existing rows are backfilled as `OPPORTUNITY`. Legacy opportunity authors remain
nullable because the historical data does not contain a trustworthy author.

Update JPA and in-memory mapping while preserving version append-only behavior.

## Step 5 - Add Regression Coverage

Cover:

* manual creation without Opportunity;
* manual author attribution;
* rejected unauthorized context;
* rejected opportunity plan without provenance;
* rejected manual plan with Opportunity provenance;
* response origin and author;
* persistence mapping round-trip;
* existing opportunity-origin planning and risk handoff.

## Step 6 - Validate

Run:

```text
mvn test                         # market-intelligence
mvn test -DskipTests             # trading-core compatibility compilation
git diff --check
```

Runtime Gateway authentication, Risk Evaluation and PAPER execution remain
follow-up validation for subsequent Stories.

## Explicit Non-Goals

Do not implement:

* direct-trading frontend form;
* a second execution path;
* risk-policy changes;
* broker execution;
* Execution Intent creation from manual input;
* position management.
