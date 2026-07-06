package com.company.report.knowledge.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class MinioDocumentStorage implements DocumentStorage {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioDocumentStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public StoredObject store(MultipartFile file, String objectKey) throws IOException {
        ensureBucket();
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IOException("failed to store document in MinIO", ex);
        }
        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        return new StoredObject(properties.bucket(), objectKey, fileName, contentType, file.getSize());
    }

    private void ensureBucket() throws IOException {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception ex) {
            throw new IOException("failed to prepare MinIO bucket", ex);
        }
    }
}
