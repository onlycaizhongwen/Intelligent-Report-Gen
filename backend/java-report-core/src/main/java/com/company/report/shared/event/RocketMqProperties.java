package com.company.report.shared.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rocketmq")
public record RocketMqProperties(String endpoint,
                                 String documentTopic,
                                 String reportGenerationTopic,
                                 String knowledgeItemDeletedTopic,
                                 String knowledgeItemIndexRequestedTopic,
                                 String producerGroup) {
}
