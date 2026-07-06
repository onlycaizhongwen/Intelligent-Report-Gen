package com.company.report.shared.event;

public interface DomainEventPublisher {
    void publish(String eventType, String eventKey, Object payload);
}
