package com.company.report.report.domain.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportTemplate(
        String templateId,
        String name,
        String category,
        String version,
        String status,
        List<TemplateField> fields,
        Map<String, Object> outlineSchema,
        OffsetDateTime updatedAt
) {
    public Map<String, Object> toResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("templateId", templateId);
        response.put("name", name);
        response.put("category", category);
        response.put("version", version);
        response.put("status", status);
        response.put("fields", fields == null ? List.of() : fields.stream().map(TemplateField::toResponse).toList());
        response.put("outlineSchema", outlineSchema == null ? Map.of() : outlineSchema);
        response.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return response;
    }

    public Map<String, Object> toSnapshot(Map<String, Object> parameters) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("templateId", templateId);
        snapshot.put("name", name);
        snapshot.put("category", category);
        snapshot.put("version", version);
        snapshot.put("fields", fields == null ? List.of() : fields.stream().map(TemplateField::toResponse).toList());
        snapshot.put("parameters", parameters == null ? Map.of() : new LinkedHashMap<>(parameters));
        return snapshot;
    }

    public record TemplateField(
            String fieldKey,
            String label,
            String type,
            boolean required,
            List<String> options,
            String defaultValue,
            String helpText
    ) {
        public Map<String, Object> toResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("fieldKey", fieldKey);
            response.put("label", label);
            response.put("type", type);
            response.put("required", required);
            response.put("options", options == null ? List.of() : options);
            response.put("defaultValue", defaultValue);
            response.put("helpText", helpText);
            return response;
        }
    }
}
