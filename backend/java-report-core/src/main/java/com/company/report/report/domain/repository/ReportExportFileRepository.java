package com.company.report.report.domain.repository;

import java.util.Map;
import java.util.Optional;
import java.util.List;

public interface ReportExportFileRepository {
    Map<String, Object> save(Map<String, Object> exportFile);

    Optional<Map<String, Object>> findByReportIdAndExportFileId(Long reportId, Long exportFileId);

    Optional<Map<String, Object>> findByExportFileId(Long exportFileId);

    List<Map<String, Object>> findCompletedByReportId(Long reportId);
}
