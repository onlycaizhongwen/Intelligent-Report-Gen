package com.company.report.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-runtime-contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "rocketmq.enabled=false",
        "security.jwt.secret=local-dev-secret-change-me-32-bytes-minimum"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRuntimeContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void protectedPermissionEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/roles/permission-matrix"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void protectedPermissionEndpointRejectsTokenWithoutPermission() throws Exception {
        String token = tokenProvider.generateToken(101L, List.of("viewer"), List.of("report:read"));

        mockMvc.perform(get("/api/v1/roles/permission-matrix")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void protectedWriteEndpointRejectsTokenWithoutPermissionBeforeRequestBodyParsing() throws Exception {
        String token = tokenProvider.generateToken(104L, List.of("viewer"), List.of("report:read"));

        mockMvc.perform(post("/api/v1/data-sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void protectedPermissionEndpointAllowsTokenWithPermission() throws Exception {
        String token = tokenProvider.generateToken(102L, List.of("system_admin"), List.of("permission:read"));

        mockMvc.perform(get("/api/v1/roles/permission-matrix")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'permission:read')]").exists());
    }

    @Test
    void authenticatedOnlyEndpointRejectsDisabledAccountToken() throws Exception {
        String token = tokenProvider.generateToken(103L, List.of("viewer"), List.of("report:read"), "disabled");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
