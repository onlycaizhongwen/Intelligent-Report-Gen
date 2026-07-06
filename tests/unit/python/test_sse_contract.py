import json
import os

os.environ.setdefault("JWT_SECRET", "test-secret")
from app.main import create_sse_event


def test_create_sse_event_uses_unified_schema():
    event = create_sse_event(
        type="delta",
        task_id="task-001",
        content="report body",
        stage="writing",
        progress=0.6,
        references=[{"referenceId": "ref-001"}],
        trace_id="trace-001",
    )

    assert event.startswith("data: ")
    payload = json.loads(event.removeprefix("data: ").strip())
    assert payload == {
        "type": "delta",
        "taskId": "task-001",
        "content": "report body",
        "stage": "writing",
        "references": [{"referenceId": "ref-001"}],
        "progress": 0.6,
        "errorCode": None,
        "traceId": "trace-001",
    }
