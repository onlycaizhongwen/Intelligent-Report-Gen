package com.company.report.permission.infrastructure.persistence;

import com.company.report.permission.domain.model.OrganizationPosition;
import com.company.report.permission.domain.model.OrganizationPositionAssignment;
import com.company.report.permission.domain.model.OrganizationUnit;
import com.company.report.permission.domain.repository.OrganizationDirectoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcOrganizationDirectoryRepository implements OrganizationDirectoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationDirectoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OrganizationUnit> findEnabledUnits() {
        return jdbcTemplate.query("""
                        SELECT id, code, name, parent_id, unit_type, status, sort_order
                        FROM organization_units
                        WHERE status = 'enabled'
                        ORDER BY sort_order ASC, id ASC
                        """,
                (rs, rowNum) -> mapUnit(rs)
        );
    }

    @Override
    public List<OrganizationPosition> findEnabledPositions() {
        return jdbcTemplate.query("""
                        SELECT id, organization_unit_id, code, name, roles, manager_user_id, status, sort_order
                        FROM organization_positions
                        WHERE status = 'enabled'
                        ORDER BY sort_order ASC, id ASC
                        """,
                (rs, rowNum) -> mapPosition(rs)
        );
    }

    @Override
    public List<OrganizationPositionAssignment> findEnabledPositionAssignments() {
        return jdbcTemplate.query("""
                        SELECT id, user_id, position_id, primary_position, status, active_from, active_to
                        FROM organization_position_assignments
                        WHERE status = 'enabled'
                        ORDER BY primary_position DESC, id ASC
                        """,
                (rs, rowNum) -> mapAssignment(rs)
        );
    }

    @Override
    public Optional<OrganizationPositionAssignment> findPositionAssignmentById(Long assignmentId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, position_id, primary_position, status, active_from, active_to
                        FROM organization_position_assignments
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapAssignment(rs),
                assignmentId
        ).stream().findFirst();
    }

    @Override
    public Optional<OrganizationUnit> findUnitById(Long unitId) {
        return jdbcTemplate.query("""
                        SELECT id, code, name, parent_id, unit_type, status, sort_order
                        FROM organization_units
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapUnit(rs),
                unitId
        ).stream().findFirst();
    }

    @Override
    public OrganizationUnit saveUnit(OrganizationUnit unit) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organization_units(code, name, parent_id, unit_type, status, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setString(1, unit.code());
            statement.setString(2, unit.name());
            if (unit.parentId() == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, unit.parentId());
            }
            statement.setString(4, unit.unitType());
            statement.setString(5, unit.status());
            statement.setInt(6, unit.sortOrder());
            return statement;
        }, keyHolder);
        return new OrganizationUnit(
                Objects.requireNonNull(keyHolder.getKey()).longValue(),
                unit.code(),
                unit.name(),
                unit.parentId(),
                unit.unitType(),
                unit.status(),
                unit.sortOrder()
        );
    }

    @Override
    public OrganizationUnit updateUnit(OrganizationUnit unit) {
        jdbcTemplate.update("""
                        UPDATE organization_units
                        SET name = ?, parent_id = ?, unit_type = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                unit.name(),
                unit.parentId(),
                unit.unitType(),
                unit.sortOrder(),
                unit.id()
        );
        return findUnitById(unit.id()).orElseThrow();
    }

    @Override
    public OrganizationPosition savePosition(OrganizationPosition position) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organization_positions(
                        organization_unit_id, code, name, roles, manager_user_id, status, sort_order, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, position.organizationUnitId());
            statement.setString(2, position.code());
            statement.setString(3, position.name());
            statement.setArray(4, connection.createArrayOf("text", position.roles().toArray(String[]::new)));
            if (position.managerUserId() == null) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, position.managerUserId());
            }
            statement.setString(6, position.status());
            statement.setInt(7, position.sortOrder());
            return statement;
        }, keyHolder);
        return new OrganizationPosition(
                Objects.requireNonNull(keyHolder.getKey()).longValue(),
                position.organizationUnitId(),
                position.code(),
                position.name(),
                position.roles(),
                position.managerUserId(),
                position.status(),
                position.sortOrder()
        );
    }

    @Override
    public OrganizationPositionAssignment savePositionAssignment(OrganizationPositionAssignment assignment) {
        if (assignment.primary()) {
            jdbcTemplate.update("""
                            UPDATE organization_position_assignments
                            SET primary_position = FALSE, updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ? AND status = 'enabled'
                            """,
                    assignment.userId()
            );
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organization_position_assignments(
                        user_id, position_id, primary_position, status, active_from, active_to, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, assignment.userId());
            statement.setLong(2, assignment.positionId());
            statement.setBoolean(3, assignment.primary());
            statement.setString(4, assignment.status());
            statement.setObject(5, assignment.activeFrom());
            statement.setObject(6, assignment.activeTo());
            return statement;
        }, keyHolder);
        return new OrganizationPositionAssignment(
                Objects.requireNonNull(keyHolder.getKey()).longValue(),
                assignment.userId(),
                assignment.positionId(),
                assignment.primary(),
                assignment.status(),
                assignment.activeFrom(),
                assignment.activeTo()
        );
    }

    @Override
    public OrganizationPositionAssignment disablePositionAssignment(Long assignmentId) {
        jdbcTemplate.update("""
                        UPDATE organization_position_assignments
                        SET status = 'disabled', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                assignmentId
        );
        return findPositionAssignmentById(assignmentId).orElseThrow();
    }

    @Override
    public OrganizationPositionAssignment updatePositionAssignment(OrganizationPositionAssignment assignment) {
        if (assignment.primary()) {
            jdbcTemplate.update("""
                            UPDATE organization_position_assignments
                            SET primary_position = FALSE, updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ? AND status = 'enabled' AND id <> ?
                            """,
                    assignment.userId(),
                    assignment.id()
            );
        }
        jdbcTemplate.update("""
                        UPDATE organization_position_assignments
                        SET primary_position = ?, active_from = ?, active_to = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                assignment.primary(),
                assignment.activeFrom(),
                assignment.activeTo(),
                assignment.id()
        );
        return findPositionAssignmentById(assignment.id()).orElseThrow();
    }

    private OrganizationUnit mapUnit(ResultSet rs) throws SQLException {
        Object parentId = rs.getObject("parent_id");
        return new OrganizationUnit(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                parentId == null ? null : ((Number) parentId).longValue(),
                rs.getString("unit_type"),
                rs.getString("status"),
                rs.getInt("sort_order")
        );
    }

    private OrganizationPosition mapPosition(ResultSet rs) throws SQLException {
        Array roles = rs.getArray("roles");
        Object managerUserId = rs.getObject("manager_user_id");
        List<String> roleList = roles == null ? List.of() : Arrays.asList((String[]) roles.getArray());
        return new OrganizationPosition(
                rs.getLong("id"),
                rs.getLong("organization_unit_id"),
                rs.getString("code"),
                rs.getString("name"),
                roleList,
                managerUserId == null ? null : ((Number) managerUserId).longValue(),
                rs.getString("status"),
                rs.getInt("sort_order")
        );
    }

    private OrganizationPositionAssignment mapAssignment(ResultSet rs) throws SQLException {
        return new OrganizationPositionAssignment(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("position_id"),
                rs.getBoolean("primary_position"),
                rs.getString("status"),
                offsetDateTime(rs, "active_from"),
                offsetDateTime(rs, "active_to")
        );
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }
}
