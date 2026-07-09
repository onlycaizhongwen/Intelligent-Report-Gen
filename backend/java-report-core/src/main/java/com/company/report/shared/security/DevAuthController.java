package com.company.report.shared.security;

import com.company.report.shared.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Profile("dev")
@RequestMapping("/api/v1/auth")
public class DevAuthController {
    private static final List<String> DEV_PERMISSIONS = List.of(
            "report:create",
            "report:read",
            "report:export",
            "report:template:manage",
            "report:share",
            "collaboration:write",
            "knowledge:manage",
            "knowledge:upload",
            "datasource:manage",
            "rule:manage",
            "rule:debug",
            "audit:read",
            "dashboard:read",
            "notification:read",
            "permission:read",
            "user:manage"
    );

    private final JwtTokenProvider tokenProvider;

    public DevAuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PublicEndpoint("local development preview login")
    @PostMapping("/dev-login")
    public ApiResponse<Map<String, Object>> devLogin() {
        List<String> roles = List.of("system_admin");
        String accessToken = tokenProvider.generateToken(1L, roles, DEV_PERMISSIONS);
        return ApiResponse.success(Map.of(
                "accessToken", accessToken,
                "userId", 1L,
                "displayName", "本地开发管理员",
                "roles", roles,
                "permissions", DEV_PERMISSIONS,
                "authProvider", "local-dev"
        ));
    }
}
