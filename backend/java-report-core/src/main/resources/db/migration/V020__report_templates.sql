CREATE TABLE IF NOT EXISTS report_templates (
  id BIGSERIAL PRIMARY KEY,
  template_id VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(80) NOT NULL DEFAULT 'general',
  version VARCHAR(40) NOT NULL DEFAULT 'v1',
  status VARCHAR(40) NOT NULL DEFAULT 'active',
  field_schema JSONB NOT NULL DEFAULT '[]'::jsonb,
  outline_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_templates_template_id
ON report_templates(template_id)
WHERE deleted_at IS NULL;

INSERT INTO report_templates (
  template_id,
  name,
  category,
  version,
  status,
  field_schema,
  outline_schema
)
VALUES (
  'enterprise-quarterly',
  '企业季度经营分析报告',
  'operations',
  'v1',
  'active',
  '[
    {"fieldKey":"period","label":"报告周期","type":"text","required":true,"options":[],"defaultValue":"2026Q1","helpText":"例如 2026Q1"},
    {"fieldKey":"scope","label":"对象范围","type":"text","required":true,"options":[],"defaultValue":"集团整体","helpText":"组织、区域或业务线"},
    {"fieldKey":"focus","label":"关注重点","type":"textarea","required":true,"options":[],"defaultValue":null,"helpText":"管理层最关心的问题"},
    {"fieldKey":"style","label":"报告风格","type":"select","required":true,"options":["管理摘要","经营分析","风险提示"],"defaultValue":"管理摘要","helpText":"输出语气"}
  ]'::jsonb,
  '{"sections":["执行摘要","经营表现","风险与建议"]}'::jsonb
)
ON CONFLICT DO NOTHING;
