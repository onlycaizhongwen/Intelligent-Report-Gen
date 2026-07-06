package com.company.report.knowledge.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDomainService {
    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 删除被引用知识条目 */
    public void ensureDeleteConfirmedWhenReferenced(boolean referenced, boolean confirmed) {
        if (referenced && !confirmed) {
            throw new BusinessException(409, ErrorCode.KNOWLEDGE_ITEM_REFERENCED);
        }
    }
}
