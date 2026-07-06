package com.company.report.knowledge.domain.model;

public record StoredDocument(
        Long documentId,
        Long fileObjectId,
        Long knowledgeBaseId,
        String documentTitle,
        String fileType,
        String parseStatus,
        String bucket,
        String objectKey,
        String contentType,
        long sizeBytes,
        String parseFailureReason
) {
    public StoredDocument(
            Long documentId,
            Long fileObjectId,
            Long knowledgeBaseId,
            String documentTitle,
            String fileType,
            String parseStatus,
            String bucket,
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
        this(documentId, fileObjectId, knowledgeBaseId, documentTitle, fileType, parseStatus, bucket, objectKey, contentType, sizeBytes, null);
    }
}
