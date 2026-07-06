package com.company.report.citation.interfaces.rest;

import com.company.report.citation.application.CollaborationApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CollaborationController {
    private final CollaborationApplicationService service;

    public CollaborationController(CollaborationApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: permission-collaboration / REQ-COLLAB-002 / 提交批注并指派任务 */
    @RequiresPermission("collaboration:write")
    @PostMapping("/reports/{reportId}/annotations")
    public ApiResponse<Map<String, Object>> addAnnotation(@PathVariable Long reportId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.addAnnotation(reportId, request));
    }

    /** OpenSpec: permission-collaboration / REQ-COLLAB-002 / 更新任务处理状态 */
    @RequiresPermission("collaboration:write")
    @PutMapping("/tasks/{taskId}/status")
    public ApiResponse<Map<String, Object>> updateTaskStatus(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateTaskStatus(taskId, request));
    }
}
