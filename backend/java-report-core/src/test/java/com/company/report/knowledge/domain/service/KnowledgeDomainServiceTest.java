package com.company.report.knowledge.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("知识库领域服务单元测试")
class KnowledgeDomainServiceTest {
    private final KnowledgeDomainService service = new KnowledgeDomainService();

    @Test
    @DisplayName("REQ-KB-001：被引用知识条目未确认时禁止删除")
    void ensureDeleteConfirmedWhenReferenced_whenReferencedAndNotConfirmed_thenThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureDeleteConfirmedWhenReferenced(true, false));

        assertEquals(409, ex.statusCode());
        assertEquals(ErrorCode.KNOWLEDGE_ITEM_REFERENCED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-KB-001：被引用知识条目已确认时允许删除")
    void ensureDeleteConfirmedWhenReferenced_whenConfirmed_thenPasses() {
        assertDoesNotThrow(() -> service.ensureDeleteConfirmedWhenReferenced(true, true));
    }
}
