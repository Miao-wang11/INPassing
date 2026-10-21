import pytest

from app.config import Settings


@pytest.fixture
def settings(tmp_path):
    return Settings(
        _env_file=None,
        environment="test",
        internal_token="local-test-key-0123456789-abcdef-9876543210",
        model_provider="mock",
        checkpoint_path=tmp_path / "checkpoint.sqlite3",
    )
