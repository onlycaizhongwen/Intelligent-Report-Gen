package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.repository.ReportContentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcReportContentRepository implements ReportContentRepository {
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcReportContentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long saveCompletedVersion(Long reportId, Long createdBy, List<Map<String, Object>> sections) {
        jdbcTemplate.update("UPDATE report_versions SET is_current = FALSE WHERE report_id = ?", reportId);
        Long versionNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM report_versions WHERE report_id = ?",
                Long.class,
                reportId
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO report_versions (report_id, version_no, snapshot, created_by, change_reason, is_current, created_at)
                    VALUES (?, ?, ?::jsonb, ?, 'generation_completed', TRUE, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, reportId);
            ps.setLong(2, versionNo == null ? 1L : versionNo);
            ps.setString(3, writeJson(Map.of("sections", sections == null ? List.of() : sections)));
            ps.setLong(4, createdBy);
            return ps;
        }, keyHolder);
        Long versionId = keyHolder.getKey().longValue();
        replaceSections(reportId, versionId, sections == null ? List.of() : sections);
        return versionId;
    }

    @Override
    public List<Map<String, Object>> findCurrentSections(Long reportId) {
        return jdbcTemplate.query("""
                        SELECT id, version_id, section_no, heading, content, citation_marks::text
                        FROM report_sections
                        WHERE report_id = ?
                          AND deleted_at IS NULL
                          AND version_id = (SELECT current_version_id FROM reports WHERE id = ?)
                        ORDER BY section_no
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> section = new LinkedHashMap<>();
                    section.put("sectionId", rs.getLong("id"));
                    section.put("versionId", rs.getLong("version_id"));
                    section.put("sectionNo", rs.getInt("section_no"));
                    section.put("heading", rs.getString("heading"));
                    section.put("content", rs.getString("content"));
                    section.put("citations", readList(rs.getString("citation_marks")));
                    return section;
                },
                reportId,
                reportId
        );
    }

    @Override
    public List<Map<String, Object>> findSectionsByVersion(Long reportId, Long versionId) {
        Map<String, Object> snapshot = jdbcTemplate.query("""
                        SELECT snapshot::text
                        FROM report_versions
                        WHERE report_id = ? AND id = ?
                        """,
                (rs, rowNum) -> readMap(rs.getString("snapshot")),
                reportId,
                versionId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("report version not found: " + versionId));
        return sectionsFromSnapshot(snapshot);
    }

    @Override
    public Optional<Map<String, Object>> findReference(Long reportId, Long referenceId) {
        return jdbcTemplate.query("""
                        SELECT section_no, heading, content, citation_marks::text
                        FROM report_sections
                        WHERE report_id = ?
                          AND deleted_at IS NULL
                          AND version_id = (SELECT current_version_id FROM reports WHERE id = ?)
                        ORDER BY section_no
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> section = new LinkedHashMap<>();
                    section.put("sectionNo", rs.getInt("section_no"));
                    section.put("heading", rs.getString("heading"));
                    section.put("content", rs.getString("content"));
                    section.put("citations", readList(rs.getString("citation_marks")));
                    return section;
                },
                reportId,
                reportId
        ).stream()
                .map(section -> findReferenceInSection(reportId, referenceId, section))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public List<Map<String, Object>> listVersions(Long reportId) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, version_no, created_by, change_reason, is_current, created_at
                        FROM report_versions
                        WHERE report_id = ?
                        ORDER BY version_no DESC
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> version = new LinkedHashMap<>();
                    version.put("versionId", rs.getLong("id"));
                    version.put("reportId", rs.getLong("report_id"));
                    version.put("versionNo", rs.getLong("version_no"));
                    version.put("createdBy", rs.getLong("created_by"));
                    version.put("changeReason", rs.getString("change_reason"));
                    version.put("current", rs.getBoolean("is_current"));
                    version.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class).toString());
                    return version;
                },
                reportId
        );
    }

    @Override
    public void markCurrentVersion(Long reportId, Long versionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_versions WHERE report_id = ? AND id = ?",
                Long.class,
                reportId,
                versionId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("report version not found: " + versionId);
        }
        jdbcTemplate.update("UPDATE report_versions SET is_current = FALSE WHERE report_id = ?", reportId);
        jdbcTemplate.update("UPDATE report_versions SET is_current = TRUE WHERE report_id = ? AND id = ?", reportId, versionId);
    }

    @Override
    public Long createRollbackVersion(Long reportId, Long sourceVersionId, Long createdBy) {
        Map<String, Object> snapshot = jdbcTemplate.query("""
                        SELECT snapshot::text
                        FROM report_versions
                        WHERE report_id = ? AND id = ?
                        """,
                (rs, rowNum) -> readMap(rs.getString("snapshot")),
                reportId,
                sourceVersionId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("report version not found: " + sourceVersionId));
        List<Map<String, Object>> sections = sectionsFromSnapshot(snapshot);
        jdbcTemplate.update("UPDATE report_versions SET is_current = FALSE WHERE report_id = ?", reportId);
        Long versionNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM report_versions WHERE report_id = ?",
                Long.class,
                reportId
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO report_versions (report_id, version_no, snapshot, created_by, change_reason, is_current, created_at)
                    VALUES (?, ?, ?::jsonb, ?, 'rollback', TRUE, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, reportId);
            ps.setLong(2, versionNo == null ? 1L : versionNo);
            ps.setString(3, writeJson(Map.of("sections", sections, "rollbackSourceVersionId", sourceVersionId)));
            ps.setLong(4, createdBy);
            return ps;
        }, keyHolder);
        Long newVersionId = keyHolder.getKey().longValue();
        replaceSections(reportId, newVersionId, sections);
        return newVersionId;
    }

    private void replaceSections(Long reportId, Long versionId, List<Map<String, Object>> sections) {
        jdbcTemplate.update("UPDATE report_sections SET deleted_at = CURRENT_TIMESTAMP WHERE report_id = ? AND deleted_at IS NULL", reportId);
        int sectionNo = 1;
        for (Map<String, Object> section : sections) {
            jdbcTemplate.update("""
                            INSERT INTO report_sections (report_id, version_id, section_no, heading, content, citation_marks, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """,
                    reportId,
                    versionId,
                    sectionNo++,
                    String.valueOf(section.getOrDefault("heading", "未命名段落")),
                    String.valueOf(section.getOrDefault("content", "")),
                    writeJson(section.getOrDefault("citations", List.of()))
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize report content json", ex);
        }
    }

    private List<Object> readList(String value) {
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report content json", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to deserialize report version snapshot", ex);
        }
    }

    private List<Map<String, Object>> sectionsFromSnapshot(Map<String, Object> snapshot) {
        Object sections = snapshot.get("sections");
        if (!(sections instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(input -> {
                    Map<String, Object> section = new LinkedHashMap<>();
                    input.forEach((key, value) -> section.put(String.valueOf(key), value));
                    return section;
                })
                .toList();
    }

    private Optional<Map<String, Object>> findReferenceInSection(Long reportId, Long referenceId, Map<String, Object> section) {
        Object citations = section.get("citations");
        if (!(citations instanceof List<?> citationList)) {
            return Optional.empty();
        }
        for (Object citation : citationList) {
            if (citation instanceof Map<?, ?> citationMap && matchesReferenceId(referenceId, citationMap.get("referenceId"))) {
                Map<String, Object> reference = new LinkedHashMap<>();
                citationMap.forEach((key, value) -> reference.put(String.valueOf(key), value));
                reference.put("reportId", reportId);
                reference.put("referenceId", referenceId);
                reference.putIfAbsent("anchor", Map.of(
                        "sectionNo", section.get("sectionNo"),
                        "heading", section.get("heading"),
                        "text", section.get("content")
                ));
                return Optional.of(reference);
            }
        }
        return Optional.empty();
    }

    private boolean matchesReferenceId(Long expectedReferenceId, Object actualReferenceId) {
        if (actualReferenceId instanceof Number number) {
            return number.longValue() == expectedReferenceId;
        }
        return actualReferenceId != null && String.valueOf(expectedReferenceId).equals(String.valueOf(actualReferenceId));
    }
}
