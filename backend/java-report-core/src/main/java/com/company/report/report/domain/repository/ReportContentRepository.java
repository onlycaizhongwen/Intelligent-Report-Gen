package com.company.report.report.domain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReportContentRepository {
    Long saveCompletedVersion(Long reportId, Long createdBy, List<Map<String, Object>> sections);

    List<Map<String, Object>> findCurrentSections(Long reportId);

    List<Map<String, Object>> findSectionsByVersion(Long reportId, Long versionId);

    Optional<Map<String, Object>> findReference(Long reportId, Long referenceId);

    List<Map<String, Object>> listVersions(Long reportId);

    void markCurrentVersion(Long reportId, Long versionId);

    Long createRollbackVersion(Long reportId, Long sourceVersionId, Long createdBy);
}
