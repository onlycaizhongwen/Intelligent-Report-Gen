package com.company.report.knowledge.infrastructure.persistence;

import com.company.report.knowledge.domain.model.KnowledgeBase;
import com.company.report.knowledge.domain.model.KnowledgeDataSource;
import com.company.report.knowledge.domain.model.KnowledgeItem;
import com.company.report.knowledge.domain.repository.KnowledgeBaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.id() != null && knowledgeBase.id() > 0) {
            return knowledgeBase;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_bases(name, owner_user_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setString(1, knowledgeBase.name());
            statement.setLong(2, knowledgeBase.ownerUserId());
            statement.setString(3, knowledgeBase.status());
            return statement;
        }, keyHolder);
        return findById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeBase> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, name, owner_user_id, status
                        FROM knowledge_bases
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> new KnowledgeBase(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("owner_user_id"),
                        rs.getString("status")
                ),
                id
        ).stream().findFirst();
    }

    @Override
    public List<KnowledgeBase> findByOwner(Long ownerUserId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, name, owner_user_id, status
                        FROM knowledge_bases
                        WHERE owner_user_id = ? AND deleted_at IS NULL
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new KnowledgeBase(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("owner_user_id"),
                        rs.getString("status")
                ),
                ownerUserId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countByOwner(Long ownerUserId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM knowledge_bases
                        WHERE owner_user_id = ? AND deleted_at IS NULL
                        """,
                Long.class,
                ownerUserId
        );
        return count == null ? 0L : count;
    }

    @Override
    public KnowledgeItem saveItem(KnowledgeItem item) {
        if (item.id() != null && item.id() > 0) {
            return item;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_items(
                      knowledge_base_id, title, content, source_type, index_status, created_by, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, item.knowledgeBaseId());
            statement.setString(2, item.title());
            statement.setString(3, item.content());
            statement.setString(4, item.sourceType());
            statement.setString(5, item.indexStatus());
            statement.setLong(6, item.createdBy());
            return statement;
        }, keyHolder);
        return findItemById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
    }

    @Override
    public List<KnowledgeItem> searchItems(Long ownerUserId, String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String normalizedKeyword = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbcTemplate.query("""
                        SELECT i.id, i.knowledge_base_id, i.title, i.content, i.source_type, i.index_status, i.created_by
                        FROM knowledge_items i
                        JOIN knowledge_bases b ON b.id = i.knowledge_base_id
                        WHERE b.owner_user_id = ?
                          AND b.deleted_at IS NULL
                          AND i.deleted_at IS NULL
                          AND (? = '%%' OR i.title ILIKE ? OR i.content ILIKE ?)
                        ORDER BY i.updated_at DESC, i.id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapItem(rs),
                ownerUserId,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countItems(Long ownerUserId, String keyword) {
        String normalizedKeyword = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM knowledge_items i
                        JOIN knowledge_bases b ON b.id = i.knowledge_base_id
                        WHERE b.owner_user_id = ?
                          AND b.deleted_at IS NULL
                          AND i.deleted_at IS NULL
                          AND (? = '%%' OR i.title ILIKE ? OR i.content ILIKE ?)
                        """,
                Long.class,
                ownerUserId,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword
        );
        return count == null ? 0L : count;
    }

    @Override
    public Optional<KnowledgeItem> findItemById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, knowledge_base_id, title, content, source_type, index_status, created_by
                        FROM knowledge_items
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> mapItem(rs),
                id
        ).stream().findFirst();
    }

    @Override
    public boolean softDeleteItem(Long itemId) {
        return jdbcTemplate.update("""
                        UPDATE knowledge_items
                        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                itemId
        ) > 0;
    }

    @Override
    public long countReportReferences(Long itemId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM report_sections s
                        CROSS JOIN LATERAL jsonb_array_elements(s.citation_marks) citation
                        WHERE s.deleted_at IS NULL
                          AND citation ->> 'knowledgeItemId' = ?
                        """,
                Long.class,
                String.valueOf(itemId)
        );
        return count == null ? 0L : count;
    }

    @Override
    public KnowledgeDataSource saveDataSource(KnowledgeDataSource dataSource) {
        if (dataSource.id() != null && dataSource.id() > 0) {
            jdbcTemplate.update("""
                            UPDATE knowledge_data_sources
                            SET name = ?, source_type = ?, endpoint = ?, username = ?, credential_secret = ?,
                                knowledge_base_id = ?, sync_query = ?, field_mapping_json = ?, cursor_column = ?, last_cursor = ?,
                                schedule_enabled = ?, schedule_interval_seconds = ?, next_run_at = ?, failure_count = ?, max_retry_count = ?,
                                status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL
                            """,
                    dataSource.name(),
                    dataSource.sourceType(),
                    dataSource.endpoint(),
                    dataSource.username(),
                    dataSource.credentialSecret(),
                    dataSource.knowledgeBaseId(),
                    dataSource.syncQuery(),
                    dataSource.fieldMappingJson(),
                    dataSource.cursorColumn(),
                    dataSource.lastCursor(),
                    Boolean.TRUE.equals(dataSource.scheduleEnabled()),
                    dataSource.scheduleIntervalSeconds(),
                    timestamp(dataSource.nextRunAt()),
                    dataSource.failureCount() == null ? 0 : dataSource.failureCount(),
                    dataSource.maxRetryCount() == null ? 3 : dataSource.maxRetryCount(),
                    dataSource.status(),
                    dataSource.id(),
                    dataSource.ownerUserId()
            );
            return findDataSourceById(dataSource.id()).orElseThrow();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_data_sources(
                      owner_user_id, name, source_type, endpoint, username, credential_secret, knowledge_base_id,
                      sync_query, field_mapping_json, cursor_column, last_cursor, schedule_enabled, schedule_interval_seconds, next_run_at,
                      failure_count, max_retry_count, status, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, dataSource.ownerUserId());
            statement.setString(2, dataSource.name());
            statement.setString(3, dataSource.sourceType());
            statement.setString(4, dataSource.endpoint());
            statement.setString(5, dataSource.username());
            statement.setString(6, dataSource.credentialSecret());
            if (dataSource.knowledgeBaseId() == null) {
                statement.setObject(7, null);
            } else {
                statement.setLong(7, dataSource.knowledgeBaseId());
            }
            statement.setString(8, dataSource.syncQuery());
            statement.setString(9, dataSource.fieldMappingJson());
            statement.setString(10, dataSource.cursorColumn());
            statement.setString(11, dataSource.lastCursor());
            statement.setBoolean(12, Boolean.TRUE.equals(dataSource.scheduleEnabled()));
            if (dataSource.scheduleIntervalSeconds() == null) {
                statement.setObject(13, null);
            } else {
                statement.setInt(13, dataSource.scheduleIntervalSeconds());
            }
            statement.setTimestamp(14, timestamp(dataSource.nextRunAt()));
            statement.setInt(15, dataSource.failureCount() == null ? 0 : dataSource.failureCount());
            statement.setInt(16, dataSource.maxRetryCount() == null ? 3 : dataSource.maxRetryCount());
            statement.setString(17, dataSource.status());
            return statement;
        }, keyHolder);
        return findDataSourceById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeDataSource> findDataSourceById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, name, source_type, endpoint, username, credential_secret,
                               knowledge_base_id, sync_query, field_mapping_json, cursor_column, last_cursor, schedule_enabled,
                               schedule_interval_seconds, next_run_at, failure_count, max_retry_count, status
                        FROM knowledge_data_sources
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> mapDataSource(rs),
                id
        ).stream().findFirst();
    }

    @Override
    public KnowledgeDataSource updateDataSourceCursor(Long dataSourceId, String lastCursor) {
        jdbcTemplate.update("""
                        UPDATE knowledge_data_sources
                        SET last_cursor = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                lastCursor,
                dataSourceId
        );
        return findDataSourceById(dataSourceId).orElseThrow();
    }

    @Override
    public boolean tryAcquireDataSourceSyncLease(Long dataSourceId, OffsetDateTime lockedUntil) {
        return jdbcTemplate.update("""
                        UPDATE knowledge_data_sources
                        SET sync_locked_until = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND deleted_at IS NULL
                          AND (sync_locked_until IS NULL OR sync_locked_until <= CURRENT_TIMESTAMP)
                        """,
                timestamp(lockedUntil),
                dataSourceId
        ) > 0;
    }

    @Override
    public void releaseDataSourceSyncLease(Long dataSourceId) {
        jdbcTemplate.update("""
                        UPDATE knowledge_data_sources
                        SET sync_locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                dataSourceId
        );
    }

    @Override
    public List<KnowledgeDataSource> findDueScheduledDataSources(OffsetDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, name, source_type, endpoint, username, credential_secret,
                               knowledge_base_id, sync_query, field_mapping_json, cursor_column, last_cursor, schedule_enabled,
                               schedule_interval_seconds, next_run_at, failure_count, max_retry_count, status
                        FROM knowledge_data_sources
                        WHERE deleted_at IS NULL
                          AND status = 'enabled'
                          AND schedule_enabled = TRUE
                          AND next_run_at IS NOT NULL
                          AND next_run_at <= ?
                        ORDER BY next_run_at ASC, id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> mapDataSource(rs),
                timestamp(now),
                Math.max(limit, 1)
        );
    }

    @Override
    public KnowledgeDataSource updateDataSourceScheduleState(Long dataSourceId, OffsetDateTime nextRunAt, int failureCount) {
        jdbcTemplate.update("""
                        UPDATE knowledge_data_sources
                        SET next_run_at = ?, failure_count = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                timestamp(nextRunAt),
                failureCount,
                dataSourceId
        );
        return findDataSourceById(dataSourceId).orElseThrow();
    }

    @Override
    public Map<String, Object> saveDataSourceSyncRun(Map<String, Object> syncRun) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_data_source_sync_runs(
                      data_source_id, mode, status, processed_rows, failure_reason, message, last_cursor, started_at, finished_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, longValue(syncRun.get("dataSourceId")));
            statement.setString(2, string(syncRun.get("mode")));
            statement.setString(3, string(syncRun.get("status")));
            statement.setLong(4, longValue(syncRun.get("processedRows")));
            statement.setString(5, string(syncRun.get("failureReason")));
            statement.setString(6, string(syncRun.get("message")));
            statement.setString(7, string(syncRun.get("lastCursor")));
            statement.setTimestamp(8, timestamp(syncRun.get("startedAt")));
            statement.setTimestamp(9, timestamp(syncRun.get("finishedAt")));
            return statement;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findDataSourceSyncRuns(longValue(syncRun.get("dataSourceId")), 1, 1).stream()
                .filter(run -> id.equals(run.get("syncRunId")))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> fallback = new LinkedHashMap<>(syncRun);
                    fallback.put("syncRunId", id);
                    return fallback;
                });
    }

    @Override
    public List<Map<String, Object>> findDataSourceSyncRuns(Long dataSourceId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, data_source_id, mode, status, processed_rows, failure_reason, message, last_cursor, started_at, finished_at
                        FROM knowledge_data_source_sync_runs
                        WHERE data_source_id = ?
                        ORDER BY started_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("syncRunId", rs.getLong("id"));
                    item.put("dataSourceId", rs.getLong("data_source_id"));
                    item.put("mode", rs.getString("mode"));
                    item.put("status", rs.getString("status"));
                    item.put("processedRows", rs.getLong("processed_rows"));
                    item.put("failureReason", rs.getString("failure_reason") == null ? "" : rs.getString("failure_reason"));
                    item.put("message", rs.getString("message") == null ? "" : rs.getString("message"));
                    item.put("lastCursor", rs.getString("last_cursor") == null ? "" : rs.getString("last_cursor"));
                    item.put("startedAt", rs.getObject("started_at", OffsetDateTime.class).toString());
                    OffsetDateTime finishedAt = rs.getObject("finished_at", OffsetDateTime.class);
                    item.put("finishedAt", finishedAt == null ? "" : finishedAt.toString());
                    return item;
                },
                dataSourceId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countDataSourceSyncRuns(Long dataSourceId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM knowledge_data_source_sync_runs
                        WHERE data_source_id = ?
                        """,
                Long.class,
                dataSourceId
        );
        return count == null ? 0L : count;
    }

    private KnowledgeItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KnowledgeItem(
                rs.getLong("id"),
                rs.getLong("knowledge_base_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getString("index_status"),
                rs.getLong("created_by")
        );
    }

    private KnowledgeDataSource mapDataSource(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp nextRunAt = rs.getTimestamp("next_run_at");
        return new KnowledgeDataSource(
                rs.getLong("id"),
                rs.getLong("owner_user_id"),
                rs.getString("name"),
                rs.getString("source_type"),
                rs.getString("endpoint"),
                rs.getString("username"),
                rs.getString("credential_secret"),
                nullableLong(rs.getObject("knowledge_base_id")),
                rs.getString("sync_query"),
                rs.getString("field_mapping_json"),
                rs.getString("cursor_column"),
                rs.getString("last_cursor"),
                rs.getBoolean("schedule_enabled"),
                nullableInteger(rs.getObject("schedule_interval_seconds")),
                nextRunAt == null ? null : nextRunAt.toInstant().atOffset(java.time.ZoneOffset.UTC),
                nullableInteger(rs.getObject("failure_count")) == null ? 0 : nullableInteger(rs.getObject("failure_count")),
                nullableInteger(rs.getObject("max_retry_count")) == null ? 3 : nullableInteger(rs.getObject("max_retry_count")),
                rs.getString("status")
        );
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private Integer nullableInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private Timestamp timestamp(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Timestamp.from(OffsetDateTime.parse(String.valueOf(value)).toInstant());
    }
}
