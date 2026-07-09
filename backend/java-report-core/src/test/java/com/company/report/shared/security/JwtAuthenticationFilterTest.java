package com.company.report.shared.security;

import com.company.report.shared.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtTokenProvider("0123456789abcdef0123456789abcdef", 7200),
            objectMapper
    );

    @Test
    void returnsReadableChineseMessageWhenBearerTokenIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("missing token must stop before downstream filter");
        });

        ApiResponse<?> body = objectMapper.readValue(response.getContentAsString(), ApiResponse.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.message()).isEqualTo("请登录后继续操作");
    }
    @Test
    void authenticatedIntentEndpointsRequireBearerToken() throws Exception {
        assertMissingTokenRejected("GET", "/api/v1/auth/me");
        assertMissingTokenRejected("GET", "/api/v1/history");
        assertMissingTokenRejected("GET", "/api/v1/reports/generation-tasks/42/stream");
    }

    @Test
    void publicShareEndpointsBypassJwtAndContinueToBusinessValidation() throws Exception {
        assertPublicPathContinues("POST", "/api/v1/share-links/share-token/access");
        assertPublicPathContinues("POST", "/api/v1/share-links/share-token/report");
        assertPublicPathContinues("POST", "/api/v1/share-links/share-token/exports/77/download-url");
    }

    @Test
    void devLoginEndpointBypassesJwtSoLocalPreviewCanAcquireToken() throws Exception {
        assertPublicPathContinues("POST", "/api/v1/auth/dev-login");
    }

    @Test
    void publicShareEndpointsDoNotBypassJwtForUnexpectedHttpMethods() throws Exception {
        assertMissingTokenRejected("GET", "/api/v1/share-links/share-token/access");
        assertMissingTokenRejected("GET", "/api/v1/share-links/share-token/report");
        assertMissingTokenRejected("GET", "/api/v1/share-links/share-token/exports/77/download-url");
    }

    private void assertMissingTokenRejected(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError(path + " must require bearer token before downstream filter");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private void assertPublicPathContinues(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            continued.set(true);
            ((MockHttpServletResponse) servletResponse).setStatus(204);
        });

        assertThat(continued).isTrue();
        assertThat(response.getStatus()).isEqualTo(204);
    }
}
