package com.company.report.rule.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule.approval.supplement-attachment.external-av")
public record RuleApprovalSupplementAttachmentExternalAvProperties(
        boolean enabled,
        String host,
        int port,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        long maxScanSizeBytes
) {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3310;
    private static final int DEFAULT_TIMEOUT_MILLIS = 2000;
    private static final long DEFAULT_MAX_SCAN_SIZE_BYTES = 10L * 1024 * 1024;

    public RuleApprovalSupplementAttachmentExternalAvProperties {
        host = host == null || host.isBlank() ? DEFAULT_HOST : host;
        port = port <= 0 ? DEFAULT_PORT : port;
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? DEFAULT_TIMEOUT_MILLIS : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? DEFAULT_TIMEOUT_MILLIS : readTimeoutMillis;
        maxScanSizeBytes = maxScanSizeBytes <= 0 ? DEFAULT_MAX_SCAN_SIZE_BYTES : maxScanSizeBytes;
    }

    public static RuleApprovalSupplementAttachmentExternalAvProperties disabled() {
        return new RuleApprovalSupplementAttachmentExternalAvProperties(
                false,
                DEFAULT_HOST,
                DEFAULT_PORT,
                DEFAULT_TIMEOUT_MILLIS,
                DEFAULT_TIMEOUT_MILLIS,
                DEFAULT_MAX_SCAN_SIZE_BYTES
        );
    }
}
