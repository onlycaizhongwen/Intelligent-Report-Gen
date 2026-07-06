package com.company.report.notification.infrastructure.persistence;

import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class JdbcSystemAlertRepository implements SystemAlertRepository {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSystemAlertRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SystemAlert save(SystemAlert alert) {
        if (alert.id() != null && alert.id() > 0) {
            return alert;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO system_alerts(
                      recipient_user_id, type, severity, status, resource_type, resource_id, payload_json, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, alert.recipientUserId());
            statement.setString(2, alert.type());
            statement.setString(3, alert.severity());
            statement.setString(4, alert.status());
            statement.setString(5, alert.resourceType());
            statement.setObject(6, alert.resourceId());
            statement.setString(7, writeJson(alert.payload()));
            return statement;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findByRecipient(alert.recipientUserId(), null, 1, 1).stream()
                .filter(item -> id.equals(item.id()))
                .findFirst()
                .orElse(alert.withId(id));
    }

    @Override
    public List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String normalizedStatus = status == null ? "" : status.trim();
        return jdbcTemplate.query("""
                        SELECT id, recipient_user_id, type, severity, status, resource_type, resource_id, payload_json, created_at
                        FROM system_alerts
                        WHERE recipient_user_id = ?
                          AND (? = '' OR status = ?)
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new SystemAlert(
                        rs.getLong("id"),
                        rs.getLong("recipient_user_id"),
                        rs.getString("type"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        readJson(rs.getString("payload_json")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                recipientUserId,
                normalizedStatus,
                normalizedStatus,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countByRecipient(Long recipientUserId, String status) {
        String normalizedStatus = status == null ? "" : status.trim();
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM system_alerts
                        WHERE recipient_user_id = ?
                          AND (? = '' OR status = ?)
                        """,
                Long.class,
                recipientUserId,
                normalizedStatus,
                normalizedStatus
        );
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsUnreadByDedupeKey(Long recipientUserId,
                                           String type,
                                           String resourceType,
                                           Long resourceId,
                                           String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM system_alerts
                        WHERE recipient_user_id = ?
                          AND type = ?
                          AND status = 'unread'
                          AND resource_type = ?
                          AND resource_id = ?
                          AND payload_json ->> 'dedupeKey' = ?
                        """,
                Long.class,
                recipientUserId,
                type,
                resourceType,
                resourceId,
                dedupeKey
        );
        return count != null && count > 0;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize system alert payload", ex);
        }
    }

    private Map<String, Object> readJson(String payload) {
        try {
            return payload == null || payload.isBlank() ? Map.of() : objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read system alert payload", ex);
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
