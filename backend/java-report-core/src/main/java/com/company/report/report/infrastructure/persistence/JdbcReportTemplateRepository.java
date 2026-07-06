package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.ReportTemplate;
import com.company.report.report.domain.repository.ReportTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Repository
public class JdbcReportTemplateRepository implements ReportTemplateRepository {
    private static final TypeReference<List<ReportTemplate.TemplateField>> FIELD_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcReportTemplateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ReportTemplate> findActiveTemplates() {
        return jdbcTemplate.query("""
                        SELECT template_id, name, category, version, status, field_schema::text, outline_schema::text, updated_at
                        FROM report_templates
                        WHERE status = 'active' AND deleted_at IS NULL
                        ORDER BY category, name
                        """,
                (rs, rowNum) -> mapTemplate(rs)
        );
    }

    @Override
    public Optional<ReportTemplate> findActiveByTemplateId(String templateId) {
        return jdbcTemplate.query("""
                        SELECT template_id, name, category, version, status, field_schema::text, outline_schema::text, updated_at
                        FROM report_templates
                        WHERE template_id = ? AND status = 'active' AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                templateId
        ).stream().findFirst();
    }

    private ReportTemplate mapTemplate(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReportTemplate(
                rs.getString("template_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("version"),
                rs.getString("status"),
                readFields(rs.getString("field_schema")),
                readMap(rs.getString("outline_schema")),
                rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC)
        );
    }

    private List<ReportTemplate.TemplateField> readFields(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, FIELD_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report template fields", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report template outline", ex);
        }
    }
}
