import urllib.error

from app.container_healthcheck import run_healthcheck


def test_run_healthcheck_uses_http_probe_for_api_process():
    calls = []

    def fake_urlopen(url, timeout):
        calls.append((url, timeout))
        return object()

    exit_code = run_healthcheck(
        cmdline="python -m uvicorn app.main:app --host 0.0.0.0 --port 8000",
        urlopen=fake_urlopen,
    )

    assert exit_code == 0
    assert calls == [("http://127.0.0.1:8000/health", 3)]


def test_run_healthcheck_accepts_report_generation_worker_without_http_probe():
    calls = []

    def fake_urlopen(url, timeout):
        calls.append((url, timeout))
        raise AssertionError("worker healthcheck should not call http probe")

    exit_code = run_healthcheck(
        cmdline="python -m app.report_generation.worker_main",
        urlopen=fake_urlopen,
    )

    assert exit_code == 0
    assert calls == []


def test_run_healthcheck_accepts_document_processing_workers_without_http_probe():
    worker_cmdlines = [
        "python -m app.document_processing.worker_main",
        "python -m app.document_processing.cleanup_worker_main",
        "python -m app.document_processing.item_index_worker_main",
    ]

    for cmdline in worker_cmdlines:
        exit_code = run_healthcheck(
            cmdline=cmdline,
            urlopen=lambda *_args, **_kwargs: (_ for _ in ()).throw(
                AssertionError("worker healthcheck should not call http probe")
            ),
        )

        assert exit_code == 0


def test_run_healthcheck_fails_when_api_probe_is_unreachable():
    def fake_urlopen(url, timeout):
        raise urllib.error.URLError("connection refused")

    exit_code = run_healthcheck(
        cmdline="python -m uvicorn app.main:app --host 0.0.0.0 --port 8000",
        urlopen=fake_urlopen,
    )

    assert exit_code == 1


def test_run_healthcheck_rejects_unknown_pid1_command():
    exit_code = run_healthcheck(cmdline="python -m some.unknown.entrypoint")

    assert exit_code == 1
