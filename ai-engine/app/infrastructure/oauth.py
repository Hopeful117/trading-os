from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import hashlib
import secrets
import sqlite3
from pathlib import Path
from urllib.parse import urlencode

import httpx
import jwt

from app.config import Settings
from app.infrastructure.credential_store import EncryptedCredentialStore, StoredCredential


class OAuthError(ValueError):
    pass


@dataclass(frozen=True)
class OAuthTransaction:
    state: str
    verifier: str
    nonce: str
    client_id: str
    created_at: datetime


class ChatGptOAuthService:
    def __init__(self, config: Settings, credentials: EncryptedCredentialStore,
                 http_client: object | None = None) -> None:
        self._config = config
        self._credentials = credentials
        self._http_client = http_client
        path = config.oauth_store_path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute("CREATE TABLE IF NOT EXISTS oauth_transactions "
                               "(state TEXT PRIMARY KEY, verifier TEXT NOT NULL, nonce TEXT NOT NULL, "
                               "client_id TEXT NOT NULL, created_at TEXT NOT NULL)")
            try:
                connection.execute("ALTER TABLE oauth_transactions ADD COLUMN client_id TEXT NOT NULL DEFAULT 'dynamic_agent_client'")
            except sqlite3.OperationalError:
                pass

    def start(self) -> str:
        if not self._config.chatgpt_oauth_enabled:
            raise OAuthError("ChatGPT OAuth is disabled")
        verifier = secrets.token_urlsafe(64)
        challenge = base64url(hashlib.sha256(verifier.encode()).digest())
        state = secrets.token_urlsafe(32)
        nonce = secrets.token_urlsafe(32)
        client_id = self._config.chatgpt_client_id
        with self._connect() as connection:
            connection.execute("INSERT INTO oauth_transactions(state, verifier, nonce, client_id, created_at) "
                               "VALUES (?, ?, ?, ?, ?)",
                               (state, verifier, nonce, client_id, datetime.now(timezone.utc).isoformat()))
        params = {
            "response_type": "code", "client_id": client_id,
            "redirect_uri": self._config.chatgpt_redirect_uri,
            "scope": " ".join(self._config.chatgpt_scopes.split()), "state": state,
            "code_challenge": challenge, "code_challenge_method": "S256", "nonce": nonce,
        }
        if client_id == "dynamic_agent_client":
            params["agent_name_hint"] = self._config.chatgpt_agent_name
            if self._config.chatgpt_ext_agent_host_id:
                params["ext_agent_host_id"] = self._config.chatgpt_ext_agent_host_id
        if self._config.chatgpt_resource:
            params["resource"] = self._config.chatgpt_resource
        return self._config.chatgpt_authorization_endpoint + "?" + urlencode(params)

    def callback(self, state: str, code: str, client_id: str | None = None,
                 error: str | None = None) -> StoredCredential:
        if error or not code:
            raise OAuthError("authorization was not completed")
        with self._connect() as connection:
            row = connection.execute("SELECT verifier, nonce, client_id, created_at FROM oauth_transactions WHERE state = ?", (state,)).fetchone()
            if row is None:
                raise OAuthError("invalid OAuth transaction")
            connection.execute("DELETE FROM oauth_transactions WHERE state = ?", (state,))
        effective_client_id = client_id or row[2]
        if row[2] == "dynamic_agent_client":
            if not client_id or client_id == "dynamic_agent_client":
                raise OAuthError("authorization could not be completed")
        elif effective_client_id != row[2]:
            raise OAuthError("authorization could not be completed")
        transaction = OAuthTransaction(state, row[0], row[1], row[2], datetime.fromisoformat(row[3]))
        if datetime.now(timezone.utc) - transaction.created_at > timedelta(seconds=self._config.oauth_transaction_ttl_seconds):
            raise OAuthError("expired OAuth transaction")
        payload = {"grant_type": "authorization_code", "client_id": effective_client_id,
                   "code": code, "redirect_uri": self._config.chatgpt_redirect_uri,
                   "code_verifier": transaction.verifier}
        if self._config.chatgpt_resource:
            payload["resource"] = self._config.chatgpt_resource
        try:
            response = self._post(self._config.chatgpt_token_endpoint, data=payload)
            if response.status_code >= 400:
                raise OAuthError("authorization could not be completed")
            token = response.json()
            access_token = token.get("access_token")
            refresh_token = token.get("refresh_token")
            scopes = token.get("scope", "")
            if (not isinstance(access_token, str) or not access_token
                    or (refresh_token is not None and not isinstance(refresh_token, str))
                    or not isinstance(scopes, str)):
                raise OAuthError("authorization could not be completed")
            credential = StoredCredential(
                handle=self._config.chatgpt_credential_handle or "chatgpt-" + secrets.token_urlsafe(24), provider="chatgpt",
                access_token=access_token, refresh_token=refresh_token,
                expires_at=self._expiry(token.get("expires_in")),
                client_id=effective_client_id,
                scopes=scopes,
                subject=str(self._validate_id_token(token.get("id_token"), transaction.nonce, effective_client_id)["sub"]),
            )
            self._credentials.put(credential)
            return credential
        except OAuthError:
            raise
        except (httpx.HTTPError, ValueError, KeyError) as error:
            raise OAuthError("authorization could not be completed") from error

    def refresh(self, credential: StoredCredential) -> StoredCredential:
        if not credential.refresh_token or not credential.client_id:
            raise OAuthError("ChatGPT credential requires reauthorization")
        try:
            response = self._post(self._config.chatgpt_token_endpoint, data={
                "grant_type": "refresh_token", "client_id": credential.client_id,
                "refresh_token": credential.refresh_token,
                "resource": self._config.chatgpt_resource,
            })
            if response.status_code >= 400:
                raise OAuthError("ChatGPT credential requires reauthorization")
            token = response.json()
            access_token = token.get("access_token")
            refresh_token = token.get("refresh_token")
            if (not isinstance(access_token, str) or not access_token
                    or (refresh_token is not None and not isinstance(refresh_token, str))):
                raise OAuthError("ChatGPT credential requires reauthorization")
            return self._credentials.replace_tokens(credential, access_token,
                                                    refresh_token, self._expiry(token.get("expires_in")))
        except OAuthError:
            raise
        except (httpx.HTTPError, ValueError, KeyError) as error:
            raise OAuthError("ChatGPT credential requires reauthorization") from error

    def _post(self, url: str, **kwargs: object) -> httpx.Response:
        if self._http_client is not None:
            return self._http_client.post(url, **kwargs)
        with httpx.Client(timeout=self._config.timeout_seconds) as client:
            return client.post(url, **kwargs)

    def _validate_id_token(self, value: object, nonce: str, client_id: str) -> dict:
        if not isinstance(value, str) or not value:
            raise OAuthError("authorization could not be completed")
        try:
            key = jwt.PyJWKClient(self._config.chatgpt_jwks_url).get_signing_key_from_jwt(value)
            claims = jwt.decode(value, key.key, algorithms=["RS256", "ES256"],
                                audience=client_id,
                                issuer=self._config.chatgpt_issuer,
                                options={"require": ["sub", "nonce"]})
            if claims.get("nonce") != nonce:
                raise OAuthError("authorization could not be completed")
            return claims
        except OAuthError:
            raise
        except Exception as error:
            raise OAuthError("authorization could not be completed") from error

    @staticmethod
    def _expiry(value: object) -> datetime | None:
        return datetime.now(timezone.utc) + timedelta(seconds=value) if isinstance(value, int) else None

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._config.oauth_store_path, timeout=5)


def base64url(value: bytes) -> str:
    import base64
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
