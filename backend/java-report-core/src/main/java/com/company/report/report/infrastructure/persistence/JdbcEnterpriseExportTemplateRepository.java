package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.EnterpriseExportTemplate;
import com.company.report.report.domain.repository.EnterpriseExportTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Repository
public class JdbcEnterpriseExportTemplateRepository implements EnterpriseExportTemplateRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEnterpriseExportTemplateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public EnterpriseExportTemplate save(EnterpriseExportTemplate template) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO enterprise_export_templates (
                          template_id, name, version, status, brand_snapshot, created_by, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))
                        ON CONFLICT (template_id, version)
                        DO UPDATE SET
                          name = EXCLUDED.name,
                          status = EXCLUDED.status,
                          brand_snapshot = EXCLUDED.brand_snapshot,
                          updated_at = EXCLUDED.updated_at
                        RETURNING id
                        """,
                Long.class,
                template.templateId(),
                template.name(),
                template.version(),
                template.status(),
                writeJson(template.brandSnapshot()),
                template.createdBy(),
                template.createdAt(),
                template.updatedAt()
        );
        return findById(id).orElseThrow(() -> new IllegalStateException("enterprise export template save failed"));
    }

    @Override
    public Optional<EnterpriseExportTemplate> findActiveByTemplateId(String templateId) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, version, status, brand_snapshot::text, created_by, created_at, updated_at
                        FROM enterprise_export_templates
                        WHERE template_id = ? AND status = 'active' AND deleted_at IS NULL
                        ORDER BY version DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                templateId
        ).stream().findFirst();
    }

    @Override
    public Optional<EnterpriseExportTemplate> findLatestByTemplateId(String templateId) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, version, status, brand_snapshot::text, created_by, created_at, updated_at
                        FROM enterprise_export_templates
                        WHERE template_id = ? AND deleted_at IS NULL
                        ORDER BY version DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                templateId
        ).stream().findFirst();
    }

    @Override
    public List<EnterpriseExportTemplate> findVersions(String templateId) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, version, status, brand_snapshot::text, created_by, created_at, updated_at
                        FROM enterprise_export_templates
                        WHERE template_id = ? AND deleted_at IS NULL
                        ORDER BY version DESC
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                templateId
        );
    }

    @Override
    public List<EnterpriseExportTemplate> findPage(String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String requestedStatus = status == null ? "" : status;
        return jdbcTemplate.query("""
                        SELECT DISTINCT ON (template_id)
                          id, template_id, name, version, status, brand_snapshot::text, created_by, created_at, updated_at
                        FROM enterprise_export_templates
                        WHERE deleted_at IS NULL AND (? = '' OR status = ?)
                        ORDER BY template_id, version DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                requestedStatus,
                requestedStatus,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long count(String status) {
        String requestedStatus = status == null ? "" : status;
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT template_id)
                        FROM enterprise_export_templates
                        WHERE deleted_at IS NULL AND (? = '' OR status = ?)
                        """,
                Long.class,
                requestedStatus,
                requestedStatus
        );
        return count == null ? 0L : count;
    }

    private Optional<EnterpriseExportTemplate> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, version, status, brand_snapshot::text, created_by, created_at, updated_at
                        FROM enterprise_export_templates
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapTemplate(rs),
                id
        ).stream().findFirst();
    }

    private EnterpriseExportTemplate mapTemplate(ResultSet rs) throws SQLException {
        return new EnterpriseExportTemplate(
                rs.getLong("id"),
                rs.getString("template_id"),
                rs.getString("name"),
                rs.getInt("version"),
                rs.getString("status"),
                readMap(rs.getString("brand_snapshot")),
                rs.getObject("created_by", Long.class),
                rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize enterprise export template brand snapshot", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize enterprise export template brand snapshot", ex);
        }
    }
}
