package com.company.report.report.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("报告领域服务单元测试")
class ReportDomainServiceTest {
    private final ReportDomainService service = new ReportDomainService();

    @Test
    @DisplayName("REQ-REPORT-002：大纲已确认时允许进入正文生成")
    void ensureOutlineConfirmed_whenConfirmed_thenPasses() {
        assertDoesNotThrow(() -> service.ensureOutlineConfirmed(Map.of("confirmed", true)));
    }

    @Test
    @DisplayName("REQ-REPORT-002：大纲未确认时拒绝生成正文")
    void ensureOutlineConfirmed_whenNotConfirmed_thenThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureOutlineConfirmed(Map.of("confirmed", false)));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.OUTLINE_NOT_CONFIRMED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-REPORT-004：企业导出模板缺失时拒绝导出")
    void ensureExportTemplateAvailable_whenMissing_thenThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureExportTemplateAvailable(null));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.EXPORT_TEMPLATE_MISSING.code(), ex.code());
    }
}
