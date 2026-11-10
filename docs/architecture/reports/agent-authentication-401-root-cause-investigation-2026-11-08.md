# Agent Authentication 401 Root-Cause Investigation

**Date:** 2026-11-08  
**Baseline:** `main` at `c7954b40a07e5e74da2b2176c2ac99f792061537`  
**Scope:** read-only investigation; no source, configuration, credential, grant,
database, container or service changes were made.

## 1. Verdict

The currently observed workspace bridge is not failing authentication.

The verified current request sequence is:

```text
POST /api/v1/agents/token       -> 200 OK
GET  /api/v1/markets             -> 200 OK
GET  /api/v1/opportunities       -> 403 FORBIDDEN
```

This proves that the bridge received a runtime agent credential, exchanged it
for an agent access token, and successfully authenticated a Gateway request.
The `403` is an authorization result for the opportunities capability, not an
authentication failure.

The previously reported `401` is therefore not reproducible in the current
bridge flow and cannot be attributed to the current Gateway authentication
implementation from the available evidence. It came from an earlier runtime
attempt, an expired/invalid cached token, or a different MCP process/request
path. The precise historical request identity is not available in the logs.

## 2. Evidence

### Current bridge runtime

- Workspace OpenCode process: PID `744072`, started 2026-11-07 23:26:30.
- Workspace bridge process: PID `744733`, started 2026-11-07 23:26:34.
- Bridge status endpoint reports `connected: true`.
- The bridge accepts the credential through its local pairing endpoint and
  stores it only in process memory.
- Gateway logs at 2026-11-08 17:47:29 show token exchange `200 OK` followed by
  `/api/v1/markets` `200 OK`.
- Gateway logs at 2026-11-08 17:47:51 show `/api/v1/opportunities` `403
  FORBIDDEN`.

The current source path is consistent with that sequence:

- `mcp-adapter/app/bridge.py` validates the pairing credential by performing a
  token exchange before marking the bridge connected.
- `mcp-adapter/app/client.py` sends the resulting access token as a Bearer
  token and sanitizes Gateway errors.
- `mcp-adapter/app/server.py` calls only public Gateway paths.

### Historical runtime entries

Gateway logs also contain earlier research requests with `401 UNAUTHORIZED`,
including requests at approximately 08:33 and 09:36. Those entries are not
correlated to the current bridge request sequence. Other requests in the same
period successfully exchanged tokens and later received `403 FORBIDDEN` or
`200 OK`.

The MCP client deliberately retries `401` only for GET requests. POST requests
are not retried because automatic retry of a write operation is unsafe. An
expired or invalid cached token on a POST can consequently surface as a single
`401` until a new credential/token exchange occurs. This is a plausible
explanation for some historical 401s, but the available logs do not establish
that it caused the original report.

## 3. Authorization Finding

The `403` for `/api/v1/opportunities` is consistent with the architecture:

- Agent principals are authenticated separately from human JWT principals.
- Market Intelligence applies an agent allow-list to permitted research
  endpoints.
- Opportunities are not an authorized agent research endpoint in the current
  allow-list.

This is expected default-deny behavior and should not be changed to make the
MCP probe pass.

## 4. Runtime Freshness

The application images were created before the merge commit at 18:03:18+01:00:

- Gateway image created at 09:32:30+01:00.
- Trading Core image created at 10:38:13+01:00.
- Market Intelligence image created at 10:35:28+01:00.

The Dockerfiles build application artifacts from source during image creation,
but the images do not carry a reliable repository revision label for every
service. Runtime freshness relative to the merge is therefore **not proven**.
The successful current token exchange and market request prove behavior of the
running image, not that it contains the exact merge commit.

The MCP bridge, however, runs from the local repository and its source files
are present in the current worktree. No code changes were made during this
investigation.

## 5. Root-Cause Classification

```text
CURRENT AUTHENTICATION: PASS
CURRENT AUTHORIZATION: EXPECTED 403 FOR UNAUTHORIZED OPPORTUNITIES ACCESS
HISTORICAL 401 ROOT CAUSE: NOT PROVEN FROM AVAILABLE CORRELATED EVIDENCE
RUNTIME FRESHNESS: UNKNOWN FOR DOCKER APPLICATION IMAGES
```

The original issue should not be fixed by weakening authentication, adding
opportunities to the agent allow-list, retrying POST requests automatically,
or substituting a human JWT.

## 6. Recommended Follow-Up

1. Capture the exact MCP tool call, timestamp and sanitized Gateway status for
   any renewed 401 report.
2. If the 401 is a POST after token expiry, renew the credential through the
   existing pairing flow before retrying; do not add unsafe automatic POST
   retries.
3. If runtime validation must target the merged application code, rebuild and
   redeploy the relevant images through the approved operational workflow. That
   was not performed here.
4. Provision only the explicit research grants required for the intended
   readiness probe. Do not grant opportunities access merely to test MCP
   connectivity.

## 7. Worktree Safety

The investigation added only this report. Existing untracked architecture
reports were preserved. `git diff --check` passes.
