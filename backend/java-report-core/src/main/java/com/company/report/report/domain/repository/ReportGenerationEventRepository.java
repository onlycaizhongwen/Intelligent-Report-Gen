package com.company.report.report.domain.repository;

import com.company.report.shared.api.SseEvent;

import java.util.List;

public interface ReportGenerationEventRepository {
    void append(SseEvent event);

    List<SseEvent> findByTaskId(Long taskId);
}
