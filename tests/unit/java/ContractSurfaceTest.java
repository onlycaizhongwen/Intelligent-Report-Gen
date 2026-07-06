package com.company.report.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSurfaceTest {
    private static final Path SOURCE_ROOT = Path.of("backend/java-report-core/src/main/java");

    @Test
    void exposesExternalAuthProfileEndpoint() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"));

        assertThat(permissionController).contains("@GetMapping(\"/auth/me\")");
        assertThat(permissionController).contains("CurrentUserHolder.get()");
    }

    @Test
    void javaOwnsDocumentUploadOrchestration() throws IOException {
        String knowledgeController = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/interfaces/rest/KnowledgeController.java"));
        String knowledgeService = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/application/KnowledgeApplicationService.java"));

        assertThat(knowledgeController).contains("@PostMapping(value = \"/documents/upload\"");
        assertThat(knowledgeController).contains("MultipartFile file");
        assertThat(knowledgeService).contains("document.parse.requested");
    }

    @Test
    void reportStreamUsesUnifiedSseEventSchema() throws IOException {
        String sseEvent = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/api/SseEvent.java"));
        String reportService = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/application/ReportApplicationService.java"));

        assertThat(sseEvent).contains("record SseEvent");
        assertThat(sseEvent).contains("taskId");
        assertThat(sseEvent).contains("errorCode");
        assertThat(reportService).contains("SseEvent.stage");
        assertThat(reportService).contains("SseEvent.done");
    }
}
