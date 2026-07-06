package com.company.report.report.infrastructure.storage;

import com.company.report.knowledge.infrastructure.storage.MinioProperties;
import com.company.report.report.domain.repository.ReportExportStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class MinioReportExportStorage implements ReportExportStorage {
    private final MinioClient minioClient;
    private final MinioClient publicPresignClient;
    private final MinioProperties properties;

    @Autowired
    public MinioReportExportStorage(MinioClient minioClient, MinioProperties properties) {
        this(minioClient, buildPublicPresignClient(minioClient, properties), properties);
    }

    MinioReportExportStorage(MinioClient minioClient, MinioClient publicPresignClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.publicPresignClient = publicPresignClient;
        this.properties = properties;
    }

    @Override
    public StoredExport store(String objectKey, String fileName, String contentType, byte[] content) throws IOException {
        ensureBucket();
        byte[] safeContent = content == null ? new byte[0] : content;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(safeContent)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, safeContent.length, -1)
                    .contentType(contentType)
                    .build());
            String downloadUrl = publicPresignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(30, TimeUnit.MINUTES)
                    .build());
            return new StoredExport(properties.bucket(), objectKey, fileName, contentType, safeContent.length, downloadUrl);
        } catch (Exception ex) {
            throw new IOException("failed to store report export in MinIO", ex);
        }
    }

    @Override
    public String createDownloadUrl(String objectKey) throws IOException {
        try {
            return publicPresignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(30, TimeUnit.MINUTES)
                    .build());
        } catch (Exception ex) {
            throw new IOException("failed to create report export download URL", ex);
        }
    }

    @Override
    public Optional<StoredObject> readObject(String objectKey) throws IOException {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            byte[] content = inputStream.readAllBytes();
            return Optional.of(new StoredObject(properties.bucket(), objectKey, contentType(objectKey), content));
        } catch (io.minio.errors.ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IOException("failed to read report export object from MinIO", ex);
        } catch (Exception ex) {
            throw new IOException("failed to read report export object from MinIO", ex);
        }
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

    private String contentType(String objectKey) {
        String value = objectKey == null ? "" : objectKey.toLowerCase(java.util.Locale.ROOT);
        if (value.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (value.endsWith(".png")) {
            return "image/png";
        }
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static MinioClient buildPublicPresignClient(MinioClient fallbackClient, MinioProperties properties) {
        String publicEndpoint = properties.publicEndpoint();
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return fallbackClient;
        }
        URI.create(publicEndpoint);
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
