package com.company.report.report.domain.repository;

import java.io.IOException;
import java.util.Optional;

public interface ReportExportStorage {
    StoredExport store(String objectKey, String fileName, String contentType, byte[] content) throws IOException;

    String createDownloadUrl(String objectKey) throws IOException;

    default Optional<StoredObject> readObject(String objectKey) throws IOException {
        return Optional.empty();
    }

    record StoredExport(
            String bucket,
            String objectKey,
            String fileName,
            String contentType,
            long sizeBytes,
            String downloadUrl
    ) {
    }

    record StoredObject(
            String bucket,
            String objectKey,
            String contentType,
            byte[] content
    ) {
    }
}
