package com.company.report.permission.interfaces.rest;

import com.company.report.permission.application.PermissionApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.AuthenticatedEndpoint;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import com.company.report.shared.security.PublicEndpoint;
import com.company.report.shared.security.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class PermissionController {
    private final PermissionApplicationService service;

    public PermissionController(PermissionApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / 查看权限矩阵 */
    @RequiresPermission("user:manage")
    @GetMapping("/users")
    public ApiResponse<PageResponse<Map<String, Object>>> users(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.users(page, pageSize));
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / Auth entry is external Higress/OIDC; Java exposes current RBAC profile. */
    @AuthenticatedEndpoint("current authenticated RBAC profile")
    @GetMapping("/auth/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            currentUser = new CurrentUser(1L, Set.of("ADMIN"), Set.of("report:create", "report:read", "knowledge:upload"));
        }
        return ApiResponse.success(Map.of(
                "userId", currentUser.userId(),
                "displayName", "当前用户",
                "roles", currentUser.roles(),
                "permissions", currentUser.permissions(),
                "status", currentUser.status(),
                "authProvider", "higress-oidc"
        ));
    }

    @RequiresPermission("user:manage")
    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> addUser(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.addUser(request));
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / 批量导入用户 */
    @RequiresPermission("user:manage")
    @PostMapping("/users/batch-import")
    public ApiResponse<Map<String, Object>> batchImportUsers(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchImportUsers(request));
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / 禁用或启用用户 */
    @RequiresPermission("user:manage")
    @PutMapping("/users/{userId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long userId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateStatus(userId, request));
    }

    @RequiresPermission("permission:read")
    @GetMapping("/roles/permission-matrix")
    public ApiResponse<Map<String, Object>> permissionMatrix() {
        return ApiResponse.success(service.permissionMatrix());
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / Read-only organization directory for role and approver selection. */
    @RequiresPermission("permission:read")
    @GetMapping("/organization-directory")
    public ApiResponse<Map<String, Object>> organizationDirectory() {
        return ApiResponse.success(service.organizationDirectory());
    }

    @RequiresPermission("user:manage")
    @PostMapping("/organization-units")
    public ApiResponse<Map<String, Object>> createOrganizationUnit(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createOrganizationUnit(request));
    }

    @RequiresPermission("user:manage")
    @PutMapping("/organization-units/{unitId}")
    public ApiResponse<Map<String, Object>> updateOrganizationUnit(@PathVariable Long unitId,
                                                                   @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateOrganizationUnit(unitId, request));
    }

    @RequiresPermission("user:manage")
    @PostMapping("/organization-positions")
    public ApiResponse<Map<String, Object>> createOrganizationPosition(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createOrganizationPosition(request));
    }

    @RequiresPermission("user:manage")
    @PostMapping("/organization-position-assignments")
    public ApiResponse<Map<String, Object>> assignUserToOrganizationPosition(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.assignUserToOrganizationPosition(request));
    }

    @RequiresPermission("user:manage")
    @PostMapping("/organization-position-assignments/batch-import")
    public ApiResponse<Map<String, Object>> batchImportOrganizationPositionAssignments(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchImportOrganizationPositionAssignments(request));
    }

    @RequiresPermission("user:manage")
    @PutMapping("/organization-position-assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> updateOrganizationPositionAssignment(@PathVariable Long assignmentId,
                                                                                 @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateOrganizationPositionAssignment(assignmentId, request));
    }

    @RequiresPermission("user:manage")
    @PostMapping("/organization-position-assignments/{assignmentId}/disable")
    public ApiResponse<Map<String, Object>> disableOrganizationPositionAssignment(@PathVariable Long assignmentId,
                                                                                  @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.disableOrganizationPositionAssignment(assignmentId, request));
    }

    /** OpenSpec: permission-collaboration / REQ-COLLAB-001 / 外部用户访问有效链接 */
    @RequiresPermission("report:share")
    @PostMapping("/reports/{reportId}/share-links")
    public ApiResponse<Map<String, Object>> createShare(@PathVariable Long reportId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createShare(reportId, request));
    }

    @RequiresPermission("report:share")
    @PostMapping("/share-links/{shareToken}/revoke")
    public ApiResponse<Map<String, Object>> revokeShare(@PathVariable String shareToken) {
        return ApiResponse.success(service.revokeShare(shareToken));
    }

    @PublicEndpoint("share token access challenge")
    @PostMapping("/share-links/{shareToken}/access")
    public ApiResponse<Map<String, Object>> accessShare(@PathVariable String shareToken,
                                                        @RequestBody Map<String, Object> request,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.success(service.accessShare(shareToken, withShareRiskContext(request, httpRequest)));
    }

    @PublicEndpoint("share token read-only report view")
    @PostMapping("/share-links/{shareToken}/report")
    public ApiResponse<Map<String, Object>> sharedReport(@PathVariable String shareToken,
                                                         @RequestBody Map<String, Object> request,
                                                         HttpServletRequest httpRequest) {
        return ApiResponse.success(service.sharedReport(shareToken, withShareRiskContext(request, httpRequest)));
    }

    @PublicEndpoint("share token controlled export download")
    @PostMapping("/share-links/{shareToken}/exports/{exportFileId}/download-url")
    public ApiResponse<Map<String, Object>> sharedExportDownloadUrl(@PathVariable String shareToken,
                                                                    @PathVariable Long exportFileId,
                                                                    @RequestBody Map<String, Object> request,
                                                                    HttpServletRequest httpRequest) {
        return ApiResponse.success(service.sharedExportDownloadUrl(shareToken, exportFileId, withShareRiskContext(request, httpRequest)));
    }

    private Map<String, Object> withShareRiskContext(Map<String, Object> request, HttpServletRequest httpRequest) {
        Map<String, Object> enriched = new LinkedHashMap<>(request == null ? Map.of() : request);
        String clientIp = firstForwardedIp(header(httpRequest, "X-Forwarded-For"));
        if (clientIp.isBlank()) {
            clientIp = header(httpRequest, "X-Real-IP");
        }
        if (clientIp.isBlank() && httpRequest != null && httpRequest.getRemoteAddr() != null) {
            clientIp = httpRequest.getRemoteAddr().trim();
        }
        String userAgent = header(httpRequest, "User-Agent");
        if (!clientIp.isBlank()) {
            enriched.put("clientIp", clientIp);
        }
        if (!userAgent.isBlank()) {
            enriched.put("userAgent", userAgent);
        }
        return enriched;
    }

    private String firstForwardedIp(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.split(",")[0].trim();
    }

    private String header(HttpServletRequest httpRequest, String name) {
        if (httpRequest == null) {
            return "";
        }
        String value = httpRequest.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
