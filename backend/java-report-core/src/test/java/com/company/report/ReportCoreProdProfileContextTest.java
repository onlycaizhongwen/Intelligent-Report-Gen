package com.company.report;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ReportCoreApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-profile-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "storage.minio.endpoint=http://minio:9000",
        "storage.minio.access-key=minioadmin",
        "storage.minio.secret-key=minioadmin123",
        "rocketmq.endpoint=localhost:9876",
        "rocketmq.enabled=false",
        "security.jwt.secret=local-dev-secret-change-me-32-bytes-minimum",
        "security.jwt.oidc-jwks-url=http://127.0.0.1/.well-known/jwks.json",
        "security.data-source-credential-key=prod-profile-test-data-source-key",
        "security.data-source-credential-key-id=prod-profile-test-key",
        "knowledge.data-source.endpoint-allowlist=localhost,127.0.0.1",
        "knowledge.data-source.profile-catalog-json=[]"
})
@ActiveProfiles("prod")
class ReportCoreProdProfileContextTest {
    @Test
    void prodProfileLoadsWithExternalizedInfrastructureProperties() {
    }
}
