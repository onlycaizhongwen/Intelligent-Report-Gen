package com.company.report.notification.interfaces.rest;

import com.company.report.notification.application.SystemAlertApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SystemAlertController {
    private final SystemAlertApplicationService service;

    public SystemAlertController(SystemAlertApplicationService service) {
        this.service = service;
    }

    @RequiresPermission("notification:read")
    @GetMapping("/system-alerts")
    public ApiResponse<PageResponse<Map<String, Object>>> listMine(@RequestParam(required = false) String status,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listMine(status, page, pageSize));
    }
}
