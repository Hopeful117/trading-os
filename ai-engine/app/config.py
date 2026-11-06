from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_ENGINE_", extra="ignore")

    enabled: bool = False
    shadow_mode: bool = True
    provider: str = "mock"
    model: str = "gpt-5.1-mini"
    api_key: str | None = Field(default=None, repr=False)
    timeout_seconds: float = Field(default=20.0, gt=0, le=120)
    max_retries: int = Field(default=1, ge=0, le=3)
    max_context_bytes: int = Field(default=256_000, gt=0, le=2_000_000)
    max_evidence: int = Field(default=256, gt=0, le=2_000)
    max_output_tokens: int = Field(default=1_500, gt=0, le=8_000)
    fallback_provider: str | None = None
    chatgpt_oauth_enabled: bool = False
    chatgpt_credential_handle: str | None = Field(default=None, repr=False)
    chatgpt_authorization_endpoint: str = "https://auth.openai.com/api/accounts/authorize"
    chatgpt_token_endpoint: str = "https://auth.openai.com/api/accounts/oauth/token"
    chatgpt_responses_endpoint: str = "https://api.openai.com/v1/responses"
    chatgpt_redirect_uri: str = "http://127.0.0.1:8086/internal/v1/oauth/chatgpt/callback"
    chatgpt_client_id: str = "dynamic_agent_client"
    chatgpt_agent_name: str = "Trading OS"
    chatgpt_ext_agent_host_id: str | None = None
    chatgpt_issuer: str = "https://auth.openai.com"
    chatgpt_jwks_url: str = "https://auth.openai.com/.well-known/jwks.json"
    chatgpt_resource: str = "https://api.openai.com/v1"
    chatgpt_scopes: str = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    oauth_store_path: str = "/data/ai-oauth.sqlite3"
    credential_store_path: str = "/data/provider-credentials.sqlite3"
    credential_encryption_key: str | None = Field(default=None, repr=False)
    oauth_transaction_ttl_seconds: int = Field(default=300, ge=30, le=3600)
    require_service_auth: bool = True
    service_jwt_secret: str | None = Field(default=None, repr=False)
    service_jwt_issuer: str = "market-intelligence"
    service_jwt_audience: str = "ai-engine"


settings = Settings()
