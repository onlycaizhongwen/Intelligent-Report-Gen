import logging
import os

import pytest

from app.document_processing.application.rocketmq_worker import RocketMqDocumentParseSource
from app.document_processing.application.rocketmq_worker import RocketMqKnowledgeIndexCleanupSource
from app.document_processing.application.rocketmq_worker import RocketMqKnowledgeItemIndexSource
from app.report_generation.application.rocketmq_worker import RocketMqReportGenerationSource


def test_rocketmq_source_configures_python_client_nameserver(monkeypatch):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    monkeypatch.setenv("ROCKETMQ_ENDPOINT", "namesrv:9876")
    monkeypatch.setenv("ROCKETMQ_DOCUMENT_TOPIC", "document_parse_requested")
    monkeypatch.setenv("ROCKETMQ_DOCUMENT_CONSUMER_GROUP", "group-a")

    source = RocketMqDocumentParseSource.from_env()

    assert source.topic == "document_parse_requested"
    assert fake_module.consumer.group == "group-a"
    assert fake_module.consumer.namesrv == "namesrv:9876"


def test_report_generation_rocketmq_source_uses_outline_confirmed_topic(monkeypatch):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    monkeypatch.setenv("ROCKETMQ_ENDPOINT", "namesrv:9876")
    monkeypatch.setenv("ROCKETMQ_REPORT_GENERATION_TOPIC", "report_generation_outline_confirmed")
    monkeypatch.setenv("ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP", "group-report")

    source = RocketMqReportGenerationSource.from_env()

    assert source.topic == "report_generation_outline_confirmed"
    assert source.tag == "report.generation.outline_confirmed"
    assert fake_module.consumer.group == "group-report"
    assert fake_module.consumer.namesrv == "namesrv:9876"


def test_report_generation_rocketmq_source_processes_message(monkeypatch):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    source = RocketMqReportGenerationSource(consumer=fake_module.PushConsumer("group-report"), topic="topic")
    worker = RecordingReportGenerationWorker()

    source.start(worker)
    status = fake_module.consumer.callback(FakeMessage(b'{"eventType":"report.generation.outline_confirmed","eventKey":"501","payload":{"taskId":501}}'))

    assert status == fake_module._CConsumeStatus.CONSUME_SUCCESS
    assert worker.events[0]["payload"]["taskId"] == 501


def test_report_generation_rocketmq_source_logs_processing_failures(monkeypatch, caplog):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    source = RocketMqReportGenerationSource(consumer=fake_module.PushConsumer("group-report"), topic="topic")
    worker = FailingReportGenerationWorker()

    source.start(worker)
    with caplog.at_level(logging.ERROR):
        status = fake_module.consumer.callback(
            FakeMessage(b'{"eventType":"report.generation.outline_confirmed","eventKey":"501","payload":{"taskId":501}}')
        )

    assert status == fake_module._CConsumeStatus.RECONSUME_LATER
    assert "failed to process report generation message" in caplog.text
    assert "eventKey=501" in caplog.text
    assert "taskId=501" in caplog.text


def test_knowledge_index_cleanup_source_consumes_deleted_item_events(monkeypatch):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    monkeypatch.setenv("ROCKETMQ_ENDPOINT", "namesrv:9876")
    monkeypatch.setenv("ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC", "knowledge_item_deleted")
    monkeypatch.setenv("ROCKETMQ_KNOWLEDGE_CLEANUP_CONSUMER_GROUP", "group-cleanup")

    source = RocketMqKnowledgeIndexCleanupSource.from_env()
    worker = RecordingReportGenerationWorker()

    source.start(worker)
    status = fake_module.consumer.callback(FakeMessage(b'{"eventType":"knowledge.item.deleted","eventKey":"501","payload":{"knowledgeItemId":501}}'))

    assert source.topic == "knowledge_item_deleted"
    assert source.tag == "knowledge.item.deleted"
    assert fake_module.consumer.group == "group-cleanup"
    assert fake_module.consumer.namesrv == "namesrv:9876"
    assert status == fake_module._CConsumeStatus.CONSUME_SUCCESS
    assert worker.events[0]["payload"]["knowledgeItemId"] == 501


def test_knowledge_item_index_source_consumes_index_requested_events(monkeypatch):
    fake_module = FakeRocketMqModule()
    monkeypatch.setitem(__import__("sys").modules, "rocketmq.client", fake_module)
    monkeypatch.setenv("ROCKETMQ_ENDPOINT", "namesrv:9876")
    monkeypatch.setenv("ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC", "knowledge_item_index_requested")
    monkeypatch.setenv("ROCKETMQ_KNOWLEDGE_INDEX_CONSUMER_GROUP", "group-index")

    source = RocketMqKnowledgeItemIndexSource.from_env()
    worker = RecordingReportGenerationWorker()

    source.start(worker)
    status = fake_module.consumer.callback(FakeMessage(b'{"eventType":"knowledge.item.index_requested","eventKey":"501","payload":{"knowledgeItemId":501}}'))

    assert source.topic == "knowledge_item_index_requested"
    assert source.tag == "knowledge.item.index_requested"
    assert fake_module.consumer.group == "group-index"
    assert fake_module.consumer.namesrv == "namesrv:9876"
    assert status == fake_module._CConsumeStatus.CONSUME_SUCCESS
    assert worker.events[0]["payload"]["knowledgeItemId"] == 501


class FakeRocketMqModule:
    class _CConsumeStatus:
        CONSUME_SUCCESS = 0
        RECONSUME_LATER = 1

    def __init__(self):
        self.consumer = None

    def PushConsumer(self, group):
        self.consumer = FakePushConsumer(group)
        return self.consumer


class FakePushConsumer:
    def __init__(self, group):
        self.group = group
        self.namesrv = None
        self.topic = None
        self.callback = None
        self.tag = None

    def set_namesrv_addr(self, namesrv):
        self.namesrv = namesrv

    def subscribe(self, topic, callback, tag):
        self.topic = topic
        self.callback = callback
        self.tag = tag

    def start(self):
        pass

    def shutdown(self):
        pass


class FakeMessage:
    def __init__(self, body):
        self.body = body


class RecordingReportGenerationWorker:
    def __init__(self):
        self.events = []

    async def handle(self, event):
        self.events.append(event)
        return {"eventType": "report.generation.completed"}


class FailingReportGenerationWorker:
    async def handle(self, event):
        raise RuntimeError("callback failed")
