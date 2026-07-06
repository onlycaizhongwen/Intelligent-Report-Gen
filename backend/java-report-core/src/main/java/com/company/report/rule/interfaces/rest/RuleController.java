package com.company.report.rule.interfaces.rest;

import com.company.report.rule.application.RuleApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {
    private final RuleApplicationService service;

    public RuleController(RuleApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 创建规则 */
    @RequiresPermission("rule:manage")
    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.list(page, pageSize));
    }

    @RequiresPermission("rule:manage")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.create(request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-delegate-rules")
    public ApiResponse<Map<String, Object>> createApprovalDelegateRule(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createApprovalDelegateRule(request));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-delegate-rules")
    public ApiResponse<PageResponse<Map<String, Object>>> listApprovalDelegateRules(@RequestParam(defaultValue = "1") int page,
                                                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                                                    @RequestParam(required = false) String status,
                                                                                    @RequestParam(required = false) String assigneeRole) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("assigneeRole", assigneeRole);
        return ApiResponse.success(service.listApprovalDelegateRules(page, pageSize, filters));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-templates")
    public ApiResponse<Map<String, Object>> createApprovalTemplate(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createApprovalTemplate(request));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-templates")
    public ApiResponse<PageResponse<Map<String, Object>>> listApprovalTemplates(@RequestParam(defaultValue = "1") int page,
                                                                                @RequestParam(defaultValue = "20") int pageSize,
                                                                                @RequestParam(required = false) String status) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        return ApiResponse.success(service.listApprovalTemplates(page, pageSize, filters));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-templates/{templateId}/usage")
    public ApiResponse<PageResponse<Map<String, Object>>> listApprovalTemplateUsage(@PathVariable Long templateId,
                                                                                    @RequestParam(defaultValue = "1") int page,
                                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.listApprovalTemplateUsage(templateId, page, pageSize));
    }

    @RequiresPermission("rule:manage")
    @PutMapping("/approval-templates/{templateId}")
    public ApiResponse<Map<String, Object>> updateApprovalTemplate(@PathVariable Long templateId,
                                                                   @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateApprovalTemplate(templateId, request));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-templates/{templateId}/versions")
    public ApiResponse<java.util.List<Map<String, Object>>> listApprovalTemplateVersions(@PathVariable Long templateId) {
        return ApiResponse.success(service.listApprovalTemplateVersions(templateId));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-templates/{templateId}/versions/diff")
    public ApiResponse<Map<String, Object>> compareApprovalTemplateVersions(@PathVariable Long templateId,
                                                                            @RequestParam int baseVersion,
                                                                            @RequestParam int targetVersion) {
        return ApiResponse.success(service.compareApprovalTemplateVersions(templateId, baseVersion, targetVersion));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-templates/{templateId}/versions/{version}/rollback")
    public ApiResponse<Map<String, Object>> rollbackApprovalTemplateVersion(@PathVariable Long templateId,
                                                                            @PathVariable int version) {
        return ApiResponse.success(service.rollbackApprovalTemplateVersion(templateId, version));
    }

    @RequiresPermission("rule:manage")
    @GetMapping("/approval-templates/{templateId}/versions/{version}")
    public ApiResponse<Map<String, Object>> getApprovalTemplateVersion(@PathVariable Long templateId,
                                                                       @PathVariable int version) {
        return ApiResponse.success(service.getApprovalTemplateVersion(templateId, version));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-templates/{templateId}/disable")
    public ApiResponse<Map<String, Object>> disableApprovalTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(service.disableApprovalTemplate(templateId));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-templates/{templateId}/enable")
    public ApiResponse<Map<String, Object>> enableApprovalTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(service.enableApprovalTemplate(templateId));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-delegate-rules/batch-import")
    public ApiResponse<Map<String, Object>> batchImportApprovalDelegateRules(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchImportApprovalDelegateRules(request));
    }

    @RequiresPermission("rule:manage")
    @PutMapping("/approval-delegate-rules/{delegateRuleId}")
    public ApiResponse<Map<String, Object>> updateApprovalDelegateRule(@PathVariable Long delegateRuleId,
                                                                       @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.updateApprovalDelegateRule(delegateRuleId, request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-delegate-rules/{delegateRuleId}/disable")
    public ApiResponse<Map<String, Object>> disableApprovalDelegateRule(@PathVariable Long delegateRuleId,
                                                                        @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.disableApprovalDelegateRule(delegateRuleId, request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/approval-delegate-rules/{delegateRuleId}/enable")
    public ApiResponse<Map<String, Object>> enableApprovalDelegateRule(@PathVariable Long delegateRuleId,
                                                                       @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.enableApprovalDelegateRule(delegateRuleId, request));
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 非法连线被拒绝 */
    @RequiresPermission("rule:manage")
    @PutMapping("/{ruleId}")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.save(ruleId, request));
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 单步执行节点 */
    @RequiresPermission("rule:debug")
    @PostMapping("/{ruleId}/debug-runs")
    public ApiResponse<Map<String, Object>> debug(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.debug(ruleId, request));
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 鍙戝竷瀹℃壒 */
    @RequiresPermission("rule:manage")
    @PostMapping("/{ruleId}/review-submissions")
    public ApiResponse<Map<String, Object>> submitForReview(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.submitForReview(ruleId, request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/{ruleId}/approvals")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.approve(ruleId, request));
    }

    @RequiresPermission("rule:debug")
    @PostMapping("/{ruleId}/runs")
    public ApiResponse<Map<String, Object>> execute(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.execute(ruleId, request));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/{ruleId}/runs")
    public ApiResponse<PageResponse<Map<String, Object>>> listRuns(@PathVariable Long ruleId,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listRuns(ruleId, page, pageSize));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/{ruleId}/runs/{runId}/subprocess-topology")
    public ApiResponse<Map<String, Object>> subprocessRunTopology(@PathVariable Long ruleId,
                                                                  @PathVariable Long runId) {
        return ApiResponse.success(service.subprocessRunTopology(ruleId, runId));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/{ruleId}/approval-records")
    public ApiResponse<PageResponse<Map<String, Object>>> listApprovalRecords(@PathVariable Long ruleId,
                                                                              @RequestParam(defaultValue = "1") int page,
                                                                              @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listApprovalRecords(ruleId, page, pageSize));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/approval-records")
    public ApiResponse<PageResponse<Map<String, Object>>> listPendingApprovalRecords(@RequestParam(defaultValue = "1") int page,
                                                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                                                     @RequestParam(defaultValue = "pending") String status,
                                                                                     @RequestParam(required = false) Long ruleId,
                                                                                     @RequestParam(required = false) String assigneeRole,
                                                                                     @RequestParam(required = false) String approvalTitle,
                                                                                     @RequestParam(required = false) Long createdByUserId,
                                                                                     @RequestParam(required = false) Long approvedByUserId,
                                                                                     @RequestParam(required = false) String createdAtFrom,
                                                                                     @RequestParam(required = false) String createdAtTo) {
        if (!"pending".equals(status)
                && !"approved".equals(status)
                && !"rejected".equals(status)
                && !"closed".equals(status)
                && !"supplement_required".equals(status)
                && !"resubmitted".equals(status)) {
            throw new IllegalArgumentException("approval status must be pending, approved, rejected, closed, supplement_required or resubmitted");
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("ruleId", ruleId);
        filters.put("assigneeRole", assigneeRole);
        filters.put("approvalTitle", approvalTitle);
        filters.put("createdByUserId", createdByUserId);
        filters.put("approvedByUserId", approvedByUserId);
        filters.put("createdAtFrom", createdAtFrom);
        filters.put("createdAtTo", createdAtTo);
        return ApiResponse.success(service.listApprovalRecordsByStatus(status, page, pageSize, filters));
    }

    @RequiresPermission("rule:debug")
    @PostMapping("/{ruleId}/approval-records/{approvalRecordId}/actions")
    public ApiResponse<Map<String, Object>> handleApprovalRecord(@PathVariable Long ruleId,
                                                                 @PathVariable Long approvalRecordId,
                                                                 @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.handleApprovalRecord(ruleId, approvalRecordId, request));
    }

    @RequiresPermission("rule:debug")
    @PostMapping("/{ruleId}/approval-records/{approvalRecordId}/supplements")
    public ApiResponse<Map<String, Object>> submitApprovalSupplement(@PathVariable Long ruleId,
                                                                     @PathVariable Long approvalRecordId,
                                                                     @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.submitApprovalSupplement(ruleId, approvalRecordId, request));
    }

    @RequiresPermission("rule:debug")
    @PostMapping("/{ruleId}/approval-records/{approvalRecordId}/reminders")
    public ApiResponse<Map<String, Object>> remindApprovalRecord(@PathVariable Long ruleId,
                                                                 @PathVariable Long approvalRecordId) {
        return ApiResponse.success(service.remindApprovalRecord(ruleId, approvalRecordId));
    }

    @RequiresPermission("rule:debug")
    @PostMapping("/approval-records/batch-actions")
    public ApiResponse<Map<String, Object>> batchHandleApprovalRecords(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchHandleApprovalRecords(request));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/{ruleId}/action-executions")
    public ApiResponse<PageResponse<Map<String, Object>>> listActionExecutions(@PathVariable Long ruleId,
                                                                               @RequestParam(defaultValue = "1") int page,
                                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listActionExecutions(ruleId, page, pageSize));
    }

    @RequiresPermission("rule:debug")
    @GetMapping("/{ruleId}/metrics")
    public ApiResponse<Map<String, Object>> metrics(@PathVariable Long ruleId) {
        return ApiResponse.success(service.metrics(ruleId));
    }

    @RequiresPermission("rule:manage")
    @PutMapping("/{ruleId}/schedule")
    public ApiResponse<Map<String, Object>> configureSchedule(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.configureSchedule(ruleId, request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/{ruleId}/schedule/retry")
    public ApiResponse<Map<String, Object>> retrySchedule(@PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.retrySchedule(ruleId, request));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/{ruleId}/action-executions/{actionExecutionId}/retry")
    public ApiResponse<Map<String, Object>> retryWebhookActionExecution(@PathVariable Long ruleId,
                                                                        @PathVariable Long actionExecutionId) {
        return ApiResponse.success(service.retryWebhookActionExecution(ruleId, actionExecutionId));
    }

    @RequiresPermission("rule:manage")
    @PostMapping("/{ruleId}/action-executions/batch")
    public ApiResponse<Map<String, Object>> batchHandleWebhookActionExecutions(@PathVariable Long ruleId,
                                                                               @RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchHandleWebhookActionExecutions(ruleId, request));
    }
}
