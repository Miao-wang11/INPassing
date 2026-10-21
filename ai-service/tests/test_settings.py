import pytest
from pydantic import ValidationError

from app.config import Settings


def test_missing_service_token_fails_closed(monkeypatch):
    monkeypatch.delenv("SHUNSHOU_AI_INTERNAL_TOKEN", raising=False)
    with pytest.raises(ValidationError):
        Settings(_env_file=None)


def test_mock_prohibited_in_production(settings):
    with pytest.raises(ValidationError):
        Settings(
            _env_file=None,
            environment="production",
            model_provider="mock",
            internal_token=settings.internal_token,
        )


def test_missing_model_key_rejected(settings):
    with pytest.raises(ValidationError):
        Settings(
            _env_file=None,
            environment="test",
            model_provider="openai_compatible",
            internal_token=settings.internal_token,
        )
