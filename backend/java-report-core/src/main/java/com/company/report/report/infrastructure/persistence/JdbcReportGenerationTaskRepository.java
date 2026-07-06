package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.ReportGenerationTask;
import com.company.report.report.domain.repository.ReportGenerationTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcReportGenerationTaskRepository implements ReportGenerationTaskRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcReportGenerationTaskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReportGenerationTask save(ReportGenerationTask task) {
        if (task.id() == null) {
            return insert(task);
        }
        update(task);
        return task;
    }

    @Override
    public Optional<ReportGenerationTask> findById(Long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, created_by, generation_mode, user_input::text, template_snapshot::text,
                               status, current_stage, progress, failure_reason, trace_id, created_at, updated_at
                        FROM report_generation_tasks
                        WHERE id = ?
                        """,
                (rs, rowNum) -> {
                    Long id = rs.getLong("id");
                    ReportGenerationTask base = new ReportGenerationTask(
                            id,
                            nullableLong(rs.getObject("report_id")),
                            rs.getLong("created_by"),
                            rs.getString("generation_mode"),
                            readMap(rs.getString("user_input")),
                            readNullableMap(rs.getString("template_snapshot")),
                            rs.getString("status"),
                            rs.getString("current_stage"),
                            rs.getInt("progress"),
                            rs.getString("failure_reason"),
                            rs.getString("trace_id"),
                            findOutline(id),
                            rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC),
                            rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC)
                    );
                    return base;
                },
                taskId
        ).stream().findFirst();
    }

    private ReportGenerationTask insert(ReportGenerationTask task) {
        ReportGenerationTask taskToInsert = task.reportId() == null ? task.bindReport(createDraftReport(task)) : task;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO report_generation_tasks (
                      report_id, created_by, generation_mode, user_input, template_snapshot,
                      status, current_stage, progress, failure_reason, trace_id, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindTask(ps, taskToInsert);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey().longValue();
        ReportGenerationTask saved = taskToInsert.withId(id);
        upsertOutline(saved);
        return saved;
    }

    private Long createDraftReport(ReportGenerationTask task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO reports (title, report_type, owner_user_id, status, knowledge_scope, summary, created_at, updated_at)
                    VALUES (?, ?, ?, 'draft', ?::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setString(1, String.valueOf(task.outline().getOrDefault("title", "未命名报告")));
            ps.setString(2, task.generationMode());
            ps.setLong(3, task.createdBy());
            ps.setString(4, writeJson(Map.of("source", "generation_task")));
            ps.setString(5, null);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void update(ReportGenerationTask task) {
        jdbcTemplate.update("""
                        UPDATE report_generation_tasks
                        SET status = ?, current_stage = ?, progress = ?, failure_reason = ?, updated_at = ?
                        WHERE id = ?
                        """,
                task.status(),
                task.currentStage(),
                task.progress(),
                task.failureReason(),
                Timestamp.from(task.updatedAt().toInstant()),
                task.id()
        );
        upsertOutline(task);
    }

    private void bindTask(PreparedStatement ps, ReportGenerationTask task) throws java.sql.SQLException {
        ps.setObject(1, task.reportId());
        ps.setLong(2, task.createdBy());
        ps.setString(3, task.generationMode());
        ps.setString(4, writeJson(task.userInput()));
        ps.setString(5, task.templateSnapshot() == null ? null : writeJson(task.templateSnapshot()));
        ps.setString(6, task.status());
        ps.setString(7, task.currentStage());
        ps.setInt(8, task.progress());
        ps.setString(9, task.failureReason());
        ps.setString(10, task.traceId());
        ps.setTimestamp(11, Timestamp.from(task.createdAt().toInstant()));
        ps.setTimestamp(12, Timestamp.from(task.updatedAt().toInstant()));
    }

    private void upsertOutline(ReportGenerationTask task) {
        jdbcTemplate.update("""
                        INSERT INTO report_outlines (task_id, report_id, outline_content, confirmed, confirmed_by, confirmed_at, created_at, updated_at)
                        VALUES (?, ?, ?::jsonb, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (task_id)
                        DO UPDATE SET outline_content = EXCLUDED.outline_content,
                                      confirmed = EXCLUDED.confirmed,
                                      confirmed_by = EXCLUDED.confirmed_by,
                                      confirmed_at = EXCLUDED.confirmed_at,
                                      updated_at = CURRENT_TIMESTAMP
                        """,
                task.id(),
                task.reportId(),
                writeJson(task.outline()),
                Boolean.TRUE.equals(task.outline().get("confirmed")),
                Boolean.TRUE.equals(task.outline().get("confirmed")) ? task.createdBy() : null,
                Boolean.TRUE.equals(task.outline().get("confirmed")) ? Timestamp.from(OffsetDateTime.now().toInstant()) : null
        );
    }

    private Map<String, Object> findOutline(Long taskId) {
        return jdbcTemplate.query("""
                        SELECT outline_content::text FROM report_outlines WHERE task_id = ?
                        """,
                (rs, rowNum) -> readMap(rs.getString("outline_content")),
                taskId
        ).stream().findFirst().orElse(Map.of());
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize report task json", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report task json", ex);
        }
    }

    private Map<String, Object> readNullableMap(String value) {
        return value == null ? null : readMap(value);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
