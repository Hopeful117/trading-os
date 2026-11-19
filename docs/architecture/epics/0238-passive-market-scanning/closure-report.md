# Epic 0238 — Conditional Closure Report

**Date:** 2026-11-19
**Status:** `READY_FOR_HUMAN_CLOSURE_APPROVAL`
**Closure state:** Not closed; `V1_INTEGRATION_VALIDATED` not yet declared
**Scope:** Stories 0239–0246 and the V1 passive market scanning boundary

## A. Executive Summary

Epic 0238 delivers a bounded passive market monitoring path:

```text
configured market universe
    -> durable passive run and market lifecycle
    -> bounded dispatch and recovery
    -> deterministic analysis evidence
    -> passive awareness projection
    -> explicit trader-requested ActiveScan investigation
```

The implementation preserves the intended boundary. Passive monitoring does not authorize an
account, create a TradePlan, invoke risk authority, place broker orders, or automatically escalate
to ActiveScan.

The Epic is technically ready for explicit human closure approval with accepted residual risks. It
is not automatically closed by this report. Story 0246 provides meaningful PostgreSQL coordinator
integration evidence, but it does not exercise the complete production analysis pipeline or browser-
level E2E behavior.

## B. Repository Baseline

| Item | Value |
|---|---|
| Branch | `story/0246-passive-scanning-v1-dispatch-integration` |
| Local HEAD | `18078cacc36e0567bbeddd4924a06afc82ea6105` |
| `origin/main` | `8142cb14cbcd4e9ba7ec8803dbcc1c4358efca28` |
| Working tree before documentation | One pre-existing untracked `CLAUDE.md`; preserved and not modified |
| ADR baseline | ADR-102 accepted 2026-11-13 for Epic planning and implementation subject to Story gates |

The local branch and `origin/main` are distinct references. This report does not imply synchronization,
merge, commit, or push.

## C. Delivered Stories

| Story | Delivery and evidence | Governance state |
|---|---|---|
| 0239 | Bounded configured passive universe; PR #291 | Delivered and reviewed; human approval evidence should remain separately verified |
| 0240 | Durable `PassiveScanRun` and `PassiveScanMarket` lifecycle; PR #292 | Retrospective review exists; explicit human approval record not found |
| 0241 | Bounded recurring dispatch, leases, retries, metrics; PRs #293/#294 | Retrospective review exists; explicit human approval record not found |
| 0242 | Passive awareness projection and deterministic evaluator; PR #294, `41b9a741` | Retrospective implementation/review artifacts exist; explicit human approval record not found |
| 0243 | Passive-to-active validation and provenance; PR #295 | Existing engineering report records approval; repository evidence supports closure |
| 0244 | Angular passive awareness dashboard; PR #296, `c034970f` | Engineering report exists; independent review and explicit approval record remain unresolved |
| 0245 | Projection/Gateway validation; PR #297, `6d98226` | Review correctly identified H2 and coordinator gaps; not a complete V1 E2E proof |
| 0246 | PostgreSQL coordinator integration test; local implementation at `18078cacc` | Independent review completed in this report; human approval remains required |

## D. Integration Evidence

| Layer | Evidence | Boundary |
|---|---|---|
| Unit/domain | Passive lifecycle, evaluator, strategy, thesis, opportunity, risk, and active-scan tests | Individual rules and state transitions |
| Spring integration | Passive projection and persistence tests | Service-local persistence and projection behavior |
| PostgreSQL integration | `PassiveScanDispatchIntegrationTest` with Testcontainers and Flyway | Real coordinator, passive run/market persistence, and execution registration |
| Gateway integration | Passive-awareness route forwarding and query-parameter tests | Route and JWT forwarding contract |
| Angular tests/build | Passive feed, card, investigation panel, and production build evidence in Story 0244 | User-facing passive awareness behavior |
| Complete cross-service E2E | Not established | No single test runs the real downstream analysis pipeline through browser/UI behavior |

Story 0246's dispatcher mock and manually seeded downstream artifacts are explicitly accepted as a
V1 limitation. They must not be represented as full production analysis E2E coverage.

## E. Closure Matrix

| Criterion | Status | Evidence and limitation | Human requirement |
|---|---|---|---|
| C-01 | `SATISFIED` | Stories 0239–0244 are present in the `origin/main` delivery history. | Confirm the intended mainline reference. |
| C-02 | `PENDING_HUMAN_APPROVAL` | Story 0242 implementation report and retrospective review exist. | Owner must accept the retrospective governance record. |
| C-03 | `PENDING_HUMAN_APPROVAL` | Stories 0240 and 0241 retrospective reviews/reports exist. | Owner must accept the retrospective governance record. |
| C-04 | `PENDING_HUMAN_APPROVAL` | Story 0244 engineering report and merged status evidence exist; independent review approval is not recorded. | Owner must accept or assign the remaining review gate. |
| C-05 | `SATISFIED` | Gateway tests cover passive-awareness routing, JWT forwarding, and query parameters. | None beyond closure approval. |
| C-06 | `ACCEPTED_RESIDUAL_RISK` | Projection no-flood and origin rules are tested; coordinator-generated qualifying/non-qualifying output is not fully generated by the real analysis path. | Accept the evidence boundary under D-02. |
| C-07 | `ACCEPTED_RESIDUAL_RISK` | PostgreSQL Testcontainers and coordinator invocation are real; dispatcher and downstream artifacts are mocked/seeded. | Accept conditional V1 scope; do not call it full E2E. |
| C-08 | `ACCEPTED_RESIDUAL_RISK` | Account eligibility and passive-to-active safety are covered by existing validator/unit/architecture evidence; cross-service reproduction is not present. | Accept missing cross-service evidence under D-03. |
| C-09 | `ACCEPTED_RESIDUAL_RISK` | Deterministic idempotency and version-fingerprint behavior have supporting unit evidence; repeated coordinator dispatch is not asserted in Story 0246. | Accept missing integration evidence under D-03. |
| C-10 | `ACCEPTED_RESIDUAL_RISK` | Lease/recovery mechanisms and unit/component tests exist; PostgreSQL restart/recovery integration is not established. | Accept missing integration evidence under D-03. |
| C-11 | `SATISFIED` | Passive packages have no broker-write path; architecture and implementation evidence confirm no broker operation. | None beyond closure approval. |
| C-12 | `SATISFIED` | Passive-to-active architecture tests and frontend safety tests prevent automatic escalation/execution. | None beyond closure approval. |
| C-13 | `ACCEPTED_RESIDUAL_RISK` | Diagnostic codes and projection diagnostics exist; propagation through the real downstream integrated pipeline is not fully asserted. | Accept missing integrated diagnostic evidence under D-03. |
| C-14 | `PENDING_HUMAN_APPROVAL` | This report and Story 0246 review provide the technical recommendation; no automatic approval is inferred. | Explicit owner closure approval required. |

## F. Accepted Scope Exception

Story 0246 added `clearAutomatically = true` to the `@Modifying` claim update in
`SpringDataPassiveScanMarketRepository`.

This is accepted as a **corrective production defect fix and documented scope exception** because:

- PostgreSQL coordinator validation exposed a real stale first-level-cache defect.
- The defect caused the claim path to fail before dispatch could proceed.
- The annotation is localized to the failing repository method.
- It changes no trading behavior or authority boundary.
- The original Story constraint did not anticipate a production defect revealed by integration testing.

No additional production modifications are authorized by this exception.

## G. Residual Risks

| Risk ID | Description | Evidence | Severity | Acceptance rationale | Blocks controlled V1 usage? |
|---|---|---|---|---|---|
| R-0238-01 | Real analysis dispatcher and downstream opportunity generation are not exercised in Story 0246 | Dispatcher mocked; downstream records manually seeded | P2 | Coordinator/persistence boundary is valuable and domain tests support downstream rules | No, if clearly labelled |
| R-0238-02 | Scheduler bean is present but mocked rather than absent | `enabled=true` plus `@MockitoBean` scheduler | P2 | Scheduled execution is prevented in the test | No |
| R-0238-03 | Cross-service authorization integration evidence is incomplete | Existing validator/security/architecture tests | P2 | Underlying account and passive authority boundaries remain enforced | No, accepted under D-03 |
| R-0238-04 | Repeated-dispatch idempotency is not asserted at PostgreSQL coordinator level | Unit and persistence evidence exists | P2 | Durable keys and constraints are present | No, accepted under D-03 |
| R-0238-05 | PostgreSQL restart/recovery integration is not established | Recovery paths tested below full restart boundary | P2 | Recovery mechanism is bounded and no duplicate execution authority exists | No, accepted under D-03 |
| R-0238-06 | Full diagnostic propagation is not asserted through real downstream generation | Diagnostic contract and projection tests exist | P2 | Diagnostics remain visible in the projection contract | No, accepted under D-03 |
| R-0238-07 | Multi-instance passive capacity is not strictly atomic | Story 0241 retrospective review | P2 | V1 is bounded to single-instance operation | Yes for unqualified multi-instance operation |
| R-0238-08 | Passive opportunity origin query uses a text `LIKE` predicate | Story 0242 review | P2 | Acceptable at V1 volume; future indexing/denormalization may be needed | No |

No P0 or P1 defect remains confirmed by the static review. Missing evidence is not treated as proof
of correctness, but the owner-approved residual-risk posture permits controlled V1 usage.

## H. Safety Boundaries

- Passive scanning cannot automatically create an ActiveScan.
- Passive scanning cannot create a TradePlan or invoke deterministic risk authority.
- Passive scanning has no broker-write dependency.
- Passive awareness requires authentication while remaining account-independent by design.
- Account eligibility is revalidated by the active investigation path.
- Passive dispatch is disabled by default.
- Market selection, batch size, concurrency, retries, and provider evaluations are bounded.
- Idempotency and optimistic version predicates prevent duplicate logical claim ownership.
- AI remains optional and cannot override deterministic evidence, risk, or human authority.
- A connected cTrader account is not required for passive monitoring and is not accessed by this work.

## I. Governance Assessment

Completed or available:

- Story implementation reports for the Epic delivery chain
- Retrospective reviews for Stories 0240, 0241, 0242, and 0245
- Story 0246 independent review
- Story 0246 accepted corrective scope exception record
- Existing implementation and integration evidence

Still required:

- Human acceptance of the retrospective review records
- Human acceptance of the Story 0246 review and scope exception
- Human acceptance of the documented residual risks
- Explicit human decision on Epic closure

No approval is inferred from merge history, passing test reports, or this document.

## J. Closure Recommendation

`READY_FOR_HUMAN_CLOSURE_APPROVAL`

The recommendation is conditional. Epic 0238 should not be marked `CLOSED` or
`V1_INTEGRATION_VALIDATED` until the owner explicitly approves this report, the Story 0246 review,
the production scope exception, and the listed residual risks.
