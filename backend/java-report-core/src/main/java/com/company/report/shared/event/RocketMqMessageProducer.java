package com.company.report.shared.event;

public interface RocketMqMessageProducer {
    void send(String topic, String tag, String key, byte[] body);
}
