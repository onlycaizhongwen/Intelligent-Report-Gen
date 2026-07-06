package com.company.report.audit.infrastructure.persistence;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
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
import java.util.Optional;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private static final TypeReference<Map<String, Object>> DETAIL_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public OperationLog save(OperationLog log) {
        if (log.id() != null && log.id() > 0) {
            return log;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO operation_logs
                    (actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setObject(1, log.actorUserId());
            ps.setString(2, log.operationType());
            ps.setString(3, log.resourceType());
            ps.setObject(4, log.resourceId());
            ps.setString(5, log.result());
            ps.setString(6, writeJson(log.detail()));
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<OperationLog> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at
                        FROM operation_logs
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("operation_type"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        rs.getString("result"),
                        readJson(rs.getString("detail")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                id
        ).stream().findFirst();
    }

    @Override
    public List<OperationLog> findPage(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at
                        FROM operation_logs
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("operation_type"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        rs.getString("result"),
                        readJson(rs.getString("detail")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public List<OperationLog> findPageByActor(Long actorUserId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at
                        FROM operation_logs
                        WHERE actor_user_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("operation_type"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        rs.getString("result"),
                        readJson(rs.getString("detail")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                actorUserId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_logs", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countByActor(Long actorUserId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE actor_user_id = ?", Long.class, actorUserId);
        return count == null ? 0L : count;
    }

    @Override
    public long countByResourceOperationAndDetailSince(String resourceType,
                                                       Long resourceId,
                                                       String operationType,
                                                       String detailKey,
                                                       String detailValue,
                                                       OffsetDateTime since) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM operation_logs
                        WHERE resource_type = ?
                          AND resource_id = ?
                          AND operation_type = ?
                          AND detail ->> ? = ?
                          AND created_at >= ?
                        """,
                Long.class,
                resourceType,
                resourceId,
                operationType,
                detailKey,
                detailValue,
                since
        );
        return count == null ? 0L : count;
    }

    @Override
    public long countByResourceOperationSince(String resourceType,
                                              Long resourceId,
                                              String operationType,
                                              OffsetDateTime since) {
        Long count;
        if (since == null) {
            count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM operation_logs
                            WHERE resource_type = ?
                              AND resource_id = ?
                              AND operation_type = ?
                            """,
                    Long.class,
                    resourceType,
                    resourceId,
                    operationType
            );
        } else {
            count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM operation_logs
                            WHERE resource_type = ?
                              AND resource_id = ?
                              AND operation_type = ?
                              AND created_at >= ?
                            """,
                    Long.class,
                    resourceType,
                    resourceId,
                    operationType,
                    since
            );
        }
        return count == null ? 0L : count;
    }

    @Override
    public List<OperationLog> findByResourceOperationAndDetail(String resourceType,
                                                               Long resourceId,
                                                               String operationType,
                                                               String detailKey,
                                                               String detailValue) {
        return jdbcTemplate.query("""
                        SELECT id, actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at
                        FROM operation_logs
                        WHERE resource_type = ?
                          AND resource_id = ?
                          AND operation_type = ?
                          AND detail ->> ? = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("operation_type"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        rs.getString("result"),
                        readJson(rs.getString("detail")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                resourceType,
                resourceId,
                operationType,
                detailKey,
                detailValue
        );
    }

    @Override
    public List<OperationLog> findByOperationAndDetail(String operationType,
                                                       String detailKey,
                                                       String detailValue) {
        return jdbcTemplate.query("""
                        SELECT id, actor_user_id, operation_type, resource_type, resource_id, result, detail, created_at
                        FROM operation_logs
                        WHERE operation_type = ?
                          AND detail ->> ? = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("operation_type"),
                        rs.getString("resource_type"),
                        nullableLong(rs.getObject("resource_id")),
                        rs.getString("result"),
                        readJson(rs.getString("detail")),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                operationType,
                detailKey,
                detailValue
        );
    }

    private String writeJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize audit detail", ex);
        }
    }

    private Map<String, Object> readJson(String detail) {
        try {
            return detail == null || detail.isBlank() ? Map.of() : objectMapper.readValue(detail, DETAIL_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read audit detail", ex);
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
