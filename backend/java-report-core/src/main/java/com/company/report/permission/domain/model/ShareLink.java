package com.company.report.permission.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record ShareLink(
        Long id,
        Long reportId,
        Long createdBy,
        String shareToken,
        String status,
        String passwordHash,
        boolean allowDownload,
        List<String> allowedDownloadFormats,
        int maxAccessCount,
        List<String> allowedVisitors,
        List<String> allowedVisitorDomains,
        boolean singleUse,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
    public static ShareLink newLink(Long reportId, Long createdBy, String shareToken, OffsetDateTime expiresAt) {
        return newLink(reportId, createdBy, shareToken, null, false, List.of(), 0, List.of(), List.of(), false, expiresAt);
    }

    public static ShareLink newLink(Long reportId, Long createdBy, String shareToken, String passwordHash, OffsetDateTime expiresAt) {
        return newLink(reportId, createdBy, shareToken, passwordHash, false, List.of(), 0, List.of(), List.of(), false, expiresAt);
    }

    public static ShareLink newLink(Long reportId, Long createdBy, String shareToken, String passwordHash, boolean allowDownload, OffsetDateTime expiresAt) {
        return newLink(reportId, createdBy, shareToken, passwordHash, allowDownload, List.of(), 0, List.of(), List.of(), false, expiresAt);
    }

    public static ShareLink newLink(Long reportId,
                                    Long createdBy,
                                    String shareToken,
                                    String passwordHash,
                                    boolean allowDownload,
                                    List<String> allowedDownloadFormats,
                                    int maxAccessCount,
                                    List<String> allowedVisitors,
                                    List<String> allowedVisitorDomains,
                                    boolean singleUse,
                                    OffsetDateTime expiresAt) {
        return new ShareLink(null, reportId, createdBy, shareToken, "active", passwordHash, allowDownload, normalizeValues(allowedDownloadFormats), Math.max(maxAccessCount, 0), normalizeValues(allowedVisitors), normalizeDomains(allowedVisitorDomains), singleUse, expiresAt, null);
    }

    public ShareLink withId(Long id) {
        return new ShareLink(id, reportId, createdBy, shareToken, status, passwordHash, allowDownload, allowedDownloadFormats, maxAccessCount, allowedVisitors, allowedVisitorDomains, singleUse, expiresAt, createdAt);
    }

    public ShareLink revoke() {
        return new ShareLink(id, reportId, createdBy, shareToken, "revoked", passwordHash, allowDownload, allowedDownloadFormats, maxAccessCount, allowedVisitors, allowedVisitorDomains, singleUse, expiresAt, createdAt);
    }

    public boolean expiredAt(OffsetDateTime now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean allowsDownloadFormat(String format) {
        if (allowedDownloadFormats == null || allowedDownloadFormats.isEmpty()) {
            return true;
        }
        if (format == null || format.isBlank()) {
            return false;
        }
        return allowedDownloadFormats.contains(format.trim().toLowerCase());
    }

    public boolean restrictsVisitors() {
        return (allowedVisitors != null && !allowedVisitors.isEmpty())
                || (allowedVisitorDomains != null && !allowedVisitorDomains.isEmpty());
    }

    public boolean allowsVisitor(String visitor) {
        if (!restrictsVisitors()) {
            return true;
        }
        if (visitor == null || visitor.isBlank()) {
            return false;
        }
        String normalizedVisitor = visitor.trim().toLowerCase();
        if (allowedVisitors != null && allowedVisitors.contains(normalizedVisitor)) {
            return true;
        }
        int at = normalizedVisitor.lastIndexOf('@');
        if (at < 0 || at == normalizedVisitor.length() - 1) {
            return false;
        }
        String domain = normalizedVisitor.substring(at + 1);
        return allowedVisitorDomains != null && allowedVisitorDomains.contains(domain);
    }

    public boolean hasAccessCountLimit() {
        return maxAccessCount > 0;
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .distinct()
                .toList();
    }

    private static List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        return domains.stream()
                .filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.trim().toLowerCase())
                .map(domain -> domain.startsWith("@") ? domain.substring(1) : domain)
                .filter(domain -> !domain.isBlank())
                .distinct()
                .toList();
    }
}
