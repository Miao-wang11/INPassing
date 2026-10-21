"""Environment-only configuration. Missing internal authentication fails closed."""
from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SHUNSHOU_AI_", env_file=".env", extra="ignore")

    environment: Literal["development", "test", "production"] = "development"
    internal_token: SecretStr = Field(min_length=32)
    model_provider: Literal["mock", "openai_compatible"] = "mock"
    model_name: str = "gpt-4.1-mini"
    model_base_url: str | None = None
    model_api_key: SecretStr | None = None
    java_base_url: str = "http://127.0.0.1:8080/api"
    checkpoint_path: Path = Path("data/checkpoints.sqlite3")
    request_timeout_seconds: float = Field(default=15, gt=0, le=120)
    max_history_messages: int = Field(default=12, ge=1, le=50)

    @model_validator(mode="after")
    def check_configuration(self) -> "Settings":
        secret = self.internal_token.get_secret_value()
        if not secret.strip() or secret.startswith("change-me"):
            raise ValueError("Configure a random SHUNSHOU_AI_INTERNAL_TOKEN")
        if not self.java_base_url.startswith(("http://", "https://")) or "@" in self.java_base_url:
            raise ValueError("Invalid trusted Java backend URL")
        if self.environment == "production" and self.model_provider == "mock":
            raise ValueError("mock model provider is prohibited in production")
        if self.model_provider == "openai_compatible" and (
            not self.model_api_key or not self.model_api_key.get_secret_value().strip()
        ):
            raise ValueError("model API key required for openai_compatible provider")
        return self
