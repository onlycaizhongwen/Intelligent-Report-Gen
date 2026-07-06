package com.company.report.shared.security;

import com.company.report.permission.application.PermissionApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:share-link-security-runtime-contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "rocketmq.enabled=false",
        "security.jwt.secret=local-dev-secret-change-me-32-bytes-minimum"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareLinkSecurityRuntimeContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionApplicationService permissionApplicationService;

    @Test
    void publicShareAccessPostReachesBusinessLayerWithoutBearerToken() throws Exception {
        when(permissionApplicationService.accessShare(eq("public-token"), any()))
                .thenReturn(Map.of("accessGranted", true, "shareToken", "public-token"));

        mockMvc.perform(post("/api/v1/share-links/public-token/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessGranted").value(true));

        verify(permissionApplicationService).accessShare(eq("public-token"), any());
    }

    @Test
    void publicShareAccessGetDoesNotBypassBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/share-links/public-token/access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(permissionApplicationService);
    }

    @Test
    void shareManagementEndpointStillRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/reports/10/share-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(permissionApplicationService);
    }
}
