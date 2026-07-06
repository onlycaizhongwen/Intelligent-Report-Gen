-- Skill: S10 migration.gen
-- Migration: V001__rollback.sql
-- Database: PostgreSQL 16
-- Purpose: rollback initial local/dev schema only. Do not run in production without explicit DBA approval.
-- OpenSpec: technical support rollback for S9 schema validation.

DROP TABLE IF EXISTS dashboard_activities;
DROP TABLE IF EXISTS dashboard_rank_items;
DROP TABLE IF EXISTS dashboard_trend_points;
DROP TABLE IF EXISTS dashboard_metric_snapshots;
DROP TABLE IF EXISTS operation_log_details;
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS collaboration_tasks;
DROP TABLE IF EXISTS annotation_comments;
DROP TABLE IF EXISTS annotations;
DROP TABLE IF EXISTS share_access_records;
DROP TABLE IF EXISTS share_links;
DROP TABLE IF EXISTS rule_node_execution_logs;
DROP TABLE IF EXISTS rule_execution_records;
DROP TABLE IF EXISTS rule_versions;
DROP TABLE IF EXISTS rule_edges;
DROP TABLE IF EXISTS rule_nodes;
DROP TABLE IF EXISTS rules;
DROP TABLE IF EXISTS model_responses;
DROP TABLE IF EXISTS model_invocations;
DROP TABLE IF EXISTS prompt_templates;
DROP TABLE IF EXISTS context_segments;
DROP TABLE IF EXISTS retrieval_results;
DROP TABLE IF EXISTS retrieval_sessions;
DROP TABLE IF EXISTS data_source_sync_tasks;
DROP TABLE IF EXISTS data_source_connections;
DROP TABLE IF EXISTS embeddings;
DROP TABLE IF EXISTS document_chunks;
DROP TABLE IF EXISTS document_parse_results;
DROP TABLE IF EXISTS knowledge_documents;
DROP TABLE IF EXISTS knowledge_entries;
DROP TABLE IF EXISTS knowledge_bases;
DROP TABLE IF EXISTS export_files;
DROP TABLE IF EXISTS citation_quality_scores;
DROP TABLE IF EXISTS citation_anchors;
DROP TABLE IF EXISTS citation_sources;
DROP TABLE IF EXISTS report_templates;
DROP TABLE IF EXISTS report_versions;
DROP TABLE IF EXISTS report_sections;
DROP TABLE IF EXISTS report_outlines;
DROP TABLE IF EXISTS report_generation_tasks;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS file_objects;
