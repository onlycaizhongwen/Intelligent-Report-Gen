package com.company.report.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RocketMqDomainEventPublisher.class);

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final RocketMqMessageProducer producer;

    public RocketMqDomainEventPublisher(RocketMqProperties properties,
                                        ObjectMapper objectMapper,
                                        RocketMqMessageProducer producer) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    @Override
    public void publish(String eventType, String eventKey, Object payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of("eventType", eventType, "eventKey", eventKey, "payload", payload));
            producer.send(resolveTopic(eventType), eventType, eventKey, body);
        } catch (Exception ex) {
            log.warn("Failed to publish RocketMQ event {}, key={}. Synchronous business flow will continue.", eventType, eventKey, ex);
        }
    }

    private String resolveTopic(String eventType) {
        if ("document.parse.requested".equals(eventType)) {
            return properties.documentTopic();
        }
        if ("report.generation.outline_confirmed".equals(eventType)) {
            return properties.reportGenerationTopic();
        }
        if ("knowledge.item.deleted".equals(eventType)) {
            return properties.knowledgeItemDeletedTopic();
        }
        if ("knowledge.item.index_requested".equals(eventType)) {
            return properties.knowledgeItemIndexRequestedTopic();
        }
        return eventType.replace('.', '_');
    }
}
