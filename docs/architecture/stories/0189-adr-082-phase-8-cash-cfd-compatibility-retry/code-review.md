# Code Review - Story 0189

## Verdict

**BLOCKED / NO PRODUCTION CHANGE APPROVED**

## High Findings

### 1. T1 does not prove fresh required-margin calculation

`PaperRequiredMarginClient` uses `FreshnessRequirement.t0()` for its own Market
Data call and passes `RequiredMarginPort.Request.price` into the calculator.
`ExecutionTimeRiskRevalidationService` does not supply the fresh valuation price
as the required-margin reference. A price change between T0 and T1 therefore has
no demonstrated effect on PAPER required margin. This fails ADR-083 T1
independent revalidation and blocks authorization.

### 2. Durable policy immutability is not enforced

V49 stores versioned rows and the entity is annotated `@Immutable`, but the
database has no immutable update/delete trigger and does not enforce the ADR-083
rate precision/provenance invariants. Historical policy economics can therefore
be changed outside the JPA write path. This blocks the required immutable
authority claim.

### 3. Required integration evidence is absent

No tracked Story 0188 `code-review.md` exists. There is no PostgreSQL policy
assignment/restart test and no application-level PAPER T0/T1 plus settlement E2E
using the local policy. Focused unit tests cannot establish the required
financial-authority chain.

## Confirmed Safe Boundaries

- PAPER no longer calls the Broker Service preview through the Story 0188
  production branch.
- LIVE remains on the provider-backed margin client.
- Risk Domain remains unchanged and provider-neutral.
- CFD settlement has a dedicated PAPER service and is selected before generic
  settlement.
- Human authorization and execution safety were not weakened.
- Compatibility remains fail-closed for `CASH + CFD`, `INVENTORY + CFD`, and
  `UNKNOWN + CFD`.

## Recommendation

Preserve rejection. Implement the smallest ADR-083 follow-up that repairs the
T1 reference/freshness binding and durable policy invariants, then rerun the
matrix and independent review. Do not modify `SettlementCompatibilityPolicy`
until that evidence exists.
