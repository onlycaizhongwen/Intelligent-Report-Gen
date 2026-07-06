package com.company.report.report.infrastructure.persistence;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.infrastructure.persistence.JdbcAuditRepository;
import com.company.report.audit.infrastructure.persistence.JdbcDashboardMetricsRepository;
import com.company.report.audit.domain.model.ModelInvocationAudit;
import com.company.report.audit.domain.model.ModelResponseAudit;
import com.company.report.audit.infrastructure.persistence.JdbcModelInvocationRepository;
import com.company.report.knowledge.application.KnowledgeApplicationService;
import com.company.report.knowledge.domain.service.KnowledgeDomainService;
import com.company.report.knowledge.infrastructure.persistence.JdbcKnowledgeBaseRepository;
import com.company.report.knowledge.infrastructure.persistence.JdbcKnowledgeDocumentRepository;
import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.infrastructure.persistence.JdbcSystemAlertRepository;
import com.company.report.permission.application.PermissionApplicationService;
import com.company.report.permission.infrastructure.persistence.JdbcOrganizationDirectoryRepository;
import com.company.report.permission.infrastructure.persistence.JdbcShareLinkRepository;
import com.company.report.permission.infrastructure.persistence.JdbcUserRepository;
import com.company.report.citation.application.CollaborationApplicationService;
import com.company.report.citation.infrastructure.persistence.JdbcCollaborationRepository;
import com.company.report.report.application.ReportApplicationService;
import com.company.report.report.domain.model.ReportGenerationTask;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.repository.ReportExportStorage;
import com.company.report.report.domain.repository.ReportGenerationEventRepository;
import com.company.report.report.domain.service.ReportDomainService;
import com.company.report.rule.application.RuleApplicationService;
import com.company.report.rule.application.RuleProductionScheduler;
import com.company.report.rule.domain.service.RuleDomainService;
import com.company.report.rule.infrastructure.persistence.JdbcRuleRepository;
import com.company.report.shared.api.SseEvent;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.OffsetDateTime;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class JdbcReportGenerationTaskRepositoryPostgresIT {
    private static final String JDBC_URL = env("POSTGRES_IT_JDBC_URL", "jdbc:postgresql://localhost:5432/intelligent_report");
    private static final String USERNAME = env("POSTGRES_IT_USERNAME", "report");
    private static final String PASSWORD = env("POSTGRES_IT_PASSWORD", "report123");

    private static JdbcReportGenerationTaskRepository repository;
    private static MyBatisReportRepository reportRepository;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(JDBC_URL, USERNAME, PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcReportGenerationTaskRepository(jdbcTemplate, new ObjectMapper());
        reportRepository = new MyBatisReportRepository(new ReportMapper() {
            @Override
            public ReportPo findById(Long id) {
                return jdbcTemplate.query("""
                                SELECT id, title, owner_user_id, status, current_version_id
                                FROM reports
                                WHERE id = ? AND deleted_at IS NULL
                                """,
                        (rs, rowNum) -> new ReportPo(
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("owner_user_id"),
                                rs.getString("status"),
                                nullableLong(rs.getObject("current_version_id"))
                        ),
                        id
                ).stream().findFirst().orElse(null);
            }

            @Override
            public List<ReportPo> findByOwner(Long ownerUserId, int pageSize, long offset) {
                return jdbcTemplate.query("""
                                SELECT id, title, owner_user_id, status, current_version_id
                                FROM reports
                                WHERE owner_user_id = ? AND deleted_at IS NULL
                                ORDER BY updated_at DESC, id DESC
                                LIMIT ? OFFSET ?
                                """,
                        (rs, rowNum) -> new ReportPo(
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("owner_user_id"),
                                rs.getString("status"),
                                nullableLong(rs.getObject("current_version_id"))
                        ),
                        ownerUserId,
                        pageSize,
                        offset
                );
            }

            @Override
            public long countByOwner(Long ownerUserId) {
                Long count = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM reports
                                WHERE owner_user_id = ? AND deleted_at IS NULL
                                """,
                        Long.class,
                        ownerUserId
                );
                return count == null ? 0L : count;
            }

            @Override
            public int update(ReportPo report) {
                return jdbcTemplate.update("""
                                UPDATE reports
                                SET title = ?, status = ?, current_version_id = ?, updated_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                                """,
                        report.title(),
                        report.status(),
                        report.currentVersionId(),
                        report.id()
                );
            }
        });
    }

    @Test
    void persistsAndReloadsReportGenerationTaskWithOutlineState() {
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1001L,
                "PostgreSQL 集成验证 " + System.nanoTime(),
                Map.of("period", "2026Q1", "knowledgeBaseIds", List.of(1L, 2L)),
                "postgres-it-" + System.nanoTime()
        ));

        ReportGenerationTask loaded = repository.findById(created.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.status()).isEqualTo("outline_ready");
        assertThat(loaded.currentStage()).isEqualTo("outline");
        assertThat(loaded.outline())
                .containsKey("sections")
                .containsEntry("title", created.outline().get("title"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_outlines WHERE task_id = ?",
                Long.class,
                created.id()
        )).isEqualTo(1L);
    }

    @Test
    void createsDraftReportRecordWhenSavingTaskWithoutReportId() {
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1002L,
                "报告主记录绑定验证 " + System.nanoTime(),
                Map.of("period", "2026Q3"),
                "postgres-report-it-" + System.nanoTime()
        ));

        assertThat(created.reportId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE id = ? AND owner_user_id = ? AND status = 'draft'",
                Long.class,
                created.reportId(),
                1002L
        )).isEqualTo(1L);
        assertThat(reportRepository.findById(created.reportId()))
                .get()
                .extracting("title", "ownerUserId", "status")
                .containsExactly(created.outline().get("title"), 1002L, ReportStatus.DRAFT);
    }

    @Test
    void findsReportsByOwnerWithPaginationAndCountFromPostgres() {
        Long ownerUserId = 2101L;
        Long otherOwnerUserId = 2102L;
        Long firstReportId = insertReport("Owner page first " + System.nanoTime(), ownerUserId, "completed", null);
        Long secondReportId = insertReport("Owner page second " + System.nanoTime(), ownerUserId, "draft", null);
        insertReport("Other owner " + System.nanoTime(), otherOwnerUserId, "completed", null);

        List<com.company.report.report.domain.model.Report> firstPage = reportRepository.findByOwner(ownerUserId, 1, 1);
        List<com.company.report.report.domain.model.Report> secondPage = reportRepository.findByOwner(ownerUserId, 2, 1);

        assertThat(reportRepository.countByOwner(ownerUserId)).isGreaterThanOrEqualTo(2L);
        assertThat(firstPage)
                .hasSize(1)
                .first()
                .extracting("id", "ownerUserId")
                .containsExactly(secondReportId, ownerUserId);
        assertThat(secondPage)
                .extracting(com.company.report.report.domain.model.Report::id)
                .contains(firstReportId);
    }

    @Test
    void rollbackCreatesNewCurrentVersionAndAuditInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        JdbcAuditRepository auditRepository = new JdbcAuditRepository(jdbcTemplate, new ObjectMapper());
        Long reportId = insertReport("Version rollback " + System.nanoTime(), 2103L, "completed", null);
        Long firstVersionId = contentRepository.saveCompletedVersion(reportId, 2103L, List.of(
                Map.of("heading", "V1", "content", "first content", "citations", List.of())
        ));
        Long secondVersionId = contentRepository.saveCompletedVersion(reportId, 2103L, List.of(
                Map.of("heading", "V2", "content", "second content", "citations", List.of())
        ));
        reportRepository.save(new com.company.report.report.domain.model.Report(
                reportId,
                "Version rollback",
                2103L,
                ReportStatus.COMPLETED,
                secondVersionId
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                (eventType, eventKey, payload) -> {
                },
                repository,
                reportRepository,
                contentRepository,
                new JdbcReportGenerationEventRepository(jdbcTemplate, new ObjectMapper()),
                new StubReportExportStorage(),
                new JdbcReportExportFileRepository(jdbcTemplate),
                auditRepository
        );

        Map<String, Object> rollback;
        try {
            CurrentUserHolder.set(new CurrentUser(2103L, Set.of("analyst"), Set.of("report:read")));
            rollback = service.rollbackVersion(reportId, firstVersionId);
        } finally {
            CurrentUserHolder.clear();
        }
        Long newVersionId = ((Number) rollback.get("newVersionId")).longValue();
        List<Map<String, Object>> versions = contentRepository.listVersions(reportId);

        assertThat(versions)
                .extracting(version -> version.get("versionId"))
                .containsExactly(newVersionId, secondVersionId, firstVersionId);
        assertThat(versions.get(0))
                .containsEntry("current", true)
                .containsEntry("changeReason", "rollback");
        assertThat(contentRepository.findCurrentSections(reportId))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("heading", "V1")
                .containsEntry("content", "first content");
        assertThat(versions)
                .filteredOn(version -> version.get("versionId").equals(firstVersionId))
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("current", false);
        assertThat(reportRepository.findById(reportId))
                .get()
                .extracting("currentVersionId")
                .isEqualTo(newVersionId);
        assertThat(auditRepository.findPage(1, 20))
                .filteredOn(log -> "report_version_rollback".equals(log.operationType()))
                .anySatisfy(log -> {
                    assertThat(((Number) log.detail().get("sourceVersionId")).longValue()).isEqualTo(firstVersionId);
                    assertThat(((Number) log.detail().get("newVersionId")).longValue()).isEqualTo(newVersionId);
                });
    }

    @Test
    void comparesVersionSnapshotsFromPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        Long reportId = insertReport("Version diff " + System.nanoTime(), 2104L, "completed", null);
        Long baseVersionId = contentRepository.saveCompletedVersion(reportId, 2104L, List.of(
                Map.of("heading", "Summary", "content", "Revenue grew 8%.", "citations", List.of()),
                Map.of("heading", "Risk", "content", "No material risk.", "citations", List.of())
        ));
        Long targetVersionId = contentRepository.saveCompletedVersion(reportId, 2104L, List.of(
                Map.of("heading", "Summary", "content", "Revenue grew 12%.", "citations", List.of()),
                Map.of("heading", "Outlook", "content", "Expansion planned.", "citations", List.of())
        ));
        reportRepository.save(new com.company.report.report.domain.model.Report(
                reportId,
                "Version diff",
                2104L,
                ReportStatus.COMPLETED,
                targetVersionId
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                (eventType, eventKey, payload) -> {
                },
                repository,
                reportRepository,
                contentRepository,
                new JdbcReportGenerationEventRepository(jdbcTemplate, new ObjectMapper()),
                new StubReportExportStorage(),
                new JdbcReportExportFileRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper())
        );

        Map<String, Object> diff;
        try {
            CurrentUserHolder.set(new CurrentUser(2104L, Set.of("analyst"), Set.of("report:read")));
            diff = service.compareVersions(reportId, baseVersionId, targetVersionId);
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(diff.get("summary"))
                .isEqualTo(Map.of("added", 1, "removed", 1, "modified", 1, "unchanged", 0));
        assertThat((List<Map<String, Object>>) diff.get("changes"))
                .extracting(change -> change.get("heading"))
                .containsExactly("Summary", "Risk", "Outlook");
    }

    @Test
    void updatesExistingOutlineWhenTaskMovesToRetrieval() {
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1001L,
                "确认大纲集成验证 " + System.nanoTime(),
                Map.of("period", "2026Q2"),
                "postgres-confirm-it-" + System.nanoTime()
        ));

        ReportGenerationTask confirmed = repository.save(created.confirmOutline(Map.of(
                "confirmed", true,
                "outline", List.of("执行摘要", "风险与建议")
        )));
        ReportGenerationTask loaded = repository.findById(confirmed.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo("running");
        assertThat(loaded.currentStage()).isEqualTo("retrieval");
        assertThat(loaded.progress()).isEqualTo(10);
        assertThat(loaded.outline()).containsEntry("confirmed", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_outlines WHERE task_id = ?",
                Long.class,
                confirmed.id()
        )).isEqualTo(1L);
    }

    @Test
    void persistsCompletedReportVersionAndSectionsInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1003L,
                "正文版本集成验证 " + System.nanoTime(),
                Map.of("period", "2026Q4"),
                "postgres-content-it-" + System.nanoTime()
        ));

        Long versionId = contentRepository.saveCompletedVersion(created.reportId(), created.createdBy(), List.of(
                Map.of("heading", "执行摘要", "content", "核心指标稳定", "citations", List.of()),
                Map.of("heading", "风险与建议", "content", "建议关注现金流", "citations", List.of())
        ));
        reportRepository.findById(created.reportId())
                .map(report -> new com.company.report.report.domain.model.Report(
                        report.id(),
                        report.title(),
                        report.ownerUserId(),
                        ReportStatus.COMPLETED,
                        versionId
                ))
                .ifPresent(reportRepository::save);
        repository.save(created.complete());

        assertThat(reportRepository.findById(created.reportId()))
                .get()
                .extracting("status", "currentVersionId")
                .containsExactly(ReportStatus.COMPLETED, versionId);
        List<Map<String, Object>> sections = contentRepository.findCurrentSections(created.reportId());
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0))
                .containsEntry("heading", "执行摘要")
                .containsEntry("content", "核心指标稳定");
        assertThat(repository.findById(created.id()))
                .get()
                .extracting(ReportGenerationTask::status, ReportGenerationTask::currentStage, ReportGenerationTask::progress)
                .containsExactly("completed", "export", 100);
    }

    @Test
    void readsReferenceDetailsFromCurrentReportCitationMarksInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        Long ownerUserId = 2111L;
        Long reportId = insertReport("Reference detail " + System.nanoTime(), ownerUserId, "completed", null);
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("referenceId", 77L);
        citation.put("sourceTitle", "East region sales dataset");
        citation.put("sourceType", "knowledge_document");
        citation.put("snapshot", "Revenue increased 12% year over year.");
        citation.put("score", Map.of("credibility", 0.92, "citationQuality", 0.88));
        citation.put("anchor", Map.of("sectionNo", 1, "heading", "Summary", "text", "Revenue increased"));
        Long versionId = contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Summary", "content", "Revenue increased 12% year over year.", "citations", List.of(citation))
        ));
        reportRepository.save(new com.company.report.report.domain.model.Report(
                reportId,
                "Reference detail",
                ownerUserId,
                ReportStatus.COMPLETED,
                versionId
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                (eventType, eventKey, payload) -> {
                },
                repository,
                reportRepository,
                contentRepository,
                new NoopGenerationEventRepository(),
                new StubReportExportStorage(),
                new JdbcReportExportFileRepository(jdbcTemplate)
        );

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:read")));

            Map<String, Object> reference = service.getReference(reportId, 77L);

            assertThat(reference)
                    .containsEntry("reportId", reportId)
                    .containsEntry("referenceId", 77L)
                    .containsEntry("sourceTitle", "East region sales dataset")
                    .containsEntry("sourceType", "knowledge_document")
                    .containsEntry("snapshot", "Revenue increased 12% year over year.")
                    .containsEntry("score", Map.of("credibility", 0.92, "citationQuality", 0.88))
                    .containsEntry("anchor", Map.of("sectionNo", 1, "heading", "Summary", "text", "Revenue increased"));

            CurrentUserHolder.set(new CurrentUser(ownerUserId + 1, Set.of("analyst"), Set.of("report:read")));
            assertThatThrownBy(() -> service.getReference(reportId, 77L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report reference access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsAndReadsGenerationEventsInSequenceWithReferencesPayload() {
        JdbcReportGenerationEventRepository eventRepository = new JdbcReportGenerationEventRepository(jdbcTemplate, new ObjectMapper());
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1004L,
                "事件流集成验证 " + System.nanoTime(),
                Map.of("period", "2026Q4"),
                "postgres-event-it-" + System.nanoTime()
        ));

        eventRepository.append(SseEvent.stage(created.id(), "retrieval", "检索证据", 0.2));
        eventRepository.append(SseEvent.references(
                created.id(),
                List.of(Map.of("referenceId", 77L, "qualityScore", 0.91)),
                "trace-reference-77"
        ));
        eventRepository.append(SseEvent.done(created.id()));

        List<SseEvent> events = eventRepository.findByTaskId(created.id());

        assertThat(events)
                .extracting(SseEvent::type)
                .containsExactly("stage", "references", "done");
        assertThat(events.get(1).references())
                .hasSize(1)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("referenceId", 77)
                .containsEntry("qualityScore", 0.91);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_generation_events WHERE task_id = ?",
                Long.class,
                created.id()
        )).isEqualTo(3L);
    }

    @Test
    void persistsFailureAndRetryableGenerationTaskStateWithErrorEvent() {
        JdbcReportGenerationEventRepository eventRepository = new JdbcReportGenerationEventRepository(jdbcTemplate, new ObjectMapper());
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1007L,
                "retryable failure integration " + System.nanoTime(),
                Map.of("period", "2026Q4"),
                "postgres-failure-it-" + System.nanoTime()
        ).confirmOutline(Map.of("confirmed", true, "outline", List.of("Executive summary"))));

        ReportGenerationTask failed = repository.save(created.fail("AI service unavailable", true));
        eventRepository.append(SseEvent.error(failed.id(), "AI_MODEL_UNAVAILABLE", "AI service unavailable", failed.traceId()));
        ReportGenerationTask loaded = repository.findById(failed.id()).orElseThrow();
        List<SseEvent> events = eventRepository.findByTaskId(failed.id());

        assertThat(loaded.status()).isEqualTo("retryable");
        assertThat(loaded.currentStage()).isEqualTo("retrieval");
        assertThat(loaded.failureReason()).isEqualTo("AI service unavailable");
        assertThat(events)
                .extracting(SseEvent::type)
                .containsExactly("error");
        assertThat(events.get(0).errorCode()).isEqualTo("AI_MODEL_UNAVAILABLE");
    }

    @Test
    void persistsAndReadsReportExportFileMetadataInPostgres() {
        JdbcReportExportFileRepository exportFileRepository = new JdbcReportExportFileRepository(jdbcTemplate);
        ReportGenerationTask created = repository.save(ReportGenerationTask.naturalLanguage(
                1005L,
                "导出文件记录集成验证 " + System.nanoTime(),
                Map.of("period", "2026Q4"),
                "postgres-export-it-" + System.nanoTime()
        ));

        Map<String, Object> exportFile = new LinkedHashMap<>();
        exportFile.put("reportId", created.reportId());
        exportFile.put("status", "completed");
        exportFile.put("format", "markdown");
        exportFile.put("templateId", "enterprise-default");
        exportFile.put("downloadPolicy", "presigned_url");
        exportFile.put("bucket", "report-bucket");
        exportFile.put("objectKey", "reports/" + created.reportId() + "/exports/export.md");
        exportFile.put("fileName", "export.md");
        exportFile.put("contentType", "text/markdown; charset=UTF-8");
        exportFile.put("sizeBytes", 128L);
        exportFile.put("downloadUrl", "http://localhost:9000/report-bucket/export.md");
        exportFile.put("expiresAt", java.time.OffsetDateTime.now().plusMinutes(30).toString());
        Map<String, Object> saved = exportFileRepository.save(exportFile);

        Map<String, Object> loaded = exportFileRepository
                .findByReportIdAndExportFileId(created.reportId(), (Long) saved.get("exportFileId"))
                .orElseThrow();
        Map<String, Object> loadedByFileId = exportFileRepository
                .findByExportFileId((Long) saved.get("exportFileId"))
                .orElseThrow();

        assertThat(loaded)
                .containsEntry("reportId", created.reportId())
                .containsEntry("status", "completed")
                .containsEntry("format", "markdown")
                .containsEntry("objectKey", "reports/" + created.reportId() + "/exports/export.md")
                .containsEntry("sizeBytes", 128L);
        assertThat(loadedByFileId)
                .containsEntry("exportFileId", saved.get("exportFileId"))
                .containsEntry("reportId", created.reportId())
                .containsEntry("fileName", "export.md");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_export_files WHERE report_id = ?",
                Long.class,
                created.reportId()
        )).isEqualTo(1L);
    }

    @Test
    void createsControlledDownloadUrlWithOwnerCheckAndAuditInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        JdbcReportExportFileRepository exportFileRepository = new JdbcReportExportFileRepository(jdbcTemplate);
        JdbcAuditRepository auditRepository = new JdbcAuditRepository(jdbcTemplate, new ObjectMapper());
        Long ownerUserId = 2110L;
        Long reportId = insertReport("Controlled download " + System.nanoTime(), ownerUserId, "completed", null);
        Long versionId = contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Summary", "content", "download content", "citations", List.of())
        ));
        reportRepository.save(new com.company.report.report.domain.model.Report(
                reportId,
                "Controlled download",
                ownerUserId,
                ReportStatus.COMPLETED,
                versionId
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                (eventType, eventKey, payload) -> {
                },
                repository,
                reportRepository,
                contentRepository,
                new NoopGenerationEventRepository(),
                new StubReportExportStorage(),
                exportFileRepository,
                auditRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId + 1, Set.of("analyst"), Set.of("report:export")));
            assertThatThrownBy(() -> service.createExport(reportId, Map.of("format", "markdown", "templateId", "enterprise-default")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report export access denied");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report_export_files WHERE report_id = ?",
                    Long.class,
                    reportId
            )).isEqualTo(0L);

            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:export")));
            Map<String, Object> exported = service.createExport(reportId, Map.of("format", "markdown", "templateId", "enterprise-default"));
            Map<String, Object> download = service.createExportDownloadUrl((Long) exported.get("exportFileId"));

            assertThat(exported.get("downloadUrl"))
                    .isEqualTo("/api/v1/files/report-exports/" + exported.get("exportFileId") + "/download-url");
            assertThat(download)
                    .containsEntry("reportId", reportId)
                    .containsEntry("exportFileId", exported.get("exportFileId"));
            assertThat(download.get("downloadUrl").toString()).startsWith("https://storage.local/");
            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "report_export_download".equals(log.operationType()))
                    .filteredOn(log -> ((Number) log.detail().get("reportId")).longValue() == reportId)
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.actorUserId()).isEqualTo(ownerUserId);
                        assertThat(log.resourceType()).isEqualTo("report_export_file");
                        assertThat(log.resourceId()).isEqualTo(exported.get("exportFileId"));
                    });

            CurrentUserHolder.set(new CurrentUser(ownerUserId + 1, Set.of("analyst"), Set.of("report:export")));
            assertThatThrownBy(() -> service.createExportDownloadUrl((Long) exported.get("exportFileId")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report export file access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsAndAccessesShareLinkInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(new JdbcShareLinkRepository(jdbcTemplate), reportRepository);
        Long reportId = insertReport("Share link " + System.nanoTime(), 2112L, "completed", null);

        try {
            CurrentUserHolder.set(new CurrentUser(2113L, Set.of("analyst"), Set.of("report:share")));
            assertThatThrownBy(() -> service.createShare(reportId, Map.of()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report share access denied");

            CurrentUserHolder.set(new CurrentUser(2112L, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of("expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()));
            String shareToken = String.valueOf(created.get("shareToken"));
            Map<String, Object> accessed = service.accessShare(shareToken, Map.of("visitor", "external@example.com"));

            assertThat(created)
                    .containsEntry("reportId", reportId)
                    .containsEntry("createdBy", 2112L)
                    .containsEntry("status", "active");
            assertThat(created.get("shareUrl").toString()).isEqualTo("/share/" + shareToken);
            assertThat(accessed)
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId)
                    .containsEntry("shareToken", shareToken);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM share_links WHERE share_token = ? AND report_id = ?",
                    Long.class,
                    shareToken,
                    reportId
            )).isEqualTo(1L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void protectsPasswordShareAndWritesAccessAuditInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper())
        );
        Long ownerUserId = 2114L;
        Long reportId = insertReport("Password share link " + System.nanoTime(), ownerUserId, "completed", null);

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM share_links WHERE share_token = ?",
                    String.class,
                    shareToken
            ))
                    .isNotBlank()
                    .isNotEqualTo("ExternalPass#1");

            assertThatThrownBy(() -> service.accessShare(shareToken, Map.of("password", "wrong", "visitor", "external@example.com")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share password invalid");

            Map<String, Object> accessed = service.accessShare(shareToken, Map.of("password", "ExternalPass#1", "visitor", "external@example.com"));
            Map<String, Object> revoked = service.revokeShare(shareToken);

            assertThat(accessed).containsEntry("accessGranted", true).containsEntry("reportId", reportId);
            assertThat(revoked).containsEntry("status", "revoked");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM share_links WHERE share_token = ?",
                    String.class,
                    shareToken
            )).isEqualTo("revoked");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM operation_logs WHERE resource_type = 'share_link' AND operation_type IN ('share_access_failed', 'share_access', 'share_revoke')",
                    Long.class
            )).isGreaterThanOrEqualTo(3L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsShareDownloadPolicyAndCreatesSharedDownloadUrlInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        JdbcReportExportFileRepository exportFileRepository = new JdbcReportExportFileRepository(jdbcTemplate);
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository,
                exportFileRepository,
                new StubReportExportStorage()
        );
        Long ownerUserId = 2119L;
        Long reportId = insertReport("Share download policy " + System.nanoTime(), ownerUserId, "completed", null);
        Map<String, Object> exportFileRequest = new LinkedHashMap<>();
        exportFileRequest.put("reportId", reportId);
        exportFileRequest.put("status", "completed");
        exportFileRequest.put("format", "markdown");
        exportFileRequest.put("templateId", "enterprise-default");
        exportFileRequest.put("downloadPolicy", "presigned_url");
        exportFileRequest.put("bucket", "report-bucket");
        exportFileRequest.put("objectKey", "reports/" + reportId + "/exports/share-policy.md");
        exportFileRequest.put("fileName", "share-policy.md");
        exportFileRequest.put("contentType", "text/markdown; charset=UTF-8");
        exportFileRequest.put("sizeBytes", 256L);
        exportFileRequest.put("downloadUrl", "https://storage.local/reports/" + reportId + "/exports/share-policy.md");
        exportFileRequest.put("expiresAt", java.time.OffsetDateTime.now().plusMinutes(30).toString());
        Map<String, Object> exportFile = exportFileRepository.save(exportFileRequest);

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Map<String, Object> download = service.sharedExportDownloadUrl(
                    shareToken,
                    ((Number) exportFile.get("exportFileId")).longValue(),
                    Map.of("password", "ExternalPass#1", "visitor", "external@example.com")
            );
            Map<String, Object> sharedReport = service.sharedReport(
                    shareToken,
                    Map.of("password", "ExternalPass#1", "visitor", "external@example.com")
            );

            assertThat(created).containsEntry("allowDownload", true);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT allow_download FROM share_links WHERE share_token = ?",
                    Boolean.class,
                    shareToken
            )).isTrue();
            assertThat(download)
                    .containsEntry("reportId", reportId)
                    .containsEntry("exportFileId", exportFile.get("exportFileId"))
                    .containsEntry("downloadPolicy", "share_presigned_url");
            assertThat(download.get("downloadUrl").toString()).contains("presigned=true");
            assertThat(sharedReport)
                    .containsEntry("allowDownload", true);
            assertThat(sharedReport.get("exports"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("exportFileId", exportFile.get("exportFileId"))
                    .containsEntry("fileName", "share-policy.md")
                    .containsEntry("format", "markdown");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM operation_logs WHERE operation_type = 'share_export_download' AND resource_type = 'share_link'",
                    Long.class
            )).isGreaterThanOrEqualTo(1L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsShareDownloadFormatScopeAndEnforcesItInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        JdbcReportExportFileRepository exportFileRepository = new JdbcReportExportFileRepository(jdbcTemplate);
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository,
                exportFileRepository,
                new StubReportExportStorage()
        );
        Long ownerUserId = 2123L;
        Long reportId = insertReport("Share download format scope " + System.nanoTime(), ownerUserId, "completed", null);
        Map<String, Object> markdownExport = new LinkedHashMap<>();
        markdownExport.put("reportId", reportId);
        markdownExport.put("status", "completed");
        markdownExport.put("format", "markdown");
        markdownExport.put("templateId", "enterprise-default");
        markdownExport.put("downloadPolicy", "presigned_url");
        markdownExport.put("bucket", "report-bucket");
        markdownExport.put("objectKey", "reports/" + reportId + "/exports/share-scope.md");
        markdownExport.put("fileName", "share-scope.md");
        markdownExport.put("contentType", "text/markdown; charset=UTF-8");
        markdownExport.put("sizeBytes", 128L);
        markdownExport.put("downloadUrl", "https://storage.local/reports/" + reportId + "/exports/share-scope.md");
        markdownExport.put("expiresAt", java.time.OffsetDateTime.now().plusMinutes(30).toString());
        Map<String, Object> savedMarkdownExport = exportFileRepository.save(markdownExport);
        Map<String, Object> pdfExport = new LinkedHashMap<>();
        pdfExport.put("reportId", reportId);
        pdfExport.put("status", "completed");
        pdfExport.put("format", "pdf");
        pdfExport.put("templateId", "enterprise-default");
        pdfExport.put("downloadPolicy", "presigned_url");
        pdfExport.put("bucket", "report-bucket");
        pdfExport.put("objectKey", "reports/" + reportId + "/exports/share-scope.pdf");
        pdfExport.put("fileName", "share-scope.pdf");
        pdfExport.put("contentType", "application/pdf");
        pdfExport.put("sizeBytes", 256L);
        pdfExport.put("downloadUrl", "https://storage.local/reports/" + reportId + "/exports/share-scope.pdf");
        pdfExport.put("expiresAt", java.time.OffsetDateTime.now().plusMinutes(30).toString());
        Map<String, Object> savedPdfExport = exportFileRepository.save(pdfExport);

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "allowedDownloadFormats", List.of("markdown"),
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();
            Long markdownExportFileId = ((Number) savedMarkdownExport.get("exportFileId")).longValue();
            Long pdfExportFileId = ((Number) savedPdfExport.get("exportFileId")).longValue();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT allowed_download_formats ->> 0 FROM share_links WHERE share_token = ?",
                    String.class,
                    shareToken
            )).isEqualTo("markdown");

            Map<String, Object> sharedReport = service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));
            assertThat(sharedReport.get("exports"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("exportFileId", markdownExportFileId)
                    .containsEntry("format", "markdown");
            assertThatThrownBy(() -> service.sharedExportDownloadUrl(shareToken, pdfExportFileId, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share download format access denied");
            assertThat(service.sharedExportDownloadUrl(shareToken, markdownExportFileId, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ))).containsEntry("downloadPolicy", "share_presigned_url");

            Map<String, Object> deniedAudit = jdbcTemplate.queryForMap("""
                    SELECT detail ->> 'reason' AS reason,
                           detail ->> 'exportFileId' AS export_file_id,
                           detail ->> 'format' AS format
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_download_denied'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);
            assertThat(deniedAudit)
                    .containsEntry("reason", "download_format_not_allowed")
                    .containsEntry("export_file_id", String.valueOf(pdfExportFileId))
                    .containsEntry("format", "pdf");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsShareMaxAccessCountAndEnforcesItInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2124L;
        Long reportId = insertReport("Share max access count " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive Summary", "content", "One-time customer share")
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "maxAccessCount", 1,
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT max_access_count FROM share_links WHERE share_token = ?",
                    Integer.class,
                    shareToken
            )).isEqualTo(1);
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access count exceeded");

            Map<String, Object> limitAudit = jdbcTemplate.queryForMap("""
                    SELECT detail ->> 'reason' AS reason,
                           detail ->> 'maxAccessCount' AS max_access_count,
                           detail ->> 'accessCount' AS access_count
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_limit_exceeded'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);
            assertThat(limitAudit)
                    .containsEntry("reason", "max_access_count_exceeded")
                    .containsEntry("max_access_count", "1")
                    .containsEntry("access_count", "1");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsShareVisitorScopeAndEnforcesItInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2125L;
        Long reportId = insertReport("Share visitor scope " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive Summary", "content", "Scoped customer share")
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "allowedVisitors", List.of("external@example.com"),
                    "allowedVisitorDomains", List.of("partner.com"),
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT allowed_visitors ->> 0 FROM share_links WHERE share_token = ?",
                    String.class,
                    shareToken
            )).isEqualTo("external@example.com");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT allowed_visitor_domains ->> 0 FROM share_links WHERE share_token = ?",
                    String.class,
                    shareToken
            )).isEqualTo("partner.com");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "reviewer@partner.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "intruder@evil.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access scope denied");

            Map<String, Object> scopeAudit = jdbcTemplate.queryForMap("""
                    SELECT detail ->> 'reason' AS reason,
                           detail ->> 'visitor' AS visitor
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_scope_denied'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);
            assertThat(scopeAudit)
                    .containsEntry("reason", "visitor_not_allowed")
                    .containsEntry("visitor", "intruder@evil.com");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsSingleUseShareAndEnforcesItInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2126L;
        Long reportId = insertReport("Single use share " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive Summary", "content", "Single-use customer share")
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "singleUse", true,
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT single_use FROM share_links WHERE share_token = ?",
                    Boolean.class,
                    shareToken
            )).isTrue();
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share single use already consumed");

            Map<String, Object> consumedAudit = jdbcTemplate.queryForMap("""
                    SELECT detail ->> 'reason' AS reason,
                           detail ->> 'visitor' AS visitor,
                           detail ->> 'accessCount' AS access_count
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_single_use_consumed'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);
            assertThat(consumedAudit)
                    .containsEntry("reason", "single_use_consumed")
                    .containsEntry("visitor", "external@example.com")
                    .containsEntry("access_count", "1");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        JdbcReportExportFileRepository exportFileRepository = new JdbcReportExportFileRepository(jdbcTemplate);
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository,
                exportFileRepository,
                new StubReportExportStorage()
        );
        Long ownerUserId = 2120L;
        Long reportId = insertReport("External share audit " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive summary", "content", "Revenue is stable.", "citations", List.of())
        ));
        Map<String, Object> exportFileRequest = new LinkedHashMap<>();
        exportFileRequest.put("reportId", reportId);
        exportFileRequest.put("status", "completed");
        exportFileRequest.put("format", "pdf");
        exportFileRequest.put("templateId", "enterprise-default");
        exportFileRequest.put("downloadPolicy", "presigned_url");
        exportFileRequest.put("bucket", "report-bucket");
        exportFileRequest.put("objectKey", "reports/" + reportId + "/exports/external-share-audit.pdf");
        exportFileRequest.put("fileName", "external-share-audit.pdf");
        exportFileRequest.put("contentType", "application/pdf");
        exportFileRequest.put("sizeBytes", 512L);
        exportFileRequest.put("downloadUrl", "https://storage.local/reports/" + reportId + "/exports/external-share-audit.pdf");
        exportFileRequest.put("expiresAt", java.time.OffsetDateTime.now().plusMinutes(30).toString());
        Map<String, Object> exportFile = exportFileRepository.save(exportFileRequest);

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();
            Long exportFileId = ((Number) exportFile.get("exportFileId")).longValue();

            CurrentUserHolder.clear();
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "wrong",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share password invalid");
            Map<String, Object> sharedReport = service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));
            Map<String, Object> download = service.sharedExportDownloadUrl(shareToken, exportFileId, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));

            assertThat(sharedReport).containsEntry("accessGranted", true).containsEntry("reportId", reportId);
            assertThat(download)
                    .containsEntry("exportFileId", exportFileId)
                    .containsEntry("downloadPolicy", "share_presigned_url");
            List<Map<String, Object>> auditRows = jdbcTemplate.queryForList("""
                    SELECT operation_type,
                           actor_user_id,
                           resource_type,
                           resource_id,
                           result,
                           detail ->> 'reason' AS reason,
                           detail ->> 'visitor' AS visitor,
                           detail ->> 'shareToken' AS share_token,
                           detail ->> 'reportId' AS report_id,
                           detail ->> 'exportFileId' AS export_file_id,
                           detail ->> 'downloadPolicy' AS download_policy
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type IN ('share_access_failed', 'share_report_view', 'share_export_download')
                    ORDER BY id
                    """, shareLinkId);

            assertThat(auditRows)
                    .extracting(row -> row.get("operation_type"))
                    .containsExactly("share_access_failed", "share_report_view", "share_export_download");
            assertThat(auditRows)
                    .allSatisfy(row -> {
                        assertThat(((Number) row.get("actor_user_id")).longValue()).isEqualTo(ownerUserId);
                        assertThat(row.get("resource_type")).isEqualTo("share_link");
                        assertThat(((Number) row.get("resource_id")).longValue()).isEqualTo(shareLinkId);
                        assertThat(row.get("share_token")).isEqualTo(shareToken);
                        assertThat(row.get("report_id")).isEqualTo(String.valueOf(reportId));
                    });
            assertThat(auditRows.get(0))
                    .containsEntry("result", "failed")
                    .containsEntry("reason", "invalid_password")
                    .containsEntry("visitor", "external@example.com");
            assertThat(auditRows.get(1))
                    .containsEntry("result", "succeeded")
                    .containsEntry("visitor", "external@example.com");
            assertThat(auditRows.get(2))
                    .containsEntry("result", "succeeded")
                    .containsEntry("visitor", "external@example.com")
                    .containsEntry("export_file_id", String.valueOf(exportFileId))
                    .containsEntry("download_policy", "share_presigned_url");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rateLimitsRepeatedInvalidSharePasswordAttemptsInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2121L;
        Long reportId = insertReport("External share rate limit " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive summary", "content", "Rate limited shared content.", "citations", List.of())
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            CurrentUserHolder.clear();
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                        "password", "wrong",
                        "visitor", "external@example.com"
                )))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access rate limited");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "another@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);

            Map<String, Object> rateLimitedAudit = jdbcTemplate.queryForMap("""
                    SELECT actor_user_id,
                           resource_type,
                           resource_id,
                           result,
                           detail ->> 'reason' AS reason,
                           detail ->> 'visitor' AS visitor,
                           detail ->> 'failedAttempts' AS failed_attempts,
                           detail ->> 'windowMinutes' AS window_minutes
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_rate_limited'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);

            assertThat(((Number) rateLimitedAudit.get("actor_user_id")).longValue()).isEqualTo(ownerUserId);
            assertThat(rateLimitedAudit)
                    .containsEntry("resource_type", "share_link")
                    .containsEntry("resource_id", shareLinkId)
                    .containsEntry("result", "failed")
                    .containsEntry("reason", "too_many_invalid_password_attempts")
                    .containsEntry("visitor", "external@example.com")
                    .containsEntry("failed_attempts", "5")
                    .containsEntry("window_minutes", "15");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void requiresShareChallengeAfterRepeatedInvalidPasswordAttemptsInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2124L;
        Long reportId = insertReport("External share challenge " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive summary", "content", "Challenge protected shared content.", "citations", List.of())
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            CurrentUserHolder.clear();
            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                        "password", "wrong",
                        "visitor", "external@example.com"
                )))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access challenge required");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com",
                    "challengeAnswer", "REPORT"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);

            Map<String, Object> challengeAudit = jdbcTemplate.queryForMap("""
                    SELECT actor_user_id,
                           resource_type,
                           resource_id,
                           result,
                           detail ->> 'reason' AS reason,
                           detail ->> 'visitor' AS visitor,
                           detail ->> 'challengeType' AS challenge_type,
                           detail ->> 'challengePrompt' AS challenge_prompt,
                           detail ->> 'failedAttempts' AS failed_attempts
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_challenge_required'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);

            assertThat(((Number) challengeAudit.get("actor_user_id")).longValue()).isEqualTo(ownerUserId);
            assertThat(challengeAudit)
                    .containsEntry("resource_type", "share_link")
                    .containsEntry("resource_id", shareLinkId)
                    .containsEntry("result", "failed")
                    .containsEntry("reason", "challenge_required")
                    .containsEntry("visitor", "external@example.com")
                    .containsEntry("challenge_type", "text")
                    .containsEntry("challenge_prompt", "Type REPORT to continue")
                    .containsEntry("failed_attempts", "3");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprintInPostgres() {
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                contentRepository
        );
        Long ownerUserId = 2122L;
        Long reportId = insertReport("External share risk fingerprint " + System.nanoTime(), ownerUserId, "completed", null);
        contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                Map.of("heading", "Executive summary", "content", "Risk fingerprint limited shared content.", "citations", List.of())
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(reportId, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", java.time.OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));
            Long shareLinkId = ((Number) created.get("shareLinkId")).longValue();

            CurrentUserHolder.clear();
            for (int i = 0; i < 5; i++) {
                int attempt = i;
                assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                        "password", "wrong",
                        "visitor", "external-risk-" + attempt + "@example.com",
                        "clientIp", "203.0.113.20",
                        "userAgent", "Mozilla/5.0 PostgreSQL Risk Browser"
                )))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "new-external-risk@example.com",
                    "clientIp", "203.0.113.20",
                    "userAgent", "Mozilla/5.0 PostgreSQL Risk Browser"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access rate limited");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "new-external-risk@example.com",
                    "clientIp", "203.0.113.21",
                    "userAgent", "Mozilla/5.0 PostgreSQL Risk Browser"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", reportId);

            Map<String, Object> rateLimitedAudit = jdbcTemplate.queryForMap("""
                    SELECT actor_user_id,
                           resource_type,
                           resource_id,
                           result,
                           detail ->> 'reason' AS reason,
                           detail ->> 'clientIp' AS client_ip,
                           detail ->> 'userAgent' AS user_agent,
                           detail ->> 'riskFingerprint' AS risk_fingerprint,
                           detail ->> 'failedAttempts' AS failed_attempts,
                           detail ->> 'windowMinutes' AS window_minutes
                    FROM operation_logs
                    WHERE resource_type = 'share_link'
                      AND resource_id = ?
                      AND operation_type = 'share_access_rate_limited'
                    ORDER BY id DESC
                    LIMIT 1
                    """, shareLinkId);

            assertThat(((Number) rateLimitedAudit.get("actor_user_id")).longValue()).isEqualTo(ownerUserId);
            assertThat(rateLimitedAudit)
                    .containsEntry("resource_type", "share_link")
                    .containsEntry("resource_id", shareLinkId)
                    .containsEntry("result", "failed")
                    .containsEntry("reason", "too_many_invalid_password_attempts")
                    .containsEntry("client_ip", "203.0.113.20")
                    .containsEntry("user_agent", "Mozilla/5.0 PostgreSQL Risk Browser")
                    .containsEntry("failed_attempts", "5")
                    .containsEntry("window_minutes", "15");
            assertThat(String.valueOf(rateLimitedAudit.get("risk_fingerprint"))).isNotBlank();
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void persistsUsersAndRbacMatrixInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate)
        );
        String username = "postgres.user." + System.nanoTime();

        Map<String, Object> created = service.addUser(Map.of(
                "username", username,
                "displayName", "Postgres User",
                "department", "Finance Center",
                "position", "Senior Analyst",
                "roles", List.of("analyst")
        ));
        Long userId = ((Number) created.get("userId")).longValue();
        Map<String, Object> disabled = service.updateStatus(userId, Map.of("status", "disabled"));
        var users = service.users(1, 50);
        Map<String, Object> matrix = service.permissionMatrix();

        assertThat(created)
                .containsEntry("username", username)
                .containsEntry("displayName", "Postgres User")
                .containsEntry("department", "Finance Center")
                .containsEntry("position", "Senior Analyst")
                .containsEntry("status", "enabled");
        assertThat(disabled)
                .containsEntry("userId", userId)
                .containsEntry("status", "disabled");
        assertThat(users.items())
                .filteredOn(item -> userId.equals(((Number) item.get("userId")).longValue()))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("username", username)
                .containsEntry("department", "Finance Center")
                .containsEntry("position", "Senior Analyst")
                .containsEntry("status", "disabled");
        assertThat(matrix.get("permissions")).asList().contains("user:manage", "knowledge:upload");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM user_accounts
                        WHERE id = ?
                          AND status = 'disabled'
                          AND department = 'Finance Center'
                          AND position = 'Senior Analyst'
                        """,
                Long.class,
                userId
        )).isEqualTo(1L);

        String enabledUsername = "postgres.enabled.user." + System.nanoTime();
        Map<String, Object> enabled = service.addUser(Map.of(
                "username", enabledUsername,
                "displayName", "Enabled Approver",
                "department", "Risk Office",
                "position", "Approval Lead",
                "roles", List.of("risk_manager")
        ));
        Long enabledUserId = ((Number) enabled.get("userId")).longValue();
        JdbcUserRepository userRepository = new JdbcUserRepository(jdbcTemplate);
        assertThat(userRepository.findEnabledByRole("risk_manager"))
                .filteredOn(user -> enabledUserId.equals(user.id()))
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.department()).isEqualTo("Risk Office");
                    assertThat(user.position()).isEqualTo("Approval Lead");
                });
    }

    @Test
    void persistsOrganizationDirectoryTreeInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long managerUserId = ((Number) service.addUser(Map.of(
                "username", "org.manager." + suffix,
                "displayName", "Org Manager",
                "department", "Finance Center",
                "position", "Finance Manager",
                "roles", List.of("finance_manager")
        )).get("userId")).longValue();
        Long rootId = insertOrganizationUnit("ORG-HQ-" + suffix, "Headquarters " + suffix, null, "company", 10);
        Long financeId = insertOrganizationUnit("ORG-FIN-" + suffix, "Finance Center " + suffix, rootId, "department", 20);
        Long riskId = insertOrganizationUnit("ORG-RISK-" + suffix, "Finance Risk Team " + suffix, financeId, "team", 30);
        Long disabledId = insertOrganizationUnit("ORG-OFF-" + suffix, "Disabled Office " + suffix, rootId, "department", 40);
        insertOrganizationPosition(financeId, "finance_manager_" + suffix, "Finance Manager", List.of("finance_manager"), managerUserId, "enabled", 10);
        insertOrganizationPosition(riskId, "risk_approver_" + suffix, "Risk Approver", List.of("risk_manager", "finance_delegate"), null, "enabled", 20);
        insertOrganizationPosition(disabledId, "disabled_position_" + suffix, "Disabled Position", List.of("disabled_role"), null, "enabled", 30);
        jdbcTemplate.update("UPDATE organization_units SET status = 'disabled' WHERE id = ?", disabledId);

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(root -> assertThat(root)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "ORG-HQ-" + suffix)
                        .satisfies(item -> assertThat(item.get("children"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("code", "ORG-FIN-" + suffix)
                                .satisfies(finance -> {
                                    assertThat(finance.get("positions"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .singleElement()
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                            .containsEntry("code", "finance_manager_" + suffix)
                                            .containsEntry("managerUserId", managerUserId);
                                    assertThat(finance.get("children"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .singleElement()
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                            .containsEntry("code", "ORG-RISK-" + suffix);
                                })));
        assertThat(directory.toString()).doesNotContain("Disabled Office " + suffix, "disabled_role");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organization_units WHERE code = ?", Long.class, "ORG-HQ-" + suffix))
                .isEqualTo(1L);
    }

    @Test
    void createsOrganizationMaintenanceRecordsThroughServiceInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long managerUserId = ((Number) service.addUser(Map.of(
                "username", "org.maintenance.manager." + suffix,
                "displayName", "Organization Manager",
                "department", "Management Office",
                "position", "Organization Lead",
                "roles", List.of("organization_manager")
        )).get("userId")).longValue();

        Map<String, Object> root = service.createOrganizationUnit(Map.of(
                "code", "ORG-MAINT-HQ-" + suffix,
                "name", "Maintenance Headquarters " + suffix,
                "unitType", "company",
                "sortOrder", 11
        ));
        Long rootId = ((Number) root.get("unitId")).longValue();
        Map<String, Object> department = service.createOrganizationUnit(Map.of(
                "code", "ORG-MAINT-FIN-" + suffix,
                "name", "Maintenance Finance " + suffix,
                "parentId", rootId,
                "unitType", "department",
                "sortOrder", 12
        ));
        Long departmentId = ((Number) department.get("unitId")).longValue();
        Map<String, Object> position = service.createOrganizationPosition(Map.of(
                "organizationUnitId", departmentId,
                "code", "maintenance_finance_manager_" + suffix,
                "name", "Maintenance Finance Manager",
                "roles", List.of("finance_manager", "organization_manager"),
                "managerUserId", managerUserId,
                "sortOrder", 13
        ));

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(position)
                .containsEntry("organizationUnitId", departmentId)
                .containsEntry("managerUserId", managerUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_units WHERE code IN (?, ?)",
                Long.class,
                "ORG-MAINT-HQ-" + suffix,
                "ORG-MAINT-FIN-" + suffix
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_positions WHERE code = ? AND organization_unit_id = ?",
                Long.class,
                "maintenance_finance_manager_" + suffix,
                departmentId
        )).isEqualTo(1L);
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(rootNode -> assertThat(rootNode)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "ORG-MAINT-HQ-" + suffix)
                        .satisfies(item -> assertThat(item.get("children"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("code", "ORG-MAINT-FIN-" + suffix)
                                .satisfies(child -> assertThat(child.get("positions"))
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                        .singleElement()
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("code", "maintenance_finance_manager_" + suffix)
                                        .containsEntry("managerUserId", managerUserId))));
    }

    @Test
    void updatesOrganizationUnitHierarchyInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long rootId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-UNIT-UPD-HQ-" + suffix,
                "name", "Unit Update Headquarters " + suffix,
                "unitType", "company",
                "sortOrder", 51
        )).get("unitId")).longValue();
        Long financeId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-UNIT-UPD-FIN-" + suffix,
                "name", "Unit Update Finance " + suffix,
                "parentId", rootId,
                "unitType", "department",
                "sortOrder", 52
        )).get("unitId")).longValue();
        Long riskId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-UNIT-UPD-RISK-" + suffix,
                "name", "Unit Update Risk Office " + suffix,
                "parentId", rootId,
                "unitType", "department",
                "sortOrder", 53
        )).get("unitId")).longValue();

        Map<String, Object> updated = service.updateOrganizationUnit(riskId, Map.of(
                "name", "Unit Update Risk Team " + suffix,
                "parentId", financeId,
                "unitType", "team",
                "sortOrder", 5
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(updated)
                .containsEntry("unitId", riskId)
                .containsEntry("code", "ORG-UNIT-UPD-RISK-" + suffix)
                .containsEntry("name", "Unit Update Risk Team " + suffix)
                .containsEntry("parentId", financeId)
                .containsEntry("unitType", "team")
                .containsEntry("sortOrder", 5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT parent_id FROM organization_units WHERE id = ?",
                Long.class,
                riskId
        )).isEqualTo(financeId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM organization_units WHERE id = ?",
                String.class,
                riskId
        )).isEqualTo("Unit Update Risk Team " + suffix);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT unit_type FROM organization_units WHERE id = ?",
                String.class,
                riskId
        )).isEqualTo("team");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sort_order FROM organization_units WHERE id = ?",
                Integer.class,
                riskId
        )).isEqualTo(5);
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(root -> assertThat(root)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "ORG-UNIT-UPD-HQ-" + suffix)
                        .satisfies(rootNode -> assertThat(rootNode.get("children"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("code", "ORG-UNIT-UPD-FIN-" + suffix)
                                .satisfies(finance -> assertThat(finance.get("children"))
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                        .singleElement()
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("code", "ORG-UNIT-UPD-RISK-" + suffix)
                                        .containsEntry("name", "Unit Update Risk Team " + suffix))));
    }

    @Test
    void persistsOrganizationPositionAssignmentsThroughServiceInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long userId = ((Number) service.addUser(Map.of(
                "username", "assigned.position.user." + suffix,
                "displayName", "Assigned Position User",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long rootId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-ASSIGN-HQ-" + suffix,
                "name", "Assignment Headquarters " + suffix,
                "unitType", "company",
                "sortOrder", 21
        )).get("unitId")).longValue();
        Long financeId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-ASSIGN-FIN-" + suffix,
                "name", "Assignment Finance " + suffix,
                "parentId", rootId,
                "unitType", "department",
                "sortOrder", 22
        )).get("unitId")).longValue();
        Long positionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", financeId,
                "code", "assignment_finance_manager_" + suffix,
                "name", "Assignment Finance Manager",
                "roles", List.of("finance_manager", "approval_owner"),
                "sortOrder", 23
        )).get("positionId")).longValue();

        Map<String, Object> assignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", userId,
                "positionId", positionId,
                "primary", true
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(assignment)
                .containsEntry("userId", userId)
                .containsEntry("positionId", positionId)
                .containsEntry("primary", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_position_assignments WHERE user_id = ? AND position_id = ? AND primary_position = TRUE",
                Long.class,
                userId,
                positionId
        )).isEqualTo(1L);
        assertThat(directory.get("roles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .filteredOn(role -> "finance_manager".equals(((Map<?, ?>) role).get("role")))
                .anySatisfy(role -> assertThat(role)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .satisfies(item -> assertThat(item.get("users"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .anySatisfy(user -> assertThat(user)
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("userId", userId)
                                        .containsEntry("username", "assigned.position.user." + suffix)
                                        .containsEntry("department", "Assignment Finance " + suffix)
                                        .containsEntry("position", "Assignment Finance Manager"))));
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(rootNode -> assertThat(rootNode)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "ORG-ASSIGN-HQ-" + suffix)
                        .satisfies(root -> assertThat(root.get("children"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("code", "ORG-ASSIGN-FIN-" + suffix)
                                .satisfies(unit -> assertThat(unit.get("positions"))
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                        .singleElement()
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("code", "assignment_finance_manager_" + suffix)
                                        .satisfies(position -> assertThat(position.get("users"))
                                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                                .singleElement()
                                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                                .containsEntry("userId", userId)))));
    }

    @Test
    void disablesOrganizationPositionAssignmentsAndKeepsSinglePrimaryInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long userId = ((Number) service.addUser(Map.of(
                "username", "assignment.lifecycle.user." + suffix,
                "displayName", "Assignment Lifecycle User",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long unitId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-LIFE-OPS-" + suffix,
                "name", "Assignment Lifecycle Ops " + suffix,
                "unitType", "department",
                "sortOrder", 31
        )).get("unitId")).longValue();
        Long firstPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_lifecycle_manager_" + suffix,
                "name", "Assignment Lifecycle Manager",
                "roles", List.of("assignment_lifecycle_manager"),
                "sortOrder", 32
        )).get("positionId")).longValue();
        Long secondPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_lifecycle_director_" + suffix,
                "name", "Assignment Lifecycle Director",
                "roles", List.of("assignment_lifecycle_director"),
                "sortOrder", 33
        )).get("positionId")).longValue();

        Long firstAssignmentId = ((Number) service.assignUserToOrganizationPosition(Map.of(
                "userId", userId,
                "positionId", firstPositionId,
                "primary", true
        )).get("assignmentId")).longValue();
        Long secondAssignmentId = ((Number) service.assignUserToOrganizationPosition(Map.of(
                "userId", userId,
                "positionId", secondPositionId,
                "primary", true
        )).get("assignmentId")).longValue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_position_assignments WHERE user_id = ? AND status = 'enabled' AND primary_position = TRUE AND position_id = ?",
                Long.class,
                userId,
                secondPositionId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT primary_position FROM organization_position_assignments WHERE id = ?",
                Boolean.class,
                firstAssignmentId
        )).isFalse();

        Map<String, Object> disabled = service.disableOrganizationPositionAssignment(secondAssignmentId, Map.of("reason", "role ended"));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(disabled)
                .containsEntry("assignmentId", secondAssignmentId)
                .containsEntry("status", "disabled");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM organization_position_assignments WHERE id = ?",
                String.class,
                secondAssignmentId
        )).isEqualTo("disabled");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_position_assignments WHERE user_id = ? AND status = 'enabled'",
                Long.class,
                userId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM organization_position_assignments WHERE user_id = ? AND status = 'enabled'",
                Long.class,
                userId
        )).isEqualTo(firstAssignmentId);
        assertThat(directory.get("roles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .extracting(role -> String.valueOf(((Map<?, ?>) role).get("role")))
                .contains("assignment_lifecycle_manager")
                .doesNotContain("assignment_lifecycle_director");
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(unit -> assertThat(unit)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "ORG-LIFE-OPS-" + suffix)
                        .satisfies(item -> {
                            List<?> positions = (List<?>) item.get("positions");
                            assertThat(positions)
                                    .filteredOn(position -> ("assignment_lifecycle_manager_" + suffix).equals(((Map<?, ?>) position).get("code")))
                                    .singleElement()
                                    .satisfies(position -> assertThat(((Map<?, ?>) position).get("users"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .singleElement()
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                            .containsEntry("userId", userId));
                            assertThat(positions)
                                    .filteredOn(position -> ("assignment_lifecycle_director_" + suffix).equals(((Map<?, ?>) position).get("code")))
                                    .singleElement()
                                    .satisfies(position -> assertThat(((Map<?, ?>) position).get("users"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .isEmpty());
                        }));
    }

    @Test
    void filtersOrganizationPositionAssignmentsByActiveWindowInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long currentUserId = ((Number) service.addUser(Map.of(
                "username", "assignment.window.current." + suffix,
                "displayName", "Assignment Window Current",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long futureUserId = ((Number) service.addUser(Map.of(
                "username", "assignment.window.future." + suffix,
                "displayName", "Assignment Window Future",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long expiredUserId = ((Number) service.addUser(Map.of(
                "username", "assignment.window.expired." + suffix,
                "displayName", "Assignment Window Expired",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long unitId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-WINDOW-" + suffix,
                "name", "Assignment Window " + suffix,
                "unitType", "department",
                "sortOrder", 41
        )).get("unitId")).longValue();
        Long positionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_window_reviewer_" + suffix,
                "name", "Assignment Window Reviewer",
                "roles", List.of("assignment_window_reviewer"),
                "sortOrder", 42
        )).get("positionId")).longValue();

        Long currentAssignmentId = ((Number) service.assignUserToOrganizationPosition(Map.of(
                "userId", currentUserId,
                "positionId", positionId,
                "activeFrom", "2026-01-01T00:00:00Z",
                "activeTo", "2026-12-31T23:59:59Z"
        )).get("assignmentId")).longValue();
        service.assignUserToOrganizationPosition(Map.of(
                "userId", futureUserId,
                "positionId", positionId,
                "activeFrom", "2099-01-01T00:00:00Z",
                "activeTo", "2099-12-31T23:59:59Z"
        ));
        service.assignUserToOrganizationPosition(Map.of(
                "userId", expiredUserId,
                "positionId", positionId,
                "activeFrom", "2020-01-01T00:00:00Z",
                "activeTo", "2020-12-31T23:59:59Z"
        ));

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_from IS NOT NULL AND active_to IS NOT NULL FROM organization_position_assignments WHERE id = ?",
                Boolean.class,
                currentAssignmentId
        )).isTrue();
        assertThat(directory.toString())
                .contains("Assignment Window Current", "assignment_window_reviewer")
                .doesNotContain("Assignment Window Future", "Assignment Window Expired");
    }

    @Test
    void updatesOrganizationPositionAssignmentWindowAndPrimaryInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long userId = ((Number) service.addUser(Map.of(
                "username", "assignment.update.user." + suffix,
                "displayName", "Assignment Update User",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long unitId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-UPD-" + suffix,
                "name", "Assignment Update " + suffix,
                "unitType", "department",
                "sortOrder", 43
        )).get("unitId")).longValue();
        Long firstPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_update_primary_" + suffix,
                "name", "Assignment Update Primary",
                "roles", List.of("assignment_update_primary"),
                "sortOrder", 44
        )).get("positionId")).longValue();
        Long secondPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_update_secondary_" + suffix,
                "name", "Assignment Update Secondary",
                "roles", List.of("assignment_update_secondary"),
                "sortOrder", 45
        )).get("positionId")).longValue();
        Long firstAssignmentId = ((Number) service.assignUserToOrganizationPosition(Map.of(
                "userId", userId,
                "positionId", firstPositionId,
                "primary", true,
                "activeFrom", "2026-01-01T00:00:00Z",
                "activeTo", "2026-01-31T23:59:59Z"
        )).get("assignmentId")).longValue();
        Long secondAssignmentId = ((Number) service.assignUserToOrganizationPosition(Map.of(
                "userId", userId,
                "positionId", secondPositionId,
                "primary", false,
                "activeFrom", "2099-01-01T00:00:00Z",
                "activeTo", "2099-12-31T23:59:59Z"
        )).get("assignmentId")).longValue();

        Map<String, Object> updated = service.updateOrganizationPositionAssignment(secondAssignmentId, Map.of(
                "primary", true,
                "activeFrom", "2026-01-01T00:00:00Z",
                "activeTo", "2026-12-31T23:59:59Z"
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(updated)
                .containsEntry("assignmentId", secondAssignmentId)
                .containsEntry("primary", true)
                .containsEntry("status", "enabled");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT primary_position FROM organization_position_assignments WHERE id = ?",
                Boolean.class,
                firstAssignmentId
        )).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT primary_position FROM organization_position_assignments WHERE id = ?",
                Boolean.class,
                secondAssignmentId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_from IS NOT NULL AND active_to IS NOT NULL FROM organization_position_assignments WHERE id = ?",
                Boolean.class,
                secondAssignmentId
        )).isTrue();
        assertThat(directory.get("roles").toString())
                .contains("assignment_update_secondary")
                .doesNotContain("assignment_update_primary");
        assertThat(directory.get("departments").toString())
                .contains("Assignment Update User", "Assignment Update Secondary")
                .doesNotContain("Assignment Update Primary");
    }

    @Test
    void batchImportsOrganizationPositionAssignmentsInPostgres() {
        PermissionApplicationService service = new PermissionApplicationService(
                new JdbcShareLinkRepository(jdbcTemplate),
                reportRepository,
                new JdbcUserRepository(jdbcTemplate),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcOrganizationDirectoryRepository(jdbcTemplate)
        );
        long suffix = System.nanoTime();
        Long userId = ((Number) service.addUser(Map.of(
                "username", "assignment.batch.user." + suffix,
                "displayName", "Assignment Batch User",
                "roles", List.of("viewer")
        )).get("userId")).longValue();
        Long unitId = ((Number) service.createOrganizationUnit(Map.of(
                "code", "ORG-BATCH-" + suffix,
                "name", "Assignment Batch " + suffix,
                "unitType", "department",
                "sortOrder", 46
        )).get("unitId")).longValue();
        Long firstPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_batch_primary_" + suffix,
                "name", "Assignment Batch Primary",
                "roles", List.of("assignment_batch_primary"),
                "sortOrder", 47
        )).get("positionId")).longValue();
        Long secondPositionId = ((Number) service.createOrganizationPosition(Map.of(
                "organizationUnitId", unitId,
                "code", "assignment_batch_secondary_" + suffix,
                "name", "Assignment Batch Secondary",
                "roles", List.of("assignment_batch_secondary"),
                "sortOrder", 48
        )).get("positionId")).longValue();

        Map<String, Object> result = service.batchImportOrganizationPositionAssignments(Map.of(
                "assignments", List.of(
                        Map.of(
                                "userId", userId,
                                "positionId", firstPositionId,
                                "primary", true,
                                "activeFrom", "2026-01-01T00:00:00Z",
                                "activeTo", "2026-01-31T23:59:59Z"
                        ),
                        Map.of(
                                "userId", userId,
                                "positionId", secondPositionId,
                                "primary", true,
                                "activeFrom", "2026-01-01T00:00:00Z",
                                "activeTo", "2026-12-31T23:59:59Z"
                        ),
                        Map.of(
                                "userId", 999999L,
                                "positionId", firstPositionId
                        )
                )
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(result)
                .containsEntry("imported", 2)
                .containsEntry("failed", 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_position_assignments WHERE user_id = ? AND status = 'enabled'",
                Long.class,
                userId
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_position_assignments WHERE user_id = ? AND status = 'enabled' AND primary_position = TRUE AND position_id = ?",
                Long.class,
                userId,
                secondPositionId
        )).isEqualTo(1L);
        assertThat(directory.get("roles").toString())
                .contains("assignment_batch_secondary")
                .doesNotContain("assignment_batch_primary");
    }

    @Test
    void persistsAndSearchesKnowledgeBasesAndItemsInPostgres() {
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new StubDocumentStorage(),
                new JdbcKnowledgeDocumentRepository(jdbcTemplate),
                new JdbcKnowledgeBaseRepository(jdbcTemplate),
                (eventType, eventKey, payload) -> {
                },
                new com.company.report.knowledge.application.DataSourceCredentialCodec("postgres-it-data-source-key"),
                new com.company.report.audit.infrastructure.persistence.JdbcAuditRepository(
                        jdbcTemplate,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                ),
                new JdbcSystemAlertRepository(jdbcTemplate, new com.fasterxml.jackson.databind.ObjectMapper())
        );

        try {
            CurrentUserHolder.set(new CurrentUser(2114L, Set.of("analyst"), Set.of("knowledge:manage", "knowledge:upload")));
            Map<String, Object> createdBase = service.createKnowledgeBase(Map.of("name", "Postgres KB " + System.nanoTime()));
            Long knowledgeBaseId = ((Number) createdBase.get("knowledgeBaseId")).longValue();
            Map<String, Object> createdItem = service.createItem(Map.of(
                    "knowledgeBaseId", knowledgeBaseId,
                    "title", "East revenue evidence",
                    "content", "East revenue grew 12 percent",
                    "sourceType", "manual"
            ));

            var bases = service.listKnowledgeBases(1, 10);
            var items = service.searchItems(1, 10, "East");

            assertThat(bases.items())
                    .filteredOn(item -> knowledgeBaseId.equals(((Number) item.get("knowledgeBaseId")).longValue()))
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("ownerUserId", 2114L)
                    .containsEntry("status", "enabled");
            assertThat(createdItem)
                    .containsEntry("knowledgeBaseId", knowledgeBaseId)
                    .containsEntry("title", "East revenue evidence")
                    .containsEntry("indexStatus", "pending");
            assertThat(items.items())
                    .filteredOn(item -> "East revenue evidence".equals(item.get("title")))
                    .filteredOn(item -> knowledgeBaseId.equals(((Number) item.get("knowledgeBaseId")).longValue()))
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("sourceType", "manual");
            Map<String, Object> dataSourceRequest = new LinkedHashMap<>();
            dataSourceRequest.put("name", "ERP PostgreSQL " + System.nanoTime());
            dataSourceRequest.put("sourceType", "postgresql");
            dataSourceRequest.put("endpoint", JDBC_URL);
            dataSourceRequest.put("username", USERNAME);
            dataSourceRequest.put("password", PASSWORD);
            dataSourceRequest.put("knowledgeBaseId", knowledgeBaseId);
            dataSourceRequest.put("syncQuery", "SELECT id, title, content FROM uc07_source_rows ORDER BY id");
            dataSourceRequest.put("fieldMapping", Map.of(
                    "rowsPath", "data.items",
                    "titleField", "title",
                    "contentField", "content"
            ));
            dataSourceRequest.put("cursorColumn", "id");
            dataSourceRequest.put("scheduleEnabled", true);
            dataSourceRequest.put("scheduleIntervalSeconds", 300);
            dataSourceRequest.put("maxRetryCount", 1);
            dataSourceRequest.put("nextRunAt", java.time.OffsetDateTime.now().minusMinutes(1).toString());
            Map<String, Object> dataSource = service.saveDataSource(dataSourceRequest);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS uc07_source_rows (
                      id BIGSERIAL PRIMARY KEY,
                      title TEXT NOT NULL,
                      content TEXT NOT NULL
                    )
                    """);
            jdbcTemplate.update("DELETE FROM uc07_source_rows");
            jdbcTemplate.update("INSERT INTO uc07_source_rows(title, content) VALUES (?, ?)", "ERP revenue row", "ERP revenue increased 18 percent");
            jdbcTemplate.update("INSERT INTO uc07_source_rows(title, content) VALUES (?, ?)", "ERP risk row", "ERP overdue risk requires follow-up");
            String firstCursor = String.valueOf(jdbcTemplate.queryForObject("SELECT MAX(id) FROM uc07_source_rows", Long.class));
            Long dataSourceId = ((Number) dataSource.get("dataSourceId")).longValue();
            Map<String, Object> connection = service.testConnection(Map.of("dataSourceId", dataSourceId));
            Map<String, Object> syncRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));
            JdbcKnowledgeBaseRepository jdbcKnowledgeBaseRepository = new JdbcKnowledgeBaseRepository(jdbcTemplate);
            boolean leaseAfterServiceSync = jdbcTemplate.queryForObject(
                    "SELECT sync_locked_until IS NULL FROM knowledge_data_sources WHERE id = ?",
                    Boolean.class,
                    dataSourceId
            );
            boolean firstLease = jdbcKnowledgeBaseRepository.tryAcquireDataSourceSyncLease(
                    dataSourceId,
                    java.time.OffsetDateTime.now().plusMinutes(5)
            );
            boolean duplicateLease = jdbcKnowledgeBaseRepository.tryAcquireDataSourceSyncLease(
                    dataSourceId,
                    java.time.OffsetDateTime.now().plusMinutes(5)
            );
            jdbcKnowledgeBaseRepository.releaseDataSourceSyncLease(dataSourceId);
            boolean reacquiredLease = jdbcKnowledgeBaseRepository.tryAcquireDataSourceSyncLease(
                    dataSourceId,
                    java.time.OffsetDateTime.now().plusMinutes(5)
            );
            jdbcKnowledgeBaseRepository.releaseDataSourceSyncLease(dataSourceId);
            jdbcTemplate.update("INSERT INTO uc07_source_rows(title, content) VALUES (?, ?)", "ERP margin row", "ERP margin improved");
            String secondCursor = String.valueOf(jdbcTemplate.queryForObject("SELECT MAX(id) FROM uc07_source_rows", Long.class));
            Map<String, Object> incrementalRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));
            Long failedDataSourceId = ((Number) service.saveDataSource(Map.of(
                    "name", "Broken PostgreSQL " + System.nanoTime(),
                    "sourceType", "postgresql",
                    "endpoint", "jdbc:postgresql://localhost:55432/missing",
                    "username", "broken_app",
                    "password", "plain-secret"
            )).get("dataSourceId")).longValue();
            Map<String, Object> failedSyncRun = service.startDataSourceSync(failedDataSourceId, Map.of("mode", "manual"));
            var scheduledDataSource = new JdbcKnowledgeBaseRepository(jdbcTemplate)
                    .findDataSourceById(dataSourceId)
                    .orElseThrow();
            new JdbcKnowledgeBaseRepository(jdbcTemplate)
                    .updateDataSourceScheduleState(dataSourceId, java.time.OffsetDateTime.now().plusMinutes(5), 0);
            var syncRuns = service.listDataSourceSyncRuns(dataSourceId, 1, 10);
            Map<String, Object> deleted = service.deleteItem(((Number) createdItem.get("itemId")).longValue(), true);

            assertThat(dataSource)
                    .containsEntry("ownerUserId", 2114L)
                    .containsEntry("sourceType", "postgresql")
                    .containsEntry("status", "enabled")
                    .containsEntry("credentialConfigured", true)
                    .containsEntry("knowledgeBaseId", knowledgeBaseId)
                    .containsEntry("cursorColumn", "id")
                    .containsEntry("fieldMapping", Map.of(
                            "rowsPath", "data.items",
                            "titleField", "title",
                            "contentField", "content"
                    ))
                    .containsEntry("scheduleEnabled", true)
                    .containsEntry("scheduleIntervalSeconds", 300)
                    .containsEntry("maxRetryCount", 1);
            assertThat(dataSource).doesNotContainKeys("password", "credentialSecret");
            assertThat(scheduledDataSource.nextRunAt()).isAfter(java.time.OffsetDateTime.now());
            assertThat(scheduledDataSource.failureCount()).isZero();
            assertThat(scheduledDataSource.maxRetryCount()).isEqualTo(1);
            assertThat(connection)
                    .containsEntry("dataSourceId", dataSourceId)
                    .containsEntry("success", true)
                    .containsEntry("sourceType", "postgresql");
            assertThat(syncRun)
                    .containsEntry("dataSourceId", dataSourceId)
                    .containsEntry("status", "succeeded")
                    .containsEntry("mode", "manual")
                    .containsEntry("processedRows", 2L)
                    .containsEntry("lastCursor", firstCursor)
                    .containsKey("syncRunId");
            assertThat(leaseAfterServiceSync).isTrue();
            assertThat(firstLease).isTrue();
            assertThat(duplicateLease).isFalse();
            assertThat(reacquiredLease).isTrue();
            assertThat(incrementalRun)
                    .containsEntry("dataSourceId", dataSourceId)
                    .containsEntry("status", "succeeded")
                    .containsEntry("processedRows", 1L)
                    .containsEntry("lastCursor", secondCursor);
            assertThat(syncRuns.items())
                    .hasSize(2)
                    .first()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("dataSourceId", dataSourceId)
                    .containsEntry("status", "succeeded")
                    .containsEntry("lastCursor", secondCursor);
            assertThat(service.searchItems(1, 10, "ERP revenue").items())
                    .filteredOn(item -> knowledgeBaseId.equals(((Number) item.get("knowledgeBaseId")).longValue()))
                    .filteredOn(item -> "ERP revenue row".equals(item.get("title")))
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("sourceType", "data_source:postgresql")
                    .containsEntry("knowledgeBaseId", knowledgeBaseId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT credential_secret LIKE 'enc:%' FROM knowledge_data_sources WHERE id = ?",
                    Boolean.class,
                    dataSourceId
            )).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT field_mapping_json FROM knowledge_data_sources WHERE id = ?",
                    String.class,
                    dataSourceId
            )).contains("\"rowsPath\":\"data.items\"");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT max_retry_count FROM knowledge_data_sources WHERE id = ?",
                    Integer.class,
                    dataSourceId
            )).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_data_source_sync_runs WHERE data_source_id = ?",
                    Long.class,
                    dataSourceId
            )).isEqualTo(2L);
            assertThat(failedSyncRun)
                    .containsEntry("status", "failed")
                    .containsEntry("failureReason", "unsupported or unreachable endpoint")
                    .containsKey("syncRunId");
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM system_alerts
                    WHERE recipient_user_id = ?
                      AND type = 'knowledge_data_source_sync_failed'
                      AND severity = 'warning'
                      AND status = 'unread'
                      AND resource_type = 'knowledge_data_source'
                      AND resource_id = ?
                      AND payload_json ->> 'failureReason' = 'unsupported or unreachable endpoint'
                      AND payload_json ->> 'syncRunId' = ?
                      AND payload_json::text NOT LIKE '%plain-secret%'
                      AND payload_json::text NOT LIKE '%credentialSecret%'
                    """,
                    Long.class,
                    2114L,
                    failedDataSourceId,
                    String.valueOf(failedSyncRun.get("syncRunId"))
            )).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM operation_logs
                    WHERE actor_user_id = ?
                      AND resource_type = 'knowledge_data_source'
                      AND resource_id = ?
                      AND operation_type IN (
                        'knowledge_data_source_saved',
                        'knowledge_data_source_tested',
                        'knowledge_data_source_sync_run'
                      )
                      AND detail::text NOT LIKE '%plain-secret%'
                      AND detail::text NOT LIKE '%credentialSecret%'
                    """,
                    Long.class,
                    2114L,
                    dataSourceId
            )).isEqualTo(4L);
            assertThat(deleted)
                    .containsEntry("deleted", true)
                    .containsEntry("requiresConfirmation", false);
            assertThat(service.searchItems(1, 10, "East").items())
                    .filteredOn(item -> ((Number) item.get("itemId")).longValue() == ((Number) createdItem.get("itemId")).longValue())
                    .isEmpty();

            CurrentUserHolder.set(new CurrentUser(2115L, Set.of("analyst"), Set.of("knowledge:upload")));
            org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                    "file",
                    "private.pdf",
                    "application/pdf",
                    "private".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            assertThatThrownBy(() -> service.uploadDocument(file, knowledgeBaseId))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("knowledge base access denied");
            assertThatThrownBy(() -> service.testConnection(Map.of("dataSourceId", dataSourceId)))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("knowledge data source access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void blocksKnowledgeItemDeleteWhenReferencedByReportCitationInPostgres() {
        JdbcKnowledgeBaseRepository knowledgeBaseRepository = new JdbcKnowledgeBaseRepository(jdbcTemplate);
        JdbcReportContentRepository contentRepository = new JdbcReportContentRepository(jdbcTemplate, new ObjectMapper());
        KnowledgeApplicationService service = new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                new StubDocumentStorage(),
                new JdbcKnowledgeDocumentRepository(jdbcTemplate),
                knowledgeBaseRepository,
                (eventType, eventKey, payload) -> {
                }
        );
        long suffix = System.nanoTime();
        Long ownerUserId = suffix;

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("knowledge:manage")));
            Map<String, Object> createdBase = service.createKnowledgeBase(Map.of("name", "Referenced KB " + suffix));
            Long knowledgeBaseId = ((Number) createdBase.get("knowledgeBaseId")).longValue();
            Map<String, Object> createdItem = service.createItem(Map.of(
                    "knowledgeBaseId", knowledgeBaseId,
                    "title", "Referenced evidence " + suffix,
                    "content", "Revenue evidence",
                    "sourceType", "manual"
            ));
            Long itemId = ((Number) createdItem.get("itemId")).longValue();
            Long reportId = insertReport("Report references knowledge item " + suffix, ownerUserId, "completed", null);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("referenceId", 880001L);
            citation.put("knowledgeItemId", itemId);
            citation.put("sourceTitle", "Referenced evidence " + suffix);
            Long versionId = contentRepository.saveCompletedVersion(reportId, ownerUserId, List.of(
                    Map.of("heading", "Summary", "content", "Revenue evidence cited.", "citations", List.of(citation))
            ));
            reportRepository.save(new com.company.report.report.domain.model.Report(
                    reportId,
                    "Report references knowledge item " + suffix,
                    ownerUserId,
                    com.company.report.report.domain.model.ReportStatus.COMPLETED,
                    versionId
            ));

            assertThatThrownBy(() -> service.deleteItem(itemId, false))
                    .isInstanceOf(com.company.report.shared.error.BusinessException.class)
                    .extracting(error -> ((com.company.report.shared.error.BusinessException) error).code())
                    .isEqualTo(1003);
            assertThat(knowledgeBaseRepository.countReportReferences(itemId)).isEqualTo(1L);

            Map<String, Object> deleted = service.deleteItem(itemId, true);

            assertThat(deleted)
                    .containsEntry("itemId", itemId)
                    .containsEntry("referenceCount", 1L)
                    .containsEntry("deleted", true);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void migratesKnowledgeParseIndexingTablesInPostgres() {
        Long documentId = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_documents
                        (knowledge_base_id, file_object_id, document_title, file_type, parse_status, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                1L,
                1L,
                "parse-indexing-" + System.nanoTime() + ".txt",
                "txt",
                "pending",
                2118L
        );
        Long parseResultId = jdbcTemplate.queryForObject("""
                        INSERT INTO document_parse_results
                        (document_id, parse_type, result_payload, confidence, status)
                        VALUES (?, ?, ?::jsonb, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                documentId,
                "text",
                "{\"chunks\":1}",
                0.98,
                "processed"
        );
        Long chunkId = jdbcTemplate.queryForObject("""
                        INSERT INTO document_chunks
                        (document_id, chunk_index, content, position_payload, parse_confidence, embedding_status)
                        VALUES (?, ?, ?, ?::jsonb, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                documentId,
                0,
                "East revenue increased 12%.",
                "{}",
                0.98,
                "embedded"
        );
        Long embeddingId = jdbcTemplate.queryForObject("""
                        INSERT INTO embeddings
                        (chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                chunkId,
                "local-hash-embedding",
                64,
                "knowledge_chunks",
                "emb_test",
                "hash_test"
        );

        assertThat(parseResultId).isNotNull();
        assertThat(chunkId).isNotNull();
        assertThat(embeddingId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ? AND embedding_status = 'embedded'",
                Long.class,
                documentId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT milvus_primary_key FROM embeddings WHERE chunk_id = ?",
                String.class,
                chunkId
        )).isEqualTo("emb_test");
    }

    @Test
    void persistsRulesVersionsAndDebugRunsInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );

        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "score", "type", "condition", "field", "risk", "operator", ">=", "value", 90),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "score"),
                        Map.of("source", "score", "target", "end")
                )
        );
        Map<String, Object> created = service.create(Map.of(
                "name", "Postgres rule " + System.nanoTime(),
                "description", "runtime rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        Map<String, Object> saved = service.save(ruleId, Map.of(
                "status", "draft",
                "definition", definition
        ));
        Map<String, Object> debugged = service.debug(ruleId, Map.of("nodeId", "score", "sample", Map.of("risk", 91)));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        Map<String, Object> productionRun;
        try {
            CurrentUserHolder.set(new CurrentUser(2026L, Set.of("rule-manager"), Set.of("rule:run")));
            productionRun = service.execute(ruleId, Map.of("sample", Map.of("risk", 91)));
        } finally {
            CurrentUserHolder.clear();
        }
        Map<String, Object> schedule = service.configureSchedule(ruleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 2,
                "scheduleInput", Map.of("sample", Map.of("risk", 92))
        ));
        jdbcTemplate.update("UPDATE rules SET schedule_enabled = FALSE, schedule_locked_until = NULL WHERE id <> ?", ruleId);
        JdbcRuleRepository scheduledRuleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleProductionScheduler ruleScheduler = new RuleProductionScheduler(
                scheduledRuleRepository,
                service,
                false
        );
        assertThat(scheduledRuleRepository.tryAcquireRuleScheduleLease(ruleId, OffsetDateTime.now().plusMinutes(5))).isTrue();
        int skippedScheduledRuns = ruleScheduler.runDueRulesOnce();
        scheduledRuleRepository.releaseRuleScheduleLease(ruleId);
        int scheduledRuns = ruleScheduler.runDueRulesOnce();

        assertThat(saved)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "draft")
                .containsEntry("versionId", ((Number) saved.get("versionId")).longValue())
                .containsEntry("valid", true);
        assertThat(debugged)
                .containsEntry("ruleId", ruleId)
                .containsEntry("versionId", ((Number) saved.get("versionId")).longValue())
                .containsEntry("status", "succeeded");
        assertThat(productionRun)
                .containsEntry("runType", "production")
                .containsEntry("triggeredByUserId", 2026L)
                .containsEntry("errorMessage", null)
                .extractingByKey("durationMs")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LONG)
                .isGreaterThanOrEqualTo(0L);
        assertThat(schedule)
                .containsEntry("scheduleEnabled", true)
                .containsEntry("scheduleIntervalSeconds", 60)
                .containsEntry("failureCount", 0)
                .containsEntry("maxRetryCount", 2);
        assertThat(skippedScheduledRuns).isZero();
        assertThat(scheduledRuns).isEqualTo(1);
        assertThat(service.metrics(ruleId))
                .containsEntry("ruleId", ruleId)
                .containsEntry("totalRuns", 2L)
                .containsEntry("succeededRuns", 2L)
                .containsEntry("failedRuns", 0L)
                .containsEntry("successRate", 1.0d)
                .containsEntry("lastStatus", "succeeded");
        assertThat(service.list(1, 20).items())
                .filteredOn(item -> ruleId.equals(((Number) item.get("ruleId")).longValue()))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "published");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_versions WHERE rule_id = ?",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_debug_runs WHERE rule_id = ?",
                Long.class,
                ruleId
        )).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT run_type FROM rule_debug_runs WHERE rule_id = ? ORDER BY id ASC LIMIT 1",
                String.class,
                ruleId
        )).isEqualTo("debug");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT triggered_by_user_id FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'production' ORDER BY id ASC LIMIT 1",
                Long.class,
                ruleId
        )).isEqualTo(2026L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT duration_ms FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'production' ORDER BY id DESC LIMIT 1",
                Long.class,
                ruleId
        )).isGreaterThanOrEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'production' AND error_message IS NULL",
                Long.class,
                ruleId
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'production' AND triggered_by_user_id = 1",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json::text FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'production' ORDER BY id DESC LIMIT 1",
                String.class,
                ruleId
        )).contains("\"matched\": true");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT schedule_enabled FROM rules WHERE id = ?",
                Boolean.class,
                ruleId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT schedule_interval_seconds FROM rules WHERE id = ?",
                Integer.class,
                ruleId
        )).isEqualTo(60);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT max_retry_count FROM rules WHERE id = ?",
                Integer.class,
                ruleId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_run_at > CURRENT_TIMESTAMP FROM rules WHERE id = ?",
                Boolean.class,
                ruleId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_count FROM rules WHERE id = ?",
                Integer.class,
                ruleId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT schedule_input_json::text FROM rules WHERE id = ?",
                String.class,
                ruleId
        )).contains("\"risk\": 92");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT schedule_locked_until IS NULL FROM rules WHERE id = ?",
                Boolean.class,
                ruleId
        )).isTrue();
        Map<String, Object> failingRule = service.create(Map.of(
                "name", "Postgres failing scheduled rule " + System.nanoTime(),
                "description", "runtime rule failure",
                "definition", definition
        ));
        Long failingRuleId = ((Number) failingRule.get("ruleId")).longValue();
        service.save(failingRuleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(failingRuleId, Map.of("comment", "ready"));
        service.approve(failingRuleId, Map.of("comment", "approved"));
        service.configureSchedule(failingRuleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 2,
                "scheduleInput", Map.of("sample", Map.of("risk", 92), "secretPrompt", "do-not-leak")
        ));
        Map<String, Object> invalidDefinition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "score", "type", "condition", "field", "risk", "operator", "contains", "value", 90),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "score"),
                        Map.of("source", "score", "target", "end")
                )
        );
        scheduledRuleRepository.save(scheduledRuleRepository.findById(failingRuleId).orElseThrow().update(
                (String) failingRule.get("name"),
                "runtime rule failure",
                "published",
                invalidDefinition,
                ((Number) service.list(1, 50).items().stream()
                        .filter(item -> failingRuleId.equals(((Number) item.get("ruleId")).longValue()))
                        .findFirst()
                        .orElseThrow()
                        .get("versionId")).longValue()
        ));
        jdbcTemplate.update("UPDATE rules SET schedule_enabled = FALSE, schedule_locked_until = NULL WHERE id NOT IN (?, ?)", ruleId, failingRuleId);

        int failedScheduledRuns = ruleScheduler.runDueRulesOnce();

        assertThat(failedScheduledRuns).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM system_alerts
                WHERE recipient_user_id = 1
                  AND type = 'rule_schedule_run_failed'
                  AND severity = 'warning'
                  AND status = 'unread'
                  AND resource_type = 'rule'
                  AND resource_id = ?
                  AND payload_json ->> 'ruleId' = ?
                  AND payload_json ->> 'ruleName' LIKE 'Postgres failing scheduled rule%'
                  AND payload_json ->> 'failureCount' = '1'
                  AND payload_json ->> 'maxRetryCount' = '2'
                  AND payload_json::text LIKE '%"runId"%'
                  AND payload_json::text LIKE '%"errorMessage"%'
                  AND payload_json::text NOT LIKE '%do-not-leak%'
                  AND payload_json::text NOT LIKE '%secretPrompt%'
                  AND payload_json::text NOT LIKE '%scheduleInput%'
                """,
                Long.class,
                failingRuleId,
                String.valueOf(failingRuleId)
        )).isEqualTo(1L);
        Map<String, Object> retrySchedule = service.retrySchedule(failingRuleId, Map.of(
                "nextRunAt", OffsetDateTime.now().minusSeconds(1).toString(),
                "scheduleInput", Map.of("sample", Map.of("risk", 93))
        ));

        assertThat(retrySchedule)
                .containsEntry("ruleId", failingRuleId)
                .containsEntry("failureCount", 0)
                .containsEntry("scheduleEnabled", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_count FROM rules WHERE id = ?",
                Integer.class,
                failingRuleId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_run_at <= CURRENT_TIMESTAMP FROM rules WHERE id = ?",
                Boolean.class,
                failingRuleId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT schedule_input_json::text FROM rules WHERE id = ?",
                String.class,
                failingRuleId
        )).contains("\"risk\": 93");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM operation_logs
                WHERE resource_type = 'rule'
                  AND resource_id = ?
                  AND operation_type = 'rule_schedule_retry'
                  AND result = 'succeeded'
                """,
                Long.class,
                failingRuleId
        )).isEqualTo(1L);
        String outputJson = jdbcTemplate.queryForObject(
                "SELECT output_json::text FROM rule_debug_runs WHERE rule_id = ? AND run_type = 'debug'",
                String.class,
                ruleId
        );
        assertThat(outputJson).contains("\"matched\": true").contains("\"evaluatedNodes\": 3");
    }

    @Test
    void findsUnreadSystemAlertByDedupeKeyInPostgres() {
        JdbcSystemAlertRepository alertRepository = new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper());
        String dedupeKey = "rule:901:webhook:writebackRisk:https://erp.example.com/risk-events";

        alertRepository.save(SystemAlert.unread(
                901L,
                "rule_action_webhook_failed",
                "error",
                "rule",
                901L,
                Map.of(
                        "dedupeKey", dedupeKey,
                        "nodeId", "writebackRisk",
                        "endpoint", "https://erp.example.com/risk-events"
                )
        ));

        assertThat(alertRepository.existsUnreadByDedupeKey(
                901L,
                "rule_action_webhook_failed",
                "rule",
                901L,
                dedupeKey
        )).isTrue();
        assertThat(alertRepository.existsUnreadByDedupeKey(
                901L,
                "rule_action_webhook_failed",
                "rule",
                901L,
                "rule:901:webhook:other:https://erp.example.com/risk-events"
        )).isFalse();
    }

    @Test
    void persistsApprovalRecordsWhenProductionRunHitsApprovalNodeInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Map<String, Object> created = service.create(Map.of(
                "name", "Postgres approval rule " + System.nanoTime(),
                "description", "approval runtime rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of(
                "status", "draft",
                "definition", definition
        ));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        Map<String, Object> productionRun;
        try {
            CurrentUserHolder.set(new CurrentUser(3030L, Set.of("rule-manager"), Set.of("rule:run")));
            productionRun = service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(productionRun)
                .containsEntry("ruleId", ruleId)
                .containsEntry("runType", "production")
                .containsEntry("triggeredByUserId", 3030L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_approval_records WHERE rule_id = ?",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rule_approval_records WHERE rule_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ruleId
        )).isEqualTo("pending");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT assignee_role FROM rule_approval_records WHERE rule_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ruleId
        )).isEqualTo("finance_manager");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approval_title FROM rule_approval_records WHERE rule_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ruleId
        )).isEqualTo("Finance review");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT run_id FROM rule_approval_records WHERE rule_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                ruleId
        )).isEqualTo(((Number) productionRun.get("debugRunId")).longValue());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by_user_id FROM rule_approval_records WHERE rule_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                ruleId
        )).isEqualTo(3030L);
    }

    @Test
    void stopsDownstreamNotifyActionWhenApprovalNodeIsHitInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyFinance", "type", "action", "actionType", "notify", "severity", "warning", "message", "Need finance confirmation"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyFinance"),
                        Map.of("source", "notifyFinance", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "Postgres approval notify gate rule " + System.nanoTime(),
                "description", "approval blocks downstream notify",
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        try {
            CurrentUserHolder.set(new CurrentUser(5050L, Set.of("rule-manager"), Set.of("rule:run")));
            service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_approval_records WHERE rule_id = ?",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_alerts WHERE resource_type = 'rule' AND resource_id = ? AND type = 'rule_action_notify'",
                Long.class,
                ruleId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_action_executions WHERE rule_id = ?",
                Long.class,
                ruleId
        )).isZero();
    }

    @Test
    void updatesApprovalRecordStatusWhenHandledInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "Postgres handled approval rule " + System.nanoTime(),
                "description", "approval handle runtime rule",
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        Long approvalRecordId;
        try {
            CurrentUserHolder.set(new CurrentUser(4040L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            approvalRecordId = ((Number) service.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();
            service.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved in postgres"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rule_approval_records WHERE id = ?",
                String.class,
                approvalRecordId
        )).isEqualTo("approved");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_by_user_id FROM rule_approval_records WHERE id = ?",
                Long.class,
                approvalRecordId
        )).isEqualTo(4040L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approval_comment FROM rule_approval_records WHERE id = ?",
                String.class,
                approvalRecordId
        )).isEqualTo("approved in postgres");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_at IS NOT NULL FROM rule_approval_records WHERE id = ?",
                Boolean.class,
                approvalRecordId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM operation_logs
                WHERE resource_type = 'rule'
                  AND resource_id = ?
                  AND operation_type = 'rule_node_approval_approved'
                  AND result = 'succeeded'
                """,
                Long.class,
                ruleId
        )).isEqualTo(1L);
    }

    @Test
    void createsRejectedApprovalAlertWhenApprovalIsRejectedInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "Postgres rejected approval alert rule " + System.nanoTime(),
                "description", "approval reject alert",
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        Long approvalRecordId;
        try {
            CurrentUserHolder.set(new CurrentUser(8080L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            approvalRecordId = ((Number) service.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();
            service.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "reject",
                    "comment", "missing attachment"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_alerts WHERE resource_type = 'rule' AND resource_id = ? AND type = 'rule_approval_rejected'",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload_json->>'comment' FROM system_alerts WHERE resource_type = 'rule' AND resource_id = ? AND type = 'rule_approval_rejected' ORDER BY id DESC LIMIT 1",
                String.class,
                ruleId
        )).isEqualTo("missing attachment");
    }

    @Test
    void approvingApprovalRecordResumesDownstreamNotifyActionInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyFinance", "type", "action", "actionType", "notify", "severity", "warning", "message", "Need finance confirmation"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyFinance"),
                        Map.of("source", "notifyFinance", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "Postgres approval resume notify rule " + System.nanoTime(),
                "description", "approval approve resumes notify",
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        Long approvalRecordId;
        try {
            CurrentUserHolder.set(new CurrentUser(6060L, Set.of("rule-manager"), Set.of("rule:manage", "rule:run")));
            service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            approvalRecordId = ((Number) service.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();
            service.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "resume notify"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_alerts WHERE resource_type = 'rule' AND resource_id = ? AND type = 'rule_action_notify'",
                Long.class,
                ruleId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rule_approval_records WHERE id = ?",
                String.class,
                approvalRecordId
        )).isEqualTo("approved");
    }

    @Test
    void listsOnlyPendingApprovalRecordsAcrossRulesInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long firstRuleId = ((Number) service.create(Map.of(
                "name", "Postgres global pending rule A " + System.nanoTime(),
                "description", "global pending approvals A",
                "definition", definition
        )).get("ruleId")).longValue();
        Long secondRuleId = ((Number) service.create(Map.of(
                "name", "Postgres global pending rule B " + System.nanoTime(),
                "description", "global pending approvals B",
                "definition", definition
        )).get("ruleId")).longValue();
        for (Long ruleId : List.of(firstRuleId, secondRuleId)) {
            service.save(ruleId, Map.of("status", "draft", "definition", definition));
            service.submitForReview(ruleId, Map.of("comment", "ready"));
            service.approve(ruleId, Map.of("comment", "approved"));
        }

        Long firstApprovalRecordId;
        try {
            CurrentUserHolder.set(new CurrentUser(7070L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            service.execute(firstRuleId, Map.of("sample", Map.of("riskScore", 91)));
            service.execute(secondRuleId, Map.of("sample", Map.of("riskScore", 92)));
            firstApprovalRecordId = ((Number) service.listApprovalRecords(firstRuleId, 1, 10)
                    .items()
                    .get(0)
                    .get("approvalRecordId")).longValue();
            service.handleApprovalRecord(firstRuleId, firstApprovalRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved in postgres"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        var pendingApprovals = service.listPendingApprovalRecords(1, 10);

        Map<String, Object> remainingApproval = pendingApprovals.items().stream()
                .filter(item -> secondRuleId.equals(item.get("ruleId")) || firstRuleId.equals(item.get("ruleId")))
                .findFirst()
                .orElseThrow();
        assertThat(remainingApproval)
                .containsEntry("ruleId", secondRuleId)
                .containsEntry("status", "pending")
                .containsEntry("nodeId", "financeApproval");
    }

    @Test
    void listsHandledApprovalRecordsByStatusAcrossRulesInPostgres() {
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                new JdbcRuleRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long approvedRuleId = ((Number) service.create(Map.of(
                "name", "Postgres approved approval list rule " + System.nanoTime(),
                "description", "approved approval list",
                "definition", definition
        )).get("ruleId")).longValue();
        Long rejectedRuleId = ((Number) service.create(Map.of(
                "name", "Postgres rejected approval list rule " + System.nanoTime(),
                "description", "rejected approval list",
                "definition", definition
        )).get("ruleId")).longValue();
        for (Long ruleId : List.of(approvedRuleId, rejectedRuleId)) {
            service.save(ruleId, Map.of("status", "draft", "definition", definition));
            service.submitForReview(ruleId, Map.of("comment", "ready"));
            service.approve(ruleId, Map.of("comment", "approved"));
        }

        try {
            CurrentUserHolder.set(new CurrentUser(9090L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            service.execute(approvedRuleId, Map.of("sample", Map.of("riskScore", 91)));
            service.execute(rejectedRuleId, Map.of("sample", Map.of("riskScore", 92)));
            Long approvedRecordId = ((Number) service.listApprovalRecords(approvedRuleId, 1, 10)
                    .items()
                    .get(0)
                    .get("approvalRecordId")).longValue();
            Long rejectedRecordId = ((Number) service.listApprovalRecords(rejectedRuleId, 1, 10)
                    .items()
                    .get(0)
                    .get("approvalRecordId")).longValue();
            service.handleApprovalRecord(approvedRuleId, approvedRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved in postgres"
            ));
            service.handleApprovalRecord(rejectedRuleId, rejectedRecordId, Map.of(
                    "action", "reject",
                    "comment", "rejected in postgres"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        var approvedApprovals = service.listApprovalRecordsByStatus("approved", 1, 20);
        var rejectedApprovals = service.listApprovalRecordsByStatus("rejected", 1, 20);

        Map<String, Object> approvedApproval = approvedApprovals.items().stream()
                .filter(item -> approvedRuleId.equals(item.get("ruleId")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> rejectedApproval = rejectedApprovals.items().stream()
                .filter(item -> rejectedRuleId.equals(item.get("ruleId")))
                .findFirst()
                .orElseThrow();
        assertThat(approvedApproval)
                .containsEntry("ruleId", approvedRuleId)
                .containsEntry("status", "approved");
        assertThat(rejectedApproval)
                .containsEntry("ruleId", rejectedRuleId)
                .containsEntry("status", "rejected")
                .containsEntry("approvalComment", "rejected in postgres");
    }

    @Test
    void filtersApprovalRecordsByStatusInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );

        long uniqueSeed = System.nanoTime();
        Map<String, Object> financeDefinition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance approval " + uniqueSeed),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Map<String, Object> legalDefinition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "legalApproval", "type", "approval", "assigneeRole", "legal_manager", "approvalTitle", "Legal approval " + uniqueSeed),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "legalApproval"),
                        Map.of("source", "legalApproval", "target", "end")
                )
        );

        Long financeRuleId = ((Number) service.create(Map.of(
                "name", "postgres finance filter " + uniqueSeed,
                "definition", financeDefinition
        )).get("ruleId")).longValue();
        Long legalRuleId = ((Number) service.create(Map.of(
                "name", "postgres legal filter " + uniqueSeed,
                "definition", legalDefinition
        )).get("ruleId")).longValue();

        for (Long ruleId : List.of(financeRuleId, legalRuleId)) {
            Map<String, Object> definition = ruleId.equals(financeRuleId) ? financeDefinition : legalDefinition;
            service.save(ruleId, Map.of("status", "draft", "definition", definition));
            service.submitForReview(ruleId, Map.of("comment", "ready"));
            service.approve(ruleId, Map.of("comment", "approved"));
        }

        try {
            CurrentUserHolder.set(new CurrentUser(9191L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            service.execute(financeRuleId, Map.of("sample", Map.of("riskScore", 91)));
            service.execute(legalRuleId, Map.of("sample", Map.of("riskScore", 92)));
        } finally {
            CurrentUserHolder.clear();
        }

        var filtered = service.listApprovalRecordsByStatus("pending", 1, 20, Map.of(
                "ruleId", financeRuleId,
                "assigneeRole", "finance_manager",
                "approvalTitle", "Finance approval " + uniqueSeed
        ));

        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", financeRuleId)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("approvalTitle", "Finance approval " + uniqueSeed)
                .containsEntry("status", "pending");
        assertThat(filtered.items().get(0).get("ruleId")).isNotEqualTo(legalRuleId);
    }

    @Test
    void updatesApprovalDelegateRuleFieldsInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        long uniqueSeed = System.nanoTime();
        String originalAssigneeRole = "postgres_delegate_manager_" + uniqueSeed;
        String originalDelegateRole = "postgres_delegate_agent_" + uniqueSeed;
        String updatedAssigneeRole = "postgres_delegate_director_" + uniqueSeed;
        String updatedDelegateRole = "postgres_delegate_director_agent_" + uniqueSeed;
        String updatedFrom = "2026-06-27T08:00Z";
        String updatedTo = "2099-12-31T19:00Z";

        Map<String, Object> created = service.createApprovalDelegateRule(Map.of(
                "assigneeRole", originalAssigneeRole,
                "delegateRole", originalDelegateRole,
                "activeFrom", "2026-06-26T08:00:00Z",
                "activeTo", "2099-12-31T18:00:00Z",
                "reason", "postgres delegate create"
        ));
        Long delegateRuleId = ((Number) created.get("delegateRuleId")).longValue();

        Map<String, Object> updated = service.updateApprovalDelegateRule(delegateRuleId, Map.of(
                "assigneeRole", updatedAssigneeRole,
                "delegateRole", updatedDelegateRole,
                "activeFrom", "2026-06-27T08:00:00Z",
                "activeTo", "2099-12-31T19:00:00Z",
                "activeWeekdays", List.of("MONDAY", "WEDNESDAY"),
                "reason", "postgres delegate edit"
        ));
        var originalList = service.listApprovalDelegateRules(1, 10, Map.of("assigneeRole", originalAssigneeRole));
        var updatedList = service.listApprovalDelegateRules(1, 10, Map.of("assigneeRole", updatedAssigneeRole));

        assertThat(updated)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("assigneeRole", updatedAssigneeRole)
                .containsEntry("delegateRole", updatedDelegateRole)
                .containsEntry("activeFrom", updatedFrom)
                .containsEntry("activeTo", updatedTo)
                .containsEntry("activeWeekdays", List.of("MONDAY", "WEDNESDAY"))
                .containsEntry("status", "enabled")
                .containsEntry("reason", "postgres delegate edit");
        assertThat(originalList.total()).isZero();
        assertThat(updatedList.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("assigneeRole", updatedAssigneeRole)
                .containsEntry("delegateRole", updatedDelegateRole)
                .containsEntry("activeFrom", updatedFrom)
                .containsEntry("activeTo", updatedTo)
                .containsEntry("activeWeekdays", List.of("MONDAY", "WEDNESDAY"))
                .containsEntry("reason", "postgres delegate edit");
    }

    @Test
    void activeWeekdayDelegateRuleDoesNotApplyOnUnmatchedDayInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        long uniqueSeed = System.nanoTime();
        String assigneeRole = "postgres_weekday_manager_" + uniqueSeed;
        String delegateRole = "postgres_weekday_delegate_" + uniqueSeed;
        String tomorrow = OffsetDateTime.now().plusDays(1).getDayOfWeek().name();
        service.createApprovalDelegateRule(Map.of(
                "assigneeRole", assigneeRole,
                "delegateRole", delegateRole,
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "activeWeekdays", List.of(tomorrow),
                "reason", "postgres weekday cover"
        ));
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "weekdayApproval",
                                "type", "approval",
                                "assigneeRole", assigneeRole,
                                "approvalTitle", "Weekday approval " + uniqueSeed
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "weekdayApproval"),
                        Map.of("source", "weekdayApproval", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "postgres weekday delegate " + uniqueSeed,
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = service.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(approvalRecord)
                .containsEntry("assigneeRole", assigneeRole)
                .containsEntry("delegateRole", null)
                .containsEntry("delegateActiveFrom", null)
                .containsEntry("delegateActiveTo", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_weekdays FROM rule_approval_delegate_rules WHERE assignee_role = ?",
                String.class,
                assigneeRole
        )).contains(tomorrow);
    }

    @Test
    void activeDateDelegateRuleDoesNotApplyOnUnmatchedDateInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        long uniqueSeed = System.nanoTime();
        String assigneeRole = "postgres_date_manager_" + uniqueSeed;
        String delegateRole = "postgres_date_delegate_" + uniqueSeed;
        String tomorrow = LocalDate.now().plusDays(1).toString();
        Map<String, Object> created = service.createApprovalDelegateRule(Map.of(
                "assigneeRole", assigneeRole,
                "delegateRole", delegateRole,
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "activeDates", List.of(tomorrow),
                "reason", "postgres date cover"
        ));
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "dateApproval",
                                "type", "approval",
                                "assigneeRole", assigneeRole,
                                "approvalTitle", "Date approval " + uniqueSeed
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", "dateApproval"),
                        Map.of("source", "dateApproval", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "postgres date delegate " + uniqueSeed,
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("status", "draft", "definition", definition));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));

        service.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = service.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(created).containsEntry("activeDates", List.of(tomorrow));
        assertThat(approvalRecord)
                .containsEntry("assigneeRole", assigneeRole)
                .containsEntry("delegateRole", null)
                .containsEntry("delegateActiveFrom", null)
                .containsEntry("delegateActiveTo", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_dates FROM rule_approval_delegate_rules WHERE assignee_role = ?",
                String.class,
                assigneeRole
        )).contains(tomorrow);
    }

    @Test
    void rejectsApprovalDelegateRuleScheduleConflictsInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        long uniqueSeed = System.nanoTime();
        String assigneeRole = "postgres_conflict_manager_" + uniqueSeed;
        String today = LocalDate.now().toString();
        service.createApprovalDelegateRule(Map.of(
                "assigneeRole", assigneeRole,
                "delegateRole", "postgres_conflict_delegate_" + uniqueSeed,
                "activeFrom", "2026-06-29T08:00:00Z",
                "activeTo", "2026-06-29T18:00:00Z",
                "activeDates", List.of(today),
                "reason", "postgres primary cover"
        ));

        assertThatThrownBy(() -> service.createApprovalDelegateRule(Map.of(
                "assigneeRole", assigneeRole,
                "delegateRole", "postgres_conflict_backup_" + uniqueSeed,
                "activeFrom", "2026-06-29T12:00:00Z",
                "activeTo", "2026-06-29T20:00:00Z",
                "activeDates", List.of(today),
                "reason", "postgres conflicting cover"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval delegate rule schedule conflicts");
    }

    @Test
    void batchImportsApprovalDelegateRulesInPostgres() {
        JdbcRuleRepository ruleRepository = new JdbcRuleRepository(jdbcTemplate, new ObjectMapper());
        RuleApplicationService service = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new JdbcAuditRepository(jdbcTemplate, new ObjectMapper()),
                new JdbcSystemAlertRepository(jdbcTemplate, new ObjectMapper())
        );
        long uniqueSeed = System.nanoTime();
        String assigneeRole = "postgres_batch_delegate_manager_" + uniqueSeed;
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        Map<String, Object> result = service.batchImportApprovalDelegateRules(Map.of(
                "rules", List.of(
                        Map.of(
                                "assigneeRole", assigneeRole,
                                "delegateRole", "postgres_batch_delegate_" + uniqueSeed,
                                "activeFrom", "2026-06-29T08:00:00Z",
                                "activeTo", "2026-06-29T18:00:00Z",
                                "activeDates", List.of(today),
                                "reason", "postgres batch primary"
                        ),
                        Map.of(
                                "assigneeRole", assigneeRole,
                                "delegateRole", "postgres_batch_conflict_" + uniqueSeed,
                                "activeFrom", "2026-06-29T12:00:00Z",
                                "activeTo", "2026-06-29T20:00:00Z",
                                "activeDates", List.of(today),
                                "reason", "postgres batch conflict"
                        ),
                        Map.of(
                                "assigneeRole", assigneeRole,
                                "delegateRole", "postgres_batch_tomorrow_" + uniqueSeed,
                                "activeFrom", "2026-06-29T12:00:00Z",
                                "activeTo", "2026-06-29T20:00:00Z",
                                "activeDates", List.of(tomorrow),
                                "reason", "postgres batch tomorrow"
                        ),
                        Map.of(
                                "assigneeRole", "",
                                "delegateRole", "postgres_batch_missing_" + uniqueSeed
                        )
                )
        ));

        assertThat(result)
                .containsEntry("imported", 2)
                .containsEntry("failed", 2);
        assertThat(result.get("results"))
                .asList()
                .element(1)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rowNumber", 2)
                .containsEntry("status", "failed")
                .extractingByKey("reason")
                .asString()
                .contains("approval delegate rule schedule conflicts");
        assertThat(result.get("results"))
                .asList()
                .element(3)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rowNumber", 4)
                .containsEntry("status", "failed")
                .extractingByKey("reason")
                .asString()
                .contains("assigneeRole is required");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_approval_delegate_rules WHERE assignee_role = ?",
                Long.class,
                assigneeRole
        )).isEqualTo(2L);
    }

    @Test
    void persistsAnnotationsAndCollaborationTasksInPostgresWithAccessGuard() {
        Long ownerUserId = 2116L;
        Long assigneeUserId = 2117L;
        Long reportId = insertReport("Collaboration report " + System.nanoTime(), ownerUserId, "draft", null);
        upsertUserAccount(ownerUserId, "owner-" + ownerUserId, "Owner " + ownerUserId, "enabled");
        upsertUserAccount(assigneeUserId, "assignee-" + assigneeUserId, "Assignee " + assigneeUserId, "enabled");
        JdbcUserRepository userRepository = new JdbcUserRepository(jdbcTemplate);
        CollaborationApplicationService service = new CollaborationApplicationService(
                reportRepository,
                new JdbcCollaborationRepository(jdbcTemplate, new ObjectMapper()),
                userRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(ownerUserId + 99, Set.of("analyst"), Set.of("collaboration:write")));
            assertThatThrownBy(() -> service.addAnnotation(reportId, Map.of("content", "not owner")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report collaboration access denied");

            CurrentUserHolder.set(new CurrentUser(ownerUserId, Set.of("analyst"), Set.of("collaboration:write")));
            Map<String, Object> created = service.addAnnotation(reportId, Map.of(
                    "content", "Please verify evidence",
                    "anchor", Map.of(
                            "sectionId", "summary",
                            "startOffset", 14,
                            "endOffset", 22,
                            "selectedText", "evidence"
                    ),
                    "assigneeUserId", assigneeUserId
            ));
            Long annotationId = ((Number) created.get("annotationId")).longValue();
            Long taskId = ((Number) created.get("taskId")).longValue();

            CurrentUserHolder.set(new CurrentUser(ownerUserId + 100, Set.of("analyst"), Set.of("collaboration:write")));
            assertThatThrownBy(() -> service.updateTaskStatus(taskId, Map.of("status", "done")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("task collaboration access denied");

            CurrentUserHolder.set(new CurrentUser(assigneeUserId, Set.of("analyst"), Set.of("collaboration:write")));
            Map<String, Object> updated = service.updateTaskStatus(taskId, Map.of("status", "done"));

            assertThat(created)
                    .containsEntry("reportId", reportId)
                    .containsEntry("assigneeUserId", assigneeUserId)
                    .containsEntry("status", "open")
                    .containsEntry("taskStatus", "open");
            assertThat(updated)
                    .containsEntry("taskId", taskId)
                    .containsEntry("status", "done");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report_annotations WHERE id = ? AND report_id = ?",
                    Long.class,
                    annotationId,
                    reportId
            )).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM collaboration_tasks WHERE id = ?",
                    String.class,
                    taskId
            )).isEqualTo("done");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM collaboration_notifications WHERE task_id = ? AND recipient_user_id IN (?, ?)",
                    Long.class,
                    taskId,
                    ownerUserId,
                    assigneeUserId
            )).isEqualTo(2L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    private Long upsertUserAccount(Long userId, String username, String displayName, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO user_accounts(id, username, display_name, status, roles, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ARRAY['analyst']::TEXT[], CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (id) DO UPDATE
                        SET username = EXCLUDED.username,
                            display_name = EXCLUDED.display_name,
                            status = EXCLUDED.status,
                            roles = EXCLUDED.roles,
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                        """,
                Long.class,
                userId,
                username,
                displayName,
                status
        );
    }

    private Long insertOrganizationUnit(String code, String name, Long parentId, String unitType, int sortOrder) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO organization_units(code, name, parent_id, unit_type, status, sort_order, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'enabled', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                code,
                name,
                parentId,
                unitType,
                sortOrder
        );
    }

    private void insertOrganizationPosition(Long organizationUnitId,
                                            String code,
                                            String name,
                                            List<String> roles,
                                            Long managerUserId,
                                            String status,
                                            int sortOrder) {
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO organization_positions(
                        organization_unit_id, code, name, roles, manager_user_id, status, sort_order, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.setLong(1, organizationUnitId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.setArray(4, connection.createArrayOf("text", roles.toArray(String[]::new)));
            if (managerUserId == null) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, managerUserId);
            }
            statement.setString(6, status);
            statement.setInt(7, sortOrder);
            return statement;
        });
    }

    @Test
    void persistsAndReadsOperationAuditLogsInPostgres() {
        JdbcAuditRepository auditRepository = new JdbcAuditRepository(jdbcTemplate, new ObjectMapper());
        Long resourceId = System.nanoTime();
        Long exportFileId = resourceId + 1;
        OperationLog saved = auditRepository.save(new OperationLog(
                null,
                1006L,
                "report_export",
                "report",
                resourceId,
                "succeeded",
                Map.of("exportFileId", exportFileId, "format", "markdown"),
                null
        ));

        OperationLog loaded = auditRepository.findById(saved.id()).orElseThrow();

        assertThat(loaded.operationType()).isEqualTo("report_export");
        assertThat(loaded.resourceType()).isEqualTo("report");
        assertThat(loaded.resourceId()).isEqualTo(resourceId);
        assertThat(loaded.result()).isEqualTo("succeeded");
        assertThat(loaded.detail())
                .containsEntry("exportFileId", exportFileId)
                .containsEntry("format", "markdown");
        assertThat(auditRepository.findPage(1, 10))
                .extracting(OperationLog::id)
                .contains(saved.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE operation_type = 'report_export' AND resource_id = ?",
                Long.class,
                resourceId
        )).isEqualTo(1L);
    }

    @Test
    void buildsDashboardMetricsFromBusinessTablesInPostgres() {
        JdbcDashboardMetricsRepository dashboardRepository = new JdbcDashboardMetricsRepository(jdbcTemplate);
        Map<String, Object> baseline = dashboardRepository.collect(OffsetDateTime.now().minusDays(1));
        Map<?, ?> baselineCards = (Map<?, ?>) baseline.get("cards");
        Map<?, ?> baselineRuleHealth = (Map<?, ?>) baseline.get("ruleScheduleHealth");
        long suffix = System.nanoTime();
        Long ownerUserId = suffix;
        Long reportWithCitationId = insertReport("Dashboard report cited " + suffix, ownerUserId, "completed", null);
        insertReport("Dashboard report uncited " + suffix, ownerUserId, "completed", null);
        Long knowledgeBaseId = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_bases (name, owner_user_id, status)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "Finance KB " + suffix,
                ownerUserId,
                "enabled"
        );
        Long knowledgeItemId = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_items (knowledge_base_id, title, content, source_type, index_status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                knowledgeBaseId,
                "Revenue citation " + suffix,
                "Revenue risk evidence",
                "manual",
                "indexed",
                ownerUserId
        );
        jdbcTemplate.update("""
                        INSERT INTO knowledge_data_sources (owner_user_id, name, source_type, endpoint, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                ownerUserId,
                "ERP source " + suffix,
                "postgresql",
                "jdbc:postgresql://erp",
                "enabled"
        );
        jdbcTemplate.update("""
                        INSERT INTO report_sections (report_id, section_no, heading, content, citation_marks)
                        VALUES (?, ?, ?, ?, ?::jsonb)
                        """,
                reportWithCitationId,
                1,
                "Summary",
                "Revenue risk evidence",
                "[{\"knowledgeItemId\":\"" + knowledgeItemId + "\",\"referenceId\":\"ref-" + suffix + "\"}]"
        );
        jdbcTemplate.update("""
                        INSERT INTO report_sections (report_id, section_no, heading, content, citation_marks)
                        VALUES (?, ?, ?, ?, '[]'::jsonb)
                        """,
                reportWithCitationId,
                2,
                "Risk",
                "No citation"
        );
        jdbcTemplate.update("""
                        INSERT INTO operation_logs (actor_user_id, operation_type, resource_type, resource_id, result, detail)
                        VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                        """,
                ownerUserId,
                "dashboard_smoke",
                "dashboard",
                reportWithCitationId,
                "succeeded"
        );
        Long scheduledRuleId = jdbcTemplate.queryForObject("""
                        INSERT INTO rules (
                          name, description, status, definition_json, schedule_enabled,
                          schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        )
                        VALUES (?, ?, 'published', '{}'::jsonb, TRUE, 60, CURRENT_TIMESTAMP, 1, 3, '{}'::jsonb)
                        RETURNING id
                        """,
                Long.class,
                "Dashboard scheduled rule " + suffix,
                "healthy scheduled rule"
        );
        jdbcTemplate.update("""
                        INSERT INTO rules (
                          name, description, status, definition_json, schedule_enabled,
                          schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        )
                        VALUES (?, ?, 'published', '{}'::jsonb, TRUE, 60, CURRENT_TIMESTAMP, 2, 2, '{}'::jsonb)
                        """,
                "Dashboard blocked rule " + suffix,
                "blocked scheduled rule"
        );
        jdbcTemplate.update("""
                        INSERT INTO system_alerts (
                          recipient_user_id, type, severity, status, resource_type, resource_id, payload_json, created_at
                        )
                        VALUES (?, 'rule_schedule_run_failed', 'warning', 'unread', 'rule', ?, ?::jsonb, CURRENT_TIMESTAMP)
                        """,
                1L,
                scheduledRuleId,
                "{\"ruleName\":\"Dashboard scheduled rule " + suffix + "\"}"
        );

        Map<String, Object> metrics = dashboardRepository.collect(OffsetDateTime.now().minusDays(1));
        Map<?, ?> cards = (Map<?, ?>) metrics.get("cards");
        Map<?, ?> ruleScheduleHealth = (Map<?, ?>) metrics.get("ruleScheduleHealth");

        assertThat(((Number) cards.get("reportOutputs")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineCards.get("reportOutputs")).longValue() + 2L);
        assertThat(((Number) cards.get("knowledgeItems")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineCards.get("knowledgeItems")).longValue() + 1L);
        assertThat(((Number) cards.get("activeDataSources")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineCards.get("activeDataSources")).longValue() + 1L);
        assertThat(((Number) cards.get("activeUsers")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineCards.get("activeUsers")).longValue() + 1L);
        assertThat(((Number) cards.get("citationHitRate")).doubleValue())
                .isBetween(0.0, 1.0);
        assertThat(metrics.get("reportTrend"))
                .asList()
                .isNotEmpty();
        assertThat(metrics.get("knowledgeRank"))
                .asList()
                .filteredOn(item -> ((Map<?, ?>) item).get("knowledgeBaseId").equals(knowledgeBaseId))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("name", "Finance KB " + suffix)
                .containsEntry("references", 1L);
        assertThat(((Number) ruleScheduleHealth.get("scheduledRules")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineRuleHealth.get("scheduledRules")).longValue() + 2L);
        assertThat(((Number) ruleScheduleHealth.get("failedScheduledRules")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineRuleHealth.get("failedScheduledRules")).longValue() + 2L);
        assertThat(((Number) ruleScheduleHealth.get("blockedScheduledRules")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineRuleHealth.get("blockedScheduledRules")).longValue() + 1L);
        assertThat(((Number) ruleScheduleHealth.get("recentAlerts")).longValue())
                .isGreaterThanOrEqualTo(((Number) baselineRuleHealth.get("recentAlerts")).longValue() + 1L);
    }

    @Test
    void buildsDashboardMetricsWithPersonalScopeInPostgres() {
        JdbcDashboardMetricsRepository dashboardRepository = new JdbcDashboardMetricsRepository(jdbcTemplate);
        long suffix = System.nanoTime();
        Long ownerUserId = suffix;
        Long otherUserId = suffix + 1;
        Long ownerReportId = insertReport("Dashboard personal owner " + suffix, ownerUserId, "completed", null);
        insertReport("Dashboard personal other " + suffix, otherUserId, "completed", null);
        Long ownerKnowledgeBaseId = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_bases (name, owner_user_id, status)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "Personal KB " + suffix,
                ownerUserId,
                "enabled"
        );
        Long otherKnowledgeBaseId = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_bases (name, owner_user_id, status)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "Other KB " + suffix,
                otherUserId,
                "enabled"
        );
        Long ownerKnowledgeItemId = insertKnowledgeItem(ownerKnowledgeBaseId, ownerUserId, "Personal item " + suffix);
        insertKnowledgeItem(otherKnowledgeBaseId, otherUserId, "Other item " + suffix);
        jdbcTemplate.update("""
                        INSERT INTO knowledge_data_sources (owner_user_id, name, source_type, endpoint, status)
                        VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                        """,
                ownerUserId,
                "Personal source " + suffix,
                "postgresql",
                "jdbc:postgresql://personal",
                "enabled",
                otherUserId,
                "Other source " + suffix,
                "postgresql",
                "jdbc:postgresql://other",
                "enabled"
        );
        jdbcTemplate.update("""
                        INSERT INTO report_sections (report_id, section_no, heading, content, citation_marks)
                        VALUES (?, ?, ?, ?, ?::jsonb)
                        """,
                ownerReportId,
                1,
                "Summary",
                "Personal citation",
                "[{\"knowledgeItemId\":\"" + ownerKnowledgeItemId + "\",\"referenceId\":\"personal-ref-" + suffix + "\"}]"
        );
        jdbcTemplate.update("""
                        INSERT INTO operation_logs (actor_user_id, operation_type, resource_type, resource_id, result, detail)
                        VALUES (?, ?, ?, ?, ?, '{}'::jsonb), (?, ?, ?, ?, ?, '{}'::jsonb)
                        """,
                ownerUserId,
                "dashboard_personal",
                "dashboard",
                ownerReportId,
                "succeeded",
                otherUserId,
                "dashboard_other",
                "dashboard",
                ownerReportId + 1,
                "succeeded"
        );

        Map<String, Object> personalMetrics = dashboardRepository.collect(OffsetDateTime.now().minusDays(1), ownerUserId);
        Map<?, ?> cards = (Map<?, ?>) personalMetrics.get("cards");

        assertThat(((Number) cards.get("reportOutputs")).longValue()).isEqualTo(1L);
        assertThat(((Number) cards.get("knowledgeItems")).longValue()).isEqualTo(1L);
        assertThat(((Number) cards.get("activeDataSources")).longValue()).isEqualTo(1L);
        assertThat(((Number) cards.get("activeUsers")).longValue()).isEqualTo(1L);
        assertThat(personalMetrics.get("knowledgeRank"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("knowledgeBaseId", ownerKnowledgeBaseId)
                .containsEntry("name", "Personal KB " + suffix);
    }

    private static Long insertKnowledgeItem(Long knowledgeBaseId, Long ownerUserId, String title) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_items (knowledge_base_id, title, content, source_type, index_status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                knowledgeBaseId,
                title,
                "Dashboard evidence",
                "manual",
                "indexed",
                ownerUserId
        );
    }

    @Test
    void persistsAndReadsModelInvocationAuditTablesInPostgres() {
        JdbcModelInvocationRepository modelInvocationRepository = new JdbcModelInvocationRepository(jdbcTemplate, new ObjectMapper());
        Long taskId = System.nanoTime();
        Long reportId = taskId + 1;
        String eventKey = "it-model-audit-" + taskId;

        ModelInvocationAudit saved = modelInvocationRepository.save(new ModelInvocationAudit(
                null,
                taskId,
                reportId,
                1007L,
                "openai-compatible",
                "gpt",
                "tpl-it",
                "Prompt snapshot for integration test",
                "Context snapshot for integration test",
                Map.of("temperature", 0.2),
                "sha256:it",
                "succeeded",
                1234L,
                321,
                654,
                975,
                null,
                null,
                "trace-it-model-audit",
                eventKey,
                null,
                new ModelResponseAudit(null, null, 1, "Response summary for integration test", Map.of("finish", "stop"), "stop", null)
        ));

        ModelInvocationAudit loaded = modelInvocationRepository.findById(saved.id()).orElseThrow();

        assertThat(loaded.taskId()).isEqualTo(taskId);
        assertThat(loaded.reportId()).isEqualTo(reportId);
        assertThat(loaded.provider()).isEqualTo("openai-compatible");
        assertThat(loaded.modelName()).isEqualTo("gpt");
        assertThat(loaded.promptSnapshot()).isEqualTo("Prompt snapshot for integration test");
        assertThat(loaded.contextSnapshot()).isEqualTo("Context snapshot for integration test");
        assertThat(loaded.parameters()).containsEntry("temperature", 0.2);
        assertThat(loaded.durationMs()).isEqualTo(1234L);
        assertThat(loaded.inputTokens()).isEqualTo(321);
        assertThat(loaded.outputTokens()).isEqualTo(654);
        assertThat(loaded.totalTokens()).isEqualTo(975);
        assertThat(loaded.response().responseContent()).isEqualTo("Response summary for integration test");
        assertThat(loaded.response().finishReason()).isEqualTo("stop");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_invocations WHERE audit_event_key = ?",
                Long.class,
                eventKey
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_responses WHERE invocation_id = ?",
                Long.class,
                saved.id()
        )).isEqualTo(1L);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Long insertReport(String title, Long ownerUserId, String status, Long currentVersionId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO reports (title, owner_user_id, status, current_version_id, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                title,
                ownerUserId,
                status,
                currentVersionId
        );
    }

    private static class NoopGenerationEventRepository implements ReportGenerationEventRepository {
        @Override
        public void append(SseEvent event) {
        }

        @Override
        public List<SseEvent> findByTaskId(Long taskId) {
            return List.of();
        }
    }

    private static class StubReportExportStorage implements ReportExportStorage {
        @Override
        public StoredExport store(String objectKey, String fileName, String contentType, byte[] content) {
            return new StoredExport("it-bucket", objectKey, fileName, contentType, content.length, "https://storage.local/" + objectKey);
        }

        @Override
        public String createDownloadUrl(String objectKey) {
            return "https://storage.local/" + objectKey + "?presigned=true";
        }
    }

    private static class StubDocumentStorage implements com.company.report.knowledge.infrastructure.storage.DocumentStorage {
        @Override
        public StoredObject store(org.springframework.web.multipart.MultipartFile file, String objectKey) {
            return new StoredObject("it-knowledge", objectKey, file.getOriginalFilename(), file.getContentType(), file.getSize());
        }
    }
}
