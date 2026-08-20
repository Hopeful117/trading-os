# Implementation Plan — Story 0005

## Objective

Implement account-aware active scan scope resolution as a deterministic orchestration slice in `market-intelligence`.

The plan intentionally stops before:

- `ActiveScan` persistence;
- multi-market `AnalysisExecution` orchestration;
- cross-market ranking;
- AI relevance.

## Architecture Decision

Story 0005 will be implemented as a small orchestration feature in `market-intelligence` that composes authoritative reads from:

- `trading-core` for account / broker ownership and usable account context;
- `market-data` for candidate markets and tradability.

The Story should produce an explicit effective scan scope and deterministic eligibility diagnostics.

## Step 1 — Add Scope-Resolution Domain Objects

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.domain` or a small adjacent `scope` package if the current package layout needs separation

### New Concepts

- `ActiveScanScopeResolutionRequest`
- `ActiveScanScopeResolutionResult`
- `EffectiveScanScope`
- `MarketEligibilityDecision`
- `MarketEligibilityReason`

### Responsibility

Represent the request and deterministic output of scope resolution without persisting anything.

### Inputs

- authenticated user identity
- selected account id
- selected broker-account id if required by the chosen design
- user objective
- optional requested market ids

### Outputs

- requested scope
- candidate markets
- per-market eligibility decisions
- effective market ids
- exclusion diagnostics

### Invariants

- deterministic output for the same inputs;
- duplicate requested markets collapse to one decision per market;
- no ranking claim;
- no persistence.

### Tests

- pure unit tests for deduplication and output structure;
- deterministic ordering assertions.

## Step 2 — Add Authoritative Read Clients for Trading Context

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.adapter.tradingcore` or equivalent adapter package

### New / Modified Components

- read-only Feign client for account lookup;
- read-only Feign client for broker-account lookup if required by the final request contract;
- auth-propagating Feign configuration if current `market-intelligence` requests need bearer-token forwarding.

### Responsibility

Load account and broker-account facts from the authoritative owner service without duplicating state locally.

### Inputs

- user-authenticated request context
- account id
- broker-account id if used

### Outputs

- account ownership / usability facts
- broker-account ownership / connection facts if used

### MUST_REUSE

- existing `@EnableFeignClients` setup in `MarketIntelligenceApplication`
- existing gateway route exposure

### MUST_NOT_DUPLICATE

- account persistence
- broker-account persistence
- risk evaluation logic

### Tests

- client contract tests or controller tests with mocked responses;
- ownership rejection tests.

## Step 3 — Add Candidate Universe Resolution

### Module

- `market-intelligence`

### Existing Component to Reuse

- `MarketDataClient`
- `MarketController` / market-data catalog API

### Responsibility

Resolve the candidate universe from the current market catalog and optional requested market ids.

### Inputs

- requested market ids if provided
- market catalog from market-data

### Outputs

- candidate market ids
- duplicate-free normalized set

### Invariants

- market-data remains the source of truth for the universe;
- no persisted universe model is introduced;
- markets absent from the catalog are rejected or excluded deterministically.

### Tests

- deduplication;
- missing market handling;
- empty requested scope handling;
- deterministic ordering.

## Step 4 — Implement Deterministic Hard Eligibility

### Module

- `market-intelligence`

### Existing Facts to Reuse

- `MarketState.tradable`
- account/broker ownership facts

### Responsibility

Apply only the hard eligibility checks that the repository already supports.

### Included Rules

- account ownership;
- broker-account ownership if used;
- market exists;
- market is tradable;
- request is not empty after normalization.

### Deferred Rules

- account balance;
- position concentration;
- daily loss;
- leverage;
- prop-firm limits;
- contextual relevance;
- AI relevance.

### Tests

- tradable market accepted;
- non-tradable market excluded;
- unknown market excluded;
- unauthorized account rejected;
- unauthorized broker account rejected if used.

## Step 5 — Expose a Read-Only Scope-Resolution Endpoint

### Module

- `market-intelligence`

### Existing Component to Extend

- `MarketIntelligenceController`

### Responsibility

Expose the scope-resolution result through a small endpoint so the feature can be validated independently of future `ActiveScan` orchestration.

### Proposed Shape

- `POST /api/v1/intelligence/scans/scope`

### Request

- account id
- broker-account id if used
- objective
- optional requested market ids

### Response

- requested scope
- candidate markets
- eligibility decisions
- effective scope
- diagnostics

### Security

- authentication required;
- account ownership verified;
- broker-account ownership verified if part of the request;
- no scan result may be returned for another user's account.

### Tests

- controller test for happy path;
- controller test for unauthorized ownership;
- controller test for unknown / non-tradable markets.

## Step 6 — Preserve Existing Intelligence Flow

### MUST_NOT_MODIFY

- `AnalysisExecution`
- `AnalysisExecutionService`
- `CapabilityAnalysisCoordinator`
- `ObservationBuilder`
- `OpportunityEngine`
- `ProductionIntelligencePipeline`
- `TradePlanningEngine`
- `Risk Domain`
- `PipelineRun` semantics

### Responsibility

Ensure Story 0005 stops before execution orchestration and only prepares the effective scope for future scan execution.

### Tests

- regression test that the existing single-market analysis endpoint still behaves as before;
- regression test that no new persistence is created by scope resolution.

## Step 7 — Validation Coverage

### Target Test Areas

- `market-intelligence`
- `market-data`
- `trading-core` account and broker-account ownership

### Expected Test Types

- unit tests for resolver logic;
- controller tests for request/response shape;
- service tests for ownership and eligibility;
- regression tests ensuring `Risk Domain` is not called.

## Step 8 — Explicit Non-Goals

### OUT_OF_SCOPE

- `ActiveScan` aggregate creation;
- multi-market child `AnalysisExecution` orchestration;
- `PipelineRun` reuse as a scan aggregate;
- passive scanner implementation;
- AI relevance;
- opportunity ranking;
- trade planning;
- risk validation;
- broker execution;
- frontend scanner UI;
- watchlists;
- news service;
- Quant/V2 work.
