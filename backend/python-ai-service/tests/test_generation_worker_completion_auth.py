import os

import pytest
from jose import jwt

from app.report_generation.application.generation_worker import configured_completion_client


@pytest.fixture(autouse=True)
def clear_completion_auth_env(monkeypatch):
    for key in [
        "JAVA_SERVICE_URL",
        "REPORT_CORE_URL",
        "JAVA_SERVICE_TOKEN",
        "JWT_SECRET",
        "JAVA_SERVICE_TOKEN_SUBJECT",
        "JAVA_SERVICE_TOKEN_ROLES",
        "JAVA_SERVICE_TOKEN_PERMISSIONS",
        "JAVA_SERVICE_TOKEN_STATUS",
        "JAVA_SERVICE_TOKEN_TTL_SECONDS",
    ]:
        monkeypatch.delenv(key, raising=False)


def test_configured_completion_client_prefers_explicit_service_token(monkeypatch):
    monkeypatch.setenv("JAVA_SERVICE_URL", "http://java-report-core:8080")
    monkeypatch.setenv("JAVA_SERVICE_TOKEN", "explicit-token")
    monkeypatch.setenv("JWT_SECRET", "local-dev-secret-change-me-32-bytes-minimum")

    client = configured_completion_client()

    assert client.base_url == "http://java-report-core:8080"
    assert client.token == "explicit-token"


def test_configured_completion_client_generates_dev_token_from_shared_jwt_secret(monkeypatch):
    secret = "local-dev-secret-change-me-32-bytes-minimum"
    monkeypatch.setenv("JAVA_SERVICE_URL", "http://java-report-core:8080")
    monkeypatch.setenv("JWT_SECRET", secret)

    client = configured_completion_client()

    claims = jwt.decode(client.token, secret, algorithms=["HS256"])
    assert claims["sub"] == "1"
    assert claims["status"] == "enabled"
    assert claims["roles"] == ["ADMIN"]
    assert claims["permissions"] == ["report:create"]
    assert claims["exp"] > claims["iat"]


def test_configured_completion_client_raises_when_no_service_auth_is_available(monkeypatch):
    monkeypatch.setenv("JAVA_SERVICE_URL", "http://java-report-core:8080")

    with pytest.raises(RuntimeError, match="JAVA_SERVICE_TOKEN or JWT_SECRET is required"):
        configured_completion_client()
