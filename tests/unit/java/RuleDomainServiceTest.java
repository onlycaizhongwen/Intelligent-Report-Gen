package com.company.report.rule.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("规则领域服务单元测试")
class RuleDomainServiceTest {
    private final RuleDomainService service = new RuleDomainService();

    @Test
    @DisplayName("REQ-RULE-001：调试输入为空时拒绝运行")
    void ensureDebugInputValid_whenEmpty_thenThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureDebugInputValid(Map.of()));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001：调试输入有效时允许运行")
    void ensureDebugInputValid_whenValid_thenPasses() {
        assertDoesNotThrow(() -> service.ensureDebugInputValid(Map.of("nodeId", "start")));
    }
}
