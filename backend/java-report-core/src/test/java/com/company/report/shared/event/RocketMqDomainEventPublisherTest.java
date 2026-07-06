package com.company.report.shared.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqDomainEventPublisherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesDocumentParseEventToConfiguredTopicWithEnvelope() throws Exception {
        CapturingProducer producer = new CapturingProducer();
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_item_deleted", "knowledge_item_index_requested", "test-producer"),
                objectMapper,
                producer);

        publisher.publish("document.parse.requested", "101", Map.of("documentId", 101L));

        assertThat(producer.topic).isEqualTo("document_parse_requested");
        assertThat(producer.tag).isEqualTo("document.parse.requested");
        assertThat(producer.key).isEqualTo("101");
        JsonNode body = objectMapper.readTree(new String(producer.body, StandardCharsets.UTF_8));
        assertThat(body.get("eventType").asText()).isEqualTo("document.parse.requested");
        assertThat(body.get("eventKey").asText()).isEqualTo("101");
        assertThat(body.get("payload").get("documentId").asLong()).isEqualTo(101L);
    }

    @Test
    void mapsKnownDottedEventTypesToRocketMqSafeTopics() {
        CapturingProducer producer = new CapturingProducer();
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_item_deleted", "knowledge_item_index_requested", "test-producer"),
                objectMapper,
                producer);

        publisher.publish("report.generation.outline_ready", "task-1", Map.of("taskId", 1L));

        assertThat(producer.topic).isEqualTo("report_generation_outline_ready");
        assertThat(producer.tag).isEqualTo("report.generation.outline_ready");
    }

    @Test
    void publishesOutlineConfirmedEventToConfiguredReportGenerationTopic() {
        CapturingProducer producer = new CapturingProducer();
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_item_deleted", "knowledge_item_index_requested", "test-producer"),
                objectMapper,
                producer);

        publisher.publish("report.generation.outline_confirmed", "task-1", Map.of("taskId", 1L));

        assertThat(producer.topic).isEqualTo("report_generation_outline_confirmed");
        assertThat(producer.tag).isEqualTo("report.generation.outline_confirmed");
    }

    @Test
    void publishesKnowledgeItemDeletedEventToConfiguredCleanupTopic() {
        CapturingProducer producer = new CapturingProducer();
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_cleanup_topic", "knowledge_index_topic", "test-producer"),
                objectMapper,
                producer);

        publisher.publish("knowledge.item.deleted", "501", Map.of("knowledgeItemId", 501L));

        assertThat(producer.topic).isEqualTo("knowledge_cleanup_topic");
        assertThat(producer.tag).isEqualTo("knowledge.item.deleted");
    }

    @Test
    void publishesKnowledgeItemIndexRequestedEventToConfiguredIndexTopic() {
        CapturingProducer producer = new CapturingProducer();
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_cleanup_topic", "knowledge_index_topic", "test-producer"),
                objectMapper,
                producer);

        publisher.publish("knowledge.item.index_requested", "501", Map.of("knowledgeItemId", 501L));

        assertThat(producer.topic).isEqualTo("knowledge_index_topic");
        assertThat(producer.tag).isEqualTo("knowledge.item.index_requested");
    }

    @Test
    void doesNotFailSynchronousBusinessFlowWhenRocketMqSendFails() {
        RocketMqDomainEventPublisher publisher = new RocketMqDomainEventPublisher(
                new RocketMqProperties("localhost:9876", "document_parse_requested", "report_generation_outline_confirmed", "knowledge_item_deleted", "knowledge_item_index_requested", "test-producer"),
                objectMapper,
                (topic, tag, key, body) -> {
                    throw new IllegalStateException("broker unavailable");
                });

        publisher.publish("report.generation.outline_ready", "task-1", Map.of("taskId", 1L));
    }

    private static class CapturingProducer implements RocketMqMessageProducer {
        private String topic;
        private String tag;
        private String key;
        private byte[] body;

        @Override
        public void send(String topic, String tag, String key, byte[] body) {
            this.topic = topic;
            this.tag = tag;
            this.key = key;
            this.body = body;
        }
    }
}
