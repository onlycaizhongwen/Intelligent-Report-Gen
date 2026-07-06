package com.company.report.audit.infrastructure.persistence;

import com.company.report.audit.domain.model.ModelInvocationAudit;
import com.company.report.audit.domain.model.ModelResponseAudit;
import com.company.report.audit.domain.repository.ModelInvocationRepository;
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
public class JdbcModelInvocationRepository implements ModelInvocationRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcModelInvocationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelInvocationAudit save(ModelInvocationAudit invocation) {
        Long invocationId = insertInvocation(invocation);
        ModelResponseAudit response = invocation.response();
        if (response != null) {
            insertResponse(response.withInvocationId(invocationId));
        }
        return findById(invocationId).orElseThrow();
    }

    @Override
    public Optional<ModelInvocationAudit> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, task_id, report_id, actor_user_id, provider, model_name,
                               prompt_template_id, prompt_snapshot, context_snapshot, parameters,
                               request_hash, status, duration_ms, input_tokens, output_tokens,
                               total_tokens, error_code, error_message, trace_id, audit_event_key, created_at
                        FROM model_invocations
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new ModelInvocationAudit(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("task_id")),
                        nullableLong(rs.getObject("report_id")),
                        nullableLong(rs.getObject("actor_user_id")),
                        rs.getString("provider"),
                        rs.getString("model_name"),
                        rs.getString("prompt_template_id"),
                        rs.getString("prompt_snapshot"),
                        rs.getString("context_snapshot"),
                        readJson(rs.getString("parameters")),
                        rs.getString("request_hash"),
                        rs.getString("status"),
                        nullableLong(rs.getObject("duration_ms")),
                        nullableInteger(rs.getObject("input_tokens")),
                        nullableInteger(rs.getObject("output_tokens")),
                        nullableInteger(rs.getObject("total_tokens")),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getString("trace_id"),
                        rs.getString("audit_event_key"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        findResponse(rs.getLong("id")).orElse(null)
                ),
                id
        ).stream().findFirst();
    }

    private Long insertInvocation(ModelInvocationAudit invocation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO model_invocations
                    (task_id, report_id, actor_user_id, provider, model_name, prompt_template_id,
                     prompt_snapshot, context_snapshot, parameters, request_hash, status, duration_ms,
                     input_tokens, output_tokens, total_tokens, error_code, error_message, trace_id,
                     audit_event_key, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (audit_event_key) DO UPDATE SET
                      status = EXCLUDED.status,
                      duration_ms = EXCLUDED.duration_ms,
                      input_tokens = EXCLUDED.input_tokens,
                      output_tokens = EXCLUDED.output_tokens,
                      total_tokens = EXCLUDED.total_tokens,
                      error_code = EXCLUDED.error_code,
                      error_message = EXCLUDED.error_message
                    RETURNING id
                    """, new String[]{"id"});
            ps.setObject(1, invocation.taskId());
            ps.setObject(2, invocation.reportId());
            ps.setObject(3, invocation.actorUserId());
            ps.setString(4, invocation.provider());
            ps.setString(5, invocation.modelName());
            ps.setString(6, invocation.promptTemplateId());
            ps.setString(7, invocation.promptSnapshot());
            ps.setString(8, invocation.contextSnapshot());
            ps.setString(9, writeJson(invocation.parameters()));
            ps.setString(10, invocation.requestHash());
            ps.setString(11, invocation.status());
            ps.setObject(12, invocation.durationMs());
            ps.setObject(13, invocation.inputTokens());
            ps.setObject(14, invocation.outputTokens());
            ps.setObject(15, invocation.totalTokens());
            ps.setString(16, invocation.errorCode());
            ps.setString(17, invocation.errorMessage());
            ps.setString(18, invocation.traceId());
            ps.setString(19, invocation.auditEventKey());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertResponse(ModelResponseAudit response) {
        jdbcTemplate.update("""
                        INSERT INTO model_responses
                        (invocation_id, response_order, response_content, response_metadata, finish_reason, created_at)
                        VALUES (?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)
                        """,
                response.invocationId(),
                response.responseOrder() == null ? 1 : response.responseOrder(),
                response.responseContent(),
                writeJson(response.responseMetadata()),
                response.finishReason()
        );
    }

    private Optional<ModelResponseAudit> findResponse(Long invocationId) {
        List<ModelResponseAudit> responses = jdbcTemplate.query("""
                        SELECT id, invocation_id, response_order, response_content, response_metadata, finish_reason, created_at
                        FROM model_responses
                        WHERE invocation_id = ?
                        ORDER BY response_order ASC, id ASC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new ModelResponseAudit(
                        rs.getLong("id"),
                        rs.getLong("invocation_id"),
                        rs.getInt("response_order"),
                        rs.getString("response_content"),
                        readJson(rs.getString("response_metadata")),
                        rs.getString("finish_reason"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                invocationId
        );
        return responses.stream().findFirst();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize model audit json", ex);
        }
    }

    private Map<String, Object> readJson(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read model audit json", ex);
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
