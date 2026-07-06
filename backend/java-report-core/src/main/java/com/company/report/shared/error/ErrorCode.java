package com.company.report.shared.error;

public enum ErrorCode {
    PARAMETER_INVALID(400, "请求参数错误", "通用参数校验"),
    UNAUTHORIZED(401, "请登录后继续操作", "REQ-AUTH-001"),
    FORBIDDEN(403, "当前账号无权执行该操作", "REQ-AUTH-001"),
    RESOURCE_NOT_FOUND(404, "资源不存在", "通用资源查询"),
    RESOURCE_STATE_CONFLICT(409, "资源状态冲突", "REQ-KB-001"),
    BUSINESS_RULE_INVALID(422, "业务规则校验失败", "OpenSpec 业务规则"),
    SYSTEM_BUSY(500, "系统繁忙，请稍后重试", "通用服务端错误"),
    AI_SERVICE_UNAVAILABLE(503, "AI 服务繁忙，请稍后重试", "REQ-AI-001"),
    OUTLINE_NOT_CONFIRMED(1001, "大纲未确认，不能进入正文生成", "REQ-REPORT-002"),
    EXPORT_TEMPLATE_MISSING(1002, "企业导出模板缺失或配置异常", "REQ-REPORT-004"),
    KNOWLEDGE_ITEM_REFERENCED(1003, "知识条目已被报告引用，请确认影响后再删除", "REQ-KB-001"),
    RULE_VALIDATION_FAILED(1004, "规则校验失败，请修复节点参数或连线", "REQ-RULE-001"),
    SHARE_LINK_EXPIRED(1005, "链接已过期或访问凭证错误", "REQ-COLLAB-001"),
    AI_MODEL_TIMEOUT(2001, "AI 服务繁忙，请稍后重试", "REQ-AI-001"),
    TOKEN_LIMIT_EXCEEDED(2002, "问题太长，请精简后重试", "REQ-AI-001"),
    PROMPT_BLOCKED(2003, "输入包含不安全内容，请调整后重试", "REQ-AI-001"),
    RETRIEVAL_EMPTY(2004, "未找到相关信息，请补充知识库或调整报告范围", "REQ-REPORT-003"),
    DOCUMENT_PARSE_FAILED(2005, "文档解析失败，请重新上传或修正文件", "REQ-KB-002");

    private final int code;
    private final String message;
    private final String traceability;

    ErrorCode(int code, String message, String traceability) {
        this.code = code;
        this.message = message;
        this.traceability = traceability;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String traceability() {
        return traceability;
    }
}
