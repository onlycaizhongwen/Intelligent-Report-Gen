package com.company.report.knowledge.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApiDataSourceProfileCatalog {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> PROFILE_LIST = new TypeReference<>() {
    };
    private final Map<String, Profile> profiles;

    public ApiDataSourceProfileCatalog() {
        this(List.of());
    }

    public ApiDataSourceProfileCatalog(String customProfilesJson) {
        this(parseCustomProfiles(customProfilesJson));
    }

    public ApiDataSourceProfileCatalog(Collection<Profile> customProfiles) {
        Map<String, Profile> merged = new LinkedHashMap<>();
        builtInProfiles().forEach(profile -> merged.put(profile.profileId(), profile));
        if (customProfiles != null) {
            customProfiles.forEach(profile -> merged.put(profile.profileId(), profile));
        }
        this.profiles = Map.copyOf(merged);
    }

    public static ApiDataSourceProfileCatalog builtIn() {
        return new ApiDataSourceProfileCatalog();
    }

    public Optional<Profile> find(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(profileId.trim()));
    }

    public Profile require(String profileId) {
        return find(profileId)
                .orElseThrow(() -> new IllegalArgumentException("api data source fieldMapping.profileId is not supported: " + profileId));
    }

    private static List<Profile> builtInProfiles() {
        return List.of(
                new Profile(
                        "oa-documents",
                        "data.documents",
                        "documentNo",
                        "content",
                        "id",
                        "id",
                        "GET",
                        "bearer",
                        "",
                        Map.of()
                ),
                new Profile(
                        "finance-vouchers",
                        "data.vouchers",
                        "voucherNo",
                        "summary",
                        "voucherId",
                        "voucherId",
                        "POST",
                        "api_key",
                        "X-API-Key",
                        Map.of("X-Tenant", "finance")
                )
        );
    }

    private static List<Profile> parseCustomProfiles(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawJson, PROFILE_LIST).stream()
                    .map(ApiDataSourceProfileCatalog::profileFromMap)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("knowledge.data-source.profile-catalog-json must be a JSON array of profile definitions", ex);
        }
    }

    private static Profile profileFromMap(Map<String, Object> raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        Object rawHeaders = raw.get("headers");
        if (rawHeaders instanceof Map<?, ?> headerMap) {
            headerMap.forEach((key, value) -> headers.put(String.valueOf(key), String.valueOf(value)));
        } else if (rawHeaders != null) {
            throw new IllegalArgumentException("api data source profile headers must be an object");
        }
        return new Profile(
                stringValue(raw.get("profileId")),
                stringValue(raw.get("rowsPath")),
                stringValue(raw.get("titleField")),
                stringValue(raw.get("contentField")),
                stringValue(raw.get("cursorField")),
                stringValue(raw.getOrDefault("cursorColumn", raw.get("cursorField"))),
                stringValue(raw.getOrDefault("method", "GET")),
                stringValue(raw.getOrDefault("authType", "bearer")),
                stringValue(raw.getOrDefault("apiKeyHeader", "")),
                headers
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Profile(String profileId,
                          String rowsPath,
                          String titleField,
                          String contentField,
                          String cursorField,
                          String cursorColumn,
                          String method,
                          String authType,
                          String apiKeyHeader,
                          Map<String, String> headers) {
        public Profile {
            profileId = require(profileId, "profileId");
            rowsPath = require(rowsPath, "rowsPath");
            titleField = require(titleField, "titleField");
            contentField = require(contentField, "contentField");
            cursorField = require(cursorField, "cursorField");
            cursorColumn = cursorColumn == null || cursorColumn.isBlank() ? cursorField : cursorColumn.trim();
            method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
            authType = authType == null || authType.isBlank() ? "bearer" : authType.trim().toLowerCase();
            apiKeyHeader = apiKeyHeader == null ? "" : apiKeyHeader.trim();
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            if (!profileId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{1,127}")) {
                throw new IllegalArgumentException("api data source profile profileId contains invalid characters");
            }
            if (!List.of("GET", "POST").contains(method)) {
                throw new IllegalArgumentException("api data source profile method must be GET or POST");
            }
            if (!List.of("bearer", "api_key", "basic", "none").contains(authType)) {
                throw new IllegalArgumentException("api data source profile authType must be bearer, api_key, basic, or none");
            }
        }

        public Map<String, Object> canonicalMapping(Map<String, Object> originalMapping) {
            Map<String, Object> mapping = new LinkedHashMap<>(originalMapping);
            mapping.put("profileId", profileId);
            mapping.put("rowsPath", rowsPath);
            mapping.put("titleField", titleField);
            mapping.put("contentField", contentField);
            mapping.put("cursorField", cursorField);
            mapping.put("method", method);
            mapping.put("authType", authType);
            if (!apiKeyHeader.isBlank()) {
                mapping.put("apiKeyHeader", apiKeyHeader);
            }
            if (!headers.isEmpty()) {
                Map<String, Object> mergedHeaders = new LinkedHashMap<>();
                Object rawHeaders = mapping.get("headers");
                if (rawHeaders instanceof Map<?, ?> existingHeaders) {
                    existingHeaders.forEach((key, value) -> mergedHeaders.put(String.valueOf(key), value));
                }
                headers.forEach(mergedHeaders::put);
                mapping.put("headers", mergedHeaders);
            }
            return mapping;
        }

        private static String require(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("api data source profile " + fieldName + " is required");
            }
            return value.trim();
        }
    }
}
