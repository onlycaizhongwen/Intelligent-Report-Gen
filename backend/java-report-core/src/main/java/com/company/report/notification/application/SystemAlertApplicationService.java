package com.company.report.notification.application;

import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemAlertApplicationService {
    private final SystemAlertRepository repository;

    public SystemAlertApplicationService(SystemAlertRepository repository) {
        this.repository = repository;
    }

    public PageResponse<Map<String, Object>> listMine(String status, int page, int pageSize) {
        Long userId = CurrentUserHolder.get().userId();
        return new PageResponse<>(
                repository.findByRecipient(userId, status, page, pageSize).stream()
                        .map(this::toResponse)
                        .toList(),
                page,
                pageSize,
                repository.countByRecipient(userId, status)
        );
    }

    private Map<String, Object> toResponse(SystemAlert alert) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("alertId", alert.id());
        response.put("recipientUserId", alert.recipientUserId());
        response.put("type", alert.type());
        response.put("severity", alert.severity());
        response.put("status", alert.status());
        response.put("resourceType", alert.resourceType());
        response.put("resourceId", alert.resourceId());
        response.put("payload", alert.payload());
        response.put("createdAt", alert.createdAt() == null ? "" : alert.createdAt().toString());
        return response;
    }
}
