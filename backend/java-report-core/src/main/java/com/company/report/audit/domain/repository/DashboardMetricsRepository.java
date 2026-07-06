package com.company.report.audit.domain.repository;

import java.time.OffsetDateTime;
import java.util.Map;

public interface DashboardMetricsRepository {
    Map<String, Object> collect(OffsetDateTime since);

    default Map<String, Object> collect(OffsetDateTime since, Long actorUserId) {
        return collect(since);
    }
}
