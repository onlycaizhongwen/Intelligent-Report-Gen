package com.company.report.report.infrastructure.storage;

import com.company.report.knowledge.infrastructure.storage.MinioProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinioReportExportStorageTest {
    @Test
    void createDownloadUrlUsesPublicEndpointPresignClientWhenConfigured() throws Exception {
        MinioClient storageClient = mock(MinioClient.class);
        MinioClient publicPresignClient = mock(MinioClient.class);
        when(publicPresignClient.getPresignedObjectUrl(any()))
                .thenReturn("http://host.docker.internal:9000/report-artifacts/reports/397/exports/2/report-397-v193.pptx?X-Amz-Signature=test");
        MinioReportExportStorage storage = new MinioReportExportStorage(
                storageClient,
                publicPresignClient,
                new MinioProperties(
                        "http://minio:9000",
                        "http://host.docker.internal:9000",
                        "minioadmin",
                        "minioadmin123",
                        "report-artifacts"
                )
        );

        String downloadUrl = storage.createDownloadUrl("reports/397/exports/2/report-397-v193.pptx");

        assertThat(downloadUrl)
                .startsWith("http://host.docker.internal:9000/report-artifacts/reports/397/exports/2/report-397-v193.pptx")
                .contains("X-Amz-Signature=test");
    }

    @Test
    void createDownloadUrlFallsBackToRawUrlWhenPublicEndpointIsMissing() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.getPresignedObjectUrl(any()))
                .thenReturn("http://minio:9000/report-artifacts/reports/397/exports/2/report-397-v193.pptx?X-Amz-Signature=test");
        MinioReportExportStorage storage = new MinioReportExportStorage(
                minioClient,
                new MinioProperties(
                        "http://minio:9000",
                        null,
                        "minioadmin",
                        "minioadmin123",
                        "report-artifacts"
                )
        );

        String downloadUrl = storage.createDownloadUrl("reports/397/exports/2/report-397-v193.pptx");

        assertThat(downloadUrl)
                .isEqualTo("http://minio:9000/report-artifacts/reports/397/exports/2/report-397-v193.pptx?X-Amz-Signature=test");
    }
}
