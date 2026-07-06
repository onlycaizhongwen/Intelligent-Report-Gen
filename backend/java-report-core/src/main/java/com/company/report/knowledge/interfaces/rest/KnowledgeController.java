package com.company.report.knowledge.interfaces.rest;

import com.company.report.knowledge.application.KnowledgeApplicationService;
import com.company.report.shared.api.ApiResponse;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {
    private final KnowledgeApplicationService service;

    public KnowledgeController(KnowledgeApplicationService service) {
        this.service = service;
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 搜索筛选知识条目 */
    @RequiresPermission("knowledge:manage")
    @GetMapping("/knowledge-bases")
    public ApiResponse<PageResponse<Map<String, Object>>> listKnowledgeBases(@RequestParam(defaultValue = "1") int page,
                                                                             @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listKnowledgeBases(page, pageSize));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 新建知识库 */
    @RequiresPermission("knowledge:manage")
    @PostMapping("/knowledge-bases")
    public ApiResponse<Map<String, Object>> createKnowledgeBase(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createKnowledgeBase(request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 搜索筛选知识条目 */
    @RequiresPermission("knowledge:manage")
    @GetMapping("/knowledge-items")
    public ApiResponse<PageResponse<Map<String, Object>>> searchItems(@RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.searchItems(page, pageSize, keyword));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 手动录入成功 */
    @RequiresPermission("knowledge:manage")
    @PostMapping("/knowledge-items")
    public ApiResponse<Map<String, Object>> createItem(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.createItem(request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / batch import knowledge items. */
    @RequiresPermission("knowledge:manage")
    @PostMapping("/knowledge-items/batch-import")
    public ApiResponse<Map<String, Object>> batchImportItems(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.batchImportItems(request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-002 / Java upload orchestration, MinIO metadata, audit, RocketMQ parse request. */
    @RequiresPermission("knowledge:upload")
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestPart("file") MultipartFile file,
                                                           @RequestParam(defaultValue = "1") Long knowledgeBaseId) {
        return ApiResponse.success(service.uploadDocument(file, knowledgeBaseId));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-002 / Query async document parse status. */
    @RequiresPermission("knowledge:upload")
    @GetMapping("/documents/{documentId}")
    public ApiResponse<Map<String, Object>> getDocumentStatus(@PathVariable Long documentId) {
        return ApiResponse.success(service.getDocumentStatus(documentId));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 / 删除被引用知识条目 */
    @RequiresPermission("knowledge:manage")
    @DeleteMapping("/knowledge-items/{itemId}")
    public ApiResponse<Map<String, Object>> deleteItem(@PathVariable Long itemId, @RequestParam(defaultValue = "false") boolean confirmed) {
        return ApiResponse.success(service.deleteItem(itemId, confirmed));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / 测试连接失败 */
    @RequiresPermission("datasource:manage")
    @PostMapping("/data-sources/test-connection")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.testConnection(request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / 数据源同步异常 */
    @RequiresPermission("datasource:manage")
    @PostMapping("/data-sources")
    public ApiResponse<Map<String, Object>> saveDataSource(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(service.saveDataSource(request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / ERP, OA and finance data-source template presets. */
    @RequiresPermission("datasource:manage")
    @GetMapping("/data-sources/presets")
    public ApiResponse<java.util.List<Map<String, Object>>> listDataSourcePresets() {
        return ApiResponse.success(service.listDataSourcePresets());
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / Re-encrypt stale data-source credentials with the active key. */
    @RequiresPermission("datasource:manage")
    @PostMapping("/data-sources/credentials/reencrypt")
    public ApiResponse<Map<String, Object>> reencryptDataSourceCredentials(@RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(service.reencryptStaleDataSourceCredentials(requestLimit(request)));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / 启动数据源同步 */
    @RequiresPermission("datasource:manage")
    @PostMapping("/data-sources/{dataSourceId}/sync-runs")
    public ApiResponse<Map<String, Object>> startDataSourceSync(@PathVariable Long dataSourceId,
                                                                @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(service.startDataSourceSync(dataSourceId, request));
    }

    /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 / 查看同步日志 */
    @RequiresPermission("datasource:manage")
    @GetMapping("/data-sources/{dataSourceId}/sync-runs")
    public ApiResponse<PageResponse<Map<String, Object>>> listDataSourceSyncRuns(@PathVariable Long dataSourceId,
                                                                                 @RequestParam(defaultValue = "1") int page,
                                                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.listDataSourceSyncRuns(dataSourceId, page, pageSize));
    }

    private int requestLimit(Map<String, Object> request) {
        Object value = request == null ? null : request.get("limit");
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 1);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 100;
        }
        return Math.max(Integer.parseInt(String.valueOf(value)), 1);
    }
}
