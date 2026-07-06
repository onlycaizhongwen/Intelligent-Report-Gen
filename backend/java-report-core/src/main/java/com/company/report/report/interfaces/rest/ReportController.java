package com.company.report.report.interfaces.rest;

import com.company.report.report.application.ReportApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.AuthenticatedEndpoint;
import com.company.report.shared.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportApplicationService service;

    public ReportController(ReportApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: report-generation / REQ-REPORT-001 / 自然语言创建报告生成任务 */
    @RequiresPermission("report:create")
    @PostMapping("/reports/generation-tasks")
    public ApiResponse<Map<String, Object>> createGenerationTask(@Valid @RequestBody CreateReportTaskRequest request) {
        return ApiResponse.success(service.createGenerationTask(request.topic(), request.payload()));
    }

    /** OpenSpec: report-generation / REQ-REPORT-001 / 模板参数完整时生成报告 */
    @RequiresPermission("report:create")
    @PostMapping("/reports/template-generation-tasks")
    public ApiResponse<Map<String, Object>> createTemplateTask(@Valid @RequestBody TemplateReportTaskRequest request) {
        return ApiResponse.success(service.createTemplateTask(request.templateId(), request.payload()));
    }

    /** OpenSpec: report-generation / REQ-REPORT-002 / 用户确认大纲后开始正文生成 */
    /** OpenSpec: report-generation / REQ-REPORT-001 / Template field schema for report filling. */
    @RequiresPermission("report:create")
    @GetMapping("/report-templates")
    public ApiResponse<List<Map<String, Object>>> listReportTemplates() {
        return ApiResponse.success(service.listReportTemplates());
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @PostMapping("/enterprise-export-templates")
    public ApiResponse<Map<String, Object>> createEnterpriseExportTemplate(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createEnterpriseExportTemplate(request));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @GetMapping("/enterprise-export-templates")
    public ApiResponse<PageResponse<Map<String, Object>>> listEnterpriseExportTemplates(@RequestParam(defaultValue = "1") int page,
                                                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                                                        @RequestParam Map<String, Object> filters) {
        return ApiResponse.success(service.listEnterpriseExportTemplates(page, pageSize, filters));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @PutMapping("/enterprise-export-templates/{templateId}")
    public ApiResponse<Map<String, Object>> updateEnterpriseExportTemplate(@PathVariable String templateId,
                                                                           @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateEnterpriseExportTemplate(templateId, request));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @GetMapping("/enterprise-export-templates/{templateId}/versions")
    public ApiResponse<List<Map<String, Object>>> listEnterpriseExportTemplateVersions(@PathVariable String templateId) {
        return ApiResponse.success(service.listEnterpriseExportTemplateVersions(templateId));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @PostMapping("/enterprise-export-templates/{templateId}/disable")
    public ApiResponse<Map<String, Object>> disableEnterpriseExportTemplate(@PathVariable String templateId) {
        return ApiResponse.success(service.disableEnterpriseExportTemplate(templateId));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Enterprise export template governance. */
    @RequiresPermission("report:template:manage")
    @PostMapping("/enterprise-export-templates/{templateId}/enable")
    public ApiResponse<Map<String, Object>> enableEnterpriseExportTemplate(@PathVariable String templateId) {
        return ApiResponse.success(service.enableEnterpriseExportTemplate(templateId));
    }

    @RequiresPermission("report:create")
    @PutMapping("/reports/generation-tasks/{taskId}/outline")
    public ApiResponse<Map<String, Object>> confirmOutline(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.confirmOutline(taskId, request));
    }

    /** OpenSpec: report-generation / REQ-REPORT-002 / 生成过程中查看阶段进度 */
    @AuthenticatedEndpoint("current user's report generation event stream")
    @GetMapping(value = "/reports/generation-tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long taskId) {
        return service.openTaskStream(taskId);
    }

    /** OpenSpec: report-generation / REQ-REPORT-002 / Mark generation failure from controlled worker callback. */
    @RequiresPermission("report:create")
    @PostMapping("/reports/generation-tasks/{taskId}/failure")
    public ApiResponse<Map<String, Object>> failGenerationTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.failGenerationTask(taskId, request));
    }

    /** OpenSpec: report-generation / REQ-REPORT-002 / Complete generation from controlled AI worker callback. */
    @RequiresPermission("report:create")
    @PostMapping("/reports/generation-tasks/{taskId}/completion")
    public ApiResponse<Map<String, Object>> completeGenerationTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.completeGenerationTaskFromWorker(taskId, request));
    }

    /** OpenSpec: report-generation / REQ-REPORT-002 / Retry a failed or retryable generation task. */
    @RequiresPermission("report:create")
    @PostMapping("/reports/generation-tasks/{taskId}/retry")
    public ApiResponse<Map<String, Object>> retryGenerationTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.retryGenerationTask(taskId, request));
    }

    /** OpenSpec: report-generation / REQ-REPORT-001 / 我的报告与授权报告列表 */
    @RequiresPermission("report:read")
    @GetMapping("/reports")
    public ApiResponse<PageResponse<Map<String, Object>>> listReports(@RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listReports(page, pageSize));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-003 / 查看报告详情 */
    @RequiresPermission("report:read")
    @GetMapping("/reports/{reportId}")
    public ApiResponse<Map<String, Object>> getReport(@PathVariable Long reportId) {
        return ApiResponse.success(service.getReport(reportId));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-003 / 查看引用质量 */
    @RequiresPermission("report:read")
    @GetMapping("/reports/{reportId}/references/{referenceId}")
    public ApiResponse<Map<String, Object>> getReference(@PathVariable Long reportId, @PathVariable Long referenceId) {
        return ApiResponse.success(service.getReference(reportId, referenceId));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / 导出 PDF 报告 */
    @RequiresPermission("report:export")
    @PostMapping("/reports/{reportId}/exports")
    public ApiResponse<Map<String, Object>> createExport(@PathVariable Long reportId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createExport(reportId, request));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-004 / Query export status and download URL. */
    @RequiresPermission("report:export")
    @GetMapping("/reports/{reportId}/exports/{exportFileId}")
    public ApiResponse<Map<String, Object>> getExportStatus(@PathVariable Long reportId, @PathVariable Long exportFileId) {
        return ApiResponse.success(service.getExportStatus(reportId, exportFileId));
    }

    /** OpenSpec: report-citation-export-version / REQ-REPORT-005 / 回滚报告版本 */
    @RequiresPermission("report:create")
    @PostMapping("/reports/{reportId}/versions/{versionId}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable Long reportId, @PathVariable Long versionId) {
        return ApiResponse.success(service.rollbackVersion(reportId, versionId));
    }

    @RequiresPermission("report:read")
    @GetMapping("/reports/{reportId}/versions")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable Long reportId) {
        return ApiResponse.success(service.listVersions(reportId));
    }

    @RequiresPermission("report:read")
    @GetMapping("/reports/{reportId}/versions/diff")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable Long reportId,
                                                            @RequestParam Long baseVersionId,
                                                            @RequestParam Long targetVersionId) {
        return ApiResponse.success(service.compareVersions(reportId, baseVersionId, targetVersionId));
    }

    public record CreateReportTaskRequest(@NotBlank String topic, Map<String, Object> payload) {}
    public record TemplateReportTaskRequest(@NotBlank String templateId, Map<String, Object> payload) {}
}
