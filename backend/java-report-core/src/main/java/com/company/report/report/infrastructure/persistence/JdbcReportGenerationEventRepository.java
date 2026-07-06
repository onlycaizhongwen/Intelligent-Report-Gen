package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.repository.ReportGenerationEventRepository;
import com.company.report.shared.api.SseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcReportGenerationEventRepository implements ReportGenerationEventRepository {
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcReportGenerationEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(SseEvent event) {
        Integer sequenceNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM report_generation_events WHERE task_id = ?",
                Integer.class,
                Long.valueOf(event.taskId())
        );
        jdbcTemplate.update("""
                        INSERT INTO report_generation_events
                        (task_id, event_type, stage, content, references_payload, progress, error_code, trace_id, sequence_no, created_at)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                Long.valueOf(event.taskId()),
                event.type(),
                event.stage(),
                event.content(),
                writeJson(event.references()),
                event.progress(),
                event.errorCode(),
                event.traceId(),
                sequenceNo == null ? 1 : sequenceNo
        );
    }

    @Override
    public List<SseEvent> findByTaskId(Long taskId) {
        return jdbcTemplate.query("""
                        SELECT event_type, stage, content, references_payload::text, progress, error_code, trace_id
                        FROM report_generation_events
                        WHERE task_id = ?
                        ORDER BY sequence_no ASC, id ASC
                        """,
                (rs, rowNum) -> new SseEvent(
                        rs.getString("event_type"),
                        String.valueOf(taskId),
                        rs.getString("content"),
                        rs.getString("stage"),
                        readList(rs.getString("references_payload")),
                        rs.getObject("progress") == null ? null : rs.getDouble("progress"),
                        rs.getString("error_code"),
                        rs.getString("trace_id")
                ),
                taskId
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize report generation event json", ex);
        }
    }

    private List<Object> readList(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report generation event json", ex);
        }
    }
}
