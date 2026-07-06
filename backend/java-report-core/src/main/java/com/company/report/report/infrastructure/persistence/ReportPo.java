package com.company.report.report.infrastructure.persistence;

public record ReportPo(Long id, String title, Long ownerUserId, String status, Long currentVersionId) {
}
