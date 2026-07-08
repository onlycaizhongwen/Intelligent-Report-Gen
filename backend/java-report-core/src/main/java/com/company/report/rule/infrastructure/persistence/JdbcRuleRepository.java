package com.company.report.rule.infrastructure.persistence;

import com.company.report.rule.domain.model.Rule;
import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.model.RuleApprovalDelegateRule;
import com.company.report.rule.domain.model.RuleApprovalRecord;
import com.company.report.rule.domain.model.RuleApprovalTemplate;
import com.company.report.rule.domain.model.RuleDebugRun;
import com.company.report.rule.domain.model.RuleRunMetrics;
import com.company.report.rule.domain.repository.RuleRepository;
import com.company.report.rule.domain.repository.RuleRepository.ApprovalRecordFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;

@Repository
public class JdbcRuleRepository implements RuleRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRuleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Rule save(Rule rule) {
        if (rule.id() == null || rule.id() <= 0) {
            return insert(rule);
        }
        jdbcTemplate.update("""
                        UPDATE rules
                        SET name = ?, description = ?, status = ?, definition_json = ?::jsonb,
                            current_version_id = ?, schedule_enabled = ?, schedule_interval_seconds = ?,
                            next_run_at = ?, failure_count = ?, max_retry_count = ?, schedule_input_json = ?::jsonb,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                rule.name(),
                rule.description(),
                rule.status(),
                writeJson(rule.definition()),
                rule.currentVersionId(),
                Boolean.TRUE.equals(rule.scheduleEnabled()),
                rule.scheduleIntervalSeconds(),
                timestamp(rule.nextRunAt()),
                rule.failureCount() == null ? 0 : rule.failureCount(),
                rule.maxRetryCount() == null ? 3 : rule.maxRetryCount(),
                writeJson(rule.scheduleInput()),
                rule.id()
        );
        return findById(rule.id()).orElseThrow();
    }

    @Override
    public Optional<Rule> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, name, description, status, definition_json, current_version_id,
                               schedule_enabled, schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        FROM rules
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> mapRule(rs),
                id
        ).stream().findFirst();
    }

    @Override
    public List<Rule> findPage(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, name, description, status, definition_json, current_version_id,
                               schedule_enabled, schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        FROM rules
                        WHERE deleted_at IS NULL
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapRule(rs),
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rules
                        WHERE deleted_at IS NULL
                        """,
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<Rule> findRulesUsingApprovalTemplate(Long templateId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, name, description, status, definition_json, current_version_id,
                               schedule_enabled, schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        FROM rules
                        WHERE deleted_at IS NULL
                          AND EXISTS (
                            SELECT 1
                            FROM jsonb_array_elements(COALESCE(definition_json->'nodes', '[]'::jsonb)) AS node
                            WHERE node->>'approvalTemplateId' = ?
                               OR node->>'id' LIKE ? ESCAPE '\'
                          )
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapRule(rs),
                String.valueOf(templateId),
                "tpl" + templateId + "\\_%",
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countRulesUsingApprovalTemplate(Long templateId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rules
                        WHERE deleted_at IS NULL
                          AND EXISTS (
                            SELECT 1
                            FROM jsonb_array_elements(COALESCE(definition_json->'nodes', '[]'::jsonb)) AS node
                            WHERE node->>'approvalTemplateId' = ?
                               OR node->>'id' LIKE ? ESCAPE '\'
                          )
                        """,
                Long.class,
                String.valueOf(templateId),
                "tpl" + templateId + "\\_%"
        );
        return count == null ? 0L : count;
    }

    @Override
    public Long saveVersion(Long ruleId, Map<String, Object> definition) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_versions(rule_id, definition_json, validation_status, created_at)
                    VALUES (?, ?::jsonb, 'valid', CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, ruleId);
            statement.setString(2, writeJson(definition));
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    @Override
    public RuleDebugRun saveDebugRun(RuleDebugRun debugRun) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_debug_runs(rule_id, version_id, run_type, status, triggered_by_user_id, duration_ms, error_message, input_json, output_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, debugRun.ruleId());
            if (debugRun.versionId() == null || debugRun.versionId() <= 0) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, debugRun.versionId());
            }
            statement.setString(3, debugRun.runType());
            statement.setString(4, debugRun.status());
            statement.setObject(5, debugRun.triggeredByUserId());
            statement.setObject(6, debugRun.durationMs());
            statement.setString(7, debugRun.errorMessage());
            statement.setString(8, writeJson(debugRun.input()));
            statement.setString(9, writeJson(debugRun.output()));
            return statement;
        }, keyHolder);
        return debugRun.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public RuleDebugRun updateDebugRun(RuleDebugRun debugRun) {
        int updated = jdbcTemplate.update("""
                        UPDATE rule_debug_runs
                        SET status = ?, duration_ms = ?, error_message = ?, output_json = ?::jsonb
                        WHERE id = ?
                        """,
                debugRun.status(),
                debugRun.durationMs(),
                debugRun.errorMessage(),
                writeJson(debugRun.output()),
                debugRun.id()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("rule run not found: " + debugRun.id());
        }
        return debugRun;
    }

    @Override
    public Optional<RuleDebugRun> findRunById(Long runId) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, version_id, run_type, status, triggered_by_user_id, duration_ms, error_message, input_json, output_json
                        FROM rule_debug_runs
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapRun(rs),
                runId
        ).stream().findFirst();
    }

    @Override
    public List<RuleDebugRun> findRuns(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, rule_id, version_id, run_type, status, triggered_by_user_id, duration_ms, error_message, input_json, output_json
                        FROM rule_debug_runs
                        WHERE rule_id = ? AND run_type = 'production'
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapRun(rs),
                ruleId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long countRuns(Long ruleId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rule_debug_runs
                        WHERE rule_id = ? AND run_type = 'production'
                        """,
                Long.class,
                ruleId
        );
        return count == null ? 0L : count;
    }

    @Override
    public RuleRunMetrics metrics(Long ruleId) {
        RuleRunMetrics aggregate = jdbcTemplate.queryForObject("""
                        SELECT
                          COUNT(*) AS total_runs,
                          SUM(CASE WHEN status = 'succeeded' THEN 1 ELSE 0 END) AS succeeded_runs,
                          SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_runs,
                          COALESCE(AVG(duration_ms), 0) AS average_duration_ms
                        FROM rule_debug_runs
                        WHERE rule_id = ? AND run_type = 'production'
                        """,
                (rs, rowNum) -> new RuleRunMetrics(
                        rs.getLong("total_runs"),
                        rs.getLong("succeeded_runs"),
                        rs.getLong("failed_runs"),
                        rs.getDouble("average_duration_ms"),
                        null,
                        null
                ),
                ruleId
        );
        if (aggregate == null || aggregate.totalRuns() == 0L) {
            return RuleRunMetrics.empty();
        }
        String lastStatus = jdbcTemplate.query("""
                        SELECT status
                        FROM rule_debug_runs
                        WHERE rule_id = ? AND run_type = 'production'
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("status"),
                ruleId
        ).stream().findFirst().orElse(null);
        String lastErrorMessage = jdbcTemplate.query("""
                        SELECT error_message
                        FROM rule_debug_runs
                        WHERE rule_id = ? AND run_type = 'production' AND error_message IS NOT NULL AND error_message <> ''
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("error_message"),
                ruleId
        ).stream().findFirst().orElse(null);
        return new RuleRunMetrics(
                aggregate.totalRuns(),
                aggregate.succeededRuns(),
                aggregate.failedRuns(),
                aggregate.averageDurationMs(),
                lastStatus,
                lastErrorMessage
        );
    }

    @Override
    public RuleActionExecution saveActionExecution(RuleActionExecution execution) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_action_executions(
                        rule_id, run_id, node_id, action_type, status, attempt, max_retry_count,
                        endpoint, idempotency_key, next_retry_at, error_message, metadata_json, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, execution.ruleId());
            statement.setLong(2, execution.runId());
            statement.setString(3, execution.nodeId());
            statement.setString(4, execution.actionType());
            statement.setString(5, execution.status());
            statement.setObject(6, execution.attempt());
            statement.setObject(7, execution.maxRetryCount());
            statement.setString(8, execution.endpoint());
            statement.setString(9, execution.idempotencyKey());
            statement.setTimestamp(10, timestamp(execution.nextRetryAt()));
            statement.setString(11, execution.errorMessage());
            statement.setString(12, writeJson(execution.metadata()));
            return statement;
        }, keyHolder);
        return execution.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public RuleApprovalRecord saveApprovalRecord(RuleApprovalRecord approvalRecord) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_approval_records(
                        rule_id, run_id, node_id, assignee_role, delegate_role, approval_title, status,
                        created_by_user_id, approved_by_user_id, approval_comment, approved_at, created_at,
                        sla_hours, remind_count, last_reminded_at, delegate_active_from, delegate_active_to
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, approvalRecord.ruleId());
            statement.setLong(2, approvalRecord.runId());
            statement.setString(3, approvalRecord.nodeId());
            statement.setString(4, approvalRecord.assigneeRole());
            statement.setString(5, approvalRecord.delegateRole());
            statement.setString(6, approvalRecord.approvalTitle());
            statement.setString(7, approvalRecord.status());
            statement.setObject(8, approvalRecord.createdByUserId());
            statement.setObject(9, approvalRecord.approvedByUserId());
            statement.setString(10, approvalRecord.approvalComment());
            statement.setTimestamp(11, timestamp(approvalRecord.approvedAt()));
            statement.setTimestamp(12, timestamp(approvalRecord.createdAt()));
            statement.setObject(13, approvalRecord.slaHours());
            statement.setObject(14, approvalRecord.remindCount() == null ? 0 : approvalRecord.remindCount());
            statement.setTimestamp(15, timestamp(approvalRecord.lastRemindedAt()));
            statement.setTimestamp(16, timestamp(approvalRecord.delegateActiveFrom()));
            statement.setTimestamp(17, timestamp(approvalRecord.delegateActiveTo()));
            return statement;
        }, keyHolder);
        return approvalRecord.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public RuleApprovalDelegateRule saveApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_approval_delegate_rules(
                        assignee_role, delegate_role, active_from, active_to, active_weekdays, active_dates, status, reason, created_by_user_id, created_at
                    )
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, delegateRule.assigneeRole());
            statement.setString(2, delegateRule.delegateRole());
            statement.setTimestamp(3, timestamp(delegateRule.activeFrom()));
            statement.setTimestamp(4, timestamp(delegateRule.activeTo()));
            statement.setString(5, writeStringListJson(delegateRule.activeWeekdays()));
            statement.setString(6, writeStringListJson(delegateRule.activeDates()));
            statement.setString(7, delegateRule.status());
            statement.setString(8, delegateRule.reason());
            statement.setObject(9, delegateRule.createdByUserId());
            statement.setTimestamp(10, timestamp(delegateRule.createdAt()));
            return statement;
        }, keyHolder);
        return delegateRule.withId(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    @Override
    public Optional<RuleApprovalDelegateRule> findActiveApprovalDelegateRule(String assigneeRole, OffsetDateTime now) {
        if (assigneeRole == null || assigneeRole.isBlank()) {
            return Optional.empty();
        }
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now() : now;
        return jdbcTemplate.query("""
                        SELECT id, assignee_role, delegate_role, active_from, active_to, active_weekdays, active_dates, status, reason, created_by_user_id, created_at
                        FROM rule_approval_delegate_rules
                        WHERE assignee_role = ?
                          AND status = 'enabled'
                          AND (active_from IS NULL OR active_from <= ?)
                          AND (active_to IS NULL OR active_to >= ?)
                          AND (
                            active_weekdays IS NULL
                            OR active_weekdays = '[]'::jsonb
                            OR jsonb_exists(active_weekdays, ?)
                          )
                          AND (
                            active_dates IS NULL
                            OR active_dates = '[]'::jsonb
                            OR jsonb_exists(active_dates, ?)
                          )
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapApprovalDelegateRule(rs),
                assigneeRole.trim(),
                timestamp(effectiveNow),
                timestamp(effectiveNow),
                effectiveNow.getDayOfWeek().name(),
                effectiveNow.toLocalDate().toString()
        ).stream().findFirst();
    }

    @Override
    public Optional<RuleApprovalDelegateRule> findApprovalDelegateRuleById(Long delegateRuleId) {
        return jdbcTemplate.query("""
                        SELECT id, assignee_role, delegate_role, active_from, active_to, active_weekdays, active_dates, status, reason, created_by_user_id, created_at
                        FROM rule_approval_delegate_rules
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapApprovalDelegateRule(rs),
                delegateRuleId
        ).stream().findFirst();
    }

    @Override
    public List<RuleApprovalDelegateRule> findApprovalDelegateRules(String status, String assigneeRole, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        StringBuilder sql = new StringBuilder("""
                        SELECT id, assignee_role, delegate_role, active_from, active_to, active_weekdays, active_dates, status, reason, created_by_user_id, created_at
                        FROM rule_approval_delegate_rules
                        WHERE 1 = 1
                        """);
        List<Object> args = new ArrayList<>();
        appendApprovalDelegateRuleFilter(sql, args, status, assigneeRole);
        sql.append("""
                        
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """);
        args.add(safePageSize);
        args.add((safePage - 1) * safePageSize);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapApprovalDelegateRule(rs), args.toArray());
    }

    @Override
    public List<RuleApprovalDelegateRule> findEnabledApprovalDelegateRulesByAssigneeRole(String assigneeRole) {
        if (assigneeRole == null || assigneeRole.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                        SELECT id, assignee_role, delegate_role, active_from, active_to, active_weekdays, active_dates, status, reason, created_by_user_id, created_at
                        FROM rule_approval_delegate_rules
                        WHERE assignee_role = ?
                          AND status = 'enabled'
                        ORDER BY id DESC
                        """,
                (rs, rowNum) -> mapApprovalDelegateRule(rs),
                assigneeRole.trim()
        );
    }

    @Override
    public long countApprovalDelegateRules(String status, String assigneeRole) {
        StringBuilder sql = new StringBuilder("""
                        SELECT COUNT(*)
                        FROM rule_approval_delegate_rules
                        WHERE 1 = 1
                        """);
        List<Object> args = new ArrayList<>();
        appendApprovalDelegateRuleFilter(sql, args, status, assigneeRole);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public RuleApprovalDelegateRule updateApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        jdbcTemplate.update("""
                        UPDATE rule_approval_delegate_rules
                        SET assignee_role = ?, delegate_role = ?, active_from = ?, active_to = ?, active_weekdays = ?::jsonb,
                            active_dates = ?::jsonb,
                            status = ?, reason = ?
                        WHERE id = ?
                        """,
                delegateRule.assigneeRole(),
                delegateRule.delegateRole(),
                timestamp(delegateRule.activeFrom()),
                timestamp(delegateRule.activeTo()),
                writeStringListJson(delegateRule.activeWeekdays()),
                writeStringListJson(delegateRule.activeDates()),
                delegateRule.status(),
                delegateRule.reason(),
                delegateRule.id()
        );
        return findApprovalDelegateRuleById(delegateRule.id()).orElseThrow();
    }

    @Override
    public RuleApprovalTemplate saveApprovalTemplate(RuleApprovalTemplate template) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_approval_templates(
                        name, description, status, version, steps_json, created_by_user_id, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, template.name());
            statement.setString(2, template.description());
            statement.setString(3, template.status());
            statement.setObject(4, template.version() == null ? 1 : template.version());
            statement.setString(5, writeMapListJson(template.steps()));
            statement.setObject(6, template.createdByUserId());
            statement.setTimestamp(7, timestamp(template.createdAt()));
            statement.setTimestamp(8, timestamp(template.updatedAt()));
            return statement;
        }, keyHolder);
        RuleApprovalTemplate saved = findApprovalTemplateById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
        upsertApprovalTemplateVersion(saved);
        return saved;
    }

    @Override
    public Optional<RuleApprovalTemplate> findApprovalTemplateById(Long templateId) {
        return jdbcTemplate.query("""
                        SELECT id, name, description, status, version, steps_json, created_by_user_id, created_at, updated_at
                        FROM rule_approval_templates
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapApprovalTemplate(rs),
                templateId
        ).stream().findFirst();
    }

    @Override
    public Optional<RuleApprovalTemplate> findApprovalTemplateVersion(Long templateId, int version) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, description, status, version, steps_json, created_by_user_id, created_at, updated_at
                        FROM rule_approval_template_versions
                        WHERE template_id = ? AND version = ?
                        """,
                (rs, rowNum) -> mapApprovalTemplateVersion(rs),
                templateId,
                version
        ).stream().findFirst();
    }

    @Override
    public List<RuleApprovalTemplate> findApprovalTemplateVersions(Long templateId) {
        return jdbcTemplate.query("""
                        SELECT id, template_id, name, description, status, version, steps_json, created_by_user_id, created_at, updated_at
                        FROM rule_approval_template_versions
                        WHERE template_id = ?
                        ORDER BY version DESC
                        """,
                (rs, rowNum) -> mapApprovalTemplateVersion(rs),
                templateId
        );
    }

    @Override
    public RuleApprovalTemplate updateApprovalTemplate(RuleApprovalTemplate template) {
        jdbcTemplate.update("""
                        UPDATE rule_approval_templates
                        SET name = ?, description = ?, status = ?, version = ?, steps_json = ?::jsonb, updated_at = ?
                        WHERE id = ?
                        """,
                template.name(),
                template.description(),
                template.status(),
                template.version() == null ? 1 : template.version(),
                writeMapListJson(template.steps()),
                timestamp(template.updatedAt()),
                template.id()
        );
        RuleApprovalTemplate updated = findApprovalTemplateById(template.id()).orElseThrow();
        upsertApprovalTemplateVersion(updated);
        return updated;
    }

    private void upsertApprovalTemplateVersion(RuleApprovalTemplate template) {
        jdbcTemplate.update("""
                        INSERT INTO rule_approval_template_versions(
                            template_id, version, name, description, status, steps_json, created_by_user_id, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                        ON CONFLICT (template_id, version) DO NOTHING
                        """,
                template.id(),
                template.version() == null ? 1 : template.version(),
                template.name(),
                template.description(),
                template.status(),
                writeMapListJson(template.steps()),
                template.createdByUserId(),
                timestamp(template.createdAt()),
                timestamp(template.updatedAt())
        );
    }

    @Override
    public List<RuleApprovalTemplate> findApprovalTemplates(String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        StringBuilder sql = new StringBuilder("""
                        SELECT id, name, description, status, version, steps_json, created_by_user_id, created_at, updated_at
                        FROM rule_approval_templates
                        WHERE 1 = 1
                        """);
        List<Object> args = new ArrayList<>();
        appendApprovalTemplateFilter(sql, args, status);
        sql.append("""
                        
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """);
        args.add(safePageSize);
        args.add((safePage - 1) * safePageSize);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapApprovalTemplate(rs), args.toArray());
    }

    @Override
    public long countApprovalTemplates(String status) {
        StringBuilder sql = new StringBuilder("""
                        SELECT COUNT(*)
                        FROM rule_approval_templates
                        WHERE 1 = 1
                        """);
        List<Object> args = new ArrayList<>();
        appendApprovalTemplateFilter(sql, args, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public Optional<RuleApprovalRecord> findApprovalRecordById(Long approvalRecordId) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, run_id, node_id, assignee_role, delegate_role, approval_title, status,
                               created_by_user_id, approved_by_user_id, approval_comment, approved_at, created_at,
                               sla_hours, remind_count, last_reminded_at, delegate_active_from, delegate_active_to
                        FROM rule_approval_records
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapApprovalRecord(rs),
                approvalRecordId
        ).stream().findFirst();
    }

    @Override
    public RuleApprovalRecord updateApprovalRecord(RuleApprovalRecord approvalRecord) {
        jdbcTemplate.update("""
                        UPDATE rule_approval_records
                        SET status = ?, approved_by_user_id = ?, approval_comment = ?, approved_at = ?,
                            sla_hours = ?, remind_count = ?, last_reminded_at = ?, delegate_role = ?,
                            delegate_active_from = ?, delegate_active_to = ?
                        WHERE id = ?
                        """,
                approvalRecord.status(),
                approvalRecord.approvedByUserId(),
                approvalRecord.approvalComment(),
                timestamp(approvalRecord.approvedAt()),
                approvalRecord.slaHours(),
                approvalRecord.remindCount(),
                timestamp(approvalRecord.lastRemindedAt()),
                approvalRecord.delegateRole(),
                timestamp(approvalRecord.delegateActiveFrom()),
                timestamp(approvalRecord.delegateActiveTo()),
                approvalRecord.id()
        );
        return findApprovalRecordById(approvalRecord.id()).orElseThrow();
    }

    @Override
    public List<RuleApprovalRecord> findApprovalRecords(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, rule_id, run_id, node_id, assignee_role, delegate_role, approval_title, status,
                               created_by_user_id, approved_by_user_id, approval_comment, approved_at, created_at,
                               sla_hours, remind_count, last_reminded_at, delegate_active_from, delegate_active_to
                        FROM rule_approval_records
                        WHERE rule_id = ?
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapApprovalRecord(rs),
                ruleId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public List<RuleApprovalRecord> findApprovalRecordsByStatus(String status, ApprovalRecordFilter filter, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        StringBuilder sql = new StringBuilder("""
                        SELECT id, rule_id, run_id, node_id, assignee_role, delegate_role, approval_title, status,
                               created_by_user_id, approved_by_user_id, approval_comment, approved_at, created_at,
                               sla_hours, remind_count, last_reminded_at, delegate_active_from, delegate_active_to
                        FROM rule_approval_records
                        WHERE status = ?
                        """);
        List<Object> args = new ArrayList<>();
        args.add(status);
        appendApprovalRecordFilter(sql, args, filter);
        sql.append("""
                        
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """);
        args.add(safePageSize);
        args.add((safePage - 1) * safePageSize);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapApprovalRecord(rs), args.toArray());
    }

    @Override
    public long countApprovalRecords(Long ruleId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rule_approval_records
                        WHERE rule_id = ?
                        """,
                Long.class,
                ruleId
        );
        return count == null ? 0L : count;
    }

    @Override
    public long countApprovalRecordsByStatus(String status, ApprovalRecordFilter filter) {
        StringBuilder sql = new StringBuilder("""
                        SELECT COUNT(*)
                        FROM rule_approval_records
                        WHERE status = ?
                        """);
        List<Object> args = new ArrayList<>();
        args.add(status);
        appendApprovalRecordFilter(sql, args, filter);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private void appendApprovalRecordFilter(StringBuilder sql, List<Object> args, ApprovalRecordFilter filter) {
        ApprovalRecordFilter safeFilter = filter == null ? ApprovalRecordFilter.empty() : filter;
        if (safeFilter.ruleId() != null) {
            sql.append(" AND rule_id = ?");
            args.add(safeFilter.ruleId());
        }
        if (safeFilter.assigneeRole() != null && !safeFilter.assigneeRole().isBlank()) {
            sql.append(" AND assignee_role = ?");
            args.add(safeFilter.assigneeRole());
        }
        if (safeFilter.visibleRoles() != null && !safeFilter.visibleRoles().isEmpty()) {
            String placeholders = safeFilter.visibleRoles().stream()
                    .map(role -> "?")
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(" AND (assignee_role IN (")
                    .append(placeholders)
                    .append(") OR (delegate_role IN (")
                    .append(placeholders)
                    .append(") AND (delegate_active_from IS NULL OR delegate_active_from <= ?) AND (delegate_active_to IS NULL OR delegate_active_to >= ?)))");
            args.addAll(safeFilter.visibleRoles());
            args.addAll(safeFilter.visibleRoles());
            OffsetDateTime visibilityAt = safeFilter.visibilityAt() == null ? OffsetDateTime.now() : safeFilter.visibilityAt();
            args.add(timestamp(visibilityAt));
            args.add(timestamp(visibilityAt));
        }
        if (safeFilter.approvalTitle() != null && !safeFilter.approvalTitle().isBlank()) {
            sql.append(" AND LOWER(approval_title) LIKE ?");
            args.add("%" + safeFilter.approvalTitle().toLowerCase() + "%");
        }
        if (safeFilter.createdByUserId() != null) {
            sql.append(" AND created_by_user_id = ?");
            args.add(safeFilter.createdByUserId());
        }
        if (safeFilter.approvedByUserId() != null) {
            sql.append(" AND approved_by_user_id = ?");
            args.add(safeFilter.approvedByUserId());
        }
        if (safeFilter.createdAtFrom() != null) {
            sql.append(" AND created_at >= ?");
            args.add(timestamp(safeFilter.createdAtFrom()));
        }
        if (safeFilter.createdAtTo() != null) {
            sql.append(" AND created_at <= ?");
            args.add(timestamp(safeFilter.createdAtTo()));
        }
    }

    private void appendApprovalDelegateRuleFilter(StringBuilder sql, List<Object> args, String status, String assigneeRole) {
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        if (assigneeRole != null && !assigneeRole.isBlank()) {
            sql.append(" AND assignee_role = ?");
            args.add(assigneeRole.trim());
        }
    }

    private void appendApprovalTemplateFilter(StringBuilder sql, List<Object> args, String status) {
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
    }

    @Override
    public List<RuleActionExecution> findActionExecutions(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, rule_id, run_id, node_id, action_type, status, attempt, max_retry_count,
                               endpoint, idempotency_key, next_retry_at, error_message, metadata_json, created_at
                        FROM rule_action_executions
                        WHERE rule_id = ?
                        ORDER BY id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapActionExecution(rs),
                ruleId,
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public Optional<RuleActionExecution> findActionExecutionById(Long actionExecutionId) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, run_id, node_id, action_type, status, attempt, max_retry_count,
                               endpoint, idempotency_key, next_retry_at, error_message, metadata_json, created_at
                        FROM rule_action_executions
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapActionExecution(rs),
                actionExecutionId
        ).stream().findFirst();
    }

    @Override
    public long countActionExecutions(Long ruleId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rule_action_executions
                        WHERE rule_id = ?
                        """,
                Long.class,
                ruleId
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<RuleActionExecution> findDueWebhookActionExecutions(OffsetDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, run_id, node_id, action_type, status, attempt, max_retry_count,
                               endpoint, idempotency_key, next_retry_at, error_message, metadata_json, created_at
                        FROM rule_action_executions
                        WHERE action_type = 'webhook'
                          AND status IN ('pending_retry', 'failed')
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                          AND (replay_locked_until IS NULL OR replay_locked_until <= CURRENT_TIMESTAMP)
                          AND COALESCE(NULLIF(metadata_json ->> 'maxAsyncReplayAttempts', '')::integer, 3) > 0
                          AND id = (
                            SELECT MAX(latest.id)
                            FROM rule_action_executions latest
                            WHERE latest.idempotency_key = rule_action_executions.idempotency_key
                          )
                        ORDER BY next_retry_at ASC NULLS FIRST, id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> mapActionExecution(rs),
                timestamp(now),
                Math.max(limit, 1)
        );
    }

    @Override
    public boolean tryAcquireActionExecutionLease(Long actionExecutionId, OffsetDateTime lockedUntil) {
        return jdbcTemplate.update("""
                        UPDATE rule_action_executions
                        SET replay_locked_until = ?
                        WHERE id = ?
                          AND (replay_locked_until IS NULL OR replay_locked_until <= CURRENT_TIMESTAMP)
                        """,
                timestamp(lockedUntil),
                actionExecutionId
        ) > 0;
    }

    @Override
    public void releaseActionExecutionLease(Long actionExecutionId) {
        jdbcTemplate.update("""
                        UPDATE rule_action_executions
                        SET replay_locked_until = NULL
                        WHERE id = ?
                        """,
                actionExecutionId
        );
    }

    @Override
    public List<Rule> findDueScheduledRules(OffsetDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, name, description, status, definition_json, current_version_id,
                               schedule_enabled, schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json
                        FROM rules
                        WHERE deleted_at IS NULL
                          AND schedule_enabled = TRUE
                          AND status = 'published'
                          AND next_run_at IS NOT NULL
                          AND next_run_at <= ?
                          AND (schedule_locked_until IS NULL OR schedule_locked_until <= CURRENT_TIMESTAMP)
                        ORDER BY next_run_at ASC, id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> mapRule(rs),
                timestamp(now),
                Math.max(limit, 1)
        );
    }

    @Override
    public boolean tryAcquireRuleScheduleLease(Long ruleId, OffsetDateTime lockedUntil) {
        return jdbcTemplate.update("""
                        UPDATE rules
                        SET schedule_locked_until = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND deleted_at IS NULL
                          AND (schedule_locked_until IS NULL OR schedule_locked_until <= CURRENT_TIMESTAMP)
                        """,
                timestamp(lockedUntil),
                ruleId
        ) > 0;
    }

    @Override
    public void releaseRuleScheduleLease(Long ruleId) {
        jdbcTemplate.update("""
                        UPDATE rules
                        SET schedule_locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                ruleId
        );
    }

    @Override
    public Rule updateScheduleState(Long ruleId, OffsetDateTime nextRunAt, int failureCount) {
        jdbcTemplate.update("""
                        UPDATE rules
                        SET next_run_at = ?, failure_count = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                timestamp(nextRunAt),
                failureCount,
                ruleId
        );
        return findById(ruleId).orElseThrow();
    }

    private Rule insert(Rule rule) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rules(name, description, status, definition_json, current_version_id,
                                      schedule_enabled, schedule_interval_seconds, next_run_at, failure_count, max_retry_count, schedule_input_json,
                                      created_at, updated_at)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setString(1, rule.name());
            statement.setString(2, rule.description());
            statement.setString(3, rule.status());
            statement.setString(4, writeJson(rule.definition()));
            if (rule.currentVersionId() == null || rule.currentVersionId() <= 0) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, rule.currentVersionId());
            }
            statement.setBoolean(6, Boolean.TRUE.equals(rule.scheduleEnabled()));
            statement.setObject(7, rule.scheduleIntervalSeconds());
            statement.setTimestamp(8, timestamp(rule.nextRunAt()));
            statement.setInt(9, rule.failureCount() == null ? 0 : rule.failureCount());
            statement.setInt(10, rule.maxRetryCount() == null ? 3 : rule.maxRetryCount());
            statement.setString(11, writeJson(rule.scheduleInput()));
            return statement;
        }, keyHolder);
        return findById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
    }

    private Rule mapRule(ResultSet rs) throws SQLException {
        Long currentVersionId = rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id");
        Timestamp nextRunAt = rs.getTimestamp("next_run_at");
        return new Rule(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                readJson(rs.getString("definition_json")),
                currentVersionId,
                rs.getBoolean("schedule_enabled"),
                rs.getObject("schedule_interval_seconds") == null ? null : rs.getInt("schedule_interval_seconds"),
                nextRunAt == null ? null : nextRunAt.toInstant().atOffset(ZoneOffset.UTC),
                rs.getObject("failure_count") == null ? 0 : rs.getInt("failure_count"),
                rs.getObject("max_retry_count") == null ? 3 : rs.getInt("max_retry_count"),
                readJson(rs.getString("schedule_input_json"))
        );
    }

    private RuleApprovalRecord mapApprovalRecord(ResultSet rs) throws SQLException {
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp lastRemindedAt = rs.getTimestamp("last_reminded_at");
        Timestamp delegateActiveFrom = rs.getTimestamp("delegate_active_from");
        Timestamp delegateActiveTo = rs.getTimestamp("delegate_active_to");
        return new RuleApprovalRecord(
                rs.getLong("id"),
                rs.getLong("rule_id"),
                rs.getLong("run_id"),
                rs.getString("node_id"),
                rs.getString("assignee_role"),
                rs.getString("delegate_role"),
                delegateActiveFrom == null ? null : delegateActiveFrom.toInstant().atOffset(ZoneOffset.UTC),
                delegateActiveTo == null ? null : delegateActiveTo.toInstant().atOffset(ZoneOffset.UTC),
                rs.getString("approval_title"),
                rs.getString("status"),
                rs.getObject("created_by_user_id") == null ? null : rs.getLong("created_by_user_id"),
                rs.getObject("approved_by_user_id") == null ? null : rs.getLong("approved_by_user_id"),
                rs.getString("approval_comment"),
                approvedAt == null ? null : approvedAt.toInstant().atOffset(ZoneOffset.UTC),
                createdAt == null ? null : createdAt.toInstant().atOffset(ZoneOffset.UTC),
                rs.getObject("sla_hours") == null ? null : rs.getInt("sla_hours"),
                rs.getObject("remind_count") == null ? 0 : rs.getInt("remind_count"),
                lastRemindedAt == null ? null : lastRemindedAt.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private RuleApprovalDelegateRule mapApprovalDelegateRule(ResultSet rs) throws SQLException {
        Timestamp activeFrom = rs.getTimestamp("active_from");
        Timestamp activeTo = rs.getTimestamp("active_to");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new RuleApprovalDelegateRule(
                rs.getLong("id"),
                rs.getString("assignee_role"),
                rs.getString("delegate_role"),
                activeFrom == null ? null : activeFrom.toInstant().atOffset(ZoneOffset.UTC),
                activeTo == null ? null : activeTo.toInstant().atOffset(ZoneOffset.UTC),
                readStringListJson(rs.getString("active_weekdays")),
                readStringListJson(rs.getString("active_dates")),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getObject("created_by_user_id") == null ? null : rs.getLong("created_by_user_id"),
                createdAt == null ? null : createdAt.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private RuleApprovalTemplate mapApprovalTemplate(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new RuleApprovalTemplate(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("version") == null ? 1 : rs.getInt("version"),
                readMapListJson(rs.getString("steps_json")),
                rs.getObject("created_by_user_id") == null ? null : rs.getLong("created_by_user_id"),
                createdAt == null ? null : createdAt.toInstant().atOffset(ZoneOffset.UTC),
                updatedAt == null ? null : updatedAt.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private RuleApprovalTemplate mapApprovalTemplateVersion(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new RuleApprovalTemplate(
                rs.getLong("template_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("version") == null ? 1 : rs.getInt("version"),
                readMapListJson(rs.getString("steps_json")),
                rs.getObject("created_by_user_id") == null ? null : rs.getLong("created_by_user_id"),
                createdAt == null ? null : createdAt.toInstant().atOffset(ZoneOffset.UTC),
                updatedAt == null ? null : updatedAt.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private RuleDebugRun mapRun(ResultSet rs) throws SQLException {
        Long versionId = rs.getObject("version_id") == null ? null : rs.getLong("version_id");
        return new RuleDebugRun(
                rs.getLong("id"),
                rs.getLong("rule_id"),
                versionId,
                rs.getString("run_type"),
                rs.getString("status"),
                rs.getObject("triggered_by_user_id") == null ? null : rs.getLong("triggered_by_user_id"),
                rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"),
                rs.getString("error_message"),
                readJson(rs.getString("input_json")),
                readJson(rs.getString("output_json"))
        );
    }

    private RuleActionExecution mapActionExecution(ResultSet rs) throws SQLException {
        Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new RuleActionExecution(
                rs.getLong("id"),
                rs.getLong("rule_id"),
                rs.getLong("run_id"),
                rs.getString("node_id"),
                rs.getString("action_type"),
                rs.getString("status"),
                rs.getObject("attempt") == null ? null : rs.getInt("attempt"),
                rs.getObject("max_retry_count") == null ? null : rs.getInt("max_retry_count"),
                rs.getString("endpoint"),
                rs.getString("idempotency_key"),
                nextRetryAt == null ? null : nextRetryAt.toInstant().atOffset(ZoneOffset.UTC),
                rs.getString("error_message"),
                readJson(rs.getString("metadata_json")),
                createdAt == null ? null : createdAt.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private Map<String, Object> readJson(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read rule json", e);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to write rule json", e);
        }
    }

    private List<String> readStringListJson(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read rule string list json", e);
        }
    }

    private String writeStringListJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to write rule string list json", e);
        }
    }

    private List<Map<String, Object>> readMapListJson(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, MAP_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read rule map list json", e);
        }
    }

    private String writeMapListJson(List<Map<String, Object>> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to write rule map list json", e);
        }
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }
}
