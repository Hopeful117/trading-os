# Code Review Checklist - Story 0058

## Reviewed Scope

* `TradePlanOrigin` and origin-specific domain invariants.
* Manual planning application path.
* Authenticated manual Trade Plan endpoint.
* Origin and author persistence migration.
* Opportunity-origin regression behavior.
* Domain, application, HTTP and persistence tests.

## Findings

No confirmed implementation defect was found in the delivered Story scope.

## Review Notes

* Manual plans do not manufacture Opportunity provenance.
* Opportunity-origin plans still require Opportunity and Observation references.
* Manual author identity is derived from `MiUserPrincipal` rather than the
  request body.
* Manual creation persists a proposed plan only.
* Risk evaluation and Execution Intent creation remain downstream.
* Existing rows are migrated to `OPPORTUNITY`; missing historical authors are
  not fabricated.
* No provider-specific broker fields were added to the Trade Plan model.

## Known Risks

* The manual request accepts explicit sizing and risk inputs. Trading Core Risk
  Evaluation remains responsible for authorizing those values.
* Gateway authentication and production database migration were not exercised.
* The complete manual-plan-to-PAPER-execution journey is a subsequent Story.

## Human Review Required

* Confirm the manual API contract and required explicit sizing fields.
* Review the production migration strategy for historical author values.
* Validate the authenticated Gateway route and downstream Risk Evaluation.
* Approve the next Story for human validation and Execution Intent creation.
