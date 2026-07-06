CREATE TABLE IF NOT EXISTS report_annotations (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    created_by BIGINT NOT NULL,
    assignee_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    anchor_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS collaboration_tasks (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    annotation_id BIGINT NOT NULL REFERENCES report_annotations(id),
    assignee_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_annotations_report_id ON report_annotations(report_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_collaboration_tasks_report_id ON collaboration_tasks(report_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_collaboration_tasks_assignee ON collaboration_tasks(assignee_user_id, status, id DESC);
