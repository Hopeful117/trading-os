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
    require_service_auth: bool = True
    service_jwt_secret: str | None = Field(default=None, repr=False)
    service_jwt_issuer: str = "market-intelligence"
    service_jwt_audience: str = "ai-engine"


settings = Settings()
