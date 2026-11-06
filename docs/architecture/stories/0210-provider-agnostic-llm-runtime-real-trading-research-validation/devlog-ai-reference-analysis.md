# DevLog AI Reference Analysis - Story 0210

## Reference Repository

`/home/ludo/workspace/devlog-ai` was inspected locally. No runtime dependency,
shared library or copied business logic is proposed.

## Reusable Technical Mechanisms

### Provider Abstraction

DevLog separates the provider-neutral `LlmProvider` contract from concrete
providers. Structured generation receives a prompt and response model and
returns validated output. Provider identity and model identity are exposed as
metadata, not domain behavior. Trading OS should preserve its existing simpler
`LLMProvider` port and extend it only with the metadata/usage required for
research evaluation.

### Provider Selection

DevLog uses explicit product-to-engine provider identity mapping and a factory.
Unknown providers fail configuration rather than silently falling back. Trading
OS should use an explicit registry with `mock` and `openai_chatgpt` entries;
ChatGPT should only be constructed when enabled and configured.

### OAuth Lifecycle

`ChatGptOAuthService` implements bounded OAuth state with single-use
transactions, PKCE S256, nonce validation, callback exchange and explicit
failure messages. It validates the ID token issuer, audience, nonce and signing
key. Browser/session state is separate from provider credentials.

### Credential Storage

`EncryptedCredentialStore` persists access tokens, refresh tokens, expiry,
scopes, issued client ID, subject and ID-token metadata in SQLite encrypted with
deployment-provided AES-GCM key material. Callers use opaque credential handles.
Credentials are not returned in normal responses or logs.

### Refresh and Provider Call

The ChatGPT provider resolves credentials only through a scoped resolver,
refreshes expired access tokens once, verifies required scopes, calls the
Responses endpoint with `store=false`, streams terminal events, and rejects
failed/incomplete responses. It uses strict JSON Schema output and maps remote
errors to bounded diagnostics. It does not expose remote payloads.

### Retry and Timeout

DevLog bounds provider timeout and retries only explicitly retryable transport/
status failures. OAuth refresh and authorization failures are not retried in an
uncontrolled loop. Trading OS should retain one bounded provider attempt or a
small configured retry count within the existing research AI-call budget.

### Testing

DevLog tests cover PKCE transaction creation and single-use claims, token
refresh, missing scopes, failed exchanges, credential encryption, provider
headers, structured output, incomplete streams, bounded error messages and
configured model/output limits. Trading OS should adapt this strategy with
synthetic tokens and fake HTTP clients; no real credential is needed in CI.

## Mechanisms Not Copied

- DevLog Core project membership, user configuration, credential associations
  and task binding persistence.
- DevLog business prompts, repository-context contracts and Story Agent logic.
- DevLog MCP identity/delegation behavior.
- DevLog-specific HMAC callbacks and completion codes unless Trading OS later
  approves a corresponding control-plane boundary.
- DevLog provider catalog or additional providers.

## Required Adaptation Decision

Trading OS currently lacks a Core control plane for user-scoped AI credential
bindings. The first implementation must therefore choose one explicitly:

1. an operator-managed AI Engine credential handle/configuration reference for
   the bounded single-runtime validation; or
2. a larger Java/Core configuration and binding feature, which is outside the
   smallest Story 0210 slice and may require an ADR.

The plan assumes option 1 for the first validation, with no user JWT, broker
credential or OAuth token crossing MCP or Java. This assumption requires human
approval before implementation.
