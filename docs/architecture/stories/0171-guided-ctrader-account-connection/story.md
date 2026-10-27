# Story 0171 - Guided cTrader Account Connection

## Metadata

**ID:** `0171`
**Title:** Guided cTrader Account Connection
**Status:** Draft - Repository Analysis Awaiting Approval
**Related Stories:** `0147`, `0167`, `0169`, `0170`
**Related ADRs:** `ADR-073`, `ADR-074`, `ADR-077`

---

## Goal

Provide a single guided Trading OS workflow for connecting a cTrader account,
selecting its authorized external account, binding it to the local broker
account, provisioning the Trading Core account, and completing the separate
Market Data authorization when required.

The workflow must remove manual endpoint, callback, polling and provisioning
steps without merging the Broker Service and Market Data credential boundaries.

---

## Context

The cTrader authentication and explicit account-binding foundation exists in
Broker Service. Market Data has a separate service-owned cTrader authorization
and token lifecycle. ADR-073 and ADR-077 explicitly prohibit sharing token
material or runtime sessions between those services.

The current web account workflow exposes Kraken credential connection only. The
cTrader flow is therefore operated through separate manual browser/API steps.

---

## Problem

Connecting a cTrader account currently requires manual orchestration across
multiple endpoints and services. The user must initiate OAuth, handle the
callback, inspect discovered account IDs, bind one external account, provision
Trading Core, and separately authorize Market Data.

The UI does not provide a cTrader connection wizard or a unified state model for
these stages.

---

## Scope

- Add a cTrader connection entry point to the Accounts workflow.
- Create or reuse the owner-scoped local broker account through the guided flow.
- Initiate Broker Service OAuth from the authenticated web session.
- Handle callback completion and expose safe authorization state to the UI.
- Present discovered cTrader accounts for explicit human selection.
- Bind the selected external account through the existing owner/provider checks.
- Provision the Trading Core account after successful binding when required.
- Present the separate Market Data authorization as a guided next step.
- Poll or refresh safe provider-neutral state until each stage reaches a terminal
  result.
- Display actionable failure states for authorization, selection, binding,
  provisioning and Market Data authorization.
- Preserve idempotency for refreshes, callback reloads and repeated completion
  actions.
- Keep Broker Service and Market Data credentials, tokens and sessions separate.

---

## Out Of Scope

- Sharing or copying tokens between Broker Service and Market Data.
- Automatic cTrader account selection.
- Silent rebinding to a different external account.
- Changes to cTrader provider OAuth scopes or execution permissions.
- cTrader order placement, execution, reconciliation or trade authorization.
- Changes to FTMO rules, Risk formulas or Challenge admission.
- New cTrader market-data capabilities beyond connection orchestration.
- Exposing cTrader DTOs, tokens, authorization codes or provider payloads to the
  frontend or Trading Core.
- Replacing the accepted service responsibility boundaries.

---

## Acceptance Criteria

- [ ] An authenticated user can start cTrader connection from the Accounts UI
      without manually calling service endpoints.
- [ ] The workflow preserves the owner-scoped local broker account and does not
      trust a browser-selected local account during callback processing.
- [ ] OAuth callback completion is represented by a safe provider-neutral state
      and survives page refresh within the authorization-attempt lifetime.
- [ ] Zero, one and multiple discovered external accounts are handled
      deterministically; multiple accounts always require explicit selection.
- [ ] Selecting an external account completes the existing secure binding flow.
- [ ] Successful binding can continue directly to Trading Core provisioning
      without a separate manual API operation.
- [ ] Market Data authorization is presented as a separate guided stage and does
      not reuse Broker Service token material.
- [ ] Repeated callback loads, polling and completion actions are safe and do not
      create duplicate bindings or Trading Core accounts.
- [ ] Expired, denied, revoked, unavailable and invalid states produce safe,
      actionable UI feedback without exposing provider secrets.
- [ ] Existing Kraken connection and non-cTrader workflows remain unchanged.
- [ ] Focused backend, frontend and browser-level workflow tests pass.
- [ ] No authorization code, access token, refresh token or client secret is
      exposed in frontend state, URLs after callback, logs or API responses.

---

## Constraints

- Preserve `ADR-073`, `ADR-074` and `ADR-077` responsibility and secret-storage
  boundaries.
- Keep cTrader transport and provider-specific payloads inside provider adapters.
- Keep account binding explicit, owner-scoped and fail closed.
- Keep business, Risk and execution decisions deterministic and unchanged.
- Do not introduce automatic account selection or silent rebinding.
- Do not commit, push or merge automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/adr/ADR-077-ctrader-market-data-transport-boundary.md`

---

## Relevant Modules

- `broker-service`
- `market-data`
- `gateway`
- `trading-core`
- `trading-os-web`

---

## Validation

- Focused Broker Service OAuth, callback, discovery and binding tests.
- Focused Market Data authorization lifecycle tests.
- Trading Core provisioning idempotency tests.
- Angular service and Accounts workflow tests.
- Browser-level validation of the guided cTrader connection flow with no order
  submission.
- `git diff --check` and affected module validation.
- Explicit verification that no secrets appear in API responses, browser state
  or logs.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
