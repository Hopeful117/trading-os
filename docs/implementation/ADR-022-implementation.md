# ADR-022 — V1 artifact management implementation

## Scope

This increment implements the domain contracts and an in-memory validation
adapter for ADR-022. It does not integrate artifact resolution into the
analysis pipeline yet and introduces no distributed infrastructure.

The implementation belongs to `market-intelligence` because that service owns
derived intelligence artifacts. Primary market, account, broker and future
news data remain owned by their source services.

## Package boundaries

```text
domain/artifact
    immutable identities, scopes, keys, fingerprints
    freshness, provenance, requirements, resolutions
    AI artifact identity and dependency relation

application/artifact
    reuse decisions and targeted invalidation

application/port
    ArtifactStore
    ArtifactDependencyRegistry

adapter/persistence
    InMemoryArtifactStore
    InMemoryArtifactDependencyRegistry
```

The domain has no dependency on Redis, Caffeine, SQL or Spring Cache.

## Identity

`ArtifactCacheKey` contains:

- logical artifact identity and producer version;
- complete reuse scope;
- deterministic parameters fingerprint;
- deterministic input fingerprint.

Parameters are serialized canonically before SHA-256 hashing. Map and set
ordering therefore does not alter the key. Technical request identifiers and
`AnalysisExecution.executionId` are not part of the key.

Private classifications require at least a user, account or tenant dimension.
Public scopes contain no user dimension and can therefore be shared.

`AiArtifactIdentity` adds context, prompt, model compatibility, inference,
authorized tools and security fingerprints. It provides no model integration.

## Freshness

`FreshnessEvaluator` distinguishes:

- `FRESH`;
- `STALE`;
- `EXPIRED`;
- `INVALIDATED`;
- `UNKNOWN`.

The assessment is relative to a `FreshnessPolicy` and execution mode. A stale
artifact can be accepted only by an explicit non-critical policy. Unknown,
expired and invalidated artifacts are rejected by default.

Technical storage lifetime and business freshness are independent.
Invalidation updates freshness metadata without deleting the entry.

## Resolution

`DefaultArtifactResolver` coordinates:

- execution validity;
- forced recalculation;
- exact key and scope compatibility;
- minimum result quality;
- freshness assessment;
- recalculation permission;
- provenance of reuse.

It returns `REUSE`, `REUSE_WITH_WARNING`, `RECALCULATE` or `REJECT` with an
explicit reason. The store retrieves artifacts but never makes reuse
decisions.

## Dependencies

Dependencies are registered between strongly typed artifact keys.
`ArtifactInvalidationService` propagates invalidation only through registered
dependents. It handles cycles defensively and does not perform global eviction.

## Deferred

- integration with `IntelligenceOrchestrator` and individual capabilities;
- capability-specific reuse policies;
- internal invalidation events;
- durable or distributed storage;
- metrics and artifact-resolution traces on `AnalysisExecution`;
- physical cache-key serialization;
- eviction policy;
- multi-instance consistency;
- inheritance of the most restrictive dependency classification.
