from __future__ import annotations

import base64
from dataclasses import dataclass
from datetime import datetime, timezone
import secrets
import sqlite3
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class CredentialStoreError(RuntimeError):
    pass


@dataclass(frozen=True)
class StoredCredential:
    handle: str
    provider: str
    access_token: str
    refresh_token: str | None
    expires_at: datetime | None
    client_id: str | None = None
    scopes: str = ""
    subject: str | None = None


class EncryptedCredentialStore:
    def __init__(self, path: str, encoded_key: str | None) -> None:
        if not encoded_key:
            raise CredentialStoreError("credential encryption is not configured")
        try:
            key = base64.urlsafe_b64decode(encoded_key.encode("ascii"))
        except (ValueError, UnicodeError) as error:
            raise CredentialStoreError("credential encryption key is invalid") from error
        if len(key) not in {16, 24, 32}:
            raise CredentialStoreError("credential encryption key has invalid length")
        self._key = key
        self._path = path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                "CREATE TABLE IF NOT EXISTS provider_credentials ("
                "handle TEXT PRIMARY KEY, provider TEXT NOT NULL, access_token BLOB NOT NULL, "
                "refresh_token BLOB, expires_at TEXT, client_id TEXT, scopes TEXT NOT NULL, subject TEXT, created_at TEXT NOT NULL)"
            )
            try:
                connection.execute("ALTER TABLE provider_credentials ADD COLUMN subject TEXT")
            except sqlite3.OperationalError:
                pass

    def put(self, credential: StoredCredential) -> None:
        if not credential.handle or not credential.provider or not credential.access_token:
            raise CredentialStoreError("credential metadata is incomplete")
        cipher = AESGCM(self._key)
        access_nonce = secrets.token_bytes(12)
        access = access_nonce + cipher.encrypt(access_nonce, credential.access_token.encode(), credential.handle.encode())
        refresh = None
        if credential.refresh_token:
            refresh_nonce = secrets.token_bytes(12)
            refresh = refresh_nonce + cipher.encrypt(refresh_nonce, credential.refresh_token.encode(), credential.handle.encode())
        with self._connect() as connection:
            connection.execute(
                "INSERT INTO provider_credentials(handle, provider, access_token, refresh_token, expires_at, client_id, scopes, subject, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(handle) DO UPDATE SET provider=excluded.provider, "
                "access_token=excluded.access_token, refresh_token=excluded.refresh_token, expires_at=excluded.expires_at, "
                "client_id=excluded.client_id, scopes=excluded.scopes, subject=excluded.subject",
                (credential.handle, credential.provider, access, refresh,
                 credential.expires_at.isoformat() if credential.expires_at else None,
                 credential.client_id, credential.scopes, credential.subject,
                 datetime.now(timezone.utc).isoformat()),
            )

    def get(self, handle: str, provider: str) -> StoredCredential:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT provider, access_token, refresh_token, expires_at, client_id, scopes, subject "
                "FROM provider_credentials WHERE handle = ? AND provider = ?", (handle, provider)
            ).fetchone()
        if row is None:
            raise CredentialStoreError("credential is not available")
        try:
            access = self._decrypt(handle, row[1])
            refresh = self._decrypt(handle, row[2]) if row[2] else None
        except Exception as error:
            raise CredentialStoreError("credential could not be decrypted") from error
        return StoredCredential(handle, row[0], access, refresh,
                                datetime.fromisoformat(row[3]) if row[3] else None, row[4], row[5], row[6])

    def replace_tokens(self, credential: StoredCredential, access_token: str,
                       refresh_token: str | None, expires_at: datetime | None) -> StoredCredential:
        updated = StoredCredential(credential.handle, credential.provider, access_token,
                                   refresh_token or credential.refresh_token, expires_at,
                                    credential.client_id, credential.scopes, credential.subject)
        self.put(updated)
        return updated

    def _decrypt(self, handle: str, value: bytes) -> str:
        return AESGCM(self._key).decrypt(value[:12], value[12:], handle.encode()).decode()

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, timeout=5)
