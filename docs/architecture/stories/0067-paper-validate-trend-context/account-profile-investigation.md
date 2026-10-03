# Story 0067 - Account/Profile Runtime Investigation

**Investigation date:** 2026-10-03  
**Source baseline:** `80c3b40384a1ce78912e5740d638b81878e2c826`  
**Runtime account:** `65bc843f-3bab-43af-bcdb-564630f2f950`  
**Mode:** Read-only. No code, tests, database state, profile assignment,
account, TradePlan, RiskEvaluation, scan, execution, configuration, or
credentials were changed.

## 1. Executive Conclusion

**Primary classification: `STALE_RUNTIME_DATA`.**

The current runtime account selected in Decision Workspace is not a current
PAPER-provisioned account. The authenticated `GET /api/v1/broker-accounts`
response contains one broker account:

```text
id: 3c349dd9-c135-4ecc-8897-10c253b4e73e
provider: KRAKEN
executionMode: LIVE
displayName: Kraken Read Only
connectionStatus: CONNECTED
createdAt: 2026-08-24T23:57:50.841159Z
```

The selected trading account is `65bc843f-3bab-43af-bcdb-564630f2f950`, with
`brokerAccountId: null`, and its Account DTO contains null Risk and Trade
Planning Profile references. Decision Context returns the same null fields.

The current source correctly implements explicit profile assignment for the
official PAPER provisioning path. That path was introduced by Story 0043 for
Risk Profile assignment and Story 0053/ADR-046 for Trade Planning Profile
assignment. The current runtime broker account predates those changes and is a
LIVE synchronized/legacy account, not evidence from the current PAPER creation
path.

No application-level read endpoint available in this investigation exposed a
direct effective Risk Profile for the selected trading account. The effective
Trade Planning Profile endpoint did not return within the browser read timeout.
The null Account DTO fields prove that the assignment lookups used by
`AccountServiceImpl.toDto` did not return a usable profile, but do not expose
the underlying assignment rows. No database tables were queried directly.

The smallest justified action is to provision or select an already existing
PAPER account created through the current official flow, then re-run only the
account projection check. Creating or assigning anything was intentionally not
performed here. If the existing account is required to remain the validation
subject, a separate approved data-repair/provisioning decision is required.

## 2. Current Runtime Evidence

Authenticated read evidence:

| Read | Result | Meaning |
|---|---|---|
| `GET /api/v1/accounts` | HTTP 200 | Trading account list is available. |
| Account `65bc843f-3bab-43af-bcdb-564630f2f950` | `brokerAccountId: null`, all four profile reference fields null | Canonical Account DTO has no usable assigned references. |
| `GET /api/v1/intelligence/decision-context/{accountId}` | HTTP 200 | Decision Context propagated the same null references. |
| Decision Workspace | `Risk profile: Unavailable`; `Planning profile: Unavailable` | UI correctly reflects the Decision Context fields. |
| `GET /api/v1/broker-accounts` | One `KRAKEN / LIVE / CONNECTED` broker account | No current PAPER broker account is present in the authenticated account scope. |
| Effective Trade Planning Profile read | Browser request timed out | Effective planning profile is NOT_PROVEN through this read attempt. |

No Active Scan, Trend Context request, TradePlan, RiskEvaluation,
ExecutionIntent, PAPER execution, or LIVE operation was performed.

## 3. Risk Profile Domain Model

### Existence

Risk Profiles are immutable, versioned records in `risk_profile`, with rules in
`risk_profile_rule`. Their composite identity is `(id, semantic_version)`;
`trading-core/src/main/resources/db/migration/common/V2__trade_plan_risk_evaluation.sql:1-25`.

`RiskPersistence.profile(profileId, semanticVersion)` loads an exact existing
profile. `RiskProfileCatalogController` exposes eligible platform profiles at
`GET /api/v1/risk-profiles/eligible`. Catalog existence is not account
assignment.

### Explicit assignment

Risk assignment is a separate row in `account_risk_profile_assignment`, keyed by
`account_id`, with an exact profile ID/version, timestamp, and provenance;
`V2__trade_plan_risk_evaluation.sql:35-43`.

`RiskPersistence.assignProfile(...)` validates that the exact profile exists and
persists an assignment for the exact account ID. It does not select a default or
infer one from account facts.

`RiskPersistence.assignedProfile(accountId)` performs:

```text
entityManager.find(AccountRiskProfileAssignmentEntity, accountId)
 -> exact RiskProfileEntity(profileId, semanticVersion)
 -> Optional.empty if either is absent
```

This is explicit assignment lookup only. There is no fallback/default/effective
Risk Profile resolution in this method.

### Classification

- Profile exists: **PROFILE_EXISTS** when the catalog/profile lookup finds it.
- Profile is assigned: **PROFILE_ASSIGNED** only when the account assignment
  row resolves to an exact existing profile version.
- Profile is effective through fallback: **not a supported RiskPersistence
  semantic** in the inspected model.

## 4. Trade Planning Profile Domain Model

### Existence

Trade Planning Profiles are immutable, versioned records in
`trade_planning_profiles`, keyed by `(profile_id, profile_version)`;
`trading-core/src/main/resources/db/migration/common/V5__trade_planning_profiles.sql:1-21`.

The profile contains an explicit Risk Budget and planning preferences. It is
owned by the authenticated user and is not derived from account equity, broker
facts, Risk Profile, or application defaults. ADR-031 explicitly prohibits
those inferences.

### Explicit assignment and effective resolution

Assignments are append-only rows in
`account_trade_planning_profile_assignments`, keyed by account and assignment
version, with an exact profile ID/version;
`V5__trade_planning_profiles.sql:23-40`.

`TradePlanningProfileJpaRepository.findAssigned(accountId)` selects the latest
assignment version for the exact account ID, then loads the exact referenced
profile version. If no assignment exists, or the referenced profile cannot be
loaded, it returns `Optional.empty`.

`TradePlanningProfileService.effective(actorId, accountId)` first checks account
ownership, then calls `findAssigned(accountId)`. Missing assignment fails closed
with `PLANNING_PROFILE_MISSING` and HTTP 422. It does not create a profile,
choose the latest unassigned profile, or use a fallback.

The supported read endpoint is:

```text
GET /api/v1/trade-planning-profiles/accounts/{accountId}/effective
```

### Classification

- Profile exists: **PROFILE_EXISTS** when an immutable profile record is found.
- Profile is assigned: **PROFILE_ASSIGNED** when an assignment row resolves to
  an exact profile version.
- Profile is effective: **PROFILE_EFFECTIVE** when the ownership-checked
  effective endpoint resolves the assignment.
- Default/fallback effective profile: **not supported** by the current model.

## 5. Explicit Assignment vs Effective Profile Semantics

The three concepts are distinct:

| Concept | Risk Profile | Trade Planning Profile |
|---|---|---|
| Exists | Immutable exact profile/version exists | Immutable exact profile/version exists |
| Explicitly assigned | Account assignment row points to exact profile/version | Account assignment history points to exact profile/version |
| Effective | Assignment lookup resolves exact profile; no fallback found | Latest assignment resolves exact profile; no fallback found |
| Missing assignment | `assignedProfile` returns empty | `effective` fails closed with `PLANNING_PROFILE_MISSING` |

The Account DTO does not expose a separate “catalog exists” or “fallback
effective” concept. Its four profile fields are populated from explicit
assignment lookups in `AccountServiceImpl.toDto`.

The architecture is therefore not ambiguous on the inspected evidence:
current PAPER onboarding must create explicit assignments, and effective
resolution consumes those assignments. A LIVE account is not expected to
receive the PAPER platform-managed Trade Planning Profile.

## 6. PAPER Account Creation Flow

### Frontend

`trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts:71-90`
requires, for PAPER mode:

- display name;
- positive initial capital;
- explicit eligible Risk Profile selection.

`createPaper` at lines 144-162 sends:

```text
POST /api/v1/broker-accounts
provider
displayName
executionMode = PAPER
initialCapital
riskProfile.profileId
riskProfile.semanticVersion
```

The UI does not select a Trade Planning Profile. That is intentional: the
backend creates the platform-managed planning profile.

### Trading Core

`BrokerAccountController.create` delegates the authenticated owner and request
to `BrokerAccountService.create`.

`BrokerAccountService.create`:

1. Requires a Risk Profile reference for PAPER.
2. Loads and validates the exact Risk Profile.
3. Persists the BrokerAccount.
4. Creates the associated Trading Core `Account` with initial capital.
5. Persists Risk configuration.
6. Explicitly assigns the selected Risk Profile to the new account with
   provenance `paper-account-provisioning`.
7. Creates a new version-one platform-managed Trade Planning Profile.
8. Explicitly assigns that profile/version to the new account.

The service is annotated `@Transactional` at
`trading-core/.../brokeraccount/application/BrokerAccountService.java:36-39`.
The account, Risk assignment, planning profile, and planning assignment are
therefore intended to succeed or roll back together.

### Creation conclusion

Current official PAPER account creation **does guarantee both assignments in
source**, assuming the transaction completes and the current deployed image is
used. It cannot explain the current account evidence because the authenticated
runtime has no PAPER BrokerAccount and the selected broker account is LIVE.

## 7. Accounts Page Projection

The Accounts page uses two separate reads:

- `AccountService.getAccounts()` -> `GET /api/v1/accounts` for Trading Core
  account cards;
- `BrokerAccountService.list()` -> `GET /api/v1/broker-accounts` for broker
  connection/mode cards.

`account-card.html:12-25` displays Risk and Trade Planning references only when
the corresponding Account DTO fields are non-null. It does not call an
effective Trade Planning Profile endpoint and does not synthesize profile
references.

Therefore the current runtime does **not** demonstrate a UI mismatch where the
Accounts page shows profiles absent from AccountDto. It shows the opposite:

- broker connection state is shown from the BrokerAccount endpoint;
- trading account profile references are omitted because AccountDto fields are
  null.

The UI can show a LIVE broker connection beside a legacy trading account, but
that is not evidence that the trading account has effective PAPER profiles.

## 8. AccountService DTO Projection

Current `AccountServiceImpl.toDto` is:

```text
AccountMapper.toDto(account)
 -> RiskPersistence.assignedProfile(account.getAccountId())
 -> TradePlanningProfileRepository.findAssigned(account.getAccountId())
 -> set exact ID/version fields when Optional is present
```

The identifier passed to both lookups is the Trading Core `Account.accountId`,
not `BrokerAccount.id`.

Missing lookup results leave DTO fields null. There is no fallback to:

- an eligible Risk Profile catalog entry;
- the latest unassigned Risk Profile;
- the latest unassigned Trade Planning Profile;
- a profile associated with the BrokerAccount ID;
- account equity or currency-derived defaults.

Given the explicit assignment model, null fields are correct for an account with
no resolvable assignment. The current code would be wrong only if the account
has valid assignments that those exact account-ID lookups fail to find. The
application-level evidence cannot distinguish those two states without a
dedicated read contract or direct persistence inspection.

## 9. Validation Account Evidence

### Proven

- The authenticated session selected Trading Core account
  `65bc843f-3bab-43af-bcdb-564630f2f950`.
- `GET /api/v1/accounts` returned all four profile references as null.
- Decision Context returned the same account and null profile references.
- Decision Workspace displayed both profiles as `Unavailable`.
- `GET /api/v1/broker-accounts` returned one `KRAKEN / LIVE / CONNECTED`
  broker account, created 2026-08-24.
- No PAPER BrokerAccount was returned for the authenticated user.
- The selected Trading Core account had `brokerAccountId: null`.

### Not proven by allowed application reads

| Question | Classification | Reason |
|---|---|---|
| Exact Risk Profile record exists for this account | NOT_PROVEN | Catalog existence is not account-scoped assignment; no account-effective Risk read endpoint was exposed. |
| Risk Profile explicitly assigned | NOT_PROVEN at row level; Account lookup result is empty | DTO nulls show no usable assignment resolved, but do not expose whether a row is absent or invalid. |
| Effective Risk Profile through fallback | NOT_PROVEN / unsupported by source model | No fallback resolver was found. |
| Trade Planning Profile record exists for this account | NOT_PROVEN | Effective read timed out; Account DTO does not expose unassigned records. |
| Trade Planning Profile explicitly assigned | NOT_PROVEN at row level; `findAssigned` result is empty through DTO mapping | No assignment reference was returned. |
| Effective Trade Planning Profile | NOT_PROVEN | The supported effective endpoint did not complete in the browser read attempt. |
| Account is a current PAPER-provisioned account | **NOT_PROVEN and contradicted by broker read** | Current broker endpoint shows only LIVE; selected trading account is unlinked. |

### Strong historical inference

The broker connection was created on 2026-08-24. Story 0053's implementation
commit `698fe03` is dated 2026-09-20 and Story 0043's explicit PAPER Risk
onboarding commit `d91215a` is dated 2026-09-15. The selected runtime account
therefore predates both relevant onboarding changes and is not a valid proof of
current PAPER provisioning semantics.

This is an inference from application timestamps and Git history, not a direct
database assertion.

## 10. Decision Context Contract

`ActiveScanScopeResolutionService.resolveDecisionContext(accountId)` calls
`TradingCoreAccountClient.findOwnedAccount(accountId)`, which uses:

```text
GET /api/v1/accounts/{accountId}
```

The Feign response includes:

- account identity and financial summary;
- Risk Profile ID/version;
- Trade Planning Profile ID/version.

`DecisionContextResponse` copies these fields without fallback or independent
profile resolution. This is consistent with the canonical Account DTO
contract: Decision Context is a reference projection, not a profile catalog or
effective-profile resolver.

The current null Decision Context fields therefore originate before or at the
Trading Core singular Account projection. They are not manufactured by Market
Intelligence.

## 11. ADR / Story Invariants

### ADR-031

Trading Core owns the authoritative immutable versioned Trade Planning Profile.
No profile may be inferred from equity, broker facts, Risk Profile, or defaults.
An account without an explicit effective planning profile fails closed.

### ADR-046

Every newly created PAPER account receives a new immutable version-one
platform-managed Trade Planning Profile in the same transaction as provisioning.
Risk and Trade Planning Profiles remain separate. LIVE accounts do not receive
the PAPER default.

### Story 0053

Story 0053 explicitly addresses the former missing planning assignment during
PAPER onboarding. It requires transactional assignment, effective endpoint
visibility, and Account reload projection. It explicitly excludes LIVE
behavior and user-selected planning profiles.

### Story 0048

The official PAPER journey requires a fresh authenticated PAPER account and
records unresolved blockers without bypassing ownership, Risk, or human
authorization. A legacy LIVE/synchronized account cannot satisfy that fixture
requirement.

### Intended invariant

The repository evidence supports:

> Every current PAPER account created through official onboarding has explicit
> Risk Profile and Trade Planning Profile assignments. Account DTO and Decision
> Context expose those assigned exact references. LIVE and legacy accounts do
> not acquire the PAPER planning default by fallback.

No architecture decision is required between explicit and effective semantics;
the accepted ADRs already choose explicit assignment plus effective resolution
from that assignment.

## 12. Existing Test Coverage

| Test area | What it proves | Classification |
|---|---|---|
| `AccountServiceImplTest.singularAccountDtoIncludesAssignedProfiles` | DTO fields are set when both mocked assignment lookups return profiles | Mapping semantics only |
| `AccountControllerTest.singularAccountEndpointReturnsEnrichedProfiles` | Controller delegates to profile-enriching service and returns references | Controller mapping only |
| `RiskPersistenceTest.paperProvisioningPersistsCanonicalIdentityConfigurationAndExactProfile` | PAPER service persists account/Risk configuration and exact Risk assignment | Integration proves Risk assignment, not planning assignment |
| `TradePlanningProfileJpaRepositoryTest` | Explicit planning assignments and latest assignment version resolve correctly | Repository assignment semantics |
| `TradePlanningProfileServiceTest` | Missing effective planning profile fails closed | Effective lookup semantics |
| `accounts.spec.ts` PAPER tests | Frontend sends explicit Risk reference and positive capital, without credentials | UI request semantics only |
| `ActiveScanScopeResolutionServiceTest` | Decision Context passes mocked account references through market scope resolution | Client/service projection with mocked account |
| Story 0067 runtime evidence | Current account/Decision Context null fields | Runtime evidence, but not a current PAPER provisioning test |

## 13. Missing Test Boundary

The missing boundary is an application-level or Spring integration test that:

1. creates a PAPER BrokerAccount through `BrokerAccountService.create` with an
   eligible Risk Profile;
2. reloads the created Trading Core Account by its generated `accountId`;
3. verifies `RiskPersistence.assignedProfile(accountId)` resolves the exact
   selected profile;
4. verifies `TradePlanningProfileRepository.findAssigned(accountId)` resolves
   the generated version-one planning profile;
5. verifies `AccountServiceImpl.getAccountDtoById` exposes both references;
6. verifies the supported effective planning endpoint returns the same exact
   profile/version;
7. verifies the singular Account endpoint and Decision Context carry those
   references after reload.

Current tests prove creation inputs, Risk assignment, repository mechanics, and
mapping in isolation, but not the complete official provisioning-to-projection
boundary. No test was added in this investigation.

## 14. Root Cause Classification

### Primary: `STALE_RUNTIME_DATA`

The selected runtime account is not a current PAPER-provisioned account. The
only broker account in the authenticated scope is LIVE and was created before
the relevant onboarding fixes. The account is unlinked from that BrokerAccount
and has no profile references in the canonical Account DTO.

### Secondary classifications

- `DOCUMENTATION_DRIFT`: Section 18 labels the selected account as the current
  validation account but does not identify that its broker scope is LIVE and
  legacy rather than a current PAPER provisioning result.
- `INCONCLUSIVE`: application-level reads cannot prove the underlying
  assignment rows or the exact effective-profile endpoint result.

The evidence does **not** support:

- `ACCOUNT_CREATION_ASSIGNMENT_DEFECT` for current PAPER creation, because the
  current PAPER source path explicitly performs both assignments and this
  account was not shown to originate from that path;
- `ACCOUNT_PROJECTION_DEFECT` as the primary current-source cause, because the
  DTO correctly leaves fields null when assignment lookups resolve empty;
- `DECISION_CONTEXT_CONTRACT_DEFECT`, because Decision Context copies the
  canonical Account references as designed;
- `DEPLOYMENT_CONFIGURATION_DEFECT`, because the current source/image and
  service discovery were verified for this investigation.

## 15. Smallest Remediation

No code remediation is justified by this evidence.

The smallest operational remediation is to use an existing correctly
provisioned PAPER account, or provision one through the current official UI
flow, and then rerun only the account projection validation. This would create
or mutate runtime state and is intentionally **not performed** here.

If the existing account must be repaired instead, the smallest safe path is an
approved application-level profile assignment/provisioning operation that
preserves ownership, exact profile versions, provenance, and transactionality.
Direct database backfill is not justified or permitted by this investigation.

## 16. Architecture Decision Required, If Any

**No architecture decision is required for the intended current PAPER flow.**
ADR-046 and Story 0053 already define explicit assignment and no fallback.

A human product/architecture decision would be required only if the project
wants to make legacy/LIVE accounts usable through fallback profiles or to
retroactively migrate accounts without explicit provisioning. That would
conflict with the current “no inference/no PAPER default for LIVE” semantics
and is outside Story 0067.

## 17. Recommended Next Action

1. Treat account `65bc843f-3bab-43af-bcdb-564630f2f950` as an invalid fixture
   for current PAPER Trend Context validation unless its provenance is resolved.
2. Using the official Accounts UI, identify an existing PAPER account in the
   authenticated scope; do not create or assign one during this investigation.
3. If none exists, obtain explicit authorization for a separate controlled
   PAPER provisioning step.
4. After a valid PAPER account is selected, re-run only `GET /api/v1/accounts`
   and Decision Context. Do not proceed to Active Scan until both exact profile
   references are present.

## Explicit Answers

### Q1. Does the validation account have an explicitly assigned Risk Profile?

**Not proven at persistence-row level.** The canonical Account DTO and Decision
Context expose no resolved Risk assignment. The current source has no fallback
Risk resolver. The runtime account is also not shown to be a PAPER-provisioned
account.

### Q2. Does it have an effective Risk Profile through another mechanism?

**Not proven, and no such fallback mechanism exists in the inspected source.**
The Risk catalog exposes eligible profiles, but catalog existence does not make
one effective for an account.

### Q3. Does it have an explicitly assigned Trade Planning Profile?

**Not proven at persistence-row level; no usable assignment resolved.**
`AccountServiceImpl.toDto` received no profile from
`findAssigned(accountId)`, and the Account/Decision Context fields are null.

### Q4. Does it have an effective Trade Planning Profile through another mechanism?

**Not proven.** The supported effective endpoint timed out in the browser read
attempt, and source resolution has no fallback. The endpoint is expected to
fail closed when no assignment exists.

### Q5. Can the Accounts UI display profiles that are not represented as explicit assignments in AccountDto?

**No, based on current source.** Account cards display profile references only
from `GET /api/v1/accounts`; they do not call effective-profile endpoints or
invent references. The UI can display a separate LIVE broker connection, which
must not be confused with assigned trading-account profiles.

### Q6. Does official PAPER account creation guarantee the assignments required by Decision Context?

**Yes in current source.** `BrokerAccountService.create` transactionally assigns
the selected Risk Profile and creates/assigns a version-one Trade Planning
Profile. The current runtime account was not proven to originate from that path.

### Q7. Are current AccountService tests proving creation semantics, or only mapping semantics when assignments already exist?

**Primarily mapping semantics.** `AccountServiceImplTest` mocks both assignment
lookups as present. Separate persistence coverage proves Risk assignment and
repository mechanics, but no complete test proves PAPER creation through
Account DTO and Decision Context after reload.

### Q8. Is AccountDto wrong, account provisioning wrong, Decision Context wrong, or are the components using different valid concepts?

**The components use different valid concepts only at the broker/trading-account
display boundary; current AccountDto and Decision Context semantics are
consistent.** The selected runtime account is stale/legacy and not a valid
current PAPER provisioning fixture. Current PAPER provisioning source appears
correct, while the end-to-end creation-to-projection test boundary is missing.

### Q9. What is the primary root-cause classification?

**`STALE_RUNTIME_DATA`**, with secondary `DOCUMENTATION_DRIFT` and
`INCONCLUSIVE` assignment-row evidence.

### Q10. What is the smallest remediation justified by evidence?

Use an existing correctly provisioned PAPER account, or obtain separate
authorization to provision one through the official current UI, then rerun only
the account and Decision Context reads. Do not repair or assign this account in
this investigation.

### Q11. Does that remediation require a human architecture/product decision?

**No** for selecting/provisioning a current PAPER account under ADR-046. **Yes**
only if the product wants fallback profiles or migration of legacy/LIVE accounts
into explicit PAPER semantics.

## 18. Follow-up After Official PAPER Provisioning

The operational remediation described above was completed through the official
Accounts UI after this investigation. A new PAPER account was created with
initial capital and explicit Risk and Trade Planning Profile references.

Follow-up runtime evidence:

- Account: `2ded32af-a161-4ac6-8cea-b1a9f208ab33`;
- Broker account: `c3d7306a-1185-40c0-aa44-fee16a9a8602`;
- Risk Profile: `0a10c7e2-9d1e-4f5a-b6c8-123456789043` version `1.0.0`;
- Trade Planning Profile: `115a0394-49fe-43a7-af79-6e09f980f8d5` version `1`;
- Account list projection: HTTP 200 with both references;
- Decision Context projection: HTTP 200 with both references.

The original account remains a valid historical example of a legacy LIVE
account with no explicit PAPER profile assignments. It is not used as the
Story 0067 validation fixture. The earlier `STALE_RUNTIME_DATA` conclusion is
therefore retained as the cause of the original blocker, while the current
PAPER provisioning and projection path is validated by the follow-up run.
