package com.company.report.report.domain.model;

public record Report(Long id, String title, Long ownerUserId, ReportStatus status, Long currentVersionId) {
    public Report(Long id, String title, Long ownerUserId, ReportStatus status) {
        this(id, title, ownerUserId, status, null);
    }
}
