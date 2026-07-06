import os
from pathlib import Path
import sys
import urllib.error
import urllib.request


HTTP_HEALTH_ENDPOINT = "http://127.0.0.1:8000/health"
HTTP_SERVER_MARKERS = ("uvicorn app.main:app",)
WORKER_MARKERS = (
    "app.report_generation.worker_main",
    "app.document_processing.worker_main",
    "app.document_processing.cleanup_worker_main",
    "app.document_processing.item_index_worker_main",
)


def run_healthcheck(*, cmdline: str, urlopen=urllib.request.urlopen) -> int:
    normalized_cmdline = " ".join(cmdline.split())
    if any(marker in normalized_cmdline for marker in HTTP_SERVER_MARKERS):
        try:
            urlopen(HTTP_HEALTH_ENDPOINT, timeout=3)
            return 0
        except urllib.error.URLError:
            return 1

    if any(marker in normalized_cmdline for marker in WORKER_MARKERS):
        return 0

    return 1


def main() -> int:
    cmdline = os.environ.get("CODEX_CONTAINER_CMDLINE", "")
    if not cmdline:
        proc_cmdline = Path("/proc/1/cmdline")
        if proc_cmdline.exists():
            cmdline = proc_cmdline.read_text(encoding="utf-8").replace("\x00", " ").strip()
    if not cmdline:
        cmdline = " ".join(sys.argv[1:])

    return run_healthcheck(cmdline=cmdline)


if __name__ == "__main__":
    raise SystemExit(main())
