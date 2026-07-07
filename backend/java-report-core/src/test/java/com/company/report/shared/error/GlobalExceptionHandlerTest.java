package com.company.report.shared.error;

import com.company.report.shared.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsSecurityExceptionToForbiddenBusinessResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleSecurity(new SecurityException("share password invalid"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(403);
    }

    @Test
    void mapsShareRateLimitSecurityExceptionToTooManyRequestsResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleSecurity(
                new SecurityException("share access rate limited: token")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(429);
        assertThat(response.getBody().message()).isEqualTo("share access rate limited");
    }

    @Test
    void mapsShareChallengeSecurityExceptionToPreconditionRequiredResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleSecurity(
                new SecurityException("share access challenge required: token")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(428);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(428);
        assertThat(response.getBody().message()).isEqualTo("share access challenge required");
    }

    @Test
    void mapsNotFoundIllegalArgumentToNotFoundResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(
                new IllegalArgumentException("share link not found: missing-token")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("share link not found: missing-token");
    }

    @Test
    void mapsInvalidStateToConflictResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalState(
                new IllegalStateException("share link expired: expired-token")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("share link expired: expired-token");
    }

    @Test
    void mapsTypeMismatchRequestParameterToBadRequestResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("1' or '1'='1", Integer.class, "page", null, null)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("请求参数错误");
    }
}
