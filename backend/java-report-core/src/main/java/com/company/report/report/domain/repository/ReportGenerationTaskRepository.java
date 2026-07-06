package com.company.report.report.domain.repository;

import com.company.report.report.domain.model.ReportGenerationTask;

import java.util.Optional;

public interface ReportGenerationTaskRepository {
    ReportGenerationTask save(ReportGenerationTask task);

    Optional<ReportGenerationTask> findById(Long taskId);
}
