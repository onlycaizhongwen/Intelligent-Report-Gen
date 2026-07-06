package com.company.report.audit.interfaces.rest;

import com.company.report.audit.application.AuditApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.AuthenticatedEndpoint;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuditController {
    private final AuditApplicationService service;

    public AuditController(AuditApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: audit-history-dashboard / REQ-AUDIT-001 / 普通用户查看个人历史 */
    @AuthenticatedEndpoint("current user personal audit history")
    @GetMapping("/history")
    public ApiResponse<PageResponse<Map<String, Object>>> history(@RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.history(page, pageSize));
    }

    /** OpenSpec: audit-history-dashboard / REQ-AUDIT-001 / 管理员筛选操作日志 */
    @RequiresPermission("audit:read")
    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<Map<String, Object>>> auditLogs(@RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.auditLogs(page, pageSize));
    }

    /** OpenSpec: audit-history-dashboard / REQ-AI-001 / 查询模型调用审计 */
    @RequiresPermission("audit:read")
    @GetMapping("/audit-logs/model-invocations/{invocationId}")
    public ApiResponse<Map<String, Object>> modelInvocation(@PathVariable Long invocationId) {
        return ApiResponse.success(service.modelInvocation(invocationId));
    }

    /** OpenSpec: audit-history-dashboard / REQ-DASH-001 / 切换时间范围 */
    @RequiresPermission("dashboard:read")
    @GetMapping("/dashboard/overview")
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam(defaultValue = "last7days") String range) {
        return ApiResponse.success(service.dashboard(range));
    }
}
