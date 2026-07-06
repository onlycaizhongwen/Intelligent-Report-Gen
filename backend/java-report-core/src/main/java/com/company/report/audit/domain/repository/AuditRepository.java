package com.company.report.audit.domain.repository;

import com.company.report.audit.domain.model.OperationLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AuditRepository {
    OperationLog save(OperationLog log);

    Optional<OperationLog> findById(Long id);

    List<OperationLog> findPage(int page, int pageSize);

    long count();

    default List<OperationLog> findPageByActor(Long actorUserId, int page, int pageSize) {
        return findPage(page, pageSize).stream()
                .filter(log -> actorUserId != null && actorUserId.equals(log.actorUserId()))
                .toList();
    }

    default long countByActor(Long actorUserId) {
        return findPage(1, Integer.MAX_VALUE).stream()
                .filter(log -> actorUserId != null && actorUserId.equals(log.actorUserId()))
                .count();
    }

    default long countByResourceOperationAndDetailSince(String resourceType,
                                                        Long resourceId,
                                                        String operationType,
                                                        String detailKey,
                                                        String detailValue,
                                                        OffsetDateTime since) {
        return findPage(1, Integer.MAX_VALUE).stream()
                .filter(log -> resourceType != null && resourceType.equals(log.resourceType()))
                .filter(log -> resourceId != null && resourceId.equals(log.resourceId()))
                .filter(log -> operationType != null && operationType.equals(log.operationType()))
                .filter(log -> since == null || log.createdAt() == null || !log.createdAt().isBefore(since))
                .filter(log -> detailValue != null && detailValue.equals(String.valueOf(log.detail().get(detailKey))))
                .count();
    }

    default long countByResourceOperationSince(String resourceType,
                                               Long resourceId,
                                               String operationType,
                                               OffsetDateTime since) {
        return findPage(1, Integer.MAX_VALUE).stream()
                .filter(log -> resourceType != null && resourceType.equals(log.resourceType()))
                .filter(log -> resourceId != null && resourceId.equals(log.resourceId()))
                .filter(log -> operationType != null && operationType.equals(log.operationType()))
                .filter(log -> since == null || log.createdAt() == null || !log.createdAt().isBefore(since))
                .count();
    }

    default List<OperationLog> findByResourceOperationAndDetail(String resourceType,
                                                                Long resourceId,
                                                                String operationType,
                                                                String detailKey,
                                                                String detailValue) {
        return findPage(1, Integer.MAX_VALUE).stream()
                .filter(log -> resourceType != null && resourceType.equals(log.resourceType()))
                .filter(log -> resourceId != null && resourceId.equals(log.resourceId()))
                .filter(log -> operationType != null && operationType.equals(log.operationType()))
                .filter(log -> detailValue != null && detailValue.equals(String.valueOf(log.detail().get(detailKey))))
                .toList();
    }

    default List<OperationLog> findByOperationAndDetail(String operationType,
                                                        String detailKey,
                                                        String detailValue) {
        return findPage(1, Integer.MAX_VALUE).stream()
                .filter(log -> operationType != null && operationType.equals(log.operationType()))
                .filter(log -> detailValue != null && detailValue.equals(String.valueOf(log.detail().get(detailKey))))
                .toList();
    }
}
