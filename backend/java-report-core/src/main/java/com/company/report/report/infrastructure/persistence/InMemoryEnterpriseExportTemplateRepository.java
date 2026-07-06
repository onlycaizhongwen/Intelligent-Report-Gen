package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.EnterpriseExportTemplate;
import com.company.report.report.domain.repository.EnterpriseExportTemplateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnMissingBean(EnterpriseExportTemplateRepository.class)
public class InMemoryEnterpriseExportTemplateRepository implements EnterpriseExportTemplateRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, EnterpriseExportTemplate> templates = new LinkedHashMap<>();

    @Override
    public synchronized EnterpriseExportTemplate save(EnterpriseExportTemplate template) {
        Long id = template.id() == null ? ids.getAndIncrement() : template.id();
        EnterpriseExportTemplate saved = template.withId(id);
        templates.put(id, saved);
        return saved;
    }

    @Override
    public synchronized Optional<EnterpriseExportTemplate> findActiveByTemplateId(String templateId) {
        return templates.values().stream()
                .filter(template -> template.templateId().equals(templateId))
                .filter(template -> "active".equals(template.status()))
                .max(Comparator.comparingInt(EnterpriseExportTemplate::version));
    }

    @Override
    public synchronized Optional<EnterpriseExportTemplate> findLatestByTemplateId(String templateId) {
        return templates.values().stream()
                .filter(template -> template.templateId().equals(templateId))
                .max(Comparator.comparingInt(EnterpriseExportTemplate::version));
    }

    @Override
    public synchronized List<EnterpriseExportTemplate> findVersions(String templateId) {
        return templates.values().stream()
                .filter(template -> template.templateId().equals(templateId))
                .sorted(Comparator.comparingInt(EnterpriseExportTemplate::version).reversed())
                .toList();
    }

    @Override
    public synchronized List<EnterpriseExportTemplate> findPage(String status, int page, int pageSize) {
        String requestedStatus = status == null ? "" : status;
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return templates.values().stream()
                .filter(template -> requestedStatus.isBlank() || requestedStatus.equals(template.status()))
                .collect(java.util.stream.Collectors.toMap(
                        EnterpriseExportTemplate::templateId,
                        template -> template,
                        (left, right) -> left.version() >= right.version() ? left : right,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(EnterpriseExportTemplate::templateId))
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long count(String status) {
        String requestedStatus = status == null ? "" : status;
        return templates.values().stream()
                .filter(template -> requestedStatus.isBlank() || requestedStatus.equals(template.status()))
                .map(EnterpriseExportTemplate::templateId)
                .distinct()
                .count();
    }
}
