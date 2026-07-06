package com.company.report.report.domain.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record EnterpriseExportTemplate(
        Long id,
        String templateId,
        String name,
        int version,
        String status,
        Map<String, Object> brandSnapshot,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public String versionLabel() {
        return "v" + version;
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("templateId", templateId);
        response.put("name", name);
        response.put("version", versionLabel());
        response.put("status", status);
        response.put("brandSnapshot", brandSnapshot == null ? Map.of() : new LinkedHashMap<>(brandSnapshot));
        response.put("createdBy", createdBy);
        response.put("createdAt", createdAt == null ? null : createdAt.toString());
        response.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return response;
    }

    public EnterpriseExportTemplate withId(Long id) {
        return new EnterpriseExportTemplate(id, templateId, name, version, status, brandSnapshot, createdBy, createdAt, updatedAt);
    }

    public EnterpriseExportTemplate withStatus(String status, OffsetDateTime updatedAt) {
        return new EnterpriseExportTemplate(id, templateId, name, version, status, brandSnapshot, createdBy, createdAt, updatedAt);
    }
}
