package com.company.report.knowledge.application;

import com.company.report.knowledge.domain.model.StoredDocument;
import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.knowledge.domain.model.KnowledgeBase;
import com.company.report.knowledge.domain.model.KnowledgeDataSource;
import com.company.report.knowledge.domain.model.KnowledgeItem;
import com.company.report.knowledge.domain.repository.KnowledgeBaseRepository;
import com.company.report.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.company.report.knowledge.domain.repository.KnowledgeDocumentRepository.UploadedDocumentRecord;
import com.company.report.knowledge.domain.service.KnowledgeDomainService;
import com.company.report.knowledge.infrastructure.storage.DocumentStorage;
import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.company.report.shared.event.DomainEventPublisher;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeApplicationServiceTest {
    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void uploadDocumentStoresObjectPersistsMetadataAndPublishesParseEvent() {
        CurrentUserHolder.set(new CurrentUser(7L, Set.of("analyst"), Set.of("knowledge:upload")));
        FakeStorage storage = new FakeStorage();
        FakeRepository repository = new FakeRepository();
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("经营分析", 7L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), storage, repository, knowledgeBaseRepository, publisher);
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "hello".getBytes());

        Map<String, Object> result = service.uploadDocument(file, base.id());

        assertThat(storage.objectKey.get()).startsWith("knowledge/");
        assertThat(repository.record.get().knowledgeBaseId()).isEqualTo(base.id());
        assertThat(repository.record.get().uploadedBy()).isEqualTo(7L);
        assertThat(repository.record.get().ocrRequired()).isTrue();
        assertThat(publisher.eventType.get()).isEqualTo("document.parse.requested");
        assertThat(publisher.eventKey.get()).isEqualTo("101");
        assertThat(result).containsEntry("documentId", 101L)
                .containsEntry("fileObjectId", 201L)
                .containsEntry("parseStatus", "pending")
                .containsEntry("bucket", "report-artifacts");
    }

    @Test
    void getsUploadedDocumentStatusForOwningKnowledgeBase() {
        CurrentUserHolder.set(new CurrentUser(7L, Set.of("analyst"), Set.of("knowledge:upload")));
        FakeRepository repository = new FakeRepository();
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Document KB", 7L));
        repository.document = new StoredDocument(
                101L,
                201L,
                base.id(),
                "scan.pdf",
                "pdf",
                "failed",
                "report-artifacts",
                "knowledge/scan.pdf",
                "application/pdf",
                512L,
                "document knowledge/scan.pdf requires OCR or table extraction before text parsing"
        );
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), repository, knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> result = service.getDocumentStatus(101L);

        assertThat(result)
                .containsEntry("documentId", 101L)
                .containsEntry("knowledgeBaseId", base.id())
                .containsEntry("filename", "scan.pdf")
                .containsEntry("parseStatus", "failed")
                .containsEntry("parseFailureReason", "document knowledge/scan.pdf requires OCR or table extraction before text parsing");
    }

    @Test
    void createsAndListsKnowledgeBasesFromRepositoryInsteadOfFixedDemoPayload() {
        CurrentUserHolder.set(new CurrentUser(9L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> created = service.createKnowledgeBase(Map.of("name", "市场洞察库"));
        var page = service.listKnowledgeBases(1, 10);

        assertThat(created)
                .containsEntry("knowledgeBaseId", 1L)
                .containsEntry("name", "市场洞察库")
                .containsEntry("ownerUserId", 9L)
                .containsEntry("status", "enabled");
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("knowledgeBaseId", 1L)
                .containsEntry("name", "市场洞察库")
                .containsEntry("ownerUserId", 9L);
    }

    @Test
    void listsEnterpriseDataSourcePresetsForErpOaAndFinanceConfiguration() {
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), new FakeKnowledgeBaseRepository(), new FakePublisher());

        List<Map<String, Object>> presets = service.listDataSourcePresets();

        assertThat(presets)
                .extracting(preset -> preset.get("presetId"))
                .contains("erp-postgresql", "oa-api", "finance-api");
        assertThat(presets)
                .filteredOn(preset -> "finance-api".equals(preset.get("presetId")))
                .singleElement()
                .satisfies(preset -> {
                    assertThat(preset)
                            .containsEntry("sourceType", "api")
                            .containsEntry("endpoint", "https://finance.example.com/api/v1/vouchers")
                            .containsEntry("cursorColumn", "voucherId");
                    Map<?, ?> fieldMapping = (Map<?, ?>) preset.get("fieldMapping");
                    assertThat(fieldMapping.get("profileId")).isEqualTo("finance-vouchers");
                    assertThat(fieldMapping.get("rowsPath")).isEqualTo("data.vouchers");
                    assertThat(fieldMapping.get("titleField")).isEqualTo("voucherNo");
                    assertThat(fieldMapping.get("contentField")).isEqualTo("summary");
                    assertThat(fieldMapping.get("authType")).isEqualTo("api_key");
                });
        assertThat(presets)
                .filteredOn(preset -> "oa-api".equals(preset.get("presetId")))
                .singleElement()
                .satisfies(preset -> {
                    Map<?, ?> fieldMapping = (Map<?, ?>) preset.get("fieldMapping");
                    assertThat(fieldMapping.get("profileId")).isEqualTo("oa-documents");
                    assertThat(fieldMapping.get("rowsPath")).isEqualTo("data.documents");
                    assertThat(fieldMapping.get("titleField")).isEqualTo("documentNo");
                    assertThat(fieldMapping.get("contentField")).isEqualTo("content");
                });
    }

    @Test
    void createsAndSearchesKnowledgeItemsFromRepositoryInsteadOfFixedDemoPayload() {
        CurrentUserHolder.set(new CurrentUser(10L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("经营分析", 10L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> created = service.createItem(Map.of(
                "knowledgeBaseId", base.id(),
                "title", "华东区收入摘要",
                "content", "华东区收入同比增长 12%",
                "sourceType", "manual"
        ));
        var page = service.searchItems(1, 10, "华东");

        assertThat(created)
                .containsEntry("itemId", 1L)
                .containsEntry("knowledgeBaseId", base.id())
                .containsEntry("title", "华东区收入摘要")
                .containsEntry("indexStatus", "pending");
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("itemId", 1L)
                .containsEntry("title", "华东区收入摘要")
                .containsEntry("sourceType", "manual");
    }

    @Test
    void batchImportsKnowledgeItemsAndReportsInvalidOrDuplicateRows() {
        CurrentUserHolder.set(new CurrentUser(10L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("经营知识库", 10L));
        knowledgeBaseRepository.saveItem(KnowledgeItem.manual(base.id(), "已有标题", "old", "manual", 10L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);

        Map<String, Object> result = service.batchImportItems(Map.of(
                "knowledgeBaseId", base.id(),
                "items", List.of(
                        Map.of("title", "市场洞察", "content", "收入增长 12%", "sourceType", "manual"),
                        Map.of("title", "", "content", "missing title"),
                        Map.of("title", "市场洞察", "content", "duplicate in batch"),
                        Map.of("title", "已有标题", "content", "duplicate existing")
                )
        ));

        assertThat(result)
                .containsEntry("total", 4)
                .containsEntry("imported", 1)
                .containsEntry("failed", 3);
        assertThat((List<?>) result.get("items"))
                .hasSize(4)
                .element(0)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "imported")
                .containsEntry("title", "市场洞察");
        assertThat((List<?>) result.get("items"))
                .element(1)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "failed")
                .containsEntry("reason", "title_required");
        assertThat((List<?>) result.get("items"))
                .element(2)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("reason", "duplicate_title");
        assertThat(service.searchItems(1, 10, "市场").total()).isEqualTo(1L);
        assertThat(service.searchItems(1, 10, "市场").items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("indexStatus", "pending");
        assertThat(publisher.events)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("eventType", "knowledge.item.index_requested")
                .containsEntry("eventKey", "2");
    }

    @Test
    void rejectsUploadWhenKnowledgeBaseBelongsToAnotherUser() {
        CurrentUserHolder.set(new CurrentUser(12L, Set.of("analyst"), Set.of("knowledge:upload")));
        FakeStorage storage = new FakeStorage();
        FakeRepository repository = new FakeRepository();
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Private KB", 11L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), storage, repository, knowledgeBaseRepository, new FakePublisher());
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadDocument(file, base.id()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("knowledge base access denied");
        assertThat(storage.objectKey.get()).isNull();
        assertThat(repository.record.get()).isNull();
    }

    @Test
    void softDeletesKnowledgeItemOnlyForOwningUser() {
        CurrentUserHolder.set(new CurrentUser(21L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Owned KB", 21L));
        KnowledgeItem item = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                base.id(),
                "Delete me",
                "content",
                "manual",
                21L
        ));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> deleted = service.deleteItem(item.id(), true);

        assertThat(deleted)
                .containsEntry("itemId", item.id())
                .containsEntry("deleted", true)
                .containsEntry("requiresConfirmation", false);
        assertThat(service.searchItems(1, 10, "Delete").total()).isZero();

        KnowledgeBase otherBase = knowledgeBaseRepository.save(KnowledgeBase.newBase("Other KB", 22L));
        KnowledgeItem otherItem = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                otherBase.id(),
                "Private item",
                "content",
                "manual",
                22L
        ));

        assertThatThrownBy(() -> service.deleteItem(otherItem.id(), true))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("knowledge item access denied");
    }

    @Test
    void requiresConfirmationWhenKnowledgeItemIsReferencedByReportCitation() {
        CurrentUserHolder.set(new CurrentUser(25L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Referenced KB", 25L));
        KnowledgeItem item = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                base.id(),
                "Referenced item",
                "content",
                "manual",
                25L
        ));
        knowledgeBaseRepository.referenceCounts.put(item.id(), 2L);
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);

        assertThatThrownBy(() -> service.deleteItem(item.id(), false))
                .isInstanceOf(com.company.report.shared.error.BusinessException.class)
                .extracting(error -> ((com.company.report.shared.error.BusinessException) error).code())
                .isEqualTo(1003);
        assertThat(publisher.eventType.get()).isNull();

        Map<String, Object> deleted = service.deleteItem(item.id(), true);

        assertThat(deleted)
                .containsEntry("itemId", item.id())
                .containsEntry("referenceCount", 2L)
                .containsEntry("deleted", true)
                .containsEntry("requiresConfirmation", false);
        assertThat(publisher.eventType.get()).isEqualTo("knowledge.item.deleted");
        assertThat(publisher.eventKey.get()).isEqualTo(String.valueOf(item.id()));
        assertThat(publisher.payload.get())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("knowledgeItemId", item.id())
                .containsEntry("knowledgeBaseId", base.id())
                .containsEntry("title", "Referenced item")
                .containsEntry("referenceCount", 2L);
    }

    @Test
    void savesAndTestsDataSourceFromRepositoryStateWithoutReturningFalseConnectionSuccess() {
        CurrentUserHolder.set(new CurrentUser(23L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> saved = service.saveDataSource(Map.of(
                "name", "ERP PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/erp"
        ));
        Long dataSourceId = ((Number) saved.get("dataSourceId")).longValue();
        Map<String, Object> tested = service.testConnection(Map.of("dataSourceId", dataSourceId));

        assertThat(saved)
                .containsEntry("dataSourceId", dataSourceId)
                .containsEntry("name", "ERP PostgreSQL")
                .containsEntry("sourceType", "postgresql")
                .containsEntry("status", "enabled");
        assertThat(tested)
                .containsEntry("dataSourceId", dataSourceId)
                .containsEntry("success", false)
                .containsEntry("sourceType", "postgresql");
    }

    @Test
    void startsDataSourceSyncAndListsSyncLogsWithFailureReason() {
        CurrentUserHolder.set(new CurrentUser(24L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        Long enabledId = ((Number) service.saveDataSource(Map.of(
                "name", "ERP PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/erp"
        )).get("dataSourceId")).longValue();
        Long brokenId = ((Number) service.saveDataSource(Map.of(
                "name", "Broken ERP",
                "sourceType", "postgresql",
                "endpoint", "unreachable://erp"
        )).get("dataSourceId")).longValue();

        Map<String, Object> succeeded = service.startDataSourceSync(enabledId, Map.of("mode", "manual"));
        Map<String, Object> failed = service.startDataSourceSync(brokenId, Map.of("mode", "manual"));
        var logs = service.listDataSourceSyncRuns(enabledId, 1, 10);

        assertThat(succeeded)
                .containsEntry("dataSourceId", enabledId)
                .containsEntry("status", "failed")
                .containsEntry("mode", "manual")
                .containsEntry("processedRows", 0L)
                .containsEntry("failureReason", "unsupported or unreachable endpoint")
                .containsKey("syncRunId");
        assertThat(failed)
                .containsEntry("dataSourceId", brokenId)
                .containsEntry("status", "failed")
                .containsEntry("failureReason", "unsupported or unreachable endpoint");
        assertThat(logs.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("dataSourceId", enabledId)
                .containsEntry("status", "failed")
                .containsEntry("mode", "manual");
    }

    @Test
    void savesDataSourceCredentialsWithoutReturningPlainSecret() {
        CurrentUserHolder.set(new CurrentUser(26L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Finance KB", 26L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> saved = service.saveDataSource(Map.of(
                "name", "Finance PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/finance",
                "username", "finance_app",
                "password", "plain-secret",
                "knowledgeBaseId", base.id(),
                "syncQuery", "select title, content from finance_reports"
        ));

        Long dataSourceId = ((Number) saved.get("dataSourceId")).longValue();
        KnowledgeDataSource stored = knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow();
        assertThat(saved)
                .containsEntry("credentialConfigured", true)
                .containsEntry("knowledgeBaseId", base.id());
        assertThat(saved).doesNotContainKeys("password", "credentialSecret");
        assertThat(stored.credentialSecret())
                .startsWith("enc:v1:")
                .doesNotContain("plain-secret")
                .doesNotContain("sampleRows");
        assertThat(stored.syncQuery()).isEqualTo("select title, content from finance_reports");
    }

    @Test
    void syncsConfiguredRowsIntoKnowledgeItemsAndPublishesIndexEvents() {
        CurrentUserHolder.set(new CurrentUser(27L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Finance KB", 27L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);

        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "Finance PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/finance",
                "username", "finance_app",
                "password", "plain-secret",
                "knowledgeBaseId", base.id(),
                "syncQuery", "select title, content from finance_reports"
        )).get("dataSourceId")).longValue();

        Map<String, Object> syncRun = service.startDataSourceSync(dataSourceId, Map.of(
                "mode", "manual",
                "sampleRows", List.of(
                        Map.of("title", "Q1 revenue", "content", "Revenue increased 12%"),
                        Map.of("title", "Receivable risk", "content", "Aging receivables require follow-up")
                )
        ));
        var imported = service.searchItems(1, 10, "Revenue");

        assertThat(syncRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 2L)
                .containsEntry("lastCursor", "2");
        assertThat(imported.total()).isEqualTo(1L);
        assertThat(imported.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("title", "Q1 revenue")
                .containsEntry("sourceType", "data_source:postgresql");
        assertThat(publisher.events)
                .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                .hasSize(2);
        assertThat(knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow().lastCursor())
                .isEqualTo("2");
    }

    @Test
    void syncsOnlyRowsAfterConfiguredCursorColumnAndKeepsLatestCursor() {
        CurrentUserHolder.set(new CurrentUser(30L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Finance KB", 30L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);

        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "Incremental PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/finance",
                "username", "finance_app",
                "password", "plain-secret",
                "knowledgeBaseId", base.id(),
                "syncQuery", "select id, title, content from finance_reports",
                "cursorColumn", "id"
        )).get("dataSourceId")).longValue();

        Map<String, Object> firstRun = service.startDataSourceSync(dataSourceId, Map.of(
                "mode", "manual",
                "sampleRows", List.of(
                        Map.of("id", 1, "title", "Q1 revenue", "content", "Revenue increased 12%"),
                        Map.of("id", 2, "title", "Receivable risk", "content", "Aging receivables require follow-up")
                )
        ));
        Map<String, Object> secondRun = service.startDataSourceSync(dataSourceId, Map.of(
                "mode", "manual",
                "sampleRows", List.of(
                        Map.of("id", 1, "title", "Q1 revenue", "content", "Revenue increased 12%"),
                        Map.of("id", 2, "title", "Receivable risk", "content", "Aging receivables require follow-up"),
                        Map.of("id", 3, "title", "Q2 revenue", "content", "Revenue increased 15%")
                )
        ));

        assertThat(firstRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 2L)
                .containsEntry("lastCursor", "2");
        assertThat(secondRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "3");
        assertThat(service.searchItems(1, 10, "revenue").total()).isEqualTo(2L);
        assertThat(publisher.events)
                .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                .hasSize(3);
        assertThat(knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow().lastCursor())
                .isEqualTo("3");
    }

    @Test
    void syncsMysqlDataSourceRowsWithJdbcConnectorAndCursor() throws Exception {
        CurrentUserHolder.set(new CurrentUser(31L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("ERP KB", 31L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);
        String jdbcUrl = "jdbc:h2:mem:mysql_connector_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(jdbcUrl, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE erp_reports (
                      id BIGINT PRIMARY KEY,
                      title VARCHAR(255),
                      content VARCHAR(255)
                    )
                    """);
            connection.createStatement().execute("""
                    INSERT INTO erp_reports(id, title, content) VALUES
                    (1, 'MySQL revenue', 'Revenue from MySQL increased 11%'),
                    (2, 'MySQL risk', 'MySQL receivable risk requires follow-up')
                    """);
        }

        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "ERP MySQL",
                "sourceType", "mysql",
                "endpoint", jdbcUrl,
                "username", "sa",
                "password", "",
                "knowledgeBaseId", base.id(),
                "syncQuery", "select id, title, content from erp_reports",
                "cursorColumn", "id"
        )).get("dataSourceId")).longValue();

        Map<String, Object> connectionTest = service.testConnection(Map.of("dataSourceId", dataSourceId));
        Map<String, Object> firstRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(jdbcUrl, "sa", "")) {
            connection.createStatement().execute("""
                    INSERT INTO erp_reports(id, title, content)
                    VALUES (3, 'MySQL margin', 'MySQL margin improved')
                    """);
        }
        Map<String, Object> secondRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

        assertThat(connectionTest)
                .containsEntry("success", true)
                .containsEntry("sourceType", "mysql");
        assertThat(firstRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 2L)
                .containsEntry("lastCursor", "2");
        assertThat(secondRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "3");
        assertThat(service.searchItems(1, 10, "MySQL").items())
                .allSatisfy(item -> assertThat(item).containsEntry("sourceType", "data_source:mysql"));
        assertThat(publisher.events)
                .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                .hasSize(3);
    }

    @Test
    void testsHttpApiDataSourceConnectionWithBearerToken() throws Exception {
        CurrentUserHolder.set(new CurrentUser(32L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startJsonServer("/items", "{\"data\":{\"items\":[]}}", authorization);
        try {
            Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                    "name", "ERP API",
                    "sourceType", "api",
                    "endpoint", "http://localhost:" + server.getAddress().getPort() + "/items",
                    "password", "api-token"
            )).get("dataSourceId")).longValue();

            Map<String, Object> tested = service.testConnection(Map.of("dataSourceId", dataSourceId));

            assertThat(tested)
                    .containsEntry("dataSourceId", dataSourceId)
                    .containsEntry("success", true)
                    .containsEntry("sourceType", "api");
            assertThat(authorization.get()).isEqualTo("Bearer api-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void syncsHttpApiRowsWithFieldMappingAndCursorIntoKnowledgeItems() throws Exception {
        CurrentUserHolder.set(new CurrentUser(33L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("ERP KB", 33L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);
        AtomicReference<String> responseBody = new AtomicReference<>("""
                {"data":{"items":[
                  {"id":1,"headline":"ERP revenue","body":"Revenue up"},
                  {"id":2,"headline":"ERP risk","body":"Risk high"}
                ]}}
                """);
        HttpServer server = startMutableJsonServer("/items", responseBody);
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", "ERP API");
            request.put("sourceType", "api");
            request.put("endpoint", "http://localhost:" + server.getAddress().getPort() + "/items");
            request.put("password", "api-token");
            request.put("knowledgeBaseId", base.id());
            request.put("cursorColumn", "id");
            request.put("fieldMapping", Map.of(
                    "rowsPath", "data.items",
                    "titleField", "headline",
                    "contentField", "body"
            ));
            Long dataSourceId = ((Number) service.saveDataSource(request).get("dataSourceId")).longValue();

            Map<String, Object> firstRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));
            responseBody.set("""
                    {"data":{"items":[
                      {"id":1,"headline":"ERP revenue","body":"Revenue up"},
                      {"id":2,"headline":"ERP risk","body":"Risk high"},
                      {"id":3,"headline":"ERP cash","body":"Cash flow improved"}
                    ]}}
                    """);
            Map<String, Object> secondRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

            assertThat(firstRun)
                    .containsEntry("status", "succeeded")
                    .containsEntry("processedRows", 2L)
                    .containsEntry("lastCursor", "2");
            assertThat(secondRun)
                    .containsEntry("status", "succeeded")
                    .containsEntry("processedRows", 1L)
                    .containsEntry("lastCursor", "3");
            assertThat(service.searchItems(1, 10, "ERP").total()).isEqualTo(3L);
            assertThat(service.searchItems(1, 10, "cash").items())
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("title", "ERP cash")
                    .containsEntry("content", "Cash flow improved")
                    .containsEntry("sourceType", "data_source:api");
            assertThat(knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow().lastCursor())
                    .isEqualTo("3");
            assertThat(publisher.events)
                    .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                    .hasSize(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsApiKnowledgeDataSourceWithoutRequiredFieldMapping() {
        CurrentUserHolder.set(new CurrentUser(40L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("API Mapping KB", 40L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        assertThatThrownBy(() -> service.saveDataSource(Map.of(
                "name", "Incomplete API Mapping",
                "sourceType", "api",
                "endpoint", "https://erp.example.test/items",
                "knowledgeBaseId", base.id(),
                "fieldMapping", Map.of(
                        "rowsPath", "data.items",
                        "titleField", "headline"
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api data source fieldMapping.contentField is required");
    }

    @Test
    void rejectsApiDataSourceEndpointOutsideAllowlistOnSave() {
        CurrentUserHolder.set(new CurrentUser(44L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        assertThatThrownBy(() -> service.saveDataSource(Map.of(
                "name", "Metadata Service API",
                "sourceType", "api",
                "endpoint", "http://169.254.169.254/latest/meta-data"
        )))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("knowledge data source endpoint is not allowed");
    }

    @Test
    void rejectsJdbcDataSourceEndpointOutsideAllowlistOnSave() {
        CurrentUserHolder.set(new CurrentUser(45L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        assertThatThrownBy(() -> service.saveDataSource(Map.of(
                "name", "External ERP PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://evil.internal:5432/erp"
        )))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("knowledge data source endpoint is not allowed");
    }

    @Test
    void rejectsDisallowedEndpointDuringSyncEvenWhenRowsAreProvidedByRequest() {
        CurrentUserHolder.set(new CurrentUser(46L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Bypass KB", 46L));
        KnowledgeDataSource dataSource = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                46L,
                "Bypass API",
                "api",
                "http://169.254.169.254/latest/meta-data",
                null,
                null,
                base.id(),
                null,
                "{\"rowsPath\":\"data.items\",\"titleField\":\"title\",\"contentField\":\"content\"}"
        ));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> syncRun = service.startDataSourceSync(dataSource.id(), Map.of(
                "mode", "manual",
                "sampleRows", List.of(Map.of("title", "Should not import", "content", "Blocked by allowlist"))
        ));

        assertThat(syncRun)
                .containsEntry("status", "failed")
                .containsEntry("processedRows", 0L)
                .containsEntry("failureReason", "endpoint not allowed");
        assertThat(service.searchItems(1, 10, "Should not import").total()).isZero();
    }

    @Test
    void rejectsApiKnowledgeDataSourceWithUnsafeAdvancedMappingConfigurationOnSave() {
        CurrentUserHolder.set(new CurrentUser(47L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("API Governance KB", 47L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        List<Map<String, Object>> invalidMappings = List.of(
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of("method", "DELETE")),
                        "message", "api data source fieldMapping.method must be GET or POST"
                ),
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of("authType", "oauth")),
                        "message", "api data source fieldMapping.authType must be bearer, api_key, basic, or none"
                ),
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of("maxPages", 101)),
                        "message", "api data source fieldMapping.maxPages must be between 1 and 100"
                ),
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of("headers", Map.of("Authorization", "Bearer override"))),
                        "message", "api data source fieldMapping.headers must not include reserved header: Authorization"
                )
        );

        for (Map<String, Object> invalidMapping : invalidMappings) {
            assertThatThrownBy(() -> service.saveDataSource(Map.of(
                    "name", "Unsafe API Mapping",
                    "sourceType", "api",
                    "endpoint", "http://localhost:18080/items",
                    "knowledgeBaseId", base.id(),
                    "fieldMapping", invalidMapping.get("fieldMapping")
            )))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(invalidMapping.get("message")));
        }
    }

    @Test
    void rejectsApiKnowledgeDataSourceWhenProfileMappingDoesNotMatchEnterpriseSchema() {
        CurrentUserHolder.set(new CurrentUser(48L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Profile KB", 48L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        List<Map<String, Object>> invalidMappings = List.of(
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of(
                                "profileId", "oa-documents",
                                "rowsPath", "data.items",
                                "titleField", "headline",
                                "cursorField", "rowId"
                        )),
                        "message", "api data source profile oa-documents requires rowsPath=data.documents"
                ),
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of(
                                "profileId", "finance-vouchers",
                                "rowsPath", "data.vouchers",
                                "titleField", "voucherNo",
                                "contentField", "summary",
                                "cursorField", "voucherId",
                                "method", "GET",
                                "authType", "bearer",
                                "headers", Map.of()
                        )),
                        "cursorColumn", "voucherId",
                        "message", "api data source profile finance-vouchers requires method=POST"
                ),
                Map.of(
                        "fieldMapping", apiMappingWith(Map.of(
                                "profileId", "unknown-profile"
                        )),
                        "message", "api data source fieldMapping.profileId is not supported: unknown-profile"
                )
        );

        for (Map<String, Object> invalidMapping : invalidMappings) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", "Profile API Mapping");
            request.put("sourceType", "api");
            request.put("endpoint", "http://localhost:18080/items");
            request.put("knowledgeBaseId", base.id());
            request.put("fieldMapping", invalidMapping.get("fieldMapping"));
            if (invalidMapping.containsKey("cursorColumn")) {
                request.put("cursorColumn", invalidMapping.get("cursorColumn"));
            }

            assertThatThrownBy(() -> service.saveDataSource(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(invalidMapping.get("message")));
        }
    }

    @Test
    void rejectsApiKnowledgeDataSourceWhenProfileCursorColumnDoesNotMatchSchemaCursor() {
        CurrentUserHolder.set(new CurrentUser(49L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Profile Cursor KB", 49L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());

        Map<String, Object> financeProfile = apiMappingWith(Map.of(
                "profileId", "finance-vouchers",
                "rowsPath", "data.vouchers",
                "titleField", "voucherNo",
                "contentField", "summary",
                "cursorField", "voucherId",
                "method", "POST",
                "authType", "api_key",
                "apiKeyHeader", "X-API-Key",
                "headers", Map.of("X-Tenant", "finance")
        ));

        assertThatThrownBy(() -> service.saveDataSource(Map.of(
                "name", "Finance Profile API Mapping",
                "sourceType", "api",
                "endpoint", "http://localhost:18080/finance/vouchers",
                "knowledgeBaseId", base.id(),
                "cursorColumn", "id",
                "fieldMapping", financeProfile
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api data source profile finance-vouchers requires cursorColumn=voucherId");
    }

    @Test
    void rejectsLegacyApiProfileCursorDriftBeforeSyncingRows() {
        CurrentUserHolder.set(new CurrentUser(50L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Legacy Profile KB", 50L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        KnowledgeDataSource legacyDrifted = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                50L,
                "Legacy Finance API",
                "api",
                "http://localhost:18080/finance/vouchers",
                "finance_reader",
                "enc:v1:legacy-token",
                base.id(),
                null,
                """
                        {"profileId":"finance-vouchers","rowsPath":"data.vouchers","titleField":"voucherNo","contentField":"summary","cursorField":"voucherId","method":"POST","authType":"api_key","apiKeyHeader":"X-API-Key","headers":{"X-Tenant":"finance"}}
                        """,
                "id",
                null
        ));

        Map<String, Object> syncRun = service.startDataSourceSync(legacyDrifted.id(), Map.of(
                "mode", "manual",
                "sampleRows", List.of(Map.of(
                        "voucherId", 1,
                        "voucherNo", "FIN-LEGACY-001",
                        "summary", "Should not import drifted legacy profile"
                ))
        ));

        assertThat(syncRun)
                .containsEntry("status", "failed")
                .containsEntry("processedRows", 0L)
                .containsEntry("failureReason", "api data source profile finance-vouchers requires cursorColumn=voucherId")
                .containsEntry("lastCursor", "");
        assertThat(knowledgeBaseRepository.releaseLeaseCalls).isZero();
        assertThat(service.searchItems(1, 10, "FIN-LEGACY").total()).isZero();
    }

    @Test
    void listsApiDataSourcesWithProfileDriftForOperationalAudit() {
        CurrentUserHolder.set(new CurrentUser(51L, Set.of("ops"), Set.of("datasource:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Profile Audit KB", 51L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        String financeProfile = """
                {"profileId":"finance-vouchers","rowsPath":"data.vouchers","titleField":"voucherNo","contentField":"summary","cursorField":"voucherId","method":"POST","authType":"api_key","apiKeyHeader":"X-API-Key","headers":{"X-Tenant":"finance"}}
                """;
        KnowledgeDataSource valid = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                51L, "Finance API Valid", "api", "http://localhost:18080/finance/vouchers",
                "finance_reader", "enc:v1:token", base.id(), null, financeProfile, "voucherId", null));
        KnowledgeDataSource drifted = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                51L, "Finance API Drifted", "api", "http://localhost:18080/finance/vouchers",
                "finance_reader", "enc:v1:legacy-token", base.id(), null, financeProfile, "id", null));
        knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                51L, "ERP Database", "postgresql", "jdbc:postgresql://localhost:5432/erp",
                "erp_reader", "enc:v1:db-token", base.id(), null, "", "id", null));

        Map<String, Object> audit = service.auditDataSourceProfileDrift(100);

        assertThat(audit)
                .containsEntry("scannedCount", 2)
                .containsEntry("driftCount", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) audit.get("items");
        assertThat(items).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("dataSourceId", drifted.id())
                .containsEntry("name", "Finance API Drifted")
                .containsEntry("sourceType", "api")
                .containsEntry("profileId", "finance-vouchers")
                .containsEntry("cursorColumn", "id")
                .containsEntry("failureReason", "api data source profile finance-vouchers requires cursorColumn=voucherId"));
        assertThat(items).noneMatch(item -> valid.id().equals(item.get("dataSourceId")));
    }

    @Test
    void previewsApiDataSourceProfileDriftRepairWithoutPersistingChanges() {
        CurrentUserHolder.set(new CurrentUser(52L, Set.of("ops"), Set.of("datasource:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Profile Repair Preview KB", 52L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        KnowledgeDataSource drifted = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                52L,
                "Finance API Preview Drift",
                "api",
                "http://localhost:18080/finance/vouchers",
                "finance_reader",
                "enc:v1:legacy-token",
                base.id(),
                null,
                """
                        {"profileId":"finance-vouchers","rowsPath":"data.vouchers","titleField":"voucherNo","contentField":"summary","cursorField":"voucherId","method":"POST","authType":"api_key","apiKeyHeader":"X-API-Key","headers":{"X-Tenant":"finance"}}
                        """,
                "id",
                "42"
        ));

        Map<String, Object> preview = service.repairDataSourceProfileDrift(drifted.id(), Map.of("confirmed", false));

        assertThat(preview)
                .containsEntry("dataSourceId", drifted.id())
                .containsEntry("profileId", "finance-vouchers")
                .containsEntry("repaired", false)
                .containsEntry("requiresConfirmation", true)
                .containsEntry("currentFailureReason", "api data source profile finance-vouchers requires cursorColumn=voucherId")
                .containsEntry("proposedCursorColumn", "voucherId");
        assertThat(knowledgeBaseRepository.findDataSourceById(drifted.id()).orElseThrow().cursorColumn()).isEqualTo("id");
        assertThat(service.auditDataSourceProfileDrift(100)).containsEntry("driftCount", 1);
    }

    @Test
    void confirmsApiDataSourceProfileDriftRepairAndAllowsNextSync() {
        CurrentUserHolder.set(new CurrentUser(53L, Set.of("ops"), Set.of("datasource:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        FakeAuditRepository auditRepository = new FakeAuditRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Profile Repair KB", 53L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                new DataSourceCredentialCodec("local-test-data-source-credential-key"),
                auditRepository);
        KnowledgeDataSource drifted = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                53L,
                "Finance API Repair Drift",
                "api",
                "http://localhost:18080/finance/vouchers",
                "finance_reader",
                "enc:v1:legacy-token",
                base.id(),
                null,
                """
                        {"profileId":"finance-vouchers","rowsPath":"data.vouchers","titleField":"voucherNo","contentField":"summary","cursorField":"voucherId","method":"POST","authType":"api_key","apiKeyHeader":"X-API-Key","headers":{"X-Tenant":"finance"}}
                        """,
                "id",
                null
        ));

        Map<String, Object> repaired = service.repairDataSourceProfileDrift(drifted.id(), Map.of("confirmed", true));
        KnowledgeDataSource repairedDataSource = knowledgeBaseRepository.findDataSourceById(drifted.id()).orElseThrow();
        Map<String, Object> syncRun = service.startDataSourceSync(drifted.id(), Map.of(
                "mode", "manual",
                "sampleRows", List.of(Map.of(
                        "voucherId", 2001,
                        "voucherNo", "FIN-REPAIRED-001",
                        "summary", "Imported after profile repair"
                ))
        ));

        assertThat(repaired)
                .containsEntry("dataSourceId", drifted.id())
                .containsEntry("profileId", "finance-vouchers")
                .containsEntry("repaired", true)
                .containsEntry("requiresConfirmation", false)
                .containsEntry("previousCursorColumn", "id")
                .containsEntry("proposedCursorColumn", "voucherId");
        assertThat(repairedDataSource.cursorColumn()).isEqualTo("voucherId");
        assertThat(repairedDataSource.lastCursor()).isNull();
        assertThat(service.auditDataSourceProfileDrift(100)).containsEntry("driftCount", 0);
        assertThat(syncRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "2001");
        assertThat(auditRepository.logs)
                .anySatisfy(log -> assertThat(log.operationType()).isEqualTo("knowledge_data_source_profile_repaired"));
    }

    @Test
    void usesCustomerApiProfileCatalogForSaveAuditRepairAndSync() {
        CurrentUserHolder.set(new CurrentUser(54L, Set.of("ops"), Set.of("datasource:manage", "knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        FakeAuditRepository auditRepository = new FakeAuditRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Customer Profile KB", 54L));
        ApiDataSourceProfileCatalog profileCatalog = new ApiDataSourceProfileCatalog("""
                [
                  {
                    "profileId": "customer-crm-tickets",
                    "rowsPath": "data.tickets",
                    "titleField": "ticketNo",
                    "contentField": "description",
                    "cursorField": "updatedAt",
                    "cursorColumn": "updatedAt",
                    "method": "GET",
                    "authType": "bearer",
                    "headers": {"X-System": "crm"}
                  }
                ]
                """);
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                new DataSourceCredentialCodec("local-test-data-source-credential-key"),
                auditRepository,
                new FakeSystemAlertRepository(),
                profileCatalog);
        Map<String, Object> customerProfile = apiMappingWith(Map.of(
                "profileId", "customer-crm-tickets",
                "rowsPath", "data.tickets",
                "titleField", "ticketNo",
                "contentField", "description",
                "cursorField", "updatedAt",
                "method", "GET",
                "authType", "bearer",
                "headers", Map.of("X-System", "crm")
        ));

        Map<String, Object> saved = service.saveDataSource(Map.of(
                "name", "Customer CRM API",
                "sourceType", "api",
                "endpoint", "http://localhost:18080/customer/crm/tickets",
                "knowledgeBaseId", base.id(),
                "credential", "customer-token",
                "cursorColumn", "updatedAt",
                "fieldMapping", customerProfile
        ));
        KnowledgeDataSource drifted = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                54L,
                "Customer CRM Drifted",
                "api",
                "http://localhost:18080/customer/crm/tickets",
                "crm_reader",
                "enc:v1:legacy-token",
                base.id(),
                null,
                """
                        {"profileId":"customer-crm-tickets","rowsPath":"data.tickets","titleField":"ticketNo","contentField":"description","cursorField":"updatedAt","method":"GET","authType":"bearer","headers":{"X-System":"legacy"}}
                        """,
                "id",
                "100"
        ));

        Map<String, Object> audit = service.auditDataSourceProfileDrift(100);
        Map<String, Object> repaired = service.repairDataSourceProfileDrift(drifted.id(), Map.of("confirmed", true));
        assertThat(knowledgeBaseRepository.findDataSourceById(drifted.id()).orElseThrow().lastCursor()).isNull();
        Map<String, Object> syncRun = service.startDataSourceSync(drifted.id(), Map.of(
                "mode", "manual",
                "sampleRows", List.of(Map.of(
                        "ticketNo", "CRM-001",
                        "description", "Customer escalation",
                        "updatedAt", "2026-07-06T09:00:00Z"
                ))
        ));

        assertThat(saved)
                .containsEntry("sourceType", "api")
                .containsEntry("cursorColumn", "updatedAt");
        assertThat(audit).containsEntry("driftCount", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> proposedFieldMapping = (Map<String, Object>) repaired.get("proposedFieldMapping");
        assertThat(repaired)
                .containsEntry("profileId", "customer-crm-tickets")
                .containsEntry("repaired", true)
                .containsEntry("previousCursorColumn", "id")
                .containsEntry("proposedCursorColumn", "updatedAt");
        assertThat(proposedFieldMapping)
                .containsEntry("rowsPath", "data.tickets")
                .containsEntry("titleField", "ticketNo")
                .containsEntry("contentField", "description")
                .containsEntry("cursorField", "updatedAt");
        assertThat(syncRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "2026-07-06T09:00:00Z");
        assertThat(auditRepository.logs)
                .anySatisfy(log -> assertThat(log.operationType()).isEqualTo("knowledge_data_source_profile_repaired"));
    }

    @Test
    void syncsHttpApiRowsWithPostBodyCustomHeadersAndApiKey() throws Exception {
        CurrentUserHolder.set(new CurrentUser(38L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("ERP API KB", 38L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = startCaptureJsonServer("/reports", """
                {"data":{"items":[{"id":1,"headline":"POST revenue","body":"Revenue by POST"}]}}
                """, exchange -> {
                    method.set(exchange.getRequestMethod());
                    apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
                    tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant"));
                    body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                });
        try {
            Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                    "name", "ERP POST API",
                    "sourceType", "api",
                    "endpoint", "http://localhost:" + server.getAddress().getPort() + "/reports",
                    "password", "api-secret",
                    "knowledgeBaseId", base.id(),
                    "fieldMapping", Map.of(
                            "method", "POST",
                            "authType", "api_key",
                            "apiKeyHeader", "X-API-Key",
                            "headers", Map.of("X-Tenant", "finance"),
                            "bodyTemplate", "{\"period\":\"2026Q1\"}",
                            "rowsPath", "data.items",
                            "titleField", "headline",
                            "contentField", "body"
                    )
            )).get("dataSourceId")).longValue();

            Map<String, Object> run = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

            assertThat(run)
                    .containsEntry("status", "succeeded")
                    .containsEntry("processedRows", 1L);
            assertThat(method.get()).isEqualTo("POST");
            assertThat(apiKey.get()).isEqualTo("api-secret");
            assertThat(tenant.get()).isEqualTo("finance");
            assertThat(body.get()).isEqualTo("{\"period\":\"2026Q1\"}");
            assertThat(service.searchItems(1, 10, "POST revenue").total()).isEqualTo(1L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void syncsHttpApiRowsAcrossConfiguredPages() throws Exception {
        CurrentUserHolder.set(new CurrentUser(39L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Paged API KB", 39L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        List<String> requests = new ArrayList<>();
        HttpServer server = startCaptureJsonServer("/paged", "", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String query = exchange.getRequestURI().getQuery();
            String response = query != null && query.contains("page=2")
                    ? "{\"data\":{\"items\":[{\"id\":2,\"headline\":\"Page two\",\"body\":\"Second page\"}]}}"
                    : "{\"data\":{\"items\":[{\"id\":1,\"headline\":\"Page one\",\"body\":\"First page\"}]}}";
            exchange.setAttribute("responseBody", response);
        });
        try {
            Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                    "name", "Paged API",
                    "sourceType", "api",
                    "endpoint", "http://localhost:" + server.getAddress().getPort() + "/paged",
                    "knowledgeBaseId", base.id(),
                    "fieldMapping", Map.of(
                            "rowsPath", "data.items",
                            "titleField", "headline",
                            "contentField", "body",
                            "pageParam", "page",
                            "pageStart", 1,
                            "pageSizeParam", "pageSize",
                            "pageSize", 1,
                            "maxPages", 2
                    )
            )).get("dataSourceId")).longValue();

            Map<String, Object> run = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

            assertThat(run)
                    .containsEntry("status", "succeeded")
                    .containsEntry("processedRows", 2L);
            assertThat(requests)
                    .containsExactly("/paged?page=1&pageSize=1", "/paged?page=2&pageSize=1");
            assertThat(service.searchItems(1, 10, "Page").total()).isEqualTo(2L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsDataSourceSyncWhenAnotherRunHoldsTheLease() {
        CurrentUserHolder.set(new CurrentUser(34L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "Locked PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/locked"
        )).get("dataSourceId")).longValue();
        knowledgeBaseRepository.syncLeaseAvailable = false;

        Map<String, Object> syncRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

        assertThat(syncRun)
                .containsEntry("status", "skipped")
                .containsEntry("processedRows", 0L)
                .containsEntry("failureReason", "sync already running")
                .containsEntry("lastCursor", "");
        assertThat(knowledgeBaseRepository.releaseLeaseCalls).isZero();
    }

    @Test
    void recordsTimeoutFailureReasonWhenApiSyncExceedsConfiguredTimeout() throws Exception {
        CurrentUserHolder.set(new CurrentUser(35L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Timeout KB", 35L));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, new FakePublisher());
        HttpServer server = startDelayedJsonServer("/items", "{\"data\":{\"items\":[]}}", 250);
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", "Slow API");
            request.put("sourceType", "api");
            request.put("endpoint", "http://localhost:" + server.getAddress().getPort() + "/items");
            request.put("knowledgeBaseId", base.id());
            request.put("fieldMapping", Map.of(
                    "rowsPath", "data.items",
                    "titleField", "headline",
                    "contentField", "body"
            ));
            Long dataSourceId = ((Number) service.saveDataSource(request).get("dataSourceId")).longValue();

            Map<String, Object> syncRun = service.startDataSourceSync(dataSourceId, Map.of(
                    "mode", "manual",
                    "timeoutMs", 50
            ));

            assertThat(syncRun)
                    .containsEntry("status", "failed")
                    .containsEntry("processedRows", 0L)
                    .containsEntry("failureReason", "timeout");
            assertThat(knowledgeBaseRepository.releaseLeaseCalls).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void savesScheduledDataSourceAndSchedulerRunsDueSyncThenMovesNextRunForward() {
        CurrentUserHolder.set(new CurrentUser(31L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Scheduled KB", 31L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);
        DataSourceSyncScheduler scheduler = new DataSourceSyncScheduler(knowledgeBaseRepository, service, false);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Scheduled PostgreSQL");
        request.put("sourceType", "postgresql");
        request.put("endpoint", "jdbc:postgresql://localhost:5432/scheduled");
        request.put("username", "sync_app");
        request.put("password", "plain-secret");
        request.put("knowledgeBaseId", base.id());
        request.put("syncQuery", "select id, title, content from scheduled_reports");
        request.put("cursorColumn", "id");
        request.put("scheduleEnabled", true);
        request.put("scheduleIntervalSeconds", 60);
        request.put("nextRunAt", OffsetDateTime.now().minusMinutes(1).toString());
        Long dataSourceId = ((Number) service.saveDataSource(request).get("dataSourceId")).longValue();

        int runs = scheduler.runDueSyncsOnce(Map.of(
                "sampleRows", List.of(Map.of("id", 1, "title", "Scheduled row", "content", "Scheduled content"))
        ));

        KnowledgeDataSource scheduled = knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow();
        assertThat(runs).isEqualTo(1);
        assertThat(service.listDataSourceSyncRuns(dataSourceId, 1, 10).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("mode", "scheduled")
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L);
        assertThat(scheduled.nextRunAt()).isAfter(OffsetDateTime.now());
        assertThat(scheduled.failureCount()).isZero();
        assertThat(publisher.events)
                .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                .hasSize(1);
    }

    @Test
    void schedulerSkipsDataSourceAfterMaxRetryCountAndManualRetryClearsFailureState() {
        CurrentUserHolder.set(new CurrentUser(36L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Retry KB", 36L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);
        DataSourceSyncScheduler scheduler = new DataSourceSyncScheduler(knowledgeBaseRepository, service, false);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Retry PostgreSQL");
        request.put("sourceType", "postgresql");
        request.put("endpoint", "jdbc:postgresql://localhost:5432/retry");
        request.put("knowledgeBaseId", base.id());
        request.put("cursorColumn", "id");
        request.put("scheduleEnabled", true);
        request.put("scheduleIntervalSeconds", 60);
        request.put("nextRunAt", OffsetDateTime.now().minusMinutes(1).toString());
        request.put("maxRetryCount", 1);
        Long dataSourceId = ((Number) service.saveDataSource(request).get("dataSourceId")).longValue();

        int failedRuns = scheduler.runDueSyncsOnce(Map.of());
        int skippedRuns = scheduler.runDueSyncsOnce(Map.of());
        Map<String, Object> manualRetry = service.startDataSourceSync(dataSourceId, Map.of(
                "mode", "manual_retry",
                "sampleRows", List.of(Map.of("id", 1, "title", "Recovered row", "content", "Recovered content"))
        ));

        KnowledgeDataSource recovered = knowledgeBaseRepository.findDataSourceById(dataSourceId).orElseThrow();
        assertThat(failedRuns).isEqualTo(1);
        assertThat(skippedRuns).isZero();
        assertThat(manualRetry)
                .containsEntry("status", "succeeded")
                .containsEntry("mode", "manual_retry")
                .containsEntry("processedRows", 1L);
        assertThat(recovered.failureCount()).isZero();
        assertThat(publisher.events)
                .filteredOn(event -> "knowledge.item.index_requested".equals(event.get("eventType")))
                .hasSize(1);
    }

    @Test
    void createsUnreadSystemAlertWhenDataSourceSyncFailsWithoutLeakingSecrets() {
        CurrentUserHolder.set(new CurrentUser(37L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                new DataSourceCredentialCodec("alert-test-key"),
                new FakeAuditRepository(),
                alertRepository
        );
        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "Broken Secret PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:55432/missing",
                "username", "sync_app",
                "password", "plain-secret"
        )).get("dataSourceId")).longValue();

        Map<String, Object> failedRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));

        assertThat(failedRun)
                .containsEntry("status", "failed")
                .containsEntry("failureReason", "unsupported or unreachable endpoint")
                .containsKey("syncRunId");
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.recipientUserId()).isEqualTo(37L);
                    assertThat(alert.severity()).isEqualTo("warning");
                    assertThat(alert.type()).isEqualTo("knowledge_data_source_sync_failed");
                    assertThat(alert.status()).isEqualTo("unread");
                    assertThat(alert.resourceType()).isEqualTo("knowledge_data_source");
                    assertThat(alert.resourceId()).isEqualTo(dataSourceId);
                    assertThat(alert.payload())
                            .containsEntry("dataSourceId", dataSourceId)
                            .containsEntry("dataSourceName", "Broken Secret PostgreSQL")
                            .containsEntry("sourceType", "postgresql")
                            .containsEntry("failureReason", "unsupported or unreachable endpoint")
                            .containsEntry("syncRunId", failedRun.get("syncRunId"));
                    assertThat(alert.payload()).doesNotContainKeys("password", "credentialSecret");
                    assertThat(alert.payload().toString()).doesNotContain("plain-secret");
                });
    }

    @Test
    void writesAuditLogsForDataSourceSaveConnectionTestAndSyncWithoutSecrets() {
        CurrentUserHolder.set(new CurrentUser(29L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Audit KB", 29L));
        FakeAuditRepository auditRepository = new FakeAuditRepository();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                new DataSourceCredentialCodec("audit-test-key"),
                auditRepository
        );

        Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                "name", "Audited PostgreSQL",
                "sourceType", "postgresql",
                "endpoint", "jdbc:postgresql://localhost:5432/audit",
                "username", "audit_app",
                "password", "plain-secret",
                "knowledgeBaseId", base.id(),
                "syncQuery", "select title, content from audit_reports"
        )).get("dataSourceId")).longValue();
        service.testConnection(Map.of("dataSourceId", dataSourceId));
        service.startDataSourceSync(dataSourceId, Map.of(
                "mode", "manual",
                "sampleRows", List.of(Map.of("title", "Audit row", "content", "Audit content"))
        ));

        assertThat(auditRepository.logs)
                .extracting(OperationLog::operationType)
                .containsExactly("knowledge_data_source_saved", "knowledge_data_source_tested", "knowledge_data_source_sync_run");
        assertThat(auditRepository.logs)
                .allSatisfy(log -> {
                    assertThat(log.actorUserId()).isEqualTo(29L);
                    assertThat(log.resourceType()).isEqualTo("knowledge_data_source");
                    assertThat(log.resourceId()).isEqualTo(dataSourceId);
                    assertThat(log.detail()).doesNotContainKeys("password", "credentialSecret");
                    assertThat(log.detail().toString()).doesNotContain("plain-secret");
                });
        assertThat(auditRepository.logs.get(2).detail())
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "1");
    }

    @Test
    void decryptsLegacyBase64WrappedCredentialsForExistingDataSources() {
        CurrentUserHolder.set(new CurrentUser(28L, Set.of("analyst"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        KnowledgeBase base = knowledgeBaseRepository.save(KnowledgeBase.newBase("Legacy KB", 28L));
        FakePublisher publisher = new FakePublisher();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(), new FakeStorage(), new FakeRepository(), knowledgeBaseRepository, publisher);
        String legacyPayload = java.util.Base64.getEncoder().encodeToString(
                "legacy-password\n---sampleRows---\nLegacy row|Legacy content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KnowledgeDataSource saved = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                28L,
                "Legacy PostgreSQL",
                "postgresql",
                "jdbc:postgresql://localhost:5432/legacy",
                "legacy_user",
                "enc:" + legacyPayload,
                base.id(),
                "select title, content from legacy_reports",
                null
        ));

        Map<String, Object> syncRun = service.startDataSourceSync(saved.id(), Map.of("mode", "manual"));

        assertThat(syncRun)
                .containsEntry("status", "succeeded")
                .containsEntry("processedRows", 1L)
                .containsEntry("lastCursor", "1");
        assertThat(service.searchItems(1, 10, "Legacy").total()).isEqualTo(1L);
    }

    @Test
    void reencryptsStaleDataSourceCredentialsWithCurrentKeyAndAuditsWithoutSecrets() {
        CurrentUserHolder.set(new CurrentUser(7L, Set.of("admin"), Set.of("knowledge:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        FakeAuditRepository auditRepository = new FakeAuditRepository();
        DataSourceCredentialCodec retiredCodec = new DataSourceCredentialCodec(
                "retired-2026-06",
                "retired-data-source-key",
                new java.security.SecureRandom(new byte[]{1, 2, 3, 4}));
        DataSourceCredentialCodec currentCodec = new DataSourceCredentialCodec(
                "primary-2026-07",
                "current-data-source-key",
                Map.of("retired-2026-06", "retired-data-source-key"),
                new java.security.SecureRandom(new byte[]{1, 2, 3, 4}));
        KnowledgeDataSource stale = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                7L,
                "Finance API",
                "api",
                "https://example.test/reports",
                "sync_app",
                retiredCodec.encrypt("rotated-password"),
                null,
                null,
                null
        ));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                currentCodec,
                auditRepository,
                new FakeSystemAlertRepository());

        Map<String, Object> result = service.reencryptStaleDataSourceCredentials(100);

        KnowledgeDataSource migrated = knowledgeBaseRepository.findDataSourceById(stale.id()).orElseThrow();
        assertThat(result)
                .containsEntry("scannedCount", 1)
                .containsEntry("migratedCount", 1);
        assertThat(migrated.credentialSecret()).startsWith("enc:v2:primary-2026-07:");
        assertThat(currentCodec.decrypt(migrated.credentialSecret())).isEqualTo("rotated-password");
        assertThat(auditRepository.logs)
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.operationType()).isEqualTo("knowledge_data_source_credential_reencrypted");
                    assertThat(log.detail()).containsEntry("dataSourceId", stale.id());
                    assertThat(log.detail()).doesNotContainKeys("credentialSecret", "password");
                });
    }

    @Test
    void credentialReencryptionSchedulerUsesConfiguredScanLimit() {
        CurrentUserHolder.set(new CurrentUser(43L, Set.of("admin"), Set.of("datasource:manage")));
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository();
        FakeAuditRepository auditRepository = new FakeAuditRepository();
        DataSourceCredentialCodec retiredCodec = new DataSourceCredentialCodec(
                "retired-2026-06",
                "retired-data-source-key",
                new java.security.SecureRandom(new byte[]{5, 6, 7, 8}));
        DataSourceCredentialCodec currentCodec = new DataSourceCredentialCodec(
                "primary-2026-07",
                "current-data-source-key",
                Map.of("retired-2026-06", "retired-data-source-key"),
                new java.security.SecureRandom(new byte[]{9, 10, 11, 12}));
        KnowledgeDataSource firstStale = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                7L,
                "Finance API A",
                "api",
                "https://example.test/a",
                "sync_app",
                retiredCodec.encrypt("rotated-password-a"),
                null,
                null,
                null
        ));
        KnowledgeDataSource secondStale = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                7L,
                "Finance API B",
                "api",
                "https://example.test/b",
                "sync_app",
                retiredCodec.encrypt("rotated-password-b"),
                null,
                null,
                null
        ));
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new FakeStorage(),
                new FakeRepository(),
                knowledgeBaseRepository,
                new FakePublisher(),
                currentCodec,
                auditRepository,
                new FakeSystemAlertRepository());
        DataSourceCredentialReencryptionScheduler scheduler =
                new DataSourceCredentialReencryptionScheduler(service, false, 1);

        Map<String, Object> result = scheduler.runCredentialReencryptionOnce();

        assertThat(result)
                .containsEntry("scannedCount", 1)
                .containsEntry("migratedCount", 1);
        assertThat(knowledgeBaseRepository.findDataSourceById(firstStale.id()).orElseThrow().credentialSecret())
                .startsWith("enc:v2:primary-2026-07:");
        assertThat(knowledgeBaseRepository.findDataSourceById(secondStale.id()).orElseThrow().credentialSecret())
                .startsWith("enc:v2:retired-2026-06:");
    }

    private static Map<String, Object> apiMappingWith(Map<String, Object> overrides) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("rowsPath", "data.items");
        mapping.put("titleField", "headline");
        mapping.put("contentField", "body");
        mapping.putAll(overrides);
        return mapping;
    }

    private static class FakeStorage implements DocumentStorage {
        private final AtomicReference<String> objectKey = new AtomicReference<>();

        @Override
        public StoredObject store(org.springframework.web.multipart.MultipartFile file, String objectKey) throws IOException {
            this.objectKey.set(objectKey);
            return new StoredObject("report-artifacts", objectKey, file.getOriginalFilename(), file.getContentType(), file.getSize());
        }
    }

    private static class FakeRepository implements KnowledgeDocumentRepository {
        private final AtomicReference<UploadedDocumentRecord> record = new AtomicReference<>();
        private StoredDocument document;

        @Override
        public StoredDocument saveUploadedDocument(UploadedDocumentRecord record) {
            this.record.set(record);
            this.document = new StoredDocument(101L, 201L, record.knowledgeBaseId(), record.fileName(), record.fileType(),
                    "pending", record.bucket(), record.objectKey(), record.contentType(), record.sizeBytes());
            return this.document;
        }

        @Override
        public Optional<StoredDocument> findById(Long documentId) {
            return document != null && document.documentId().equals(documentId) ? Optional.of(document) : Optional.empty();
        }
    }

    private static class FakeKnowledgeBaseRepository implements KnowledgeBaseRepository {
        private final AtomicLong baseIds = new AtomicLong(1);
        private final AtomicLong itemIds = new AtomicLong(1);
        private final Map<Long, KnowledgeBase> bases = new LinkedHashMap<>();
        private final List<KnowledgeItem> items = new ArrayList<>();
        private final Map<Long, KnowledgeDataSource> dataSources = new LinkedHashMap<>();
        private final Map<Long, Long> referenceCounts = new LinkedHashMap<>();
        private final AtomicLong dataSourceIds = new AtomicLong(1);
        private final AtomicLong syncRunIds = new AtomicLong(1);
        private final List<Map<String, Object>> syncRuns = new ArrayList<>();
        private boolean syncLeaseAvailable = true;
        private int releaseLeaseCalls = 0;

        @Override
        public KnowledgeBase save(KnowledgeBase knowledgeBase) {
            KnowledgeBase saved = knowledgeBase.id() == null ? knowledgeBase.withId(baseIds.getAndIncrement()) : knowledgeBase;
            bases.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<KnowledgeBase> findById(Long id) {
            return Optional.ofNullable(bases.get(id));
        }

        @Override
        public List<KnowledgeBase> findByOwner(Long ownerUserId, int page, int pageSize) {
            return bases.values().stream()
                    .filter(base -> base.ownerUserId().equals(ownerUserId))
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long countByOwner(Long ownerUserId) {
            return bases.values().stream()
                    .filter(base -> base.ownerUserId().equals(ownerUserId))
                    .count();
        }

        @Override
        public KnowledgeItem saveItem(KnowledgeItem item) {
            KnowledgeItem saved = item.id() == null ? item.withId(itemIds.getAndIncrement()) : item;
            items.add(saved);
            return saved;
        }

        @Override
        public List<KnowledgeItem> searchItems(Long ownerUserId, String keyword, int page, int pageSize) {
            return items.stream()
                    .filter(item -> bases.get(item.knowledgeBaseId()).ownerUserId().equals(ownerUserId))
                    .filter(item -> keyword == null || keyword.isBlank() || item.title().contains(keyword) || item.content().contains(keyword))
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long countItems(Long ownerUserId, String keyword) {
            return searchItems(ownerUserId, keyword, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public Optional<KnowledgeItem> findItemById(Long id) {
            return items.stream().filter(item -> item.id().equals(id)).findFirst();
        }

        @Override
        public boolean softDeleteItem(Long itemId) {
            return items.removeIf(item -> item.id().equals(itemId));
        }

        @Override
        public long countReportReferences(Long itemId) {
            return referenceCounts.getOrDefault(itemId, 0L);
        }

        @Override
        public KnowledgeDataSource saveDataSource(KnowledgeDataSource dataSource) {
            KnowledgeDataSource saved = dataSource.id() == null ? dataSource.withId(dataSourceIds.getAndIncrement()) : dataSource;
            dataSources.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<KnowledgeDataSource> findDataSourceById(Long id) {
            return Optional.ofNullable(dataSources.get(id));
        }

        @Override
        public List<KnowledgeDataSource> findDataSourcesWithCredentials(int limit) {
            return dataSources.values().stream()
                    .filter(dataSource -> dataSource.credentialSecret() != null && !dataSource.credentialSecret().isBlank())
                    .limit(Math.max(limit, 1))
                    .toList();
        }

        @Override
        public List<KnowledgeDataSource> findDataSourcesForProfileAudit(int limit) {
            return dataSources.values().stream()
                    .filter(dataSource -> "api".equalsIgnoreCase(dataSource.sourceType()))
                    .filter(dataSource -> dataSource.fieldMappingJson() != null
                            && dataSource.fieldMappingJson().contains("\"profileId\""))
                    .limit(Math.max(limit, 1))
                    .toList();
        }

        @Override
        public KnowledgeDataSource updateDataSourceCursor(Long dataSourceId, String lastCursor) {
            KnowledgeDataSource updated = dataSources.get(dataSourceId).withLastCursor(lastCursor);
            dataSources.put(dataSourceId, updated);
            return updated;
        }

        @Override
        public boolean tryAcquireDataSourceSyncLease(Long dataSourceId, OffsetDateTime lockedUntil) {
            return syncLeaseAvailable;
        }

        @Override
        public void releaseDataSourceSyncLease(Long dataSourceId) {
            releaseLeaseCalls++;
        }

        @Override
        public List<KnowledgeDataSource> findDueScheduledDataSources(OffsetDateTime now, int limit) {
            return dataSources.values().stream()
                    .filter(dataSource -> Boolean.TRUE.equals(dataSource.scheduleEnabled()))
                    .filter(dataSource -> "enabled".equals(dataSource.status()))
                    .filter(dataSource -> dataSource.nextRunAt() != null && !dataSource.nextRunAt().isAfter(now))
                    .limit(Math.max(limit, 1))
                    .toList();
        }

        @Override
        public KnowledgeDataSource updateDataSourceScheduleState(Long dataSourceId, OffsetDateTime nextRunAt, int failureCount) {
            KnowledgeDataSource updated = dataSources.get(dataSourceId).withScheduleState(nextRunAt, failureCount);
            dataSources.put(dataSourceId, updated);
            return updated;
        }

        @Override
        public Map<String, Object> saveDataSourceSyncRun(Map<String, Object> syncRun) {
            Map<String, Object> saved = new LinkedHashMap<>(syncRun);
            saved.put("syncRunId", syncRunIds.getAndIncrement());
            syncRuns.add(saved);
            return saved;
        }

        @Override
        public List<Map<String, Object>> findDataSourceSyncRuns(Long dataSourceId, int page, int pageSize) {
            return syncRuns.stream()
                    .filter(run -> dataSourceId.equals(run.get("dataSourceId")))
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long countDataSourceSyncRuns(Long dataSourceId) {
            return syncRuns.stream()
                    .filter(run -> dataSourceId.equals(run.get("dataSourceId")))
                    .count();
        }
    }

    private static class FakePublisher implements DomainEventPublisher {
        private final AtomicReference<String> eventType = new AtomicReference<>();
        private final AtomicReference<String> eventKey = new AtomicReference<>();
        private final AtomicReference<Object> payload = new AtomicReference<>();
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public void publish(String eventType, String eventKey, Object payload) {
            this.eventType.set(eventType);
            this.eventKey.set(eventKey);
            this.payload.set(payload);
            events.add(Map.of("eventType", eventType, "eventKey", eventKey, "payload", payload));
        }
    }

    private static class FakeAuditRepository implements AuditRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<OperationLog> logs = new ArrayList<>();

        @Override
        public OperationLog save(OperationLog log) {
            OperationLog saved = log.id() == null ? log.withId(ids.getAndIncrement()) : log;
            logs.add(saved);
            return saved;
        }

        @Override
        public Optional<OperationLog> findById(Long id) {
            return logs.stream().filter(log -> id.equals(log.id())).findFirst();
        }

        @Override
        public List<OperationLog> findPage(int page, int pageSize) {
            return logs.stream()
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long count() {
            return logs.size();
        }
    }

    private static class FakeSystemAlertRepository implements SystemAlertRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<SystemAlert> alerts = new ArrayList<>();

        @Override
        public SystemAlert save(SystemAlert alert) {
            SystemAlert saved = alert.id() == null ? alert.withId(ids.getAndIncrement()) : alert;
            alerts.add(saved);
            return saved;
        }

        @Override
        public List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize) {
            return alerts.stream()
                    .filter(alert -> alert.recipientUserId().equals(recipientUserId))
                    .filter(alert -> status == null || status.isBlank() || alert.status().equals(status))
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long countByRecipient(Long recipientUserId, String status) {
            return findByRecipient(recipientUserId, status, 1, Integer.MAX_VALUE).size();
        }
    }

    private static HttpServer startJsonServer(String path, String body, AtomicReference<String> authorization) throws IOException {
        AtomicReference<String> responseBody = new AtomicReference<>(body);
        HttpServer server = startMutableJsonServer(path, responseBody, authorization);
        return server;
    }

    private static HttpServer startMutableJsonServer(String path, AtomicReference<String> responseBody) throws IOException {
        return startMutableJsonServer(path, responseBody, new AtomicReference<>());
    }

    private static HttpServer startMutableJsonServer(
            String path,
            AtomicReference<String> responseBody,
            AtomicReference<String> authorization
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = responseBody.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        server.start();
        return server;
    }

    private static HttpServer startDelayedJsonServer(String path, String body, long delayMs) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        server.start();
        return server;
    }

    private static HttpServer startCaptureJsonServer(
            String path,
            String body,
            ThrowingExchangeConsumer capture
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            capture.accept(exchange);
            String responseBody = String.valueOf(exchange.getAttribute("responseBody") == null ? body : exchange.getAttribute("responseBody"));
            byte[] payload = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        server.start();
        return server;
    }

    @FunctionalInterface
    private interface ThrowingExchangeConsumer {
        void accept(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}
