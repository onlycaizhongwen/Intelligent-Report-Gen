-- Skill: S11 seed_data.gen
-- Project: 智能报告生成系统
-- Database: PostgreSQL 16
-- OpenSpec baseline: S7 confirmed, S8-S30 must not deviate
-- Purpose: local/dev/demo seed data only. Do not use real personal, credential, enterprise, or model-provider data.
-- Password values are BCrypt-like demo hashes, not plaintext passwords.

BEGIN;

-- =========================================================
-- 1. RBAC users, roles, permissions
-- Covers: permission-collaboration / REQ-AUTH-001
-- =========================================================

INSERT INTO users (id, username, display_name, email, phone, department, password_hash, status, created_by)
VALUES
  (1, 'admin.demo', '系统管理员', 'admin.demo@example.invalid', '13800000000', '平台部', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9DemoHashOnly000000000000000', 'enabled', NULL),
  (2, 'senior.analyst.demo', '高级分析师', 'senior.analyst@example.invalid', '13800000001', '经营分析部', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9DemoHashOnly000000000000001', 'enabled', 1),
  (3, 'analyst.demo', '分析师', 'analyst@example.invalid', '13800000002', '经营分析部', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9DemoHashOnly000000000000002', 'enabled', 1),
  (4, 'viewer.demo', '查看者', 'viewer@example.invalid', '13800000003', '业务部门', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9DemoHashOnly000000000000003', 'enabled', 1),
  (5, 'disabled.demo', '禁用用户', 'disabled@example.invalid', '13800000004', '业务部门', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9DemoHashOnly000000000000004', 'disabled', 1)
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, role_code, role_name, description, built_in, status)
VALUES
  (1, 'system_admin', '系统管理员', '用户、角色、权限、审计和全局配置管理', TRUE, 'enabled'),
  (2, 'senior_analyst', '高级分析师', '报告、知识库、规则、导出和分享管理', TRUE, 'enabled'),
  (3, 'analyst', '分析师', '报告创建、查看授权知识库和处理协作任务', TRUE, 'enabled'),
  (4, 'viewer', '查看者', '查看授权报告、来源和个人历史', TRUE, 'enabled')
ON CONFLICT DO NOTHING;

INSERT INTO permissions (id, permission_code, permission_name, resource_type, action_code, description)
VALUES
  (1, 'report:create', '创建报告', 'report', 'create', '创建自然语言或模板报告生成任务'),
  (2, 'report:read', '查看报告', 'report', 'read', '查看授权报告详情'),
  (3, 'report:export', '导出报告', 'report', 'export', '创建报告导出任务并下载结果'),
  (4, 'report:share', '分享报告', 'report', 'share', '生成内部或外部分享链接'),
  (5, 'knowledge:manage', '管理知识库', 'knowledge', 'manage', '新建知识库、维护知识条目和上传文件'),
  (6, 'datasource:manage', '管理数据源', 'datasource', 'manage', '配置企业内部数据源并测试同步'),
  (7, 'rule:manage', '管理规则', 'rule', 'manage', '创建、保存、导入和运行规则'),
  (8, 'rule:debug', '调试规则', 'rule', 'debug', '单步或完整执行规则调试'),
  (9, 'user:manage', '管理用户', 'user', 'manage', '添加、编辑、启用或禁用用户'),
  (10, 'permission:read', '查看权限矩阵', 'permission', 'read', '查看角色与权限项矩阵'),
  (11, 'audit:read', '查看审计', 'audit', 'read', '查看操作审计和模型调用审计'),
  (12, 'dashboard:read', '查看工作台', 'dashboard', 'read', '查看指标总览、趋势、排行和活动'),
  (13, 'collaboration:comment', '提交批注', 'collaboration', 'comment', '添加批注、评论和任务')
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (id, user_id, role_id, assigned_by) VALUES
  (1, 1, 1, 1), (2, 2, 2, 1), (3, 3, 3, 1), (4, 4, 4, 1), (5, 5, 4, 1)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_id, granted_by)
VALUES
  (1, 1, 1, 1), (2, 1, 2, 1), (3, 1, 3, 1), (4, 1, 4, 1), (5, 1, 5, 1), (6, 1, 6, 1), (7, 1, 7, 1), (8, 1, 8, 1), (9, 1, 9, 1), (10, 1, 10, 1), (11, 1, 11, 1), (12, 1, 12, 1), (13, 1, 13, 1),
  (14, 2, 1, 1), (15, 2, 2, 1), (16, 2, 3, 1), (17, 2, 4, 1), (18, 2, 5, 1), (19, 2, 6, 1), (20, 2, 7, 1), (21, 2, 8, 1), (22, 2, 12, 1), (23, 2, 13, 1),
  (24, 3, 1, 1), (25, 3, 2, 1), (26, 3, 3, 1), (27, 3, 13, 1),
  (28, 4, 2, 1)
ON CONFLICT DO NOTHING;

-- =========================================================
-- 2. Files, reports, generation, citations, export
-- Covers: report-generation / report-citation-export-version
-- =========================================================

INSERT INTO file_objects (id, bucket, object_key, file_name, content_type, size_bytes, checksum, storage_purpose, owner_user_id, sensitivity_level)
VALUES
  (1, 'knowledge', 'demo/uploads/market-q1.pdf', '市场季度资料.pdf', 'application/pdf', 102400, 'demo-checksum-001', 'upload', 2, 'internal'),
  (2, 'knowledge', 'demo/parsed/market-q1.json', '市场季度资料解析结果.json', 'application/json', 20480, 'demo-checksum-002', 'parsed', 2, 'internal'),
  (3, 'exports', 'demo/reports/q1-report.pdf', '一季度经营分析报告.pdf', 'application/pdf', 409600, 'demo-checksum-003', 'export', 2, 'internal'),
  (4, 'templates', 'demo/templates/brand-template.docx', '企业标准报告模板.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 51200, 'demo-checksum-004', 'template', 1, 'internal')
ON CONFLICT DO NOTHING;

INSERT INTO report_templates (id, template_code, template_name, template_type, layout_config, brand_config, status, created_by)
VALUES
  (1, 'standard-business-report', '企业标准经营报告模板', 'business', '{"toc":true,"header":"企业经营分析","footer":"内部资料","pageSize":"A4"}', '{"logo":"demo-logo","primaryColor":"#1F6FEB","font":"Noto Sans CJK"}', 'enabled', 1)
ON CONFLICT DO NOTHING;

INSERT INTO reports (id, title, report_type, owner_user_id, status, current_version_id, knowledge_scope, summary)
VALUES
  (1, '一季度经营分析报告', 'natural_language', 2, 'completed', 1, '{"knowledgeBaseIds":[1],"dataSourceIds":[1]}', '演示报告摘要，包含收入趋势、风险点和改进建议。')
ON CONFLICT DO NOTHING;

INSERT INTO report_generation_tasks (id, report_id, created_by, generation_mode, user_input, template_snapshot, status, current_stage, progress, trace_id, started_at, finished_at)
VALUES
  (1, 1, 2, 'natural_language', '{"topic":"生成一季度经营分析报告","timeRange":"2026Q1","dimensions":["收入","成本","风险"]}', NULL, 'succeeded', 'done', 100, 'trace-demo-001', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hours')
ON CONFLICT DO NOTHING;

INSERT INTO report_outlines (id, task_id, report_id, outline_content, confirmed, confirmed_by, confirmed_at)
VALUES
  (1, 1, 1, '{"sections":[{"title":"经营概览"},{"title":"收入与成本分析"},{"title":"风险与建议"}]}', TRUE, 2, CURRENT_TIMESTAMP - INTERVAL '100 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO report_versions (id, report_id, version_no, snapshot, created_by, change_reason, is_current)
VALUES
  (1, 1, 1, '{"title":"一季度经营分析报告","sections":["经营概览","收入与成本分析","风险与建议"]}', 2, 'AI 生成初版', TRUE),
  (2, 1, 2, '{"title":"一季度经营分析报告","sections":["经营概览","收入与成本分析","风险与建议","附录"]}', 2, '回滚演示目标版本', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO report_sections (id, report_id, version_id, section_no, heading, content, citation_marks)
VALUES
  (1, 1, 1, 1, '经营概览', '一季度整体经营保持稳定，重点业务线收入增长。', '[{"citationNo":"[1]","sourceId":1}]'),
  (2, 1, 1, 2, '收入与成本分析', '收入增长主要来自重点区域，成本控制仍需优化。', '[{"citationNo":"[2]","sourceId":2}]')
ON CONFLICT DO NOTHING;

INSERT INTO citation_sources (id, report_id, source_type, source_title, source_ref_id, source_snapshot, source_metadata)
VALUES
  (1, 1, 'document', '市场季度资料第 3 页', 1, '演示来源快照：一季度重点区域收入增长。', '{"page":3,"documentId":1}'),
  (2, 1, 'datasource', '经营数据源同步结果', 1, '演示来源快照：成本项目结构化汇总。', '{"syncTaskId":1}')
ON CONFLICT DO NOTHING;

INSERT INTO citation_anchors (id, report_id, section_id, citation_source_id, citation_no, anchor_text, anchor_offset)
VALUES
  (1, 1, 1, 1, '[1]', '重点业务线收入增长', '{"start":18,"end":28}'),
  (2, 1, 2, 2, '[2]', '成本控制仍需优化', '{"start":20,"end":29}')
ON CONFLICT DO NOTHING;

INSERT INTO citation_quality_scores (id, citation_source_id, relevance_score, timeliness_score, authority_score, coverage_score, total_score, confidence_level, quality_reason, evaluated_by)
VALUES
  (1, 1, 92.00, 86.00, 80.00, 88.00, 86.50, 'high', '来源与报告段落高度相关，时间范围匹配。', 'ai'),
  (2, 2, 84.00, 90.00, 85.00, 78.00, 84.25, 'medium', '结构化数据可信，但覆盖范围需结合补充材料。', 'ai')
ON CONFLICT DO NOTHING;

INSERT INTO export_files (id, report_id, version_id, file_object_id, export_format, export_status, layout_snapshot, template_id, download_policy, failure_reason, created_by, finished_at)
VALUES
  (1, 1, 1, 3, 'pdf', 'succeeded', '{"toc":true,"header":"企业经营分析","footer":"内部资料","brand":"demo"}', 1, 'presigned_url', NULL, 2, CURRENT_TIMESTAMP - INTERVAL '50 minutes'),
  (2, 1, 1, NULL, 'docx', 'failed', '{"toc":true,"header":"企业经营分析"}', 999, 'backend_proxy', 'EXPORT_TEMPLATE_MISSING', 2, NULL)
ON CONFLICT DO NOTHING;

-- =========================================================
-- 3. Knowledge base, documents, data source
-- Covers: knowledge-base-ingestion / REQ-KB-001/002/003
-- =========================================================

INSERT INTO knowledge_bases (id, name, description, visibility, owner_user_id, status)
VALUES
  (1, '经营分析知识库', '演示用经营分析资料库', 'department', 2, 'enabled')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_documents (id, knowledge_base_id, file_object_id, document_title, file_type, parse_status, ocr_required, table_recognition_required, uploaded_by)
VALUES
  (1, 1, 1, '市场季度资料', 'pdf', 'processed', TRUE, TRUE, 2)
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_entries (id, knowledge_base_id, document_id, title, content_type, content, tags, index_status, created_by)
VALUES
  (1, 1, 1, '一季度区域收入摘要', 'document', '演示知识条目：重点区域收入增长，需结合成本结构分析。', '["收入","季度"]', 'indexed', 2),
  (2, 1, NULL, '手动录入风险提示', 'manual', '演示知识条目：成本波动风险需要在报告中披露。', '["风险","成本"]', 'indexed', 3)
ON CONFLICT DO NOTHING;

INSERT INTO document_parse_results (id, document_id, parse_type, result_payload, confidence, status, failure_reason)
VALUES
  (1, 1, 'ocr', '{"text":"一季度市场资料 OCR 结果","tables":1}', 91.50, 'succeeded', NULL),
  (2, 1, 'table', '{"tables":[{"name":"收入表","rows":3}]}', 88.00, 'succeeded', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO document_chunks (id, document_id, knowledge_entry_id, chunk_index, content, page_no, position_payload, parse_confidence, embedding_status)
VALUES
  (1, 1, 1, 0, '一季度重点区域收入增长，客户续费率稳定。', 3, '{"bbox":[10,20,300,120]}', 91.50, 'embedded'),
  (2, 1, 1, 1, '成本项目中服务采购占比上升，需要持续跟踪。', 4, '{"bbox":[12,25,320,160]}', 88.00, 'embedded')
ON CONFLICT DO NOTHING;

INSERT INTO embeddings (id, chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
VALUES
  (1, 1, 'BGE-M3', 1024, 'knowledge_chunks', 'chunk-1-bge-m3', 'hash-demo-chunk-1'),
  (2, 2, 'BGE-M3', 1024, 'knowledge_chunks', 'chunk-2-bge-m3', 'hash-demo-chunk-2')
ON CONFLICT DO NOTHING;

INSERT INTO data_source_connections (id, knowledge_base_id, source_name, source_type, connection_summary, encrypted_credentials, sync_policy, status, last_test_result, created_by)
VALUES
  (1, 1, '演示ERP数据源', 'erp', '{"host":"erp-demo.internal.invalid","database":"demo_finance"}', 'encrypted-demo-credential', '{"schedule":"daily","scope":"finance-summary"}', 'enabled', '{"success":true,"testedAt":"2026-06-18T00:00:00Z"}', 2),
  (2, 1, '连接失败演示数据源', 'database', '{"host":"invalid.internal.invalid","database":"demo"}', 'encrypted-demo-credential-failed', '{"schedule":"manual"}', 'draft', '{"success":false,"reason":"连接超时"}', 2)
ON CONFLICT DO NOTHING;

INSERT INTO data_source_sync_tasks (id, data_source_id, trigger_type, sync_scope, status, started_at, finished_at, failure_reason)
VALUES
  (1, 1, 'manual', '{"range":"2026Q1"}', 'succeeded', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '170 minutes', NULL),
  (2, 2, 'manual', '{"range":"2026Q1"}', 'failed', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '119 minutes', '连接超时')
ON CONFLICT DO NOTHING;

-- =========================================================
-- 4. RAG, prompt, model audit
-- Covers: report-generation / audit-history-dashboard / REQ-AI-001
-- =========================================================

INSERT INTO retrieval_sessions (id, task_id, report_id, query_text, knowledge_scope, retrieval_strategy, status, finished_at)
VALUES
  (1, 1, 1, '一季度经营分析 收入 成本 风险', '{"knowledgeBaseIds":[1]}', 'hybrid_vector_keyword_rerank', 'succeeded', CURRENT_TIMESTAMP - INTERVAL '90 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO retrieval_results (id, session_id, chunk_id, source_type, source_ref_id, relevance_score, rerank_score, rank_no, selected)
VALUES
  (1, 1, 1, 'document_chunk', 1, 0.9200, 0.9100, 1, TRUE),
  (2, 1, 2, 'document_chunk', 2, 0.8600, 0.8300, 2, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO context_segments (id, session_id, retrieval_result_id, segment_order, content, compressed_summary, source_metadata)
VALUES
  (1, 1, 1, 1, '一季度重点区域收入增长，客户续费率稳定。', '收入增长与续费稳定。', '{"chunkId":1,"citationSourceId":1}'),
  (2, 1, 2, 2, '成本项目中服务采购占比上升，需要持续跟踪。', '服务采购成本上升。', '{"chunkId":2,"citationSourceId":2}')
ON CONFLICT DO NOTHING;

INSERT INTO prompt_templates (id, template_code, template_name, scene, version_no, template_body, variables, status, created_by)
VALUES
  (1, 'report-outline-v1', '报告大纲生成模板', 'outline_generation', 1, '请基于主题 {topic} 和上下文 {context} 生成报告大纲。', '["topic","context"]', 'enabled', 1),
  (2, 'report-section-v1', '报告正文生成模板', 'section_generation', 1, '请基于大纲 {outline}、上下文 {context} 和引用要求生成正文。', '["outline","context"]', 'enabled', 1)
ON CONFLICT DO NOTHING;

INSERT INTO model_invocations (id, task_id, report_id, provider, model_name, prompt_template_id, prompt_snapshot, context_snapshot, parameters, request_hash, status, duration_ms, input_tokens, output_tokens, total_tokens, trace_id, audit_event_key)
VALUES
  (1, 1, 1, 'openai', 'gpt-online-demo', 1, '{"system":"你是企业报告助手","user":"生成一季度经营分析大纲"}', '[{"contextSegmentId":1},{"contextSegmentId":2}]', '{"temperature":0.2,"stream":false}', 'request-hash-demo-001', 'succeeded', 3200, 860, 420, 1280, 'trace-demo-001', 'model-audit-demo-001')
ON CONFLICT DO NOTHING;

INSERT INTO model_responses (id, invocation_id, response_order, response_content, response_metadata, finish_reason)
VALUES
  (1, 1, 1, '经营概览、收入与成本分析、风险与建议。', '{"safety":"passed"}', 'stop')
ON CONFLICT DO NOTHING;

-- =========================================================
-- 5. Rule engine
-- Covers: rule-engine / REQ-RULE-001
-- =========================================================

INSERT INTO rules (id, rule_name, source, status, current_version_id, created_by)
VALUES
  (1, '报告风险提示规则', 'blank', 'enabled', 1, 2)
ON CONFLICT DO NOTHING;

INSERT INTO rule_nodes (id, rule_id, node_key, node_type, title, position_payload, parameter_schema, input_ports, output_ports)
VALUES
  (1, 1, 'node-start', 'input', '输入报告指标', '{"x":80,"y":120}', '{"required":["costRatio"]}', '[]', '["out"]'),
  (2, 1, 'node-risk', 'condition', '成本风险判断', '{"x":320,"y":120}', '{"threshold":0.35}', '["in"]', '["risk","normal"]')
ON CONFLICT DO NOTHING;

INSERT INTO rule_edges (id, rule_id, edge_key, source_node_key, source_port, target_node_key, target_port, condition_payload, status)
VALUES
  (1, 1, 'edge-start-risk', 'node-start', 'out', 'node-risk', 'in', '{}', 'valid')
ON CONFLICT DO NOTHING;

INSERT INTO rule_versions (id, rule_id, version_no, rule_snapshot, created_by)
VALUES
  (1, 1, 1, '{"nodes":["node-start","node-risk"],"edges":["edge-start-risk"]}', 2)
ON CONFLICT DO NOTHING;

INSERT INTO rule_execution_records (id, rule_id, version_id, mode, input_payload, status, error_message, duration_ms, executed_by, finished_at)
VALUES
  (1, 1, 1, 'full', '{"costRatio":0.41}', 'succeeded', NULL, 35, 2, CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
  (2, 1, 1, 'full', '{}', 'failed', '缺少 costRatio 参数', 5, 2, CURRENT_TIMESTAMP - INTERVAL '25 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO rule_node_execution_logs (id, execution_id, node_key, input_payload, output_payload, status, duration_ms, error_message)
VALUES
  (1, 1, 'node-start', '{"costRatio":0.41}', '{"costRatio":0.41}', 'succeeded', 10, NULL),
  (2, 1, 'node-risk', '{"costRatio":0.41}', '{"riskLevel":"high"}', 'succeeded', 25, NULL),
  (3, 2, 'node-start', '{}', NULL, 'failed', 5, '缺少 costRatio 参数')
ON CONFLICT DO NOTHING;

-- =========================================================
-- 6. Sharing, collaboration, notification
-- Covers: permission-collaboration / REQ-COLLAB-001/002
-- =========================================================

INSERT INTO share_links (id, report_id, share_token, scope, target_payload, password_hash, allow_download, expires_at, created_by)
VALUES
  (1, 1, 'demo-share-token-valid', 'external', '{"allowSections":[1,2]}', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9ShareDemoHash0000000000', TRUE, CURRENT_TIMESTAMP + INTERVAL '7 days', 2),
  (2, 1, 'demo-share-token-expired', 'external', '{"allowSections":[1]}', '$2a$10$abcdefghijklmnopqrstuu5XU5xk9ShareDemoHash0000000001', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day', 2)
ON CONFLICT DO NOTHING;

INSERT INTO share_access_records (id, share_link_id, visitor_name, visitor_ip, user_agent, access_result, denied_reason)
VALUES
  (1, 1, '外部访客演示', '203.0.113.10', 'DemoBrowser/1.0', 'granted', NULL),
  (2, 2, '过期链接访客', '203.0.113.11', 'DemoBrowser/1.0', 'denied', 'SHARE_LINK_EXPIRED')
ON CONFLICT DO NOTHING;

INSERT INTO annotations (id, report_id, section_id, selected_text, anchor_payload, content, status, created_by)
VALUES
  (1, 1, 2, '成本控制仍需优化', '{"sectionNo":2,"start":20,"end":29}', '请补充成本上升原因说明。', 'open', 3)
ON CONFLICT DO NOTHING;

INSERT INTO annotation_comments (id, annotation_id, parent_comment_id, content, created_by)
VALUES
  (1, 1, NULL, '已补充服务采购成本说明。', 2)
ON CONFLICT DO NOTHING;

INSERT INTO collaboration_tasks (id, annotation_id, report_id, title, assignee_id, status, due_date, created_by)
VALUES
  (1, 1, 1, '补充成本上升原因', 2, 'pending', CURRENT_DATE + INTERVAL '3 days', 3)
ON CONFLICT DO NOTHING;

INSERT INTO notifications (id, receiver_user_id, notification_type, title, content, payload)
VALUES
  (1, 2, 'collaboration_task', '新的协作任务', '请补充成本上升原因说明。', '{"taskId":1,"reportId":1}')
ON CONFLICT DO NOTHING;

-- =========================================================
-- 7. Audit, dashboard, technical support
-- Covers: audit-history-dashboard / REQ-AUDIT-001 / REQ-DASH-001
-- =========================================================

INSERT INTO operation_logs (id, operator_user_id, operation_type, resource_type, resource_id, action_result, request_ip, user_agent, trace_id, summary)
VALUES
  (1, 2, 'report_create', 'report', 1, 'succeeded', '198.51.100.10', 'DemoBrowser/1.0', 'trace-demo-001', '创建报告生成任务'),
  (2, 2, 'report_export', 'export_file', 1, 'succeeded', '198.51.100.10', 'DemoBrowser/1.0', 'trace-demo-002', '导出 PDF 报告'),
  (3, 4, 'permission_denied', 'report', 1, 'failed', '198.51.100.11', 'DemoBrowser/1.0', 'trace-demo-003', '无权限访问报告')
ON CONFLICT DO NOTHING;

INSERT INTO operation_log_details (id, operation_log_id, before_payload, after_payload, context_payload)
VALUES
  (1, 1, NULL, '{"taskId":1,"reportId":1}', '{"capability":"report-generation"}'),
  (2, 2, NULL, '{"exportFileId":1,"format":"pdf"}', '{"capability":"report-citation-export-version"}'),
  (3, 3, NULL, NULL, '{"requiredPermission":"report:read","deniedReason":"not_granted"}')
ON CONFLICT DO NOTHING;

INSERT INTO dashboard_metric_snapshots (id, metric_date, metric_scope, cards_payload)
VALUES
  (1, CURRENT_DATE, 'global', '{"reportsThisMonth":1,"knowledgeEntries":2,"activeDataSources":1,"citationHitRate":0.86,"activeUsers":4}')
ON CONFLICT DO NOTHING;

INSERT INTO dashboard_trend_points (id, metric_name, metric_date, metric_value, scope_payload)
VALUES
  (1, 'report_count', CURRENT_DATE - INTERVAL '2 days', 0, '{}'),
  (2, 'report_count', CURRENT_DATE - INTERVAL '1 day', 1, '{}'),
  (3, 'citation_hit_rate', CURRENT_DATE, 0.86, '{}')
ON CONFLICT DO NOTHING;

INSERT INTO dashboard_rank_items (id, rank_type, rank_date, item_name, item_ref_id, rank_no, metric_value)
VALUES
  (1, 'knowledge_base_reference', CURRENT_DATE, '经营分析知识库', 1, 1, 2)
ON CONFLICT DO NOTHING;

INSERT INTO dashboard_activities (id, activity_time, actor_user_id, activity_type, resource_type, resource_id, result, summary)
VALUES
  (1, CURRENT_TIMESTAMP - INTERVAL '2 hours', 2, 'report_create', 'report', 1, 'succeeded', '创建一季度经营分析报告'),
  (2, CURRENT_TIMESTAMP - INTERVAL '50 minutes', 2, 'report_export', 'export_file', 1, 'succeeded', '导出 PDF 报告')
ON CONFLICT DO NOTHING;

INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_key, payload, trace_id, status, published_at)
VALUES
  (1, 'ReportGenerationTask', 1, 'ReportOutlineConfirmed', 'outbox-demo-outline-1', '{"taskId":1,"reportId":1}', 'trace-demo-001', 'published', CURRENT_TIMESTAMP - INTERVAL '100 minutes'),
  (2, 'ModelInvocation', 1, 'ModelInvocationCompleted', 'model-audit-demo-001', '{"invocationId":1,"taskId":1}', 'trace-demo-001', 'published', CURRENT_TIMESTAMP - INTERVAL '80 minutes')
ON CONFLICT DO NOTHING;

COMMIT;

-- =========================================================
-- Seed coverage summary
-- =========================================================
-- file_objects: 4, outbox_events: 2
-- users: 5, roles: 4, permissions: 13, user_roles: 5, role_permissions: 28
-- reports: 1, report_generation_tasks: 1, report_outlines: 1, report_sections: 2, report_versions: 2, report_templates: 1
-- citation_sources: 2, citation_anchors: 2, citation_quality_scores: 2, export_files: 2
-- knowledge_bases: 1, knowledge_entries: 2, knowledge_documents: 1, document_parse_results: 2, document_chunks: 2, embeddings: 2
-- data_source_connections: 2, data_source_sync_tasks: 2
-- retrieval_sessions: 1, retrieval_results: 2, context_segments: 2, prompt_templates: 2, model_invocations: 1, model_responses: 1
-- rules: 1, rule_nodes: 2, rule_edges: 1, rule_versions: 1, rule_execution_records: 2, rule_node_execution_logs: 3
-- share_links: 2, share_access_records: 2, annotations: 1, annotation_comments: 1, collaboration_tasks: 1, notifications: 1
-- operation_logs: 3, operation_log_details: 3, dashboard_metric_snapshots: 1, dashboard_trend_points: 3, dashboard_rank_items: 1, dashboard_activities: 2
-- OpenSpec scenarios covered: natural-language report generation, outline confirmation, streamable task state, citation quality, PDF export success, template missing failure, knowledge document OCR/table parse, datasource failure, rule debug success/failure, RBAC matrix, valid/expired share link, annotation task, personal/global audit, model invocation audit, dashboard metrics.
