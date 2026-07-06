package com.company.report.report.domain.repository;

import com.company.report.report.domain.model.ReportTemplate;

import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository {
    List<ReportTemplate> findActiveTemplates();

    Optional<ReportTemplate> findActiveByTemplateId(String templateId);
}
