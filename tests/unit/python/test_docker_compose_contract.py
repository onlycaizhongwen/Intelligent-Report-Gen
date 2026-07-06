from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
COMPOSE_FILE = ROOT / "docker-compose.yml"
PYTHON_DOCKERFILE = ROOT / "Dockerfile.python"


def test_python_service_has_java_completion_callback_configuration():
    compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))

    environment = compose["services"]["python-ai-service"]["environment"]
    assert environment["JAVA_SERVICE_URL"] == "http://java-report-core:8080"
    assert environment["JAVA_SERVICE_TOKEN"] == "${JAVA_SERVICE_TOKEN:-}"


def test_report_generation_worker_consumes_java_outline_confirmed_events():
    compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))

    service = compose["services"]["report-generation-worker"]
    environment = service["environment"]

    assert service["entrypoint"] == ["python", "-m", "app.report_generation.worker_main"]
    assert environment["REPORT_GENERATION_WORKER_PROVIDER"] == "rocketmq"
    assert environment["ROCKETMQ_REPORT_GENERATION_TOPIC"] == "${ROCKETMQ_REPORT_GENERATION_TOPIC:-report_generation_outline_confirmed}"
    assert environment["JAVA_SERVICE_URL"] == "http://java-report-core:8080"
    assert environment["JAVA_SERVICE_TOKEN"] == "${JAVA_SERVICE_TOKEN:-}"
    assert "healthcheck" not in service


def test_knowledge_cleanup_worker_consumes_deleted_item_events():
    compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))

    service = compose["services"]["knowledge-index-cleanup-worker"]
    environment = service["environment"]

    assert service["entrypoint"] == ["python", "-m", "app.document_processing.cleanup_worker_main"]
    assert environment["KNOWLEDGE_CLEANUP_WORKER_PROVIDER"] == "rocketmq"
    assert environment["ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC"] == "${ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC:-knowledge_item_deleted}"
    assert environment["DATABASE_URL"] == "postgresql://report:${DB_PASSWORD:-report123}@postgres:5432/intelligent_report"
    assert environment["OPENSEARCH_URL"] == "http://opensearch:9200"


def test_knowledge_item_index_worker_consumes_index_requested_events():
    compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))

    service = compose["services"]["knowledge-item-index-worker"]
    environment = service["environment"]

    assert service["entrypoint"] == ["python", "-m", "app.document_processing.item_index_worker_main"]
    assert environment["KNOWLEDGE_INDEX_WORKER_PROVIDER"] == "rocketmq"
    assert environment["ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC"] == "${ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC:-knowledge_item_index_requested}"
    assert environment["DATABASE_URL"] == "postgresql://report:${DB_PASSWORD:-report123}@postgres:5432/intelligent_report"
    assert environment["OPENSEARCH_URL"] == "http://opensearch:9200"


def test_python_runtime_image_installs_pdf_and_ocr_commands_for_document_parse_worker():
    dockerfile = PYTHON_DOCKERFILE.read_text(encoding="utf-8")

    assert "tesseract-ocr" in dockerfile
    assert "poppler-utils" in dockerfile


def test_document_parse_worker_compose_contract_matches_local_smoke_dependencies():
    compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))

    service = compose["services"]["document-parse-worker"]
    environment = service["environment"]

    assert service["entrypoint"] == ["python", "-m", "app.document_processing.worker_main"]
    assert environment["DOCUMENT_PARSE_WORKER_PROVIDER"] == "rocketmq"
    assert environment["ROCKETMQ_DOCUMENT_TOPIC"] == "${ROCKETMQ_DOCUMENT_TOPIC:-document_parse_requested}"
    assert environment["DATABASE_URL"] == "postgresql://report:${DB_PASSWORD:-report123}@postgres:5432/intelligent_report"
    assert environment["MILVUS_HOST"] == "milvus"
    assert environment["OPENSEARCH_URL"] == "http://opensearch:9200"
    assert environment["MINIO_ENDPOINT"] == "http://minio:9000"
