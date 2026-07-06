package com.company.report.knowledge.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentStorage {
    StoredObject store(MultipartFile file, String objectKey) throws IOException;

    record StoredObject(String bucket, String objectKey, String fileName, String contentType, long sizeBytes) {
    }
}
