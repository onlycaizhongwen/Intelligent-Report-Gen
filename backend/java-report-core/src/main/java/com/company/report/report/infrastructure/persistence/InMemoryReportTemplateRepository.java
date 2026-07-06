package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.ReportTemplate;
import com.company.report.report.domain.repository.ReportTemplateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnMissingBean(ReportTemplateRepository.class)
public class InMemoryReportTemplateRepository implements ReportTemplateRepository {
    private final Map<String, ReportTemplate> templates = new LinkedHashMap<>();

    public InMemoryReportTemplateRepository() {
        save(defaultTemplate());
    }

    @Override
    public List<ReportTemplate> findActiveTemplates() {
        return templates.values().stream()
                .filter(template -> "active".equals(template.status()))
                .toList();
    }

    @Override
    public Optional<ReportTemplate> findActiveByTemplateId(String templateId) {
        ReportTemplate template = templates.get(templateId);
        if (template == null || !"active".equals(template.status())) {
            return Optional.empty();
        }
        return Optional.of(template);
    }

    public ReportTemplate save(ReportTemplate template) {
        templates.put(template.templateId(), template);
        return template;
    }

    private ReportTemplate defaultTemplate() {
        return new ReportTemplate(
                "enterprise-quarterly",
                "企业季度经营分析报告",
                "operations",
                "v1",
                "active",
                List.of(
                        new ReportTemplate.TemplateField("period", "报告周期", "text", true, List.of(), "2026Q1", "例如 2026Q1"),
                        new ReportTemplate.TemplateField("scope", "对象范围", "text", true, List.of(), "集团整体", "组织、区域或业务线"),
                        new ReportTemplate.TemplateField("focus", "关注重点", "textarea", true, List.of(), null, "管理层最关心的问题"),
                        new ReportTemplate.TemplateField("style", "报告风格", "select", true, List.of("管理摘要", "经营分析", "风险提示"), "管理摘要", "输出语气")
                ),
                Map.of("sections", List.of("执行摘要", "经营表现", "风险与建议")),
                OffsetDateTime.now()
        );
    }
}
