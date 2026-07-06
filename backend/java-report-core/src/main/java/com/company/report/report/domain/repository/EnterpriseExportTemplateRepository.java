package com.company.report.report.domain.repository;

import com.company.report.report.domain.model.EnterpriseExportTemplate;

import java.util.List;
import java.util.Optional;

public interface EnterpriseExportTemplateRepository {
    EnterpriseExportTemplate save(EnterpriseExportTemplate template);

    Optional<EnterpriseExportTemplate> findActiveByTemplateId(String templateId);

    Optional<EnterpriseExportTemplate> findLatestByTemplateId(String templateId);

    List<EnterpriseExportTemplate> findVersions(String templateId);

    List<EnterpriseExportTemplate> findPage(String status, int page, int pageSize);

    long count(String status);
}
