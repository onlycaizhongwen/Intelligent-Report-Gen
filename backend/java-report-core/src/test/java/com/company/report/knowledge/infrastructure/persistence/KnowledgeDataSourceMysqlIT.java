package com.company.report.knowledge.infrastructure.persistence;

import com.company.report.audit.infrastructure.persistence.JdbcAuditRepository;
import com.company.report.knowledge.application.DataSourceCredentialCodec;
import com.company.report.knowledge.application.KnowledgeApplicationService;
import com.company.report.knowledge.domain.service.KnowledgeDomainService;
import com.company.report.knowledge.infrastructure.storage.DocumentStorage;
import com.company.report.notification.infrastructure.persistence.JdbcSystemAlertRepository;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION", matches = "true")
class KnowledgeDataSourceMysqlIT {
    private static final String POSTGRES_JDBC_URL = env("POSTGRES_IT_JDBC_URL", "jdbc:postgresql://localhost:5432/intelligent_report");
    private static final String POSTGRES_USERNAME = env("POSTGRES_IT_USERNAME", "report");
    private static final String POSTGRES_PASSWORD = env("POSTGRES_IT_PASSWORD", "report123");
    private static final String MYSQL_JDBC_URL = env("MYSQL_IT_JDBC_URL", "jdbc:mysql://127.0.0.1:13306/erp_source");
    private static final String MYSQL_USERNAME = env("MYSQL_IT_USERNAME", "erp");
    private static final String MYSQL_PASSWORD = env("MYSQL_IT_PASSWORD", "erp123");

    private static JdbcTemplate postgres;

    @BeforeAll
    static void setUpPostgres() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES_JDBC_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        postgres = new JdbcTemplate(dataSource);
    }

    @Test
    void syncsRealMysqlContainerRowsIntoPostgresKnowledgeItemsWithCursor() throws Exception {
        String sourceTable = "erp_reports_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(MYSQL_JDBC_URL, MYSQL_USERNAME, MYSQL_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE %s (
                      id BIGINT PRIMARY KEY,
                      title VARCHAR(255) NOT NULL,
                      content TEXT NOT NULL
                    )
                    """.formatted(sourceTable));
            statement.execute("""
                    INSERT INTO %s(id, title, content) VALUES
                    (1, 'MySQL container revenue', 'Revenue from the real MySQL container increased 11 percent'),
                    (2, 'MySQL container risk', 'Receivable risk from the real MySQL container requires follow-up')
                    """.formatted(sourceTable));
        }
        KnowledgeApplicationService service = knowledgeService();

        try {
            CurrentUserHolder.set(new CurrentUser(4107L, Set.of("analyst"), Set.of("knowledge:manage", "knowledge:upload")));
            Long knowledgeBaseId = ((Number) service.createKnowledgeBase(Map.of(
                    "name", "MySQL Container KB " + System.nanoTime()
            )).get("knowledgeBaseId")).longValue();
            Long dataSourceId = ((Number) service.saveDataSource(Map.of(
                    "name", "ERP MySQL Container",
                    "sourceType", "mysql",
                    "endpoint", MYSQL_JDBC_URL,
                    "username", MYSQL_USERNAME,
                    "password", MYSQL_PASSWORD,
                    "knowledgeBaseId", knowledgeBaseId,
                    "syncQuery", "SELECT id, title, content FROM " + sourceTable,
                    "cursorColumn", "id"
            )).get("dataSourceId")).longValue();

            Map<String, Object> connectionTest = service.testConnection(Map.of("dataSourceId", dataSourceId));
            Map<String, Object> firstRun = service.startDataSourceSync(dataSourceId, Map.of("mode", "manual"));
            try (var connection = DriverManager.getConnection(MYSQL_JDBC_URL, MYSQL_USERNAME, MYSQL_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO %s(id, title, content)
                        VALUES (3, 'MySQL container margin', 'Margin from the real MySQL container improved')
                        """.formatted(sourceTable));
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
            assertThat(service.searchItems(1, 10, "MySQL container").items())
                    .hasSize(3)
                    .allSatisfy(item -> assertThat(item).containsEntry("sourceType", "data_source:mysql"));
            assertThat(service.listDataSourceSyncRuns(dataSourceId, 1, 10).items())
                    .extracting(run -> run.get("status"))
                    .containsExactly("succeeded", "succeeded");
            assertThat(postgres.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_items WHERE knowledge_base_id = ? AND source_type = 'data_source:mysql'",
                    Long.class,
                    knowledgeBaseId
            )).isEqualTo(3L);
        } finally {
            CurrentUserHolder.clear();
            try (var connection = DriverManager.getConnection(MYSQL_JDBC_URL, MYSQL_USERNAME, MYSQL_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + sourceTable);
            }
        }
    }

    private static KnowledgeApplicationService knowledgeService() {
        return new KnowledgeApplicationService(
                new KnowledgeDomainService(),
                (file, objectKey) -> new DocumentStorage.StoredObject("it-knowledge", objectKey, file.getOriginalFilename(), file.getContentType(), file.getSize()),
                new JdbcKnowledgeDocumentRepository(postgres),
                new JdbcKnowledgeBaseRepository(postgres),
                (eventType, eventKey, payload) -> {
                },
                new DataSourceCredentialCodec("mysql-it-data-source-key"),
                new JdbcAuditRepository(postgres, new ObjectMapper()),
                new JdbcSystemAlertRepository(postgres, new ObjectMapper())
        );
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
