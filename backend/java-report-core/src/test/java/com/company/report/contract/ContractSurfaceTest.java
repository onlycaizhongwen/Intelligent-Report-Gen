package com.company.report.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSurfaceTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path API_CONTRACT = Path.of("../../docs/skill-chain/api_contract.md");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern REQUEST_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping(?:\\((.*)\\))?");
    private static final Pattern QUOTED_PATH = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern VALUE_PATH = Pattern.compile("value\\s*=\\s*\"([^\"]*)\"");

    @Test
    void everyControllerEndpointDeclaresSecurityIntent() throws IOException {
        List<String> missingSecurityIntent = new ArrayList<>();
        try (var files = Files.walk(SOURCE_ROOT.resolve("com/company/report"))) {
            for (Path controller : files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                List<String> lines = Files.readAllLines(controller);
                for (int index = 0; index < lines.size(); index += 1) {
                    String line = lines.get(index).trim();
                    if (!line.matches("@(Get|Post|Put|Delete|Patch)Mapping.*")) {
                        continue;
                    }
                    int from = Math.max(0, index - 4);
                    String annotationWindow = String.join("\n", lines.subList(from, index + 1));
                    if (!annotationWindow.contains("@RequiresPermission")
                            && !annotationWindow.contains("@AuthenticatedEndpoint")
                            && !annotationWindow.contains("@PublicEndpoint")) {
                        missingSecurityIntent.add(SOURCE_ROOT.relativize(controller) + ":" + (index + 1) + " " + line);
                    }
                }
            }
        }

        assertThat(missingSecurityIntent).isEmpty();
    }

    @Test
    void everyControllerEndpointIsDocumentedInApiContract() throws IOException {
        String apiContract = Files.readString(API_CONTRACT);
        List<String> missingEndpoints = new ArrayList<>();
        try (var files = Files.walk(SOURCE_ROOT.resolve("com/company/report"))) {
            for (Path controller : files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                List<String> lines = Files.readAllLines(controller);
                String basePath = controllerBasePath(lines);
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    Matcher matcher = METHOD_MAPPING.matcher(line);
                    if (!matcher.matches()) {
                        continue;
                    }
                    String method = matcher.group(1).toUpperCase();
                    String path = normalizePath(basePath, mappingPath(matcher.group(2)));
                    String markdownTableToken = "`" + path + "` | " + method;
                    String traceabilityTableToken = "`" + method + " " + path + "`";
                    if (!apiContract.contains(markdownTableToken) && !apiContract.contains(traceabilityTableToken)) {
                        missingEndpoints.add(method + " " + path);
                    }
                }
            }
        }

        assertThat(missingEndpoints).isEmpty();
    }

    @Test
    void everyControllerEndpointHasStructuredJsonContract() throws IOException {
        String apiContract = Files.readString(API_CONTRACT);
        List<Map<String, Object>> structuredContracts = new ArrayList<>();
        structuredContracts.addAll(readJsonContractBlock(apiContract, "## 6. API JSON 契约清单"));
        structuredContracts.addAll(readJsonContractBlock(apiContract, "### 13.2 后端实际端点结构化 JSON 契约补遗"));
        Set<String> structuredEndpoints = structuredContracts.stream()
                .peek(this::assertStructuredContractShape)
                .map(contract -> contract.get("method") + " " + contract.get("path"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missingStructuredContracts = new ArrayList<>();
        try (var files = Files.walk(SOURCE_ROOT.resolve("com/company/report"))) {
            for (Path controller : files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                List<String> lines = Files.readAllLines(controller);
                String basePath = controllerBasePath(lines);
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    Matcher matcher = METHOD_MAPPING.matcher(line);
                    if (!matcher.matches()) {
                        continue;
                    }
                    String method = matcher.group(1).toUpperCase();
                    String path = normalizePath(basePath, mappingPath(matcher.group(2)));
                    if (!structuredEndpoints.contains(method + " " + path)) {
                        missingStructuredContracts.add(method + " " + path);
                    }
                }
            }
        }

        assertThat(missingStructuredContracts).isEmpty();
    }

    @Test
    void publicShareEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/share-links/{shareToken}/report"),
                "accessGranted", "shareToken", "reportId", "title", "status", "currentVersionId",
                "sections", "allowDownload", "exports");
        assertResponseDataProperties(contractsByEndpoint.get(
                        "POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url"),
                "exportFileId", "reportId", "fileName", "contentType", "sizeBytes", "downloadPolicy",
                "downloadUrl", "expiresAt");
    }

    @Test
    void enterpriseExportTemplateEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = backendSupplementContractsByEndpoint();

        assertEnterpriseTemplateDataProperties(contractsByEndpoint.get("POST /api/v1/enterprise-export-templates"));
        assertEnterpriseTemplatePageProperties(contractsByEndpoint.get("GET /api/v1/enterprise-export-templates"));
        assertEnterpriseTemplateDataProperties(contractsByEndpoint.get("PUT /api/v1/enterprise-export-templates/{templateId}"));
        assertEnterpriseTemplateArrayDataProperties(contractsByEndpoint.get(
                "GET /api/v1/enterprise-export-templates/{templateId}/versions"));
        assertEnterpriseTemplateDataProperties(contractsByEndpoint.get(
                "POST /api/v1/enterprise-export-templates/{templateId}/disable"));
        assertEnterpriseTemplateDataProperties(contractsByEndpoint.get(
                "POST /api/v1/enterprise-export-templates/{templateId}/enable"));
    }

    @Test
    void reportGenerationTaskEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertReportGenerationTaskDataProperties(contractsByEndpoint.get("POST /api/v1/reports/generation-tasks"));
        assertReportGenerationTaskDataProperties(contractsByEndpoint.get("POST /api/v1/reports/template-generation-tasks"),
                "templateSnapshot");
        assertReportGenerationTaskDataProperties(contractsByEndpoint.get(
                "PUT /api/v1/reports/generation-tasks/{taskId}/outline"), "confirmed", "nextStage");
        assertReportGenerationTaskDataProperties(contractsByEndpoint.get(
                "POST /api/v1/reports/generation-tasks/{taskId}/completion"), "versionId");
        assertReportGenerationTaskDataProperties(contractsByEndpoint.get(
                "POST /api/v1/reports/generation-tasks/{taskId}/failure"), "errorCode");
        assertReportGenerationTaskDataProperties(contractsByEndpoint.get(
                "POST /api/v1/reports/generation-tasks/{taskId}/retry"), "retryReason");
    }

    @Test
    void reportExportEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/reports/{reportId}/exports/{exportFileId}"),
                "reportId", "exportFileId", "status", "format", "templateId", "downloadPolicy",
                "bucket", "objectKey", "fileName", "contentType", "sizeBytes", "downloadUrl", "expiresAt",
                "brandSnapshot");
        assertResponseDataProperties(contractsByEndpoint.get(
                        "GET /api/v1/files/report-exports/{exportFileId}/download-url"),
                "exportFileId", "reportId", "fileName", "contentType", "sizeBytes", "downloadUrl", "expiresAt");
    }

    @Test
    void knowledgeDocumentEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/documents/{documentId}"),
                "documentId", "fileObjectId", "knowledgeBaseId", "filename", "fileType", "size",
                "contentType", "bucket", "objectKey", "parseStatus", "parseFailureReason");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/knowledge-items/batch-import"),
                "knowledgeBaseId", "total", "imported", "failed", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/knowledge-items/batch-import"),
                "items", "title", "status", "reason", "itemId", "knowledgeBaseId");
    }

    @Test
    void dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/data-sources/test-connection"),
                "dataSourceId", "success", "message", "sourceType");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/data-sources"),
                "dataSourceId", "ownerUserId", "name", "sourceType", "endpoint", "status",
                "knowledgeBaseId", "syncQuery", "fieldMapping", "cursorColumn", "lastCursor",
                "scheduleEnabled", "scheduleIntervalSeconds", "nextRunAt", "failureCount",
                "maxRetryCount", "credentialConfigured");
        assertTopLevelArrayDataItemProperties(contractsByEndpoint.get("GET /api/v1/data-sources/presets"),
                "presetId", "displayName", "category", "sourceType", "endpoint", "username",
                "syncQuery", "fieldMapping", "cursorColumn", "scheduleEnabled",
                "scheduleIntervalSeconds", "maxRetryCount");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/data-sources/credentials/reencrypt"),
                "scannedCount", "migratedCount");
        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/data-sources/profile-drift"),
                "scannedCount", "driftCount", "items");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/data-sources/profile-drift"),
                "items", "dataSourceId", "name", "sourceType", "profileId", "cursorColumn", "failureReason");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/data-sources/profile-drift/repair"),
                "scannedCount", "driftCount", "repairedCount", "failedCount", "requiresConfirmation", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/data-sources/profile-drift/repair"),
                "items", "dataSourceId", "profileId", "repaired", "requiresConfirmation");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/data-sources/{dataSourceId}/profile-drift/repair"),
                "dataSourceId", "profileId", "repaired", "requiresConfirmation", "currentFailureReason",
                "previousCursorColumn", "proposedCursorColumn", "proposedFieldMapping");
        assertNestedObjectProperties(contractsByEndpoint.get("POST /api/v1/data-sources/{dataSourceId}/profile-drift/repair"),
                "proposedFieldMapping", "profileId", "rowsPath", "titleField", "contentField", "cursorField",
                "method", "authType");
        assertDataSourceSyncRunProperties(contractsByEndpoint.get("POST /api/v1/data-sources/{dataSourceId}/sync-runs"));
        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/data-sources/{dataSourceId}/sync-runs"),
                "items", "page", "pageSize", "total");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/data-sources/{dataSourceId}/sync-runs"),
                "items", "syncRunId", "dataSourceId", "mode", "status", "processedRows",
                "failureReason", "message", "lastCursor", "startedAt", "finishedAt");
    }

    @Test
    void authenticatedUserAndBatchImportEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/auth/me"),
                "userId", "displayName", "roles", "permissions", "status", "authProvider");
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/users/batch-import"),
                "imported", "failed", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/users/batch-import"),
                "items", "username", "status", "reason", "userId", "displayName",
                "department", "position", "roles");
    }

    @Test
    void organizationDirectoryEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/organization-directory"),
                "departments", "roles", "organizationTree");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/organization-directory"),
                "departments", "department", "positions");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/organization-directory"),
                "roles", "role", "department", "position", "users");

        assertOrganizationUnitProperties(contractsByEndpoint.get("POST /api/v1/organization-units"));
        assertOrganizationUnitProperties(contractsByEndpoint.get("PUT /api/v1/organization-units/{unitId}"));
        assertOrganizationPositionProperties(contractsByEndpoint.get("POST /api/v1/organization-positions"));
        assertOrganizationPositionAssignmentProperties(contractsByEndpoint.get(
                "POST /api/v1/organization-position-assignments"));
        assertOrganizationPositionAssignmentProperties(contractsByEndpoint.get(
                "PUT /api/v1/organization-position-assignments/{assignmentId}"));
        assertOrganizationPositionAssignmentProperties(contractsByEndpoint.get(
                "POST /api/v1/organization-position-assignments/{assignmentId}/disable"));
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/organization-position-assignments/batch-import"),
                "imported", "failed", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/organization-position-assignments/batch-import"),
                "items", "userId", "positionId", "status", "reason", "assignmentId",
                "primary", "activeFrom", "activeTo");
    }

    @Test
    void notificationAndShareRevokeEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/system-alerts"),
                "items", "page", "pageSize", "total");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/system-alerts"),
                "items", "alertId", "recipientUserId", "type", "severity", "status",
                "resourceType", "resourceId", "payload", "createdAt");
        assertShareLinkProperties(contractsByEndpoint.get("POST /api/v1/share-links/{shareToken}/revoke"));
    }

    @Test
    void reportTemplateAndVersionDiffEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertResponseArrayDataItemProperties(contractsByEndpoint.get("GET /api/v1/report-templates"),
                "templateId", "name", "category", "version", "status", "fields", "outlineSchema", "updatedAt");
        assertResponseArrayDataNestedArrayItemProperties(contractsByEndpoint.get("GET /api/v1/report-templates"),
                "fields", "fieldKey", "label", "type", "required", "options", "defaultValue", "helpText");
        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/reports/{reportId}/versions/diff"),
                "reportId", "baseVersionId", "targetVersionId", "summary", "changes");
        assertNestedObjectProperties(contractsByEndpoint.get("GET /api/v1/reports/{reportId}/versions/diff"),
                "summary", "added", "removed", "modified", "unchanged");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/reports/{reportId}/versions/diff"),
                "changes", "changeType", "heading", "baseContent", "targetContent",
                "baseCitations", "targetCitations");
    }

    @Test
    void ruleGovernanceEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertApprovalDelegateRuleProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-delegate-rules"));
        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/approval-delegate-rules"),
                "delegateRuleId", "assigneeRole", "delegateRole", "activeFrom", "activeTo",
                "activeWeekdays", "activeDates", "status", "reason", "createdByUserId", "createdAt");
        assertApprovalDelegateRuleProperties(contractsByEndpoint.get(
                "PUT /api/v1/rules/approval-delegate-rules/{delegateRuleId}"));
        assertApprovalDelegateRuleProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable"));
        assertApprovalDelegateRuleProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable"));
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-delegate-rules/batch-import"),
                "imported", "failed", "results");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-delegate-rules/batch-import"),
                "results", "rowNumber", "status", "reason", "delegateRuleId", "assigneeRole", "delegateRole");

        assertApprovalTemplateProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-templates"));
        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/approval-templates"),
                "approvalTemplateId", "name", "description", "status", "version", "steps",
                "usageCount", "usageRules", "createdByUserId", "createdAt", "updatedAt");
        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/approval-templates/{templateId}/usage"),
                "ruleId", "name", "status");
        assertApprovalTemplateProperties(contractsByEndpoint.get("PUT /api/v1/rules/approval-templates/{templateId}"));
        assertResponseArrayDataItemProperties(contractsByEndpoint.get(
                        "GET /api/v1/rules/approval-templates/{templateId}/versions"),
                "approvalTemplateId", "name", "description", "status", "version", "steps",
                "usageCount", "usageRules", "createdByUserId", "createdAt", "updatedAt");
        assertApprovalTemplateVersionDiffProperties(contractsByEndpoint.get(
                "GET /api/v1/rules/approval-templates/{templateId}/versions/diff"));
        assertResponseDataProperties(contractsByEndpoint.get(
                        "POST /api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback"),
                "approvalTemplateId", "sourceVersion", "newVersion", "currentVersion", "changeReason");
        assertApprovalTemplateProperties(contractsByEndpoint.get(
                "GET /api/v1/rules/approval-templates/{templateId}/versions/{version}"));
        assertApprovalTemplateProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/approval-templates/{templateId}/disable"), "impact");
        assertApprovalTemplateProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/approval-templates/{templateId}/enable"), "impact");
    }

    @Test
    void ruleRuntimeEndpointsDeclareFieldLevelResponseContracts() throws IOException {
        Map<String, Map<String, Object>> contractsByEndpoint = structuredContractsByEndpoint();

        assertRuleProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/review-submissions"), "reviewComment");
        assertRuleProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/approvals"), "approvalComment");
        assertRuleRunProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/runs"), "debugRunId");
        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/runs"),
                "runId", "ruleId", "versionId", "status", "runType", "triggeredByUserId",
                "durationMs", "errorMessage", "matched", "input", "output");
        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology"),
                "ruleId", "runId", "nodes", "edges");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology"),
                "nodes", "runId", "ruleId", "versionId", "role", "status", "runType", "durationMs", "errorMessage");
        assertArrayItemProperties(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology"),
                "edges", "operationLogId", "parentRuleId", "parentRunId", "nodeId",
                "subprocessRuleId", "subprocessRunId", "subprocessVersionId", "result", "errorMessage", "createdAt");

        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/approval-records"),
                approvalRecordProperties());
        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/approval-records"),
                approvalRecordProperties());
        assertApprovalRecordProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions"), "supplementStatus");
        assertApprovalRecordProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements"),
                "sourceRejectedApprovalRecordId", "newApprovalRecordId", "supplementStatus", "evidenceUrl");
        assertApprovalRecordProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders"));
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-records/batch-actions"),
                "action", "requestedCount", "succeededCount", "failedCount", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/rules/approval-records/batch-actions"),
                "items", "approvalRecordId", "result", "errorMessage");

        assertPagePropertiesWithItems(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/action-executions"),
                actionExecutionProperties());
        assertResponseDataProperties(contractsByEndpoint.get("GET /api/v1/rules/{ruleId}/metrics"),
                "ruleId", "totalRuns", "succeededRuns", "failedRuns", "successRate",
                "averageDurationMs", "lastStatus", "lastErrorMessage");
        assertRuleProperties(contractsByEndpoint.get("PUT /api/v1/rules/{ruleId}/schedule"));
        assertRuleProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/schedule/retry"));
        assertActionExecutionProperties(contractsByEndpoint.get(
                "POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry"));
        assertResponseDataProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/action-executions/batch"),
                "operation", "ruleId", "requestedCount", "succeededCount", "failedCount", "items");
        assertArrayItemProperties(contractsByEndpoint.get("POST /api/v1/rules/{ruleId}/action-executions/batch"),
                "items", "actionExecutionId", "result", "handledActionExecutionId", "status", "errorMessage");
    }

    private void assertRuleProperties(Map<String, Object> contract, String... additionalProperties) {
        List<String> propertyNames = new ArrayList<>(List.of("ruleId", "name", "description", "status",
                "definition", "versionId", "scheduleEnabled", "scheduleIntervalSeconds",
                "nextRunAt", "failureCount", "maxRetryCount", "scheduleInput"));
        propertyNames.addAll(List.of(additionalProperties));
        assertResponseDataProperties(contract, propertyNames.toArray(String[]::new));
    }

    private void assertRuleRunProperties(Map<String, Object> contract, String... additionalProperties) {
        List<String> propertyNames = new ArrayList<>(List.of("ruleId", "versionId", "status", "runType",
                "triggeredByUserId", "durationMs", "errorMessage", "output"));
        propertyNames.addAll(List.of(additionalProperties));
        assertResponseDataProperties(contract, propertyNames.toArray(String[]::new));
    }

    private void assertApprovalRecordProperties(Map<String, Object> contract, String... additionalProperties) {
        List<String> propertyNames = new ArrayList<>(List.of(approvalRecordProperties()));
        propertyNames.addAll(List.of(additionalProperties));
        assertResponseDataProperties(contract, propertyNames.toArray(String[]::new));
    }

    private String[] approvalRecordProperties() {
        return new String[]{"approvalRecordId", "ruleId", "runId", "nodeId", "assigneeRole",
                "delegateRole", "assigneeUsers", "delegateUsers", "delegateActiveFrom", "delegateActiveTo",
                "handledByDelegate", "approvalTitle", "status", "createdByUserId", "approvedByUserId",
                "approvalComment", "approvedAt", "createdAt", "slaHours", "remindCount", "lastRemindedAt",
                "slaDueAt", "isOverdue", "approvalGroupKey", "approvalMode", "assigneeRoles",
                "approvalGroupTotalCount", "approvalGroupApprovedCount", "approvalGroupPendingCount",
                "approvalGroupRejectedCount", "approvalGroupClosedCount"};
    }

    private void assertActionExecutionProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, actionExecutionProperties());
    }

    private String[] actionExecutionProperties() {
        return new String[]{"actionExecutionId", "sourceActionExecutionId", "ruleId", "runId", "nodeId",
                "actionType", "status", "attempt", "maxRetryCount", "endpoint", "idempotencyKey",
                "nextRetryAt", "errorMessage", "metadata"};
    }

    private void assertApprovalDelegateRuleProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "delegateRuleId", "assigneeRole", "delegateRole",
                "activeFrom", "activeTo", "activeWeekdays", "activeDates", "status",
                "reason", "createdByUserId", "createdAt");
    }

    private void assertApprovalTemplateProperties(Map<String, Object> contract, String... additionalProperties) {
        List<String> propertyNames = new ArrayList<>(List.of("approvalTemplateId", "name", "description",
                "status", "version", "steps", "usageCount", "usageRules",
                "createdByUserId", "createdAt", "updatedAt"));
        propertyNames.addAll(List.of(additionalProperties));
        assertResponseDataProperties(contract, propertyNames.toArray(String[]::new));
    }

    private void assertApprovalTemplateVersionDiffProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "approvalTemplateId", "baseVersion", "targetVersion",
                "summary", "changes");
        assertNestedObjectProperties(contract, "summary", "added", "removed", "modified", "unchanged");
        assertArrayItemProperties(contract, "changes", "changeType", "stepId", "baseStep", "targetStep");
    }

    private void assertShareLinkProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "shareLinkId", "reportId", "createdBy", "shareToken",
                "status", "allowDownload", "allowedDownloadFormats", "maxAccessCount",
                "allowedVisitors", "allowedVisitorDomains", "singleUse", "expiresAt");
    }

    private void assertOrganizationUnitProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "unitId", "code", "name", "parentId", "unitType",
                "sortOrder", "positions", "children");
    }

    private void assertOrganizationPositionProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "positionId", "organizationUnitId", "code", "name",
                "roles", "managerUserId", "users");
    }

    private void assertOrganizationPositionAssignmentProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "assignmentId", "userId", "positionId", "primary",
                "status", "activeFrom", "activeTo");
    }

    private void assertDataSourceSyncRunProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "syncRunId", "dataSourceId", "mode", "status", "processedRows",
                "failureReason", "message", "lastCursor", "startedAt", "finishedAt");
    }

    private void assertReportGenerationTaskDataProperties(Map<String, Object> contract, String... additionalProperties) {
        List<String> propertyNames = new ArrayList<>(List.of("taskId", "reportId", "status", "currentStage",
                "progress", "traceId", "outline", "failureReason", "createdAt"));
        propertyNames.addAll(List.of(additionalProperties));
        assertResponseDataProperties(contract, propertyNames.toArray(String[]::new));
    }

    private void assertEnterpriseTemplateDataProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "id", "templateId", "name", "version", "status",
                "brandSnapshot", "createdBy", "createdAt", "updatedAt");
    }

    private void assertEnterpriseTemplatePageProperties(Map<String, Object> contract) {
        assertResponseDataProperties(contract, "items", "page", "pageSize", "total");
        assertArrayItemProperties(contract, "items", "id", "templateId", "name", "version", "status",
                "brandSnapshot", "createdBy", "createdAt", "updatedAt");
    }

    private void assertPagePropertiesWithItems(Map<String, Object> contract, String... itemPropertyNames) {
        assertResponseDataProperties(contract, "items", "page", "pageSize", "total");
        assertArrayItemProperties(contract, "items", itemPropertyNames);
    }

    private void assertEnterpriseTemplateArrayDataProperties(Map<String, Object> contract) {
        assertResponseArrayDataItemProperties(contract, "id", "templateId", "name", "version", "status",
                "brandSnapshot", "createdBy", "createdAt", "updatedAt");
    }

    private void assertResponseArrayDataItemProperties(Map<String, Object> contract, String... propertyNames) {
        assertThat(contract).isNotNull();
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        assertThat(dataSchema).as(endpoint + " responseBody.data").isNotNull();
        assertThat(dataSchema.get("type")).as(endpoint + " responseBody.data.type").isEqualTo("array");
        assertThat(dataSchema.containsKey("items")).as(endpoint + " responseBody.data.items").isTrue();
        Map<?, ?> itemSchema = (Map<?, ?>) dataSchema.get("items");
        assertThat(itemSchema.containsKey("properties"))
                .as(endpoint + " responseBody.data.items.properties").isTrue();
        Map<?, ?> itemProperties = (Map<?, ?>) itemSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(itemProperties.containsKey(propertyName))
                    .as(endpoint + " responseBody.data.items." + propertyName)
                    .isTrue();
        }
    }

    private void assertResponseArrayDataNestedArrayItemProperties(Map<String, Object> contract, String arrayPropertyName,
                                                                  String... propertyNames) {
        assertThat(contract).isNotNull();
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        Map<?, ?> dataItemSchema = (Map<?, ?>) dataSchema.get("items");
        Map<?, ?> dataItemProperties = (Map<?, ?>) dataItemSchema.get("properties");
        Map<?, ?> arraySchema = (Map<?, ?>) dataItemProperties.get(arrayPropertyName);
        assertThat(arraySchema).as(endpoint + " responseBody.data.items." + arrayPropertyName).isNotNull();
        assertThat(arraySchema.containsKey("items"))
                .as(endpoint + " responseBody.data.items." + arrayPropertyName + ".items")
                .isTrue();
        Map<?, ?> nestedItemSchema = (Map<?, ?>) arraySchema.get("items");
        assertThat(nestedItemSchema.containsKey("properties"))
                .as(endpoint + " responseBody.data.items." + arrayPropertyName + ".items.properties")
                .isTrue();
        Map<?, ?> nestedItemProperties = (Map<?, ?>) nestedItemSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(nestedItemProperties.containsKey(propertyName))
                    .as(endpoint + " responseBody.data.items." + arrayPropertyName + ".items." + propertyName)
                    .isTrue();
        }
    }

    private void assertArrayItemProperties(Map<String, Object> contract, String arrayPropertyName, String... propertyNames) {
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        Map<?, ?> properties = (Map<?, ?>) dataSchema.get("properties");
        Map<?, ?> arraySchema = (Map<?, ?>) properties.get(arrayPropertyName);
        assertThat(arraySchema).as(endpoint + " responseBody.data." + arrayPropertyName).isNotNull();
        assertThat(arraySchema.containsKey("items")).as(endpoint + " responseBody.data." + arrayPropertyName + ".items").isTrue();
        Map<?, ?> itemSchema = (Map<?, ?>) arraySchema.get("items");
        assertThat(itemSchema.containsKey("properties"))
                .as(endpoint + " responseBody.data." + arrayPropertyName + ".items.properties").isTrue();
        Map<?, ?> itemProperties = (Map<?, ?>) itemSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(itemProperties.containsKey(propertyName))
                    .as(endpoint + " responseBody.data." + arrayPropertyName + ".items." + propertyName)
                    .isTrue();
        }
    }

    private void assertTopLevelArrayDataItemProperties(Map<String, Object> contract, String... propertyNames) {
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        assertThat(dataSchema.get("type")).as(endpoint + " responseBody.data.type").isEqualTo("array");
        assertThat(dataSchema.containsKey("items")).as(endpoint + " responseBody.data.items").isTrue();
        Map<?, ?> itemSchema = (Map<?, ?>) dataSchema.get("items");
        assertThat(itemSchema.containsKey("properties")).as(endpoint + " responseBody.data.items.properties").isTrue();
        Map<?, ?> itemProperties = (Map<?, ?>) itemSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(itemProperties.containsKey(propertyName))
                    .as(endpoint + " responseBody.data.items." + propertyName)
                    .isTrue();
        }
    }

    private void assertNestedObjectProperties(Map<String, Object> contract, String objectPropertyName, String... propertyNames) {
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        Map<?, ?> properties = (Map<?, ?>) dataSchema.get("properties");
        Map<?, ?> objectSchema = (Map<?, ?>) properties.get(objectPropertyName);
        assertThat(objectSchema).as(endpoint + " responseBody.data." + objectPropertyName).isNotNull();
        assertThat(objectSchema.containsKey("properties")).as(endpoint + " responseBody.data." + objectPropertyName + ".properties").isTrue();
        Map<?, ?> objectProperties = (Map<?, ?>) objectSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(objectProperties.containsKey(propertyName))
                    .as(endpoint + " responseBody.data." + objectPropertyName + "." + propertyName)
                    .isTrue();
        }
    }

    private void assertResponseDataProperties(Map<String, Object> contract, String... propertyNames) {
        assertThat(contract).isNotNull();
        String endpoint = contract.get("method") + " " + contract.get("path");
        Map<?, ?> responseBody = (Map<?, ?>) contract.get("responseBody");
        assertThat(responseBody.containsKey("data")).as(endpoint + " responseBody.data").isTrue();
        Map<?, ?> dataSchema = (Map<?, ?>) responseBody.get("data");
        assertThat(dataSchema.containsKey("properties")).as(endpoint + " responseBody.data.properties").isTrue();
        Map<?, ?> properties = (Map<?, ?>) dataSchema.get("properties");
        for (String propertyName : propertyNames) {
            assertThat(properties.containsKey(propertyName)).as(endpoint + " responseBody.data." + propertyName).isTrue();
        }
    }

    private void assertStructuredContractShape(Map<String, Object> contract) {
        String endpoint = contract.get("method") + " " + contract.get("path");
        assertThat(contract)
                .containsKeys("method", "path", "contentType", "pathParams", "queryParams", "headers",
                        "requestBody", "responseBody", "statusCodes");
        assertThat(contract.get("method")).as(endpoint + " method").isInstanceOf(String.class);
        assertThat(contract.get("path")).as(endpoint + " path").isInstanceOf(String.class);
        assertThat(contract.get("requestBody")).as(endpoint + " requestBody").isInstanceOf(Map.class);
        assertThat(contract.get("responseBody")).as(endpoint + " responseBody").isInstanceOf(Map.class);
        assertThat(contract.get("statusCodes")).as(endpoint + " statusCodes").isInstanceOf(List.class);
    }

    private List<Map<String, Object>> readJsonContractBlock(String apiContract, String marker) throws IOException {
        return OBJECT_MAPPER.readValue(jsonBlockAfter(apiContract, marker), new TypeReference<>() {
        });
    }

    private Map<String, Map<String, Object>> backendSupplementContractsByEndpoint() throws IOException {
        return readJsonContractBlock(Files.readString(API_CONTRACT),
                "<!-- API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_START -->").stream()
                .collect(Collectors.toMap(contract -> contract.get("method") + " " + contract.get("path"),
                        contract -> contract));
    }

    private Map<String, Map<String, Object>> structuredContractsByEndpoint() throws IOException {
        String apiContract = Files.readString(API_CONTRACT);
        List<Map<String, Object>> structuredContracts = new ArrayList<>();
        structuredContracts.addAll(readJsonContractBlock(apiContract, "## 6. API JSON"));
        structuredContracts.addAll(readJsonContractBlock(apiContract,
                "<!-- API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_START -->"));
        return structuredContracts.stream()
                .collect(Collectors.toMap(contract -> contract.get("method") + " " + contract.get("path"),
                        contract -> contract, (mainContract, supplementContract) -> supplementContract));
    }

    private String jsonBlockAfter(String apiContract, String marker) {
        int markerIndex = apiContract.indexOf(marker);
        if (markerIndex < 0 && marker.contains("13.2")) {
            markerIndex = apiContract.indexOf("<!-- API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_START -->");
        }
        assertThat(markerIndex).as(marker + " marker").isGreaterThanOrEqualTo(0);
        int fenceStart = apiContract.indexOf("```json", markerIndex);
        assertThat(fenceStart).as(marker + " opening fence").isGreaterThanOrEqualTo(0);
        int jsonStart = apiContract.indexOf("\n", fenceStart) + 1;
        int fenceEnd = apiContract.indexOf("```", jsonStart);
        assertThat(fenceEnd).as(marker + " closing fence").isGreaterThanOrEqualTo(0);
        return apiContract.substring(jsonStart, fenceEnd);
    }

    @Test
    void exposesExternalAuthProfileEndpoint() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"));

        assertThat(permissionController).contains("@GetMapping(\"/auth/me\")");
        assertThat(permissionController).contains("CurrentUserHolder.get()");
        assertThat(permissionController).contains("\"displayName\", \"当前用户\"");
        assertThat(permissionController).doesNotContain("褰撳墠鐢ㄦ埛");
    }

    private String controllerBasePath(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .map(REQUEST_MAPPING::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse("");
    }

    private String mappingPath(String mappingArgs) {
        if (mappingArgs == null || mappingArgs.isBlank()) {
            return "";
        }
        Matcher valueMatcher = VALUE_PATH.matcher(mappingArgs);
        if (valueMatcher.find()) {
            return valueMatcher.group(1);
        }
        Matcher quotedMatcher = QUOTED_PATH.matcher(mappingArgs);
        return quotedMatcher.find() ? quotedMatcher.group(1) : "";
    }

    private String normalizePath(String basePath, String mappingPath) {
        String combined = (basePath == null ? "" : basePath) + "/" + (mappingPath == null ? "" : mappingPath);
        return combined.replaceAll("/+", "/").replaceAll("/$", "");
    }

    @Test
    void javaOwnsDocumentUploadOrchestration() throws IOException {
        String knowledgeController = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/interfaces/rest/KnowledgeController.java"));
        String knowledgeService = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/application/KnowledgeApplicationService.java"));

        assertThat(knowledgeController).contains("@PostMapping(value = \"/documents/upload\"");
        assertThat(knowledgeController).contains("MultipartFile file");
        assertThat(knowledgeService).contains("document.parse.requested");
    }

    @Test
    void reportStreamUsesUnifiedSseEventSchema() throws IOException {
        String sseEvent = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/api/SseEvent.java"));
        String reportService = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/application/ReportApplicationService.java"));

        assertThat(sseEvent).contains("record SseEvent");
        assertThat(sseEvent).contains("taskId");
        assertThat(sseEvent).contains("errorCode");
        assertThat(reportService).contains("SseEvent.stage");
        assertThat(reportService).contains("SseEvent.done");
    }

    @Test
    void reportGenerationExposesControlledWorkerCompletionCallback() throws IOException {
        String reportController = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/interfaces/rest/ReportController.java"));
        String reportService = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/application/ReportApplicationService.java"));

        assertThat(reportController).contains("@PostMapping(\"/reports/generation-tasks/{taskId}/completion\")");
        assertThat(reportController).contains("completeGenerationTaskFromWorker(taskId, request)");
        assertThat(reportService).contains("completeGenerationTaskFromWorker");
        assertThat(reportService).contains("SseEvent.references");
        assertThat(reportService).contains("\"model_invocation\"");
    }

    @Test
    void reportApplicationServiceSpringConstructorInjectsAuditRepository() throws IOException {
        String reportService = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/application/ReportApplicationService.java"));

        assertThat(reportService).contains("AuditRepository auditRepository");
        assertThat(reportService).doesNotContain("new NoopAuditRepository()");
    }

    @Test
    void rocketMqPropertiesExposeReportGenerationTopic() throws IOException {
        String properties = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/event/RocketMqProperties.java"));
        String publisher = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/event/RocketMqDomainEventPublisher.java"));

        assertThat(properties).contains("reportGenerationTopic");
        assertThat(publisher).contains("\"report.generation.outline_confirmed\"");
        assertThat(publisher).contains("properties.reportGenerationTopic()");
    }

    @Test
    void apiResponseUsesReadableChineseSuccessMessage() throws IOException {
        String apiResponse = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/api/ApiResponse.java"));

        assertThat(apiResponse).contains("\"操作成功\"");
        assertThat(apiResponse).doesNotContain("鎿嶄綔鎴愬姛");
    }
    @Test
    void ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard() throws IOException {
        String ruleController = Files.readString(SOURCE_ROOT.resolve("com/company/report/rule/interfaces/rest/RuleController.java"))
                .replace("\r\n", "\n");

        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @PostMapping(\"/{ruleId}/approval-records/{approvalRecordId}/actions\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @PostMapping(\"/{ruleId}/approval-records/{approvalRecordId}/supplements\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @PostMapping(value = \"/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)");
        assertThat(ruleController).contains("uploadApprovalSupplementAttachment(ruleId, approvalRecordId, file)");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @PostMapping(\"/{ruleId}/approval-records/{approvalRecordId}/reminders\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @PostMapping(\"/approval-records/batch-actions\")");
        assertThat(ruleController).contains("submitApprovalSupplement(ruleId, approvalRecordId, request)");
        assertThat(ruleController).contains("\"supplement_required\"");
        assertThat(ruleController).contains("\"resubmitted\"");
    }

    @Test
    void ruleSubprocessRunTopologyUsesDebugPermissionEndpoint() throws IOException {
        String ruleController = Files.readString(SOURCE_ROOT.resolve("com/company/report/rule/interfaces/rest/RuleController.java"))
                .replace("\r\n", "\n");

        assertThat(ruleController).contains("@RequiresPermission(\"rule:debug\")\n    @GetMapping(\"/{ruleId}/runs/{runId}/subprocess-topology\")");
        assertThat(ruleController).contains("subprocessRunTopology(ruleId, runId)");
    }

    @Test
    void ruleApprovalDelegateRuleCreationUsesManagePermission() throws IOException {
        String ruleController = Files.readString(SOURCE_ROOT.resolve("com/company/report/rule/interfaces/rest/RuleController.java"))
                .replace("\r\n", "\n");

        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-delegate-rules\")");
        assertThat(ruleController).contains("createApprovalDelegateRule(request)");
    }

    @Test
    void ruleApprovalDelegateRuleOperationsUseManagePermission() throws IOException {
        String ruleController = Files.readString(SOURCE_ROOT.resolve("com/company/report/rule/interfaces/rest/RuleController.java"))
                .replace("\r\n", "\n");

        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-delegate-rules\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-delegate-rules/batch-import\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PutMapping(\"/approval-delegate-rules/{delegateRuleId}\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-delegate-rules/{delegateRuleId}/disable\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-delegate-rules/{delegateRuleId}/enable\")");
        assertThat(ruleController).contains("listApprovalDelegateRules(page, pageSize, filters)");
        assertThat(ruleController).contains("batchImportApprovalDelegateRules(request)");
        assertThat(ruleController).contains("updateApprovalDelegateRule(delegateRuleId, request)");
        assertThat(ruleController).contains("disableApprovalDelegateRule(delegateRuleId, request)");
        assertThat(ruleController).contains("enableApprovalDelegateRule(delegateRuleId, request)");
    }

    @Test
    void ruleApprovalTemplatesUseManagePermissionEndpoints() throws IOException {
        String ruleController = Files.readString(SOURCE_ROOT.resolve("com/company/report/rule/interfaces/rest/RuleController.java"))
                .replace("\r\n", "\n");
        String versionMigration = Files.readString(Path.of("src/main/resources/db/migration/V050__rule_approval_template_version.sql"));
        String versionHistoryMigration = Files.readString(Path.of("src/main/resources/db/migration/V051__rule_approval_template_versions.sql"));

        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-templates\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-templates\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-templates/{templateId}/usage\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PutMapping(\"/approval-templates/{templateId}\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-templates/{templateId}/versions\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-templates/{templateId}/versions/diff\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-templates/{templateId}/versions/{version}/rollback\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @GetMapping(\"/approval-templates/{templateId}/versions/{version}\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-templates/{templateId}/disable\")");
        assertThat(ruleController).contains("@RequiresPermission(\"rule:manage\")\n    @PostMapping(\"/approval-templates/{templateId}/enable\")");
        assertThat(ruleController).contains("createApprovalTemplate(request)");
        assertThat(ruleController).contains("listApprovalTemplates(page, pageSize, filters)");
        assertThat(ruleController).contains("listApprovalTemplateUsage(templateId, page, pageSize)");
        assertThat(ruleController).contains("updateApprovalTemplate(templateId, request)");
        assertThat(ruleController).contains("listApprovalTemplateVersions(templateId)");
        assertThat(ruleController).contains("compareApprovalTemplateVersions(templateId, baseVersion, targetVersion)");
        assertThat(ruleController).contains("rollbackApprovalTemplateVersion(templateId, version)");
        assertThat(ruleController).contains("getApprovalTemplateVersion(templateId, version)");
        assertThat(ruleController).contains("disableApprovalTemplate(templateId)");
        assertThat(ruleController).contains("enableApprovalTemplate(templateId)");
        assertThat(versionMigration).contains("ALTER TABLE rule_approval_templates");
        assertThat(versionMigration).contains("ADD COLUMN IF NOT EXISTS version");
        assertThat(versionHistoryMigration).contains("CREATE TABLE IF NOT EXISTS rule_approval_template_versions");
        assertThat(versionHistoryMigration).contains("UNIQUE(template_id, version)");
    }

    @Test
    void enterpriseExportTemplatesExposeGovernedReportExportEndpoints() throws IOException {
        String reportController = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/interfaces/rest/ReportController.java"))
                .replace("\r\n", "\n");
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V053__enterprise_export_templates.sql"));

        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @PostMapping(\"/enterprise-export-templates\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @GetMapping(\"/enterprise-export-templates\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @PutMapping(\"/enterprise-export-templates/{templateId}\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @GetMapping(\"/enterprise-export-templates/{templateId}/versions\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @PostMapping(\"/enterprise-export-templates/{templateId}/disable\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:template:manage\")\n    @PostMapping(\"/enterprise-export-templates/{templateId}/enable\")");
        assertThat(reportController).contains("createEnterpriseExportTemplate(request)");
        assertThat(reportController).contains("listEnterpriseExportTemplates(page, pageSize, filters)");
        assertThat(reportController).contains("updateEnterpriseExportTemplate(templateId, request)");
        assertThat(reportController).contains("listEnterpriseExportTemplateVersions(templateId)");
        assertThat(reportController).contains("disableEnterpriseExportTemplate(templateId)");
        assertThat(reportController).contains("enableEnterpriseExportTemplate(templateId)");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS enterprise_export_templates");
        assertThat(migration).contains("brand_snapshot JSONB");
        assertThat(migration).contains("UNIQUE(template_id, version)");
    }

    @Test
    void reportVersionEndpointsRequireReadOrCreatePermissions() throws IOException {
        String reportController = Files.readString(SOURCE_ROOT.resolve("com/company/report/report/interfaces/rest/ReportController.java"))
                .replace("\r\n", "\n");

        assertThat(reportController).contains("@RequiresPermission(\"report:read\")\n    @GetMapping(\"/reports/{reportId}/versions\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:read\")\n    @GetMapping(\"/reports/{reportId}/versions/diff\")");
        assertThat(reportController).contains("@RequiresPermission(\"report:create\")\n    @PostMapping(\"/reports/{reportId}/versions/{versionId}/rollback\")");
    }

    @Test
    void collaborationEndpointsRequireWritePermission() throws IOException {
        String collaborationController = Files.readString(SOURCE_ROOT.resolve("com/company/report/citation/interfaces/rest/CollaborationController.java"))
                .replace("\r\n", "\n");

        assertThat(collaborationController).contains("@RequiresPermission(\"collaboration:write\")\n    @PostMapping(\"/reports/{reportId}/annotations\")");
        assertThat(collaborationController).contains("@RequiresPermission(\"collaboration:write\")\n    @PutMapping(\"/tasks/{taskId}/status\")");
        assertThat(collaborationController).contains("addAnnotation(reportId, request)");
        assertThat(collaborationController).contains("updateTaskStatus(taskId, request)");
    }

    @Test
    void knowledgeDeleteEndpointRequiresManagePermission() throws IOException {
        String knowledgeController = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/interfaces/rest/KnowledgeController.java"))
                .replace("\r\n", "\n");

        assertThat(knowledgeController).contains("@RequiresPermission(\"knowledge:manage\")\n    @DeleteMapping(\"/knowledge-items/{itemId}\")");
        assertThat(knowledgeController).contains("deleteItem(itemId, confirmed)");
    }

    @Test
    void dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission() throws IOException {
        String knowledgeController = Files.readString(SOURCE_ROOT.resolve("com/company/report/knowledge/interfaces/rest/KnowledgeController.java"))
                .replace("\r\n", "\n");

        assertThat(knowledgeController).contains("@RequiresPermission(\"datasource:manage\")\n    @PostMapping(\"/data-sources/credentials/reencrypt\")");
        assertThat(knowledgeController).contains("reencryptStaleDataSourceCredentials");
        assertThat(knowledgeController).contains("@RequiresPermission(\"datasource:manage\")\n    @GetMapping(\"/data-sources/profile-drift\")");
        assertThat(knowledgeController).contains("auditDataSourceProfileDrift");
        assertThat(knowledgeController).contains("@RequiresPermission(\"datasource:manage\")\n    @PostMapping(\"/data-sources/profile-drift/repair\")");
        assertThat(knowledgeController).contains("repairDataSourceProfileDriftBatch(request)");
        assertThat(knowledgeController).contains("@RequiresPermission(\"datasource:manage\")\n    @PostMapping(\"/data-sources/{dataSourceId}/profile-drift/repair\")");
        assertThat(knowledgeController).contains("repairDataSourceProfileDrift(dataSourceId, request)");
    }

    @Test
    void shareManagementExposesOwnerCreateAndRevokeEndpoints() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"))
                .replace("\r\n", "\n");
        String jwtFilter = Files.readString(SOURCE_ROOT.resolve("com/company/report/shared/security/JwtAuthenticationFilter.java"))
                .replace("\r\n", "\n");

        assertThat(permissionController).contains("@RequiresPermission(\"report:share\")\n    @PostMapping(\"/reports/{reportId}/share-links\")");
        assertThat(permissionController).contains("@RequiresPermission(\"report:share\")\n    @PostMapping(\"/share-links/{shareToken}/revoke\")");
        assertThat(permissionController).contains("revokeShare(shareToken)");
        assertThat(jwtFilter).contains("path.matches(\"^/api/v1/share-links/[^/]+/(access|report)$\")");
        assertThat(jwtFilter).contains("path.matches(\"^/api/v1/share-links/[^/]+/exports/[^/]+/download-url$\")");
        assertThat(jwtFilter).doesNotContain("path.startsWith(\"/api/v1/share-links/\") || path.startsWith(\"/actuator/health\")");
    }

    @Test
    void externalShareAccessEnrichesRiskContextFromRequestHeaders() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"))
                .replace("\r\n", "\n");

        assertThat(permissionController).contains("HttpServletRequest httpRequest");
        assertThat(permissionController).contains("withShareRiskContext(request, httpRequest)");
        assertThat(permissionController).contains("\"X-Forwarded-For\"");
        assertThat(permissionController).contains("\"X-Real-IP\"");
        assertThat(permissionController).contains("\"User-Agent\"");
        assertThat(permissionController).contains("enriched.put(\"clientIp\"");
        assertThat(permissionController).contains("enriched.put(\"userAgent\"");
    }

    @Test
    void organizationDirectoryExposesReadOnlyPermissionEndpoint() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"))
                .replace("\r\n", "\n");

        assertThat(permissionController).contains("@RequiresPermission(\"permission:read\")\n    @GetMapping(\"/organization-directory\")");
        assertThat(permissionController).contains("organizationDirectory()");
        assertThat(permissionController).contains("service.organizationDirectory()");
    }

    @Test
    void organizationMaintenanceUsesUserManagePermissionEndpoints() throws IOException {
        String permissionController = Files.readString(SOURCE_ROOT.resolve("com/company/report/permission/interfaces/rest/PermissionController.java"))
                .replace("\r\n", "\n");

        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PostMapping(\"/organization-units\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PutMapping(\"/organization-units/{unitId}\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PostMapping(\"/organization-positions\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PostMapping(\"/organization-position-assignments\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PostMapping(\"/organization-position-assignments/batch-import\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PutMapping(\"/organization-position-assignments/{assignmentId}\")");
        assertThat(permissionController).contains("@RequiresPermission(\"user:manage\")\n    @PostMapping(\"/organization-position-assignments/{assignmentId}/disable\")");
        assertThat(permissionController).contains("createOrganizationUnit(@RequestBody Map<String, Object> request)");
        assertThat(permissionController).contains("updateOrganizationUnit(@PathVariable Long unitId");
        assertThat(permissionController).contains("createOrganizationPosition(@RequestBody Map<String, Object> request)");
        assertThat(permissionController).contains("assignUserToOrganizationPosition(@RequestBody Map<String, Object> request)");
        assertThat(permissionController).contains("batchImportOrganizationPositionAssignments(@RequestBody Map<String, Object> request)");
        assertThat(permissionController).contains("updateOrganizationPositionAssignment(@PathVariable Long assignmentId");
        assertThat(permissionController).contains("disableOrganizationPositionAssignment(@PathVariable Long assignmentId");
        assertThat(permissionController).contains("@RequestBody Map<String, Object> request)");
        assertThat(permissionController).contains("service.createOrganizationUnit(request)");
        assertThat(permissionController).contains("service.updateOrganizationUnit(unitId, request)");
        assertThat(permissionController).contains("service.createOrganizationPosition(request)");
        assertThat(permissionController).contains("service.assignUserToOrganizationPosition(request)");
        assertThat(permissionController).contains("service.batchImportOrganizationPositionAssignments(request)");
        assertThat(permissionController).contains("service.updateOrganizationPositionAssignment(assignmentId, request)");
        assertThat(permissionController).contains("service.disableOrganizationPositionAssignment(assignmentId, request)");
    }
}
