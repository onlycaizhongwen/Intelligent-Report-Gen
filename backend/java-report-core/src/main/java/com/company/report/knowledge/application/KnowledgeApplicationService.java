package com.company.report.knowledge.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.knowledge.domain.model.KnowledgeBase;
import com.company.report.knowledge.domain.model.KnowledgeDataSource;
import com.company.report.knowledge.domain.model.KnowledgeItem;
import com.company.report.knowledge.domain.model.StoredDocument;
import com.company.report.knowledge.domain.repository.KnowledgeBaseRepository;
import com.company.report.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.company.report.knowledge.domain.repository.KnowledgeDocumentRepository.UploadedDocumentRecord;
import com.company.report.knowledge.domain.service.KnowledgeDomainService;
import com.company.report.knowledge.infrastructure.storage.DocumentStorage;
import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.event.DomainEventPublisher;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeApplicationService {
    private final KnowledgeDomainService domainService;
    private final DocumentStorage documentStorage;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DomainEventPublisher eventPublisher;
    private final DataSourceCredentialCodec credentialCodec;
    private final AuditRepository auditRepository;
    private final SystemAlertRepository systemAlertRepository;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    public KnowledgeApplicationService(KnowledgeDomainService domainService,
                                       DocumentStorage documentStorage,
                                       KnowledgeDocumentRepository documentRepository,
                                       KnowledgeBaseRepository knowledgeBaseRepository,
                                       DomainEventPublisher eventPublisher) {
        this(domainService, documentStorage, documentRepository, knowledgeBaseRepository, eventPublisher,
                new DataSourceCredentialCodec("local-test-data-source-credential-key"), noopAuditRepository(), noopSystemAlertRepository());
    }

    public KnowledgeApplicationService(KnowledgeDomainService domainService,
                                       DocumentStorage documentStorage,
                                       KnowledgeDocumentRepository documentRepository,
                                       KnowledgeBaseRepository knowledgeBaseRepository,
                                       DomainEventPublisher eventPublisher,
                                       DataSourceCredentialCodec credentialCodec) {
        this(domainService, documentStorage, documentRepository, knowledgeBaseRepository, eventPublisher,
                credentialCodec, noopAuditRepository(), noopSystemAlertRepository());
    }

    public KnowledgeApplicationService(KnowledgeDomainService domainService,
                                       DocumentStorage documentStorage,
                                       KnowledgeDocumentRepository documentRepository,
                                       KnowledgeBaseRepository knowledgeBaseRepository,
                                       DomainEventPublisher eventPublisher,
                                       DataSourceCredentialCodec credentialCodec,
                                       AuditRepository auditRepository) {
        this(domainService, documentStorage, documentRepository, knowledgeBaseRepository, eventPublisher,
                credentialCodec, auditRepository, noopSystemAlertRepository());
    }

    @Autowired
    public KnowledgeApplicationService(KnowledgeDomainService domainService,
                                       DocumentStorage documentStorage,
                                       KnowledgeDocumentRepository documentRepository,
                                       KnowledgeBaseRepository knowledgeBaseRepository,
                                       DomainEventPublisher eventPublisher,
                                       DataSourceCredentialCodec credentialCodec,
                                       AuditRepository auditRepository,
                                       SystemAlertRepository systemAlertRepository) {
        this.domainService = domainService;
        this.documentStorage = documentStorage;
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.eventPublisher = eventPublisher;
        this.credentialCodec = credentialCodec;
        this.auditRepository = auditRepository;
        this.systemAlertRepository = systemAlertRepository;
    }

    public PageResponse<Map<String, Object>> listKnowledgeBases(int page, int pageSize) {
        Long ownerUserId = currentUserId();
        List<Map<String, Object>> items = knowledgeBaseRepository.findByOwner(ownerUserId, page, pageSize).stream()
                .map(this::toKnowledgeBaseResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, knowledgeBaseRepository.countByOwner(ownerUserId));
    }

    public Map<String, Object> createKnowledgeBase(Map<String, Object> request) {
        String name = String.valueOf(request == null ? "" : request.getOrDefault("name", "")).trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("knowledge base name is required");
        }
        return toKnowledgeBaseResponse(knowledgeBaseRepository.save(KnowledgeBase.newBase(name, currentUserId())));
    }

    public PageResponse<Map<String, Object>> searchItems(int page, int pageSize, String keyword) {
        Long ownerUserId = currentUserId();
        List<Map<String, Object>> items = knowledgeBaseRepository.searchItems(ownerUserId, keyword, page, pageSize).stream()
                .map(this::toKnowledgeItemResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, knowledgeBaseRepository.countItems(ownerUserId, keyword));
    }

    public Map<String, Object> createItem(Map<String, Object> request) {
        Long knowledgeBaseId = longValue(request == null ? null : request.get("knowledgeBaseId"), "knowledgeBaseId");
        ensureKnowledgeBaseOwner(knowledgeBaseId);
        String title = String.valueOf(request.getOrDefault("title", "")).trim();
        String content = String.valueOf(request.getOrDefault("content", "")).trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("knowledge item title is required");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("knowledge item content is required");
        }
        String sourceType = String.valueOf(request.getOrDefault("sourceType", "manual"));
        KnowledgeItem saved = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                knowledgeBaseId,
                title,
                content,
                sourceType,
                currentUserId()
        ));
        return toKnowledgeItemResponse(saved);
    }

    public Map<String, Object> batchImportItems(Map<String, Object> request) {
        Long knowledgeBaseId = longValue(request == null ? null : request.get("knowledgeBaseId"), "knowledgeBaseId");
        ensureKnowledgeBaseOwner(knowledgeBaseId);
        Object rawItems = request.get("items");
        if (!(rawItems instanceof List<?> items)) {
            throw new IllegalArgumentException("items is required");
        }
        Set<String> existingTitles = new LinkedHashSet<>(
                knowledgeBaseRepository.searchItems(currentUserId(), "", 1, Integer.MAX_VALUE).stream()
                        .filter(item -> item.knowledgeBaseId().equals(knowledgeBaseId))
                        .map(item -> item.title().trim())
                        .toList()
        );
        Set<String> importedTitles = new LinkedHashSet<>();
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        int imported = 0;
        for (Object rawItem : items) {
            Map<?, ?> itemRequest = rawItem instanceof Map<?, ?> map ? map : Map.of();
            String title = String.valueOf(itemRequest.get("title") == null ? "" : itemRequest.get("title")).trim();
            String content = String.valueOf(itemRequest.get("content") == null ? "" : itemRequest.get("content")).trim();
            String sourceType = String.valueOf(itemRequest.get("sourceType") == null ? "batch_import" : itemRequest.get("sourceType")).trim();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", title);
            if (title.isBlank()) {
                row.put("status", "failed");
                row.put("reason", "title_required");
            } else if (content.isBlank()) {
                row.put("status", "failed");
                row.put("reason", "content_required");
            } else if (existingTitles.contains(title) || importedTitles.contains(title)) {
                row.put("status", "failed");
                row.put("reason", "duplicate_title");
            } else {
                KnowledgeItem saved = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                        knowledgeBaseId,
                        title,
                        content,
                        sourceType.isBlank() ? "batch_import" : sourceType,
                        currentUserId()
                ));
                importedTitles.add(title);
                imported++;
                row.put("status", "imported");
                row.put("itemId", saved.id());
                row.put("knowledgeBaseId", saved.knowledgeBaseId());
                publishKnowledgeIndexRequested(saved);
            }
            results.add(row);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("knowledgeBaseId", knowledgeBaseId);
        response.put("total", items.size());
        response.put("imported", imported);
        response.put("failed", items.size() - imported);
        response.put("items", results);
        return response;
    }

    private void publishKnowledgeIndexRequested(KnowledgeItem item) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("knowledgeItemId", item.id());
        event.put("knowledgeBaseId", item.knowledgeBaseId());
        event.put("title", item.title());
        event.put("content", item.content());
        event.put("sourceType", item.sourceType());
        event.put("createdBy", item.createdBy());
        eventPublisher.publish("knowledge.item.index_requested", String.valueOf(item.id()), event);
    }

    public Map<String, Object> deleteItem(Long itemId, boolean confirmed) {
        KnowledgeItem item = knowledgeBaseRepository.findItemById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge item not found: " + itemId));
        ensureKnowledgeItemOwner(item);
        long referenceCount = knowledgeBaseRepository.countReportReferences(itemId);
        boolean referenced = referenceCount > 0;
        domainService.ensureDeleteConfirmedWhenReferenced(referenced, confirmed);
        boolean deleted = confirmed && knowledgeBaseRepository.softDeleteItem(itemId);
        if (deleted) {
            Map<String, Object> deletedEvent = new LinkedHashMap<>();
            deletedEvent.put("knowledgeItemId", item.id());
            deletedEvent.put("knowledgeBaseId", item.knowledgeBaseId());
            deletedEvent.put("title", item.title());
            deletedEvent.put("referenceCount", referenceCount);
            deletedEvent.put("deletedBy", currentUserId());
            eventPublisher.publish("knowledge.item.deleted", String.valueOf(item.id()), deletedEvent);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("itemId", itemId);
        response.put("deleted", deleted);
        response.put("requiresConfirmation", referenced && !confirmed);
        response.put("referenceCount", referenceCount);
        return response;
    }

    public Map<String, Object> testConnection(Map<String, Object> request) {
        Long dataSourceId = longValue(request == null ? null : request.get("dataSourceId"), "dataSourceId");
        KnowledgeDataSource dataSource = findOwnedDataSource(dataSourceId);
        boolean success = canConnect(dataSource);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataSourceId", dataSource.id());
        response.put("success", success);
        response.put("message", success ? "connection available" : "connection unavailable");
        response.put("sourceType", dataSource.sourceType());
        writeDataSourceAudit("knowledge_data_source_tested", dataSource, success ? "succeeded" : "failed", Map.of(
                "sourceType", dataSource.sourceType(),
                "success", success,
                "message", response.get("message")
        ));
        return response;
    }

    public Map<String, Object> saveDataSource(Map<String, Object> request) {
        String name = String.valueOf(request == null ? "" : request.getOrDefault("name", "")).trim();
        String sourceType = String.valueOf(request == null ? "" : request.getOrDefault("sourceType", "")).trim();
        String endpoint = String.valueOf(request == null ? "" : request.getOrDefault("endpoint", "")).trim();
        String username = stringValue(request == null ? null : request.get("username")).trim();
        String password = stringValue(request == null ? null : request.get("password"));
        String syncQuery = stringValue(request == null ? null : request.get("syncQuery")).trim();
        String fieldMappingJson = fieldMappingJson(request == null ? null : request.get("fieldMapping"));
        String cursorColumn = stringValue(request == null ? null : request.get("cursorColumn")).trim();
        boolean scheduleEnabled = booleanValue(request == null ? null : request.get("scheduleEnabled"));
        Integer scheduleIntervalSeconds = nullableInteger(request == null ? null : request.get("scheduleIntervalSeconds"));
        OffsetDateTime nextRunAt = nullableOffsetDateTime(request == null ? null : request.get("nextRunAt"));
        Integer maxRetryCount = nullableInteger(request == null ? null : request.get("maxRetryCount"));
        Long knowledgeBaseId = nullableLong(request == null ? null : request.get("knowledgeBaseId"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("knowledge data source name is required");
        }
        if (sourceType.isBlank()) {
            throw new IllegalArgumentException("knowledge data source type is required");
        }
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("knowledge data source endpoint is required");
        }
        if (knowledgeBaseId != null) {
            ensureKnowledgeBaseOwner(knowledgeBaseId);
        }
        if (scheduleEnabled && (scheduleIntervalSeconds == null || scheduleIntervalSeconds < 30)) {
            throw new IllegalArgumentException("scheduleIntervalSeconds must be at least 30 when schedule is enabled");
        }
        if (maxRetryCount != null && (maxRetryCount < 0 || maxRetryCount > 20)) {
            throw new IllegalArgumentException("maxRetryCount must be between 0 and 20");
        }
        KnowledgeDataSource saved = knowledgeBaseRepository.saveDataSource(KnowledgeDataSource.enabled(
                currentUserId(),
                name,
                sourceType,
                endpoint,
                username.isBlank() ? null : username,
                credentialCodec.encrypt(password),
                knowledgeBaseId,
                syncQuery.isBlank() ? null : syncQuery,
                fieldMappingJson,
                cursorColumn.isBlank() ? null : safeIdentifier(cursorColumn),
                null,
                scheduleEnabled,
                scheduleIntervalSeconds,
                scheduleEnabled ? (nextRunAt == null ? OffsetDateTime.now() : nextRunAt) : null,
                0,
                maxRetryCount == null ? 3 : maxRetryCount
        ));
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("name", saved.name());
        auditDetail.put("sourceType", saved.sourceType());
        auditDetail.put("endpoint", saved.endpoint());
        if (saved.knowledgeBaseId() != null) {
            auditDetail.put("knowledgeBaseId", saved.knowledgeBaseId());
        }
        auditDetail.put("syncQueryConfigured", saved.syncQuery() != null && !saved.syncQuery().isBlank());
        auditDetail.put("fieldMappingConfigured", saved.fieldMappingJson() != null && !saved.fieldMappingJson().isBlank());
        auditDetail.put("cursorColumnConfigured", saved.cursorColumn() != null && !saved.cursorColumn().isBlank());
        auditDetail.put("scheduleEnabled", Boolean.TRUE.equals(saved.scheduleEnabled()));
        auditDetail.put("maxRetryCount", saved.maxRetryCount());
        auditDetail.put("credentialConfigured", saved.credentialSecret() != null && !saved.credentialSecret().isBlank());
        writeDataSourceAudit("knowledge_data_source_saved", saved, "succeeded", auditDetail);
        return toDataSourceResponse(saved);
    }

    public Map<String, Object> reencryptStaleDataSourceCredentials(int limit) {
        List<KnowledgeDataSource> candidates = knowledgeBaseRepository.findDataSourcesWithCredentials(Math.max(limit, 1));
        int migratedCount = 0;
        for (KnowledgeDataSource dataSource : candidates) {
            String currentSecret = dataSource.credentialSecret();
            if (currentSecret == null || currentSecret.isBlank() || credentialCodec.isCurrent(currentSecret)) {
                continue;
            }
            String plainSecret = credentialCodec.decrypt(currentSecret);
            KnowledgeDataSource migrated = knowledgeBaseRepository.saveDataSource(
                    dataSource.withCredentialSecret(credentialCodec.encrypt(plainSecret)));
            migratedCount++;
            writeDataSourceAudit("knowledge_data_source_credential_reencrypted", migrated, "succeeded", Map.of(
                    "dataSourceId", migrated.id(),
                    "sourceType", migrated.sourceType(),
                    "credentialCurrent", true
            ));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scannedCount", candidates.size());
        result.put("migratedCount", migratedCount);
        return result;
    }

    public Map<String, Object> startDataSourceSync(Long dataSourceId, Map<String, Object> request) {
        KnowledgeDataSource dataSource = findOwnedDataSource(dataSourceId);
        String mode = String.valueOf(request == null ? "manual" : request.getOrDefault("mode", "manual"));
        int timeoutMs = syncTimeoutMs(request);
        if (!knowledgeBaseRepository.tryAcquireDataSourceSyncLease(dataSource.id(), OffsetDateTime.now().plus(Duration.ofMillis(timeoutMs + 30_000L)))) {
            return saveDataSourceSyncRun(dataSource, mode, "skipped", 0L, "sync already running", "sync skipped", dataSource.lastCursor());
        }
        List<Map<String, Object>> rows = List.of();
        String failureReason = "";
        try {
            try {
                rows = extractRows(dataSource, request, timeoutMs);
            } catch (Exception ex) {
                failureReason = failureReason(ex);
            }
            boolean success = failureReason.isBlank();
            long processedRows = 0L;
            String lastCursor = dataSource.lastCursor();
            if (success) {
                List<Map<String, Object>> rowsAfterCursor = filterRowsAfterCursor(dataSource, rows);
                processedRows = importRows(dataSource, rowsAfterCursor);
                lastCursor = resolveNextCursor(dataSource, rowsAfterCursor, processedRows);
                knowledgeBaseRepository.updateDataSourceCursor(dataSource.id(), lastCursor);
                if ("manual_retry".equals(mode) || Boolean.TRUE.equals(dataSource.scheduleEnabled())) {
                    knowledgeBaseRepository.updateDataSourceScheduleState(dataSource.id(), nextRunAfterManualSuccess(dataSource), 0);
                }
            }
            return saveDataSourceSyncRun(
                    dataSource,
                    mode,
                    success ? "succeeded" : "failed",
                    processedRows,
                    failureReason,
                    success ? "sync completed" : "sync failed",
                    lastCursor
            );
        } finally {
            knowledgeBaseRepository.releaseDataSourceSyncLease(dataSource.id());
        }
    }

    private Map<String, Object> saveDataSourceSyncRun(
            KnowledgeDataSource dataSource,
            String mode,
            String status,
            long processedRows,
            String failureReason,
            String message,
            String lastCursor
    ) {
        Map<String, Object> syncRun = new LinkedHashMap<>();
        syncRun.put("dataSourceId", dataSource.id());
        syncRun.put("mode", mode);
        syncRun.put("status", status);
        syncRun.put("processedRows", processedRows);
        syncRun.put("failureReason", failureReason);
        syncRun.put("message", message);
        syncRun.put("lastCursor", lastCursor == null ? "" : lastCursor);
        syncRun.put("startedAt", java.time.OffsetDateTime.now().toString());
        syncRun.put("finishedAt", java.time.OffsetDateTime.now().toString());
        Map<String, Object> savedRun = knowledgeBaseRepository.saveDataSourceSyncRun(syncRun);
        writeDataSourceAudit("knowledge_data_source_sync_run", dataSource, "succeeded".equals(status) ? "succeeded" : status, Map.of(
                "mode", mode,
                "status", savedRun.get("status"),
                "processedRows", savedRun.get("processedRows"),
                "failureReason", savedRun.get("failureReason"),
                "lastCursor", savedRun.get("lastCursor"),
                "syncRunId", savedRun.get("syncRunId")
        ));
        if ("failed".equals(status)) {
            createDataSourceFailureAlert(dataSource, savedRun);
        }
        return savedRun;
    }

    private void createDataSourceFailureAlert(KnowledgeDataSource dataSource, Map<String, Object> savedRun) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataSourceId", dataSource.id());
        payload.put("dataSourceName", dataSource.name());
        payload.put("sourceType", dataSource.sourceType());
        payload.put("failureReason", savedRun.get("failureReason"));
        payload.put("syncRunId", savedRun.get("syncRunId"));
        payload.put("mode", savedRun.get("mode"));
        systemAlertRepository.save(SystemAlert.unread(
                dataSource.ownerUserId(),
                "knowledge_data_source_sync_failed",
                "warning",
                "knowledge_data_source",
                dataSource.id(),
                payload
        ));
    }

    public PageResponse<Map<String, Object>> listDataSourceSyncRuns(Long dataSourceId, int page, int pageSize) {
        findOwnedDataSource(dataSourceId);
        return new PageResponse<>(
                knowledgeBaseRepository.findDataSourceSyncRuns(dataSourceId, page, pageSize),
                page,
                pageSize,
                knowledgeBaseRepository.countDataSourceSyncRuns(dataSourceId)
        );
    }

    public Map<String, Object> runScheduledDataSourceSync(KnowledgeDataSource dataSource, Map<String, Object> request) {
        CurrentUser previous = CurrentUserHolder.get();
        try {
            CurrentUserHolder.set(new CurrentUser(dataSource.ownerUserId(), Set.of("system"), Set.of("knowledge:manage")));
            Map<String, Object> syncRequest = new LinkedHashMap<>(request == null ? Map.of() : request);
            syncRequest.put("mode", "scheduled");
            return startDataSourceSync(dataSource.id(), syncRequest);
        } finally {
            if (previous == null) {
                CurrentUserHolder.clear();
            } else {
                CurrentUserHolder.set(previous);
            }
        }
    }

    public Map<String, Object> uploadDocument(MultipartFile file, Long knowledgeBaseId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("document file is required");
        }
        Long resolvedKnowledgeBaseId = knowledgeBaseId == null ? 1L : knowledgeBaseId;
        ensureKnowledgeBaseOwner(resolvedKnowledgeBaseId);
        Long uploadedBy = currentUserId();
        String objectKey = "knowledge/" + UUID.randomUUID() + "/" + safeFileName(file);
        try {
            DocumentStorage.StoredObject storedObject = documentStorage.store(file, objectKey);
            StoredDocument storedDocument = documentRepository.saveUploadedDocument(new UploadedDocumentRecord(
                    resolvedKnowledgeBaseId,
                    uploadedBy,
                    storedObject.bucket(),
                    storedObject.objectKey(),
                    storedObject.fileName(),
                    storedObject.contentType(),
                    storedObject.sizeBytes(),
                    fileType(storedObject.fileName()),
                    requiresOcr(storedObject.contentType(), storedObject.fileName()),
                    requiresTableRecognition(storedObject.fileName())
            ));
            Map<String, Object> metadata = toMetadata(storedDocument);
            eventPublisher.publish("document.parse.requested", String.valueOf(storedDocument.documentId()), metadata);
            return metadata;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to orchestrate document upload", ex);
        }
    }

    public Map<String, Object> getDocumentStatus(Long documentId) {
        StoredDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + documentId));
        ensureKnowledgeBaseOwner(document.knowledgeBaseId());
        return toMetadata(document);
    }

    private Map<String, Object> toKnowledgeBaseResponse(KnowledgeBase knowledgeBase) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("knowledgeBaseId", knowledgeBase.id());
        response.put("name", knowledgeBase.name());
        response.put("ownerUserId", knowledgeBase.ownerUserId());
        response.put("status", knowledgeBase.status());
        return response;
    }

    private Map<String, Object> toKnowledgeItemResponse(KnowledgeItem item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("itemId", item.id());
        response.put("knowledgeBaseId", item.knowledgeBaseId());
        response.put("title", item.title());
        response.put("content", item.content());
        response.put("sourceType", item.sourceType());
        response.put("indexStatus", item.indexStatus());
        response.put("createdBy", item.createdBy());
        return response;
    }

    private Map<String, Object> toDataSourceResponse(KnowledgeDataSource dataSource) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataSourceId", dataSource.id());
        response.put("ownerUserId", dataSource.ownerUserId());
        response.put("name", dataSource.name());
        response.put("sourceType", dataSource.sourceType());
        response.put("endpoint", dataSource.endpoint());
        response.put("status", dataSource.status());
        response.put("knowledgeBaseId", dataSource.knowledgeBaseId());
        response.put("syncQuery", dataSource.syncQuery());
        response.put("fieldMapping", parseFieldMapping(dataSource.fieldMappingJson()));
        response.put("cursorColumn", dataSource.cursorColumn());
        response.put("lastCursor", dataSource.lastCursor());
        response.put("scheduleEnabled", Boolean.TRUE.equals(dataSource.scheduleEnabled()));
        response.put("scheduleIntervalSeconds", dataSource.scheduleIntervalSeconds());
        response.put("nextRunAt", dataSource.nextRunAt() == null ? "" : dataSource.nextRunAt().toString());
        response.put("failureCount", dataSource.failureCount() == null ? 0 : dataSource.failureCount());
        response.put("maxRetryCount", dataSource.maxRetryCount() == null ? 3 : dataSource.maxRetryCount());
        response.put("credentialConfigured", dataSource.credentialSecret() != null && !dataSource.credentialSecret().isBlank());
        return response;
    }

    private Map<String, Object> toMetadata(StoredDocument storedDocument) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", storedDocument.documentId());
        metadata.put("fileObjectId", storedDocument.fileObjectId());
        metadata.put("knowledgeBaseId", storedDocument.knowledgeBaseId());
        metadata.put("filename", storedDocument.documentTitle());
        metadata.put("fileType", storedDocument.fileType());
        metadata.put("size", storedDocument.sizeBytes());
        metadata.put("contentType", storedDocument.contentType());
        metadata.put("bucket", storedDocument.bucket());
        metadata.put("objectKey", storedDocument.objectKey());
        metadata.put("parseStatus", storedDocument.parseStatus());
        metadata.put("parseFailureReason", storedDocument.parseFailureReason());
        return metadata;
    }

    private void ensureKnowledgeBaseOwner(Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge base not found: " + knowledgeBaseId));
        if (!knowledgeBase.ownerUserId().equals(currentUserId())) {
            throw new SecurityException("knowledge base access denied: " + knowledgeBaseId);
        }
    }

    private void ensureKnowledgeItemOwner(KnowledgeItem item) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(item.knowledgeBaseId())
                .orElseThrow(() -> new IllegalArgumentException("knowledge base not found: " + item.knowledgeBaseId()));
        if (!knowledgeBase.ownerUserId().equals(currentUserId())) {
            throw new SecurityException("knowledge item access denied: " + item.id());
        }
    }

    private Long currentUserId() {
        CurrentUser currentUser = CurrentUserHolder.get();
        return currentUser == null ? 1L : currentUser.userId();
    }

    private OffsetDateTime nextRunAfterManualSuccess(KnowledgeDataSource dataSource) {
        if (!Boolean.TRUE.equals(dataSource.scheduleEnabled())) {
            return null;
        }
        int intervalSeconds = dataSource.scheduleIntervalSeconds() == null ? 300 : dataSource.scheduleIntervalSeconds();
        return OffsetDateTime.now().plusSeconds(intervalSeconds);
    }

    private KnowledgeDataSource findOwnedDataSource(Long dataSourceId) {
        KnowledgeDataSource dataSource = knowledgeBaseRepository.findDataSourceById(dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge data source not found: " + dataSourceId));
        if (!dataSource.ownerUserId().equals(currentUserId())) {
            throw new SecurityException("knowledge data source access denied: " + dataSourceId);
        }
        return dataSource;
    }

    private boolean canConnect(KnowledgeDataSource dataSource) {
        if ("api".equalsIgnoreCase(dataSource.sourceType())) {
            return canConnectApi(dataSource);
        }
        if (!isSupportedJdbcDataSource(dataSource)) {
            return false;
        }
        try (java.sql.Connection connection = DriverManager.getConnection(
                dataSource.endpoint(),
                dataSource.username() == null ? "" : dataSource.username(),
                decryptPassword(dataSource.credentialSecret()))) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(KnowledgeDataSource dataSource, Map<String, Object> request, int timeoutMs) throws Exception {
        Object requestRows = request == null ? null : request.get("sampleRows");
        if (requestRows instanceof List<?> list) {
            return normalizeRows(list);
        }
        List<Map<String, Object>> storedRows = decodeLegacySampleRows(dataSource.credentialSecret());
        if (!storedRows.isEmpty()) {
            return storedRows;
        }
        if ("api".equalsIgnoreCase(dataSource.sourceType())) {
            return extractApiRows(dataSource, timeoutMs);
        }
        if (!isSupportedJdbcDataSource(dataSource) || !canConnect(dataSource) || dataSource.syncQuery() == null || dataSource.syncQuery().isBlank()) {
            throw new IllegalStateException("data source is not extractable");
        }
        try (java.sql.Connection connection = DriverManager.getConnection(
                dataSource.endpoint(),
                dataSource.username() == null ? "" : dataSource.username(),
                decryptPassword(dataSource.credentialSecret()));
             PreparedStatement statement = incrementalStatement(connection, dataSource);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= columnCount; index++) {
                    row.put(resultSet.getMetaData().getColumnLabel(index), resultSet.getObject(index));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private PreparedStatement incrementalStatement(java.sql.Connection connection, KnowledgeDataSource dataSource) throws java.sql.SQLException {
        String cursorColumn = dataSource.cursorColumn();
        if (cursorColumn == null || cursorColumn.isBlank() || dataSource.lastCursor() == null || dataSource.lastCursor().isBlank()) {
            return connection.prepareStatement(dataSource.syncQuery());
        }
        String safeCursorColumn = safeIdentifier(cursorColumn);
        PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM (%s) incremental_source
                WHERE incremental_source.%s > ?
                ORDER BY incremental_source.%s
                """.formatted(dataSource.syncQuery(), safeCursorColumn, safeCursorColumn));
        statement.setObject(1, typedCursorValue(dataSource.lastCursor()));
        return statement;
    }

    private List<Map<String, Object>> normalizeRows(List<?> list) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                rows.add(row);
            }
        }
        return rows;
    }

    private long importRows(KnowledgeDataSource dataSource, List<Map<String, Object>> rows) {
        if (dataSource.knowledgeBaseId() == null) {
            return rows.size();
        }
        ensureKnowledgeBaseOwner(dataSource.knowledgeBaseId());
        long imported = 0L;
        for (Map<String, Object> row : rows) {
            String title = String.valueOf(row.getOrDefault("title", row.getOrDefault("name", "data-source-row-" + (imported + 1)))).trim();
            String content = String.valueOf(row.getOrDefault("content", row)).trim();
            if (title.isBlank() || content.isBlank()) {
                continue;
            }
            KnowledgeItem saved = knowledgeBaseRepository.saveItem(KnowledgeItem.manual(
                    dataSource.knowledgeBaseId(),
                    title,
                    content,
                    "data_source:" + dataSource.sourceType(),
                    currentUserId()
            ));
            publishKnowledgeIndexRequested(saved);
            imported++;
        }
        return imported;
    }

    private boolean canConnectApi(KnowledgeDataSource dataSource) {
        if (dataSource.endpoint() == null
                || dataSource.endpoint().isBlank()
                || !"enabled".equals(dataSource.status())) {
            return false;
        }
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(apiRequest(dataSource), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isSupportedJdbcDataSource(KnowledgeDataSource dataSource) {
        if (dataSource == null
                || dataSource.endpoint() == null
                || dataSource.endpoint().isBlank()
                || !"enabled".equals(dataSource.status())) {
            return false;
        }
        String sourceType = dataSource.sourceType() == null ? "" : dataSource.sourceType().toLowerCase();
        String endpoint = dataSource.endpoint().toLowerCase();
        return switch (sourceType) {
            case "postgresql" -> endpoint.startsWith("jdbc:postgresql://")
                    || endpoint.startsWith("jdbc:h2:mem:");
            case "mysql" -> endpoint.startsWith("jdbc:mysql://")
                    || endpoint.startsWith("jdbc:h2:mem:");
            default -> false;
        };
    }

    private List<Map<String, Object>> extractApiRows(KnowledgeDataSource dataSource, int timeoutMs) throws Exception {
        Map<String, Object> fieldMapping = parseFieldMapping(dataSource.fieldMappingJson());
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        int maxPages = integerValue(fieldMapping.get("maxPages"), 1);
        if (maxPages < 1 || maxPages > 100) {
            throw new IllegalArgumentException("maxPages must be between 1 and 100");
        }
        for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
            HttpResponse<String> response = HTTP_CLIENT.send(apiRequest(dataSource, timeoutMs, fieldMapping, pageIndex), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("api data source returned " + response.statusCode());
            }
            Object payload = OBJECT_MAPPER.readValue(response.body(), Object.class);
            rows.addAll(mapApiRows(payload, fieldMapping));
        }
        return rows;
    }

    private List<Map<String, Object>> mapApiRows(Object payload, Map<String, Object> fieldMapping) {
        Object rowsNode = selectJsonPath(payload, stringValue(fieldMapping.getOrDefault("rowsPath", "")));
        List<Map<String, Object>> rawRows = rowsNode instanceof List<?> list
                ? normalizeRows(list)
                : normalizeRows(List.of(rowsNode));
        String titleField = stringValue(fieldMapping.getOrDefault("titleField", "title")).trim();
        String contentField = stringValue(fieldMapping.getOrDefault("contentField", "content")).trim();
        if (titleField.isBlank()) {
            titleField = "title";
        }
        if (contentField.isBlank()) {
            contentField = "content";
        }
        List<Map<String, Object>> mappedRows = new java.util.ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            Map<String, Object> mapped = new LinkedHashMap<>(row);
            mapped.put("title", stringValue(readFieldPath(row, titleField)));
            mapped.put("content", stringValue(readFieldPath(row, contentField)));
            mappedRows.add(mapped);
        }
        return mappedRows;
    }

    private HttpRequest apiRequest(KnowledgeDataSource dataSource) {
        return apiRequest(dataSource, 5_000, parseFieldMapping(dataSource.fieldMappingJson()), 0);
    }

    private HttpRequest apiRequest(KnowledgeDataSource dataSource, int timeoutMs, Map<String, Object> fieldMapping, int pageIndex) {
        String method = stringValue(fieldMapping.getOrDefault("method", "GET")).trim().toUpperCase();
        if (method.isBlank()) {
            method = "GET";
        }
        if (!Set.of("GET", "POST").contains(method)) {
            throw new IllegalArgumentException("api method must be GET or POST");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiUri(dataSource.endpoint(), fieldMapping, pageIndex))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json");
        applyApiHeaders(builder, fieldMapping);
        String token = decryptPassword(dataSource.credentialSecret());
        String authType = stringValue(fieldMapping.getOrDefault("authType", "bearer")).trim().toLowerCase();
        if (!token.isBlank() && "api_key".equals(authType)) {
            String headerName = stringValue(fieldMapping.getOrDefault("apiKeyHeader", "X-API-Key")).trim();
            builder.header(headerName.isBlank() ? "X-API-Key" : headerName, token);
        } else if (!token.isBlank() && "basic".equals(authType)) {
            String username = dataSource.username() == null ? "" : dataSource.username();
            String encoded = java.util.Base64.getEncoder().encodeToString((username + ":" + token).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        } else if (!token.isBlank() && !"none".equals(authType)) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(stringValue(fieldMapping.getOrDefault("bodyTemplate", ""))));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private URI apiUri(String endpoint, Map<String, Object> fieldMapping, int pageIndex) {
        String pageParam = stringValue(fieldMapping.get("pageParam")).trim();
        if (pageParam.isBlank()) {
            return URI.create(endpoint);
        }
        int pageStart = integerValue(fieldMapping.get("pageStart"), 1);
        int page = pageStart + pageIndex;
        String pageSizeParam = stringValue(fieldMapping.get("pageSizeParam")).trim();
        Integer pageSize = nullableInteger(fieldMapping.get("pageSize"));
        StringBuilder uri = new StringBuilder(endpoint);
        uri.append(endpoint.contains("?") ? "&" : "?")
                .append(encodeQueryParam(pageParam))
                .append("=")
                .append(page);
        if (!pageSizeParam.isBlank() && pageSize != null) {
            uri.append("&")
                    .append(encodeQueryParam(pageSizeParam))
                    .append("=")
                    .append(pageSize);
        }
        return URI.create(uri.toString());
    }

    private void applyApiHeaders(HttpRequest.Builder builder, Map<String, Object> fieldMapping) {
        Object rawHeaders = fieldMapping.get("headers");
        if (!(rawHeaders instanceof Map<?, ?> headers)) {
            return;
        }
        headers.forEach((key, value) -> {
            String headerName = String.valueOf(key).trim();
            String headerValue = stringValue(value).trim();
            if (!headerName.isBlank() && !headerValue.isBlank()
                    && !headerName.equalsIgnoreCase("Authorization")
                    && !headerName.equalsIgnoreCase("Content-Length")
                    && !headerName.equalsIgnoreCase("Host")) {
                builder.header(headerName, headerValue);
            }
        });
    }

    private String encodeQueryParam(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Object selectJsonPath(Object payload, String path) {
        if (path == null || path.isBlank()) {
            return payload;
        }
        Object current = payload;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) {
                continue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return List.of();
            }
            if (current == null) {
                return List.of();
            }
        }
        return current;
    }

    private Object readFieldPath(Map<String, Object> row, String path) {
        return selectJsonPath(row, path);
    }

    private List<Map<String, Object>> filterRowsAfterCursor(KnowledgeDataSource dataSource, List<Map<String, Object>> rows) {
        String cursorColumn = dataSource.cursorColumn();
        String lastCursor = dataSource.lastCursor();
        if (cursorColumn == null || cursorColumn.isBlank() || lastCursor == null || lastCursor.isBlank()) {
            return rows;
        }
        Object previousCursor = typedCursorValue(lastCursor);
        return rows.stream()
                .filter(row -> compareCursor(row.get(cursorColumn), previousCursor) > 0)
                .toList();
    }

    private String resolveNextCursor(KnowledgeDataSource dataSource, List<Map<String, Object>> rows, long processedRows) {
        String cursorColumn = dataSource.cursorColumn();
        if (cursorColumn == null || cursorColumn.isBlank()) {
            return String.valueOf(processedRows);
        }
        Object maxCursor = null;
        for (Map<String, Object> row : rows) {
            Object value = row.get(cursorColumn);
            if (value != null && (maxCursor == null || compareCursor(value, maxCursor) > 0)) {
                maxCursor = value;
            }
        }
        if (maxCursor == null) {
            return dataSource.lastCursor() == null ? "" : dataSource.lastCursor();
        }
        return String.valueOf(maxCursor);
    }

    private int compareCursor(Object left, Object right) {
        Object normalizedLeft = typedCursorValue(left);
        Object normalizedRight = typedCursorValue(right);
        if (normalizedLeft instanceof Number leftNumber && normalizedRight instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (normalizedLeft instanceof Instant leftInstant && normalizedRight instanceof Instant rightInstant) {
            return leftInstant.compareTo(rightInstant);
        }
        return String.valueOf(normalizedLeft).compareTo(String.valueOf(normalizedRight));
    }

    private Object typedCursorValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Instant) {
            return value;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        String text = String.valueOf(value).trim();
        if (text.matches("-?\\d+")) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        if (text.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
            return text;
        }
    }

    private String safeIdentifier(String value) {
        String identifier = value == null ? "" : value.trim();
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("cursorColumn must be a safe SQL identifier");
        }
        return identifier;
    }

    private String decryptPayload(String credentialSecret) {
        return credentialCodec.decrypt(credentialSecret);
    }

    private String decryptPassword(String credentialSecret) {
        String payload = decryptPayload(credentialSecret);
        int marker = payload.indexOf("\n---sampleRows---\n");
        return marker < 0 ? payload : payload.substring(0, marker);
    }

    private List<Map<String, Object>> decodeLegacySampleRows(String credentialSecret) {
        if (credentialCodec.isCurrent(credentialSecret)) {
            return List.of();
        }
        String payload = decryptPayload(credentialSecret);
        int marker = payload.indexOf("\n---sampleRows---\n");
        if (marker < 0) {
            return List.of();
        }
        String encodedRows = payload.substring(marker + "\n---sampleRows---\n".length());
        if (encodedRows.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String line : encodedRows.split("\\R")) {
            String[] parts = line.split("\\|", 2);
            if (parts.length == 2) {
                rows.add(Map.of("title", parts[0], "content", parts[1]));
            }
        }
        return rows;
    }

    private String fieldMappingJson(Object rawFieldMapping) {
        if (rawFieldMapping == null || String.valueOf(rawFieldMapping).isBlank()) {
            return null;
        }
        try {
            if (rawFieldMapping instanceof Map<?, ?> map) {
                return OBJECT_MAPPER.writeValueAsString(map);
            }
            OBJECT_MAPPER.readTree(String.valueOf(rawFieldMapping));
            return String.valueOf(rawFieldMapping);
        } catch (Exception ex) {
            throw new IllegalArgumentException("fieldMapping must be valid JSON object");
        }
    }

    private Map<String, Object> parseFieldMapping(String fieldMappingJson) {
        if (fieldMappingJson == null || fieldMappingJson.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(fieldMappingJson, STRING_OBJECT_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int syncTimeoutMs(Map<String, Object> request) {
        Object value = request == null ? null : request.get("timeoutMs");
        if (value == null || String.valueOf(value).isBlank()) {
            return 5_000;
        }
        int timeoutMs = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        if (timeoutMs < 50 || timeoutMs > 300_000) {
            throw new IllegalArgumentException("timeoutMs must be between 50 and 300000");
        }
        return timeoutMs;
    }

    private String failureReason(Exception ex) {
        if (ex instanceof HttpTimeoutException || ex.getCause() instanceof HttpTimeoutException) {
            return "timeout";
        }
        return "unsupported or unreachable endpoint";
    }

    private void writeDataSourceAudit(String operationType, KnowledgeDataSource dataSource, String result, Map<String, Object> detail) {
        auditRepository.save(new OperationLog(
                null,
                currentUserId(),
                operationType,
                "knowledge_data_source",
                dataSource.id(),
                result,
                detail == null ? Map.of() : detail,
                java.time.OffsetDateTime.now()
        ));
    }

    private static AuditRepository noopAuditRepository() {
        return new AuditRepository() {
            @Override
            public OperationLog save(OperationLog log) {
                return log;
            }

            @Override
            public java.util.Optional<OperationLog> findById(Long id) {
                return java.util.Optional.empty();
            }

            @Override
            public List<OperationLog> findPage(int page, int pageSize) {
                return List.of();
            }

            @Override
            public long count() {
                return 0;
            }
        };
    }

    private static SystemAlertRepository noopSystemAlertRepository() {
        return new SystemAlertRepository() {
            @Override
            public SystemAlert save(SystemAlert alert) {
                return alert;
            }

            @Override
            public List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize) {
                return List.of();
            }

            @Override
            public long countByRecipient(Long recipientUserId, String status) {
                return 0;
            }
        };
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private Integer nullableInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private int integerValue(Object value, int defaultValue) {
        Integer parsed = nullableInteger(value);
        return parsed == null ? defaultValue : parsed;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private OffsetDateTime nullableOffsetDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private Long longValue(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String safeFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank() ? "unknown" : originalFilename.replace("\\", "_").replace("/", "_");
    }

    private String fileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "unknown" : fileName.substring(index + 1).toLowerCase();
    }

    private boolean requiresOcr(String contentType, String fileName) {
        String lowerName = fileName.toLowerCase();
        return contentType.startsWith("image/") || lowerName.endsWith(".pdf");
    }

    private boolean requiresTableRecognition(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv");
    }
}
