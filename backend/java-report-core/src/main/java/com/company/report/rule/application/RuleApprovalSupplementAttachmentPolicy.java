package com.company.report.rule.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "rule.approval.supplement-attachment")
public record RuleApprovalSupplementAttachmentPolicy(
        Long maxSizeBytes,
        Set<String> allowedContentTypes
) {
    private static final long DEFAULT_MAX_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> DEFAULT_ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "text/csv",
            "text/markdown",
            "text/plain",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public RuleApprovalSupplementAttachmentPolicy {
        if (maxSizeBytes == null || maxSizeBytes <= 0L) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = DEFAULT_ALLOWED_CONTENT_TYPES;
        }
        allowedContentTypes = allowedContentTypes.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (allowedContentTypes.isEmpty()) {
            allowedContentTypes = DEFAULT_ALLOWED_CONTENT_TYPES;
        }
    }

    public static RuleApprovalSupplementAttachmentPolicy defaults() {
        return new RuleApprovalSupplementAttachmentPolicy(DEFAULT_MAX_SIZE_BYTES, DEFAULT_ALLOWED_CONTENT_TYPES);
    }
}
