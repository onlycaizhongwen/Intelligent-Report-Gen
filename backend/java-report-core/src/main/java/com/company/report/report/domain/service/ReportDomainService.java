package com.company.report.report.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReportDomainService {
    /** OpenSpec: report-generation / REQ-REPORT-002 / 未确认大纲不得生成正文。 */
    public void ensureOutlineConfirmed(Map<String, Object> outlineRequest) {
        Object confirmed = outlineRequest.get("confirmed");
        if (!(confirmed instanceof Boolean value) || !value) {
            throw new BusinessException(422, ErrorCode.OUTLINE_NOT_CONFIRMED);
        }
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / 企业模板缺失。 */
    public void ensureExportTemplateAvailable(Object templateId) {
        if (templateId == null || String.valueOf(templateId).isBlank()) {
            throw new BusinessException(422, ErrorCode.EXPORT_TEMPLATE_MISSING);
        }
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / 企业品牌模板字段校验。 */
    public void ensureEnterpriseBrandTemplate(Object brand) {
        if (!(brand instanceof Map<?, ?> values)) {
            throw new BusinessException(422, ErrorCode.EXPORT_TEMPLATE_MISSING);
        }
        for (String key : List.of("companyName", "logoObjectKey", "header", "footer", "fontFamily", "primaryColor")) {
            Object value = values.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BusinessException(422, ErrorCode.EXPORT_TEMPLATE_MISSING);
            }
        }
    }
}
