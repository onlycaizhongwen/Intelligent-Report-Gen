package com.company.report.report.interfaces.rest;

import com.company.report.report.application.ReportApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final ReportApplicationService reportApplicationService;

    public FileController(ReportApplicationService reportApplicationService) {
        this.reportApplicationService = reportApplicationService;
    }

    @RequiresPermission("report:export")
    @GetMapping("/report-exports/{exportFileId}/download-url")
    public ApiResponse<Map<String, Object>> reportExportDownloadUrl(@PathVariable Long exportFileId) {
        return ApiResponse.success(reportApplicationService.createExportDownloadUrl(exportFileId));
    }
}
