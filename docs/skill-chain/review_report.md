# 代码审查报告

日期：2026-06-22

## 概要

- 审查范围：`backend/`、`frontend/web-console/src/`、`tests/`、`openspec/specs/`
- 严重问题：3 项，已在 S20 修复记录中闭环
- 一般问题：6 项，部分已修复，部分保留为后续迭代建议
- 当前状态：上传链路已补强并通过本地 Docker 写入烟测

## 已闭环的严重问题

| 问题 | 当前状态 | 证据 |
| --- | --- | --- |
| Java 与 Python 对 `/api/v1/documents/upload` 的职责边界不完整 | 已修复。外部上传由 Java 业务核心承接，Python AI 服务作为内部解析执行面 | `KnowledgeController.uploadDocument`、`KnowledgeApplicationService.uploadDocument`、`docs/skill-chain/validation_report.md` |
| 登录入口缺少契约归属 | 已修复。登录归 Higress/OIDC 外部入口，Java 暴露 `/api/v1/auth/me` 当前用户画像 | `PermissionController.currentUser`、`docs/skill-chain/api_contract.md` |
| SSE 事件格式不统一 | 已修复。统一为 `stage`、`delta`、`references`、`error`、`done` JSON schema | `SseEvent.java`、`useSseStream.ts`、`docs/skill-chain/api_contract.md` |

## 一般问题与建议

| 问题 | 建议 |
| --- | --- |
| 部分 Controller 仍返回 `Map<String,Object>` | 后续为报告创建、导出、分享、数据源等核心命令补 DTO 与 Bean Validation |
| Python JWT 算法策略仍偏基础 | 保留 HS256 默认，同时预留 `JWT_ALGORITHM`、`OIDC_JWKS_URL` 配置 |
| RAG 服务仍是演示检索 | 后续补 Milvus/OpenSearch adapter，并把引用可信度评分纳入检索结果 |
| 前端 API client 覆盖不足 | 补齐 `shareApi`、`ruleApi`、`auditApi`、`collaborationApi` |
| 导出接口缺少下载状态查询 | 补 `GET /api/v1/reports/{reportId}/exports/{exportFileId}` 或统一文件下载 URL |
| 统一 `tests/` 目录尚未完全接入各模块原生发现 | CI 保留显式扫描，后续逐步迁入模块原生 test 目录 |

## RocketMQ 命名约束

RocketMQ 4.9.x topic 仅允许 `%`、`|`、字母、数字、下划线和中划线。业务事件类型可以继续使用点分命名，例如 `document.parse.requested`，但物理 topic 必须使用合法名称。

当前约定：

| 业务事件类型/tag | RocketMQ 物理 topic |
| --- | --- |
| `document.parse.requested` | `document_parse_requested` |
| `report.generation.requested` | `report_generation_requested` |
| `report.export.requested` | `report_export_requested` |
| `ai.generation.completed` | `ai_generation_completed` |

## OpenSpec 偏离检查

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 多租户 | 通过 | 未发现租户维度代码或配置 |
| 私有化部署 | 通过 | 未生成私有化部署能力 |
| 高可用 / 灾备 | 通过 | 当前仅单实例/本地编排，不声称 HA/DR |
| 人工审核发布 | 通过 | 未生成报告人工审核发布流程 |
| 网关选型 | 通过 | 文档与部署继续使用 Higress，不引入自研轻量网关 |
| 前端技术栈 | 通过 | 使用 Vue 3 + TypeScript |

## S20 复核说明

三项严重问题已在 `docs/skill-chain/s20_failure_fix_report.md` 与 `docs/skill-chain/validation_report.md` 中完成修复记录。上传链路本地 Docker 写入烟测已通过，证据见 `docs/skill-chain/upload_infrastructure_status.md`。
