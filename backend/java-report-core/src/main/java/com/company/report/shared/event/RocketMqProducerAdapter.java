package com.company.report.shared.event;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqProducerAdapter implements RocketMqMessageProducer {
    private final RocketMqProperties properties;
    private DefaultMQProducer producer;

    public RocketMqProducerAdapter(RocketMqProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        try {
            producer = new DefaultMQProducer(properties.producerGroup());
            producer.setNamesrvAddr(properties.endpoint());
            producer.start();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to start RocketMQ producer", ex);
        }
    }

    @Override
    public void send(String topic, String tag, String key, byte[] body) {
        try {
            producer.send(new Message(topic, tag, key, body));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to send RocketMQ message to " + topic, ex);
        }
    }

    @PreDestroy
    void shutdown() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
