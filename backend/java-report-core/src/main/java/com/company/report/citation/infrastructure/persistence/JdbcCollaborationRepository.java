package com.company.report.citation.infrastructure.persistence;

import com.company.report.citation.domain.model.Annotation;
import com.company.report.citation.domain.model.CollaborationNotification;
import com.company.report.citation.domain.model.CollaborationTask;
import com.company.report.citation.domain.repository.CollaborationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcCollaborationRepository implements CollaborationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCollaborationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Annotation saveAnnotation(Annotation annotation) {
        if (annotation.id() == null || annotation.id() <= 0) {
            return insertAnnotation(annotation);
        }
        jdbcTemplate.update("""
                        UPDATE report_annotations
                        SET content = ?, anchor_json = ?::jsonb, status = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                annotation.content(),
                writeJson(annotation.anchor()),
                annotation.status(),
                annotation.id()
        );
        return annotation;
    }

    @Override
    public CollaborationTask saveTask(CollaborationTask task) {
        if (task.id() == null || task.id() <= 0) {
            return insertTask(task);
        }
        jdbcTemplate.update("""
                        UPDATE collaboration_tasks
                        SET status = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                task.status(),
                task.id()
        );
        return task;
    }

    @Override
    public CollaborationNotification saveNotification(CollaborationNotification notification) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO collaboration_notifications(
                      recipient_user_id, actor_user_id, type, report_id, task_id, status, payload_json, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, notification.recipientUserId());
            statement.setLong(2, notification.actorUserId());
            statement.setString(3, notification.type());
            statement.setLong(4, notification.reportId());
            statement.setLong(5, notification.taskId());
            statement.setString(6, notification.status());
            statement.setString(7, writeJson(notification.payload()));
            return statement;
        }, keyHolder);
        return notification.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public Optional<CollaborationTask> findTaskById(Long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, annotation_id, assignee_user_id, status
                        FROM collaboration_tasks
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapTask(rs),
                taskId
        ).stream().findFirst();
    }

    private Annotation insertAnnotation(Annotation annotation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO report_annotations(
                      report_id, created_by, assignee_user_id, content, anchor_json, status, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, annotation.reportId());
            statement.setLong(2, annotation.createdBy());
            statement.setLong(3, annotation.assigneeUserId());
            statement.setString(4, annotation.content());
            statement.setString(5, writeJson(annotation.anchor()));
            statement.setString(6, annotation.status());
            return statement;
        }, keyHolder);
        return annotation.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    private CollaborationTask insertTask(CollaborationTask task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO collaboration_tasks(
                      report_id, annotation_id, assignee_user_id, status, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, task.reportId());
            statement.setLong(2, task.annotationId());
            statement.setLong(3, task.assigneeUserId());
            statement.setString(4, task.status());
            return statement;
        }, keyHolder);
        return task.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    private CollaborationTask mapTask(ResultSet rs) throws SQLException {
        return new CollaborationTask(
                rs.getLong("id"),
                rs.getLong("report_id"),
                rs.getLong("annotation_id"),
                rs.getLong("assignee_user_id"),
                rs.getString("status")
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to write collaboration json", e);
        }
    }
}
