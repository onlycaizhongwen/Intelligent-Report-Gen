package com.company.report.audit.infrastructure.persistence;

import com.company.report.audit.domain.repository.DashboardMetricsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcDashboardMetricsRepository implements DashboardMetricsRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDashboardMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> collect(OffsetDateTime since) {
        return collectScoped(since, null);
    }

    @Override
    public Map<String, Object> collect(OffsetDateTime since, Long actorUserId) {
        return collectScoped(since, actorUserId);
    }

    private Map<String, Object> collectScoped(OffsetDateTime since, Long actorUserId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cards", cards(since, actorUserId));
        result.put("reportTrend", reportTrend(since, actorUserId));
        result.put("knowledgeRank", knowledgeRank(since, actorUserId));
        result.put("ruleScheduleHealth", ruleScheduleHealth(since));
        return result;
    }

    private Map<String, Object> cards(OffsetDateTime since, Long actorUserId) {
        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("reportOutputs", count("""
                SELECT COUNT(*) FROM reports
                WHERE deleted_at IS NULL
                  AND status = 'completed'
                  AND (?::timestamptz IS NULL OR updated_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR owner_user_id = ?::bigint)
                """, since, actorUserId));
        cards.put("knowledgeItems", count("""
                SELECT COUNT(*) FROM knowledge_items ki
                JOIN knowledge_bases kb ON kb.id = ki.knowledge_base_id
                WHERE ki.deleted_at IS NULL
                  AND kb.deleted_at IS NULL
                  AND (?::timestamptz IS NULL OR ki.created_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR kb.owner_user_id = ?::bigint)
                """, since, actorUserId));
        cards.put("activeDataSources", count("""
                SELECT COUNT(*) FROM knowledge_data_sources
                WHERE deleted_at IS NULL
                  AND status = 'enabled'
                  AND (?::timestamptz IS NULL OR updated_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR owner_user_id = ?::bigint)
                """, since, actorUserId));
        cards.put("citationHitRate", citationHitRate(since, actorUserId));
        cards.put("activeUsers", count("""
                SELECT COUNT(DISTINCT actor_user_id) FROM operation_logs
                WHERE actor_user_id IS NOT NULL
                  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR actor_user_id = ?::bigint)
                """, since, actorUserId));
        return cards;
    }

    private List<Map<String, Object>> reportTrend(OffsetDateTime since, Long actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT to_char(date_trunc('day', updated_at), 'YYYY-MM-DD') AS date,
                       COUNT(*) AS completed_reports
                FROM reports
                WHERE deleted_at IS NULL
                  AND status = 'completed'
                  AND (?::timestamptz IS NULL OR updated_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR owner_user_id = ?::bigint)
                GROUP BY date_trunc('day', updated_at)
                ORDER BY date_trunc('day', updated_at)
                """, timestamp(since), timestamp(since), actorUserId, actorUserId).stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", row.get("date"));
                    item.put("completedReports", longValue(row.get("completed_reports")));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> knowledgeRank(OffsetDateTime since, Long actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT kb.id AS knowledge_base_id,
                       kb.name AS name,
                       COUNT(*) AS reference_count
                FROM report_sections rs
                CROSS JOIN LATERAL jsonb_array_elements(rs.citation_marks) citation
                JOIN knowledge_items ki ON ki.id = NULLIF(citation->>'knowledgeItemId', '')::BIGINT
                JOIN knowledge_bases kb ON kb.id = ki.knowledge_base_id
                WHERE rs.deleted_at IS NULL
                  AND ki.deleted_at IS NULL
                  AND kb.deleted_at IS NULL
                  AND (?::timestamptz IS NULL OR rs.created_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR kb.owner_user_id = ?::bigint)
                GROUP BY kb.id, kb.name
                ORDER BY reference_count DESC, kb.id
                LIMIT 10
                """, timestamp(since), timestamp(since), actorUserId, actorUserId).stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("knowledgeBaseId", longValue(row.get("knowledge_base_id")));
                    item.put("name", row.get("name"));
                    item.put("references", longValue(row.get("reference_count")));
                    return item;
                })
                .toList();
    }

    private double citationHitRate(OffsetDateTime since, Long actorUserId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total_sections,
                       COUNT(*) FILTER (WHERE jsonb_array_length(citation_marks) > 0) AS cited_sections
                FROM report_sections rs
                JOIN reports r ON r.id = rs.report_id
                WHERE rs.deleted_at IS NULL
                  AND r.deleted_at IS NULL
                  AND (?::timestamptz IS NULL OR rs.created_at >= ?::timestamptz)
                  AND (?::bigint IS NULL OR r.owner_user_id = ?::bigint)
                """, timestamp(since), timestamp(since), actorUserId, actorUserId);
        long total = longValue(row.get("total_sections"));
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) longValue(row.get("cited_sections")) / total * 10_000.0) / 10_000.0;
    }

    private Map<String, Object> ruleScheduleHealth(OffsetDateTime since) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE schedule_enabled = TRUE AND status = 'published') AS scheduled_rules,
                       COUNT(*) FILTER (
                         WHERE schedule_enabled = TRUE
                           AND status = 'published'
                           AND failure_count > 0
                       ) AS failed_scheduled_rules,
                       COUNT(*) FILTER (
                         WHERE schedule_enabled = TRUE
                           AND status = 'published'
                           AND failure_count >= max_retry_count
                       ) AS blocked_scheduled_rules
                FROM rules
                WHERE deleted_at IS NULL
                """);
        long recentAlerts = countAlerts("""
                SELECT COUNT(*)
                FROM system_alerts
                WHERE type = 'rule_schedule_run_failed'
                  AND status = 'unread'
                  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
                """, since);
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("scheduledRules", longValue(row.get("scheduled_rules")));
        health.put("failedScheduledRules", longValue(row.get("failed_scheduled_rules")));
        health.put("blockedScheduledRules", longValue(row.get("blocked_scheduled_rules")));
        health.put("recentAlerts", recentAlerts);
        return health;
    }

    private long count(String sql, OffsetDateTime since, Long actorUserId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, timestamp(since), timestamp(since), actorUserId, actorUserId);
        return value == null ? 0L : value;
    }

    private long countAlerts(String sql, OffsetDateTime since) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, timestamp(since), timestamp(since));
        return value == null ? 0L : value;
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
