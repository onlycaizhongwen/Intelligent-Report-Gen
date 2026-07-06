package com.company.report.knowledge.domain.repository;

import com.company.report.knowledge.domain.model.StoredDocument;

import java.util.Optional;

public interface KnowledgeDocumentRepository {
    StoredDocument saveUploadedDocument(UploadedDocumentRecord record);

    Optional<StoredDocument> findById(Long documentId);

    record UploadedDocumentRecord(
            Long knowledgeBaseId,
            Long uploadedBy,
            String bucket,
            String objectKey,
            String fileName,
            String contentType,
            long sizeBytes,
            String fileType,
            boolean ocrRequired,
            boolean tableRecognitionRequired
    ) {
    }
}
