-- Skill: S9 schema.design
-- Project: 智能报告生成系统
-- Database: PostgreSQL 16
-- Baseline: S7 confirmed OpenSpec baseline + S8 security constraints
-- Design rules:
--   1. 不使用物理外键，跨表关系通过逻辑 ID、应用层不变量和审计约束保证。
--   2. 主数据继续使用 PostgreSQL；向量召回使用 Milvus；全文检索使用 OpenSearch。
--   3. 凭据、分享密码和外部 API Key 不得明文存储。
--   4. 所有业务表保留 trace/audit 字段，支撑 OpenSpec S8-S30 可追溯。
--   5. 导出下载统一经 Higress 入口鉴权；内部用户可用短期预签名 URL，外部分享或严格审计场景走后端代理流。

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- 0. Shared kernel / technical support
-- =========================================================

CREATE TABLE file_objects (
  id BIGSERIAL PRIMARY KEY,
  bucket VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  checksum VARCHAR(128),
  storage_purpose VARCHAR(64) NOT NULL,
  owner_user_id BIGINT,
  sensitivity_level VARCHAR(32) NOT NULL DEFAULT 'internal',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE file_objects IS '文件对象表；支撑 MinIO 上传原文、解析中间产物、导出文件和企业模板；OpenSpec: REQ-KB-002, REQ-REPORT-004';
COMMENT ON COLUMN file_objects.id IS '文件对象ID';
COMMENT ON COLUMN file_objects.bucket IS 'MinIO Bucket 名称';
COMMENT ON COLUMN file_objects.object_key IS 'MinIO 对象 Key，不暴露内部物理路径';
COMMENT ON COLUMN file_objects.file_name IS '原始文件名或导出文件名';
COMMENT ON COLUMN file_objects.content_type IS '文件 MIME 类型';
COMMENT ON COLUMN file_objects.size_bytes IS '文件大小字节数';
COMMENT ON COLUMN file_objects.checksum IS '文件校验值';
COMMENT ON COLUMN file_objects.storage_purpose IS '存储用途：upload/parsed/export/template';
COMMENT ON COLUMN file_objects.owner_user_id IS '逻辑归属用户ID';
COMMENT ON COLUMN file_objects.sensitivity_level IS '敏感级别，供授权与下载策略使用';
COMMENT ON COLUMN file_objects.expires_at IS '对象过期时间';
COMMENT ON COLUMN file_objects.created_at IS '创建时间';
COMMENT ON COLUMN file_objects.updated_at IS '更新时间';
COMMENT ON COLUMN file_objects.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_file_objects_bucket_key ON file_objects(bucket, object_key) WHERE deleted_at IS NULL;
CREATE INDEX idx_file_objects_owner ON file_objects(owner_user_id, storage_purpose) WHERE deleted_at IS NULL;

CREATE TABLE outbox_events (
  id BIGSERIAL PRIMARY KEY,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  event_key VARCHAR(160) NOT NULL,
  payload JSONB NOT NULL,
  trace_id VARCHAR(96),
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE outbox_events IS 'Outbox 事件表；支撑 RocketMQ 最终一致投递；OpenSpec: REQ-AI-001, REQ-AUDIT-001';
COMMENT ON COLUMN outbox_events.id IS '事件ID';
COMMENT ON COLUMN outbox_events.aggregate_type IS '聚合类型';
COMMENT ON COLUMN outbox_events.aggregate_id IS '聚合ID';
COMMENT ON COLUMN outbox_events.event_type IS '领域事件类型';
COMMENT ON COLUMN outbox_events.event_key IS '幂等键';
COMMENT ON COLUMN outbox_events.payload IS '事件载荷JSON';
COMMENT ON COLUMN outbox_events.trace_id IS '链路追踪ID';
COMMENT ON COLUMN outbox_events.status IS '投递状态';
COMMENT ON COLUMN outbox_events.retry_count IS '重试次数';
COMMENT ON COLUMN outbox_events.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN outbox_events.published_at IS '发布时间';
COMMENT ON COLUMN outbox_events.created_at IS '创建时间';
COMMENT ON COLUMN outbox_events.updated_at IS '更新时间';
CREATE UNIQUE INDEX uk_outbox_events_event_key ON outbox_events(event_key);
CREATE INDEX idx_outbox_events_status_retry ON outbox_events(status, next_retry_at);

-- =========================================================
-- 1. Permission collaboration
-- =========================================================

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  email VARCHAR(160),
  phone VARCHAR(40),
  department VARCHAR(120),
  password_hash VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  last_login_at TIMESTAMPTZ,
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE users IS '用户表；支撑 RBAC 用户管理；OpenSpec: REQ-AUTH-001';
COMMENT ON COLUMN users.id IS '用户ID';
COMMENT ON COLUMN users.username IS '登录用户名';
COMMENT ON COLUMN users.display_name IS '用户显示名';
COMMENT ON COLUMN users.email IS '邮箱，列表展示需按权限脱敏';
COMMENT ON COLUMN users.phone IS '手机号，列表展示需按权限脱敏';
COMMENT ON COLUMN users.department IS '部门名称或部门编码';
COMMENT ON COLUMN users.password_hash IS '密码哈希，不得存储明文密码';
COMMENT ON COLUMN users.status IS '用户状态：enabled/disabled';
COMMENT ON COLUMN users.last_login_at IS '最近登录时间';
COMMENT ON COLUMN users.created_by IS '创建人ID';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';
COMMENT ON COLUMN users.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_users_username ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status_department ON users(status, department) WHERE deleted_at IS NULL;

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  role_code VARCHAR(80) NOT NULL,
  role_name VARCHAR(120) NOT NULL,
  description TEXT,
  built_in BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE roles IS '角色表；支撑管理员、高级分析师、分析师、查看者等 RBAC 角色；OpenSpec: REQ-AUTH-001';
COMMENT ON COLUMN roles.id IS '角色ID';
COMMENT ON COLUMN roles.role_code IS '角色编码';
COMMENT ON COLUMN roles.role_name IS '角色名称';
COMMENT ON COLUMN roles.description IS '角色说明';
COMMENT ON COLUMN roles.built_in IS '是否内置角色';
COMMENT ON COLUMN roles.status IS '角色状态';
COMMENT ON COLUMN roles.created_at IS '创建时间';
COMMENT ON COLUMN roles.updated_at IS '更新时间';
COMMENT ON COLUMN roles.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_roles_code ON roles(role_code) WHERE deleted_at IS NULL;

CREATE TABLE permissions (
  id BIGSERIAL PRIMARY KEY,
  permission_code VARCHAR(120) NOT NULL,
  permission_name VARCHAR(160) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  action_code VARCHAR(80) NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE permissions IS '权限项表；支撑权限矩阵和接口权限校验；OpenSpec: REQ-AUTH-001';
COMMENT ON COLUMN permissions.id IS '权限项ID';
COMMENT ON COLUMN permissions.permission_code IS '权限编码';
COMMENT ON COLUMN permissions.permission_name IS '权限名称';
COMMENT ON COLUMN permissions.resource_type IS '资源类型';
COMMENT ON COLUMN permissions.action_code IS '动作编码';
COMMENT ON COLUMN permissions.description IS '权限说明';
COMMENT ON COLUMN permissions.created_at IS '创建时间';
COMMENT ON COLUMN permissions.updated_at IS '更新时间';
CREATE UNIQUE INDEX uk_permissions_code ON permissions(permission_code);

CREATE TABLE user_roles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_by BIGINT,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE user_roles IS '用户角色关系表；逻辑关联 users 与 roles；OpenSpec: REQ-AUTH-001';
COMMENT ON COLUMN user_roles.id IS '关系ID';
COMMENT ON COLUMN user_roles.user_id IS '用户ID';
COMMENT ON COLUMN user_roles.role_id IS '角色ID';
COMMENT ON COLUMN user_roles.assigned_by IS '分配人ID';
COMMENT ON COLUMN user_roles.assigned_at IS '分配时间';
COMMENT ON COLUMN user_roles.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_user_roles_user_role ON user_roles(user_id, role_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_roles_role ON user_roles(role_id) WHERE deleted_at IS NULL;

CREATE TABLE role_permissions (
  id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  granted_by BIGINT,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE role_permissions IS '角色权限关系表；逻辑关联 roles 与 permissions；OpenSpec: REQ-AUTH-001';
COMMENT ON COLUMN role_permissions.id IS '关系ID';
COMMENT ON COLUMN role_permissions.role_id IS '角色ID';
COMMENT ON COLUMN role_permissions.permission_id IS '权限项ID';
COMMENT ON COLUMN role_permissions.granted_by IS '授权人ID';
COMMENT ON COLUMN role_permissions.granted_at IS '授权时间';
COMMENT ON COLUMN role_permissions.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_role_permissions_role_permission ON role_permissions(role_id, permission_id) WHERE deleted_at IS NULL;

-- =========================================================
-- 2. Report generation
-- =========================================================

CREATE TABLE reports (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  report_type VARCHAR(64) NOT NULL DEFAULT 'natural_language',
  owner_user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'draft',
  current_version_id BIGINT,
  knowledge_scope JSONB NOT NULL DEFAULT '{}'::jsonb,
  summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE reports IS '报告表；报告聚合根；OpenSpec: REQ-REPORT-001, REQ-REPORT-003, REQ-REPORT-005';
COMMENT ON COLUMN reports.id IS '报告ID';
COMMENT ON COLUMN reports.title IS '报告标题';
COMMENT ON COLUMN reports.report_type IS '报告类型：natural_language/template';
COMMENT ON COLUMN reports.owner_user_id IS '报告创建人ID';
COMMENT ON COLUMN reports.status IS '报告状态';
COMMENT ON COLUMN reports.current_version_id IS '当前版本ID';
COMMENT ON COLUMN reports.knowledge_scope IS '知识库和数据范围快照';
COMMENT ON COLUMN reports.summary IS '报告摘要';
COMMENT ON COLUMN reports.created_at IS '创建时间';
COMMENT ON COLUMN reports.updated_at IS '更新时间';
COMMENT ON COLUMN reports.deleted_at IS '软删除时间';
CREATE INDEX idx_reports_owner_status ON reports(owner_user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_reports_updated_at ON reports(updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE report_generation_tasks (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT,
  created_by BIGINT NOT NULL,
  generation_mode VARCHAR(40) NOT NULL,
  user_input JSONB NOT NULL,
  template_snapshot JSONB,
  status VARCHAR(40) NOT NULL DEFAULT 'pending',
  current_stage VARCHAR(64),
  progress INT NOT NULL DEFAULT 0,
  failure_reason TEXT,
  trace_id VARCHAR(96),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE report_generation_tasks IS '报告生成任务表；支撑自然语言/模板生成、大纲确认和三阶段流式生成；OpenSpec: REQ-REPORT-001, REQ-REPORT-002, REQ-AI-001';
COMMENT ON COLUMN report_generation_tasks.id IS '生成任务ID';
COMMENT ON COLUMN report_generation_tasks.report_id IS '逻辑关联报告ID';
COMMENT ON COLUMN report_generation_tasks.created_by IS '创建人ID';
COMMENT ON COLUMN report_generation_tasks.generation_mode IS '生成方式：natural_language/template/chat';
COMMENT ON COLUMN report_generation_tasks.user_input IS '用户输入快照';
COMMENT ON COLUMN report_generation_tasks.template_snapshot IS '模板参数快照';
COMMENT ON COLUMN report_generation_tasks.status IS '任务状态';
COMMENT ON COLUMN report_generation_tasks.current_stage IS '当前阶段';
COMMENT ON COLUMN report_generation_tasks.progress IS '生成进度百分比';
COMMENT ON COLUMN report_generation_tasks.failure_reason IS '失败原因';
COMMENT ON COLUMN report_generation_tasks.trace_id IS '链路追踪ID';
COMMENT ON COLUMN report_generation_tasks.started_at IS '开始时间';
COMMENT ON COLUMN report_generation_tasks.finished_at IS '完成时间';
COMMENT ON COLUMN report_generation_tasks.created_at IS '创建时间';
COMMENT ON COLUMN report_generation_tasks.updated_at IS '更新时间';
CREATE INDEX idx_report_generation_tasks_creator_status ON report_generation_tasks(created_by, status);
CREATE INDEX idx_report_generation_tasks_report ON report_generation_tasks(report_id);

CREATE TABLE report_outlines (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  report_id BIGINT,
  outline_content JSONB NOT NULL,
  confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_by BIGINT,
  confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE report_outlines IS '报告大纲表；正文生成前必须确认；OpenSpec: REQ-REPORT-002';
COMMENT ON COLUMN report_outlines.id IS '大纲ID';
COMMENT ON COLUMN report_outlines.task_id IS '生成任务ID';
COMMENT ON COLUMN report_outlines.report_id IS '报告ID';
COMMENT ON COLUMN report_outlines.outline_content IS '大纲内容JSON';
COMMENT ON COLUMN report_outlines.confirmed IS '是否已确认';
COMMENT ON COLUMN report_outlines.confirmed_by IS '确认人ID';
COMMENT ON COLUMN report_outlines.confirmed_at IS '确认时间';
COMMENT ON COLUMN report_outlines.created_at IS '创建时间';
COMMENT ON COLUMN report_outlines.updated_at IS '更新时间';
CREATE INDEX idx_report_outlines_task ON report_outlines(task_id);

CREATE TABLE report_sections (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  version_id BIGINT,
  section_no INT NOT NULL,
  heading VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  citation_marks JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE report_sections IS '报告段落表；支撑正文逐段生成和引用锚定；OpenSpec: REQ-REPORT-003';
COMMENT ON COLUMN report_sections.id IS '段落ID';
COMMENT ON COLUMN report_sections.report_id IS '报告ID';
COMMENT ON COLUMN report_sections.version_id IS '版本ID';
COMMENT ON COLUMN report_sections.section_no IS '段落顺序号';
COMMENT ON COLUMN report_sections.heading IS '段落标题';
COMMENT ON COLUMN report_sections.content IS '段落正文';
COMMENT ON COLUMN report_sections.citation_marks IS '正文引用标记JSON';
COMMENT ON COLUMN report_sections.created_at IS '创建时间';
COMMENT ON COLUMN report_sections.updated_at IS '更新时间';
COMMENT ON COLUMN report_sections.deleted_at IS '软删除时间';
CREATE INDEX idx_report_sections_report_version ON report_sections(report_id, version_id, section_no) WHERE deleted_at IS NULL;

CREATE TABLE report_versions (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot JSONB NOT NULL,
  created_by BIGINT NOT NULL,
  change_reason VARCHAR(255),
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE report_versions IS '报告版本表；回滚产生新版本，不覆盖历史；OpenSpec: REQ-REPORT-005';
COMMENT ON COLUMN report_versions.id IS '版本ID';
COMMENT ON COLUMN report_versions.report_id IS '报告ID';
COMMENT ON COLUMN report_versions.version_no IS '版本号';
COMMENT ON COLUMN report_versions.snapshot IS '报告内容快照';
COMMENT ON COLUMN report_versions.created_by IS '创建人ID';
COMMENT ON COLUMN report_versions.change_reason IS '版本变更原因';
COMMENT ON COLUMN report_versions.is_current IS '是否当前版本';
COMMENT ON COLUMN report_versions.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_report_versions_report_no ON report_versions(report_id, version_no);
CREATE INDEX idx_report_versions_current ON report_versions(report_id, is_current);

CREATE TABLE report_templates (
  id BIGSERIAL PRIMARY KEY,
  template_code VARCHAR(120) NOT NULL,
  template_name VARCHAR(160) NOT NULL,
  template_type VARCHAR(64) NOT NULL,
  layout_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  brand_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE report_templates IS '报告模板表；支撑模板填报和企业导出版式；OpenSpec: REQ-REPORT-001, REQ-REPORT-004';
COMMENT ON COLUMN report_templates.id IS '模板ID';
COMMENT ON COLUMN report_templates.template_code IS '模板编码';
COMMENT ON COLUMN report_templates.template_name IS '模板名称';
COMMENT ON COLUMN report_templates.template_type IS '模板类型';
COMMENT ON COLUMN report_templates.layout_config IS '版式、目录、页眉页脚配置';
COMMENT ON COLUMN report_templates.brand_config IS '企业品牌规范配置';
COMMENT ON COLUMN report_templates.status IS '模板状态';
COMMENT ON COLUMN report_templates.created_by IS '创建人ID';
COMMENT ON COLUMN report_templates.created_at IS '创建时间';
COMMENT ON COLUMN report_templates.updated_at IS '更新时间';
COMMENT ON COLUMN report_templates.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_report_templates_code ON report_templates(template_code) WHERE deleted_at IS NULL;

-- =========================================================
-- 3. Citation / export / version
-- =========================================================

CREATE TABLE citation_sources (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_title VARCHAR(255) NOT NULL,
  source_ref_id BIGINT,
  source_snapshot TEXT NOT NULL,
  source_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE citation_sources IS '引用来源表；支撑来源摘要、来源快照和双向定位；OpenSpec: REQ-REPORT-003';
COMMENT ON COLUMN citation_sources.id IS '引用来源ID';
COMMENT ON COLUMN citation_sources.report_id IS '报告ID';
COMMENT ON COLUMN citation_sources.source_type IS '来源类型：knowledge/document/datasource/manual';
COMMENT ON COLUMN citation_sources.source_title IS '来源标题';
COMMENT ON COLUMN citation_sources.source_ref_id IS '来源业务对象ID';
COMMENT ON COLUMN citation_sources.source_snapshot IS '来源内容快照';
COMMENT ON COLUMN citation_sources.source_metadata IS '来源元数据';
COMMENT ON COLUMN citation_sources.created_at IS '创建时间';
CREATE INDEX idx_citation_sources_report ON citation_sources(report_id);

CREATE TABLE citation_anchors (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  citation_source_id BIGINT NOT NULL,
  citation_no VARCHAR(40) NOT NULL,
  anchor_text VARCHAR(255),
  anchor_offset JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE citation_anchors IS '引用锚点表；支撑正文引用与来源双向联动；OpenSpec: REQ-REPORT-003';
COMMENT ON COLUMN citation_anchors.id IS '引用锚点ID';
COMMENT ON COLUMN citation_anchors.report_id IS '报告ID';
COMMENT ON COLUMN citation_anchors.section_id IS '报告段落ID';
COMMENT ON COLUMN citation_anchors.citation_source_id IS '引用来源ID';
COMMENT ON COLUMN citation_anchors.citation_no IS '引用编号';
COMMENT ON COLUMN citation_anchors.anchor_text IS '引用锚定文本';
COMMENT ON COLUMN citation_anchors.anchor_offset IS '锚点位置JSON';
COMMENT ON COLUMN citation_anchors.created_at IS '创建时间';
CREATE INDEX idx_citation_anchors_report_section ON citation_anchors(report_id, section_id);
CREATE INDEX idx_citation_anchors_source ON citation_anchors(citation_source_id);

CREATE TABLE citation_quality_scores (
  id BIGSERIAL PRIMARY KEY,
  citation_source_id BIGINT NOT NULL,
  relevance_score NUMERIC(5,2) NOT NULL,
  timeliness_score NUMERIC(5,2) NOT NULL,
  authority_score NUMERIC(5,2) NOT NULL,
  coverage_score NUMERIC(5,2) NOT NULL,
  total_score NUMERIC(5,2) NOT NULL,
  confidence_level VARCHAR(32) NOT NULL,
  quality_reason TEXT NOT NULL,
  evaluated_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE citation_quality_scores IS '引用质量评分表；支撑证据可信度和引用质量说明；OpenSpec: REQ-REPORT-003';
COMMENT ON COLUMN citation_quality_scores.id IS '评分ID';
COMMENT ON COLUMN citation_quality_scores.citation_source_id IS '引用来源ID';
COMMENT ON COLUMN citation_quality_scores.relevance_score IS '相关性评分';
COMMENT ON COLUMN citation_quality_scores.timeliness_score IS '时效性评分';
COMMENT ON COLUMN citation_quality_scores.authority_score IS '权威性评分';
COMMENT ON COLUMN citation_quality_scores.coverage_score IS '覆盖度评分';
COMMENT ON COLUMN citation_quality_scores.total_score IS '总评分';
COMMENT ON COLUMN citation_quality_scores.confidence_level IS '可信度等级';
COMMENT ON COLUMN citation_quality_scores.quality_reason IS '评分理由';
COMMENT ON COLUMN citation_quality_scores.evaluated_by IS '评估来源：rule/ai/manual';
COMMENT ON COLUMN citation_quality_scores.created_at IS '创建时间';
CREATE INDEX idx_citation_quality_source ON citation_quality_scores(citation_source_id);

CREATE TABLE export_files (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  file_object_id BIGINT,
  export_format VARCHAR(32) NOT NULL,
  export_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  layout_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  template_id BIGINT,
  download_policy VARCHAR(64) NOT NULL DEFAULT 'presigned_url',
  failure_reason TEXT,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE export_files IS '导出文件表；支撑 PDF/Word/PPT/Markdown、预签名URL与后端代理流下载策略；OpenSpec: REQ-REPORT-004';
COMMENT ON COLUMN export_files.id IS '导出文件ID';
COMMENT ON COLUMN export_files.report_id IS '报告ID';
COMMENT ON COLUMN export_files.version_id IS '报告版本ID';
COMMENT ON COLUMN export_files.file_object_id IS '文件对象ID';
COMMENT ON COLUMN export_files.export_format IS '导出格式：pdf/docx/pptx/markdown';
COMMENT ON COLUMN export_files.export_status IS '导出状态';
COMMENT ON COLUMN export_files.layout_snapshot IS '导出版式、目录、页眉页脚和企业品牌快照';
COMMENT ON COLUMN export_files.template_id IS '报告模板ID';
COMMENT ON COLUMN export_files.download_policy IS '下载策略：presigned_url/backend_proxy';
COMMENT ON COLUMN export_files.failure_reason IS '失败原因';
COMMENT ON COLUMN export_files.created_by IS '导出发起人ID';
COMMENT ON COLUMN export_files.created_at IS '创建时间';
COMMENT ON COLUMN export_files.finished_at IS '完成时间';
COMMENT ON COLUMN export_files.deleted_at IS '软删除时间';
CREATE INDEX idx_export_files_report_version ON export_files(report_id, version_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_export_files_status ON export_files(export_status, created_at);

-- =========================================================
-- 4. Knowledge base / document ingestion
-- =========================================================

CREATE TABLE knowledge_bases (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  visibility VARCHAR(40) NOT NULL DEFAULT 'private',
  owner_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE knowledge_bases IS '知识库表；OpenSpec: REQ-KB-001';
COMMENT ON COLUMN knowledge_bases.id IS '知识库ID';
COMMENT ON COLUMN knowledge_bases.name IS '知识库名称';
COMMENT ON COLUMN knowledge_bases.description IS '知识库描述';
COMMENT ON COLUMN knowledge_bases.visibility IS '可见范围';
COMMENT ON COLUMN knowledge_bases.owner_user_id IS '负责人用户ID';
COMMENT ON COLUMN knowledge_bases.status IS '知识库状态';
COMMENT ON COLUMN knowledge_bases.created_at IS '创建时间';
COMMENT ON COLUMN knowledge_bases.updated_at IS '更新时间';
COMMENT ON COLUMN knowledge_bases.deleted_at IS '软删除时间';
CREATE INDEX idx_knowledge_bases_owner ON knowledge_bases(owner_user_id, status) WHERE deleted_at IS NULL;

CREATE TABLE knowledge_entries (
  id BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT NOT NULL,
  document_id BIGINT,
  title VARCHAR(255) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  content TEXT,
  tags JSONB NOT NULL DEFAULT '[]'::jsonb,
  index_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE knowledge_entries IS '知识条目表；支持手动录入、搜索筛选、删除影响确认；OpenSpec: REQ-KB-001, REQ-KB-002';
COMMENT ON COLUMN knowledge_entries.id IS '知识条目ID';
COMMENT ON COLUMN knowledge_entries.knowledge_base_id IS '知识库ID';
COMMENT ON COLUMN knowledge_entries.document_id IS '来源文档ID';
COMMENT ON COLUMN knowledge_entries.title IS '知识条目标题';
COMMENT ON COLUMN knowledge_entries.content_type IS '内容类型';
COMMENT ON COLUMN knowledge_entries.content IS '手动录入或解析后的正文';
COMMENT ON COLUMN knowledge_entries.tags IS '标签列表JSON';
COMMENT ON COLUMN knowledge_entries.index_status IS '索引状态';
COMMENT ON COLUMN knowledge_entries.created_by IS '创建人ID';
COMMENT ON COLUMN knowledge_entries.created_at IS '创建时间';
COMMENT ON COLUMN knowledge_entries.updated_at IS '更新时间';
COMMENT ON COLUMN knowledge_entries.deleted_at IS '软删除时间';
CREATE INDEX idx_knowledge_entries_base_status ON knowledge_entries(knowledge_base_id, index_status) WHERE deleted_at IS NULL;
CREATE INDEX idx_knowledge_entries_title ON knowledge_entries USING gin(to_tsvector('simple', title)) WHERE deleted_at IS NULL;

CREATE TABLE knowledge_documents (
  id BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT NOT NULL,
  file_object_id BIGINT NOT NULL,
  document_title VARCHAR(255) NOT NULL,
  file_type VARCHAR(64) NOT NULL,
  parse_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  ocr_required BOOLEAN NOT NULL DEFAULT FALSE,
  table_recognition_required BOOLEAN NOT NULL DEFAULT FALSE,
  uploaded_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE knowledge_documents IS '知识文档表；支撑文件上传、OCR、图片表格识别和扫描件处理；OpenSpec: REQ-KB-002';
COMMENT ON COLUMN knowledge_documents.id IS '知识文档ID';
COMMENT ON COLUMN knowledge_documents.knowledge_base_id IS '知识库ID';
COMMENT ON COLUMN knowledge_documents.file_object_id IS '原始文件对象ID';
COMMENT ON COLUMN knowledge_documents.document_title IS '文档标题';
COMMENT ON COLUMN knowledge_documents.file_type IS '文件类型';
COMMENT ON COLUMN knowledge_documents.parse_status IS '解析状态';
COMMENT ON COLUMN knowledge_documents.ocr_required IS '是否需要OCR';
COMMENT ON COLUMN knowledge_documents.table_recognition_required IS '是否需要表格识别';
COMMENT ON COLUMN knowledge_documents.uploaded_by IS '上传人ID';
COMMENT ON COLUMN knowledge_documents.created_at IS '创建时间';
COMMENT ON COLUMN knowledge_documents.updated_at IS '更新时间';
COMMENT ON COLUMN knowledge_documents.deleted_at IS '软删除时间';
CREATE INDEX idx_knowledge_documents_base_status ON knowledge_documents(knowledge_base_id, parse_status) WHERE deleted_at IS NULL;

CREATE TABLE document_parse_results (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL,
  parse_type VARCHAR(64) NOT NULL,
  result_payload JSONB NOT NULL,
  confidence NUMERIC(5,2),
  status VARCHAR(40) NOT NULL,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE document_parse_results IS '文档解析结果表；记录 OCR、表格识别、扫描件解析结果和失败原因；OpenSpec: REQ-KB-002';
COMMENT ON COLUMN document_parse_results.id IS '解析结果ID';
COMMENT ON COLUMN document_parse_results.document_id IS '知识文档ID';
COMMENT ON COLUMN document_parse_results.parse_type IS '解析类型：text/ocr/table/image';
COMMENT ON COLUMN document_parse_results.result_payload IS '解析结果JSON';
COMMENT ON COLUMN document_parse_results.confidence IS '解析置信度';
COMMENT ON COLUMN document_parse_results.status IS '解析状态';
COMMENT ON COLUMN document_parse_results.failure_reason IS '失败原因';
COMMENT ON COLUMN document_parse_results.created_at IS '创建时间';
CREATE INDEX idx_document_parse_results_doc ON document_parse_results(document_id, parse_type);

CREATE TABLE document_chunks (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL,
  knowledge_entry_id BIGINT,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  page_no INT,
  position_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  parse_confidence NUMERIC(5,2),
  embedding_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE document_chunks IS '文档片段表；向量 Collection 的业务主数据来源；OpenSpec: REQ-KB-002, REQ-REPORT-003';
COMMENT ON COLUMN document_chunks.id IS '文档片段ID';
COMMENT ON COLUMN document_chunks.document_id IS '知识文档ID';
COMMENT ON COLUMN document_chunks.knowledge_entry_id IS '知识条目ID';
COMMENT ON COLUMN document_chunks.chunk_index IS '片段序号';
COMMENT ON COLUMN document_chunks.content IS '片段内容';
COMMENT ON COLUMN document_chunks.page_no IS '页码';
COMMENT ON COLUMN document_chunks.position_payload IS '文档位置JSON';
COMMENT ON COLUMN document_chunks.parse_confidence IS '解析置信度';
COMMENT ON COLUMN document_chunks.embedding_status IS '向量化状态';
COMMENT ON COLUMN document_chunks.created_at IS '创建时间';
COMMENT ON COLUMN document_chunks.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_document_chunks_doc_index ON document_chunks(document_id, chunk_index) WHERE deleted_at IS NULL;
CREATE INDEX idx_document_chunks_entry ON document_chunks(knowledge_entry_id) WHERE deleted_at IS NULL;

CREATE TABLE embeddings (
  id BIGSERIAL PRIMARY KEY,
  chunk_id BIGINT NOT NULL,
  embedding_model VARCHAR(120) NOT NULL,
  vector_dimension INT NOT NULL,
  milvus_collection VARCHAR(120) NOT NULL,
  milvus_primary_key VARCHAR(160) NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE embeddings IS 'Embedding 元数据表；向量值存储在 Milvus，PostgreSQL 保存可追溯索引；OpenSpec: REQ-KB-002';
COMMENT ON COLUMN embeddings.id IS 'Embedding元数据ID';
COMMENT ON COLUMN embeddings.chunk_id IS '文档片段ID';
COMMENT ON COLUMN embeddings.embedding_model IS 'Embedding模型名称，例如 BGE-M3';
COMMENT ON COLUMN embeddings.vector_dimension IS '向量维度，BGE-M3=1024';
COMMENT ON COLUMN embeddings.milvus_collection IS 'Milvus Collection 名称';
COMMENT ON COLUMN embeddings.milvus_primary_key IS 'Milvus 主键';
COMMENT ON COLUMN embeddings.content_hash IS '片段内容哈希';
COMMENT ON COLUMN embeddings.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_embeddings_chunk_model ON embeddings(chunk_id, embedding_model);
CREATE INDEX idx_embeddings_milvus_key ON embeddings(milvus_collection, milvus_primary_key);

CREATE TABLE data_source_connections (
  id BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT NOT NULL,
  source_name VARCHAR(160) NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  connection_summary JSONB NOT NULL,
  encrypted_credentials TEXT,
  sync_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(40) NOT NULL DEFAULT 'draft',
  last_test_result JSONB,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE data_source_connections IS '数据源连接表；支持企业内部数据库、ERP、OA、财务系统等；OpenSpec: REQ-KB-003';
COMMENT ON COLUMN data_source_connections.id IS '数据源连接ID';
COMMENT ON COLUMN data_source_connections.knowledge_base_id IS '目标知识库ID';
COMMENT ON COLUMN data_source_connections.source_name IS '数据源名称';
COMMENT ON COLUMN data_source_connections.source_type IS '数据源类型';
COMMENT ON COLUMN data_source_connections.connection_summary IS '非敏感连接摘要';
COMMENT ON COLUMN data_source_connections.encrypted_credentials IS '加密后的凭据，不得明文存储';
COMMENT ON COLUMN data_source_connections.sync_policy IS '同步策略JSON';
COMMENT ON COLUMN data_source_connections.status IS '连接状态';
COMMENT ON COLUMN data_source_connections.last_test_result IS '最近测试结果';
COMMENT ON COLUMN data_source_connections.created_by IS '创建人ID';
COMMENT ON COLUMN data_source_connections.created_at IS '创建时间';
COMMENT ON COLUMN data_source_connections.updated_at IS '更新时间';
COMMENT ON COLUMN data_source_connections.deleted_at IS '软删除时间';
CREATE INDEX idx_data_source_connections_base_status ON data_source_connections(knowledge_base_id, status) WHERE deleted_at IS NULL;

CREATE TABLE data_source_sync_tasks (
  id BIGSERIAL PRIMARY KEY,
  data_source_id BIGINT NOT NULL,
  trigger_type VARCHAR(40) NOT NULL,
  sync_scope JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(40) NOT NULL DEFAULT 'pending',
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE data_source_sync_tasks IS '数据源同步任务表；OpenSpec: REQ-KB-003';
COMMENT ON COLUMN data_source_sync_tasks.id IS '同步任务ID';
COMMENT ON COLUMN data_source_sync_tasks.data_source_id IS '数据源连接ID';
COMMENT ON COLUMN data_source_sync_tasks.trigger_type IS '触发类型：manual/scheduled';
COMMENT ON COLUMN data_source_sync_tasks.sync_scope IS '同步范围JSON';
COMMENT ON COLUMN data_source_sync_tasks.status IS '同步状态';
COMMENT ON COLUMN data_source_sync_tasks.started_at IS '开始时间';
COMMENT ON COLUMN data_source_sync_tasks.finished_at IS '完成时间';
COMMENT ON COLUMN data_source_sync_tasks.failure_reason IS '失败原因';
COMMENT ON COLUMN data_source_sync_tasks.created_at IS '创建时间';
CREATE INDEX idx_data_source_sync_tasks_source_status ON data_source_sync_tasks(data_source_id, status, created_at DESC);

-- =========================================================
-- 5. RAG / LLM / model audit
-- =========================================================

CREATE TABLE retrieval_sessions (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  report_id BIGINT,
  query_text TEXT NOT NULL,
  knowledge_scope JSONB NOT NULL,
  retrieval_strategy VARCHAR(80) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'running',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMPTZ
);
COMMENT ON TABLE retrieval_sessions IS '检索会话表；记录混合检索、重排和上下文构造；OpenSpec: REQ-AI-001, REQ-REPORT-003';
COMMENT ON COLUMN retrieval_sessions.id IS '检索会话ID';
COMMENT ON COLUMN retrieval_sessions.task_id IS '报告生成任务ID';
COMMENT ON COLUMN retrieval_sessions.report_id IS '报告ID';
COMMENT ON COLUMN retrieval_sessions.query_text IS '检索查询文本';
COMMENT ON COLUMN retrieval_sessions.knowledge_scope IS '知识范围快照';
COMMENT ON COLUMN retrieval_sessions.retrieval_strategy IS '检索策略';
COMMENT ON COLUMN retrieval_sessions.status IS '检索状态';
COMMENT ON COLUMN retrieval_sessions.created_at IS '创建时间';
COMMENT ON COLUMN retrieval_sessions.finished_at IS '完成时间';
CREATE INDEX idx_retrieval_sessions_task ON retrieval_sessions(task_id);

CREATE TABLE retrieval_results (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL,
  chunk_id BIGINT,
  source_type VARCHAR(64) NOT NULL,
  source_ref_id BIGINT,
  relevance_score NUMERIC(7,4) NOT NULL,
  rerank_score NUMERIC(7,4),
  rank_no INT NOT NULL,
  selected BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE retrieval_results IS '检索结果表；保留来源、分数和排序；OpenSpec: REQ-REPORT-003';
COMMENT ON COLUMN retrieval_results.id IS '检索结果ID';
COMMENT ON COLUMN retrieval_results.session_id IS '检索会话ID';
COMMENT ON COLUMN retrieval_results.chunk_id IS '文档片段ID';
COMMENT ON COLUMN retrieval_results.source_type IS '来源类型';
COMMENT ON COLUMN retrieval_results.source_ref_id IS '来源业务对象ID';
COMMENT ON COLUMN retrieval_results.relevance_score IS '相关性分数';
COMMENT ON COLUMN retrieval_results.rerank_score IS '重排分数';
COMMENT ON COLUMN retrieval_results.rank_no IS '排序号';
COMMENT ON COLUMN retrieval_results.selected IS '是否进入上下文';
COMMENT ON COLUMN retrieval_results.created_at IS '创建时间';
CREATE INDEX idx_retrieval_results_session_rank ON retrieval_results(session_id, rank_no);

CREATE TABLE context_segments (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL,
  retrieval_result_id BIGINT,
  segment_order INT NOT NULL,
  content TEXT NOT NULL,
  compressed_summary TEXT,
  source_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE context_segments IS '上下文片段表；记录进入 Prompt 的检索上下文；OpenSpec: REQ-AI-001';
COMMENT ON COLUMN context_segments.id IS '上下文片段ID';
COMMENT ON COLUMN context_segments.session_id IS '检索会话ID';
COMMENT ON COLUMN context_segments.retrieval_result_id IS '检索结果ID';
COMMENT ON COLUMN context_segments.segment_order IS '上下文顺序';
COMMENT ON COLUMN context_segments.content IS '上下文内容';
COMMENT ON COLUMN context_segments.compressed_summary IS '压缩摘要';
COMMENT ON COLUMN context_segments.source_metadata IS '来源元数据';
COMMENT ON COLUMN context_segments.created_at IS '创建时间';
CREATE INDEX idx_context_segments_session_order ON context_segments(session_id, segment_order);

CREATE TABLE prompt_templates (
  id BIGSERIAL PRIMARY KEY,
  template_code VARCHAR(120) NOT NULL,
  template_name VARCHAR(160) NOT NULL,
  scene VARCHAR(80) NOT NULL,
  version_no INT NOT NULL,
  template_body TEXT NOT NULL,
  variables JSONB NOT NULL DEFAULT '[]'::jsonb,
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE prompt_templates IS 'Prompt 模板表；模板版本化后不可变；OpenSpec: REQ-AI-001';
COMMENT ON COLUMN prompt_templates.id IS 'Prompt模板ID';
COMMENT ON COLUMN prompt_templates.template_code IS '模板编码';
COMMENT ON COLUMN prompt_templates.template_name IS '模板名称';
COMMENT ON COLUMN prompt_templates.scene IS '适用场景';
COMMENT ON COLUMN prompt_templates.version_no IS '版本号';
COMMENT ON COLUMN prompt_templates.template_body IS '模板正文';
COMMENT ON COLUMN prompt_templates.variables IS '变量定义JSON';
COMMENT ON COLUMN prompt_templates.status IS '模板状态';
COMMENT ON COLUMN prompt_templates.created_by IS '创建人ID';
COMMENT ON COLUMN prompt_templates.created_at IS '创建时间';
COMMENT ON COLUMN prompt_templates.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_prompt_templates_code_version ON prompt_templates(template_code, version_no) WHERE deleted_at IS NULL;

CREATE TABLE model_invocations (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT,
  report_id BIGINT,
  provider VARCHAR(80) NOT NULL,
  model_name VARCHAR(160) NOT NULL,
  prompt_template_id BIGINT,
  prompt_snapshot JSONB NOT NULL,
  context_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
  parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
  request_hash VARCHAR(128) NOT NULL,
  status VARCHAR(40) NOT NULL,
  duration_ms INT,
  input_tokens INT,
  output_tokens INT,
  total_tokens INT,
  error_code VARCHAR(80),
  error_message TEXT,
  trace_id VARCHAR(96),
  audit_event_key VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE model_invocations IS '模型调用审计表；由 RocketMQ model-audit-events 归口落库；OpenSpec: REQ-AI-001, REQ-AUDIT-001';
COMMENT ON COLUMN model_invocations.id IS '模型调用记录ID';
COMMENT ON COLUMN model_invocations.task_id IS '报告生成任务ID';
COMMENT ON COLUMN model_invocations.report_id IS '报告ID';
COMMENT ON COLUMN model_invocations.provider IS '模型供应商';
COMMENT ON COLUMN model_invocations.model_name IS '模型名称，当前线上GPT优先并预留多模型';
COMMENT ON COLUMN model_invocations.prompt_template_id IS 'Prompt模板ID';
COMMENT ON COLUMN model_invocations.prompt_snapshot IS '最终Prompt快照，按权限展示';
COMMENT ON COLUMN model_invocations.context_snapshot IS '检索上下文快照';
COMMENT ON COLUMN model_invocations.parameters IS '模型参数JSON';
COMMENT ON COLUMN model_invocations.request_hash IS '请求哈希';
COMMENT ON COLUMN model_invocations.status IS '调用状态';
COMMENT ON COLUMN model_invocations.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN model_invocations.input_tokens IS '输入Token数';
COMMENT ON COLUMN model_invocations.output_tokens IS '输出Token数';
COMMENT ON COLUMN model_invocations.total_tokens IS '总Token数';
COMMENT ON COLUMN model_invocations.error_code IS '错误码';
COMMENT ON COLUMN model_invocations.error_message IS '错误摘要';
COMMENT ON COLUMN model_invocations.trace_id IS '链路追踪ID';
COMMENT ON COLUMN model_invocations.audit_event_key IS '审计事件幂等键';
COMMENT ON COLUMN model_invocations.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_model_invocations_event_key ON model_invocations(audit_event_key);
CREATE INDEX idx_model_invocations_task ON model_invocations(task_id, created_at DESC);
CREATE INDEX idx_model_invocations_model ON model_invocations(provider, model_name, created_at DESC);

CREATE TABLE model_responses (
  id BIGSERIAL PRIMARY KEY,
  invocation_id BIGINT NOT NULL,
  response_order INT NOT NULL,
  response_content TEXT,
  response_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  finish_reason VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE model_responses IS '模型响应表；支持流式或分段响应审计；OpenSpec: REQ-AI-001';
COMMENT ON COLUMN model_responses.id IS '模型响应ID';
COMMENT ON COLUMN model_responses.invocation_id IS '模型调用记录ID';
COMMENT ON COLUMN model_responses.response_order IS '响应片段顺序';
COMMENT ON COLUMN model_responses.response_content IS '模型响应内容，展示时需权限控制和脱敏';
COMMENT ON COLUMN model_responses.response_metadata IS '响应元数据';
COMMENT ON COLUMN model_responses.finish_reason IS '完成原因';
COMMENT ON COLUMN model_responses.created_at IS '创建时间';
CREATE INDEX idx_model_responses_invocation ON model_responses(invocation_id, response_order);

-- =========================================================
-- 6. Rule engine
-- =========================================================

CREATE TABLE rules (
  id BIGSERIAL PRIMARY KEY,
  rule_name VARCHAR(160) NOT NULL,
  source VARCHAR(40) NOT NULL DEFAULT 'blank',
  status VARCHAR(40) NOT NULL DEFAULT 'draft',
  current_version_id BIGINT,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE rules IS '规则表；支撑规则创建、导入、保存和运行；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rules.id IS '规则ID';
COMMENT ON COLUMN rules.rule_name IS '规则名称';
COMMENT ON COLUMN rules.source IS '创建来源：blank/template/import';
COMMENT ON COLUMN rules.status IS '规则状态';
COMMENT ON COLUMN rules.current_version_id IS '当前规则版本ID';
COMMENT ON COLUMN rules.created_by IS '创建人ID';
COMMENT ON COLUMN rules.created_at IS '创建时间';
COMMENT ON COLUMN rules.updated_at IS '更新时间';
COMMENT ON COLUMN rules.deleted_at IS '软删除时间';
CREATE INDEX idx_rules_creator_status ON rules(created_by, status) WHERE deleted_at IS NULL;

CREATE TABLE rule_nodes (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  node_key VARCHAR(120) NOT NULL,
  node_type VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  position_payload JSONB NOT NULL,
  parameter_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
  input_ports JSONB NOT NULL DEFAULT '[]'::jsonb,
  output_ports JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE rule_nodes IS '规则节点表；维护节点契约字段；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rule_nodes.id IS '规则节点ID';
COMMENT ON COLUMN rule_nodes.rule_id IS '规则ID';
COMMENT ON COLUMN rule_nodes.node_key IS '前端画布节点Key';
COMMENT ON COLUMN rule_nodes.node_type IS '节点类型';
COMMENT ON COLUMN rule_nodes.title IS '节点标题';
COMMENT ON COLUMN rule_nodes.position_payload IS '节点位置JSON';
COMMENT ON COLUMN rule_nodes.parameter_schema IS '节点参数Schema';
COMMENT ON COLUMN rule_nodes.input_ports IS '输入端口定义';
COMMENT ON COLUMN rule_nodes.output_ports IS '输出端口定义';
COMMENT ON COLUMN rule_nodes.created_at IS '创建时间';
COMMENT ON COLUMN rule_nodes.updated_at IS '更新时间';
COMMENT ON COLUMN rule_nodes.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_rule_nodes_rule_key ON rule_nodes(rule_id, node_key) WHERE deleted_at IS NULL;

CREATE TABLE rule_edges (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  edge_key VARCHAR(120) NOT NULL,
  source_node_key VARCHAR(120) NOT NULL,
  source_port VARCHAR(80) NOT NULL,
  target_node_key VARCHAR(120) NOT NULL,
  target_port VARCHAR(80) NOT NULL,
  condition_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(40) NOT NULL DEFAULT 'valid',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE rule_edges IS '规则连线表；支撑端口合法性校验；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rule_edges.id IS '规则连线ID';
COMMENT ON COLUMN rule_edges.rule_id IS '规则ID';
COMMENT ON COLUMN rule_edges.edge_key IS '前端画布连线Key';
COMMENT ON COLUMN rule_edges.source_node_key IS '起点节点Key';
COMMENT ON COLUMN rule_edges.source_port IS '起点端口';
COMMENT ON COLUMN rule_edges.target_node_key IS '终点节点Key';
COMMENT ON COLUMN rule_edges.target_port IS '终点端口';
COMMENT ON COLUMN rule_edges.condition_payload IS '连线条件JSON';
COMMENT ON COLUMN rule_edges.status IS '连线状态';
COMMENT ON COLUMN rule_edges.created_at IS '创建时间';
COMMENT ON COLUMN rule_edges.deleted_at IS '软删除时间';
CREATE UNIQUE INDEX uk_rule_edges_rule_key ON rule_edges(rule_id, edge_key) WHERE deleted_at IS NULL;

CREATE TABLE rule_versions (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  rule_snapshot JSONB NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE rule_versions IS '规则版本表；保存规则节点与连线快照；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rule_versions.id IS '规则版本ID';
COMMENT ON COLUMN rule_versions.rule_id IS '规则ID';
COMMENT ON COLUMN rule_versions.version_no IS '版本号';
COMMENT ON COLUMN rule_versions.rule_snapshot IS '规则快照JSON';
COMMENT ON COLUMN rule_versions.created_by IS '创建人ID';
COMMENT ON COLUMN rule_versions.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_rule_versions_rule_no ON rule_versions(rule_id, version_no);

CREATE TABLE rule_execution_records (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  version_id BIGINT,
  mode VARCHAR(40) NOT NULL,
  input_payload JSONB NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'running',
  error_message TEXT,
  duration_ms INT,
  executed_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMPTZ
);
COMMENT ON TABLE rule_execution_records IS '规则执行记录表；支撑调试执行和单步执行；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rule_execution_records.id IS '规则执行记录ID';
COMMENT ON COLUMN rule_execution_records.rule_id IS '规则ID';
COMMENT ON COLUMN rule_execution_records.version_id IS '规则版本ID';
COMMENT ON COLUMN rule_execution_records.mode IS '执行模式：full/step';
COMMENT ON COLUMN rule_execution_records.input_payload IS '执行输入JSON';
COMMENT ON COLUMN rule_execution_records.status IS '执行状态';
COMMENT ON COLUMN rule_execution_records.error_message IS '错误信息';
COMMENT ON COLUMN rule_execution_records.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN rule_execution_records.executed_by IS '执行人ID';
COMMENT ON COLUMN rule_execution_records.created_at IS '创建时间';
COMMENT ON COLUMN rule_execution_records.finished_at IS '完成时间';
CREATE INDEX idx_rule_execution_records_rule ON rule_execution_records(rule_id, created_at DESC);

CREATE TABLE rule_node_execution_logs (
  id BIGSERIAL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  node_key VARCHAR(120) NOT NULL,
  input_payload JSONB,
  output_payload JSONB,
  status VARCHAR(40) NOT NULL,
  duration_ms INT,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE rule_node_execution_logs IS '规则节点执行日志表；记录节点输入、输出、状态、耗时和错误；OpenSpec: REQ-RULE-001';
COMMENT ON COLUMN rule_node_execution_logs.id IS '节点日志ID';
COMMENT ON COLUMN rule_node_execution_logs.execution_id IS '规则执行记录ID';
COMMENT ON COLUMN rule_node_execution_logs.node_key IS '节点Key';
COMMENT ON COLUMN rule_node_execution_logs.input_payload IS '节点输入JSON';
COMMENT ON COLUMN rule_node_execution_logs.output_payload IS '节点输出JSON';
COMMENT ON COLUMN rule_node_execution_logs.status IS '节点执行状态';
COMMENT ON COLUMN rule_node_execution_logs.duration_ms IS '节点耗时毫秒';
COMMENT ON COLUMN rule_node_execution_logs.error_message IS '错误信息';
COMMENT ON COLUMN rule_node_execution_logs.created_at IS '创建时间';
CREATE INDEX idx_rule_node_logs_execution ON rule_node_execution_logs(execution_id, created_at);

-- =========================================================
-- 7. Sharing / annotation / collaboration
-- =========================================================

CREATE TABLE share_links (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  share_token VARCHAR(160) NOT NULL,
  scope VARCHAR(40) NOT NULL,
  target_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  password_hash VARCHAR(255),
  allow_download BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE share_links IS '分享链接表；外部用户访问报告的受控入口；OpenSpec: REQ-COLLAB-001';
COMMENT ON COLUMN share_links.id IS '分享链接ID';
COMMENT ON COLUMN share_links.report_id IS '报告ID';
COMMENT ON COLUMN share_links.share_token IS '不可预测分享令牌';
COMMENT ON COLUMN share_links.scope IS '分享范围：users/departments/external';
COMMENT ON COLUMN share_links.target_payload IS '分享目标JSON';
COMMENT ON COLUMN share_links.password_hash IS '分享访问密码哈希，不得明文存储';
COMMENT ON COLUMN share_links.allow_download IS '是否允许外部下载';
COMMENT ON COLUMN share_links.expires_at IS '过期时间';
COMMENT ON COLUMN share_links.revoked_at IS '撤销时间';
COMMENT ON COLUMN share_links.created_by IS '创建人ID';
COMMENT ON COLUMN share_links.created_at IS '创建时间';
COMMENT ON COLUMN share_links.updated_at IS '更新时间';
CREATE UNIQUE INDEX uk_share_links_token ON share_links(share_token);
CREATE INDEX idx_share_links_report ON share_links(report_id, expires_at);

CREATE TABLE share_access_records (
  id BIGSERIAL PRIMARY KEY,
  share_link_id BIGINT NOT NULL,
  visitor_name VARCHAR(120),
  visitor_ip VARCHAR(80),
  user_agent TEXT,
  access_result VARCHAR(40) NOT NULL,
  denied_reason VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE share_access_records IS '分享访问记录表；记录有效访问和失败原因；OpenSpec: REQ-COLLAB-001';
COMMENT ON COLUMN share_access_records.id IS '访问记录ID';
COMMENT ON COLUMN share_access_records.share_link_id IS '分享链接ID';
COMMENT ON COLUMN share_access_records.visitor_name IS '外部访问者名称';
COMMENT ON COLUMN share_access_records.visitor_ip IS '访问IP';
COMMENT ON COLUMN share_access_records.user_agent IS '访问User-Agent';
COMMENT ON COLUMN share_access_records.access_result IS '访问结果';
COMMENT ON COLUMN share_access_records.denied_reason IS '拒绝原因';
COMMENT ON COLUMN share_access_records.created_at IS '访问时间';
CREATE INDEX idx_share_access_records_link_time ON share_access_records(share_link_id, created_at DESC);

CREATE TABLE annotations (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  section_id BIGINT,
  selected_text TEXT,
  anchor_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  content TEXT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'open',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE annotations IS '批注表；支撑报告批注和任务指派；OpenSpec: REQ-COLLAB-002';
COMMENT ON COLUMN annotations.id IS '批注ID';
COMMENT ON COLUMN annotations.report_id IS '报告ID';
COMMENT ON COLUMN annotations.section_id IS '报告段落ID';
COMMENT ON COLUMN annotations.selected_text IS '选中文本';
COMMENT ON COLUMN annotations.anchor_payload IS '批注位置JSON';
COMMENT ON COLUMN annotations.content IS '批注内容';
COMMENT ON COLUMN annotations.status IS '批注状态';
COMMENT ON COLUMN annotations.created_by IS '创建人ID';
COMMENT ON COLUMN annotations.created_at IS '创建时间';
COMMENT ON COLUMN annotations.updated_at IS '更新时间';
COMMENT ON COLUMN annotations.deleted_at IS '软删除时间';
CREATE INDEX idx_annotations_report ON annotations(report_id, status) WHERE deleted_at IS NULL;

CREATE TABLE annotation_comments (
  id BIGSERIAL PRIMARY KEY,
  annotation_id BIGINT NOT NULL,
  parent_comment_id BIGINT,
  content TEXT NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE annotation_comments IS '批注评论表；OpenSpec: REQ-COLLAB-002';
COMMENT ON COLUMN annotation_comments.id IS '评论ID';
COMMENT ON COLUMN annotation_comments.annotation_id IS '批注ID';
COMMENT ON COLUMN annotation_comments.parent_comment_id IS '父评论ID';
COMMENT ON COLUMN annotation_comments.content IS '评论内容';
COMMENT ON COLUMN annotation_comments.created_by IS '评论人ID';
COMMENT ON COLUMN annotation_comments.created_at IS '创建时间';
COMMENT ON COLUMN annotation_comments.deleted_at IS '软删除时间';
CREATE INDEX idx_annotation_comments_annotation ON annotation_comments(annotation_id, created_at) WHERE deleted_at IS NULL;

CREATE TABLE collaboration_tasks (
  id BIGSERIAL PRIMARY KEY,
  annotation_id BIGINT,
  report_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  assignee_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'pending',
  due_date DATE,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE collaboration_tasks IS '协作任务表；被指派人必须有报告访问权限；OpenSpec: REQ-COLLAB-002';
COMMENT ON COLUMN collaboration_tasks.id IS '任务ID';
COMMENT ON COLUMN collaboration_tasks.annotation_id IS '批注ID';
COMMENT ON COLUMN collaboration_tasks.report_id IS '报告ID';
COMMENT ON COLUMN collaboration_tasks.title IS '任务标题';
COMMENT ON COLUMN collaboration_tasks.assignee_id IS '处理人ID';
COMMENT ON COLUMN collaboration_tasks.status IS '任务状态';
COMMENT ON COLUMN collaboration_tasks.due_date IS '截止日期';
COMMENT ON COLUMN collaboration_tasks.created_by IS '创建人ID';
COMMENT ON COLUMN collaboration_tasks.created_at IS '创建时间';
COMMENT ON COLUMN collaboration_tasks.updated_at IS '更新时间';
COMMENT ON COLUMN collaboration_tasks.deleted_at IS '软删除时间';
CREATE INDEX idx_collaboration_tasks_assignee ON collaboration_tasks(assignee_id, status) WHERE deleted_at IS NULL;

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  receiver_user_id BIGINT NOT NULL,
  notification_type VARCHAR(80) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE notifications IS '站内通知表；支撑批注、任务和系统消息；OpenSpec: REQ-COLLAB-002';
COMMENT ON COLUMN notifications.id IS '通知ID';
COMMENT ON COLUMN notifications.receiver_user_id IS '接收人用户ID';
COMMENT ON COLUMN notifications.notification_type IS '通知类型';
COMMENT ON COLUMN notifications.title IS '通知标题';
COMMENT ON COLUMN notifications.content IS '通知内容';
COMMENT ON COLUMN notifications.payload IS '通知载荷JSON';
COMMENT ON COLUMN notifications.read_at IS '已读时间';
COMMENT ON COLUMN notifications.created_at IS '创建时间';
CREATE INDEX idx_notifications_receiver ON notifications(receiver_user_id, read_at, created_at DESC);

-- =========================================================
-- 8. Audit history / dashboard
-- =========================================================

CREATE TABLE operation_logs (
  id BIGSERIAL PRIMARY KEY,
  operator_user_id BIGINT,
  operation_type VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id BIGINT,
  action_result VARCHAR(40) NOT NULL,
  request_ip VARCHAR(80),
  user_agent TEXT,
  trace_id VARCHAR(96),
  summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE operation_logs IS '操作审计日志表；记录创建、编辑、删除、分享、导出、访问等；OpenSpec: REQ-AUDIT-001';
COMMENT ON COLUMN operation_logs.id IS '操作日志ID';
COMMENT ON COLUMN operation_logs.operator_user_id IS '操作者用户ID';
COMMENT ON COLUMN operation_logs.operation_type IS '操作类型';
COMMENT ON COLUMN operation_logs.resource_type IS '资源类型';
COMMENT ON COLUMN operation_logs.resource_id IS '资源ID';
COMMENT ON COLUMN operation_logs.action_result IS '操作结果';
COMMENT ON COLUMN operation_logs.request_ip IS '请求IP';
COMMENT ON COLUMN operation_logs.user_agent IS 'User-Agent';
COMMENT ON COLUMN operation_logs.trace_id IS '链路追踪ID';
COMMENT ON COLUMN operation_logs.summary IS '日志摘要';
COMMENT ON COLUMN operation_logs.created_at IS '创建时间';
CREATE INDEX idx_operation_logs_operator_time ON operation_logs(operator_user_id, created_at DESC);
CREATE INDEX idx_operation_logs_resource ON operation_logs(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_operation_logs_type_time ON operation_logs(operation_type, created_at DESC);

CREATE TABLE operation_log_details (
  id BIGSERIAL PRIMARY KEY,
  operation_log_id BIGINT NOT NULL,
  before_payload JSONB,
  after_payload JSONB,
  context_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE operation_log_details IS '操作日志详情表；记录前后变化和上下文；OpenSpec: REQ-AUDIT-001';
COMMENT ON COLUMN operation_log_details.id IS '日志详情ID';
COMMENT ON COLUMN operation_log_details.operation_log_id IS '操作日志ID';
COMMENT ON COLUMN operation_log_details.before_payload IS '操作前数据快照';
COMMENT ON COLUMN operation_log_details.after_payload IS '操作后数据快照';
COMMENT ON COLUMN operation_log_details.context_payload IS '操作上下文JSON';
COMMENT ON COLUMN operation_log_details.created_at IS '创建时间';
CREATE INDEX idx_operation_log_details_log ON operation_log_details(operation_log_id);

CREATE TABLE dashboard_metric_snapshots (
  id BIGSERIAL PRIMARY KEY,
  metric_date DATE NOT NULL,
  metric_scope VARCHAR(80) NOT NULL DEFAULT 'global',
  cards_payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dashboard_metric_snapshots IS '工作台指标快照表；支撑指标卡片总览；OpenSpec: REQ-DASH-001';
COMMENT ON COLUMN dashboard_metric_snapshots.id IS '指标快照ID';
COMMENT ON COLUMN dashboard_metric_snapshots.metric_date IS '指标日期';
COMMENT ON COLUMN dashboard_metric_snapshots.metric_scope IS '指标范围';
COMMENT ON COLUMN dashboard_metric_snapshots.cards_payload IS '指标卡片JSON';
COMMENT ON COLUMN dashboard_metric_snapshots.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_dashboard_metric_snapshots_date_scope ON dashboard_metric_snapshots(metric_date, metric_scope);

CREATE TABLE dashboard_trend_points (
  id BIGSERIAL PRIMARY KEY,
  metric_name VARCHAR(120) NOT NULL,
  metric_date DATE NOT NULL,
  metric_value NUMERIC(18,4) NOT NULL,
  scope_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dashboard_trend_points IS '工作台趋势点表；OpenSpec: REQ-DASH-001';
COMMENT ON COLUMN dashboard_trend_points.id IS '趋势点ID';
COMMENT ON COLUMN dashboard_trend_points.metric_name IS '指标名称';
COMMENT ON COLUMN dashboard_trend_points.metric_date IS '指标日期';
COMMENT ON COLUMN dashboard_trend_points.metric_value IS '指标值';
COMMENT ON COLUMN dashboard_trend_points.scope_payload IS '统计范围JSON';
COMMENT ON COLUMN dashboard_trend_points.created_at IS '创建时间';
CREATE INDEX idx_dashboard_trend_points_name_date ON dashboard_trend_points(metric_name, metric_date);

CREATE TABLE dashboard_rank_items (
  id BIGSERIAL PRIMARY KEY,
  rank_type VARCHAR(120) NOT NULL,
  rank_date DATE NOT NULL,
  item_name VARCHAR(200) NOT NULL,
  item_ref_id BIGINT,
  rank_no INT NOT NULL,
  metric_value NUMERIC(18,4) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dashboard_rank_items IS '工作台排行项表；支撑知识库引用排行等；OpenSpec: REQ-DASH-001';
COMMENT ON COLUMN dashboard_rank_items.id IS '排行项ID';
COMMENT ON COLUMN dashboard_rank_items.rank_type IS '排行类型';
COMMENT ON COLUMN dashboard_rank_items.rank_date IS '排行日期';
COMMENT ON COLUMN dashboard_rank_items.item_name IS '排行项名称';
COMMENT ON COLUMN dashboard_rank_items.item_ref_id IS '排行对象ID';
COMMENT ON COLUMN dashboard_rank_items.rank_no IS '排名';
COMMENT ON COLUMN dashboard_rank_items.metric_value IS '指标值';
COMMENT ON COLUMN dashboard_rank_items.created_at IS '创建时间';
CREATE INDEX idx_dashboard_rank_items_type_date ON dashboard_rank_items(rank_type, rank_date, rank_no);

CREATE TABLE dashboard_activities (
  id BIGSERIAL PRIMARY KEY,
  activity_time TIMESTAMPTZ NOT NULL,
  actor_user_id BIGINT,
  activity_type VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id BIGINT,
  result VARCHAR(40) NOT NULL,
  summary VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dashboard_activities IS '工作台最近活动表；OpenSpec: REQ-DASH-001';
COMMENT ON COLUMN dashboard_activities.id IS '活动ID';
COMMENT ON COLUMN dashboard_activities.activity_time IS '活动发生时间';
COMMENT ON COLUMN dashboard_activities.actor_user_id IS '活动用户ID';
COMMENT ON COLUMN dashboard_activities.activity_type IS '活动类型';
COMMENT ON COLUMN dashboard_activities.resource_type IS '资源类型';
COMMENT ON COLUMN dashboard_activities.resource_id IS '资源ID';
COMMENT ON COLUMN dashboard_activities.result IS '活动结果';
COMMENT ON COLUMN dashboard_activities.summary IS '活动摘要';
COMMENT ON COLUMN dashboard_activities.created_at IS '创建时间';
CREATE INDEX idx_dashboard_activities_time ON dashboard_activities(activity_time DESC);

-- =========================================================
-- 9. Vector database schema contract
-- =========================================================

-- Milvus Collection: knowledge_chunks
-- OpenSpec: REQ-KB-002, REQ-REPORT-003
-- Embedding model: BGE-M3
-- Vector dimension: 1024
-- Primary key: milvus_primary_key (VARCHAR)
-- Vector field: embedding FLOAT_VECTOR(1024)
-- Scalar fields:
--   chunk_id BIGINT
--   document_id BIGINT
--   knowledge_base_id BIGINT
--   knowledge_entry_id BIGINT
--   content_hash VARCHAR(128)
--   source_type VARCHAR(64)
--   created_at TIMESTAMP
-- Index:
--   vector index: HNSW or IVF_FLAT, metric_type=COSINE
--   scalar index: document_id, knowledge_base_id, knowledge_entry_id

-- OpenSearch index: knowledge_entries_text
-- OpenSpec: REQ-KB-001, REQ-KB-002
-- Fields:
--   entry_id, knowledge_base_id, title, content, tags, source_type, updated_at
-- Purpose:
--   关键词检索、筛选、混合召回，与 Milvus 向量结果合并重排。

-- =========================================================
-- 10. Traceability summary
-- =========================================================

-- report-generation:
--   reports, report_generation_tasks, report_outlines, report_sections,
--   retrieval_sessions, model_invocations, model_responses
-- report-citation-export-version:
--   citation_sources, citation_anchors, citation_quality_scores,
--   report_versions, export_files, report_templates
-- knowledge-base-ingestion:
--   knowledge_bases, knowledge_entries, knowledge_documents,
--   document_parse_results, document_chunks, embeddings,
--   data_source_connections, data_source_sync_tasks
-- rule-engine:
--   rules, rule_nodes, rule_edges, rule_versions,
--   rule_execution_records, rule_node_execution_logs
-- permission-collaboration:
--   users, roles, permissions, user_roles, role_permissions,
--   share_links, share_access_records, annotations, annotation_comments,
--   collaboration_tasks, notifications
-- audit-history-dashboard:
--   operation_logs, operation_log_details, model_invocations,
--   dashboard_metric_snapshots, dashboard_trend_points,
--   dashboard_rank_items, dashboard_activities
-- scope-guardrails:
--   不包含多租户表、不包含报告人工审核发布流表、不包含 HA/DR 运维表。
