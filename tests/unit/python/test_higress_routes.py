from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
ROUTES = ROOT / "config" / "higress" / "routes.yaml"


def test_higress_does_not_expose_python_ai_service_as_external_api():
    routes = ROUTES.read_text(encoding="utf-8")

    assert "path: /api/v1/chat" not in routes
    assert "path: /api/v1/documents" not in routes
    assert "name: python-ai-service" not in routes
    assert "path: /api/v1" in routes
    assert "name: java-report-core" in routes
