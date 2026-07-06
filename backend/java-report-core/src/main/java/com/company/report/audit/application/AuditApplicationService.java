package com.company.report.audit.application;

import com.company.report.audit.domain.repository.ModelInvocationRepository;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.audit.domain.repository.DashboardMetricsRepository;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditApplicationService {
    private final AuditRepository auditRepository;
    private final ModelInvocationRepository modelInvocationRepository;
    private final DashboardMetricsRepository dashboardMetricsRepository;

    @Autowired
    public AuditApplicationService(AuditRepository auditRepository, ModelInvocationRepository modelInvocationRepository, DashboardMetricsRepository dashboardMetricsRepository) {
        this.auditRepository = auditRepository;
        this.modelInvocationRepository = modelInvocationRepository;
        this.dashboardMetricsRepository = dashboardMetricsRepository;
    }

    public AuditApplicationService(AuditRepository auditRepository, ModelInvocationRepository modelInvocationRepository) {
        this(auditRepository, modelInvocationRepository, null);
    }

    public AuditApplicationService(AuditRepository auditRepository) {
        this(auditRepository, new ModelInvocationRepository() {
            @Override
            public com.company.report.audit.domain.model.ModelInvocationAudit save(com.company.report.audit.domain.model.ModelInvocationAudit invocation) {
                return invocation;
            }

            @Override
            public java.util.Optional<com.company.report.audit.domain.model.ModelInvocationAudit> findById(Long id) {
                return java.util.Optional.empty();
            }
        });
    }

    public PageResponse<Map<String, Object>> history(int page, int pageSize) {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null || currentUser.userId() == null) {
            return new PageResponse<>(List.of(), page, pageSize, 0L);
        }
        Long actorUserId = currentUser.userId();
        List<Map<String, Object>> items = auditRepository.findPageByActor(actorUserId, page, pageSize).stream()
                .map(log -> log.toResponse())
                .toList();
        return new PageResponse<>(items, page, pageSize, auditRepository.countByActor(actorUserId));
    }

    public PageResponse<Map<String, Object>> auditLogs(int page, int pageSize) {
        List<Map<String, Object>> items = auditRepository.findPage(page, pageSize).stream()
                .map(log -> log.toResponse())
                .toList();
        return new PageResponse<>(items, page, pageSize, auditRepository.count());
    }

    public Map<String, Object> modelInvocation(Long invocationId) {
        return modelInvocationRepository.findById(invocationId)
                .<Map<String, Object>>map(invocation -> new java.util.LinkedHashMap<>(invocation.toResponse()))
                .orElseGet(() -> auditRepository.findById(invocationId)
                .filter(log -> "model_invocation".equals(log.operationType()))
                .map(log -> {
                    Map<String, Object> response = new java.util.LinkedHashMap<>(log.toResponse());
                    response.put("invocationId", log.id());
                    response.remove("operationLogId");
                    return response;
                })
                .orElseThrow(() -> new IllegalArgumentException("model invocation audit log not found: " + invocationId)));
    }

    public Map<String, Object> dashboard(String range) {
        OffsetDateTime since = rangeStart(range);
        CurrentUser currentUser = CurrentUserHolder.get();
        Long actorScope = dashboardActorScope(currentUser);
        List<Map<String, Object>> activities = (actorScope == null
                ? auditRepository.findPage(1, 1000)
                : auditRepository.findPageByActor(actorScope, 1, 1000)).stream()
                .filter(log -> since == null || log.createdAt() == null || !log.createdAt().isBefore(since))
                .sorted(Comparator.comparing(log -> log.createdAt() == null ? OffsetDateTime.MIN : log.createdAt(), Comparator.reverseOrder()))
                .map(log -> log.toResponse())
                .toList();
        long reportExports = activities.stream()
                .filter(item -> "report_export".equals(item.get("operationType")))
                .count();
        long knowledgeOperations = activities.stream()
                .filter(item -> String.valueOf(item.get("operationType")).startsWith("knowledge_"))
                .count();
        long modelInvocations = activities.stream()
                .filter(item -> "model_invocation".equals(item.get("operationType")))
                .count();
        long failedOperations = activities.stream()
                .filter(item -> "failed".equals(item.get("result")))
                .count();

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("reportExports", reportExports);
        cards.put("knowledgeOperations", knowledgeOperations);
        cards.put("modelInvocations", modelInvocations);
        cards.put("failedOperations", failedOperations);

        Map<String, Object> response = dashboardMetricsRepository == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(actorScope == null
                ? dashboardMetricsRepository.collect(since)
                : dashboardMetricsRepository.collect(since, actorScope));
        response.put("range", range);
        response.put("scope", actorScope == null ? "global" : "personal");
        response.putIfAbsent("cards", cards);
        response.putIfAbsent("reportTrend", List.of());
        response.putIfAbsent("knowledgeRank", List.of());
        response.putIfAbsent("ruleScheduleHealth", Map.of(
                "scheduledRules", 0L,
                "failedScheduledRules", 0L,
                "blockedScheduledRules", 0L,
                "recentAlerts", 0L
        ));
        response.put("recentActivities", activities.stream().limit(10).toList());
        return response;
    }

    private Long dashboardActorScope(CurrentUser currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            return null;
        }
        boolean global = currentUser.roles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role))
                || currentUser.hasPermission("audit:read");
        return global ? null : currentUser.userId();
    }

    private OffsetDateTime rangeStart(String range) {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        return switch (range == null ? "" : range) {
            case "today" -> now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();
            case "last30days" -> now.minusDays(30);
            case "all" -> null;
            case "last7days", "" -> now.minusDays(7);
            default -> now.minusDays(7);
        };
    }
}
