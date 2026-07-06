package com.company.report.notification.domain.repository;

import com.company.report.notification.domain.model.SystemAlert;

import java.util.List;

public interface SystemAlertRepository {
    SystemAlert save(SystemAlert alert);

    List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize);

    long countByRecipient(Long recipientUserId, String status);

    default boolean existsUnreadByDedupeKey(Long recipientUserId,
                                            String type,
                                            String resourceType,
                                            Long resourceId,
                                            String dedupeKey) {
        return false;
    }
}
