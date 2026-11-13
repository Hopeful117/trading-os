# Implementation Plan - Story 0239

## Metadata

**Story:** `0239 - Define the Bounded Passive Market Universe`

**Status:** Approved and implemented; merged through PR #291

**Repository Analysis:** `repository-analysis.md`

**Implementation authorization:** Granted within the Story scope

## Objective

Implement the first passive-universe boundary in Market Intelligence without
adding persistence, public APIs, scheduling, account context or broker access.
The result will bind operator configuration, resolve exact canonical market
identity through the existing Market Data catalogue, and expose an immutable
`PassiveMarketUniverse` contract for Story 0240.

## Scope

### Included

- Validated `market-intelligence.passive-universe` configuration.
- Exact canonical market ID/provider/product-type resolution.
- Explicit diagnostics for disabled, unknown, unavailable and conflicting
  entries.
- Deterministic resolved-entry ordering.
- Versioned canonical fingerprint generation.
- Immutable application records consumed by future passive scheduling.
- Unit and configuration tests for all Story 0239 acceptance cases.

### Excluded

- Passive scheduler or wake-up logic.
- `PassiveScanRun` persistence or migrations.
- Runtime universe CRUD or public endpoints.
- Account, broker-account or credential resolution.
- Broker capability calls or synchronization.
- Historical backfill and provider-specific identity guessing.
- Active-scan eligibility changes.
- Demonstration defaults or runtime activation.

## Affected Modules

### `market-intelligence`

Owns the passive selection policy and will receive the new configuration,
resolution service, immutable contract, diagnostics and tests.

### `market-data`

No source or schema change is planned. Its existing catalogue endpoint and
canonical `Market` identity remain authoritative.

No other module is required for this Story.

## Proposed File Changes

The exact package names may follow local naming conventions during
implementation, but the expected change set is:

- Add a passive-universe configuration properties class under
  `market-intelligence/.../config` or the existing application configuration
  package.
- Add immutable passive-universe domain/application records under a dedicated
  `.../passive` package:
  - `PassiveMarketUniverse`;
  - `PassiveMarketEntry`;
  - `PassiveMarketUniverseDiagnostic`;
  - identity/diagnostic enums or value types as required.
- Add a resolver under the passive application package that depends on the
  configuration adapter and existing `MarketDataClient`.
- Register the properties class using the existing
  `@EnableConfigurationProperties` convention, unless component registration
  is demonstrably more consistent with the final package.
- Add configuration binding/validation tests under the existing Market
  Intelligence configuration test package.
- Add resolver and fingerprint tests under the passive application package.
- Update Story 0239 documentation only if the implemented external property
  shape or contract differs from this plan.

Existing `MarketDataClient`, `MarketResponse`, Market Data controllers,
entities and repositories should not change unless implementation discovers a
concrete contract defect required to resolve the Story. Such a defect would
require stopping for human guidance rather than broadening scope silently.

## Configuration Design

Bind the following logical shape under the passive-universe prefix:

```yaml
market-intelligence:
  passive-universe:
    version: v1
    entries:
      - market-id: <canonical-market-uuid>
        provider: CTRADER
        product-type: CFD
        enabled: true
        cadence: 5m
        minimum-evidence-profile: FOREX_QUOTE_BASIC
```

The implementation must not add the example instruments as defaults. The
absence of entries means a disabled empty universe. `version` is a selection
contract version, not a database or deployment revision; it is included in the
fingerprint input and must be explicitly versioned when canonical serialization
semantics change.

Required validation:

- `market-id`, `provider` and `product-type` are present;
- `version` is non-blank and supported by the V1 implementation;
- `enabled` is explicit;
- cadence is positive and within the configured bounded range;
- evidence profile is present and supported;
- duplicate configured identity keys are rejected deterministically.

Spring binding errors and semantic validation errors must fail closed with
actionable diagnostics. The configuration class must not expose framework
property names to the scheduler-facing contract.

## Resolution Design

Implement a resolver with one public operation equivalent to:

```text
PassiveMarketUniverse resolve()
```

Resolution sequence:

1. Read the validated configuration.
2. Return an immutable disabled empty universe when no entries are configured.
3. Reject duplicate `(marketId, provider, productType)` configuration keys.
4. Fetch the full current catalogue once through `MarketDataClient.findAllMarkets()`.
5. Index catalogue records by canonical market ID while retaining all identity
   fields needed for exact comparison.
6. For each enabled entry, require an exact market ID match and exact provider
   and product-type match.
7. Produce stable diagnostic codes for unknown ID, provider mismatch, product
   mismatch, disabled entry and catalogue unavailability.
8. Preserve valid resolved entries even when another entry has an explicit
   non-positive diagnostic, unless the configuration itself is invalid and must
   fail the whole resolution.
9. Sort entries by canonical market ID, provider and product type, followed by
   selection fields where needed for a total order.
10. Canonically serialize the version and ordered selection fields, then compute
    a SHA-256 fingerprint. Exclude resolution timestamps and transient
    diagnostics from the selection fingerprint.
11. Return records with defensive copies and no mutable configuration reference.

The resolver must distinguish invalid configuration from catalogue resolution
diagnostics. It must not infer OPEN, tradable or account eligibility from
catalogue data. Those concerns remain for Story 0240 and later active-scan
contracts.

## Contract Design

Use Java records or equivalent immutable value types consistent with the module.
The resolved contract must contain:

- selection/configuration version;
- stable fingerprint and fingerprint algorithm/version;
- resolution timestamp;
- ordered immutable resolved entries;
- immutable diagnostics.

Each resolved entry must contain canonical market ID, provider, product type,
cadence and minimum evidence profile. It must not contain account identity,
credentials or broker authorization state.

Diagnostic records should contain a stable code, severity, configured identity
when available, and a concise human-readable reason. Diagnostics must not
expose provider payloads or secrets.

## Error and Availability Handling

- Missing or malformed required configuration: fail during configuration
  validation; do not create a partially valid configuration object.
- Duplicate configuration identity: fail deterministically with the duplicate
  identity represented in the error.
- Unknown catalogue ID: return an explicit unresolved diagnostic.
- Provider/product mismatch: return an explicit identity-conflict diagnostic;
  never merge or choose a different catalogue record.
- Market Data client failure: return an unavailable diagnostic only if the
  existing application error-handling conventions support a non-throwing
  resolution result; otherwise use a typed resolver exception. The choice must
  be consistent and tested, not silently swallowed.
- Disabled entry: report disabled state and omit it from resolved entries.

The implementation must not add retries, fallback providers, synthetic market
records or broker calls.

## Test Plan

### Configuration tests

- valid V1 binding;
- absent/empty entries disables the universe;
- missing market ID, provider and product type;
- unsupported version, evidence profile and non-positive cadence;
- duplicate configured identity;
- explicit enabled/disabled behavior.

### Resolver tests

- resolves one valid exact identity;
- resolves multiple entries independent of input order;
- rejects or diagnoses unknown market ID;
- diagnoses provider mismatch;
- diagnoses product-type mismatch;
- preserves distinct provider/product identities for equal symbols;
- reports disabled and unavailable entries explicitly;
- does not invoke account or broker clients;
- calls the catalogue once per resolution operation.

### Fingerprint and immutability tests

- equivalent input order produces the same ordered output and fingerprint;
- changing identity, cadence, evidence profile or version changes the
  fingerprint;
- resolution timestamp does not change the fingerprint;
- returned entries and diagnostics cannot be mutated;
- canonical serialization remains stable for enum and UUID values.

Use existing JUnit/AssertJ and Mockito conventions. Do not add integration
infrastructure unless the existing Spring configuration tests require it.

## Validation

Implementation validation will be targeted to Market Intelligence:

- configuration and resolver unit tests;
- relevant Market Intelligence test suite;
- `git diff --check`;
- documentation/link validation for changed Story artifacts.

The implementation report must distinguish executed, passed, unavailable and
non-applicable checks. No broker, scheduler, database migration or deployed
environment validation is applicable to this Story.

## Risks and Controls

- **Identity drift:** compare canonical market ID, provider and product type;
  never resolve by display symbol alone.
- **Hidden account coupling:** keep account and broker types out of the
  configuration and resolver dependency graph.
- **Unstable runs:** sort before fingerprinting and exclude timestamps from the
  selection fingerprint.
- **Accidental activation:** empty configuration remains disabled and no
  scheduler is introduced.
- **Contract leakage:** expose only immutable `PassiveMarketUniverse` records
  to future consumers, not Spring properties.
- **Scope expansion:** do not modify Market Data or Active Scan behavior unless
  a concrete Story-blocking contract defect is found and separately approved.

## Implementation Order

1. Add immutable identity, selection and diagnostic types.
2. Add validated configuration binding and application registration.
3. Add catalogue resolution and exact identity validation.
4. Add deterministic ordering and fingerprint generation.
5. Add unit and configuration tests for acceptance criteria.
6. Run targeted validation and inspect the complete diff.
7. Produce the Implementation Report and stop for the normal review/finalization
   gates.

## Approval Boundary

This plan is not an implementation authorization. Production code changes may
begin only after explicit human approval of this exact Implementation Plan.
