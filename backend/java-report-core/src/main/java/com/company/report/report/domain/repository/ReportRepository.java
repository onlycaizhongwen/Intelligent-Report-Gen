package com.company.report.report.domain.repository;

import com.company.report.report.domain.model.Report;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    Optional<Report> findById(Long id);
    Report save(Report report);
    List<Report> findByOwner(Long ownerUserId, int page, int pageSize);
    long countByOwner(Long ownerUserId);
}
