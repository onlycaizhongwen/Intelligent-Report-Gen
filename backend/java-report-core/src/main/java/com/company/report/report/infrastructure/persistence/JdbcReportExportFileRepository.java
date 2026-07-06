package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.repository.ReportExportFileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcReportExportFileRepository implements ReportExportFileRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcReportExportFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> save(Map<String, Object> exportFile) {
        Long id = nullableLong(exportFile.get("exportFileId"));
        if (id == null || id <= 0) {
            return insert(exportFile);
        }
        int updated = jdbcTemplate.update("""
                        UPDATE report_export_files
                        SET status = ?, download_url = ?, expires_at = ?, brand_snapshot = ?::jsonb, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND report_id = ?
                        """,
                string(exportFile.get("status")),
                string(exportFile.get("downloadUrl")),
                timestamp(exportFile.get("expiresAt")),
                writeJson(exportFile.get("brandSnapshot")),
                id,
                nullableLong(exportFile.get("reportId"))
        );
        if (updated == 0) {
            return insert(exportFile);
        }
        return findByReportIdAndExportFileId(nullableLong(exportFile.get("reportId")), id).orElseThrow();
    }

    @Override
    public Optional<Map<String, Object>> findByReportIdAndExportFileId(Long reportId, Long exportFileId) {
        return queryByExportFileId(exportFileId)
                .filter(file -> reportId.equals(nullableLong(file.get("reportId"))));
    }

    @Override
    public Optional<Map<String, Object>> findByExportFileId(Long exportFileId) {
        return queryByExportFileId(exportFileId);
    }

    @Override
    public List<Map<String, Object>> findCompletedByReportId(Long reportId) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, status, format, template_id, download_policy, bucket, object_key,
                               file_name, content_type, size_bytes, download_url, expires_at, brand_snapshot::text, created_at
                        FROM report_export_files
                        WHERE report_id = ? AND status = 'completed'
                        ORDER BY created_at DESC, id DESC
                        """,
                (rs, rowNum) -> toMap(rs),
                reportId
        );
    }

    private Optional<Map<String, Object>> queryByExportFileId(Long exportFileId) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, status, format, template_id, download_policy, bucket, object_key,
                               file_name, content_type, size_bytes, download_url, expires_at, brand_snapshot::text, created_at
                        FROM report_export_files
                        WHERE id = ?
                        """,
                (rs, rowNum) -> toMap(rs),
                exportFileId
        ).stream().findFirst();
    }

    private Map<String, Object> toMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportFileId", rs.getLong("id"));
        result.put("reportId", rs.getLong("report_id"));
        result.put("status", rs.getString("status"));
        result.put("format", rs.getString("format"));
        result.put("templateId", rs.getString("template_id"));
        result.put("downloadPolicy", rs.getString("download_policy"));
        result.put("bucket", rs.getString("bucket"));
        result.put("objectKey", rs.getString("object_key"));
        result.put("fileName", rs.getString("file_name"));
        result.put("contentType", rs.getString("content_type"));
        result.put("sizeBytes", rs.getLong("size_bytes"));
        result.put("downloadUrl", rs.getString("download_url"));
        result.put("brandSnapshot", readMap(rs.getString("brand_snapshot")));
        OffsetDateTime expiresAt = rs.getObject("expires_at", OffsetDateTime.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        result.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        result.put("createdAt", createdAt == null ? null : createdAt.toString());
        return result;
    }

    private Map<String, Object> insert(Map<String, Object> exportFile) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO report_export_files
                    (report_id, status, format, template_id, download_policy, bucket, object_key,
                     file_name, content_type, size_bytes, download_url, expires_at, brand_snapshot, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, nullableLong(exportFile.get("reportId")));
            ps.setString(2, string(exportFile.get("status")));
            ps.setString(3, string(exportFile.get("format")));
            ps.setString(4, string(exportFile.get("templateId")));
            ps.setString(5, string(exportFile.get("downloadPolicy")));
            ps.setString(6, string(exportFile.get("bucket")));
            ps.setString(7, string(exportFile.get("objectKey")));
            ps.setString(8, string(exportFile.get("fileName")));
            ps.setString(9, string(exportFile.get("contentType")));
            ps.setLong(10, nullableLong(exportFile.get("sizeBytes")));
            ps.setString(11, string(exportFile.get("downloadUrl")));
            ps.setTimestamp(12, timestamp(exportFile.get("expiresAt")));
            ps.setString(13, writeJson(exportFile.get("brandSnapshot")));
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey().longValue();
        return findByReportIdAndExportFileId(nullableLong(exportFile.get("reportId")), id).orElseThrow();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Timestamp timestamp(Object value) {
        if (value == null) {
            return null;
        }
        return Timestamp.from(OffsetDateTime.parse(String.valueOf(value)).toInstant());
    }

    private String writeJson(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid export brand snapshot", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read export brand snapshot", ex);
        }
    }
}
